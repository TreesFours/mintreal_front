package com.example.mistreal_mini.ui.settings.components

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MultiLinkDialog(
    onDismiss: () -> Unit,
    onAddBank: (String, String, String?) -> Unit,
    onBusinessHub: () -> Unit,
    onConnectSocials: () -> Unit
) {
    var isSocialsExpanded by remember { mutableStateOf(false) }
    var showAddBankForm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DEPLOY NEW PROTOCOL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 1. Socials (Dropdown)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onConnectSocials() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Connect Social Channels", fontWeight = FontWeight.Bold)
                    }
                }

                // 2. Bank Link
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showAddBankForm = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalance, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Add Financial Portal", fontWeight = FontWeight.Bold)
                    }
                }

                // 3. Website (Future)
                Card(
                    modifier = Modifier.fillMaxWidth().alpha(0.5f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("External Link (Locked)", fontWeight = FontWeight.Bold)
                    }
                }

                // 4. Business Hub
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onBusinessHub() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BusinessCenter, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Mistreal Business Hub", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abort") } }
    )

    if (showAddBankForm) {
        AddBankFormDialog(
            onDismiss = { showAddBankForm = false },
            onSave = { name, url, pkg -> 
                onAddBank(name, url, pkg)
                showAddBankForm = false
            }
        )
    }
}

@Composable
fun AddBankFormDialog(onDismiss: () -> Unit, onSave: (String, String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Financial Portal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Bank Name") })
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Website URL") })
                OutlinedTextField(value = pkg, onValueChange = { pkg = it }, label = { Text("App Package ID (Optional)") })
                Text("App will close immediately after opening bank link for security.", fontSize = 10.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, url, if(pkg.isBlank()) null else pkg) }, enabled = name.isNotBlank() && url.isNotBlank()) {
                Text("SECURE LINK")
            }
        }
    )
}
