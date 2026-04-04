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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eaglepoint.task136.shared.db.ResourceEntity
import com.eaglepoint.task136.shared.viewmodel.ResourceListViewModel

private val Purple = Color(0xFF6C5CE7)
private val Green = Color(0xFF00B894)
private val Coral = Color(0xFFFF7675)

@Composable
fun AdminScreen(
    resourceListViewModel: ResourceListViewModel,
    onBack: () -> Unit,
    onActivity: () -> Unit,
) {
    val state by resourceListViewModel.state.collectAsState()

    var newName by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("Operations") }
    var newUnits by remember { mutableStateOf("10") }
    var newPrice by remember { mutableStateOf("9.99") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Coral, Coral.copy(alpha = 0.85f))))
                .padding(horizontal = 20.dp, vertical = 16.dp).padding(top = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                    Text("Admin Panel", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text("Manage Resources", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Add Resource", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newCategory,
                            onValueChange = { newCategory = it },
                            label = { Text("Category") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newUnits,
                                onValueChange = { newUnits = it },
                                label = { Text("Units") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            OutlinedTextField(
                                value = newPrice,
                                onValueChange = { newPrice = it },
                                label = { Text("Price") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = {
                                if (newName.isBlank()) {
                                    statusMessage = "Name is required"
                                    return@FilledTonalButton
                                }
                                val units = newUnits.toIntOrNull() ?: 0
                                val price = newPrice.toDoubleOrNull() ?: 0.0
                                resourceListViewModel.addResource(
                                    name = newName.trim(),
                                    category = newCategory.trim(),
                                    availableUnits = units,
                                    unitPrice = price,
                                )
                                statusMessage = "Resource '${newName.trim()}' added"
                                newName = ""
                                onActivity()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Green.copy(alpha = 0.12f),
                                contentColor = Green,
                            ),
                        ) {
                            Text("Add Resource")
                        }
                        if (statusMessage != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(statusMessage.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Purple)
                        }
                    }
                }
            }

            item {
                Text(
                    "Resources (${state.resources.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            items(state.resources.take(50), key = { it.id }) { resource ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(resource.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${resource.category} - ${resource.availableUnits} units - $${resource.unitPrice}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                resourceListViewModel.deleteResource(resource.id)
                                statusMessage = "Deleted ${resource.name}"
                                onActivity()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Coral.copy(alpha = 0.12f),
                                contentColor = Coral,
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("Delete", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
