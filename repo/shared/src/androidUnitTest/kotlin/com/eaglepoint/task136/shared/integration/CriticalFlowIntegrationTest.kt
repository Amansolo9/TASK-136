package com.eaglepoint.task136.shared.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eaglepoint.task136.shared.db.AppDatabase
import com.eaglepoint.task136.shared.db.DeviceBindingEntity
import com.eaglepoint.task136.shared.db.InvoiceEntity
import com.eaglepoint.task136.shared.db.MeetingEntity
import com.eaglepoint.task136.shared.db.OrderEntity
import com.eaglepoint.task136.shared.orders.BookingUseCase
import com.eaglepoint.task136.shared.rbac.AbacPolicyEvaluator
import com.eaglepoint.task136.shared.rbac.PermissionEvaluator
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.rbac.defaultRules
import com.eaglepoint.task136.shared.security.DeviceBindingService
import com.eaglepoint.task136.shared.services.MeetingNoShowReconciliationService
import com.eaglepoint.task136.shared.services.ValidationService
import com.eaglepoint.task136.shared.viewmodel.MeetingStatus
import com.eaglepoint.task136.shared.viewmodel.OrderFinanceViewModel
import com.eaglepoint.task136.shared.platform.NotificationGateway
import com.eaglepoint.task136.shared.platform.ReceiptGateway
import com.eaglepoint.task136.shared.platform.ReceiptLineItem
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CriticalFlowIntegrationTest {
    private lateinit var db: AppDatabase
    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-04-01T10:00:00Z")
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun booking_recommendation_is_meeting_backed_and_conflicts_block_slots() = runBlocking {
        val useCase = BookingUseCase(meetingDao = db.meetingDao(), clock = fixedClock)
        db.meetingDao().upsert(
            MeetingEntity(
                id = "m-1",
                organizerId = "operator",
                resourceId = "res-1",
                title = "Conflicting",
                startTime = Instant.parse("2026-04-01T10:00:00Z").toEpochMilliseconds(),
                endTime = Instant.parse("2026-04-01T11:00:00Z").toEpochMilliseconds(),
                status = MeetingStatus.Approved.name,
            ),
        )

        val slots = useCase.findThreeAvailableSlots(
            resourceId = "res-1",
            duration = 30.minutes,
            anchor = Instant.parse("2026-04-01T09:00:00Z"),
        )

        assertEquals(3, slots.size)
        assertEquals(Instant.parse("2026-04-01T09:00:00Z"), slots[0].start)
        assertEquals(Instant.parse("2026-04-01T11:20:00Z"), slots[1].start)
        assertTrue(slots[2].start <= Instant.parse("2026-04-15T09:00:00Z"))
    }

    @Test
    fun refund_authorization_uses_persisted_invoice_order_context() = runBlocking {
        db.deviceBindingDao().upsert(DeviceBindingEntity("b1", "supervisor", "test", 0L))
        db.deviceBindingDao().upsert(DeviceBindingEntity("b2", "companion", "test", 0L))
        db.deviceBindingDao().upsert(DeviceBindingEntity("b3", "owner", "test", 0L))

        db.orderDao().upsert(
            OrderEntity(
                id = "ord-1",
                userId = "owner",
                resourceId = "res-1",
                state = "Confirmed",
                startTime = 0L,
                endTime = 0L,
                expiresAt = null,
                quantity = 1,
                totalPrice = 25.0,
            ),
        )
        db.invoiceDao().upsert(
            InvoiceEntity(
                id = "inv-1",
                subtotal = 25.0,
                tax = 3.0,
                total = 28.0,
                orderId = "ord-1",
                ownerId = "owner",
                actorId = "owner",
                createdAt = 0L,
            ),
        )

        val vm = OrderFinanceViewModel(
            abac = AbacPolicyEvaluator(),
            permissionEvaluator = PermissionEvaluator(defaultRules()),
            validationService = ValidationService(fixedClock),
            deviceBindingService = DeviceBindingService(db.deviceBindingDao(), fixedClock),
            cartDao = db.cartDao(),
            invoiceDao = db.invoiceDao(),
            orderDao = db.orderDao(),
            notificationGateway = object : NotificationGateway {
                override suspend fun scheduleInvoiceReady(invoiceId: String, total: Double) = Unit
                override suspend fun scheduleMeetingNotification(meetingId: String, message: String) = Unit
                override suspend fun scheduleOrderReminder(orderId: String, message: String) = Unit
            },
            receiptGateway = object : ReceiptGateway {
                override suspend fun shareReceipt(invoiceId: String, customerName: String, lineItems: List<ReceiptLineItem>, total: Double) = Unit
            },
            deviceFingerprint = "test",
            clock = fixedClock,
            requestRefundTransition = { _, role -> role == Role.Admin || role == Role.Supervisor },
            completeRefundTransition = { _, role -> role == Role.Admin || role == Role.Supervisor },
        )

        vm.refundInvoice("inv-1", Role.Companion, "companion", "owner")
        Thread.sleep(150)
        assertEquals("Refund denied for role", vm.state.value.note)

        vm.refundInvoice("inv-1", Role.Supervisor, "supervisor")
        Thread.sleep(150)
        assertEquals("Refund completed for inv-1", vm.state.value.note)
    }

    @Test
    fun no_show_reconciliation_updates_overdue_approved_records() = runBlocking {
        db.meetingDao().upsert(
            MeetingEntity(
                id = "m-overdue",
                organizerId = "owner",
                resourceId = "res-1",
                title = "Overdue",
                startTime = 0L,
                endTime = 0L,
                status = MeetingStatus.Approved.name,
                requireCheckIn = true,
                checkInDueAt = fixedClock.now().toEpochMilliseconds() - 1,
            ),
        )
        db.meetingDao().upsert(
            MeetingEntity(
                id = "m-checked",
                organizerId = "owner",
                resourceId = "res-1",
                title = "Checked",
                startTime = 0L,
                endTime = 0L,
                status = MeetingStatus.CheckedIn.name,
                requireCheckIn = true,
                checkInDueAt = fixedClock.now().toEpochMilliseconds() - 1,
            ),
        )

        val service = MeetingNoShowReconciliationService(db.meetingDao(), fixedClock)
        val changed = service.reconcileOverdueApprovedMeetings()

        assertEquals(1, changed)
        assertEquals(MeetingStatus.NoShow.name, db.meetingDao().getById("m-overdue")?.status)
        assertEquals(MeetingStatus.CheckedIn.name, db.meetingDao().getById("m-checked")?.status)
    }
}
