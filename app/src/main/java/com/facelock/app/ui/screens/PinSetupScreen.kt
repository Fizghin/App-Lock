package com.facelock.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.facelock.app.security.PinManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(
    pinManager: PinManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val currentPin = if (isConfirming) confirmPin else firstPin

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (pinManager.isPinSet()) "Change Backup PIN" else "Setup Backup PIN", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (!isConfirming) "Enter a 4-digit PIN" else "Confirm your 4-digit PIN",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                // PIN indicator dots
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 0 until 4) {
                        val filled = i < currentPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (filled) Color(0xFF6366F1) else Color(0xFF334155))
                        )
                    }
                }

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage,
                        color = Color(0xFFEF4444),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Keypad
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "DEL")
                )

                for (row in keys) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        for (key in row) {
                            if (key.isEmpty()) {
                                Spacer(modifier = Modifier.size(68.dp))
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        errorMessage = ""
                                        if (key == "DEL") {
                                            if (isConfirming) {
                                                if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                                            } else {
                                                if (firstPin.isNotEmpty()) firstPin = firstPin.dropLast(1)
                                            }
                                        } else {
                                            if (!isConfirming) {
                                                if (firstPin.length < 4) {
                                                    firstPin += key
                                                    if (firstPin.length == 4) {
                                                        isConfirming = true
                                                    }
                                                }
                                            } else {
                                                if (confirmPin.length < 4) {
                                                    confirmPin += key
                                                    if (confirmPin.length == 4) {
                                                        if (confirmPin == firstPin) {
                                                            pinManager.setPin(confirmPin)
                                                            Toast.makeText(context, "Backup PIN Set Successfully!", Toast.LENGTH_SHORT).show()
                                                            onBack()
                                                        } else {
                                                            errorMessage = "PINs do not match. Try again."
                                                            firstPin = ""
                                                            confirmPin = ""
                                                            isConfirming = false
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(68.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0xFF1E293B),
                                        contentColor = Color.White
                                    )
                                ) {
                                    if (key == "DEL") {
                                        Icon(Icons.Default.Backspace, contentDescription = "Delete")
                                    } else {
                                        Text(key, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
