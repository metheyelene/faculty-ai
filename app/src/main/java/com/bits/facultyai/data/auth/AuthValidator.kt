package com.bits.facultyai.data.auth

/**
 * Pure validation rules for the auth forms — no Android/Firebase types so the
 * rules are unit-testable and the single source of truth for what the UI
 * accepts.
 */
object AuthValidator {

    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun emailError(email: String): String? = when {
        email.isBlank() -> null // don't nag an untouched field
        !EMAIL_REGEX.matches(email.trim()) -> "Enter a valid email address"
        else -> null
    }

    /**
     * Password rule for signing up: [FirebaseAuth's minimum][6 chars] plus a
     * practical strength floor. Returns null when acceptable.
     */
    fun newPasswordError(password: String): String? = when {
        password.isEmpty() -> null
        password.length < 8 -> "Use at least 8 characters"
        !password.any { it.isLetter() } || !password.any { it.isDigit() } ->
            "Mix letters and numbers"
        else -> null
    }

    fun signInPasswordError(password: String): String? =
        if (password.isEmpty()) "Enter your password" else null

    fun nameError(name: String): String? = when {
        name.isBlank() -> null
        name.trim().length < 2 -> "That name looks too short"
        else -> null
    }

    fun emailValid(email: String): Boolean = emailError(email) == null && email.isNotBlank()
}
