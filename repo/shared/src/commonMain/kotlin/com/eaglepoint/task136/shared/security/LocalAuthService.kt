package com.eaglepoint.task136.shared.security

import com.eaglepoint.task136.shared.db.UserDao
import com.eaglepoint.task136.shared.db.UserEntity
import com.eaglepoint.task136.shared.rbac.Role

data class AuthPrincipal(
    val userId: String,
    val displayName: String,
    val role: Role,
    val delegateForUserId: String? = null,
)

class LocalAuthService(
    private val userDao: UserDao,
    private val enableDemoSeed: Boolean = false,
) {
    suspend fun seedDemoAccountsIfNeeded() {
        if (!enableDemoSeed) return
        if (userDao.countAll() > 0) return
        val demos = listOf(
            DemoRecord("admin", "Admin1234!", "Avery Admin", Role.Admin),
            DemoRecord("supervisor", "Super1234!", "Sky Supervisor", Role.Supervisor),
            DemoRecord("operator", "Oper12345!", "Olive Operator", Role.Operator),
            DemoRecord("viewer", "Viewer1234!", "Vera Viewer", Role.Viewer),
            DemoRecord("companion", "Companion1!", "Casey Companion", Role.Companion, delegateFor = "operator"),
        )
        demos.forEach { demo ->
            val salt = secureRandomHex(16)
            val hash = pbkdf2Hash(password = demo.password, salt = salt)
            userDao.upsert(
                UserEntity(
                    id = demo.username,
                    fullName = demo.displayName,
                    email = "${demo.username}@eaglepoint.local",
                    role = demo.role.name,
                    passwordHash = hash,
                    passwordSalt = salt,
                    delegateForUserId = demo.delegateFor,
                    maskedPII = "",
                    encryptedWalletRef = "",
                    isActive = true,
                ),
            )
        }
    }

    suspend fun authenticate(username: String, password: String): AuthPrincipal? {
        val user = userDao.getById(username) ?: return null
        if (!user.isActive) return null
        val candidateHash = pbkdf2Hash(password = password, salt = user.passwordSalt)
        if (!constantTimeEquals(user.passwordHash, candidateHash)) return null
        val role = try { Role.valueOf(user.role) } catch (_: Exception) { return null }
        return AuthPrincipal(
            userId = user.id,
            displayName = user.fullName,
            role = role,
            delegateForUserId = user.delegateForUserId,
        )
    }

    private data class DemoRecord(
        val username: String,
        val password: String,
        val displayName: String,
        val role: Role,
        val delegateFor: String? = null,
    )
}
