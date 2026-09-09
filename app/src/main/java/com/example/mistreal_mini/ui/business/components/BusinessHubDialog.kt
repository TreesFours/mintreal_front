package com.example.mistreal_mini.ui.business.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mistreal_mini.ui.business.BusinessViewModel

@Composable
fun BusinessHubDialog(
    onDismiss: () -> Unit,
    onNavigateToDiscovery: () -> Unit,
    onNavigateToSellerCommand: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "BUSINESS COMMAND CENTER",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Select your mission profile for the local sector.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                
                // BUYER MODE
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToDiscovery() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Discovery Mode", fontWeight = FontWeight.Bold)
                            Text("Find verified local services & items.", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }

                // SELLER MODE
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToSellerCommand() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storefront, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Tactical Shopfront", fontWeight = FontWeight.Bold)
                            Text("Register or manage your business intel.", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
