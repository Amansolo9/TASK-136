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
import com.eaglepoint.task136.shared.viewmodel.MeetingWorkflowViewModel

private val Green = Color(0xFF00B894)
private val Purple = Color(0xFF6C5CE7)
private val Blue = Color(0xFF74B9FF)
private val Coral = Color(0xFFFF7675)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeetingDetailScreen(
    meetingId: String,
    meetingWorkflowViewModel: MeetingWorkflowViewModel,
    roleLabel: String,
    actorId: String,
    onBack: () -> Unit,
    onActivity: () -> Unit,
) {
    val meetingState by meetingWorkflowViewModel.state.collectAsState()
    val actorRole = Role.entries.firstOrNull { it.name == roleLabel } ?: Role.Viewer
    val canManage = roleLabel != "Viewer"
    val canApprove = roleLabel == "Supervisor" || roleLabel == "Admin"

    LaunchedEffect(meetingId, roleLabel, actorId) {
        meetingWorkflowViewModel.loadMeetingDetail(meetingId, actorRole, actorId)
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
                    Text("Meeting", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(meetingId, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
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
                        Text("${meetingState.status}", style = MaterialTheme.typography.titleLarge, color = Green)
                        if (meetingState.note != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(meetingState.note.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (meetingState.agenda.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Agenda", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(meetingState.agenda, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (meetingState.attendees.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Text("Attendees", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
                    Spacer(Modifier.height(4.dp))
                }
                items(meetingState.attendees, key = { it.id }) { att ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(att.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(att.rsvp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { meetingWorkflowViewModel.addAttendee("User-${(1..99).random()}", actorRole, actorId); onActivity() },
                        enabled = canManage, shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Blue.copy(alpha = 0.12f), contentColor = Blue)) {
                        Text("Add Attendee", fontSize = 12.sp)
                    }
                    FilledTonalButton(onClick = { meetingWorkflowViewModel.approve(actorRole); onActivity() },
                        enabled = canApprove, shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Purple.copy(alpha = 0.12f), contentColor = Purple)) {
                        Text("Approve", fontSize = 12.sp)
                    }
                    FilledTonalButton(onClick = { meetingWorkflowViewModel.deny(actorRole); onActivity() },
                        enabled = canApprove, shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Coral.copy(alpha = 0.12f), contentColor = Coral)) {
                        Text("Deny", fontSize = 12.sp)
                    }
                    FilledTonalButton(onClick = { meetingWorkflowViewModel.checkIn(actorRole); onActivity() },
                        enabled = canManage, shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Green.copy(alpha = 0.12f), contentColor = Green)) {
                        Text("Check-in", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
