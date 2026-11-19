package com.example.mistreal_mini.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.MarketQuote
import com.example.mistreal_mini.data.api.RemoteBusiness
import com.example.mistreal_mini.data.api.RemoteDiscoveryPoi
import com.example.mistreal_mini.data.repository.BusinessRepository
import com.example.mistreal_mini.data.repository.MarketRepository
import com.example.mistreal_mini.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Market screen: three ticker bands (crypto/forex/tickers) plus
 * category/search over businesses, native-first with an external fallback
 * when nothing native matches. Deliberately separate from BusinessViewModel
 * — that one already owns DiscoverySearchScreen's native-only search and
 * stays untouched; this one additionally surfaces the external half
 * businessRoutes.ts's /search only populates when native results are empty.
 */
private val DEFAULT_MARKET_QUOTES = listOf(
    MarketQuote("BTC", "crypto", "crypto", 94850.0, "USD", 2.45),
    MarketQuote("ETH", "crypto", "crypto", 3450.0, "USD", 1.82),
    MarketQuote("SOL", "crypto", "crypto", 215.0, "USD", 5.12),
    MarketQuote("XRP", "crypto", "crypto", 1.45, "USD", 3.20),
    MarketQuote("ADA", "crypto", "crypto", 0.92, "USD", -0.85),
    MarketQuote("DOGE", "crypto", "crypto", 0.38, "USD", 4.10),
    MarketQuote("AVAX", "crypto", "crypto", 38.50, "USD", 2.10),
    MarketQuote("LINK", "crypto", "crypto", 18.20, "USD", 1.45),
    MarketQuote("DOT", "crypto", "crypto", 8.40, "USD", -1.20),
    MarketQuote("MATIC", "crypto", "crypto", 0.65, "USD", 0.95),
    MarketQuote("SUI", "crypto", "crypto", 3.40, "USD", 8.50),
    MarketQuote("APT", "crypto", "crypto", 12.80, "USD", 3.10),
    MarketQuote("RENDER", "crypto", "crypto", 9.20, "USD", 6.40),
    MarketQuote("PEPE", "crypto", "crypto", 0.000021, "USD", 12.4),
    MarketQuote("EUR/USD", "forex", "forex", 1.0850, "USD", 0.15),
    MarketQuote("GBP/USD", "forex", "forex", 1.2920, "USD", -0.22),
    MarketQuote("USD/JPY", "forex", "forex", 154.20, "JPY", 0.45),
    MarketQuote("AUD/USD", "forex", "forex", 0.6580, "USD", 0.18),
    MarketQuote("USD/CAD", "forex", "forex", 1.3950, "CAD", -0.10),
    MarketQuote("USD/CHF", "forex", "forex", 0.8840, "CHF", 0.05),
    MarketQuote("NZD/USD", "forex", "forex", 0.5920, "USD", -0.30),
    MarketQuote("USD/CNY", "forex", "forex", 7.2350, "CNY", 0.08),
    MarketQuote("USD/INR", "forex", "forex", 84.40, "INR", 0.02),
    MarketQuote("USD/NGN", "forex", "forex", 1680.0, "NGN", -1.20),
    MarketQuote("GOLD", "commodity", "tickers", 2685.50, "USD", 0.85),
    MarketQuote("SILVER", "commodity", "tickers", 31.80, "USD", 1.40),
    MarketQuote("CRUDE OIL", "commodity", "tickers", 71.40, "USD", -0.95),
    MarketQuote("NAT GAS", "commodity", "tickers", 2.85, "USD", 2.30),
    MarketQuote("S&P 500", "stock", "tickers", 5985.0, "USD", 0.65),
    MarketQuote("NASDAQ", "stock", "tickers", 18980.0, "USD", 0.92),
    MarketQuote("DOW JONES", "stock", "tickers", 43750.0, "USD", 0.35)
)

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
    private val businessRepository: BusinessRepository,
    private val locationHelper: LocationHelper
) : ViewModel() {

    private val _quotes = MutableStateFlow<List<MarketQuote>>(emptyList())
    val tickerBands: StateFlow<Map<String, List<MarketQuote>>> = _quotes
        .map { quotes -> quotes.filter { it.price != null }.groupBy { it.band ?: "tickers" } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    init {
        viewModelScope.launch {
            val result = marketRepository.getWatchlist()
            val loaded = (result as? Resource.Success)?.data?.filter { it.price != null }
            _quotes.value = if (!loaded.isNullOrEmpty()) loaded else DEFAULT_MARKET_QUOTES
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    // Fetched once and reused, same reasoning as BusinessViewModel's own
    // copy of this — a GPS fix is comparatively slow and the user's
    // position doesn't meaningfully change mid-search.
    private var cachedSearchLocation: android.location.Location? = null

    private val searchResult: StateFlow<Resource<com.example.mistreal_mini.data.repository.BusinessSearchResult>?> = _selectedCategory
        .combine(_searchQuery) { cat, query -> cat to query }
        .mapLatest { (cat, query) ->
            _isSearching.value = true
            if (cachedSearchLocation == null) {
                cachedSearchLocation = locationHelper.getCurrentLocation()
            }
            val loc = cachedSearchLocation
            val result = businessRepository.searchBusinessesRemoteFull(cat, query, loc?.latitude, loc?.longitude)
            _isSearching.value = false
            result
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val nativeResults: StateFlow<List<RemoteBusiness>> = searchResult
        .map { (it as? Resource.Success)?.data?.native ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Only ever non-empty when nativeResults is empty — matches the
    // backend's own "fallback, not supplement" rule (businessRoutes.ts).
    val externalResults: StateFlow<List<RemoteDiscoveryPoi>> = searchResult
        .map { (it as? Resource.Success)?.data?.external ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
    }
}
