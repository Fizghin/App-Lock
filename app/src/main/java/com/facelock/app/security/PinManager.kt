package com.facelock.app.security

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import kotlin.math.max

class PinManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "facelock_pin_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_TIMESTAMP = "lockout_timestamp"

        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 30_000L // 30 seconds
    }

    fun isPinSet(): Boolean {
        return !prefs.getString(KEY_PIN_HASH, null).isNullOrEmpty()
    }

    fun setPin(pin: String): Boolean {
        if (pin.length < 4) return false
        val hash = hashPin(pin)
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_TIMESTAMP, 0L)
            .apply()
        return true
    }

    fun verifyPin(pin: String): Boolean {
        if (isLockedOut()) return false

        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val inputHash = hashPin(pin)

        return if (storedHash == inputHash) {
            resetAttempts()
            true
        } else {
            recordFailedAttempt()
            false
        }
    }

    fun isLockedOut(): Boolean {
        val lockoutTime = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0L)
        if (lockoutTime == 0L) return false

        val elapsed = System.currentTimeMillis() - lockoutTime
        if (elapsed >= LOCKOUT_DURATION_MS) {
            resetAttempts()
            return false
        }
        return true
    }

    fun getRemainingAttempts(): Int {
        val failed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        return max(0, MAX_ATTEMPTS - failed)
    }

    fun getLockoutRemainingSeconds(): Long {
        val lockoutTime = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0L)
        if (lockoutTime == 0L) return 0L
        val elapsed = System.currentTimeMillis() - lockoutTime
        val remaining = LOCKOUT_DURATION_MS - elapsed
        return if (remaining > 0) remaining / 1000 else 0L
    }

    fun resetAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_TIMESTAMP, 0L)
            .apply()
    }

    private fun recordFailedAttempt() {
        val failed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val edit = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, failed)
        if (failed >= MAX_ATTEMPTS) {
            edit.putLong(KEY_LOCKOUT_TIMESTAMP, System.currentTimeMillis())
        }
        edit.apply()
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
