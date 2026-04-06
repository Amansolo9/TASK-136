package com.eaglepoint.task136.shared.platform

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.minutes

interface NotificationGateway {
    suspend fun scheduleInvoiceReady(invoiceId: String, total: Double)
    suspend fun scheduleMeetingNotification(meetingId: String, message: String)
    suspend fun scheduleOrderReminder(orderId: String, message: String)
}

interface ReceiptGateway {
    suspend fun shareReceipt(invoiceId: String, customerName: String, lineItems: List<ReceiptLineItem>, total: Double)
}

class PlatformNotificationGateway(
    private val scheduler: NotificationScheduler,
    private val clock: Clock,
    private val timeZone: TimeZone,
) : NotificationGateway {
    override suspend fun scheduleInvoiceReady(invoiceId: String, total: Double) {
        scheduler.scheduleWithQuietHours(
            id = "invoice-$invoiceId",
            title = "Invoice ready",
            body = "$invoiceId total $${"%.2f".format(total)}",
            at = clock.now().plus(2.minutes),
            timeZone = timeZone,
        )
    }

    override suspend fun scheduleMeetingNotification(meetingId: String, message: String) {
        scheduler.scheduleWithQuietHours(
            id = "meeting-$meetingId",
            title = "Meeting update",
            body = message,
            at = clock.now().plus(1.minutes),
            timeZone = timeZone,
        )
    }

    override suspend fun scheduleOrderReminder(orderId: String, message: String) {
        scheduler.scheduleWithQuietHours(
            id = "order-reminder-$orderId",
            title = "Order reminder",
            body = message,
            at = clock.now().plus(5.minutes),
            timeZone = timeZone,
        )
    }
}

class PlatformReceiptGateway(
    private val receiptService: ReceiptService,
) : ReceiptGateway {
    override suspend fun shareReceipt(invoiceId: String, customerName: String, lineItems: List<ReceiptLineItem>, total: Double) {
        receiptService.generateAndSharePdf(
            ReceiptData(
                receiptId = invoiceId,
                customerName = customerName,
                lineItems = lineItems,
                total = total,
            ),
        )
    }
}
