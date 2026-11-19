package com.example.mistreal_mini.ui.profile

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.api.Review
import com.example.mistreal_mini.data.api.SocialStat
import com.example.mistreal_mini.data.model.PlatformIcons
import com.example.mistreal_mini.ui.needs.NeedsViewModel

data class ProfileFriend(val id: String, val name: String, val avatarUrl: String?, val occupation: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerProfileScreen(
    revieweeDeviceId: String,
    ownerName: String?,
    ownerPhotoUrl: String?,
    businessId: String?,
    onBack: () -> Unit,
    onCallClick: (String) -> Unit = {},
    viewModel: ReviewViewModel = hiltViewModel(),
    needsViewModel: NeedsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val reviews by viewModel.reviews.collectAsState()
    val socialStats by viewModel.socialStats.collectAsState()
    var showWriteReview by remember { mutableStateOf(false) }
    var expandedReviewId by remember { mutableStateOf<Int?>(null) }

    val isOwnProfile = revieweeDeviceId == viewModel.myDeviceId

    var currentPhotoUrl by remember { mutableStateOf(ownerPhotoUrl) }
    var occupationText by remember { mutableStateOf("Tactical Specialist") }
    var locationTownText by remember { mutableStateOf("Lagos, Nigeria") }
    var isEditingProfile by remember { mutableStateOf(false) }

    var userPhotos by remember { mutableStateOf(mutableStateListOf<String>()) }
    var showFullPhotoGrid by remember { mutableStateOf(false) }
    var enlargedPhotoUrl by remember { mutableStateOf<String?>(null) }
    var showFriendsList by remember { mutableStateOf(false) }

    val connectedFriends = socialStats.map {
        ProfileFriend(it.platform, it.platform.uppercase() + " User", null, "${it.followers ?: it.friends ?: 0} Connections")
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            currentPhotoUrl = uri.toString()
            userPhotos.add(uri.toString())
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { userPhotos.add(it.toString()) }
    }

    LaunchedEffect(revieweeDeviceId) {
        viewModel.loadProfile(revieweeDeviceId)
        if (isOwnProfile) needsViewModel.loadMySkills()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isOwnProfile) "My Profile" else (ownerName ?: "Profile")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (isOwnProfile) {
                        IconButton(onClick = { isEditingProfile = !isEditingProfile }) {
                            Icon(if (isEditingProfile) Icons.Default.Check else Icons.Default.Edit, contentDescription = "Edit Profile")
                        }
                    } else {
                        IconButton(onClick = { onCallClick(revieweeDeviceId) }) {
                            Icon(Icons.Default.VideoCall, contentDescription = "Call")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isOwnProfile) {
                ExtendedFloatingActionButton(
                    onClick = { showWriteReview = true },
                    icon = { Icon(Icons.Default.RateReview, null) },
                    text = { Text("Leave a Review") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dual User + Business Hub Header Card (Glassmorphic)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // User Profile Avatar (Left)
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Color.DarkGray)
                                .clickable(enabled = isOwnProfile) { photoPickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!currentPhotoUrl.isNullOrBlank()) {
                                AsyncImage(model = currentPhotoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(36.dp))
                            }
                            if (isOwnProfile) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // User Info (Center)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ownerName ?: "This User", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            Text(occupationText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("📍 $locationTownText", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }

                        // Business Hub Avatar / Storefront Link (Right)
                        if (!businessId.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable { /* Navigate to Business Storefront */ },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Storefront, "Business Storefront", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Text("STORE", fontSize = 8.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // Live Stream Broadcast Button
            item {
                Button(
                    onClick = { onCallClick(revieweeDeviceId) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CellTower, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isOwnProfile) "GO LIVE STREAM NOW" else "WATCH LIVE BROADCAST", fontWeight = FontWeight.Black)
                }
            }

            // Editable Profile Details (Owner Mode)
            if (isEditingProfile) {
                item {
                    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("EDIT PROFILE DETAILS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            OutlinedTextField(
                                value = occupationText,
                                onValueChange = { occupationText = it },
                                label = { Text("Occupation / Role") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = locationTownText,
                                onValueChange = { locationTownText = it },
                                label = { Text("City / Town") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Star Rep Rating Button (Visiting another profile)
            if (!isOwnProfile) {
                item {
                    var hasRated by remember { mutableStateOf(false) }
                    var starCount by remember { mutableIntStateOf((4500..6500).random()) }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Yellow.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, "Star Rep", tint = Color.Yellow, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("REPUTATION SCORE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text("$starCount STARS", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.Yellow)
                                }
                            }

                            Button(
                                onClick = {
                                    if (!hasRated) {
                                        hasRated = true
                                        starCount += 1
                                    }
                                },
                                enabled = !hasRated,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.StarRate, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (hasRated) "RATED ⭐" else "RATE USER", fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Connected Social Handles & Badges
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("CONNECTED SOCIAL CHANNELS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            if (isOwnProfile) {
                                TextButton(onClick = { /* Add Platform */ }) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Platform", fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(socialStats) { stat ->
                                AssistChip(
                                    onClick = { /* Disconnect option if own profile */ },
                                    label = { Text(stat.platform.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = PlatformIcons.forPlatform(stat.platform) ?: Icons.Default.Link,
                                            contentDescription = stat.platform,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Friends / Connected Profiles (First Green Line in Screenshot)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showFriendsList = !showFriendsList },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.People, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("FRIENDS & CONNECTED PROFILES (${connectedFriends.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Icon(if (showFriendsList) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                        }

                        if (showFriendsList) {
                            Spacer(modifier = Modifier.height(12.dp))
                            connectedFriends.forEach { friend ->
                                ListItem(
                                    leadingContent = {
                                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Gray)) {
                                            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
                                        }
                                    },
                                    headlineContent = { Text(friend.name, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                                    supportingContent = { Text(friend.occupation ?: "Mistreal User", fontSize = 10.sp, color = Color.Gray) }
                                )
                            }
                        }
                    }
                }
            }

            // User Photo Gallery (Second Green Section in Screenshot)
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PHOTO GALLERY", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        if (isOwnProfile) {
                            TextButton(onClick = { galleryPickerLauncher.launch("image/*") }) {
                                Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload Photos", fontSize = 11.sp)
                            }
                        }
                    }

                    if (userPhotos.isEmpty()) {
                        Text("No photos uploaded yet.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            items(userPhotos.take(4)) { photoUri ->
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { enlargedPhotoUrl = photoUri }
                                ) {
                                    AsyncImage(model = photoUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                            if (userPhotos.size > 4) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .clickable { showFullPhotoGrid = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("+${userPhotos.size - 4} MORE", fontWeight = FontWeight.Black, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Skills Section
            if (isOwnProfile) {
                item {
                    Text("SKILLS YOU OFFER", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    SkillsEditor(needsViewModel)
                }
            }

            // Reviews Section (Third Green Box in Screenshot)
            item {
                Text("REVIEWS & TESTIMONIALS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }

            if (reviews.isEmpty()) {
                item { Text("No reviews yet.", color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp)) }
            } else {
                items(reviews) { review ->
                    ReviewCard(
                        review = review,
                        isExpanded = expandedReviewId == review.id,
                        onToggleExpand = { expandedReviewId = if (expandedReviewId == review.id) null else review.id },
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    // Full Photo Gallery Dialog
    if (showFullPhotoGrid) {
        Dialog(onDismissRequest = { showFullPhotoGrid = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(450.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ALL GALLERY PHOTOS", fontWeight = FontWeight.Black)
                        IconButton(onClick = { showFullPhotoGrid = false }) { Icon(Icons.Default.Close, null) }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize().padding(top = 8.dp)
                    ) {
                        items(userPhotos) { photoUri ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { enlargedPhotoUrl = photoUri }
                            ) {
                                AsyncImage(model = photoUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }

    // Enlarged Photo Viewer
    if (enlargedPhotoUrl != null) {
        Dialog(onDismissRequest = { enlargedPhotoUrl = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { enlargedPhotoUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(model = enlargedPhotoUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)))
            }
        }
    }

    if (showWriteReview) {
        WriteReviewDialog(
            revieweeDeviceId = revieweeDeviceId,
            businessId = businessId,
            onDismiss = { showWriteReview = false },
            viewModel = viewModel
        )
    }
}

@Composable
fun WriteReviewDialog(
    revieweeDeviceId: String,
    businessId: String?,
    onDismiss: () -> Unit,
    viewModel: ReviewViewModel
) {
    var reviewText by remember { mutableStateOf("") }
    val isRecording by viewModel.isRecording
    val recordedFile by viewModel.recordedFile
    val isSubmitting by viewModel.isSubmitting.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("LEAVE A REVIEW", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = reviewText,
                    onValueChange = { reviewText = it },
                    placeholder = { Text("Write your review or feedback...") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (isRecording) viewModel.stopRecording() else viewModel.startRecording()
                        }) {
                            Icon(Icons.Default.Mic, "Voice Note", tint = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary)
                        }
                        Text(if (isRecording) "Recording..." else if (recordedFile != null) "Voice Attached 🎙️" else "Attach Voice", fontSize = 12.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (reviewText.isNotBlank() || recordedFile != null) {
                                viewModel.submitReview(revieweeDeviceId, businessId, reviewText, onDone = {})
                                onDismiss()
                            }
                        },
                        enabled = !isSubmitting && (reviewText.isNotBlank() || recordedFile != null)
                    ) {
                        if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        else Text("Submit Review")
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillsEditor(viewModel: NeedsViewModel) {
    val skills by viewModel.mySkills.collectAsState()
    var newSkill by remember { mutableStateOf("") }

    Column {
        if (skills.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                skills.forEach { skill ->
                    AssistChip(
                        onClick = { viewModel.deleteSkill(skill.id) },
                        label = { Text(skill.skillLabel) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newSkill,
                onValueChange = { newSkill = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("e.g. electrician") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (newSkill.isNotBlank()) {
                        viewModel.addSkill(newSkill)
                        newSkill = ""
                    }
                })
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = {
                if (newSkill.isNotBlank()) {
                    viewModel.addSkill(newSkill)
                    newSkill = ""
                }
            }) { Text("Add") }
        }
        Text(
            "Matches people posting \"I need...\" requests near you — see the Needs screen in Settings.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun ReviewCard(review: Review, isExpanded: Boolean, onToggleExpand: () -> Unit, viewModel: ReviewViewModel) {
    val threadMessages by viewModel.threadMessages.collectAsState()
    var replyText by remember { mutableStateOf("") }

    LaunchedEffect(isExpanded) {
        if (isExpanded) viewModel.loadThread(review.id)
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onToggleExpand() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (review.status == "deleted") {
                Text(
                    "Removed — ${review.deletedReason ?: "no reason given"}",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.Gray)) {
                        Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(review.contentText?.take(40) ?: "Verified Review", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(review.createdAt ?: "Recently", fontSize = 10.sp, color = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                review.contentText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                if (isExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    threadMessages.forEach { msg ->
                        Text("${msg.senderDeviceId.take(8)}: ${msg.contentText}", fontSize = 11.sp, color = Color.LightGray)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Reply to review...", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            singleLine = true
                        )
                        IconButton(onClick = {
                            if (replyText.isNotBlank()) {
                                viewModel.sendReply(review.id, replyText)
                                replyText = ""
                            }
                        }) {
                            Icon(Icons.Default.Send, null)
                        }
                    }
                }
            }
        }
    }
}
