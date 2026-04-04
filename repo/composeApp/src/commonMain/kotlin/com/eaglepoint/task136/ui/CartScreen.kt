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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
private val Amber = Color(0xFFFDAA48)
private val Blue = Color(0xFF74B9FF)
private val Coral = Color(0xFFFF7675)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartScreen(
    orderFinanceViewModel: OrderFinanceViewModel,
    roleLabel: String,
    actorId: String,
    delegateForUserId: String? = null,
    onBack: () -> Unit,
    onActivity: () -> Unit,
) {
    val financeState by orderFinanceViewModel.state.collectAsState()
    val actorRole = Role.entries.firstOrNull { it.name == roleLabel } ?: Role.Viewer
    val canManage = roleLabel != "Viewer"

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Amber, Amber.copy(alpha = 0.85f))))
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
                    Text("Shopping Cart", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text("${financeState.cart.size} items", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(financeState.cart, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.label, style = MaterialTheme.typography.titleMedium)
                            Text("Qty: ${item.quantity}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("$${"%,.2f".format(item.unitPrice * item.quantity)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Purple)
                    }
                }
            }

            if (financeState.cart.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Purple.copy(alpha = 0.06f))) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total", style = MaterialTheme.typography.titleMedium)
                            Text("$${"%,.2f".format(financeState.cart.sumOf { it.unitPrice * it.quantity })}",
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Purple)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        orderFinanceViewModel.addCartItem(
                            role = actorRole,
                            actorId = actorId,
                            delegateForUserId = delegateForUserId,
                            resourceId = "res-1",
                            label = "Service Package ${financeState.cart.size + 1}",
                            quantity = 1,
                            unitPrice = 49.99,
                        )
                        onActivity()
                    }, enabled = canManage,
                        shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Green.copy(alpha = 0.12f), contentColor = Green)) {
                        Text("Add Item", fontSize = 12.sp)
                    }
                    FilledTonalButton(onClick = { orderFinanceViewModel.mergeFirstTwoItems(actorRole, actorId, delegateForUserId); onActivity() }, enabled = canManage,
                        shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Blue.copy(alpha = 0.12f), contentColor = Blue)) {
                        Text("Merge", fontSize = 12.sp)
                    }
                    FilledTonalButton(onClick = { orderFinanceViewModel.splitFirstItem(actorRole, actorId, delegateForUserId); onActivity() }, enabled = canManage,
                        shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Amber.copy(alpha = 0.12f), contentColor = Amber)) {
                        Text("Split", fontSize = 12.sp)
                    }
                    FilledTonalButton(onClick = { orderFinanceViewModel.generateInvoice(actorRole, actorId, delegateForUserId); onActivity() }, enabled = canManage && financeState.cart.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Purple.copy(alpha = 0.12f), contentColor = Purple)) {
                        Text("Checkout", fontSize = 12.sp)
                    }
                }
            }

            if (financeState.note != null) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(financeState.note.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Coral)
                }
            }
        }
    }
}
