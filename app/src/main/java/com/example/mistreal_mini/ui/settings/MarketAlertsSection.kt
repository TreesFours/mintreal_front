package com.example.mistreal_mini.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
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

    val canSetAlert = !isSaving && symbol.isNotBlank() && targetPrice.toDoubleOrNull() != null
    // Shared by the SET ALERT button and the price field's keyboard Done action.
    val onSetAlert: () -> Unit = {
        val price = targetPrice.toDoubleOrNull()
        if (symbol.isNotBlank() && price != null) {
            viewModel.addMarketAlert(symbol.trim(), assetClass, direction, price)
            symbol = ""
            targetPrice = ""
        }
    }

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
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSetAlert) onSetAlert() })
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onSetAlert,
            enabled = canSetAlert,
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
        val chartAssetClass by viewModel.selectedChartAssetClass.collectAsState()
        val candles by viewModel.chartCandles.collectAsState()
        val isLoadingChart by viewModel.isLoadingChart.collectAsState()
        // getCandles() already fetches/caches up to ~180 daily candles
        // server-side (marketDataService.ts) — the 1M/3M/6M timeframe toggle
        // just slices what's already in hand, no extra network call per
        // window. 1h/4h are different: there's no hourly granularity inside
        // the daily series to slice, so selecting one triggers a real
        // intraday re-fetch (see selectedIntradayInterval below) instead.
        var timeframeDays by remember { mutableIntStateOf(180) }
        var showMovingAverage by remember { mutableStateOf(false) }
        // null = showing the daily series (1M/3M/6M slice it); non-null =
        // showing a fresh intraday fetch for that interval, shown as-is
        // with no further client-side slicing.
        var selectedIntradayInterval by remember { mutableStateOf<String?>(null) }
        val windowedCandles = if (selectedIntradayInterval != null) candles else remember(candles, timeframeDays) { candles.takeLast(timeframeDays) }
        // CoinGecko's free tier has no hourly OHLC for crypto — confirmed,
        // not assumed (see marketDataService.ts::getCandles) — so the chips
        // are only offered for stock/forex/commodity, never crypto.
        val supportsIntraday = chartAssetClass != null && chartAssetClass != "crypto"

        AlertDialog(
            onDismissRequest = { viewModel.closeChart() },
            title = { Text("$chartSymbol") },
            text = {
                Column {
                    if (isLoadingChart) {
                        Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        CandlestickChart(candles = windowedCandles, showMovingAverage = showMovingAverage)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("1M" to 30, "3M" to 90, "6M" to 180).forEach { (label, days) ->
                            FilterChip(
                                selected = selectedIntradayInterval == null && timeframeDays == days,
                                onClick = {
                                    timeframeDays = days
                                    if (selectedIntradayInterval != null) {
                                        selectedIntradayInterval = null
                                        viewModel.selectChartInterval(null)
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                        if (supportsIntraday) {
                            listOf("1h", "4h").forEach { interval ->
                                FilterChip(
                                    selected = selectedIntradayInterval == interval,
                                    onClick = {
                                        selectedIntradayInterval = interval
                                        viewModel.selectChartInterval(interval)
                                    },
                                    label = { Text(interval) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    FilterChip(
                        selected = showMovingAverage,
                        onClick = { showMovingAverage = !showMovingAverage },
                        label = { Text("20-day avg") }
                    )
                    Text(
                        "Tap and drag on the chart to draw a trend line.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
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
