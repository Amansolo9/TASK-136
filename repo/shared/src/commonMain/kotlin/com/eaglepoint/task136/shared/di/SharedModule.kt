package com.eaglepoint.task136.shared.di

import com.eaglepoint.task136.shared.governance.AnomalyNotifier
import com.eaglepoint.task136.shared.governance.ReconciliationService
import com.eaglepoint.task136.shared.governance.RuleHitObserver
import com.eaglepoint.task136.shared.logging.AppLogger
import com.eaglepoint.task136.shared.orders.BookingUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.eaglepoint.task136.shared.orders.OrderStateMachine
import com.eaglepoint.task136.shared.platform.NotificationScheduler
import com.eaglepoint.task136.shared.platform.createNotificationScheduler
import com.eaglepoint.task136.shared.platform.PlatformNotificationGateway
import com.eaglepoint.task136.shared.platform.PlatformReceiptGateway
import com.eaglepoint.task136.shared.platform.ReceiptService
import com.eaglepoint.task136.shared.platform.createReceiptService
import com.eaglepoint.task136.shared.platform.NotificationGateway
import com.eaglepoint.task136.shared.platform.ReceiptGateway
import com.eaglepoint.task136.shared.rbac.AbacPolicyEvaluator
import com.eaglepoint.task136.shared.rbac.PermissionEvaluator
import com.eaglepoint.task136.shared.rbac.defaultRules
import com.eaglepoint.task136.shared.security.DeviceBindingService
import com.eaglepoint.task136.shared.security.LocalAuthService
import com.eaglepoint.task136.shared.security.SecurityRepository
import com.eaglepoint.task136.shared.viewmodel.AuthViewModel
import com.eaglepoint.task136.shared.viewmodel.MeetingWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderFinanceViewModel
import com.eaglepoint.task136.shared.viewmodel.ResourceListViewModel
import com.eaglepoint.task136.shared.services.ValidationService
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import org.koin.core.module.Module
import org.koin.dsl.module

fun sharedCoreModule(isDebug: Boolean = false): Module = module {
    single<Clock> { Clock.System }
    single<TimeZone> { TimeZone.currentSystemDefault() }

    single { SecurityRepository(clock = get(), userDao = get()) }
    single { LocalAuthService(userDao = get(), enableDemoSeed = isDebug) }
    single { AbacPolicyEvaluator() }
    single { PermissionEvaluator(defaultRules()) }
    single { ValidationService(clock = get()) }
    single { DeviceBindingService(deviceBindingDao = get(), clock = get()) }
    single {
        AuthViewModel(
            securityRepository = get(),
            authService = get(),
            deviceBindingService = get(),
            deviceFingerprint = com.eaglepoint.task136.shared.platform.getDeviceFingerprint(),
            clock = get(),
        )
    }
    single<NotificationScheduler> { createNotificationScheduler() }
    single<ReceiptService> { createReceiptService() }
    single<NotificationGateway> { PlatformNotificationGateway(scheduler = get(), clock = get(), timeZone = get()) }
    single<ReceiptGateway> { PlatformReceiptGateway(receiptService = get()) }

    single { BookingUseCase(orderDao = get(), clock = get()) }
    single { OrderStateMachine(database = get(), clock = get()) }
    single {
        OrderWorkflowViewModel(
            orderDao = get(),
            resourceDao = get(),
            stateMachine = get(),
            bookingUseCase = get(),
            permissionEvaluator = get(),
            validationService = get(),
            clock = get(),
        )
    }
    single {
        MeetingWorkflowViewModel(
            validationService = get(),
            permissionEvaluator = get(),
            abacPolicyEvaluator = get(),
            deviceBindingService = get(),
            meetingDao = get(),
            notificationGateway = get(),
            bookingUseCase = get(),
            clock = get(),
        )
    }
    single {
        OrderFinanceViewModel(
            abac = get(),
            permissionEvaluator = get(),
            validationService = get(),
            deviceBindingService = get(),
            cartDao = get(),
            invoiceDao = get(),
            stateMachine = get(),
            notificationGateway = get(),
            receiptGateway = get(),
        )
    }
    single { ReconciliationService(database = get(), clock = get(), timeZone = get()) }
    single {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val notifier = object : AnomalyNotifier {
            override suspend fun notify(ruleName: String, value: Double, detail: String) {
                AppLogger.w("RuleHitObserver", "Anomaly: $ruleName value=$value $detail")
            }
        }
        RuleHitObserver(governanceDao = get(), anomalyNotifier = notifier, scope = appScope)
    }
    single {
        ResourceListViewModel(resourceDao = get(), validationService = get(), isDebug = isDebug)
    }
    single { com.eaglepoint.task136.shared.viewmodel.LearningViewModel(learningDao = get(), permissionEvaluator = get(), clock = get()) }
}
