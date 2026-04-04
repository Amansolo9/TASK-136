package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.security.AuthPrincipal
import com.eaglepoint.task136.shared.security.DeviceBindingResult
import com.eaglepoint.task136.shared.security.DeviceBindingService
import com.eaglepoint.task136.shared.security.LocalAuthService
import com.eaglepoint.task136.shared.security.SecurityRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

data class AuthUiState(
    val username: String = "",
    val password: String = "",
    val principal: AuthPrincipal? = null,
    val error: String? = null,
    val sessionExpiresAt: Instant? = null,
    val absoluteSessionExpiresAt: Instant? = null,
) {
    val isAuthenticated: Boolean get() = principal != null
    val role: Role? get() = principal?.role
}

class AuthViewModel(
    private val securityRepository: SecurityRepository,
    private val authService: LocalAuthService,
    private val deviceBindingService: DeviceBindingService? = null,
    private val deviceFingerprint: String = "",
    private val clock: Clock,
    private val sessionDuration: Duration = 30.minutes,
    private val absoluteSessionLimit: Duration = 8.hours,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun updateUsername(value: String) {
        _state.value = _state.value.copy(username = value, error = null)
    }

    fun updatePassword(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    fun login() {
        val snapshot = _state.value
        val userId = snapshot.username.trim().lowercase()
        if (userId.isBlank()) {
            _state.value = snapshot.copy(error = "Username is required")
            return
        }

        val policyResult = securityRepository.validatePassword(snapshot.password)
        if (!policyResult.isValid) {
            _state.value = snapshot.copy(error = policyResult.errors.first())
            return
        }

        scope.launch(ioDispatcher) {
            if (!securityRepository.canAuthenticate(userId, clock.now())) {
                _state.value = snapshot.copy(error = "Too many attempts. Try again later.")
                return@launch
            }

            val principal = authService.authenticate(userId, snapshot.password)
            if (principal == null) {
                securityRepository.recordFailure(userId, clock.now())
                _state.value = snapshot.copy(error = "Invalid username or password")
                return@launch
            }

            if (deviceBindingService != null && deviceFingerprint.isNotBlank()) {
                val bindResult = deviceBindingService.checkAndBindDevice(userId, deviceFingerprint)
                if (bindResult is DeviceBindingResult.LimitExceeded) {
                    _state.value = snapshot.copy(
                        error = "Device limit exceeded (${bindResult.bound}/${bindResult.max}). Ask an Admin to reset your device bindings.",
                    )
                    return@launch
                }
            }

            securityRepository.recordSuccess(userId)
            val now = clock.now()
            _state.value = snapshot.copy(
                principal = principal,
                error = null,
                password = "",
                sessionExpiresAt = now.plus(sessionDuration),
                absoluteSessionExpiresAt = now.plus(absoluteSessionLimit),
            )
        }
    }

    fun ensureSessionActive() {
        val now = clock.now()
        _state.update { current ->
            when {
                current.sessionExpiresAt != null && now >= current.sessionExpiresAt!! ->
                    AuthUiState(username = current.username, error = "Session expired. Please sign in again.")
                current.absoluteSessionExpiresAt != null && now >= current.absoluteSessionExpiresAt!! ->
                    AuthUiState(username = current.username, error = "Maximum session duration reached. Please sign in again.")
                else -> current
            }
        }
    }

    fun touchSession() {
        _state.update { current ->
            if (current.isAuthenticated) current.copy(sessionExpiresAt = clock.now().plus(sessionDuration))
            else current
        }
    }

    fun logout(message: String? = null) {
        val previous = _state.value
        _state.value = AuthUiState(
            username = previous.username,
            error = message,
        )
    }
}
