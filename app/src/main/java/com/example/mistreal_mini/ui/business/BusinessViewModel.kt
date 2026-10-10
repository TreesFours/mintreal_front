package com.example.mistreal_mini.ui.business

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.BusinessDetailResponse
import com.example.mistreal_mini.data.api.BusinessPlatformHandle
import com.example.mistreal_mini.data.api.RemoteBusiness
import com.example.mistreal_mini.data.local.entity.BusinessEntity
import com.example.mistreal_mini.data.local.entity.BusinessItemEntity
import com.example.mistreal_mini.data.local.PreferenceManager
import com.example.mistreal_mini.data.repository.AdRepository
import com.example.mistreal_mini.data.repository.BusinessRepository
import com.example.mistreal_mini.data.repository.VerifiedFaceRepository
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
    private val preferenceManager: PreferenceManager,
    verifiedFaceRepository: VerifiedFaceRepository
) : ViewModel() {

    // For the owner-photo picker's "use my Verified Face" option.
    val verifiedFaceUris = verifiedFaceRepository.allFaceUris
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    // Previously this only ever queried the local Room cache — meaning a
    // user could only ever see businesses THEY registered on THIS device.
    // Now a real cross-device directory (businessRoutes.ts's /search).
    val searchResults: StateFlow<List<RemoteBusiness>> = _selectedCategory
        .combine(_searchQuery) { cat, query -> cat to query }
        .mapLatest { (cat, query) ->
            _isSearching.value = true
            val result = repository.searchBusinessesRemote(cat, query)
            _isSearching.value = false
            (result as? Resource.Success)?.data ?: emptyList()
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
    }

    private val _businessDetail = MutableStateFlow<BusinessDetailResponse?>(null)
    val businessDetail = _businessDetail.asStateFlow()

    fun fetchBusinessDetail(businessId: String) {
        viewModelScope.launch {
            val result = repository.getBusinessDetail(businessId)
            _businessDetail.value = (result as? Resource.Success)?.data
        }
        fetchCanShare(businessId)
        fetchBusinessAd(businessId)
    }

    private val _canShare = MutableStateFlow(false)
    val canShare = _canShare.asStateFlow()

    private fun fetchCanShare(businessId: String) {
        viewModelScope.launch {
            val result = repository.canShareBusiness(businessId)
            _canShare.value = (result as? Resource.Success)?.data ?: false
        }
    }

    private val _businessAd = MutableStateFlow<com.example.mistreal_mini.data.api.AdPayload?>(null)
    val businessAd = _businessAd.asStateFlow()

    private fun fetchBusinessAd(businessId: String) {
        viewModelScope.launch {
            val result = repository.getAdForBusiness(businessId)
            _businessAd.value = (result as? Resource.Success)?.data?.ad
        }
    }

    fun uploadOwnerPhoto(business: BusinessEntity, photoUri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            repository.uploadOwnerPhoto(business, photoUri, context)
        }
    }

    fun registerBusiness(
        name: String,
        desc: String,
        category: String,
        address: String,
        lat: Double,
        lon: Double,
        connectedPlatforms: List<BusinessPlatformHandle> = emptyList(),
        ownerName: String? = null
    ) {
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
                ownerName = ownerName?.takeIf { it.isNotBlank() },
                verifiedTimestamp = System.currentTimeMillis(),
                connectedPlatforms = repository.serializeConnectedPlatforms(connectedPlatforms)
            )
            repository.saveBusiness(business)
        }
    }

    /** Lets an existing business owner add/edit the public handles customers can reach them on. */
    fun updateConnectedPlatforms(business: BusinessEntity, platforms: List<BusinessPlatformHandle>) {
        viewModelScope.launch {
            repository.saveBusiness(business.copy(connectedPlatforms = repository.serializeConnectedPlatforms(platforms)))
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
