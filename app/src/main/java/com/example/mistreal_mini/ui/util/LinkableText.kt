package com.example.mistreal_mini.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun LinkableText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = LocalContentColor.current,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val uriHandler = LocalUriHandler.current
    // Pattern for URLs
    val urlPattern = Regex("https?://[a-zA-Z0-9./?=_-]+")
    // Pattern for *actions*
    val actionPattern = Regex("\\*[^*]+\\*")
    
    val annotatedString = buildAnnotatedString {
        var currentIndex = 0
        
        // Combine and sort all matches
        val allMatches = (urlPattern.findAll(text) + actionPattern.findAll(text))
            .sortedBy { it.range.first }
            .toList()

        allMatches.forEach { matchResult ->
            // Add text before the match
            if (matchResult.range.first > currentIndex) {
                append(text.substring(currentIndex, matchResult.range.first))
            }
            
            val matchValue = matchResult.value
            if (matchValue.startsWith("http")) {
                // Style URL
                pushStringAnnotation(tag = "URL", annotation = matchValue)
                withStyle(
                    style = SpanStyle(
                        color = Color(0xFF6B4CFF),
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(matchValue)
                }
                pop()
            } else if (matchValue.startsWith("*") && matchValue.endsWith("*")) {
                // Style Action
                withStyle(
                    style = SpanStyle(
                        color = textColor.copy(alpha = 0.7f),
                        fontStyle = FontStyle.Italic,
                        background = textColor.copy(alpha = 0.05f)
                    )
                ) {
                    append(matchValue)
                }
            }
            
            currentIndex = matchResult.range.last + 1
        }
        
        // Add remaining text
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = style.copy(color = textColor),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        uriHandler.openUri(annotation.item)
                    } catch (e: Exception) {}
                }
        }
    )
}
