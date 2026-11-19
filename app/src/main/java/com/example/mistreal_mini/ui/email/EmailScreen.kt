package com.example.mistreal_mini.ui.email

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mistreal_mini.data.api.EmailInboxMessage

private data class EmailProviderPreset(
    val label: String,
    val imapHost: String, val imapPort: Int, val imapSecure: Boolean,
    val smtpHost: String, val smtpPort: Int, val smtpSecure: Boolean,
    // Most providers now refuse the account's real password for IMAP/SMTP
    // entirely and require a separate app-specific password instead — null
    // means "just use your normal password," shown for Custom only.
    val appPasswordHint: String? = null
)

private val EMAIL_PROVIDER_PRESETS = listOf(
    EmailProviderPreset(
        "Gmail", "imap.gmail.com", 993, true, "smtp.gmail.com", 465, true,
        appPasswordHint = "Gmail requires 2-Step Verification turned on before it'll let you create an app password. Enable 2-Step Verification in your Google Account, then generate an app password under Security → App passwords, and use that here — not your normal Gmail password."
    ),
    EmailProviderPreset(
        "Outlook / Office 365", "outlook.office365.com", 993, true, "smtp.office365.com", 587, false,
        appPasswordHint = "Most Microsoft accounts need 2-step verification enabled first, then an app password generated under Security settings. Use that here, not your normal password."
    ),
    EmailProviderPreset(
        "Yahoo", "imap.mail.yahoo.com", 993, true, "smtp.mail.yahoo.com", 465, true,
        appPasswordHint = "Yahoo requires an app password, generated under Account Security → Generate app password. Use that here, not your normal Yahoo password."
    ),
    EmailProviderPreset(
        "iCloud", "imap.mail.me.com", 993, true, "smtp.mail.me.com", 587, false,
        appPasswordHint = "iCloud requires an app-specific password, generated at appleid.apple.com under Sign-In and Security. Use that here, not your normal Apple ID password."
    ),
    EmailProviderPreset("Custom", "", 993, true, "", 587, false)
)

