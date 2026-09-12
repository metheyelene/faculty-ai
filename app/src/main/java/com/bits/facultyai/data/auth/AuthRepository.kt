package com.bits.facultyai.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Single owner of authentication state. The rest of the app observes
 * [authState] — nothing reads FirebaseAuth directly.
 *
 * When Firebase is not configured (no google-services.json), [authState]
 * emits [AuthState.Unconfigured] and the UI offers guest mode with a notice
 * instead of crashing.
 */
class AuthRepository(
    context: Context,
    private val source: FirebaseAuthSource,
) {
    private val appContext = context.applicationContext

    /** Cold flow of auth state; owners convert with `stateIn`. */
    val authState: Flow<AuthState> =
        if (source.isAvailable) {
            callbackFlow {
                trySend(source.current.toState())
                val listener = source.authStateListener { trySend(it.toState()) }
                awaitClose { source.removeAuthStateListener(listener) }
            }.distinctUntilChanged()
        } else {
            callbackFlow {
                trySend(AuthState.Unconfigured)
                awaitClose { }
            }
        }

    /**
     * Launches the native Google account chooser via Credential Manager.
     *
     * Only the basic profile and openID scopes are requested — no Gmail,
     * Drive, or Contacts access, by design. Returns the resolved intent to
     * feed into [submit], [GoogleCredential.Cancelled] if the user dismissed
     * the chooser, or [GoogleCredential.Failed] with a user-facing message.
     */
    suspend fun requestGoogleCredential(): GoogleCredential {
        val clientId = resolveWebClientId()
            ?: return GoogleCredential.Failed(
                "Google sign-in isn't configured on this build yet",
            )
        val credentialManager = CredentialManager.create(appContext)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(clientId)
                    .setFilterByAuthorizedAccounts(false) // always offer account creation
                    .setAutoSelectEnabled(false) // explicit consent each time
                    .build(),
            )
            .build()
        return try {
            val result = credentialManager.getCredential(appContext, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleCredential.Resolved(
                    AuthIntent.Google(GoogleIdTokenCredential.createFrom(credential.data).idToken),
                )
            } else {
                GoogleCredential.Failed("Unexpected Google response")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleCredential.Cancelled // user closed the chooser — not an error
        } catch (e: GetCredentialException) {
            GoogleCredential.Failed("Google sign-in failed — try again")
        } catch (e: GoogleIdTokenParsingException) {
            GoogleCredential.Failed("Unexpected Google response")
        }
    }

    /**
     * Runs an auth intent. Throws [AuthError] (never raw Firebase exceptions)
     * so the ViewModel maps one type into UI copy; rethrows
     * [CancellationException] untouched.
     */
    suspend fun submit(intent: AuthIntent): AuthResult = try {
        when (intent) {
            is AuthIntent.SignIn -> AuthResult.Success(source.signIn(intent.email, intent.password))
            is AuthIntent.SignUp -> AuthResult.Success(source.signUp(intent.name, intent.email, intent.password))
            is AuthIntent.Google -> AuthResult.Success(source.signInWithGoogle(intent.idToken))
            is AuthIntent.ResetPassword -> {
                source.sendPasswordReset(intent.email)
                AuthResult.ResetSent
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw mapAuthError(e)
    }

    /**
     * Signs out and clears Credential Manager state so the next Google
     * sign-in shows the full account chooser (account switching).
     */
    suspend fun signOut() {
        if (source.isAvailable) source.signOut()
        runCatching {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    /**
     * Resolves the OAuth web client ID that google-services generates as the
     * `default_web_client_id` resource. Null only on pre-config builds —
     * callers treat that as "Google sign-in not configured".
     */
    private fun resolveWebClientId(): String? = runCatching {
        val id = appContext.resources.getIdentifier(
            "default_web_client_id",
            "string",
            appContext.packageName,
        )
        if (id != 0) appContext.getString(id) else null
    }.getOrNull()

    private fun AuthUser?.toState(): AuthState =
        if (this == null) AuthState.SignedOut else AuthState.SignedIn(this)
}

/** Result of the Credential Manager chooser step. */
sealed interface GoogleCredential {
    data class Resolved(val intent: AuthIntent.Google) : GoogleCredential
    data object Cancelled : GoogleCredential
    data class Failed(val message: String) : GoogleCredential
}

/** Auth state consumed by the UI gate. */
sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState

    /** Firebase config missing — the app runs in guest-only mode. */
    data object Unconfigured : AuthState
}
