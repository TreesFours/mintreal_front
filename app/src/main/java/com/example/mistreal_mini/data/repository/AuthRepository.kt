package com.example.mistreal_mini.data.repository

import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.safeApiCall
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    // 🛡️ Zero-Defect Fix #1: Reactive Auth State
    val userState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        firebaseAuth.addAuthStateListener(listener)
        trySend(firebaseAuth.currentUser)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithEmail(email: String, password: String): Resource<FirebaseUser> = safeApiCall("Sign in failed") {
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        result.user?.let { Resource.Success(it) } ?: Resource.Error("User not found")
    }

    suspend fun signUpWithEmail(email: String, password: String): Resource<FirebaseUser> = safeApiCall("Sign up failed") {
        val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        result.user?.let { Resource.Success(it) } ?: Resource.Error("Sign up failed")
    }

    suspend fun sendPasswordResetEmail(email: String): Resource<Unit> = safeApiCall("Couldn't send reset email") {
        firebaseAuth.sendPasswordResetEmail(email).await()
        Resource.Success(Unit)
    }

    fun signOut() {
        firebaseAuth.signOut()
        cachedToken = null
        cachedTokenExpiryMillis = 0L
    }

    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedTokenExpiryMillis: Long = 0L

    // Refresh a bit before actual expiry so a request that starts right at
    // the boundary doesn't get handed a token that expires mid-flight.
    private val tokenRefreshSkewMillis = 60_000L

    /**
     * Was: forceRefresh=true unconditionally, meaning every single HTTP
     * request round-tripped to Firebase's token endpoint before the real
     * request could even start. Now: reads a cached token unless it's
     * missing/near-expiry, or the caller explicitly needs a fresh one
     * (e.g. after a 401 — see NetworkModule's interceptor).
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): String? {
        val now = System.currentTimeMillis()
        val cached = cachedToken
        if (!forceRefresh && cached != null && now < cachedTokenExpiryMillis - tokenRefreshSkewMillis) {
            return cached
        }
        return try {
            val user = firebaseAuth.currentUser ?: return null
            val result = user.getIdToken(forceRefresh).await()
            cachedToken = result.token
            cachedTokenExpiryMillis = result.expirationTimestamp * 1000
            result.token
        } catch (e: Exception) {
            null
        }
    }
}
