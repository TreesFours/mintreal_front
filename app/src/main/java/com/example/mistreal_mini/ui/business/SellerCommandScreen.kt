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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    viewModel: BusinessViewModel = hiltViewModel()
) {
    val myBusiness by viewModel.myBusiness.collectAsStateWithLifecycle()
    val isVerifying by viewModel.isVerifying.collectAsStateWithLifecycle()
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
                onRegister = { name, desc, cat, addr, lat, lon ->
                    viewModel.registerBusiness(name, desc, cat, addr, lat, lon)
                },
                onVerifyLocation = { viewModel.verifyCurrentLocation() },
                modifier = Modifier.padding(padding)
            )
        } else {
            InventoryManager(
                business = myBusiness!!,
                viewModel = viewModel,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
fun BusinessRegistrationForm(
    isVerifying: Boolean,
    onRegister: (String, String, String, String, Double, Double) -> Unit,
    onVerifyLocation: suspend () -> Location?,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(BusinessCategories[0]) }
    var address by remember { mutableStateOf("") }
    var verifiedLoc by remember { mutableStateOf<Location?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Deploy Your Business", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Business Name") }, modifier = Modifier.fillMaxWidth())
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

        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Physical Address") }, modifier = Modifier.fillMaxWidth())
        
        Button(
            onClick = { scope.launch { verifiedLoc = onVerifyLocation() } },
            modifier = Modifier.fillMaxWidth(),
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

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onRegister(name, desc, category, address, verifiedLoc?.latitude ?: 0.0, verifiedLoc?.longitude ?: 0.0) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = name.isNotBlank() && verifiedLoc != null,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("SECURE REGISTRATION", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun InventoryManager(
    business: BusinessEntity,
    viewModel: BusinessViewModel,
    modifier: Modifier = Modifier
) {
    val items by viewModel.getInventory(business.businessId).collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddItem by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(business.name.uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("Verified: ${java.text.SimpleDateFormat("HH:mm").format(business.verifiedTimestamp)} Today", color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                ItemTile(item)
            }
        }
    }

    if (showAddItem) {
        AddItemDialog(
            onDismiss = { showAddItem = false },
            onAdd = { name, price, uri ->
                viewModel.addInventoryItem(name, price, uri?.toString(), business.businessId)
                showAddItem = false
            }
        )
    }
}

@Composable
fun ItemTile(item: com.example.mistreal_mini.data.local.entity.BusinessItemEntity) {
    Card(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box {
            AsyncImage(
                model = item.imageUrl,
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
                Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                item.price?.let { Text(it, color = Color.Green, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }
        }
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
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Item Name") })
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price/Estimate") })
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(name, price, imageUri) }, enabled = name.isNotBlank()) { Text("DEPLOY") }
        }
    )
}
