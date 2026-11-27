package com.example.terralock.auth

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

class AuthRepository {
    private val auth: FirebaseAuth? by lazy {
        runCatching { Firebase.auth }.getOrNull()
    }

    private val _isOfflineGuest = MutableStateFlow(false)
    val isOfflineGuest: StateFlow<Boolean> = _isOfflineGuest.asStateFlow()

    val currentUser: Flow<FirebaseUser?> = auth?.authStateChanged ?: flowOf(null)

    suspend fun signIn(email: String, pass: String): Result<FirebaseUser> = runCatching {
        val trimmedEmail = email.trim()
        require(trimmedEmail.isNotBlank()) { "Email cannot be empty" }
        require(pass.isNotBlank()) { "Password cannot be empty" }

        val firebaseAuth = auth ?: throw Exception("Firebase Auth is not available on this device")
        val result = firebaseAuth.signInWithEmailAndPassword(trimmedEmail, pass)
        val user = result.user ?: throw Exception("Authentication returned empty user profile")
        _isOfflineGuest.value = false
        user
    }

    suspend fun signUp(email: String, pass: String): Result<FirebaseUser> = runCatching {
        val trimmedEmail = email.trim()
        require(trimmedEmail.isNotBlank()) { "Email cannot be empty" }
        require(pass.isNotBlank()) { "Password cannot be empty" }

        val firebaseAuth = auth ?: throw Exception("Firebase Auth is not available on this device")
        val result = firebaseAuth.createUserWithEmailAndPassword(trimmedEmail, pass)
        val user = result.user ?: throw Exception("Registration returned empty user profile")
        _isOfflineGuest.value = false
        user
    }

    suspend fun signInWithGoogleCredential(idToken: String): Result<FirebaseUser> = runCatching {
        val firebaseAuth = auth ?: throw Exception("Firebase Auth is not available on this device")
        val credential = GoogleAuthProvider.credential(idToken, null)
        val result = firebaseAuth.signInWithCredential(credential)
        val user = result.user ?: throw Exception("Google Sign-In returned empty user profile")
        _isOfflineGuest.value = false
        user
    }

    suspend fun signOut() {
        _isOfflineGuest.value = false
        auth?.signOut()
    }

    fun continueOffline() {
        _isOfflineGuest.value = true
    }
}