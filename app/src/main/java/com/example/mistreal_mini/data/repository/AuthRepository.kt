package com.example.mistreal_mini.data.repository

import com.example.mistreal_mini.data.Resource
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

    suspend fun signInWithEmail(email: String, password: String): Resource<FirebaseUser> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            result.user?.let {
                Resource.Success(it)
            } ?: Resource.Error("User not found")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Sign in failed")
        }
    }

    suspend fun signUpWithEmail(email: String, password: String): Resource<FirebaseUser> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let {
                Resource.Success(it)
            } ?: Resource.Error("Sign up failed")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Sign up failed")
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }

    suspend fun getIdToken(): String? {
        return try {
            firebaseAuth.currentUser?.getIdToken(true)?.await()?.token
        } catch (e: Exception) {
            null
        }
    }
}
