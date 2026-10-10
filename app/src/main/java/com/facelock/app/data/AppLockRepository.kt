package com.facelock.app.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.facelock.app.ml.FaceNetEngine

data class AppItem(
    val packageName: String,
    val appName: String,
    val isLocked: Boolean
)

class AppLockRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "facelock_app_prefs"
        private const val KEY_LOCKED_PACKAGES = "locked_packages"
        private const val KEY_MASTER_LOCK_ENABLED = "master_lock_enabled"
        private const val KEY_FACE_EMBEDDING = "face_embedding_template"
        private const val KEY_FACE_ENROLLED = "face_enrolled"
    }

    fun isMasterLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_MASTER_LOCK_ENABLED, true)
    }

    fun setMasterLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_LOCK_ENABLED, enabled).apply()
    }

    fun getLockedPackages(): Set<String> {
        return prefs.getStringSet(KEY_LOCKED_PACKAGES, emptySet()) ?: emptySet()
    }

    fun isAppLocked(packageName: String): Boolean {
        if (!isMasterLockEnabled()) return false
        return getLockedPackages().contains(packageName)
    }

    fun setAppLocked(packageName: String, locked: Boolean) {
        val current = getLockedPackages().toMutableSet()
        if (locked) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_LOCKED_PACKAGES, current).apply()
    }

    fun getInstalledApps(): List<AppItem> {
        val pm = context.packageManager
        val lockedApps = getLockedPackages()

        val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val selfPackage = context.packageName

        return resolveInfos
            .mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == selfPackage) return@mapNotNull null // Don't lock self

                val appName = resolveInfo.loadLabel(pm).toString()
                AppItem(
                    packageName = pkg,
                    appName = appName,
                    isLocked = lockedApps.contains(pkg)
                )
            }
            .sortedBy { it.appName.lowercase() }
            .distinctBy { it.packageName }
    }

    fun saveFaceEmbedding(embedding: FloatArray) {
        val str = embedding.joinToString(",") { it.toString() }
        prefs.edit()
            .putString(KEY_FACE_EMBEDDING, str)
            .putBoolean(KEY_FACE_ENROLLED, true)
            .apply()
    }

    fun getFaceEmbedding(): FloatArray? {
        val str = prefs.getString(KEY_FACE_EMBEDDING, null) ?: return null
        return try {
            val parts = str.split(",")
            if (parts.size != FaceNetEngine.EMBEDDING_SIZE) return null
            parts.map { it.toFloat() }.toFloatArray()
        } catch (e: Exception) {
            null
        }
    }

    fun isFaceEnrolled(): Boolean {
        return prefs.getBoolean(KEY_FACE_ENROLLED, false) && getFaceEmbedding() != null
    }

    fun deleteFaceTemplate() {
        prefs.edit()
            .remove(KEY_FACE_EMBEDDING)
            .putBoolean(KEY_FACE_ENROLLED, false)
            .apply()
    }
}
