package com.example.mistreal_mini.ui.dashboard.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.browser.customtabs.CustomTabsIntent
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.dashboard.CelestialObject

// NASA's own Solar System Exploration pages — real imagery/video/facts per
// body, a direct "dig deeper" destination instead of a generic search link.
private val CELESTIAL_LEARN_MORE_URL = mapOf(
    "10" to "https://science.nasa.gov/sun/",
    "199" to "https://science.nasa.gov/mercury/",
    "299" to "https://science.nasa.gov/venus/",
    "399" to "https://science.nasa.gov/earth/",
    "301" to "https://science.nasa.gov/moon/",
    "499" to "https://science.nasa.gov/mars/",
    "599" to "https://science.nasa.gov/jupiter/",
    "699" to "https://science.nasa.gov/saturn/",
    "799" to "https://science.nasa.gov/uranus/",
    "899" to "https://science.nasa.gov/neptune/",
    "999" to "https://science.nasa.gov/dwarf-planets/pluto/"
)

@Composable
fun CelestialLedger(
    trackedObjects: List<CelestialObject>,
    onRefresh: () -> Unit,
    chatViewModel: ChatViewModel,
    onAskAi: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "CELESTIAL INTEL LEDGER", 
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, "Sync Orbitals", modifier = Modifier.size(18.dp))
            }
        }

        if (trackedObjects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(trackedObjects) { obj ->
                    CelestialObjectCard(
                        obj = obj,
                        onAskAi = { 
                            val prompt = "ORBITAL_INTERROGATION: Provide a tactical summary for ${obj.name}. Status: ${obj.status}, Earth Dist: ${obj.distEarth}, Sky Position: ${obj.relativeToMoon}. Mention any notable physical data: ${obj.description}"
                            onAskAi(prompt)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CelestialObjectCard(
    obj: CelestialObject,
    onAskAi: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when(obj.id) {
                    "10" -> "☀️"
                    "199" -> "🌑"
                    "299" -> "🌕"
                    "301" -> "🌙"
                    "499" -> "🔴"
                    "599" -> "🪐"
                    "699" -> "🪐"
                    else -> "✨"
                }
                Text(icon, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(obj.name.uppercase(), fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
                        if (!obj.isLive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color.Gray.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "UNAVAILABLE",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                    Text(
                        text = when {
                            !obj.isLive -> "LIVE TRACKING DOWN — RETRY ON NEXT REFRESH"
                            obj.status == "Visible" -> "VISIBLE IN NIGHT SKY"
                            else -> "BELOW HORIZON"
                        },
                        color = when {
                            !obj.isLive -> Color.Gray
                            obj.status == "Visible" -> Color.Green
                            else -> Color.Gray
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                IconButton(onClick = onAskAi) {
                    Icon(Icons.Default.Psychology, "AI Interrogate", tint = MaterialTheme.colorScheme.primary)
                }
                
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(thickness = 0.5.dp, color = Color.Gray.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Tactical Direction
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Navigation, null, 
                             modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = -obj.azimuth },
                             tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DIRECTION: ${obj.orientation} (${obj.azimuth.toInt()}° / ${obj.elevation.toInt()}°)", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Sky Guide
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "SKY GUIDE: ${obj.relativeToMoon}",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Distance Deck
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        InfoStat(label = "DIST TO EARTH", value = obj.distEarth, modifier = Modifier.weight(1f))
                        InfoStat(label = "DIST TO SUN", value = obj.distSun, modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Physical Intel
                    Text("PHYSICAL INTEL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text(
                        text = obj.description.ifBlank { "Synchronizing detailed physical metrics..." },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                        lineHeight = 18.sp
                    )

                    CELESTIAL_LEARN_MORE_URL[obj.id]?.let { url ->
                        val context = LocalContext.current
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                CustomTabsIntent.Builder().build()
                                    .launchUrl(context, android.net.Uri.parse(url))
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Public, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DIG DEEPER — NASA imagery & facts", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 8.sp)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}
