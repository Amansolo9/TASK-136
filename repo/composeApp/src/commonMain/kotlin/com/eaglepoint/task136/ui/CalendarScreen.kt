package com.eaglepoint.task136.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eaglepoint.task136.shared.db.ResourceEntity
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.viewmodel.OrderWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.ResourceListViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val Purple = Color(0xFF6C5CE7)
private val Green = Color(0xFF00B894)
private val Blue = Color(0xFF74B9FF)
private val Coral = Color(0xFFFF7675)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(
    orderWorkflowViewModel: OrderWorkflowViewModel,
    resourceListViewModel: ResourceListViewModel,
    roleLabel: String,
    actorId: String,
    onBack: () -> Unit,
    onActivity: () -> Unit,
) {
    val orderState by orderWorkflowViewModel.state.collectAsState()
    val resourceState by resourceListViewModel.state.collectAsState()
    val actorRole = Role.entries.firstOrNull { it.name == roleLabel } ?: Role.Viewer
    val canManage = roleLabel != "Viewer"
    var selectedResource by remember { mutableStateOf<ResourceEntity?>(null) }

    LaunchedEffect(resourceState.resources) {
        if (selectedResource == null && resourceState.resources.isNotEmpty()) {
            selectedResource = resourceState.resources.first()
        }
    }

    val today = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    var selectedDate by remember { mutableStateOf(today) }
    val monthStart = remember(selectedDate) {
        LocalDate(selectedDate.year, selectedDate.month, 1)
    }

    LaunchedEffect(selectedDate) {
        orderWorkflowViewModel.suggestSlots(actorRole)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Blue, Blue.copy(alpha = 0.85f))))
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .padding(top = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                ) {
                    Text("Back", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "Resource Calendar",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                    )
                    Text(
                        "${selectedDate.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${selectedDate.year}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }

        // Month navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = {
                    val prevMonth = monthStart.monthNumber - 1
                    selectedDate = if (prevMonth < 1) {
                        LocalDate(monthStart.year - 1, 12, 1)
                    } else {
                        LocalDate(monthStart.year, prevMonth, 1)
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Blue.copy(alpha = 0.12f), contentColor = Blue,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Prev")
            }
            Text(
                "${monthStart.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${monthStart.year}",
                style = MaterialTheme.typography.titleMedium,
            )
            FilledTonalButton(
                onClick = {
                    val nextMonth = monthStart.monthNumber + 1
                    selectedDate = if (nextMonth > 12) {
                        LocalDate(monthStart.year + 1, 1, 1)
                    } else {
                        LocalDate(monthStart.year, nextMonth, 1)
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Blue.copy(alpha = 0.12f), contentColor = Blue,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Next")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            // Day-of-week headers
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                        Text(
                            text = day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Calendar grid
            item {
                val firstDayOffset = (monthStart.dayOfWeek.ordinal + 7 - DayOfWeek.MONDAY.ordinal) % 7
                val daysInMonth = when (monthStart.month) {
                    kotlinx.datetime.Month.FEBRUARY -> if (monthStart.year % 4 == 0) 29 else 28
                    kotlinx.datetime.Month.APRIL, kotlinx.datetime.Month.JUNE,
                    kotlinx.datetime.Month.SEPTEMBER, kotlinx.datetime.Month.NOVEMBER -> 30
                    else -> 31
                }

                val cells = (0 until firstDayOffset).map<Int, Int?> { null } +
                    (1..daysInMonth).map { it }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxWidth().height(((cells.size / 7 + 1) * 48).dp),
                    userScrollEnabled = false,
                ) {
                    items(cells) { day ->
                        if (day == null) {
                            Box(Modifier.aspectRatio(1f))
                        } else {
                            val date = LocalDate(monthStart.year, monthStart.month, day)
                            val isSelected = date == selectedDate
                            val isToday = date == today

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> Purple
                                            isToday -> Purple.copy(alpha = 0.1f)
                                            else -> Color.Transparent
                                        },
                                    )
                                    .clickable { selectedDate = date },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "$day",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        isSelected -> Color.White
                                        isToday -> Purple
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }

            // Resource selector
            item {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Select Resource", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        val displayResources = resourceState.resources.take(20)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            displayResources.forEach { resource ->
                                val isSelected = selectedResource?.id == resource.id
                                FilledTonalButton(
                                    onClick = { selectedResource = resource },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isSelected) Purple else Purple.copy(alpha = 0.08f),
                                        contentColor = if (isSelected) Color.White else Purple,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(resource.name, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Selected date details
            item {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Available Slots - ${selectedDate}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(12.dp))

                        if (orderState.suggestedSlots.isEmpty()) {
                            Text(
                                "No slots loaded. Tap 'Find Slots' to search.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            orderState.suggestedSlots.forEach { slot ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Green.copy(alpha = 0.08f),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Green),
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = slot,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (canManage) {
                                            FilledTonalButton(
                                                onClick = {
                                                    val resId = selectedResource?.id ?: return@FilledTonalButton
                                                    orderWorkflowViewModel.createPendingTender(
                                                        role = actorRole,
                                                        actorId = actorId,
                                                        resourceId = resId,
                                                        quantity = 1,
                                                    )
                                                    onActivity()
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = Purple.copy(alpha = 0.12f),
                                                    contentColor = Purple,
                                                ),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            ) {
                                                Text("Book", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = {
                                orderWorkflowViewModel.suggestSlots(actorRole)
                                onActivity()
                            },
                            enabled = canManage,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Blue.copy(alpha = 0.12f),
                                contentColor = Blue,
                            ),
                        ) {
                            Text("Find Slots")
                        }
                    }
                }
            }

            // Booking status
            if (orderState.lastOrderId != null) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Purple.copy(alpha = 0.06f)),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Last Booking",
                                style = MaterialTheme.typography.titleMedium,
                                color = Purple,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${orderState.lastOrderId} - ${orderState.lastOrderState.orEmpty()}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
