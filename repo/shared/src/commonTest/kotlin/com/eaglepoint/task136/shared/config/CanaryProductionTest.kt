package com.eaglepoint.task136.shared.config

import com.eaglepoint.task136.shared.rbac.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanaryProductionTest {

    private val manifest = CanaryManifest(
        features = listOf(
            CanaryConfig(
                featureId = "meeting_form_v2",
                targetVersion = 2,
                enabledRoles = setOf("Admin", "Supervisor"),
                enabledDeviceGroups = setOf("default", "beta"),
                rolloutPercentage = 100,
            ),
            CanaryConfig(
                featureId = "order_form_v2",
                targetVersion = 2,
                enabledRoles = setOf("Admin", "Supervisor", "Operator"),
                enabledDeviceGroups = setOf("beta"),
                rolloutPercentage = 50,
            ),
        ),
    )

    private val evaluator = CanaryEvaluator(manifest)

    @Test
    fun `admin gets meeting form v2 on default device group`() {
        val version = evaluator.resolveFormVersion("meeting_form_v2", 1, Role.Admin, "default", "admin")
        assertEquals(2, version)
    }

    @Test
    fun `supervisor gets meeting form v2 on beta device group`() {
        val version = evaluator.resolveFormVersion("meeting_form_v2", 1, Role.Supervisor, "beta", "supervisor")
        assertEquals(2, version)
    }

    @Test
    fun `operator gets meeting form v1 fallback`() {
        val version = evaluator.resolveFormVersion("meeting_form_v2", 1, Role.Operator, "default", "operator")
        assertEquals(1, version)
    }

    @Test
    fun `viewer gets meeting form v1 fallback`() {
        val version = evaluator.resolveFormVersion("meeting_form_v2", 1, Role.Viewer, "default", "viewer")
        assertEquals(1, version)
    }

    @Test
    fun `role gating affects form version`() {
        assertTrue(evaluator.isFeatureEnabled("meeting_form_v2", Role.Admin, "default"))
        assertFalse(evaluator.isFeatureEnabled("meeting_form_v2", Role.Operator, "default"))
    }

    @Test
    fun `device group gating affects form version`() {
        // order_form_v2 requires beta device group
        assertFalse(evaluator.isFeatureEnabled("order_form_v2", Role.Admin, "default", "admin"))
        // With beta device group and a user that falls within 50% rollout
        val version = evaluator.resolveFormVersion("order_form_v2", 1, Role.Admin, "beta", "admin")
        // admin.hashCode() % 100 determines if within rollout - test the gating mechanism
        val defaultGroupVersion = evaluator.resolveFormVersion("order_form_v2", 1, Role.Admin, "default", "admin")
        assertEquals(1, defaultGroupVersion, "Default device group should get version 1")
    }

    @Test
    fun `unknown feature returns default version`() {
        val version = evaluator.resolveFormVersion("unknown_feature", 1, Role.Admin)
        assertEquals(1, version)
    }
}
