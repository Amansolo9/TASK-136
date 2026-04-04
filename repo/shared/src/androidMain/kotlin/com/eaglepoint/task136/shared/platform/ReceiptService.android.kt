package com.eaglepoint.task136.shared.platform

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

actual class ReceiptService {
    actual suspend fun generateAndSharePdf(data: ReceiptData) {
        try {
            val context = AndroidPlatformContext.require()

            val file = withContext(Dispatchers.IO) {
                val document = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = document.startPage(pageInfo)

                val paint = Paint().apply { textSize = 14f }
                var y = 48f
                page.canvas.drawText("Receipt #${data.receiptId}", 36f, y, paint)
                y += 28f
                page.canvas.drawText("Customer: ${data.customerName}", 36f, y, paint)
                y += 28f

                data.lineItems.forEach { item ->
                    page.canvas.drawText("${item.label}: $${"%.2f".format(item.amount)}", 36f, y, paint)
                    y += 24f
                }

                y += 16f
                page.canvas.drawText("Total: $${"%.2f".format(data.total)}", 36f, y, paint)

                document.finishPage(page)

                val file = File(context.cacheDir, "receipt-${data.receiptId}.pdf")
                FileOutputStream(file).use { output -> document.writeTo(output) }
                document.close()
                file
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Receipt ${data.receiptId}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share receipt").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            com.eaglepoint.task136.shared.logging.AppLogger.w("ReceiptService", "Error generating PDF: ${e.message}")
        }
    }
}

actual fun createReceiptService(): ReceiptService = ReceiptService()
