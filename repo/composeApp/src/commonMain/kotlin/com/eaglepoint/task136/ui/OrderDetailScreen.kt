package com.eaglepoint.task136.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.viewmodel.OrderWorkflowViewModel

private val Purple = Color(0xFF6C5CE7)
private val Green = Color(0xFF00B894)
private val Blue = Color(0xFF74B9FF)
private val Coral = Color(0xFFFF7675)
private val Amber = Color(0xFFFDAA48)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderDetailScreen(
    orderId: String,
    orderWorkflowViewModel: OrderWorkflowViewModel,
    roleLabel: String,
    actorId: String,
    onBack: () -> Unit,
    onActivity: () -> Unit,
) {
    val orderState by orderWorkflowViewModel.state.collectAsState()
    val actorRole = Role.entries.firstOrNull { it.name == roleLabel } ?: Role.Viewer
    val canManage = roleLabel != "Viewer"

    LaunchedEffect(orderId) {
        orderWorkflowViewModel.loadOrderById(orderId, actorRole, actorId)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Purple, Purple.copy(alpha = 0.85f))))
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
                    Text("Order Detail", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(orderId, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        val displayState = if (orderState.lastOrderId == orderId) {
                            orderState.lastOrderState ?: "Loading..."
                        } else {
                            "Loading..."
                        }
                        Text(displayState, style = MaterialTheme.typography.titleLarge, color = Purple)
                        if (orderState.error != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(orderState.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Coral)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Order ID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(orderId, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Actions", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton("Confirm", Green, canManage) { orderWorkflowViewModel.confirmLastOrder(actorRole); onActivity() }
                            ActionButton("Ship", Blue, canManage) { orderWorkflowViewModel.markAwaitingDelivery(actorRole); onActivity() }
                            ActionButton("Deliver", Green, canManage) { orderWorkflowViewModel.confirmDelivery(actorRole, "signed-$actorId"); onActivity() }
                            ActionButton("Return", Amber, canManage) { orderWorkflowViewModel.requestReturn(actorRole); onActivity() }
                            ActionButton("Exchange", Blue, canManage) { orderWorkflowViewModel.requestExchange(actorRole); onActivity() }
                            ActionButton("Refund", Coral, canManage && roleLabel != "Companion") { orderWorkflowViewModel.requestRefund(actorRole); onActivity() }
                            ActionButton("Split", Amber, canManage) { orderWorkflowViewModel.splitOrder(actorRole); onActivity() }
                            ActionButton("Receipt", Purple, canManage) { onActivity() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = color.copy(alpha = 0.12f), contentColor = color,
            disabledContainerColor = Color.Gray.copy(alpha = 0.08f), disabledContentColor = Color.Gray.copy(alpha = 0.4f)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontSize = 12.sp)
    }
}
