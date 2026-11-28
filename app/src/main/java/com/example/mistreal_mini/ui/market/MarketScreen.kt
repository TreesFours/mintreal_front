package com.example.mistreal_mini.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    // Confirmed gap: this screen previously had no profile button at all.
    onMyProfileClick: () -> Unit = {},
    // An advert's card always deep-links to the POSTING business's owner
    // profile — never a personal profile — matching the original vision
    // exactly and reusing the same Routes.ownerProfile(...) builder
    // BusinessDetailScreen's own onViewOwnerProfile already uses.
    onViewOwnerProfile: (ownerDeviceId: String, ownerName: String?, ownerPhotoUrl: String?, businessId: String) -> Unit = { _, _, _, _ -> },
    // Moved here from Dashboard's own bottomBar — this 5-button row now lives
    // on each of its 5 destination screens instead, so jumping between them
    // doesn't require detouring back through Dashboard every time.
    onOpenGames: () -> Unit = {},
    onOpenReels: () -> Unit = {},
    onOpenWatchHub: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    // Shared instance with the rest of the app (see MainActivity) — lets a
    // ticker's chart dialog open the same AI drafting popup the Intelligence
    // feed's "AI Analysis" buttons already use, with zero separate AI wiring.
    chatViewModel: com.example.mistreal_mini.ui.chat.ChatViewModel? = null,
    viewModel: MarketViewModel = hiltViewModel(),
    adHostViewModel: AdHostViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var aiAnalysisContext by remember { mutableStateOf<String?>(null) }
    val tickerBands by viewModel.tickerBands.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val nativeResults by viewModel.nativeResults.collectAsState()
    val externalResults by viewModel.externalResults.collectAsState()
    val dueAd by adHostViewModel.dueAd.collectAsState()
    val marketplaceMode by viewModel.marketplaceMode.collectAsState()
    val myBusiness by viewModel.myBusiness.collectAsState()
    val advertCategory by viewModel.advertCategory.collectAsState()
    val isLoadingAdverts by viewModel.isLoadingAdverts.collectAsState()
    val adverts by viewModel.adverts.collectAsState()
    var showPostAdvertDialog by remember { mutableStateOf(false) }

    LaunchedEffect(marketplaceMode) {
        if (marketplaceMode) viewModel.loadAdverts()
    }

    val selectedTicker by viewModel.selectedTicker.collectAsState()
    val chartCandles by viewModel.chartCandles.collectAsState()
    val chartTheme by viewModel.chartTheme.collectAsState()
    val showChartDialog by viewModel.showChartDialog.collectAsState()
    val isLoadingChart by viewModel.isLoadingChart.collectAsState()
    val showMovingAverage by viewModel.showMovingAverage.collectAsState()
    val showFibonacci by viewModel.showFibonacci.collectAsState()
    val intradayInterval by viewModel.intradayInterval.collectAsState()
    val intradayCandles by viewModel.intradayCandles.collectAsState()
    val timeframeDays by viewModel.timeframeDays.collectAsState()
    val selectedAssetClass by viewModel.selectedAssetClass.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Market Hub") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = onMyProfileClick) {
                        Icon(Icons.Default.Person, contentDescription = "My Profile")
                    }
                }
            )
        },
        bottomBar = {
            com.example.mistreal_mini.ui.chat.components.InstagramStyleBottomBar(
                selectedTab = 1,
                onTabSelected = { tab ->
                    when (tab) {
                        0 -> onOpenGames()
                        2 -> onOpenReels()
                        3 -> onOpenWatchHub()
                        4 -> onOpenChat()
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            // --- Ticker bands (Endless Auto-Scrolling Marquee) ---
            // Tapping any chip opens the real chart popup below — this used
            // to be an always-visible inline card that didn't actually draw
            // a chart at all (just text claiming one existed).
            item { MarqueeTickerRow(label = "CRYPTO", quotes = tickerBands["crypto"].orEmpty(), onSelect = { viewModel.selectTicker(it.symbol, it.assetClass ?: "crypto") }) }
            item { MarqueeTickerRow(label = "FOREX", quotes = tickerBands["forex"].orEmpty(), onSelect = { viewModel.selectTicker(it.symbol, it.assetClass ?: "forex") }) }
            item { MarqueeTickerRow(label = "TICKERS & COMMODITIES", quotes = tickerBands["tickers"].orEmpty(), onSelect = { viewModel.selectTicker(it.symbol, it.assetClass ?: "commodity") }) }

            // --- Ticker search — finds any symbol beyond the three fixed
            // bands above, quoted/charted on-demand (see MarketViewModel.
            // searchSymbols' comment) rather than needing it pre-populated
            // in a proactively-polled watchlist.
            item {
                var tickerSearchQuery by remember { mutableStateOf("") }
                val searchResults by viewModel.symbolSearchResults.collectAsState()
                val isSearching by viewModel.isSearchingSymbols.collectAsState()

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = tickerSearchQuery,
                        onValueChange = { tickerSearchQuery = it; viewModel.searchSymbols(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search any ticker (stocks, crypto)...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (tickerSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { tickerSearchQuery = ""; viewModel.clearSymbolSearch() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true
                    )
                    if (isSearching) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    searchResults.forEach { result ->
                        ListItem(
                            headlineContent = { Text(result.symbol, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(result.name, maxLines = 1) },
                            trailingContent = { Text(result.assetClass.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.Gray) },
                            modifier = Modifier.clickable {
                                viewModel.selectTicker(result.symbol, result.assetClass, result.coingeckoId)
                                tickerSearchQuery = ""
                                viewModel.clearSymbolSearch()
                            }
                        )
                    }
                }
            }

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

            // --- Businesses / Marketplace segmented toggle — defaults to
            // Businesses (today's unchanged search behavior); Marketplace is
            // the new classifieds advert feed.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !marketplaceMode,
                        onClick = { viewModel.setMarketplaceMode(false) },
                        label = { Text("Businesses") }
                    )
                    FilterChip(
                        selected = marketplaceMode,
                        onClick = { viewModel.setMarketplaceMode(true) },
                        label = { Text("Marketplace") }
                    )
                }
            }

            if (!marketplaceMode) {
                // --- Search ---
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search goods, services, or a business...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
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
            } else {
                // --- Marketplace: classifieds advert feed (business-only posting) ---
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Marketplace", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (myBusiness != null) {
                            Button(onClick = { showPostAdvertDialog = true }) { Text("Post New Advert") }
                        }
                    }
                }
                if (myBusiness == null) {
                    item {
                        Text(
                            "Register a business in Business Hub to post an advert here.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
                                selected = advertCategory == cat,
                                onClick = { viewModel.onAdvertCategorySelected(cat) },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
                if (isLoadingAdverts && adverts.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (adverts.isEmpty()) {
                    item {
                        Text(
                            "No adverts yet — try a different category, or be the first to post one.",
                            color = Color.Gray,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                } else {
                    items(adverts) { advert ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            AdvertCard(
                                advert = advert,
                                onClick = {
                                    advert.ownerDeviceId?.let { ownerDeviceId ->
                                        onViewOwnerProfile(ownerDeviceId, advert.ownerName, advert.ownerPhotoUrl, advert.businessId)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showChartDialog) {
        TickerChartDialog(
            symbol = selectedTicker ?: "",
            assetClass = selectedAssetClass,
            dailyCandles = chartCandles,
            intradayCandles = intradayCandles,
            intradayInterval = intradayInterval,
            timeframeDays = timeframeDays,
            isLoading = isLoadingChart,
            showMovingAverage = showMovingAverage,
            showFibonacci = showFibonacci,
            chartTheme = chartTheme,
            onThemeChange = { viewModel.setChartTheme(it) },
            onToggleMovingAverage = { viewModel.toggleMovingAverage() },
            onToggleFibonacci = { viewModel.toggleFibonacci() },
            onSelectTimeframe = { viewModel.selectTimeframeDays(it) },
            onSelectIntraday = { viewModel.selectIntradayInterval(it) },
            onDismiss = { viewModel.closeChartDialog() },
            onAiAnalysisClick = if (chatViewModel != null) {
                {
                    val series = if (intradayInterval != null) intradayCandles else chartCandles.takeLast(timeframeDays)
                    val summary = summarizeCandlesForPrompt(selectedTicker ?: "", series)
                    aiAnalysisContext = "MARKET_ANALYSIS: Provide a concise technical analysis of ${selectedTicker} ($selectedAssetClass). $summary Cover trend, momentum, and key support/resistance levels. This is not financial advice."
                }
            } else null
        )
    }

    if (aiAnalysisContext != null && chatViewModel != null) {
        com.example.mistreal_mini.ui.util.AiInsightPopup(
            contextText = aiAnalysisContext!!,
            onClose = { aiAnalysisContext = null },
            viewModel = chatViewModel
        )
    }

    if (showPostAdvertDialog) {
        myBusiness?.let { business ->
            PostAdvertDialog(
                isPosting = viewModel.isPostingAdvert.collectAsState().value,
                error = viewModel.advertPostError.collectAsState().value,
                onDismiss = { showPostAdvertDialog = false },
                onPost = { title, description, price, category, imageUri ->
                    viewModel.postAdvert(business.businessId, title, description, price, category, imageUri, context) { success ->
                        if (success) showPostAdvertDialog = false
                    }
                }
            )
        }
    }
}

@Composable
private fun AdvertCard(advert: com.example.mistreal_mini.data.api.RemoteAdvert, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!advert.imageUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = advert.imageUrl,
                    contentDescription = advert.title,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(advert.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                advert.price?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(advert.category, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("by ${advert.ownerName ?: "Unknown"}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostAdvertDialog(
    isPosting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onPost: (title: String, description: String?, price: String?, category: String, imageUri: android.net.Uri?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(BusinessCategories.first()) }
    var imageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> imageUri = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Post New Advert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BusinessCategories) { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                    }
                }
                TextButton(onClick = { imagePicker.launch("image/*") }) {
                    Text(if (imageUri == null) "Add Photo" else "Photo selected")
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && !isPosting,
                onClick = {
                    onPost(title, description.ifBlank { null }, price.ifBlank { null }, category, imageUri)
                }
            ) {
                if (isPosting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Post")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Feeds a ticker's own recent price action into the "AI Analysis" prompt
 * instead of asking the model to analyze a symbol it has no actual data
 * for. Deliberately simple arithmetic (not a second API call) — period
 * change and range — since the model itself does the real interpretation.
 */
private fun summarizeCandlesForPrompt(symbol: String, candles: List<com.example.mistreal_mini.data.api.MarketCandle>): String {
    if (candles.isEmpty()) return "No recent price data is available for $symbol."
    val first = candles.first()
    val last = candles.last()
    val periodChangePercent = if (first.close != 0.0) ((last.close - first.close) / first.close) * 100 else 0.0
    val high = candles.maxOf { it.high }
    val low = candles.minOf { it.low }
    return "Over the last ${candles.size} periods: opened near ${first.close}, now at ${last.close} " +
        "(${"%.2f".format(periodChangePercent)}% change), ranging between $low and $high."
}

/**
 * Replaces what used to be an always-visible inline card that never actually
 * drew a chart — just literal text claiming one existed ("Live Candlestick
 * Series (N points)"). This is a real popup, triggered per-ticker-tap, built
 * on the genuine Canvas-drawn chart (wicks/bodies/MA/trend-line) that
 * previously only existed inside Settings' Market Alerts section, completely
 * unreachable from this screen. Adds Fibonacci retracement on top of that
 * existing chart (new) and the same 1M/3M/6M + real-intraday-refetch
 * timeframe split Settings' dialog already uses, so both chart surfaces in
 * this app now behave identically instead of one being real and one fake.
 */
@Composable
private fun TickerChartDialog(
    symbol: String,
    assetClass: String,
    dailyCandles: List<com.example.mistreal_mini.data.api.MarketCandle>,
    intradayCandles: List<com.example.mistreal_mini.data.api.MarketCandle>,
    intradayInterval: String?,
    timeframeDays: Int,
    isLoading: Boolean,
    showMovingAverage: Boolean,
    showFibonacci: Boolean,
    chartTheme: String,
    onThemeChange: (String) -> Unit,
    onToggleMovingAverage: () -> Unit,
    onToggleFibonacci: () -> Unit,
    onSelectTimeframe: (Int) -> Unit,
    onSelectIntraday: (String) -> Unit,
    onDismiss: () -> Unit,
    onAiAnalysisClick: (() -> Unit)? = null
) {
    // CoinGecko's free /ohlc tier has no hourly granularity to request —
    // confirmed in marketDataService.ts, not assumed — so intraday chips are
    // only offered for non-crypto, same gate Settings' dialog already uses.
    val supportsIntraday = assetClass != "crypto"
    val displayedCandles = if (intradayInterval != null) intradayCandles else dailyCandles.takeLast(timeframeDays)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.example.mistreal_mini.ui.components.ThinkingTreeIcon(size = 20.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(symbol, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        onAiAnalysisClick?.let { onClick ->
                            IconButton(onClick = onClick) {
                                Icon(Icons.Default.Psychology, "AI Analysis", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, contentDescription = "Close") }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Cyber", "Neon", "Classic").forEach { theme ->
                        FilterChip(
                            selected = chartTheme == theme,
                            onClick = { onThemeChange(theme) },
                            label = { Text(theme, fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when (chartTheme) {
                                "Neon" -> Color(0xFF0F172A)
                                "Cyber" -> Color(0xFF0D0221)
                                else -> Color.Black.copy(alpha = 0.8f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading && displayedCandles.isEmpty()) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        com.example.mistreal_mini.ui.settings.CandlestickChart(
                            candles = displayedCandles,
                            modifier = Modifier.fillMaxWidth(),
                            showMovingAverage = showMovingAverage,
                            showFibonacci = showFibonacci
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = showMovingAverage, onClick = onToggleMovingAverage, label = { Text("MA 20", fontSize = 10.sp) })
                    FilterChip(selected = showFibonacci, onClick = onToggleFibonacci, label = { Text("Fibonacci", fontSize = 10.sp) })
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1M" to 30, "3M" to 90, "6M" to 180).forEach { (label, days) ->
                        FilterChip(
                            selected = intradayInterval == null && timeframeDays == days,
                            onClick = { onSelectTimeframe(days) },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }
                    if (supportsIntraday) {
                        listOf("1h", "4h").forEach { interval ->
                            FilterChip(
                                selected = intradayInterval == interval,
                                onClick = { onSelectIntraday(interval) },
                                label = { Text(interval, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarqueeTickerRow(label: String, quotes: List<MarketQuote>, onSelect: (MarketQuote) -> Unit) {
    if (quotes.isEmpty()) return
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val endlessQuotes = remember(quotes) { quotes + quotes + quotes }

    LaunchedEffect(quotes) {
        while (true) {
            kotlinx.coroutines.delay(2200)
            val currentIdx = listState.firstVisibleItemIndex
            val targetIdx = (currentIdx + 1) % endlessQuotes.size
            listState.animateScrollToItem(targetIdx)
        }
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(endlessQuotes) { quote -> TickerChip(quote, onClick = { onSelect(quote) }) }
        }
    }
}

@Composable
private fun TickerChip(quote: MarketQuote, onClick: () -> Unit) {
    val isUp = (quote.changePercent ?: 0.0) >= 0.0
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.clickable { onClick() }
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
