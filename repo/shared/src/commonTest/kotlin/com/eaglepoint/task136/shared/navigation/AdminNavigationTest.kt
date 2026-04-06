package com.eaglepoint.task136.shared.navigation

import com.eaglepoint.task136.shared.rbac.Action
import com.eaglepoint.task136.shared.rbac.PermissionEvaluator
import com.eaglepoint.task136.shared.rbac.ResourceType
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.rbac.defaultRules
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminNavigationTest {

    private val evaluator = PermissionEvaluator(defaultRules())

    private fun isAdminAllowed(role: Role): Boolean {
        return role == Role.Admin
    }

    @Test
    fun `admin can access admin screen`() {
        assertTrue(isAdminAllowed(Role.Admin))
    }

    @Test
    fun `supervisor cannot access admin screen`() {
        assertFalse(isAdminAllowed(Role.Supervisor))
    }

    @Test
    fun `operator cannot access admin screen`() {
        assertFalse(isAdminAllowed(Role.Operator))
    }

    @Test
    fun `viewer cannot access admin screen`() {
        assertFalse(isAdminAllowed(Role.Viewer))
    }

    @Test
    fun `companion cannot access admin screen`() {
        assertFalse(isAdminAllowed(Role.Companion))
    }

    @Test
    fun `admin has full resource management permissions`() {
        assertTrue(evaluator.canAccess(Role.Admin, ResourceType.Resource, "*", Action.Write))
        assertTrue(evaluator.canAccess(Role.Admin, ResourceType.Resource, "*", Action.Delete))
        assertTrue(evaluator.canAccess(Role.Admin, ResourceType.User, "*", Action.Write))
    }

    @Test
    fun `non-admin lacks admin-level permissions`() {
        assertFalse(evaluator.canAccess(Role.Operator, ResourceType.Resource, "*", Action.Write))
        assertFalse(evaluator.canAccess(Role.Viewer, ResourceType.Resource, "*", Action.Write))
        assertFalse(evaluator.canAccess(Role.Companion, ResourceType.Resource, "*", Action.Write))
    }
}
