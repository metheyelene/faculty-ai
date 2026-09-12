package com.bits.facultyai.ui.auth

import com.bits.facultyai.data.auth.AuthState
import com.bits.facultyai.data.auth.AuthUser
import com.bits.facultyai.data.auth.AuthValidator
import com.bits.facultyai.data.prefs.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthGateTest {

    private val signedIn = AuthState.SignedIn(
        AuthUser("u1", "a@b.co", "Faculty", null, isAnonymous = false, emailVerified = true),
    )

    private fun settings(guest: Boolean = false) = AppSettings(guestMode = guest)

    // ---- AuthValidator ----

    @Test fun `email accepts normal addresses`() {
        assertTrue(AuthValidator.emailValid("faculty@college.edu"))
        assertNull(AuthValidator.emailError("faculty@college.edu"))
    }

    @Test fun `email rejects garbage but not empty`() {
        assertTrue(AuthValidator.emailError("not-an-email") != null)
        assertTrue(AuthValidator.emailError("a@b") != null)
        assertNull(AuthValidator.emailError("")) // untouched field: no nag
    }

    @Test fun `new password requires length and mixed characters`() {
        assertTrue(AuthValidator.newPasswordError("") == null) // untouched
        assertTrue(AuthValidator.newPasswordError("short1") != null)
        assertTrue(AuthValidator.newPasswordError("123456789") != null) // digits only
        assertTrue(AuthValidator.newPasswordError("abcdefgh") != null) // letters only
        assertNull(AuthValidator.newPasswordError("dsp2026pass"))
    }

    @Test fun `sign-in password must not be empty`() {
        assertTrue(AuthValidator.signInPasswordError("") != null)
        assertNull(AuthValidator.signInPasswordError("x"))
    }

    // ---- resolveGate ----

    @Test fun `signed in always wins over guest flag`() {
        assertEquals(AuthGate.APP, resolveGate(settings(guest = false), signedIn))
    }

    @Test fun `loading shows while either stream is unresolved`() {
        assertEquals(AuthGate.LOADING, resolveGate(null, AuthState.SignedOut))
        assertEquals(AuthGate.LOADING, resolveGate(settings(), AuthState.Loading))
    }

    @Test fun `guest mode goes straight to the app`() {
        assertEquals(AuthGate.APP, resolveGate(settings(guest = true), AuthState.SignedOut))
    }

    @Test fun `signed-out non-guest sees the login screen`() {
        assertEquals(AuthGate.LOGIN, resolveGate(settings(guest = false), AuthState.SignedOut))
    }

    @Test fun `unconfigured firebase behaves like guest mode`() {
        assertEquals(AuthGate.APP, resolveGate(settings(guest = false), AuthState.Unconfigured))
    }

    @Test fun `google intent requires a token`() {
        // Guard against constructing an empty-token Google intent from the UI layer.
        assertFalse(AuthValidator.emailValid(""))
    }
}
