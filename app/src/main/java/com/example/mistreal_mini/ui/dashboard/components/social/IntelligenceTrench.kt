package com.example.mistreal_mini.ui.dashboard.components.social

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mistreal_mini.data.model.SocialComment

@Composable
fun IntelligenceTrench(comments: List<SocialComment>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        comments.forEach { comment ->
            CommentNode(comment = comment, depth = 0)
        }
    }
}

@Composable
fun CommentNode(comment: SocialComment, depth: Int) {
    var isExpanded by remember { mutableStateOf(depth < 1) } // Auto-expand top level
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 8.dp)) {
            // Tactical line if nested
            if (depth > 0) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(comment.author, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Signal Confirmed", fontSize = 8.sp, color = Color.Green.copy(alpha = 0.6f))
                }
                Text(comment.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FavoriteBorder, null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                        Text(comment.likes.toString(), fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
                    }
                    
                    if (comment.replies.isNotEmpty()) {
                        TextButton(
                            onClick = { isExpanded = !isExpanded },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text(
                                if (isExpanded) "Hide Intel" else "View ${comment.replies.size} Replies",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded && comment.replies.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                comment.replies.forEach { reply ->
                    CommentNode(comment = reply, depth = depth + 1)
                }
            }
        }
    }
}
