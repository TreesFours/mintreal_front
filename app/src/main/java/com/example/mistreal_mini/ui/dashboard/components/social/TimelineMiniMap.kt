package com.example.mistreal_mini.ui.dashboard.components.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mistreal_mini.data.model.SocialPost

@Composable
fun TimelineMiniMap(
    posts: List<SocialPost>,
    currentIndex: Int,
    onIndexSelected: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        listState.animateScrollToItem(currentIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(vertical = 8.dp)
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(posts) { index, post ->
                val isSelected = index == currentIndex
                
                Box(
                    modifier = Modifier
                        .size(width = 50.dp, height = 60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onIndexSelected(index) }
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(post.platformIcon, fontSize = 18.sp)
                        Text(
                            text = (index + 1).toString(),
                            fontSize = 10.sp,
                            color = if (isSelected) Color.White else Color.Gray
                        )
                    }
                }
            }
        }
    }
}
