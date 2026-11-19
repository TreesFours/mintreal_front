package com.example.mistreal_mini.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 5-Button Instagram-Style Bottom Navigation Bar
 * Positioned directly below the compact ChatInputBar:
 * 1. Games (Mini-games list & launches)
 * 2. Market (Buy-Sell, Watchlist, Financial channels)
 * 3. Live Reels (Scrolling short video reels & live streamers feed)
 * 4. Watch Hub (Screencast host/viewer & media broadcast)
 * 5. Main Chat / DM (AI Chat thread & Direct Messages)
 */
@Composable
fun InstagramStyleBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 12.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Games (Mini-Games List)
            IconButton(onClick = { onTabSelected(0) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SportsEsports,
                        "Games",
                        tint = if (selectedTab == 0) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "GAMES",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }

            // 2. Market (Financial Watchlist / Buy-Sell)
            IconButton(onClick = { onTabSelected(1) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Storefront,
                        "Market",
                        tint = if (selectedTab == 1) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "MARKET",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }

            // 3. Live Reels (Short Video Reels & Streamers)
            IconButton(onClick = { onTabSelected(2) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Movie,
                        "Reels",
                        tint = if (selectedTab == 2) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "REELS",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 2) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }

            // 4. Watch Hub (Screencast & Media Share)
            IconButton(onClick = { onTabSelected(3) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Cast,
                        "Watch Hub",
                        tint = if (selectedTab == 3) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "WATCH",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 3) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }

            // 5. Main Chat / DM
            IconButton(onClick = { onTabSelected(4) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ChatBubble,
                        "Chats",
                        tint = if (selectedTab == 4) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "CHATS",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 4) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
        }
    }
}
