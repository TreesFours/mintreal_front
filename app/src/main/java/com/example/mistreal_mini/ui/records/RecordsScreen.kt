package com.example.mistreal_mini.ui.records

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mistreal_mini.data.model.ChatMessage
import com.example.mistreal_mini.ui.chat.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    onBack: () -> Unit,
    onTrendClick: (String) -> Unit = {},
    chatViewModel: ChatViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("History", "Scribe Notes")

    // 🛡️ Fix: Fetch ALL trends and main messages for the history view
    val allMessages = chatViewModel.messages.reversed()
    val uniqueTrends = chatViewModel.uniqueTrends
    val scribeNotes = allMessages.filter { it.type == "scribe" }

    var editingNote by remember { mutableStateOf<ChatMessage?>(null) }
    var editText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Intelligence History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (selectedTab == 0) {
                RecordList(
                    mainMessages = emptyList(), // 🛡️ Main chat never in history
                    trends = uniqueTrends,
                    onTrendClick = onTrendClick,
                    onDeleteTrend = { chatViewModel.deleteTrend(it) },
                    onDeleteMessage = { chatViewModel.deleteMessage(it) }
                )
            } else {
                RecordList(
                    mainMessages = scribeNotes,
                    trends = emptyList(),
                    onTrendClick = {},
                    onDeleteTrend = {},
                    onDeleteMessage = { chatViewModel.deleteMessage(it) },
                    onEditMessage = { editingNote = it; editText = it.content }
                )
            }
        }

        editingNote?.let { note ->
            AlertDialog(
                onDismissRequest = { editingNote = null },
                title = { Text("Edit Scribe Note") },
                text = {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        chatViewModel.updateNote(note, editText)
                        editingNote = null
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { editingNote = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordList(
    mainMessages: List<ChatMessage>,
    trends: List<ChatMessage>,
    onTrendClick: (String) -> Unit,
    onDeleteTrend: (String) -> Unit,
    onDeleteMessage: (ChatMessage) -> Unit,
    onEditMessage: ((ChatMessage) -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    
    if (mainMessages.isEmpty() && trends.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No intelligence records found", color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // --- TRENDS SECTION ---
            if (trends.isNotEmpty()) {
                item {
                    Text(
                        "ACTIVE TRENDS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                items(trends) { trend ->
                    val title = trend.trendTitle ?: "Unnamed Trend"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { onTrendClick(title) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(trend.content, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray)
                            }
                            IconButton(onClick = { onDeleteTrend(title) }) {
                                Icon(Icons.Default.Delete, "Delete Trend", tint = Color.Red.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }

            // --- MAIN HISTORY SECTION ---
            if (mainMessages.isNotEmpty()) {
                item {
                    Text(
                        "MAIN CONVERSATIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
                    )
                }
                items(mainMessages) { msg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { clipboardManager.setText(AnnotatedString(msg.content)) }
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (msg.role == "user") Icons.Default.Person else Icons.Default.Psychology,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (msg.role == "user") Color.Gray else MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (msg.role == "user") "You" else msg.provider.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (onEditMessage != null) {
                                    IconButton(onClick = { onEditMessage(msg) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(14.dp), tint = Color.Gray)
                                    }
                                }
                                IconButton(onClick = { onDeleteMessage(msg) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(14.dp), tint = Color.Gray)
                                }
                            }
                            Text(
                                text = msg.content,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
