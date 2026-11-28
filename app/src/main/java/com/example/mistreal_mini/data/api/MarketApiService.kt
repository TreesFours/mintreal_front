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
    suspend fun getCandles(
        @Query("symbol") symbol: String,
        @Query("assetClass") assetClass: String,
        // Omitted (server defaults to "1day") for the existing 1M/3M/6M
        // timeframes, which still slice one daily fetch client-side. Only
        // 1h/4h (stock/forex/commodity only — crypto has no hourly data on
        // the server's free tier) pass this to trigger a fresh intraday fetch.
        @Query("interval") interval: String? = null,
        // Only ever set for a crypto symbol found via search() below that
        // isn't in the server's hand-curated COINGECKO_ID_MAP.
        @Query("coingeckoId") coingeckoId: String? = null
    ): MarketCandlesResponse

    // "All tickers" without proactively polling hundreds of symbols — see
    // searchSymbols' comment in marketDataService.ts.
    @GET("api/markets/search")
    suspend fun searchSymbols(@Query("q") query: String): SymbolSearchResponse

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
    // "crypto" | "forex" | "tickers" — groups this quote into one of the
    // Market screen's three scrolling ticker rows; absent only for quotes
    // from code paths outside the shared watchlist (there are none today).
    val band: String? = null,
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

data class SymbolSearchResult(
    val symbol: String,
    val name: String,
    val assetClass: String,
    val coingeckoId: String? = null
)

data class SymbolSearchResponse(
    val success: Boolean,
    val results: List<SymbolSearchResult> = emptyList(),
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
