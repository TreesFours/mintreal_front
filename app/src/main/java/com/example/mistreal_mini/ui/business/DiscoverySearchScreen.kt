package com.example.mistreal_mini.ui.business

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.api.RemoteBusiness
import com.example.mistreal_mini.data.model.PlatformIcons

data class MarketCategoryItem(val name: String, val emoji: String, val color: Color)

val ECommerceCategories = listOf(
    MarketCategoryItem("Phones & Tablets", "📱", Color(0xFF3B82F6)),
    MarketCategoryItem("Computing", "💻", Color(0xFF10B981)),
    MarketCategoryItem("TVs & Audio", "📺", Color(0xFF8B5CF6)),
    MarketCategoryItem("Sneakers & Fashion", "👟", Color(0xFFEC4899)),
    MarketCategoryItem("Home & Office", "🏠", Color(0xFFF59E0B)),
    MarketCategoryItem("Appliances", "🔌", Color(0xFF6366F1)),
    MarketCategoryItem("Beauty & Care", "💄", Color(0xFFEF4444)),
    MarketCategoryItem("Fitness & Sports", "🏋️", Color(0xFF14B8A6)),
    MarketCategoryItem("Vehicles & Auto", "🚗", Color(0xFF84CC16)),
    MarketCategoryItem("Supermarket", "🛒", Color(0xFF06B6D4)),
    MarketCategoryItem("Beach & Street", "🏖️", Color(0xFFF97316)),
    MarketCategoryItem("Local Shops", "📍", Color(0xFFA855F7))
)

@Composable
fun DigitalAssetTicker() {
    val assets = listOf(
        "⚡ BTC/USD $91,420 (+2.4%)" to Color(0xFF4CAF50),
        "💎 ETH/USD $3,380 (+1.8%)" to Color(0xFF4CAF50),
        "☀️ SOL/USD $195 (+4.2%)" to Color(0xFF4CAF50),
        "📈 NVDA $142 (+3.1%)" to Color(0xFF4CAF50),
        "🍎 AAPL $232 (+0.8%)" to Color(0xFF4CAF50),
        "💱 EUR/USD 1.085" to Color.Cyan,
        "🇳🇬 USD/NGN 1,680" to Color.Yellow
    )
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30)
            if (scrollState.value >= scrollState.maxValue) {
                scrollState.scrollTo(0)
            } else {
                scrollState.scrollBy(1f)
            }
        }
    }

    Surface(
        color = Color(0xFF0F172A),
        modifier = Modifier.fillMaxWidth().height(32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().horizontalScroll(scrollState).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            assets.forEach { (text, color) ->
                Text(
                    text = text,
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("•", color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun JumiaCategoryGrid(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("All Your Essentials in One Place", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(ECommerceCategories) { cat ->
                val isSelected = selectedCategory == cat.name
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(72.dp).clickable { onSelectCategory(if (isSelected) "All" else cat.name) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) cat.color else cat.color.copy(alpha = 0.15f))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color.White else cat.color.copy(alpha = 0.4f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(cat.emoji, fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        cat.name,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        lineHeight = 12.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverySearchScreen(
    onBack: () -> Unit,
    onContactBusiness: (RemoteBusiness) -> Unit,
    onViewBusinessDetail: (RemoteBusiness) -> Unit = {},
    viewModel: BusinessViewModel = hiltViewModel()
) {
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LOCAL MARKETPLACE") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Live Asset Ticker
            DigitalAssetTicker()

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search products, brands, or categories...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )

            // Jumia-style Categories Grid
            JumiaCategoryGrid(
                selectedCategory = selectedCategory,
                onSelectCategory = { viewModel.onCategorySelected(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Mandatory Safety Protocol
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GppMaybe, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("TACTICAL SAFETY: Inspect items in person before payment.", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isSearching && results.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (results.isEmpty()) {
                Text(
                    "No businesses or listings found for this category yet.",
                    color = Color.Gray,
                    modifier = Modifier.padding(32.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(results) { business ->
                        BusinessResultCard(
                            business,
                            onContact = { onContactBusiness(business) },
                            onClick = { onViewBusinessDetail(business) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BusinessResultCard(business: RemoteBusiness, onContact: () -> Unit, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Gray)) {
                    if (!business.logoUrl.isNullOrBlank()) {
                        AsyncImage(model = business.logoUrl, contentDescription = null, contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Storefront, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(business.name, fontWeight = FontWeight.Bold)
                    Text(business.address ?: "Local Sector", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    business.distanceKm?.let {
                        Text("%.1f km away".format(it), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    if (business.confirmedMeetupsCount > 0) {
                        Text(
                            "${business.confirmedMeetupsCount} confirmed meetup${if (business.confirmedMeetupsCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Surface(
                    color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("VERIFIED TODAY", color = Color(0xFF4CAF50), fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(4.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(business.description ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 2)

            // Connected Social Badges
            if (business.connectedPlatforms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Connected:", fontSize = 10.sp, color = Color.Gray)
                    business.connectedPlatforms.forEach { handle ->
                        Icon(
                            imageVector = PlatformIcons.forPlatform(handle.platform) ?: Icons.Default.Link,
                            contentDescription = handle.platform,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onContact,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Chat, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("INITIATE SECURE CHAT")
            }
        }
    }
}
