package com.example.mistreal_mini.ui.business

import android.location.Location
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.local.entity.BusinessEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerCommandScreen(
    onBack: () -> Unit,
    onUpgradeClick: () -> Unit = {},
    onStartLiveView: (businessId: String) -> Unit = {},
    onOpenAds: (businessId: String, connectedAdPlatforms: List<String>) -> Unit = { _, _ -> },
    viewModel: BusinessViewModel = hiltViewModel()
) {
    val myBusiness by viewModel.myBusiness.collectAsStateWithLifecycle()
    val isVerifying by viewModel.isVerifying.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TACTICAL SHOPFRONT") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        if (myBusiness == null) {
            BusinessRegistrationForm(
                isVerifying = isVerifying,
                onRegister = { name, desc, cat, addr, lat, lon, platforms, ownerName ->
                    viewModel.registerBusiness(name, desc, cat, addr, lat, lon, platforms, ownerName)
                },
                onVerifyLocation = { viewModel.verifyCurrentLocation() },
                onReverseGeocode = { lat, lon -> viewModel.reverseGeocode(lat, lon) },
                modifier = Modifier.padding(padding)
            )
        } else {
            InventoryManager(
                business = myBusiness!!,
                viewModel = viewModel,
                isPro = isPro,
                onUpgradeClick = onUpgradeClick,
                onStartLiveView = onStartLiveView,
                onOpenAds = { onOpenAds(myBusiness!!.businessId, emptyList()) },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
fun BusinessRegistrationForm(
    isVerifying: Boolean,
    onRegister: (String, String, String, String, Double, Double, List<com.example.mistreal_mini.data.api.BusinessPlatformHandle>, String) -> Unit,
    onVerifyLocation: suspend () -> Location?,
    onReverseGeocode: suspend (Double, Double) -> String?,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(BusinessCategories[0]) }
    var address by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var verifiedLoc by remember { mutableStateOf<Location?>(null) }
    var platforms by remember { mutableStateOf(listOf<com.example.mistreal_mini.data.api.BusinessPlatformHandle>()) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Deploy Your Business", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)

        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Business Name") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        OutlinedTextField(
            value = ownerName, onValueChange = { ownerName = it }, label = { Text("Your Name (shown as the owner)") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("What do you offer?") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        
        // Category Dropdown Simplified
        Text("Tactical Sector (Category)", style = MaterialTheme.typography.labelMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BusinessCategories.take(4).forEach { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = { category = cat },
                    label = { Text(cat, fontSize = 10.sp) }
                )
            }
        }

        OutlinedTextField(
            value = address, onValueChange = { address = it }, label = { Text("Physical Address") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            // Mirrors the SECURE REGISTRATION button's own enabled check below,
            // so pressing Done only submits once the form is actually complete.
            keyboardActions = KeyboardActions(onDone = {
                if (name.isNotBlank() && address.isNotBlank() && verifiedLoc != null) {
                    onRegister(name, desc, category, address, verifiedLoc?.latitude ?: 0.0, verifiedLoc?.longitude ?: 0.0, platforms, ownerName)
                }
            })
        )

        Button(
            onClick = {
                scope.launch {
                    val loc = onVerifyLocation()
                    verifiedLoc = loc
                    // Auto-fill, don't clobber something the owner already typed.
                    if (loc != null && address.isBlank()) {
                        onReverseGeocode(loc.latitude, loc.longitude)?.let { address = it }
                    }
                }
            },
            // Same height as the final "SECURE REGISTRATION" button below —
            // these are the form's two key actions, previously mismatched.
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if(verifiedLoc != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary)
        ) {
            if (isVerifying) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            else {
                Icon(if(verifiedLoc != null) Icons.Default.CheckCircle else Icons.Default.MyLocation, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if(verifiedLoc != null) "LOCATION VERIFIED" else "VERIFY CURRENT LOCATION")
            }
        }

        if (verifiedLoc != null) {
            Text("Coordinates Secured: ${verifiedLoc!!.latitude}, ${verifiedLoc!!.longitude}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }

        Text("Where customers can message you", style = MaterialTheme.typography.labelMedium)
        Text(
            "A public @handle per platform — not a login. A customer resolves it to a real DM themselves when they tap Contact.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
        ConnectedPlatformsEditor(platforms = platforms, onChange = { platforms = it })

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onRegister(name, desc, category, address, verifiedLoc?.latitude ?: 0.0, verifiedLoc?.longitude ?: 0.0, platforms, ownerName) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = name.isNotBlank() && address.isNotBlank() && verifiedLoc != null,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("SECURE REGISTRATION", fontWeight = FontWeight.Bold)
        }
    }
}

private val BUSINESS_CONTACT_PLATFORMS = listOf(
    "instagram", "facebook", "whatsapp", "twitter", "telegram", "linkedin", "reddit", "discord"
)

@Composable
fun ConnectedPlatformsEditor(
    platforms: List<com.example.mistreal_mini.data.api.BusinessPlatformHandle>,
    onChange: (List<com.example.mistreal_mini.data.api.BusinessPlatformHandle>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        platforms.forEachIndexed { index, entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { menuExpanded = true }) { Text(entry.platform) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        BUSINESS_CONTACT_PLATFORMS.forEach { p ->
                            DropdownMenuItem(text = { Text(p) }, onClick = {
                                onChange(platforms.toMutableList().also { it[index] = entry.copy(platform = p) })
                                menuExpanded = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = entry.handle,
                    onValueChange = { newHandle ->
                        onChange(platforms.toMutableList().also { it[index] = entry.copy(handle = newHandle) })
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("@handle") },
                    singleLine = true,
                    // Live two-way binding to the platforms list above — there's no
                    // separate per-row "save" to wire Done into, so this just makes
                    // Enter dismiss the keyboard instead of inserting a newline.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                IconButton(onClick = { onChange(platforms.toMutableList().also { it.removeAt(index) }) }) {
                    Icon(Icons.Default.Close, null, tint = Color.Red.copy(alpha = 0.6f))
                }
            }
        }
        TextButton(onClick = { onChange(platforms + com.example.mistreal_mini.data.api.BusinessPlatformHandle(BUSINESS_CONTACT_PLATFORMS[0], "")) }) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("ADD PLATFORM")
        }
    }
}

@Composable
fun InventoryManager(
    business: BusinessEntity,
    viewModel: BusinessViewModel,
    isPro: Boolean = false,
    onUpgradeClick: () -> Unit = {},
    onStartLiveView: (businessId: String) -> Unit = {},
    onOpenAds: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val items by viewModel.getInventory(business.businessId).collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddItem by remember { mutableStateOf(false) }
    var showCreateAd by remember { mutableStateOf(false) }
    var showEditPlatforms by remember { mutableStateOf(false) }
    var showEditOwnerPhoto by remember { mutableStateOf(false) }
    var showEditBusinessLogo by remember { mutableStateOf(false) }
    var showEditBusinessInfo by remember { mutableStateOf(false) }
    val isCreatingAd by viewModel.isCreatingAd.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.adCreationResult.collect { result ->
            when (result) {
                is com.example.mistreal_mini.data.Resource.Success -> {
                    showCreateAd = false
                    snackbarHostState.showSnackbar("Ad launched — it'll start cycling into rotation.")
                }
                is com.example.mistreal_mini.data.Resource.Error -> {
                    snackbarHostState.showSnackbar(result.message ?: "Failed to launch ad")
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.inventoryActionResult.collect { result ->
            if (result is com.example.mistreal_mini.data.Resource.Error) {
                snackbarHostState.showSnackbar(result.message ?: "Inventory action failed")
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = business.logoUrl,
                    contentDescription = "Business Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showEditBusinessLogo = true }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(business.name.uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("Verified: ${java.text.SimpleDateFormat("HH:mm").format(business.verifiedTimestamp)} Today", color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                IconButton(onClick = { showEditBusinessInfo = true }) {
                    Icon(Icons.Default.Edit, "Edit Business Info", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { showEditOwnerPhoto = true }) {
                    Icon(Icons.Default.Face, "Edit Owner Photo", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { showEditPlatforms = true }) {
                    Icon(Icons.Default.Link, "Edit Contact Platforms", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { showCreateAd = true }) {
                    Icon(Icons.Default.Campaign, "Launch Ad", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp))
                }
                // Real paid campaigns on Meta/Google/TikTok etc. via Zernio —
                // distinct from "Launch Ad" above, which is this app's own
                // in-house self-promotion banner (AdHostViewModel/AdMediaView).
                IconButton(onClick = onOpenAds) {
                    Icon(Icons.Default.TrendingUp, "Business Ads", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = { onStartLiveView(business.businessId) }) {
                    Icon(Icons.Default.ScreenShare, "Start Live View", tint = Color.Red, modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = { showAddItem = true }) {
                    Icon(Icons.Default.AddBox, "Add Item", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("INTEL ASSETS (INVENTORY)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items) { item ->
                    ItemTile(item.name, item.price, item.imageUrl, onDelete = { viewModel.deleteInventoryItem(item) })
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showEditOwnerPhoto) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val verifiedFaces by viewModel.verifiedFaceUris.collectAsStateWithLifecycle()
        val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
            if (uri != null) {
                viewModel.uploadOwnerPhoto(business, uri, context)
                showEditOwnerPhoto = false
            }
        }
        AlertDialog(
            onDismissRequest = { showEditOwnerPhoto = false },
            title = { Text("Owner Photo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Use a live-captured Verified Face, or upload a separate photo.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    if (verifiedFaces.isNotEmpty()) {
                        Text("Verified Faces", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            verifiedFaces.forEach { (_, uri) ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp).clip(CircleShape).clickable {
                                        viewModel.uploadOwnerPhoto(business, uri, context)
                                        showEditOwnerPhoto = false
                                    }
                                )
                            }
                        }
                    }
                    Button(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Upload a Photo")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEditOwnerPhoto = false }) { Text("Close") } }
        )
    }

    if (showEditBusinessLogo) {
        val context = LocalContext.current
        val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                viewModel.uploadBusinessLogo(business, uri, context)
                showEditBusinessLogo = false
            }
        }
        AlertDialog(
            onDismissRequest = { showEditBusinessLogo = false },
            title = { Text("Business Logo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Shown wherever customers see this business — search results, profile, ads.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Button(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Upload a Photo")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEditBusinessLogo = false }) { Text("Close") } }
        )
    }

    if (showEditBusinessInfo) {
        var editName by remember { mutableStateOf(business.name) }
        var editCategory by remember { mutableStateOf(business.category) }
        var editAddress by remember { mutableStateOf(business.address) }
        var editLat by remember { mutableStateOf(business.latitude) }
        var editLon by remember { mutableStateOf(business.longitude) }
        var isReverifying by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEditBusinessInfo = false },
            title = { Text("Business Info") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editName, onValueChange = { editName = it }, label = { Text("Business Name") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Text("Tactical Sector (Category)", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BusinessCategories.forEach { cat ->
                            FilterChip(
                                selected = editCategory == cat,
                                onClick = { editCategory = cat },
                                label = { Text(cat, fontSize = 10.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = editAddress, onValueChange = { editAddress = it }, label = { Text("Physical Address") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (editName.isNotBlank() && editAddress.isNotBlank()) {
                                viewModel.updateBusinessInfo(business, editName, editCategory, editAddress, editLat, editLon)
                                showEditBusinessInfo = false
                            }
                        })
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isReverifying = true
                                val loc = viewModel.verifyCurrentLocation()
                                if (loc != null) {
                                    editLat = loc.latitude
                                    editLon = loc.longitude
                                    viewModel.reverseGeocode(loc.latitude, loc.longitude)?.let { editAddress = it }
                                }
                                isReverifying = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isReverifying) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        else {
                            Icon(Icons.Default.MyLocation, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RE-VERIFY CURRENT LOCATION")
                        }
                    }
                    Text("Coordinates: $editLat, $editLon", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateBusinessInfo(business, editName, editCategory, editAddress, editLat, editLon)
                        showEditBusinessInfo = false
                    },
                    enabled = editName.isNotBlank() && editAddress.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditBusinessInfo = false }) { Text("Cancel") } }
        )
    }

    if (showEditPlatforms) {
        var editedPlatforms by remember { mutableStateOf(emptyList<com.example.mistreal_mini.data.api.BusinessPlatformHandle>()) }
        LaunchedEffect(business.connectedPlatforms) {
            editedPlatforms = try {
                if (business.connectedPlatforms.isNullOrBlank()) emptyList()
                else com.google.gson.Gson().fromJson(
                    business.connectedPlatforms,
                    object : com.google.gson.reflect.TypeToken<List<com.example.mistreal_mini.data.api.BusinessPlatformHandle>>() {}.type
                )
            } catch (e: Exception) { emptyList() }
        }
        AlertDialog(
            onDismissRequest = { showEditPlatforms = false },
            title = { Text("Contact Platforms") },
            text = { ConnectedPlatformsEditor(platforms = editedPlatforms, onChange = { editedPlatforms = it }) },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateConnectedPlatforms(business, editedPlatforms)
                    showEditPlatforms = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditPlatforms = false }) { Text("Cancel") } }
        )
    }

    if (showAddItem) {
        val context = androidx.compose.ui.platform.LocalContext.current
        AddItemDialog(
            onDismiss = { showAddItem = false },
            onAdd = { name, price, uri ->
                viewModel.addInventoryItem(name, price, uri, business.businessId, context)
                showAddItem = false
            }
        )
    }

    if (showCreateAd) {
        com.example.mistreal_mini.ui.business.components.CreateAdDialog(
            isPro = isPro,
            isSubmitting = isCreatingAd,
            onDismiss = { showCreateAd = false },
            onUpgradeClick = { showCreateAd = false; onUpgradeClick() },
            onSubmit = { mediaType, durationSeconds, caption, targetUrl, videoUri, imageUris ->
                viewModel.createAd(
                    businessId = business.businessId,
                    mediaType = mediaType,
                    durationSeconds = durationSeconds,
                    caption = caption,
                    targetUrl = targetUrl.takeIf { it.isNotBlank() },
                    ctaLabel = "Learn More",
                    videoUri = videoUri,
                    imageUris = imageUris.takeIf { it.isNotEmpty() }
                )
            }
        )
    }
}

@Composable
fun ItemTile(name: String, price: String?, imageUrl: String?, showDelete: Boolean = true, onDelete: () -> Unit = {}) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                price?.let { Text(it, color = Color.Green, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }
            if (showDelete) {
                IconButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete item", tint = Color.White)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove this item?") },
            text = { Text("\"$name\" will be removed from your inventory for everyone viewing this business.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AddItemDialog(onDismiss: () -> Unit, onAdd: (String, String, Uri?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { imageUri = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Secure New Asset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.AddAPhoto, null, tint = Color.Gray)
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Item Name") },
                    singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = price, onValueChange = { price = it }, label = { Text("Price/Estimate") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (name.isNotBlank()) onAdd(name, price, imageUri)
                    })
                )
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(name, price, imageUri) }, enabled = name.isNotBlank()) { Text("DEPLOY") }
        }
    )
}
