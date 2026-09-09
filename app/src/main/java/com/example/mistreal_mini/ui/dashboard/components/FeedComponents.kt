package com.example.mistreal_mini.ui.dashboard.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.api.Article
import com.example.mistreal_mini.data.api.WeatherResponse
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.chat.components.InteractionMode
import com.example.mistreal_mini.ui.dashboard.DashboardViewModel

@Composable
fun WeatherHour(time: String, temp: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Text(temp, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun IntelMiniCard(article: Article, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(160.dp).height(100.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val icon = when(article.type) {
                "novel" -> Icons.AutoMirrored.Filled.MenuBook
                "wiki" -> Icons.Default.Info
                "journal" -> Icons.Default.Science
                else -> Icons.AutoMirrored.Filled.Article
            }
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(article.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun IntelligenceFeedView(
    weather: WeatherResponse?,
    orientation: String,
    bearing: Float,
    articles: List<Article>,
    onArticleClick: (Article) -> Unit,
    onAskAi: (String) -> Unit,
    onPinClick: (Article) -> Unit,
    onReadAloud: (String, InteractionMode) -> Unit,
    chatViewModel: ChatViewModel,
    dashboardViewModel: DashboardViewModel,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onOrbitalClick: () -> Unit
) {
    var showIntelPopup by remember { mutableStateOf<Article?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            WeatherCard(
                weather = weather,
                onAskAi = onAskAi
            )
        }

        // --- HORIZONTAL INTEL SECTIONS (Novel, Wiki, Journal) ---
        item {
            val horizontalTypes = listOf("novel", "wiki", "journal")
            val horizontalArticles = articles.filter { it.type in horizontalTypes }
            if (horizontalArticles.isNotEmpty()) {
                Column {
                    Text("RESEARCH & LITERATURE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(horizontalArticles) { art ->
                            IntelMiniCard(art, onClick = { showIntelPopup = art })
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOrbitalClick() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Public, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Celestial Intelligence Dashboard", fontWeight = FontWeight.Bold)
                }
            }
        }

        items(articles.filter { it.type !in listOf("novel", "wiki", "journal") }) { article ->
            if (article.type == "astro") {
                NasaApodCard(
                    article = article,
                    onPinClick = { onPinClick(article) },
                    onAiClick = { onAskAi("NASA_APOD: ${article.title}. Explain this astronomical event and its tactical significance.") }
                )
            } else {
                NewsItem(
                    article = article,
                    onPinClick = { onPinClick(article) },
                    onAiClick = { onAskAi("NEWS_INTEL: ${article.title}. Summarize this and analyze potential impact.") },
                    onClick = { onArticleClick(article) }
                )
            }
        }
    }

    if (showIntelPopup != null) {
        IntelDetailPopup(
            article = showIntelPopup!!,
            onDismiss = { showIntelPopup = null },
            onAskAi = onAskAi,
            onPin = { onPinClick(showIntelPopup!!) },
            onSaveToScribe = { 
                chatViewModel.saveAsNote(showIntelPopup!!.description ?: "") 
                showIntelPopup = null
            }
        )
    }
}

@Composable
fun IntelDetailPopup(
    article: Article,
    onDismiss: () -> Unit,
    onAskAi: (String) -> Unit,
    onPin: () -> Unit,
    onSaveToScribe: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(article.title.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 2)
                IconButton(onClick = { onAskAi("INTEL_ANALYSIS: ${article.title}. Explain the core concepts of this ${article.type}.") }) {
                    Icon(Icons.Default.Psychology, "AI Analysis", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            SelectionContainer {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(article.description ?: "No data retrieved.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onPin) { Text("PIN") }
                TextButton(onClick = onSaveToScribe) { Text("SAVE TO SCRIBE") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE") }
        }
    )
}

@Composable
fun WeatherCard(
    weather: WeatherResponse?,
    onAskAi: (String) -> Unit
) {
    var showWeatherDetail by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { showWeatherDetail = true },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(weather?.location?.uppercase() ?: "SEARCHING...", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                    Text(weather?.summary ?: "Calibrating sensors...", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
                Icon(Icons.Default.Cloud, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            }
            
            if (weather?.rainExpected == true) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Umbrella, null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PRECIPITATION ALERT: Rain in ${weather.timeToRain} mins", color = Color.Cyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showWeatherDetail) {
        WeatherDetailPopup(
            weather = weather,
            onDismiss = { showWeatherDetail = false },
            onAskAi = onAskAi
        )
    }
}

@Composable
fun WeatherDetailPopup(
    weather: WeatherResponse?,
    onDismiss: () -> Unit,
    onAskAi: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("STRATEGIC WEATHER REPORT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (weather == null) {
                    Text("Weather data unavailable. Please verify location permissions.")
                } else {
                    Text(weather.summary, style = MaterialTheme.typography.bodyLarge)
                    HorizontalDivider(thickness = 0.5.dp)
                    Text("3-HOUR OUTLOOK", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    
                    val forecast = weather.forecast ?: emptyList()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (forecast.isEmpty()) {
                            Text("No short-term outlook data.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        } else {
                            forecast.take(4).forEach { item ->
                                val icon = when(item.condition.lowercase()) {
                                    "clear" -> Icons.Default.WbSunny
                                    "clouds" -> Icons.Default.Cloud
                                    "rain", "drizzle" -> Icons.Default.Umbrella
                                    "thunderstorm" -> Icons.Default.FlashOn
                                    else -> Icons.Default.Cloud
                                }
                                WeatherHour(item.time, item.temp, icon)
                            }
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                    Text("EXTENDED FORECAST", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    if (forecast.size > 4) {
                        forecast.drop(4).forEach { item ->
                            Text("${item.time}: ${item.temp} / ${item.condition}", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text("Day 1: 29°C / Clear Sky", style = MaterialTheme.typography.bodySmall)
                        Text("Day 2: 27°C / Partial Cloud", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAskAi("WEATHER_INTEL: ${weather?.summary}. Provide a tactical survival analysis for these conditions.") }) {
                Text("AI ANALYSIS")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE") }
        }
    )
}

@Composable
fun NewsItem(article: Article, onPinClick: () -> Unit, onAiClick: () -> Unit, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = article.category?.uppercase() ?: "WORLD", 
                    style = MaterialTheme.typography.labelSmall, 
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("• ${article.timestamp ?: "Just now"}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onPinClick, modifier = Modifier.size(24.dp)) {
                    Icon(if(article.isPinned == true) Icons.Default.PushPin else Icons.Default.PushPin, null, tint = if(article.isPinned == true) Color.Yellow else Color.Gray, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(article.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (article.description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(article.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAiClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Psychology, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AI ANALYSIS", fontSize = 10.sp)
                }
                OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text("READ FULL", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun NasaApodCard(article: Article, onPinClick: () -> Unit, onAiClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                AsyncImage(
                    model = article.url,
                    contentDescription = "NASA APOD",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                if (article.isPinned == true) {
                    Icon(
                        Icons.Default.PushPin, 
                        null, 
                        tint = Color.Yellow, 
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(24.dp)
                    )
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "NASA APOD • ${article.title.replace("[Astro] ", "")}", 
                        color = Color.White, 
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = article.description ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onPinClick) {
                    Icon(
                        if (article.isPinned == true) Icons.Default.PushPin else Icons.Default.PushPin,
                        null,
                        tint = if (article.isPinned == true) Color.Yellow else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onAiClick) {
                    Icon(Icons.Default.Psychology, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun LocationBanner(weather: WeatherResponse?) {
    val locationText = weather?.location ?: "Detecting Location..."
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        SelectionContainer {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "Active Location: $locationText", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(text = "News & Weather updated for this region", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsDetailScreen(article: Article, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Intelligence Detail", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text(article.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text(article.description ?: "No description available.", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { 
                    val customTabsIntent = androidx.browser.customtabs.CustomTabsIntent.Builder().build()
                    customTabsIntent.launchUrl(context, android.net.Uri.parse(article.url))
                }, 
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Source")
            }
        }
    }
}
