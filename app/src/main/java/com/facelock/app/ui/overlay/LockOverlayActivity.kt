package com.facelock.app.ui.overlay

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.facelock.app.data.AppLockRepository
import com.facelock.app.security.BiometricPromptManager
import com.facelock.app.security.PinManager
import com.facelock.app.service.AppLockAccessibilityService
import com.facelock.app.service.AppLockService

class LockOverlayActivity : FragmentActivity() {

    companion object {
        const val EXTRA_LOCKED_PACKAGE = "extra_locked_package"
    }

    private lateinit var pinManager: PinManager
    private lateinit var appLockRepository: AppLockRepository
    private lateinit var biometricPromptManager: BiometricPromptManager
    private var lockedPackageName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pinManager = PinManager(this)
        appLockRepository = AppLockRepository(this)
        biometricPromptManager = BiometricPromptManager(this)
        lockedPackageName = intent.getStringExtra(EXTRA_LOCKED_PACKAGE) ?: ""

        val appName = try {
            val pm = packageManager
            val info = pm.getApplicationInfo(lockedPackageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            "Locked App"
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF6366F1),
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                LockOverlayScreen(
                    appName = appName,
                    pinManager = pinManager,
                    appLockRepository = appLockRepository,
                    biometricPromptManager = biometricPromptManager,
                    onUnlockSuccess = {
                        AppLockAccessibilityService.notifyUnlocked(lockedPackageName)
                        AppLockService.notifyUnlocked(lockedPackageName)
                        finish()
                    },
                    onCloseApp = {
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
                        finish()
                    }
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

@Composable
fun LockOverlayScreen(
    appName: String,
    pinManager: PinManager,
    appLockRepository: AppLockRepository,
    biometricPromptManager: BiometricPromptManager,
    onUnlockSuccess: () -> Unit,
    onCloseApp: () -> Unit
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(OverlayMode.FACE) }
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val triggerBiometrics = {
        if (biometricPromptManager.canAuthenticate()) {
            biometricPromptManager.showBiometricPrompt(
                title = "Unlock $appName",
                subtitle = "Scan face to verify identity",
                negativeButtonText = "Use Backup PIN",
                onSuccess = { onUnlockSuccess() },
                onError = { err ->
                    errorMessage = err
                    mode = OverlayMode.PIN
                }
            )
        } else {
            errorMessage = "Biometrics unavailable on device. Use Backup PIN."
            mode = OverlayMode.PIN
        }
    }

    LaunchedEffect(Unit) {
        if (mode == OverlayMode.FACE) {
            triggerBiometrics()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCloseApp) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                Text(
                    text = "App Locked",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.width(48.dp))
            }

            // Central content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = appName,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (mode == OverlayMode.FACE) "Biometric Face Recognition" else "Enter Backup PIN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (mode == OverlayMode.FACE) {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .border(3.dp, Color(0xFF6366F1), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Face Scanner",
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Scanning Face...",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { triggerBiometrics() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tap to Scan Face")
                    }
                } else {
                    // PIN Entry Keypad
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(bottom = 20.dp)
                        ) {
                            for (i in 0 until 4) {
                                val filled = i < pinInput.length
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (filled) Color(0xFF6366F1) else Color(0xFF334155))
                                )
                            }
                        }

                        if (errorMessage.isNotEmpty()) {
                            Text(
                                text = errorMessage,
                                color = Color(0xFFEF4444),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        val keys = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("", "0", "DEL")
                        )

                        for (row in keys) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                for (key in row) {
                                    if (key.isEmpty()) {
                                        Spacer(modifier = Modifier.size(60.dp))
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                errorMessage = ""
                                                if (key == "DEL") {
                                                    if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                                                } else if (pinInput.length < 4) {
                                                    pinInput += key
                                                    if (pinInput.length == 4) {
                                                        if (pinManager.verifyPin(pinInput)) {
                                                            onUnlockSuccess()
                                                        } else {
                                                            pinInput = ""
                                                            errorMessage = "Incorrect PIN. ${pinManager.getRemainingAttempts()} attempts left."
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(60.dp),
                                            shape = CircleShape,
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = Color(0xFF1E293B),
                                                contentColor = Color.White
                                            )
                                        ) {
                                            if (key == "DEL") {
                                                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
                                            } else {
                                                Text(key, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Footer Switch Mode
            TextButton(
                onClick = {
                    mode = if (mode == OverlayMode.FACE) OverlayMode.PIN else OverlayMode.FACE
                    errorMessage = ""
                    pinInput = ""
                    if (mode == OverlayMode.FACE) {
                        triggerBiometrics()
                    }
                }
            ) {
                Icon(
                    imageVector = if (mode == OverlayMode.FACE) Icons.Default.Pin else Icons.Default.Face,
                    contentDescription = null,
                    tint = Color(0xFF818CF8)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (mode == OverlayMode.FACE) "Use Backup PIN" else "Use Face Recognition",
                    color = Color(0xFF818CF8)
                )
            }
        }
    }
}

enum class OverlayMode {
    FACE, PIN
}
