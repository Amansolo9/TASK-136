package com.eaglepoint.task136.shared.rbac

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AbacPolicyEvaluatorTest {
    private val evaluator = AbacPolicyEvaluator()

    // ── canReadInvoiceTaxField ────────────────────────────────────

    @Test
    fun `admin can read invoice tax on trusted device`() {
        assertTrue(evaluator.canReadInvoiceTaxField(Role.Admin, ctx(trusted = true)))
    }

    @Test
    fun `admin cannot read invoice tax on untrusted device`() {
        assertFalse(evaluator.canReadInvoiceTaxField(Role.Admin, ctx(trusted = false)))
    }

    @Test
    fun `supervisor cannot read invoice tax`() {
        assertFalse(evaluator.canReadInvoiceTaxField(Role.Supervisor, ctx(trusted = true)))
    }

    @Test
    fun `operator cannot read invoice tax`() {
        assertFalse(evaluator.canReadInvoiceTaxField(Role.Operator, ctx(trusted = true)))
    }

    @Test
    fun `viewer cannot read invoice tax`() {
        assertFalse(evaluator.canReadInvoiceTaxField(Role.Viewer, ctx(trusted = true)))
    }

    @Test
    fun `companion cannot read invoice tax`() {
        assertFalse(evaluator.canReadInvoiceTaxField(Role.Companion, ctx(trusted = true)))
    }

    // ── canIssueRefund ───────────────────────────────────────────

    @Test
    fun `admin can issue refund on trusted device`() {
        assertTrue(evaluator.canIssueRefund(Role.Admin, ctx(trusted = true)))
    }

    @Test
    fun `supervisor can issue refund on trusted device`() {
        assertTrue(evaluator.canIssueRefund(Role.Supervisor, ctx(trusted = true)))
    }

    @Test
    fun `admin cannot issue refund on untrusted device`() {
        assertFalse(evaluator.canIssueRefund(Role.Admin, ctx(trusted = false)))
    }

    @Test
    fun `operator cannot issue refund`() {
        assertFalse(evaluator.canIssueRefund(Role.Operator, ctx(trusted = true)))
    }

    @Test
    fun `viewer cannot issue refund`() {
        assertFalse(evaluator.canIssueRefund(Role.Viewer, ctx(trusted = true)))
    }

    @Test
    fun `companion cannot issue refund`() {
        assertFalse(evaluator.canIssueRefund(Role.Companion, ctx(trusted = true)))
    }

    // ── canReadAttendee ──────────────────────────────────────────

    @Test
    fun `admin can read any attendee on trusted device`() {
        assertTrue(evaluator.canReadAttendee(Role.Admin, ctx(requester = "admin", owner = "other", trusted = true)))
    }

    @Test
    fun `supervisor can read any attendee on trusted device`() {
        assertTrue(evaluator.canReadAttendee(Role.Supervisor, ctx(requester = "sup", owner = "other", trusted = true)))
    }

    @Test
    fun `operator cannot read attendee list`() {
        assertFalse(evaluator.canReadAttendee(Role.Operator, ctx(requester = "op1", owner = "op1", trusted = true)))
    }

    @Test
    fun `operator cannot read other attendee without delegation`() {
        assertFalse(evaluator.canReadAttendee(Role.Operator, ctx(requester = "op1", owner = "op2", trusted = true)))
    }

    @Test
    fun `operator cannot read delegated attendee list`() {
        assertFalse(evaluator.canReadAttendee(Role.Operator, ctx(requester = "op1", owner = "op2", delegate = true, trusted = true)))
    }

    @Test
    fun `companion cannot read own attendee list`() {
        assertFalse(evaluator.canReadAttendee(Role.Companion, ctx(requester = "c1", owner = "c1", trusted = true)))
    }

    @Test
    fun `companion cannot read delegated attendee list`() {
        assertFalse(evaluator.canReadAttendee(Role.Companion, ctx(requester = "c1", owner = "op1", delegate = true, trusted = true)))
    }

    @Test
    fun `companion cannot read non-delegated other attendee`() {
        assertFalse(evaluator.canReadAttendee(Role.Companion, ctx(requester = "c1", owner = "op1", trusted = true)))
    }

    @Test
    fun `viewer cannot read own attendee list`() {
        assertFalse(evaluator.canReadAttendee(Role.Viewer, ctx(requester = "v1", owner = "v1", trusted = true)))
    }

    @Test
    fun `viewer cannot read other attendee`() {
        assertFalse(evaluator.canReadAttendee(Role.Viewer, ctx(requester = "v1", owner = "v2", trusted = true)))
    }

    @Test
    fun `viewer cannot read attendee even as delegate`() {
        assertFalse(evaluator.canReadAttendee(Role.Viewer, ctx(requester = "v1", owner = "v2", delegate = true, trusted = true)))
    }

    // ── canManageAttendee ───────────────────────────────────────

    @Test
    fun `operator can manage own attendee`() {
        assertTrue(evaluator.canManageAttendee(Role.Operator, ctx(requester = "op1", owner = "op1", trusted = true)))
    }

    @Test
    fun `operator can manage delegated attendee`() {
        assertTrue(evaluator.canManageAttendee(Role.Operator, ctx(requester = "op1", owner = "op2", delegate = true, trusted = true)))
    }

    @Test
    fun `companion can manage delegated attendee`() {
        assertTrue(evaluator.canManageAttendee(Role.Companion, ctx(requester = "c1", owner = "op1", delegate = true, trusted = true)))
    }

    @Test
    fun `viewer cannot manage attendee`() {
        assertFalse(evaluator.canManageAttendee(Role.Viewer, ctx(requester = "v1", owner = "v1", trusted = true)))
    }

    // ── untrusted device blocks everything ───────────────────────

    @Test
    fun `untrusted device blocks attendee read for all roles`() {
        Role.entries.forEach { role ->
            assertFalse(evaluator.canReadAttendee(role, ctx(trusted = false)), "Should block $role on untrusted device")
        }
    }

    @Test
    fun `untrusted device blocks refund for all roles`() {
        Role.entries.forEach { role ->
            assertFalse(evaluator.canIssueRefund(role, ctx(trusted = false)), "Should block $role refund on untrusted device")
        }
    }

    // ── buildContext helper ───────────────────────────────────────

    @Test
    fun `buildContext creates correct context`() {
        val ctx = evaluator.buildContext("req", "own", isDelegate = true, deviceTrusted = false)
        assertTrue(ctx.requesterId == "req")
        assertTrue(ctx.ownerId == "own")
        assertTrue(ctx.isDelegate)
        assertFalse(ctx.deviceTrusted)
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun ctx(
        requester: String = "user1",
        owner: String = "user1",
        delegate: Boolean = false,
        trusted: Boolean = true,
    ) = AccessContext(requesterId = requester, ownerId = owner, isDelegate = delegate, deviceTrusted = trusted)
}
