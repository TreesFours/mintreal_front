package com.example.mistreal_mini.ui.records

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mistreal_mini.data.local.entity.ScribeNoteEntity
import com.example.mistreal_mini.data.model.ChatMessage
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.records.components.ScribeDetailPopup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    onBack: () -> Unit,
    onTrendClick: (String) -> Unit = {},
    chatViewModel: ChatViewModel = hiltViewModel(),
    scribeViewModel: ScribeViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("History", "Scribe Notes")

    val uniqueTrends = chatViewModel.uniqueTrends
    val scribeNotes by scribeViewModel.scribeNotes.collectAsStateWithLifecycle()

    var selectedScribeNote by remember { mutableStateOf<ScribeNoteEntity?>(null) }

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
                    trends = uniqueTrends,
                    onTrendClick = onTrendClick,
                    onDeleteTrend = { chatViewModel.deleteTrend(it) }
                )
            } else {
                ScribeNoteList(
                    notes = scribeNotes,
                    onNoteClick = { selectedScribeNote = it },
                    onDeleteNote = { scribeViewModel.deleteNote(it) }
                )
            }
        }

        if (selectedScribeNote != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                ScribeDetailPopup(
                    note = selectedScribeNote!!,
                    onClose = { selectedScribeNote = null },
                    onChatAboutNote = { note ->
                        chatViewModel.loadTrend("Discuss: ${note.sourceAuthor ?: "Scribe Note"}")
                        onTrendClick("Discuss: ${note.sourceAuthor ?: "Scribe Note"}")
                        selectedScribeNote = null
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordList(
    trends: List<ChatMessage>,
    onTrendClick: (String) -> Unit,
    onDeleteTrend: (String) -> Unit
) {
    if (trends.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No intelligence records found", color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
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
    }
}

@Composable
fun ScribeNoteList(
    notes: List<ScribeNoteEntity>,
    onNoteClick: (ScribeNoteEntity) -> Unit,
    onDeleteNote: (ScribeNoteEntity) -> Unit
) {
    if (notes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No scribe notes secured", color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(notes) { note ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onNoteClick(note) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DriveFileRenameOutline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "SCRIBE: ${note.sourcePlatform?.uppercase() ?: "ANALYSIS"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDeleteNote(note) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(
                            text = note.aiAnalysis,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
