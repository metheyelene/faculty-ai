package com.bits.facultyai.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import java.io.IOException

/**
 * Thin source over [FirebaseAuth]. Translates Firebase shapes to the app's
 * [AuthUser] vocabulary and Firebase exceptions to [AuthError]. No UI types.
 *
 * A missing Firebase config is a configuration error, not a runtime surprise —
 * fail fast with a message that names the fix (see docs/firebase-setup.md).
 */
class FirebaseAuthSource {

    private val auth: FirebaseAuth by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: IllegalStateException) {
            throw IllegalStateException(
                "Firebase is not configured: google-services.json is missing. " +
                    "See docs/firebase-setup.md.",
                e,
            )
        }
    }

    /** True when the Firebase config is present and Auth is usable. */
    val isAvailable: Boolean get() = runCatching { auth }.isSuccess

    val current: AuthUser? get() = auth.currentUser?.toAuthUser()

    /** Registers a state listener and returns it for later removal. */
    fun authStateListener(listener: (AuthUser?) -> Unit): FirebaseAuth.AuthStateListener =
        FirebaseAuth.AuthStateListener { listener(it.currentUser?.toAuthUser()) }
            .also { auth.addAuthStateListener(it) }

    fun removeAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        auth.removeAuthStateListener(listener)
 }

    suspend fun signUp(name: String, email: String, password: String): AuthUser {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: throw AuthError.General("Account creation failed — try again")
        user.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build(),
        ).await()
        user.sendEmailVerification().await()
        return user.toAuthUser()
    }

    suspend fun signIn(email: String, password: String): AuthUser =
        auth.signInWithEmailAndPassword(email.trim(), password).await()
            .user?.toAuthUser() ?: throw AuthError.General("Sign-in failed — try again")

    suspend fun signInWithGoogle(idToken: String): AuthUser =
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .await().user?.toAuthUser() ?: throw AuthError.General("Google sign-in failed — try again")

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() = auth.signOut()
}

/** Outcomes of [AuthRepository.submit] — the UI never sees raw exceptions. */
sealed interface AuthResult {
    data class Success(val user: AuthUser) : AuthResult

    /** Password-reset email accepted for delivery. */
    data object ResetSent : AuthResult
}

internal fun mapAuthError(e: Throwable): AuthError = when {
    e is AuthError -> e
    e.message?.let {
        it.contains("OPERATION_NOT_ALLOWED") ||
            it.contains("sign-in provider is disabled", ignoreCase = true) ||
            it.contains("configuration is not supported", ignoreCase = true)
    } == true -> AuthError.General(
        "Email sign-in isn't enabled for this Firebase project yet",
    )
    e is com.google.firebase.FirebaseNetworkException || e is IOException -> AuthError.NoNetwork()
    e is FirebaseAuthInvalidUserException || e is FirebaseAuthInvalidCredentialsException ->
        AuthError.InvalidCredentials()
    e is FirebaseAuthUserCollisionException -> AuthError.EmailInUse()
    e is FirebaseAuthWeakPasswordException ->
        AuthError.General(e.reason?.takeIf { it.isNotBlank() } ?: "Choose a stronger password")
    else -> AuthError.General("Something went wrong — please try again")
}

private fun FirebaseUser.toAuthUser() = AuthUser(
    uid = uid,
    email = email,
    name = displayName,
    photoUrl = photoUrl?.toString(),
    isAnonymous = isAnonymous,
    emailVerified = isEmailVerified,
)
