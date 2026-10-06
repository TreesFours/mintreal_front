package com.example.mistreal_mini.ui.business

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.local.entity.BusinessEntity
import com.example.mistreal_mini.data.local.entity.BusinessItemEntity
import com.example.mistreal_mini.data.local.PreferenceManager
import com.example.mistreal_mini.data.repository.AdRepository
import com.example.mistreal_mini.data.repository.BusinessRepository
import com.example.mistreal_mini.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BusinessViewModel @Inject constructor(
    private val repository: BusinessRepository,
    private val locationHelper: LocationHelper,
    private val adRepository: AdRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    val isPro: StateFlow<Boolean> = preferenceManager.isPro
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _isCreatingAd = MutableStateFlow(false)
    val isCreatingAd = _isCreatingAd.asStateFlow()

    private val _adCreationResult = MutableSharedFlow<Resource<Int>>()
    val adCreationResult = _adCreationResult.asSharedFlow()

    fun createAd(
        businessId: String,
        mediaType: String,
        durationSeconds: Int,
        caption: String?,
        targetUrl: String?,
        ctaLabel: String?,
        videoUri: Uri?,
        imageUris: List<Uri>?
    ) {
        viewModelScope.launch {
            _isCreatingAd.value = true
            val result = adRepository.createAd(businessId, mediaType, durationSeconds, caption, targetUrl, ctaLabel, videoUri, imageUris)
            _isCreatingAd.value = false
            _adCreationResult.emit(result)
        }
    }

    private val _myBusiness = repository.getMyBusiness()
    val myBusiness: StateFlow<BusinessEntity?> = _myBusiness
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _isVerifying = MutableStateFlow(false)
    val isVerifying = _isVerifying.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Market")
    val selectedCategory = _selectedCategory.asStateFlow()

    val searchResults: StateFlow<List<BusinessEntity>> = _selectedCategory
        .combine(_searchQuery) { cat, query ->
            repository.searchBusinesses(cat, query).first()
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun registerBusiness(name: String, desc: String, category: String, address: String, lat: Double, lon: Double) {
        viewModelScope.launch {
            val business = BusinessEntity(
                businessId = "bus_${System.currentTimeMillis()}",
                ownerId = repository.currentUserId,
                name = name,
                description = desc,
                category = category,
                address = address,
                latitude = lat,
                longitude = lon,
                logoUrl = null,
                ownerImageUrl = null,
                verifiedTimestamp = System.currentTimeMillis(),
                connectedPlatforms = null
            )
            repository.saveBusiness(business)
        }
    }

    suspend fun verifyCurrentLocation(): android.location.Location? {
        _isVerifying.value = true
        val loc = locationHelper.getCurrentLocation()
        _isVerifying.value = false
        return loc
    }

    fun getInventory(businessId: String) = repository.getInventory(businessId)

    fun addInventoryItem(name: String, price: String?, imageUrl: String?, businessId: String) {
        viewModelScope.launch {
            repository.addInventoryItem(name, price, imageUrl, businessId)
        }
    }
}