/**
 * BYOK personal email — connect your own account (app password, no OAuth)
 * and send/receive as yourself. Distinct from the existing shared-sender
 * compose-only Email feature elsewhere in the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailScreen(
    onBack: () -> Unit,
    onCompose: () -> Unit,
    onOpenMessage: (Int) -> Unit,
    viewModel: EmailAccountViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()
    val isLoadingInbox by viewModel.isLoadingInbox.collectAsStateWithLifecycle()
    val activeAccount = accounts.firstOrNull { it.isActive }
    var showAddAccount by remember { mutableStateOf(false) }
    var showAccountSwitcher by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(activeAccount?.emailAddress ?: "Email") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    if (activeAccount != null) {
                        IconButton(onClick = { viewModel.loadInbox() }) { Icon(Icons.Default.Refresh, "Refresh") }
                        IconButton(onClick = { showAccountSwitcher = true }) { Icon(Icons.Default.Person, "Accounts") }
                    }
                }
            )
        },
        floatingActionButton = {
            if (activeAccount != null) {
                FloatingActionButton(onClick = onCompose) { Icon(Icons.Default.Edit, "Compose") }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (activeAccount == null) {
                EmailAccountSetup(
                    viewModel = viewModel,
                    hasOtherAccounts = accounts.isNotEmpty(),
                    onPickExisting = { showAccountSwitcher = true }
                )
            } else if (isLoadingInbox && inbox.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (inbox.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Inbox is empty.", color = Color.Gray)
                }
            } else {
                LazyColumn {
                    items(inbox) { message -> InboxRow(message, onClick = { onOpenMessage(message.uid) }) }
                }
            }
        }
    }

    if (showAccountSwitcher) {
        AlertDialog(
            onDismissRequest = { showAccountSwitcher = false },
            title = { Text("Email Accounts") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    accounts.forEach { account ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = account.isActive, onClick = {
                                viewModel.switchAccount(account.id)
                                showAccountSwitcher = false
                            })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(account.label, fontWeight = FontWeight.Bold)
                                Text(account.emailAddress, fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { viewModel.removeAccount(account.id) }) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.6f))
                            }
                        }
                    }
                    TextButton(onClick = { showAccountSwitcher = false; showAddAccount = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Another Account")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAccountSwitcher = false }) { Text("Close") } }
        )
    }

    if (showAddAccount) {
        AddAccountDialog(viewModel = viewModel, onDismiss = { showAddAccount = false })
    }
}

@Composable
private fun InboxRow(message: EmailInboxMessage, onClick: () -> Unit) {
    androidx.compose.material3.ListItem(
        headlineContent = {
            Text(message.subject ?: "(no subject)", fontWeight = if (message.seen) FontWeight.Normal else FontWeight.Bold)
        },
        supportingContent = { Text(message.from ?: "Unknown sender") },
        trailingContent = { message.date?.let { Text(it.take(10), fontSize = 11.sp, color = Color.Gray) } },
        leadingContent = {
            Icon(
                Icons.Default.Email, null,
                tint = if (message.seen) Color.Gray else MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}

@Composable
private fun EmailAccountSetup(viewModel: EmailAccountViewModel, hasOtherAccounts: Boolean, onPickExisting: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Email, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Connect Your Email", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Send and receive as yourself using an app password — your credentials go directly to your provider, never through a third party.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (hasOtherAccounts) {
            OutlinedButton(onClick = onPickExisting, modifier = Modifier.fillMaxWidth()) {
                Text("Choose a Connected Account")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        AccountConnectForm(viewModel)
    }
}

@Composable
private fun AddAccountDialog(viewModel: EmailAccountViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Email Account") },
        text = { AccountConnectForm(viewModel, onConnected = onDismiss) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AccountConnectForm(viewModel: EmailAccountViewModel, onConnected: () -> Unit = {}) {
    var selectedPreset by remember { mutableStateOf(EMAIL_PROVIDER_PRESETS[0]) }
    var presetMenuExpanded by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var emailAddress by remember { mutableStateOf("") }
    var imapHost by remember { mutableStateOf(selectedPreset.imapHost) }
    var imapPort by remember { mutableStateOf(selectedPreset.imapPort.toString()) }
    var smtpHost by remember { mutableStateOf(selectedPreset.smtpHost) }
    var smtpPort by remember { mutableStateOf(selectedPreset.smtpPort.toString()) }
    var password by remember { mutableStateOf("") }
    val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()
    val connectError by viewModel.connectError.collectAsStateWithLifecycle()

    var connectedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(isConnecting, connectError) {
        if (connectedOnce && !isConnecting && connectError == null) onConnected()
    }

    val canConnect = !isConnecting && emailAddress.isNotBlank() && password.isNotBlank() && imapHost.isNotBlank() && smtpHost.isNotBlank()
    // Shared by the Connect button and the keyboard Done action on whichever
    // field is last visible (password for a preset, SMTP Port for Custom).
    val onConnect: () -> Unit = {
        connectedOnce = true
        viewModel.connectAccount(
            label = label.ifBlank { selectedPreset.label },
            emailAddress = emailAddress,
            imapHost = imapHost, imapPort = imapPort.toIntOrNull() ?: selectedPreset.imapPort, imapSecure = selectedPreset.imapSecure,
            smtpHost = smtpHost, smtpPort = smtpPort.toIntOrNull() ?: selectedPreset.smtpPort, smtpSecure = selectedPreset.smtpSecure,
            username = emailAddress, password = password
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box {
            OutlinedButton(onClick = { presetMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedPreset.label)
            }
            DropdownMenu(expanded = presetMenuExpanded, onDismissRequest = { presetMenuExpanded = false }) {
                EMAIL_PROVIDER_PRESETS.forEach { preset ->
                    DropdownMenuItem(text = { Text(preset.label) }, onClick = {
                        selectedPreset = preset
                        imapHost = preset.imapHost
                        imapPort = preset.imapPort.toString()
                        smtpHost = preset.smtpHost
                        smtpPort = preset.smtpPort.toString()
                        presetMenuExpanded = false
                    })
                }
            }
        }

        OutlinedTextField(
            value = label, onValueChange = { label = it }, label = { Text("Label (e.g. \"Personal\")") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        OutlinedTextField(
            value = emailAddress, onValueChange = { emailAddress = it }, label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        selectedPreset.appPasswordHint?.let { hint ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }

        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("App Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            singleLine = true,
            // Password is the last field for a preset provider (Done submits);
            // for Custom there are host/port fields still to fill (Next).
            keyboardOptions = KeyboardOptions(imeAction = if (selectedPreset.label == "Custom") ImeAction.Next else ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canConnect) onConnect() })
        )

        if (selectedPreset.label == "Custom") {
            OutlinedTextField(
                value = imapHost, onValueChange = { imapHost = it }, label = { Text("IMAP Host") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = imapPort, onValueChange = { imapPort = it }, label = { Text("IMAP Port") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = smtpHost, onValueChange = { smtpHost = it }, label = { Text("SMTP Host") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = smtpPort, onValueChange = { smtpPort = it }, label = { Text("SMTP Port") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canConnect) onConnect() })
            )
        }

        if (connectError != null) {
            Text(connectError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        Button(
            onClick = onConnect,
            enabled = canConnect,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isConnecting) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            else Text("Connect")
        }
    }
}
