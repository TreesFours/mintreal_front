package com.example.mistreal_mini.ui.startup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mistreal_mini.MainActivity
import com.example.mistreal_mini.ui.theme.MistrealTheme
import com.example.mistreal_mini.ui.theme.ObsidianBlack
import kotlinx.coroutines.delay

class PhoenixBootActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MistrealTheme(darkTheme = true) {
                BootSequence {
                    startActivity(Intent(this@PhoenixBootActivity, MainActivity::class.java))
                    finish()
                }
            }
        }
    }
}

@Composable
fun BootSequence(onComplete: () -> Unit) {
    val logs = remember { mutableStateListOf<String>() }
    var showLogo by remember { mutableStateOf(false) }
    var showMotto by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "gear")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "rotation"
    )

    LaunchedEffect(Unit) {
        delay(500)
        logs.add("[LOAD] CORE_SYSTEM_IGNITION...")
        delay(800)
        logs.add("[LINK] BACKEND_HANDSHAKE: SUCCESS")
        delay(600)
        logs.add("[SCAN] BIOMETRIC_SENTINEL: OK")
        delay(500)
        showLogo = true
        delay(1500)
        showMotto = true
        delay(2500)
        onComplete()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(ObsidianBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 🌀 THE GEAR (ENGINE)
            if (!showLogo) {
                Icon(
                    Icons.Default.SettingsInputComponent,
                    null,
                    modifier = Modifier.size(80.dp).graphicsLayer { rotationZ = rotation },
                    tint = Color(0xFFFF8C00)
                )
            }

            // 🔥 THE PHOENIX LOGO (Simplified for logic)
            AnimatedVisibility(
                visible = showLogo,
                enter = fadeIn() + scaleIn(initialScale = 0.8f) + expandVertically()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PHOENIX", fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF4500))
                    Text("IGNITION ACTIVE", fontSize = 10.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // 📜 SYSTEM LOGS
            Column(modifier = Modifier.height(100.dp)) {
                logs.forEach { log ->
                    Text(log, color = Color(0xFF4CAF50), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 🌬️ THE MOTTO
            AnimatedVisibility(visible = showMotto, enter = fadeIn(tween(1000))) {
                Text(
                    "Intelligence Liberated. Available to All. As Free as Air.",
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Light,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }
}
