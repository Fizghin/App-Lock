package com.facelock.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.facelock.app.data.AppLockRepository
import com.facelock.app.security.PinManager
import com.facelock.app.service.AppLockService
import com.facelock.app.ui.screens.DashboardScreen
import com.facelock.app.ui.screens.FaceEnrollmentScreen
import com.facelock.app.ui.screens.PinSetupScreen
import com.facelock.app.ui.theme.DarkColorScheme

class MainActivity : ComponentActivity() {

    private lateinit var appLockRepository: AppLockRepository
    private lateinit var pinManager: PinManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appLockRepository = AppLockRepository(this)
        pinManager = PinManager(this)

        startForegroundLockService()

        setContent {
            androidx.compose.material3.MaterialTheme(
                colorScheme = DarkColorScheme
            ) {
                MainAppNavigation(appLockRepository, pinManager)
            }
        }
    }

    private fun startForegroundLockService() {
        val intent = Intent(this, AppLockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@Composable
fun MainAppNavigation(
    appLockRepository: AppLockRepository,
    pinManager: PinManager
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") {
            DashboardScreen(
                appLockRepository = appLockRepository,
                pinManager = pinManager,
                onNavigateToEnrollFace = { navController.navigate("enroll_face") },
                onNavigateToSetupPin = { navController.navigate("setup_pin") }
            )
        }
        composable("enroll_face") {
            FaceEnrollmentScreen(
                appLockRepository = appLockRepository,
                onBack = { navController.popBackStack() }
            )
        }
        composable("setup_pin") {
            PinSetupScreen(
                pinManager = pinManager,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
