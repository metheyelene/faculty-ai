package com.bits.facultyai.data.auth

/** App-level identity. Sourced from FirebaseAuth; nothing here is client-invented. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val name: String?,
    val photoUrl: String?,
    val isAnonymous: Boolean,
    val emailVerified: Boolean,
)

/** What the user picked on the login screen. */
sealed interface AuthIntent {
    data class SignIn(val email: String, val password: String) : AuthIntent
    data class SignUp(val name: String, val email: String, val password: String) : AuthIntent
    data class ResetPassword(val email: String) : AuthIntent

    /** Resolved Google credential from the Credential Manager flow. */
    data class Google(val idToken: String) : AuthIntent
}

/** User-facing failure states — Firebase internals never leak into the UI. */
sealed class AuthError(override val message: String) : Exception(message) {
    data class InvalidCredentials(override val message: String = "Email or password is incorrect") : AuthError(message)
    data class EmailInUse(override val message: String = "An account with this email already exists") : AuthError(message)
    data class NoNetwork(override val message: String = "You're offline — connect and try again") : AuthError(message)
    data class Unverified(override val message: String) : AuthError(message)
    data class GoogleFailed(override val message: String) : AuthError(message)
    data class General(override val message: String) : AuthError(message)
}
