package com.eaglepoint.task136.shared.governance

import com.eaglepoint.task136.shared.db.GovernanceDao
import com.eaglepoint.task136.shared.db.RuleHitMetricEntity
import com.eaglepoint.task136.shared.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

private const val TAG = "GovernanceAnalytics"

class GovernanceAnalytics(
    private val governanceDao: GovernanceDao,
    private val clock: Clock,
) {
    suspend fun logRuleHit(ruleName: String, valueObserved: Double, resolved: Boolean = false) {
        withContext(Dispatchers.IO) {
            governanceDao.logRuleHit(
                RuleHitMetricEntity(
                    ruleName = ruleName,
                    valueObserved = valueObserved,
                    createdAt = clock.now().toEpochMilliseconds(),
                    resolved = resolved,
                ),
            )
            AppLogger.i(TAG, "Rule hit logged: $ruleName value=$valueObserved resolved=$resolved")
        }
    }

    suspend fun logValidationFailure(fieldName: String, value: Double) {
        logRuleHit("validation_failure:$fieldName", value)
    }

    suspend fun logAllergenBlock(resourceName: String) {
        logRuleHit("allergen_block:$resourceName", 0.0)
    }

    suspend fun logPriceViolation(price: Double) {
        logRuleHit("price_violation", price)
    }

    suspend fun logRefundDenied(invoiceId: String, reason: String) {
        logRuleHit("refund_denied:$reason", 0.0)
    }
}
