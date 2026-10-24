package com.example.mistreal_mini.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mistreal_mini.data.api.MarketQuote
import com.example.mistreal_mini.data.api.RemoteBusiness
import com.example.mistreal_mini.data.api.RemoteDiscoveryPoi
import com.example.mistreal_mini.ui.business.BusinessCategories
import com.example.mistreal_mini.ui.business.BusinessResultCard
import com.example.mistreal_mini.ui.dashboard.components.ads.AdHostViewModel
import com.example.mistreal_mini.ui.dashboard.components.ads.AdMediaView

/**
 * Ticker bands + an ad slot + category/search over businesses, native-first
 * with an external (non-Mistreal, no contact promise) fallback when a
 * search matches nothing native. Every piece here reuses an existing
 * pipeline (MarketRepository's watchlist, AdHostViewModel/AdMediaView,
 * BusinessCategories, BusinessResultCard) rather than introducing a second
 * version of any of them — see MarketViewModel for what's actually new.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    onBack: () -> Unit,
    onContactBusiness: (RemoteBusiness) -> Unit,
    onViewBusinessDetail: (RemoteBusiness) -> Unit,
    viewModel: MarketViewModel = hiltViewModel(),
    adHostViewModel: AdHostViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val tickerBands by viewModel.tickerBands.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val nativeResults by viewModel.nativeResults.collectAsState()
    val externalResults by viewModel.externalResults.collectAsState()
    val dueAd by adHostViewModel.dueAd.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Market") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            // --- Ticker bands ---
            item { TickerBandRow(label = "CRYPTO", quotes = tickerBands["crypto"].orEmpty()) }
            item { TickerBandRow(label = "FOREX", quotes = tickerBands["forex"].orEmpty()) }
            item { TickerBandRow(label = "TICKERS", quotes = tickerBands["tickers"].orEmpty()) }

            // --- Ad slot ---
            dueAd?.let { ad ->
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { adHostViewModel.trackEngagedClick(ad.id) }
                    ) {
                        AdMediaView(ad = ad, modifier = Modifier.fillMaxSize())
                        Text(
                            ad.caption ?: "Sponsored",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // --- Search ---
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search goods, services, or a business...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listOf("All") + BusinessCategories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { viewModel.onCategorySelected(cat) },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            if (isSearching && nativeResults.isEmpty() && externalResults.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (nativeResults.isNotEmpty()) {
                items(nativeResults) { business ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        BusinessResultCard(
                            business,
                            onContact = { onContactBusiness(business) },
                            onClick = { onViewBusinessDetail(business) }
                        )
                    }
                }
            } else if (externalResults.isNotEmpty()) {
                item {
                    Text(
                        "No matching businesses on Mistreal yet — here's what's nearby outside the app. We can't vouch for these.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(externalResults) { poi ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        ExternalResultCard(poi)
                    }
                }
            } else {
                item {
                    Text(
                        "No results yet — try a different category or search term.",
                        color = Color.Gray,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TickerBandRow(label: String, quotes: List<MarketQuote>) {
    if (quotes.isEmpty()) return
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(quotes) { quote -> TickerChip(quote) }
        }
    }
}

@Composable
private fun TickerChip(quote: MarketQuote) {
    val isUp = (quote.changePercent ?: 0.0) >= 0.0
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(quote.symbol, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Text(
                quote.price?.let { "%.2f".format(it) } ?: "—",
                style = MaterialTheme.typography.bodySmall
            )
            quote.changePercent?.let {
                Text(
                    "%+.2f%%".format(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUp) Color(0xFF4CAF50) else Color.Red
                )
            }
        }
    }
}

@Composable
private fun ExternalResultCard(poi: RemoteDiscoveryPoi) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(poi.name, fontWeight = FontWeight.Bold)
            Text(poi.address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("Found outside Mistreal", style = MaterialTheme.typography.labelSmall, color = Color.Gray)

            val phone = poi.contactPhone
            val website = poi.website
            if (phone != null || website != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    if (phone != null) {
                        OutlinedButton(onClick = {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone")))
                        }) {
                            Icon(Icons.Default.Call, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Call")
                        }
                    }
                    if (website != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(website)))
                        }) {
                            Icon(Icons.Default.Language, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Website")
                        }
                    }
                }
            }
        }
    }
}
