package com.example.mistreal_mini.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.GameSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesListScreen(
    onBack: () -> Unit,
    onPlayGame: (GameSummary) -> Unit,
    viewModel: GamesViewModel = hiltViewModel()
) {
    val gamesResource by viewModel.games.collectAsState()
    val pinnedIds by viewModel.pinnedIds.collectAsState()
    var selectedCategory by remember { mutableStateOf("All") }

    val games = gamesResource.data.orEmpty()
    val categories = remember(games) { listOf("All") + games.mapNotNull { it.category }.distinct() }
    val visibleGames = if (selectedCategory == "All") games else games.filter { it.category == selectedCategory }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Games") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyRow(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            when (gamesResource) {
                is Resource.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is Resource.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(gamesResource.message ?: "Couldn't load games", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visibleGames, key = { it.id }) { game ->
                            GameCard(
                                game = game,
                                isPinned = pinnedIds.contains(game.id),
                                onClick = { onPlayGame(game) },
                                onTogglePin = { viewModel.togglePin(game.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameCard(
    game: GameSummary,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    val categoryEmoji = when ((game.category ?: "").lowercase()) {
        "action" -> "🕹️"
        "arcade" -> "👾"
        "puzzle" -> "🧩"
        "strategy" -> "♟️"
        "card" -> "🃏"
        else -> "🎮"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                if (!game.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = game.thumbnailUrl,
                        contentDescription = game.title,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(categoryEmoji, fontSize = 36.sp)
                    }
                }
                IconButton(onClick = onTogglePin, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(
                        if (isPinned) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isPinned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(game.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                game.category?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
