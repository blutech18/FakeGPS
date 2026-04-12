package com.nodare.geosec.presentation.auth

/**
 * Typed login errors for reliable UI routing.
 * Each variant knows which field(s) it belongs to.
 */
sealed class LoginError(val message: String) {

    /** Errors that should highlight the email field */
    sealed class Email(message: String) : LoginError(message) {
        data object InvalidFormat : Email("Invalid email format. Please check your email address.")
        data object NotFound : Email("No account found with this email address.")
    }

    /** Errors that should highlight the password field */
    sealed class Password(message: String) : LoginError(message) {
        data object Wrong : Password("Incorrect password. Please try again.")
    }

    /** Errors that should highlight both fields (ambiguous credential error) */
    data object InvalidCredential : LoginError("Invalid email or password. Please check your credentials.")

    /** Errors shown as a general banner (not field-specific) */
    sealed class General(message: String) : LoginError(message) {
        data object AccountDisabled : General("This account has been disabled. Please contact support.")
        data object TooManyRequests : General("Too many failed attempts. Please try again later.")
        data object ProfileNotFound : General("User profile not found. Please contact support.")
        data object NetworkError : General("Connection failed. Please check your internet and try again.")
        data class Unknown(val msg: String) : General(msg)
    }

    companion object {
        /**
         * Maps a Firebase error code (or fallback message) to a typed LoginError.
         */
        fun fromFirebaseCode(errorCode: String?, fallbackMessage: String?): LoginError {
            return when (errorCode) {
                "ERROR_INVALID_EMAIL" -> Email.InvalidFormat
                "ERROR_WRONG_PASSWORD" -> Password.Wrong
                "ERROR_USER_NOT_FOUND" -> Email.NotFound
                "ERROR_USER_DISABLED" -> General.AccountDisabled
                "ERROR_TOO_MANY_REQUESTS" -> General.TooManyRequests
                "ERROR_INVALID_CREDENTIAL" -> InvalidCredential
                else -> General.Unknown(fallbackMessage ?: "Login failed. Please try again.")
            }
        }
    }
}
