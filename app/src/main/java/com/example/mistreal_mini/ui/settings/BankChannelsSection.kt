package com.example.mistreal_mini.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mistreal_mini.data.api.BankChannel

/**
 * Deep-links into a bank's own official WhatsApp/Facebook chat — same
 * mechanism as the existing Facebook Messenger call button elsewhere in the
 * app. This never touches payments, credentials, or account data; whatever
 * happens after the user leaves this app happens entirely inside WhatsApp/
 * Facebook and the bank's own systems.
 */
@Composable
fun BankChannelsSection(viewModel: SettingsViewModel) {
    val banks by viewModel.bankChannels.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.fetchBankChannels() }

    SettingsSection(title = "CONTACT YOUR BANK", icon = Icons.Default.AccountBalance) {
        Text(
            "Chat with your bank's official WhatsApp or Facebook — Mistreal doesn't process payments or see your messages, this just opens their real channel.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (banks.isEmpty()) {
            Text("No banks on file yet for your area.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            banks.forEach { bank ->
                BankChannelRow(bank) { intent -> context.startActivity(intent) }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BankChannelRow(bank: BankChannel, onLaunch: (Intent) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(bank.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))

            if (bank.whatsapp == null && bank.facebook == null) {
                Text("No channel on file", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }

            bank.whatsapp?.let { wa ->
                TextButton(onClick = {
                    val text = Uri.encode(wa.prefilledMessage ?: "")
                    onLaunch(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${wa.number}?text=$text")))
                }) {
                    Icon(Icons.Default.Chat, null, tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("WhatsApp")
                }
            }

            bank.facebook?.let { fb ->
                TextButton(onClick = {
                    val nativeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("fb-messenger://user-thread/${fb.pageId}"))
                    try {
                        onLaunch(nativeIntent)
                    } catch (e: Exception) {
                        onLaunch(Intent(Intent.ACTION_VIEW, Uri.parse("https://m.me/${fb.pageId}")))
                    }
                }) {
                    Icon(Icons.Default.Chat, null, tint = Color(0xFF0084FF), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Messenger")
                }
            }
        }
    }
}
