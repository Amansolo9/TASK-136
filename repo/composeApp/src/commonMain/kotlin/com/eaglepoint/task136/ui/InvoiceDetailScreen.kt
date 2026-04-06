package com.eaglepoint.task136.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.viewmodel.OrderFinanceViewModel

private val Purple = Color(0xFF6C5CE7)
private val Green = Color(0xFF00B894)
private val Coral = Color(0xFFFF7675)
private val Amber = Color(0xFFFDAA48)

@Composable
fun InvoiceDetailScreen(
    invoiceId: String,
    orderFinanceViewModel: OrderFinanceViewModel,
    roleLabel: String,
    actorId: String,
    onBack: () -> Unit,
    onActivity: () -> Unit,
) {
    val financeState by orderFinanceViewModel.state.collectAsState()
    val actorRole = Role.entries.firstOrNull { it.name == roleLabel } ?: Role.Viewer
    val invoice = financeState.invoices.firstOrNull { it.id == invoiceId }

    LaunchedEffect(invoiceId) {
        orderFinanceViewModel.loadInvoiceById(invoiceId, actorRole, actorId)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Green, Green.copy(alpha = 0.85f))))
                .padding(horizontal = 20.dp, vertical = 16.dp).padding(top = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))) {
                    Text("Back", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Invoice", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(invoiceId, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            if (invoice != null) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Subtotal", style = MaterialTheme.typography.bodyMedium)
                                Text("$${"%,.2f".format(invoice.subtotal)}", style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Tax", style = MaterialTheme.typography.bodyMedium)
                                Text("$${"%,.2f".format(invoice.tax)}", style = MaterialTheme.typography.bodyMedium, color = if (invoice.tax > 0) Amber else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(8.dp))
                            Divider()
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("$${"%,.2f".format(invoice.total)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Purple)
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    val isRefunded = financeState.refunds.any { it.contains(invoiceId) }
                    if (isRefunded) {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Coral.copy(alpha = 0.08f))) {
                            Text("Refunded", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium, color = Coral)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { orderFinanceViewModel.refundInvoice(invoiceId, actorRole, actorId); onActivity() },
                            enabled = roleLabel == "Admin" || roleLabel == "Supervisor",
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Coral.copy(alpha = 0.12f), contentColor = Coral),
                        ) {
                            Text("Issue Refund", fontSize = 12.sp)
                        }
                    }
                }
            } else {
                item {
                    Text("Invoice not found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
