package com.eaglepoint.task136.shared.rbac

data class AccessContext(
    val requesterId: String,
    val ownerId: String,
    val isDelegate: Boolean,
    val deviceTrusted: Boolean,
)

class AbacPolicyEvaluator {
    fun canReadAttendee(role: Role, context: AccessContext): Boolean {
        if (!context.deviceTrusted) return false
        return when (role) {
            Role.Admin -> true
            Role.Supervisor -> true
            Role.Operator, Role.Companion, Role.Viewer -> false
        }
    }

    fun canManageAttendee(role: Role, context: AccessContext): Boolean {
        if (!context.deviceTrusted) return false
        return when (role) {
            Role.Admin -> true
            Role.Supervisor -> true
            Role.Operator, Role.Companion -> context.requesterId == context.ownerId || context.isDelegate
            Role.Viewer -> false
        }
    }

    fun canReadInvoiceTaxField(role: Role, context: AccessContext): Boolean {
        if (!context.deviceTrusted) return false
        return role == Role.Admin
    }

    fun canIssueRefund(role: Role, context: AccessContext): Boolean {
        if (!context.deviceTrusted) return false
        return role == Role.Admin || role == Role.Supervisor
    }

    fun buildContext(
        requesterId: String,
        ownerId: String,
        isDelegate: Boolean = false,
        deviceTrusted: Boolean,
    ): AccessContext = AccessContext(
        requesterId = requesterId,
        ownerId = ownerId,
        isDelegate = isDelegate,
        deviceTrusted = deviceTrusted,
    )
}
