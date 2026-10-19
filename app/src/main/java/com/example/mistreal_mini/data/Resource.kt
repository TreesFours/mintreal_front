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
suspend fun <T> safeApiCall(fallbackMessage: String = "Request failed", block: suspend () -> Resource<T>): Resource<T> {
    return try {
        block()
    } catch (e: Exception) {
        Resource.Error(e.message ?: fallbackMessage)
    }
}
