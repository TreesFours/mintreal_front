package com.example.mistreal_mini.ui.business

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.api.RemoteBusiness
import com.example.mistreal_mini.ui.dashboard.components.ads.AdMediaView

/**
 * Previously there was no dedicated business profile view at all — a search
 * result card only had a Contact button. Hosts everything the user asked
 * for: the business's own ad looping as a profile banner (same Ad model
 * already built, just displayed here instead of in feed rotation), owner
 * identity, the confirmed-meetups trust count + up/down tally + written
 * testimonials, and a Share action gated on this device actually having a
 * confirmed, successful meetup with this business.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessDetailScreen(
    businessId: String,
    onBack: () -> Unit,
    onContactBusiness: (RemoteBusiness) -> Unit,
    viewModel: BusinessViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val detail by viewModel.businessDetail.collectAsStateWithLifecycle()
    val canShare by viewModel.canShare.collectAsStateWithLifecycle()
    val ad by viewModel.businessAd.collectAsStateWithLifecycle()

    LaunchedEffect(businessId) { viewModel.fetchBusinessDetail(businessId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.business?.name ?: "Business Profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        val business = detail?.business
        if (business == null) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Ad banner — loops on its own (AdMediaView) until the owner
            // replaces it with a new ad; absent entirely if none is active.
            ad?.let {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                        AdMediaView(ad = it, modifier = Modifier.fillMaxSize())
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.Gray)) {
                            AsyncImage(model = business.logoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(business.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Text(business.category, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    business.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

                    Spacer(modifier = Modifier.height(16.dp))
                    // Owner identity — either their live-captured Verified
                    // Face or a separate upload (ownerPhotoUrl either way,
                    // resolved at the point the owner set it).
                    if (business.ownerName != null || business.ownerPhotoUrl != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.DarkGray)) {
                                business.ownerPhotoUrl?.let {
                                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Owned by ${business.ownerName ?: "this profile's registrant"}", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${detail?.confirmedMeetupsCount ?: 0} confirmed meetups", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Icon(Icons.Default.ThumbUp, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                        Text(" ${detail?.upvotes ?: 0}", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(Icons.Default.ThumbDown, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                        Text(" ${detail?.downvotes ?: 0}", fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onContactBusiness(business) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Chat, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Contact")
                        }
                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Check out ${business.name} on Mistreal — business ID: ${business.businessId}")
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share ${business.name}"))
                            },
                            enabled = canShare,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                    if (!canShare) {
                        Text(
                            "You can share this business once you've had a confirmed meetup with them.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            val testimonials = detail?.testimonials.orEmpty()
            if (testimonials.isNotEmpty()) {
                item {
                    Text(
                        "TESTIMONIALS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                items(testimonials) { testimonial ->
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                        Text(testimonial.reviewText ?: "", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
