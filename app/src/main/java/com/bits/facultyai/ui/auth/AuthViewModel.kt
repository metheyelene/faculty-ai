package com.bits.facultyai.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bits.facultyai.data.auth.AuthError
import com.bits.facultyai.data.auth.AuthIntent
import com.bits.facultyai.data.auth.AuthRepository
import com.bits.facultyai.data.auth.AuthState
import com.bits.facultyai.data.auth.AuthValidator
import com.bits.facultyai.data.auth.FirebaseAuthSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which form the login screen shows. */
enum class AuthMode { SIGN_IN, SIGN_UP, RESET_PASSWORD }

/** Mutable UI state of the login screen. */
data class LoginUi(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val googleBusy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
) {
    val busy: Boolean get() = submitting || googleBusy
}

/**
 * Owns the login screen state and the auth-state gate. On sign-up, the user
 * is signed out after account creation: they must verify their email before
 * the first sign-in, which keeps every workspace tied to a verified address.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AuthRepository(application, FirebaseAuthSource())

    val authState: StateFlow<AuthState> = repo.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)

    private val _ui = MutableStateFlow(LoginUi())
    val ui: StateFlow<LoginUi> = _ui.asStateFlow()

    fun setMode(mode: AuthMode) = update { it.copy(mode = mode, error = null, notice = null) }
    fun setName(v: String) = update { it.copy(name = v, error = null) }
    fun setEmail(v: String) = update { it.copy(email = v, error = null) }
    fun setPassword(v: String) = update { it.copy(password = v, error = null) }
    fun dismissError() = update { it.copy(error = null) }
    fun dismissNotice() = update { it.copy(notice = null) }

    /** Final-gate validation before any submit; inline errors stay in the UI via [AuthValidator]. */
    private fun LoginUi.validate(): String? = when (mode) {
        AuthMode.SIGN_IN -> when {
            !AuthValidator.emailValid(email) -> "Enter a valid email address"
            else -> AuthValidator.signInPasswordError(password)
        }
        AuthMode.SIGN_UP -> when {
            AuthValidator.nameError(name) != null -> "Enter your name"
            !AuthValidator.emailValid(email) -> "Enter a valid email address"
            else -> AuthValidator.newPasswordError(password) ?: if (password.isEmpty()) "Choose a password" else null
        }
        AuthMode.RESET_PASSWORD ->
            if (!AuthValidator.emailValid(email)) "Enter a valid email address" else null
    }

    fun submitEmail() {
        val current = _ui.value
        val invalid = current.validate()
        if (invalid != null) {
            _ui.value = current.copy(error = invalid)
            return
        }
        val intent = when (current.mode) {
            AuthMode.SIGN_IN -> AuthIntent.SignIn(current.email, current.password)
            AuthMode.SIGN_UP -> AuthIntent.SignUp(current.name, current.email, current.password)
            AuthMode.RESET_PASSWORD -> AuthIntent.ResetPassword(current.email)
        }
        viewModelScope.launch {
            _ui.value = current.copy(submitting = true, error = null, notice = null)
            try {
                when (val result = repo.submit(intent)) {
                    is com.bits.facultyai.data.auth.AuthResult.Success ->
                        if (intent is AuthIntent.SignUp) {
                            // Enforce email verification before first entry.
                            repo.signOut()
                            _ui.value = _ui.value.copy(
                                submitting = false,
                                password = "",
                                notice = "Account created — check ${current.email.trim()} to verify, then sign in",
                            )
                        } else {
                            // authState flips to SignedIn; the gate navigates home.
                            _ui.value = _ui.value.copy(submitting = false)
                        }
                    is com.bits.facultyai.data.auth.AuthResult.ResetSent ->
                        _ui.value = _ui.value.copy(
                            submitting = false,
                            notice = "Password reset link sent to ${current.email.trim()}",
                        )
                }
            } catch (e: AuthError) {
                _ui.value = _ui.value.copy(submitting = false, error = e.message)
            }
        }
    }

    fun signInWithGoogle() {
        if (_ui.value.googleBusy) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(googleBusy = true, error = null, notice = null)
            try {
                when (val credential = repo.requestGoogleCredential()) {
                    is com.bits.facultyai.data.auth.GoogleCredential.Resolved ->
                        repo.submit(credential.intent) // SignedIn flips via authState
                    is com.bits.facultyai.data.auth.GoogleCredential.Cancelled -> Unit
                    is com.bits.facultyai.data.auth.GoogleCredential.Failed ->
                        _ui.value = _ui.value.copy(error = credential.message)
                }
            } catch (e: AuthError) {
                _ui.value = _ui.value.copy(error = e.message)
            } finally {
                _ui.value = _ui.value.copy(googleBusy = false)
            }
        }
    }

    private fun update(reducer: (LoginUi) -> LoginUi) {
        _ui.value = reducer(_ui.value)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                AuthViewModel(app)
            }
        }
    }
}
