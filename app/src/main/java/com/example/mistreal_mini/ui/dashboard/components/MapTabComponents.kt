package com.example.mistreal_mini.ui.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.local.entity.SavedIntelEntity
import com.example.mistreal_mini.ui.dashboard.TacticalMapViewModel

@Composable
fun ExploreTabView(viewModel: TacticalMapViewModel) {
    val results = viewModel.discoveryResults
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tactical leads discovered in this sector.", color = Color.Gray, fontSize = 12.sp)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results) { res ->
                ListItem(
                    headlineContent = { Text(res.name, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    supportingContent = { Text(res.category.uppercase(), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.viewPlace(res.latitude, res.longitude, res.name) }) {
                            Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(18.dp))
                        }
                    },
                    modifier = Modifier.clickable { viewModel.viewPlace(res.latitude, res.longitude, res.name) }
                )
            }
        }
    }
}

@Composable
fun HistoryTabView(viewModel: TacticalMapViewModel) {
    val history by viewModel.locationHistory.collectAsStateWithLifecycle()
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No recent mission deployments.", color = Color.Gray, fontSize = 12.sp)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(history) { entry ->
                ListItem(
                    headlineContent = { Text(entry.cityName.uppercase(), fontWeight = FontWeight.Black, fontSize = 13.sp) },
                    supportingContent = { Text("DEPLOYED: ${java.text.SimpleDateFormat("MMM dd, HH:mm").format(java.util.Date(entry.timestamp))}", fontSize = 9.sp) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.deleteLocationHistoryEntry(entry.id) }) {
                            Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier.clickable { viewModel.focusOnHistoryEntry(entry) }
                )
            }
        }
    }
}

@Composable
fun IntelTabView(
    viewModel: TacticalMapViewModel,
    savedIntel: List<SavedIntelEntity>,
    onDiscussIntel: (SavedIntelEntity) -> Unit = {}
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (savedIntel.isNotEmpty()) {
            item {
                Text("SAVED INTEL ASSETS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
            }
            items(savedIntel) { intel ->
                ListItem(
                    leadingContent = {
                        if (intel.thumbnailPath != null) {
                            AsyncImage(
                                model = intel.thumbnailPath,
                                contentDescription = intel.label,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Icon(Icons.Default.PinDrop, null, modifier = Modifier.size(24.dp), tint = Color.Gray)
                        }
                    },
                    headlineContent = { Text(intel.label.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    supportingContent = { Text("${intel.type} • ${intel.groupName ?: "Individual"}", fontSize = 10.sp) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onDiscussIntel(intel) }) {
                                Icon(Icons.Default.Psychology, "Discuss with AI", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.deleteSavedIntel(intel.id) }) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.clickable { viewModel.focusOnSavedIntel(intel) }
                )
            }
        }
        
        val log = viewModel.intelLog
        if (log.isNotEmpty()) {
            item {
                Text("RECENT TACTICAL LOG", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(8.dp))
            }
            items(log) { entry ->
                ListItem(
                    headlineContent = { Text(entry.label, fontSize = 12.sp) },
                    supportingContent = { Text(entry.type, fontSize = 9.sp, color = Color.Gray) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeIntelItem(entry) }) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                        }
                    },
                    modifier = Modifier.clickable { viewModel.addPin(entry.latitude, entry.longitude, entry.label) }
                )
            }
        }
        
        if (savedIntel.isEmpty() && log.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("Intelligence ledger is empty.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun TargetingControls(
    points: List<Pair<Double, Double>>,
    onClear: () -> Unit,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("TARGETING MODE", style = MaterialTheme.typography.labelSmall, color = Color.Cyan, fontWeight = FontWeight.Bold)
            Text("${points.size}/4 POINTS SET", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Row {
            IconButton(onClick = onClear) { Icon(Icons.Default.Refresh, "Clear", tint = Color.Yellow) }
            IconButton(onClick = onExit) { Icon(Icons.Default.Close, "Exit", tint = Color.Red) }
        }
    }
}

@Composable
fun ScanTabView(viewModel: TacticalMapViewModel) {
    val categories = listOf("restaurant", "cafe", "atm", "hospital", "police", "pharmacy", "gas_station")
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text("SELECT SCAN FREQUENCY", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            mainAxisSpacing = 8.dp,
            crossAxisSpacing = 8.dp
        ) {
            categories.forEach { cat ->
                AssistChip(
                    onClick = { viewModel.fetchDiscoveryData(cat) },
                    label = { Text(cat.uppercase(), fontSize = 9.sp) },
                    leadingIcon = { Icon(Icons.Default.Radar, null, modifier = Modifier.size(12.dp)) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.clearAllDiscoveryData() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
        ) {
            Text("CLEAR ALL TACTICAL DATA", fontSize = 10.sp)
        }
    }
}

@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier) { measurables, constraints ->
        val itemConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(itemConstraints) }
        val mainSpacingPx = mainAxisSpacing.roundToPx()
        val crossSpacingPx = crossAxisSpacing.roundToPx()

        var xPosition = 0
        var yPosition = 0
        var maxY = 0
        val positions = ArrayList<Pair<Int, Int>>(placeables.size)

        placeables.forEach { placeable ->
            if (xPosition > 0 && xPosition + placeable.width > constraints.maxWidth) {
                xPosition = 0
                yPosition += maxY + crossSpacingPx
                maxY = 0
            }
            positions.add(xPosition to yPosition)
            xPosition += placeable.width + mainSpacingPx
            maxY = maxOf(maxY, placeable.height)
        }
        val contentHeight = yPosition + maxY

        layout(constraints.maxWidth, contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeables.forEachIndexed { index, placeable ->
                val (x, y) = positions[index]
                placeable.placeRelative(x, y)
            }
        }
    }
}
