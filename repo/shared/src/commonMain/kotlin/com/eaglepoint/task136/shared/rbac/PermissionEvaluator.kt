package com.eaglepoint.task136.shared.rbac

enum class Role { Admin, Supervisor, Operator, Viewer, Companion }

enum class ResourceType { Resource, Order, User, Wallet, Analytics, Ledger, Meeting, Learning }

enum class Action { Read, Write, Delete, Approve }

data class PermissionRule(
    val role: Role,
    val resource: ResourceType,
    val field: String,
    val allowedActions: Set<Action>,
)

class PermissionEvaluator(
    private val rules: List<PermissionRule>,
) {
    fun canAccess(role: Role, resource: ResourceType, field: String, action: Action): Boolean {
        return rules.any { rule ->
            rule.role == role &&
                rule.resource == resource &&
                (rule.field == field || rule.field == "*") &&
                action in rule.allowedActions
        }
    }

    suspend fun <T> guardOrNull(
        role: Role,
        resource: ResourceType,
        field: String,
        action: Action,
        valueProvider: suspend () -> T,
    ): T? = if (canAccess(role, resource, field, action)) valueProvider() else null
}

fun defaultRules(): List<PermissionRule> = listOf(
    PermissionRule(Role.Admin, ResourceType.Resource, "*", Action.entries.toSet()),
    PermissionRule(Role.Admin, ResourceType.Order, "*", Action.entries.toSet()),
    PermissionRule(Role.Admin, ResourceType.User, "*", Action.entries.toSet()),
    PermissionRule(Role.Admin, ResourceType.Wallet, "*", Action.entries.toSet()),
    PermissionRule(Role.Admin, ResourceType.Analytics, "*", Action.entries.toSet()),
    PermissionRule(Role.Admin, ResourceType.Ledger, "*", Action.entries.toSet()),
    PermissionRule(Role.Supervisor, ResourceType.Order, "*", setOf(Action.Read, Action.Write, Action.Approve)),
    PermissionRule(Role.Supervisor, ResourceType.Resource, "*", setOf(Action.Read, Action.Write)),
    PermissionRule(Role.Supervisor, ResourceType.User, "maskedPII", setOf(Action.Read)),
    PermissionRule(Role.Operator, ResourceType.Order, "*", setOf(Action.Read, Action.Write)),
    PermissionRule(Role.Operator, ResourceType.Resource, "*", setOf(Action.Read)),
    PermissionRule(Role.Operator, ResourceType.User, "maskedPII", setOf(Action.Read)),
    PermissionRule(Role.Viewer, ResourceType.Order, "*", setOf(Action.Read)),
    PermissionRule(Role.Viewer, ResourceType.Resource, "*", setOf(Action.Read)),
    PermissionRule(Role.Companion, ResourceType.Order, "*", setOf(Action.Read, Action.Write)),
    PermissionRule(Role.Companion, ResourceType.Resource, "*", setOf(Action.Read)),
    PermissionRule(Role.Admin, ResourceType.Meeting, "*", Action.entries.toSet()),
    PermissionRule(Role.Supervisor, ResourceType.Meeting, "*", setOf(Action.Read, Action.Write, Action.Approve)),
    PermissionRule(Role.Operator, ResourceType.Meeting, "*", setOf(Action.Read, Action.Write)),
    PermissionRule(Role.Viewer, ResourceType.Meeting, "*", setOf(Action.Read)),
    PermissionRule(Role.Companion, ResourceType.Meeting, "*", setOf(Action.Read, Action.Write)),
    PermissionRule(Role.Admin, ResourceType.Learning, "*", Action.entries.toSet()),
    PermissionRule(Role.Supervisor, ResourceType.Learning, "*", setOf(Action.Read, Action.Write)),
    PermissionRule(Role.Operator, ResourceType.Learning, "*", setOf(Action.Read, Action.Write)),
    PermissionRule(Role.Viewer, ResourceType.Learning, "*", setOf(Action.Read)),
    PermissionRule(Role.Companion, ResourceType.Learning, "*", setOf(Action.Read, Action.Write)),
)
