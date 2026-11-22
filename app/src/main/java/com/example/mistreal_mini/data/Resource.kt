package com.example.mistreal_mini.data

sealed class Resource<T>(val data: T? = null, val message: String? = null) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
    class Loading<T>(data: T? = null) : Resource<T>(data)
}

/**
 * Was: the same try { ... Resource.Success/Error ... } catch (e: Exception) {
 * Resource.Error(e.message ?: "...") } shape copy-pasted across every
 * repository (109 occurrences across 12 files at last count). [block] still
 * returns the Resource itself — any business-logic Resource.Error it builds
 * (e.g. "user not found") passes through unchanged; this only catches a
 * THROWN exception and turns it into a Resource.Error with [fallbackMessage]
 * when the exception itself has no message.
 */
suspend fun <T> safeApiCall(
    fallbackMessage: String = "Request failed",
    // Most call sites also logged the exception via Timber before this
    // existed — passing a tag here preserves that instead of silently
    // dropping it during the migration to this shared wrapper. Always logs
    // even without one (falling back to fallbackMessage as the tag), since a
    // caller forgetting to pass logTag used to mean the exception vanished
    // with zero trace anywhere — that was true for 89 of ~100 call sites.
    logTag: String? = null,
    block: suspend () -> Resource<T>
): Resource<T> {
    return try {
        block()
    } catch (e: retrofit2.HttpException) {
        // Was: e.message, which for HttpException is just the generic HTTP
        // status line ("HTTP 500 Internal Server Error") — every backend
        // route in this app actually returns a real {"error": "..."} JSON
        // body on failure (socialRoutes.ts etc.), but that body lives on
        // the exception's errorBody(), not .message, so it was getting
        // silently discarded in favor of a useless status line at every one
        // of this function's ~110 call sites.
        val backendMessage = extractBackendErrorMessage(e)
        timber.log.Timber.e(e, logTag ?: fallbackMessage)
        Resource.Error(backendMessage ?: e.message() ?: fallbackMessage)
    } catch (e: Exception) {
        timber.log.Timber.e(e, logTag ?: fallbackMessage)
        Resource.Error(e.message ?: fallbackMessage)
    }
}

private fun extractBackendErrorMessage(e: retrofit2.HttpException): String? {
    return try {
        val body = e.response()?.errorBody()?.string() ?: return null
        val json = org.json.JSONObject(body)
        json.optString("error").takeIf { it.isNotBlank() }
            ?: json.optString("message").takeIf { it.isNotBlank() }
    } catch (parseError: Exception) {
        null
    }
}
