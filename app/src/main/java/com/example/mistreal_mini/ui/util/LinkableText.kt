package com.example.mistreal_mini.ui.util

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

@Composable
fun LinkableText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = LocalContentColor.current,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val uriHandler = LocalUriHandler.current
    // Simplified pattern to avoid illegal character range errors in standard Regex
    val urlPattern = Regex("https?://[a-zA-Z0-9./?=_-]+")
    
    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        urlPattern.findAll(text).forEach { matchResult ->
            // Add normal text before the link
            append(text.substring(lastIndex, matchResult.range.first))
            
            // Push link style and annotation
            val url = matchResult.value
            pushStringAnnotation(tag = "URL", annotation = url)
            withStyle(
                style = SpanStyle(
                    color = Color(0xFF6B4CFF), // Custom purple for links
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(url)
            }
            pop()
            lastIndex = matchResult.range.last + 1
        }
        // Add remaining text
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
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
                    } catch (e: Exception) {
                        // Handle potential malformed URIs
                    }
                }
        }
    )
}
