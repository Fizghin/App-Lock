package com.facelock.app.ui.overlay

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.facelock.app.data.AppLockRepository
import com.facelock.app.ml.FaceNetEngine
import com.facelock.app.security.PinManager
import com.facelock.app.service.AppLockAccessibilityService
import com.facelock.app.service.AppLockService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

class LockOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_LOCKED_PACKAGE = "extra_locked_package"
    }

    private lateinit var pinManager: PinManager
    private lateinit var appLockRepository: AppLockRepository
    private var lockedPackageName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pinManager = PinManager(this)
        appLockRepository = AppLockRepository(this)
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
    onUnlockSuccess: () -> Unit,
    onCloseApp: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val faceNetEngine = remember { FaceNetEngine() }
    val savedTemplate = remember { appLockRepository.getFaceEmbedding() }

    var mode by remember { mutableStateOf(if (appLockRepository.isFaceEnrolled()) OverlayMode.FACE else OverlayMode.PIN) }
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var hasCameraPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(mode) {
        if (mode == OverlayMode.FACE) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
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
                    text = if (mode == OverlayMode.FACE) "Position face in scanner to unlock" else "Enter backup PIN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (mode == OverlayMode.FACE) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .border(3.dp, Color(0xFF6366F1), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasCameraPermission) {
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    val executor = ContextCompat.getMainExecutor(ctx)

                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }

                                        val faceDetector = FaceDetection.getClient(
                                            FaceDetectorOptions.Builder()
                                                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                                                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                                                .build()
                                        )

                                        val imageAnalyzer = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            .also { analysis ->
                                                analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                                    @Suppress("UnsafeOptInUsageError")
                                                    val mediaImage = imageProxy.image
                                                    if (mediaImage != null) {
                                                        val image = InputImage.fromMediaImage(
                                                            mediaImage,
                                                            imageProxy.imageInfo.rotationDegrees
                                                        )
                                                        faceDetector.process(image)
                                                            .addOnSuccessListener { faces ->
                                                                val detectedFace = faces.firstOrNull()
                                                                if (detectedFace != null && savedTemplate != null) {
                                                                    val liveVector = faceNetEngine.extractLandmarkVector(detectedFace)
                                                                    if (liveVector != null && faceNetEngine.match(liveVector, savedTemplate)) {
                                                                        onUnlockSuccess()
                                                                    }
                                                                }
                                                            }
                                                            .addOnCompleteListener {
                                                                imageProxy.close()
                                                            }
                                                    } else {
                                                        imageProxy.close()
                                                    }
                                                }
                                            }

                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                                preview,
                                                imageAnalyzer
                                            )
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }, executor)

                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = "Face Scanner",
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }
                    }
                } else {
                    // PIN Entry Keypad
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Display PIN Dots
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

                        // Numeric Keypad Grid
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
