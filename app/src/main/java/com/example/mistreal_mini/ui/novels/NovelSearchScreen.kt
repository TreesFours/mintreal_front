package com.example.mistreal_mini.ui.novels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.NovelSearchResult

/**
 * Searchable ebook archive against OpenLibrary's full catalog — distinct
 * from the small daily-pick "novels" row the Intelligence feed shows
 * passively. Opening a result resolves lazily to an Archive.org scan when
 * one exists, else the OpenLibrary catalog page, via Chrome Custom Tabs
 * (same pattern every other article link in this app already uses).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelSearchScreen(
    onBack: () -> Unit,
    viewModel: NovelSearchViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    val results by viewModel.results.collectAsState()
    val openingWorkKey by viewModel.openingWorkKey.collectAsState()
    val openedUrl by viewModel.openedUrl.collectAsState()

    LaunchedEffect(openedUrl) {
        openedUrl?.let { url ->
            androidx.browser.customtabs.CustomTabsIntent.Builder().build()
                .launchUrl(context, android.net.Uri.parse(url))
            viewModel.consumeOpenedUrl()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search books, authors, subjects...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    viewModel.search(query)
                })
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (val state = results) {
                is Resource.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is Resource.Error -> {
                    Text(state.message ?: "Search failed.", color = MaterialTheme.colorScheme.error)
                }
                is Resource.Success -> {
                    val books = state.data.orEmpty()
                    if (books.isEmpty() && query.isNotBlank()) {
                        Text("No results for \"$query\".", color = androidx.compose.ui.graphics.Color.Gray)
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(books, key = { it.workKey }) { book ->
                            NovelSearchRow(
                                book = book,
                                isOpening = openingWorkKey == book.workKey,
                                onClick = { viewModel.openBook(book.workKey) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelSearchRow(book: NovelSearchResult, isOpening: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isOpening) { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (book.imageUrl != null) {
            AsyncImage(
                model = book.imageUrl,
                contentDescription = null,
                modifier = Modifier.width(48.dp).height(64.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.width(48.dp).height(64.dp).clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(book.title, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(
                book.author + (book.year?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.ui.graphics.Color.Gray
            )
        }
        if (isOpening) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        }
    }
}
