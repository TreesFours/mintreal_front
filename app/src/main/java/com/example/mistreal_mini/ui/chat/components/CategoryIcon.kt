package com.example.mistreal_mini.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CategoryIcon(
    icon: ImageVector, 
    label: String, 
    isSelected: Boolean, 
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Icon(
                    icon, 
                    null, 
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
                if (badgeCount > 0) {
                    Badge(
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp),
                        containerColor = Color.Red
                    ) {
                        Text(badgeCount.toString(), color = Color.White, fontSize = 8.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                label.uppercase(), 
                fontSize = 8.sp, 
                fontWeight = FontWeight.Black,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
            )
        }
    }
}
