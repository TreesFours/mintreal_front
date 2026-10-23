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
    } catch (e: Exception) {
        timber.log.Timber.e(e, logTag ?: fallbackMessage)
        Resource.Error(e.message ?: fallbackMessage)
    }
}
