package com.eaglepoint.task136.ui.navigation

import com.eaglepoint.task136.shared.rbac.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class Screen {
    data object Dashboard : Screen()
    data object Calendar : Screen()
    data class OrderDetail(val orderId: String) : Screen()
    data object Cart : Screen()
    data class InvoiceDetail(val invoiceId: String) : Screen()
    data class MeetingDetail(val meetingId: String) : Screen()
    data object Admin : Screen()
}

class AppNavigator {
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Dashboard)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val backStack = mutableListOf<Screen>()

    fun navigateTo(screen: Screen, role: Role): Boolean {
        if (!isScreenAllowed(screen, role)) return false
        backStack.add(_currentScreen.value)
        _currentScreen.value = screen
        return true
    }

    fun goBack(): Boolean {
        if (backStack.isEmpty()) return false
        _currentScreen.value = backStack.removeLast()
        return true
    }

    fun resetToDashboard() {
        backStack.clear()
        _currentScreen.value = Screen.Dashboard
    }

    companion object {
        fun isScreenAllowed(screen: Screen, role: Role): Boolean = when (screen) {
            is Screen.Dashboard, is Screen.Calendar -> true
            is Screen.OrderDetail, is Screen.Cart -> role != Role.Viewer
            is Screen.InvoiceDetail -> role == Role.Admin || role == Role.Supervisor || role == Role.Operator
            is Screen.MeetingDetail -> role != Role.Viewer
            is Screen.Admin -> role == Role.Admin
        }
    }
}
