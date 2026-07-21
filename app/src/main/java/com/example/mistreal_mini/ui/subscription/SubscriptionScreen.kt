package com.example.mistreal_mini.ui.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    viewModel: SubscriptionViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val isLoading = viewModel.isLoading.value

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Choose Your Plan") },
                navigationIcon = {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Unlock the full power of Mistreal AI",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            SubscriptionCard(
                title = "AI Plus",
                price = "$9.99/mo",
                features = listOf("Unlimited Gemini", "Proactive Rain & News Alerts", "High-Driven Images"),
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = { viewModel.subscribe("ai_plus") }
            )

            SubscriptionCard(
                title = "Social Plus",
                price = "$14.99/mo",
                features = listOf("Full Social Sync (FB, IG, X)", "AI Social Summaries", "Automatic Posting"),
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { viewModel.subscribe("social_plus") }
            )

            SubscriptionCard(
                title = "Elite Assistant",
                price = "$19.99/mo",
                features = listOf("Everything in AI & Social", "GPT-4 & Claude 3.5 Access", "Priority Support"),
                color = Color(0xFFFFD700).copy(alpha = 0.2f),
                isElite = true,
                onClick = { viewModel.subscribe("elite") }
            )
            
            if (isLoading) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun SubscriptionCard(
    title: String,
    price: String,
    features: List<String>,
    color: Color,
    isElite: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = color,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (isElite) {
                    Surface(
                        color = Color(0xFFFFD700),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("BEST VALUE", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(price, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            features.forEach { feature ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(feature, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Select Plan")
            }
        }
    }
}
