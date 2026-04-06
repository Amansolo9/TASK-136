package com.eaglepoint.task136.shared.governance

import com.eaglepoint.task136.shared.db.DailyLedgerEntity
import com.eaglepoint.task136.shared.db.DiscrepancyTicketEntity
import com.eaglepoint.task136.shared.db.GovernanceDao
import com.eaglepoint.task136.shared.db.RuleHitMetricEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GovernanceAnalyticsTest {

    private val testClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-03-30T10:00:00Z")
    }

    private val loggedHits = mutableListOf<RuleHitMetricEntity>()

    private val fakeGovernanceDao = object : GovernanceDao {
        override suspend fun logRuleHit(metric: RuleHitMetricEntity) {
            loggedHits.add(metric)
        }
        override fun observeOpenRuleHits(): Flow<List<RuleHitMetricEntity>> = emptyFlow()
        override suspend fun upsertLedger(entry: DailyLedgerEntity) = Unit
        override suspend fun getLedgerByDate(businessDate: String): DailyLedgerEntity? = null
        override suspend fun createDiscrepancy(ticket: DiscrepancyTicketEntity) = Unit
    }

    private fun createAnalytics(): GovernanceAnalytics {
        loggedHits.clear()
        return GovernanceAnalytics(fakeGovernanceDao, testClock)
    }

    @Test
    fun `validation failure logs rule hit`() = runTest {
        val analytics = createAnalytics()
        analytics.logValidationFailure("price", 15000.0)

        assertEquals(1, loggedHits.size)
        assertTrue(loggedHits[0].ruleName.contains("validation_failure"))
        assertEquals(15000.0, loggedHits[0].valueObserved)
    }

    @Test
    fun `allergen block logs rule hit`() = runTest {
        val analytics = createAnalytics()
        analytics.logAllergenBlock("Unsafe Food Item")

        assertEquals(1, loggedHits.size)
        assertTrue(loggedHits[0].ruleName.contains("allergen_block"))
    }

    @Test
    fun `price violation logs rule hit`() = runTest {
        val analytics = createAnalytics()
        analytics.logPriceViolation(99999.99)

        assertEquals(1, loggedHits.size)
        assertEquals("price_violation", loggedHits[0].ruleName)
        assertEquals(99999.99, loggedHits[0].valueObserved)
    }

    @Test
    fun `refund denied logs rule hit`() = runTest {
        val analytics = createAnalytics()
        analytics.logRefundDenied("inv-1", "role_denied:Viewer")

        assertEquals(1, loggedHits.size)
        assertTrue(loggedHits[0].ruleName.contains("refund_denied"))
    }

    @Test
    fun `multiple rule hits are persisted independently`() = runTest {
        val analytics = createAnalytics()
        analytics.logPriceViolation(0.001)
        analytics.logAllergenBlock("Product A")
        analytics.logRefundDenied("inv-2", "role_denied:Companion")

        assertEquals(3, loggedHits.size)
        assertTrue(loggedHits.all { it.createdAt > 0 })
    }
}
