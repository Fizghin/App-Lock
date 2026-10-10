package com.facelock.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.facelock.app.data.AppLockRepository
import com.facelock.app.ui.overlay.LockOverlayActivity

class AppLockAccessibilityService : AccessibilityService() {

    private lateinit var appLockRepository: AppLockRepository
    private var lastUnlockedPackage: String? = null
    private var lastUnlockedTime: Long = 0L

    companion object {
        var isServiceRunning = false
            private set

        fun notifyUnlocked(packageName: String) {
            instance?.lastUnlockedPackage = packageName
            instance?.lastUnlockedTime = System.currentTimeMillis()
        }

        private var instance: AppLockAccessibilityService? = null
    }

    override fun onCreate() {
        super.onCreate()
        appLockRepository = AppLockRepository(this)
        instance = this
        isServiceRunning = true
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        if (instance == this) {
            instance = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return

        // Skip our own app package
        if (packageName == applicationContext.packageName) {
            return
        }

        // Check if app is locked in repository
        if (appLockRepository.isAppLocked(packageName)) {
            // Check if recently unlocked within 3 seconds grace period
            val currentTime = System.currentTimeMillis()
            if (packageName == lastUnlockedPackage && (currentTime - lastUnlockedTime < 3000)) {
                return
            }

            // Launch lock overlay
            val intent = Intent(this, LockOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(LockOverlayActivity.EXTRA_LOCKED_PACKAGE, packageName)
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() {
        // Handle interruption
    }
}
