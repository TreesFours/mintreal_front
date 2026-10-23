package com.example.mistreal_mini.data.api

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.mistreal_mini.data.local.PreferenceManager
import com.example.mistreal_mini.util.NotificationChannels
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID = "api_errors"
private const val PEEK_BYTES = 4_096L

/**
 * Posts a local notification with the full failure detail for every failed
 * API call (network exception or non-2xx response) — same "read it later,
 * copy the exact text" purpose as a crash reporter, but local-only since
 * this app has no FCM wiring (see MarketAlertWorker/NewsWorker, same
 * local-notification-only pattern). Gated by a Settings toggle
 * (PreferenceManager.isErrorNotificationsEnabled, default on) since showing
 * raw backend error text isn't something a real end user should see by
 * default — only relevant while this is still effectively a single-developer
 * testing build.
 */
@Singleton
class ErrorNotifyInterceptor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        try {
            val response = chain.proceed(request)
            if (!response.isSuccessful) {
                val bodySnippet = try {
                    response.peekBody(PEEK_BYTES).string()
                } catch (e: Exception) {
                    "(couldn't read response body: ${e.message})"
                }
                val detail = bodySnippet.ifBlank { response.message.ifBlank { "No error detail returned." } }
                Timber.w("API error ${response.code} — ${request.method} ${request.url.encodedPath}: $detail")
                notify(
                    title = "API Error ${response.code} — ${request.method} ${request.url.encodedPath}",
                    detail = detail
                )
            }
            return response
        } catch (e: IOException) {
            Timber.e(e, "Network error — ${request.method} ${request.url.encodedPath}")
            notify(
                title = "Network error — ${request.method} ${request.url.encodedPath}",
                detail = e.message ?: e.toString()
            )
            throw e
        }
    }

    private fun notify(title: String, detail: String) {
        val enabled = try {
            runBlocking(Dispatchers.IO) { preferenceManager.isErrorNotificationsEnabled.first() }
        } catch (e: Exception) {
            true // fail open — never let a prefs read failure hide a real error
        }
        if (!enabled) return

        try {
            ensureChannel()
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Unique-ish id per error so a burst of distinct failures stacks
            // in the shade instead of overwriting each other.
            manager.notify((title.hashCode() xor System.currentTimeMillis().toInt()), notification)
        } catch (e: Exception) {
            Timber.e(e, "ErrorNotifyInterceptor: failed to post notification")
        }
    }

    private fun ensureChannel() {
        NotificationChannels.ensure(
            context, CHANNEL_ID, "API Errors", NotificationManager.IMPORTANCE_DEFAULT,
            description = "Shows the exact backend/network error when something fails, so it can be copied and shared for debugging."
        )
    }
}
