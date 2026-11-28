package com.example.mistreal_mini.ui.dashboard.components

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mistreal_mini.data.api.Article

/**
 * Replaces the old plain-text disaster rendering (which, before the type
 * rename in getInterleavedFeed, mistakenly shared SosAlertCard with the
 * unrelated Guardian broadcast feature). Small single-marker Leaflet map —
 * not the full InteractiveMapView, which needs four ViewModels and a GPS
 * loop this card has no business pulling in for one static pin.
 */
@Composable
fun DisasterAlertCard(article: Article) {
    val meta = article.metadata
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, Color(0xFFD32F2F))
    ) {
        Column {
            if (meta?.latitude != null && meta.longitude != null) {
                DisasterMiniMap(
                    latitude = meta.latitude,
                    longitude = meta.longitude,
                    severity = meta.severity ?: "ALERT",
                    label = article.title
                )
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Row {
                    Text("🌋 HAZARD ALERT", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    meta?.magnitude?.let { Text("M${it}", fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F)) }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(article.title, fontWeight = FontWeight.Bold)
                if (article.description != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(article.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DisasterMiniMap(latitude: Double, longitude: Double, severity: String, label: String) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                loadDataWithBaseURL(null, getDisasterMapHtml(latitude, longitude, severity, label), "text/html", "UTF-8", null)
            }
        }
    )
}

private fun getDisasterMapHtml(lat: Double, lon: Double, severity: String, label: String): String {
    val markerColor = when (severity.lowercase()) {
        "severe", "critical", "high" -> "#D32F2F"
        "moderate", "medium" -> "#F57C00"
        else -> "#FBC02D"
    }
    // Escape only what can realistically appear in a title/label — this is
    // backend-sourced text (USGS/GDACS), not user input, but still never
    // worth trusting blindly inside an inline <script> string.
    val safeLabel = label.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                html, body, #map { height: 100%; width: 100%; margin: 0; background: #0F172A; }
                .leaflet-container { background: #0F172A !important; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map', { zoomControl: false, attributionControl: false }).setView([$lat, $lon], 6);
                L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', { maxZoom: 18 }).addTo(map);
                var marker = L.circleMarker([$lat, $lon], {
                    radius: 10, color: '$markerColor', fillColor: '$markerColor', fillOpacity: 0.7, weight: 2
                }).addTo(map);
                marker.bindPopup('$safeLabel').openPopup();
            </script>
        </body>
        </html>
    """.trimIndent()
}
