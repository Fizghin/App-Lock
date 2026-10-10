package com.facelock.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.facelock.app.data.AppLockRepository
import com.facelock.app.ui.overlay.LockOverlayActivity
import kotlinx.coroutines.*

class AppLockService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var appLockRepository: AppLockRepository
    private var lastForegroundPackage: String? = null
    private var lastUnlockedPackage: String? = null
    private var lastUnlockedTime: Long = 0L

    companion object {
        const val CHANNEL_ID = "facelock_service_channel"
        const val NOTIFICATION_ID = 1001

        fun notifyUnlocked(packageName: String) {
            // Set temporary session unlock
        }
    }

    override fun onCreate() {
        super.onCreate()
        appLockRepository = AppLockRepository(this)
        startForegroundServiceWithNotification()
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val unlockedPkg = intent?.getStringExtra("unlocked_package")
        if (!unlockedPkg.isNullOrEmpty()) {
            lastUnlockedPackage = unlockedPkg
            lastUnlockedTime = System.currentTimeMillis()
        }
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FaceLock Protection Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FaceLock is Active")
            .setContentText("Protecting locked applications")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startMonitoring() {
        serviceScope.launch {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            while (isActive) {
                val time = System.currentTimeMillis()
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    time - 1000 * 5,
                    time
                )

                if (!stats.isNullOrEmpty()) {
                    val recentStats = stats.maxByOrNull { it.lastTimeUsed }
                    val currentPackage = recentStats?.packageName

                    if (currentPackage != null && currentPackage != packageName && currentPackage != lastForegroundPackage) {
                        lastForegroundPackage = currentPackage

                        if (appLockRepository.isAppLocked(currentPackage)) {
                            val isRecentlyUnlocked = currentPackage == lastUnlockedPackage &&
                                    (System.currentTimeMillis() - lastUnlockedTime < 3000)

                            if (!isRecentlyUnlocked) {
                                launchLockOverlay(currentPackage)
                            }
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun launchLockOverlay(packageName: String) {
        val intent = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(LockOverlayActivity.EXTRA_LOCKED_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
