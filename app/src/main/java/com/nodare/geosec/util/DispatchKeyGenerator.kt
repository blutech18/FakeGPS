package com.nodare.geosec.util

import java.security.SecureRandom

/**
 * Generates secure, time-limited dispatch keys for delivery confirmation.
 * 
 * As per the Secure Dispatch Key Protocol (manuscript specific objective #4):
 * - Only technicians with an approved dispatch key can confirm a successful delivery
 * - Keys are time-sensitive and used only once
 * - Ensures that only authorized people carry out sensitive transactions
 */
object DispatchKeyGenerator {

    private val secureRandom = SecureRandom()

    /**
     * Generates a 6-digit numeric dispatch key.
     * This key must be entered by the technician/driver to confirm delivery completion.
     */
    fun generateKey(): String {
        val code = secureRandom.nextInt(900000) + 100000 // 100000 to 999999
        return code.toString()
    }

    /**
     * Validates that the entered key matches the stored dispatch key.
     * @param enteredKey The key entered by the user
     * @param storedKey The key stored in the dispatch session
     * @return true if the keys match
     */
    fun validateKey(enteredKey: String, storedKey: String): Boolean {
        if (enteredKey.isBlank() || storedKey.isBlank()) return false
        return enteredKey.trim() == storedKey.trim()
    }
}
