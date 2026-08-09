package com.example.mistreal_mini.ui.archive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mistreal_mini.data.model.ChatMessage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveHubScreen(
    onBack: () -> Unit,
    chatHistory: List<ChatMessage>,
    onDeleteAll: () -> Unit,
    onDeleteTrend: (String) -> Unit = {},
    onTrendClick: (String, List<ChatMessage>) -> Unit = { _, _ -> }
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    
    // Trend Pop-up State
    var showTrendPopup by remember { mutableStateOf(false) }
    var selectedTrendTitle by remember { mutableStateOf("") }
    var selectedTrendMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }

    // Fetch local files from Documents
    val notes = remember {
        val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
        dir?.listFiles()?.toList() ?: emptyList()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Agent Archive") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onDeleteAll) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Nuke All", tint = Color.Red)
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Scribe Notes") },
                        icon = { Icon(Icons.Default.HistoryEdu, null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Chat Logs") },
                        icon = { Icon(Icons.Default.ChatBubbleOutline, null) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (selectedTab == 0) {
                ScribeNotesList(notes)
            } else {
                ChatLogsList(chatHistory, onDeleteTrend) { title, msgs ->
                    selectedTrendTitle = title
                    selectedTrendMessages = msgs
                    showTrendPopup = true
                    onTrendClick(title, msgs)
                }
            }
            
            if (showTrendPopup) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    TrendMiniChatPopup(
                        title = selectedTrendTitle,
                        messages = selectedTrendMessages,
                        onClose = { showTrendPopup = false }
                    )
                }
            }
        }
    }
}

@Composable
fun TrendMiniChatPopup(
    title: String,
    messages: List<ChatMessage>,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TrendingUp, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(messages) { msg ->
                    Text("${msg.role.uppercase()}: ${msg.content}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            
            Text("Trend Locked: Continue in Main Chat to update.", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun ScribeNotesList(notes: List<File>) {
    if (notes.isEmpty()) {
        EmptyState("No transcribed notes yet.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            items(notes) { file ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(file.name, fontWeight = FontWeight.Bold)
                            Text("${file.length() / 1024} KB", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatLogsList(
    history: List<ChatMessage>, 
    onDeleteTrend: (String) -> Unit,
    onTrendClick: (String, List<ChatMessage>) -> Unit
) {
    if (history.isEmpty()) {
        EmptyState("Conversation history is clear.")
    } else {
        val trends = history.filter { it.isTrend }
        if (trends.isEmpty()) {
            EmptyState("No trends saved yet.")
            return
        }
        val groupedTrends = trends.groupBy { it.trendTitle ?: "Untitled Strategy" }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            groupedTrends.forEach { (title, messages) ->
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrendClick(title, messages) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = title.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onDeleteTrend(title) }) {
                            Icon(Icons.Default.Delete, "Delete Trend", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
                items(messages.take(1)) { msg ->
                    ChatLogItem(msg)
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.2f)) }
            }
        }
    }
}

@Composable
fun ChatLogItem(message: ChatMessage) {
    val isUser = message.role == "user"
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(if (isUser) "YOU" else message.provider.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(message.content, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Inbox, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.5f))
            Text(message, color = Color.Gray)
        }
    }
}
