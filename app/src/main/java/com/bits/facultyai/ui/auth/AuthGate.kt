package com.bits.facultyai.ui.auth

import com.bits.facultyai.data.auth.AuthState
import com.bits.facultyai.data.prefs.AppSettings

/** Which composition root MainActivity should show. */
enum class AuthGate { LOADING, LOGIN, APP }

/**
 * The single decision of what the user sees at launch. Pure, so the gating
 * rules are unit-testable:
 *
 *  - signed in always wins
 *  - otherwise guest mode (or an unconfigured Firebase) goes straight to the app
 *  - otherwise the login screen
 */
fun resolveGate(settings: AppSettings?, authState: AuthState): AuthGate = when {
    settings == null || authState is AuthState.Loading -> AuthGate.LOADING
    authState is AuthState.SignedIn -> AuthGate.APP
    authState is AuthState.Unconfigured -> AuthGate.APP
    settings.guestMode -> AuthGate.APP
    else -> AuthGate.LOGIN
}
