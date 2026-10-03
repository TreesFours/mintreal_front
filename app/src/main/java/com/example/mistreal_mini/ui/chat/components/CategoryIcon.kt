package com.example.mistreal_mini.ui.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    tintColor: Color? = null,
    onClick: () -> Unit
) {
    val activeColor = tintColor ?: MaterialTheme.colorScheme.primary
    val pillColor by animateColorAsState(
        targetValue = if (isSelected) activeColor.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(200),
        label = "pill_color"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else Color.Gray,
        animationSpec = tween(200),
        label = "icon_color"
    )
    val iconSize by animateDpAsState(
        targetValue = if (isSelected) 26.dp else 22.dp,
        animationSpec = tween(200),
        label = "icon_size"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(pillColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(iconSize))
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
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                color = iconColor,
                maxLines = 1
            )
        }
    }
}
