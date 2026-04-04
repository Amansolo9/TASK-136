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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.viewmodel.MeetingWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderFinanceViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.ResourceListViewModel
import com.eaglepoint.task136.ui.navigation.Screen

// ── Color palette for section accents ──────────────────────────────
private val Purple = Color(0xFF6C5CE7)
private val Green = Color(0xFF00B894)
private val Coral = Color(0xFFFF7675)
private val Amber = Color(0xFFFDAA48)
private val Blue = Color(0xFF74B9FF)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResourceListScreen(
    viewModel: ResourceListViewModel,
    orderWorkflowViewModel: OrderWorkflowViewModel,
    meetingWorkflowViewModel: MeetingWorkflowViewModel,
    orderFinanceViewModel: OrderFinanceViewModel,
    roleLabel: String,
    actorId: String,
    delegateForUserId: String? = null,
    onActivity: () -> Unit,
    onLogout: () -> Unit,
    onNavigate: (Screen) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val orderState by orderWorkflowViewModel.state.collectAsState()
    val meetingState by meetingWorkflowViewModel.state.collectAsState()
    val financeState by orderFinanceViewModel.state.collectAsState()
    val actorRole = Role.entries.firstOrNull { it.name == roleLabel } ?: Role.Viewer
    val canManage = roleLabel != "Viewer"

    LaunchedEffect(Unit) {
        viewModel.loadPage(limit = 5000)
        onActivity()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // ── Header ─────────────────────────────────────────────
            item(key = "header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Purple, Purple.copy(alpha = 0.85f)),
                            ),
                        )
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                        .padding(top = 24.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dashboard",
                                style = MaterialTheme.typography.headlineLarge,
                                color = Color.White,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RoleBadge(roleLabel)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = actorId,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onLogout,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        ) {
                            Text("Logout", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // ── Stats bar ──────────────────────────────────────────
            item(key = "stats") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        label = "Resources",
                        value = "${state.resources.size}",
                        accent = Purple,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Cart",
                        value = "${financeState.cart.size}",
                        accent = Green,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Invoices",
                        value = "${financeState.invoices.size}",
                        accent = Amber,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Refunds",
                        value = "${financeState.refunds.size}",
                        accent = Coral,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── Quick navigation ───────────────────────────────────
            item(key = "nav") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { onNavigate(Screen.Calendar) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Blue.copy(alpha = 0.12f),
                            contentColor = Blue,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Calendar", fontSize = 12.sp)
                    }
                    FilledTonalButton(
                        onClick = { onNavigate(Screen.Cart) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Amber.copy(alpha = 0.12f),
                            contentColor = Amber,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cart", fontSize = 12.sp)
                    }
                    if (roleLabel == "Admin") {
                        FilledTonalButton(
                            onClick = { onNavigate(Screen.Admin) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Coral.copy(alpha = 0.12f),
                                contentColor = Coral,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Admin", fontSize = 12.sp)
                        }
                    }
                    if (delegateForUserId != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Amber.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                "Acting for: $delegateForUserId",
                                style = MaterialTheme.typography.labelMedium,
                                color = Amber,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Loading indicator ──────────────────────────────────
            if (state.isLoading) {
                item(key = "loading") {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Purple,
                        trackColor = Purple.copy(alpha = 0.12f),
                    )
                }
            }

            // ── Error banner ──────────────────────────────────────
            if (state.error != null) {
                item(key = "error") {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Coral.copy(alpha = 0.1f)),
                    ) {
                        Text(
                            text = state.error.orEmpty(),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Coral,
                        )
                    }
                }
            }

            // ── Order Workflow ──────────────────────────────────────
            item(key = "orders") {
                SectionCard(
                    title = "Order Workflow",
                    subtitle = orderState.lastOrderId?.let {
                        "$it  -  ${orderState.lastOrderState.orEmpty()}"
                    },
                    accent = Purple,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    if (!canManage) {
                        StatusChip("Read-only access", Coral)
                        Spacer(Modifier.height(8.dp))
                    }

                    Text(
                        "Lifecycle",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ActionChip("Create", Purple, canManage) {
                            val selectedResource = state.resources.firstOrNull()
                            if (selectedResource != null) {
                                orderWorkflowViewModel.createPendingTender(
                                    role = actorRole,
                                    actorId = actorId,
                                    resourceId = selectedResource.id,
                                    quantity = 1,
                                    delegateForUserId = delegateForUserId,
                                )
                            }
                            onActivity()
                        }
                        ActionChip("Confirm", Green, canManage) {
                            orderWorkflowViewModel.confirmLastOrder(actorRole)
                            onActivity()
                        }
                        ActionChip("Ship", Blue, canManage) {
                            orderWorkflowViewModel.markAwaitingDelivery(actorRole)
                            onActivity()
                        }
                        ActionChip("Deliver", Green, canManage) {
                            orderWorkflowViewModel.confirmDelivery(actorRole, "signed-$actorId")
                            onActivity()
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Post-Purchase",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ActionChip("Return", Amber, canManage) {
                            orderWorkflowViewModel.requestReturn(actorRole)
                            onActivity()
                        }
                        ActionChip("Exchange", Blue, canManage) {
                            orderWorkflowViewModel.requestExchange(actorRole)
                            onActivity()
                        }
                        ActionChip("Refund", Coral, canManage) {
                            orderWorkflowViewModel.requestRefund(actorRole)
                            onActivity()
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Scheduling",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    ActionChip("Suggest 3 Slots", Purple, canManage) {
                        orderWorkflowViewModel.suggestSlots(actorRole)
                        onActivity()
                    }

                    if (orderState.suggestedSlots.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        orderState.suggestedSlots.forEachIndexed { i, slot ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Purple),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = slot,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // ── Meeting Lifecycle ───────────────────────────────────
            item(key = "meetings") {
                val canApprove = roleLabel == "Supervisor" || roleLabel == "Admin"
                SectionCard(
                    title = "Meeting Lifecycle",
                    subtitle = "${meetingState.status}${meetingState.note?.let { "  -  $it" } ?: ""}",
                    accent = Green,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    if (meetingState.agenda.isNotBlank()) {
                        Text(
                            "Agenda: ${meetingState.agenda}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    if (meetingState.attendees.isNotEmpty()) {
                        Text(
                            "Attendees: ${meetingState.attendees.joinToString { it.name }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ActionChip("Submit", Green, canManage) {
                            meetingWorkflowViewModel.submitMeeting(
                                organizerId = delegateForUserId ?: actorId,
                                actorId = actorId,
                            )
                            onActivity()
                        }
                        ActionChip("Add Attendee", Blue, canManage) {
                            meetingWorkflowViewModel.addAttendee(
                                "User-${(1..99).random()}",
                                actorRole,
                                actorId,
                                delegateForUserId ?: actorId,
                                delegateForUserId != null,
                            )
                            onActivity()
                        }
                        ActionChip("Approve", Purple, canApprove) {
                            meetingWorkflowViewModel.approve(actorRole)
                            onActivity()
                        }
                        ActionChip("Deny", Coral, canApprove) {
                            meetingWorkflowViewModel.deny(actorRole)
                            onActivity()
                        }
                        ActionChip("Check-in", Blue, canManage) {
                            meetingWorkflowViewModel.checkIn(actorRole)
                            onActivity()
                        }
                        ActionChip("No-show", Coral, canApprove) {
                            meetingWorkflowViewModel.markNoShowIfDue(actorRole)
                            onActivity()
                        }
                    }
                }
            }

            // ── Cart & Invoice ──────────────────────────────────────
            item(key = "finance") {
                SectionCard(
                    title = "Cart & Invoice",
                    subtitle = financeState.note,
                    accent = Amber,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        "Cart Actions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ActionChip("Add Item", Green, canManage) {
                            val selectedResource = state.resources.firstOrNull()
                            if (selectedResource != null) {
                                orderFinanceViewModel.addCartItem(
                                    role = actorRole,
                                    actorId = actorId,
                                    delegateForUserId = delegateForUserId,
                                    resourceId = selectedResource.id,
                                    label = selectedResource.name,
                                    quantity = 1,
                                    unitPrice = selectedResource.unitPrice,
                                )
                            }
                            onActivity()
                        }
                        ActionChip("Merge", Blue, canManage) {
                            orderFinanceViewModel.mergeFirstTwoItems(actorRole, actorId)
                            onActivity()
                        }
                        ActionChip("Split", Amber, canManage) {
                            orderFinanceViewModel.splitFirstItem(actorRole, actorId)
                            onActivity()
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Billing",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ActionChip("Generate Invoice", Purple, canManage) {
                            orderFinanceViewModel.generateInvoice(role = actorRole, actorId = actorId, delegateForUserId = delegateForUserId)
                            onActivity()
                        }
                        ActionChip("Refund Latest", Coral, canManage) {
                            orderFinanceViewModel.refundLatest(role = actorRole, actorId = actorId)
                            onActivity()
                        }
                    }
                }
            }

            // ── Resource catalog header ─────────────────────────────
            item(key = "catalog-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Resource Catalog",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${state.resources.size} items",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Resource cards (RecyclerView + DiffUtil on Android) ─
            item(key = "resource-list") {
                PlatformResourceList(
                    resources = state.resources,
                    roleLabel = roleLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (state.resources.isEmpty()) 100.dp else 600.dp),
                )
            }
        }
    }
}

// ── Reusable components ─────────────────────────────────────────────

@Composable
private fun StatCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String?,
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Accent bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent, accent.copy(alpha = 0.3f)),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = color.copy(alpha = 0.12f),
            contentColor = color,
            disabledContainerColor = Color.Gray.copy(alpha = 0.08f),
            disabledContentColor = Color.Gray.copy(alpha = 0.4f),
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun RoleBadge(role: String) {
    val color = when (role) {
        "Admin" -> Coral
        "Supervisor" -> Amber
        "Operator" -> Green
        else -> Blue
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = role,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}
