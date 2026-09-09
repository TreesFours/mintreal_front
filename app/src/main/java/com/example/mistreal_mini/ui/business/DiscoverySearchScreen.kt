package com.example.mistreal_mini.ui.business

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.local.entity.BusinessEntity
import com.example.mistreal_mini.ui.chat.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverySearchScreen(
    onBack: () -> Unit,
    onContactBusiness: (BusinessEntity) -> Unit,
    viewModel: BusinessViewModel = hiltViewModel()
) {
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LOCAL DISCOVERY") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { /* viewModel search query updated internally */ },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search city (e.g. Lagos, Abuja)...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(12.dp)
            )

            // Category Bar
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BusinessCategories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { /* Update viewmodel cat */ },
                        label = { Text(cat) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Mandatory Safety Protocol
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GppMaybe, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("TACTICAL SAFETY: Inspect items in person before payment.", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(results) { business ->
                    BusinessResultCard(business, onContact = { onContactBusiness(business) })
                }
            }
        }
    }
}

@Composable
fun BusinessResultCard(business: BusinessEntity, onContact: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Gray)) {
                    AsyncImage(model = business.logoUrl, contentDescription = null, contentScale = ContentScale.Crop)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(business.name, fontWeight = FontWeight.Bold)
                    Text(business.address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Surface(
                    color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("VERIFIED TODAY", color = Color(0xFF4CAF50), fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(4.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Text(business.description ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 2)
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onContact,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Chat, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("INITIATE SECURE CHAT")
            }
        }
    }
}
