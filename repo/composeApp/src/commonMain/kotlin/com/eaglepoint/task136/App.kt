package com.eaglepoint.task136

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.viewmodel.AuthViewModel
import com.eaglepoint.task136.shared.viewmodel.LearningViewModel
import com.eaglepoint.task136.shared.viewmodel.MeetingWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderFinanceViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.ResourceListViewModel
import com.eaglepoint.task136.ui.AdminScreen
import com.eaglepoint.task136.ui.CalendarScreen
import com.eaglepoint.task136.ui.CartScreen
import com.eaglepoint.task136.ui.InvoiceDetailScreen
import com.eaglepoint.task136.ui.LoginScreen
import com.eaglepoint.task136.ui.MeetingDetailScreen
import com.eaglepoint.task136.ui.OrderDetailScreen
import com.eaglepoint.task136.ui.ResourceListScreen
import com.eaglepoint.task136.ui.navigation.AppNavigator
import com.eaglepoint.task136.ui.navigation.Screen
import com.eaglepoint.task136.ui.theme.Task136Theme

@Composable
fun App(
    resourceListViewModel: ResourceListViewModel,
    authViewModel: AuthViewModel,
    orderWorkflowViewModel: OrderWorkflowViewModel,
    meetingWorkflowViewModel: MeetingWorkflowViewModel,
    orderFinanceViewModel: OrderFinanceViewModel,
    learningViewModel: LearningViewModel? = null,
) {
    val authState by authViewModel.state.collectAsState()
    val navigator = remember { AppNavigator() }

    fun clearAllSessionState() {
        resourceListViewModel.clearSessionState()
        orderWorkflowViewModel.clearSessionState()
        meetingWorkflowViewModel.clearSessionState()
        orderFinanceViewModel.clearSessionState()
        learningViewModel?.clearSessionState()
        navigator.resetToDashboard()
    }

    LaunchedEffect(authState.isAuthenticated) {
        if (!authState.isAuthenticated) {
            clearAllSessionState()
        }
        while (authState.isAuthenticated) {
            delay(30_000)
            val wasAuthenticated = authViewModel.state.value.isAuthenticated
            authViewModel.ensureSessionActive()
            if (wasAuthenticated && !authViewModel.state.value.isAuthenticated) {
                clearAllSessionState()
            }
        }
    }

    Task136Theme {
        if (authState.isAuthenticated) {
            val currentScreen by navigator.currentScreen.collectAsState()
            val role = authState.role?.name ?: "Unknown"
            val roleEnum = authState.role ?: Role.Viewer
            val actor = authState.principal?.userId.orEmpty()
            val delegate = authState.principal?.delegateForUserId

            val onLogout: () -> Unit = {
                clearAllSessionState()
                authViewModel.logout()
            }

            val onNavigate: (Screen) -> Unit = { screen ->
                navigator.navigateTo(screen, roleEnum)
            }

            when (val screen = currentScreen) {
                is Screen.Dashboard -> {
                    ResourceListScreen(
                        viewModel = resourceListViewModel,
                        orderWorkflowViewModel = orderWorkflowViewModel,
                        meetingWorkflowViewModel = meetingWorkflowViewModel,
                        orderFinanceViewModel = orderFinanceViewModel,
                        roleLabel = role,
                        onActivity = authViewModel::touchSession,
                        onLogout = onLogout,
                        actorId = actor,
                        delegateForUserId = delegate,
                        onNavigate = onNavigate,
                    )
                }
                is Screen.Calendar -> {
                    CalendarScreen(
                        orderWorkflowViewModel = orderWorkflowViewModel,
                        resourceListViewModel = resourceListViewModel,
                        roleLabel = role,
                        actorId = actor,
                        onBack = { navigator.goBack() },
                        onActivity = authViewModel::touchSession,
                    )
                }
                is Screen.OrderDetail -> {
                    if (!AppNavigator.isScreenAllowed(screen, roleEnum)) {
                        navigator.resetToDashboard()
                    } else {
                        OrderDetailScreen(
                            orderId = screen.orderId,
                            orderWorkflowViewModel = orderWorkflowViewModel,
                            roleLabel = role,
                            actorId = actor,
                            onBack = { navigator.goBack() },
                            onActivity = authViewModel::touchSession,
                        )
                    }
                }
                is Screen.Cart -> {
                    if (!AppNavigator.isScreenAllowed(screen, roleEnum)) {
                        navigator.resetToDashboard()
                    } else {
                        CartScreen(
                            orderFinanceViewModel = orderFinanceViewModel,
                            roleLabel = role,
                            actorId = actor,
                            delegateForUserId = delegate,
                            onBack = { navigator.goBack() },
                            onActivity = authViewModel::touchSession,
                        )
                    }
                }
                is Screen.InvoiceDetail -> {
                    if (!AppNavigator.isScreenAllowed(screen, roleEnum)) {
                        navigator.resetToDashboard()
                    } else {
                        InvoiceDetailScreen(
                            invoiceId = screen.invoiceId,
                            orderFinanceViewModel = orderFinanceViewModel,
                            roleLabel = role,
                            actorId = actor,
                            onBack = { navigator.goBack() },
                            onActivity = authViewModel::touchSession,
                        )
                    }
                }
                is Screen.MeetingDetail -> {
                    if (!AppNavigator.isScreenAllowed(screen, roleEnum)) {
                        navigator.resetToDashboard()
                    } else {
                        MeetingDetailScreen(
                            meetingId = screen.meetingId,
                            meetingWorkflowViewModel = meetingWorkflowViewModel,
                            roleLabel = role,
                            actorId = actor,
                            onBack = { navigator.goBack() },
                            onActivity = authViewModel::touchSession,
                        )
                    }
                }
                is Screen.Admin -> {
                    if (!AppNavigator.isScreenAllowed(screen, roleEnum)) {
                        navigator.resetToDashboard()
                    } else {
                        AdminScreen(
                            resourceListViewModel = resourceListViewModel,
                            onBack = { navigator.goBack() },
                            onActivity = authViewModel::touchSession,
                        )
                    }
                }
            }
        } else {
            LoginScreen(
                state = authState,
                onUsernameChange = authViewModel::updateUsername,
                onPasswordChange = authViewModel::updatePassword,
                onLoginClick = authViewModel::login,
            )
        }
    }
}
