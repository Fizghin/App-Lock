package com.facelock.app.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PinManagerTest {

    private lateinit var pinManager: PinManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        pinManager = PinManager(context)
        pinManager.resetAttempts()
    }

    @Test
    fun testSetAndVerifyPin() {
        assertFalse(pinManager.isPinSet())
        assertTrue(pinManager.setPin("1234"))
        assertTrue(pinManager.isPinSet())
        assertTrue(pinManager.verifyPin("1234"))
        assertFalse(pinManager.verifyPin("9999"))
    }

    @Test
    fun testLockoutOnFailedAttempts() {
        pinManager.setPin("1234")
        for (i in 1..4) {
            assertFalse(pinManager.verifyPin("0000"))
            assertFalse(pinManager.isLockedOut())
        }
        // 5th failed attempt triggers lockout
        assertFalse(pinManager.verifyPin("0000"))
        assertTrue(pinManager.isLockedOut())
        assertFalse(pinManager.verifyPin("1234")) // Even correct PIN fails when locked out
    }
}
