package com.example.mistreal_mini.data.repository

import android.content.Context
import android.provider.Settings
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.CreateMarketAlertRequest
import com.example.mistreal_mini.data.api.MarketAlert
import com.example.mistreal_mini.data.api.MarketApiService
import com.example.mistreal_mini.data.api.MarketCandle
import com.example.mistreal_mini.data.api.MarketQuote
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketRepository @Inject constructor(
    private val api: MarketApiService,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context
) {
    private val deviceId: String
        get() = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    suspend fun getWatchlist(): Resource<List<MarketQuote>> {
        return try {
            val response = api.getWatchlist()
            if (response.success) Resource.Success(response.quotes)
            else {
                Timber.w("MarketRepository.getWatchlist: backend returned success=false — %s", response.error)
                Resource.Error(response.error ?: "Markets unavailable")
            }
        } catch (e: Exception) {
            Timber.e(e, "MarketRepository.getWatchlist failed")
            Resource.Error(e.message ?: "Markets unavailable")
        }
    }

    // In-memory per-session cache — the backend already caches candles per
    // symbol per day, this just avoids a redundant network round-trip if the
    // user reopens the same chart twice in one app session.
    private val candleCache = mutableMapOf<String, List<MarketCandle>>()

    suspend fun getCandles(symbol: String, assetClass: String): Resource<List<MarketCandle>> {
        val cacheKey = "$assetClass:$symbol"
        candleCache[cacheKey]?.let { return Resource.Success(it) }
        return try {
            val response = api.getCandles(symbol, assetClass)
            if (response.success) {
                candleCache[cacheKey] = response.candles
                Resource.Success(response.candles)
            } else {
                Timber.w("MarketRepository.getCandles(%s, %s): success=false — %s", symbol, assetClass, response.error)
                Resource.Error(response.error ?: "Chart data unavailable")
            }
        } catch (e: Exception) {
            Timber.e(e, "MarketRepository.getCandles(%s, %s) failed", symbol, assetClass)
            Resource.Error(e.message ?: "Chart data unavailable")
        }
    }

    suspend fun createAlert(symbol: String, assetClass: String, direction: String, targetPrice: Double): Resource<MarketAlert> {
        return try {
            val response = api.createAlert(
                CreateMarketAlertRequest(
                    deviceId = deviceId,
                    firebaseUid = authRepository.currentUser?.uid,
                    symbol = symbol,
                    assetClass = assetClass,
                    direction = direction,
                    targetPrice = targetPrice
                )
            )
            if (response.success && response.alert != null) Resource.Success(response.alert)
            else {
                Timber.w("MarketRepository.createAlert(%s): success=false — %s", symbol, response.error)
                Resource.Error(response.error ?: "Could not create alert")
            }
        } catch (e: Exception) {
            Timber.e(e, "MarketRepository.createAlert(%s) failed", symbol)
            Resource.Error(e.message ?: "Could not create alert")
        }
    }

    suspend fun getAlerts(): Resource<List<MarketAlert>> {
        return try {
            val response = api.getAlerts(deviceId)
            if (response.success) Resource.Success(response.alerts)
            else {
                Timber.w("MarketRepository.getAlerts: success=false — %s", response.error)
                Resource.Error(response.error ?: "Could not load alerts")
            }
        } catch (e: Exception) {
            Timber.e(e, "MarketRepository.getAlerts failed")
            Resource.Error(e.message ?: "Could not load alerts")
        }
    }

    suspend fun deleteAlert(id: Int): Resource<Unit> {
        return try {
            val response = api.deleteAlert(id)
            if (response.success) Resource.Success(Unit)
            else {
                Timber.w("MarketRepository.deleteAlert(%d): success=false — %s", id, response.error)
                Resource.Error(response.error ?: "Could not remove alert")
            }
        } catch (e: Exception) {
            Timber.e(e, "MarketRepository.deleteAlert(%d) failed", id)
            Resource.Error(e.message ?: "Could not remove alert")
        }
    }

    suspend fun getPendingAlerts(): Resource<List<MarketAlert>> {
        return try {
            val response = api.getPendingAlerts(deviceId)
            if (response.success) Resource.Success(response.alerts)
            else {
                Timber.w("MarketRepository.getPendingAlerts: success=false — %s", response.error)
                Resource.Error(response.error ?: "Could not check alerts")
            }
        } catch (e: Exception) {
            Timber.e(e, "MarketRepository.getPendingAlerts failed")
            Resource.Error(e.message ?: "Could not check alerts")
        }
    }

    suspend fun acknowledgeAlert(id: Int): Resource<Unit> {
        return try {
            api.acknowledgeAlert(id)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "MarketRepository.acknowledgeAlert(%d) failed", id)
            Resource.Error(e.message ?: "Could not acknowledge alert")
        }
    }
}
