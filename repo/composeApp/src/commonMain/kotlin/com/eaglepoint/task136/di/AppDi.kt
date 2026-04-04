package com.eaglepoint.task136.di

import com.eaglepoint.task136.shared.di.databaseModule
import com.eaglepoint.task136.shared.di.sharedCoreModule
import com.eaglepoint.task136.shared.db.AppDatabase
import com.eaglepoint.task136.shared.governance.ReconciliationService
import com.eaglepoint.task136.shared.governance.RuleHitObserver
import com.eaglepoint.task136.shared.orders.OrderStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

fun initKoinIfNeeded(database: AppDatabase, isDebug: Boolean = false) {
    if (GlobalContext.getOrNull() != null) return

    startKoin {
        modules(
            databaseModule(database),
            sharedCoreModule(isDebug = isDebug),
        )
    }

    val koin = GlobalContext.get()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // M2: Start RuleHitObserver to process anomaly detection
    koin.get<RuleHitObserver>().start()

    // M1: Trigger reconciliation on app launch
    val reconciliation = koin.get<ReconciliationService>()
    appScope.launch {
        reconciliation.runDailyClosureIfDue()
        reconciliation.runSettlementIfDue()
    }

    // M4: Expire stale PendingTender orders on startup
    val stateMachine = koin.get<OrderStateMachine>()
    appScope.launch {
        stateMachine.expireStaleOrders()
    }
}
