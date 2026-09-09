package com.example.mistreal_mini.ui.dashboard.components

import android.location.Address
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mistreal_mini.ui.dashboard.TacticalMapViewModel

@Composable
fun AmbiguousLocationDialog(
    locations: List<Address>,
    onSelect: (Address) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ambiguous Location") },
        text = {
            Column {
                Text("Multiple matches found. Select precise sector:", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                locations.forEach { addr ->
                    TextButton(onClick = { onSelect(addr) }) {
                        Text("${addr.locality ?: addr.adminArea}, ${addr.countryName}")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
