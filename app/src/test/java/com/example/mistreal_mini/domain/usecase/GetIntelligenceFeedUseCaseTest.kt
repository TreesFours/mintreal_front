package com.example.mistreal_mini.domain.usecase

import android.location.Location
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.WeatherResponse
import com.example.mistreal_mini.data.local.PreferenceManager
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.util.LocationHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class GetIntelligenceFeedUseCaseTest {

    @Mock
    lateinit var infoRepository: InfoRepository
    @Mock
    lateinit var locationHelper: LocationHelper
    @Mock
    lateinit var preferenceManager: PreferenceManager
    @Mock
    lateinit var mockLocation: Location

    private lateinit var getIntelligenceFeedUseCase: GetIntelligenceFeedUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        getIntelligenceFeedUseCase = GetIntelligenceFeedUseCase(infoRepository, locationHelper, preferenceManager)
    }

    @Test
    fun `getWeather returns success when location is available`() = runBlocking {
        `when`(mockLocation.latitude).thenReturn(10.0)
        `when`(mockLocation.longitude).thenReturn(20.0)
        
        val mockWeather = WeatherResponse("Sunny", "Tokyo", false, null)
        
        `when`(locationHelper.getCurrentLocation()).thenReturn(mockLocation)
        `when`(infoRepository.getWeather(10.0, 20.0)).thenReturn(Resource.Success(mockWeather))

        val result = getIntelligenceFeedUseCase.getWeather()
        
        assertTrue(result is Resource.Success)
        assertTrue(result.data?.summary == "Sunny")
    }
}
