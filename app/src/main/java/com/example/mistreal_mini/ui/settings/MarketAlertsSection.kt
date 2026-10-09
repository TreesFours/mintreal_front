package com.example.mistreal_mini.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mistreal_mini.data.api.MarketAlert

private val ASSET_CLASSES = listOf("stock" to "Stock", "crypto" to "Crypto", "commodity" to "Commodity")
private val DIRECTIONS = listOf("above" to "Rises above", "below" to "Falls below")

/**
 * "A handful of majors everyone sees" (free shared watchlist) + per-user price
 * alerts (first one free, more require Pro — enforced server-side, this UI
 * just surfaces whatever error comes back). Lives in its own accordion
 * section since it's a genuinely separate feature from the AI provider
 * sections around it.
 */
@Composable
fun MarketAlertsSection(viewModel: SettingsViewModel) {
    val watchlist by viewModel.marketWatchlist.collectAsState()
    val alerts by viewModel.marketAlerts.collectAsState()
    val isSaving by viewModel.isSavingMarketAlert.collectAsState()

    var symbol by remember { mutableStateOf("") }
    var assetClass by remember { mutableStateOf(ASSET_CLASSES[0].first) }
    var direction by remember { mutableStateOf(DIRECTIONS[0].first) }
    var targetPrice by remember { mutableStateOf("") }
    var assetClassMenuExpanded by remember { mutableStateOf(false) }
    var directionMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.fetchMarketWatchlist()
        viewModel.fetchMarketAlerts()
    }

    SettingsSection(title = "MARKET WATCH & ALERTS", icon = Icons.Default.ShowChart) {
        if (watchlist.isNotEmpty()) {
            Text("TODAY'S WATCHLIST", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    watchlist.forEach { quote ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { viewModel.openChart(quote.symbol, quote.assetClass ?: "stock") }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(quote.symbol, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            val price = quote.price
                            val change = quote.changePercent
                            Text(
                                if (price != null) "${quote.currency ?: "USD"} %.2f".format(price) + (change?.let { " (%+.2f%%)".format(it) } ?: "")
                                else "Unavailable",
                                style = MaterialTheme.typography.bodySmall,
                                color = if ((change ?: 0.0) >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        } else {
            Text(
                "Markets temporarily unavailable — check back shortly.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        Text("YOUR ALERTS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))

        if (alerts.isEmpty()) {
            Text(
                "No price alerts set yet.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        } else {
            alerts.forEach { alert -> MarketAlertRow(alert, onDelete = { viewModel.removeMarketAlert(alert.id) }) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = symbol,
            onValueChange = { symbol = it.uppercase() },
            label = { Text("Symbol (e.g. AAPL, BTC, XAU/USD)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(onClick = { assetClassMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(ASSET_CLASSES.find { it.first == assetClass }?.second ?: assetClass)
                }
                DropdownMenu(expanded = assetClassMenuExpanded, onDismissRequest = { assetClassMenuExpanded = false }) {
                    ASSET_CLASSES.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { assetClass = value; assetClassMenuExpanded = false })
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(onClick = { directionMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(DIRECTIONS.find { it.first == direction }?.second ?: direction)
                }
                DropdownMenu(expanded = directionMenuExpanded, onDismissRequest = { directionMenuExpanded = false }) {
                    DIRECTIONS.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { direction = value; directionMenuExpanded = false })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = targetPrice,
            onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) targetPrice = it },
            label = { Text("Target price (USD)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val price = targetPrice.toDoubleOrNull()
                if (symbol.isNotBlank() && price != null) {
                    viewModel.addMarketAlert(symbol.trim(), assetClass, direction, price)
                    symbol = ""
                    targetPrice = ""
                }
            },
            enabled = !isSaving && symbol.isNotBlank() && targetPrice.toDoubleOrNull() != null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
            else {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SET ALERT")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "First alert is free. Tracking more than one symbol at a time needs Premium.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            fontSize = 10.sp
        )
    }

    val chartSymbol by viewModel.selectedChartSymbol.collectAsState()
    if (chartSymbol != null) {
        val candles by viewModel.chartCandles.collectAsState()
        val isLoadingChart by viewModel.isLoadingChart.collectAsState()
        AlertDialog(
            onDismissRequest = { viewModel.closeChart() },
            title = { Text("$chartSymbol — 6 months") },
            text = {
                if (isLoadingChart) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    CandlestickChart(candles = candles)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.closeChart() }) { Text("CLOSE") } }
        )
    }
}

@Composable
private fun MarketAlertRow(alert: MarketAlert, onDelete: () -> Unit) {
    val statusText = when {
        alert.delivered -> "Delivered"
        alert.triggeredAt != null -> "Triggered"
        else -> "Watching"
    }
    val conditionText = if (alert.direction == "above") "rises above" else "falls below"

    ListItem(
        headlineContent = { Text("${alert.symbol} $conditionText %.2f".format(alert.targetPrice)) },
        supportingContent = { Text(statusText, color = if (statusText == "Watching") Color.Gray else Color(0xFF2E7D32)) },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
