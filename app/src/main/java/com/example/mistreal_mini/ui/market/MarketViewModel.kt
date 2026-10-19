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
            _quotes.value = (result as? Resource.Success)?.data ?: emptyList()
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
