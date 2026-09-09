package com.example.mistreal_mini.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MapTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    bearing: Float,
    compassSupported: Boolean,
    onCompassClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                placeholder = { Text("Search coordinates or city...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, "Search", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }

        if (compassSupported) {
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(8.dp)
                    .clickable { onCompassClick() },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Compass",
                    modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = -bearing },
                    tint = Color.White
                )
                Text("${bearing.toInt()}°", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MapToolSidebar(
    isDrawMode: Boolean,
    onDrawModeToggle: () -> Unit,
    isPerimeterMode: Boolean,
    onPerimeterModeToggle: () -> Unit,
    isSniperMode: Boolean,
    onSniperModeToggle: () -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FloatingActionButton(
            onClick = onDrawModeToggle,
            containerColor = if (isDrawMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(if (isDrawMode) Icons.Default.Close else Icons.Default.Edit, null)
        }

        if (isDrawMode) {
            Spacer(modifier = Modifier.height(12.dp))
            SmallFloatingActionButton(
                onClick = onPerimeterModeToggle,
                containerColor = if (isPerimeterMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            ) { Icon(Icons.Default.Polyline, null) }

            Spacer(modifier = Modifier.height(8.dp))

            SmallFloatingActionButton(
                onClick = onSniperModeToggle,
                containerColor = if (isSniperMode) Color(0xFFF44336) else MaterialTheme.colorScheme.surface
            ) { Icon(Icons.Default.FilterCenterFocus, null) }
        }
    }
}

@Composable
fun MapBottomHUD(
    selectedNavTab: Int,
    onTabSelect: (Int) -> Unit,
    onChatToggle: () -> Unit,
    isChatVisible: Boolean,
    intelLog: List<IntelLogEntry>,
    onRemoveIntel: (IntelLogEntry) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column {
            if (intelLog.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(intelLog) { entry ->
                        FilterChip(
                            selected = true,
                            onClick = { onRemoveIntel(entry) },
                            label = { Text(entry.label, fontSize = 9.sp) },
                            trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp)) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    listOf("EXPLORE", "YOU", "INTEL", "SCAN").forEachIndexed { index, title ->
                        val sel = selectedNavTab == index
                        TextButton(
                            onClick = { onTabSelect(index) },
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (sel) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (sel) Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text(title, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                
                FloatingActionButton(
                    onClick = onChatToggle,
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(if (isChatVisible) Icons.Default.ExpandMore else Icons.Default.Psychology, null)
                }
            }
        }
    }
}
