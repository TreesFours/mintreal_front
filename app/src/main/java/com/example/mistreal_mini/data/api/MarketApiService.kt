package com.example.mistreal_mini.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MarketApiService {

    @GET("api/markets/watchlist")
    suspend fun getWatchlist(): MarketWatchlistResponse

    @GET("api/markets/candles")
    suspend fun getCandles(@Query("symbol") symbol: String, @Query("assetClass") assetClass: String): MarketCandlesResponse

    @POST("api/markets/alerts")
    suspend fun createAlert(@Body request: CreateMarketAlertRequest): MarketAlertResponse

    @GET("api/markets/alerts")
    suspend fun getAlerts(@Query("deviceId") deviceId: String): MarketAlertListResponse

    @DELETE("api/markets/alerts/{id}")
    suspend fun deleteAlert(@Path("id") id: Int): GenericSuccessResponse

    @GET("api/markets/alerts/pending")
    suspend fun getPendingAlerts(@Query("deviceId") deviceId: String): MarketAlertListResponse

    @POST("api/markets/alerts/{id}/acknowledge")
    suspend fun acknowledgeAlert(@Path("id") id: Int): GenericSuccessResponse
}

data class MarketQuote(
    val symbol: String,
    val assetClass: String? = null,
    val price: Double? = null,
    val currency: String? = null,
    val changePercent: Double? = null
)

data class MarketWatchlistResponse(
    val success: Boolean,
    val quotes: List<MarketQuote> = emptyList(),
    val error: String? = null
)

data class MarketCandle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)

data class MarketCandlesResponse(
    val success: Boolean,
    val candles: List<MarketCandle> = emptyList(),
    val error: String? = null
)

data class CreateMarketAlertRequest(
    val deviceId: String,
    val firebaseUid: String? = null,
    val symbol: String,
    val assetClass: String,
    val direction: String, // "above" | "below"
    val targetPrice: Double
)

data class MarketAlert(
    val id: Int,
    val deviceId: String,
    val symbol: String,
    val assetClass: String,
    val direction: String,
    val targetPrice: Double,
    val currency: String? = "USD",
    val active: Boolean = true,
    val triggeredAt: String? = null,
    val triggeredPrice: Double? = null,
    val delivered: Boolean = false
)

data class MarketAlertResponse(
    val success: Boolean,
    val alert: MarketAlert? = null,
    val error: String? = null
)

data class MarketAlertListResponse(
    val success: Boolean,
    val alerts: List<MarketAlert> = emptyList(),
    val error: String? = null
)

data class GenericSuccessResponse(
    val success: Boolean,
    val error: String? = null
)
