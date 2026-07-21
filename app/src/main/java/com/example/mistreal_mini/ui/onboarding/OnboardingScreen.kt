package com.example.mistreal_mini.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.mistreal_mini.util.FaceGuard

@Composable
fun OnboardingScreen(
    faceGuard: FaceGuard,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Face,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Welcome to Mistreal AI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Let's set up your Face Guard to keep your social data secure.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline
        )
        
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = {
                faceGuard.authenticateOwner(
                    activity = context as FragmentActivity,
                    onSuccess = onComplete,
                    onFailure = { errorMessage = it }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Register My Face")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Skip for now", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        }
    }
}
