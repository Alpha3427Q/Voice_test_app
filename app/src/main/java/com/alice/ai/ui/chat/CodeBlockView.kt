package com.alice.ai.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private val KNOWN_LANGUAGE_PREFIXES = listOf(
    "javascript",
    "python",
    "bash",
    "java",
    "json",
    "cpp",
    "html",
    "css",
    "js",
    "c"
)

private fun extractLanguageAndBody(
    explicitLanguage: String?,
    rawCode: String
): Pair<String, String> {
    val normalized = rawCode.replace("\r\n", "\n")
    val normalizedBody = normalized.trimStart('\n', '\r').trimEnd()

    if (!explicitLanguage.isNullOrBlank()) {
        return explicitLanguage.trim().lowercase() to normalizedBody
    }

    val probe = normalized.trimStart()
    if (probe.isEmpty()) {
        return "code" to ""
    }

    val matchedPrefix = KNOWN_LANGUAGE_PREFIXES.firstOrNull { prefix ->
        probe.startsWith(prefix, ignoreCase = true)
    }
    if (matchedPrefix != null) {
        val body = probe.substring(matchedPrefix.length).trimStart()
        return matchedPrefix to body
    }

    return "code" to normalizedBody
}

@Composable
fun CodeBlockView(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
    onCopied: () -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val (languageLabel, codeBody) = remember(code, language) {
        extractLanguageAndBody(
            explicitLanguage = language,
            rawCode = code
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Color(0xFF202020))
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF202020))
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = languageLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(
                    modifier = Modifier.size(28.dp),
                    onClick = {
                        clipboardManager.setText(AnnotatedString(codeBody))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        onCopied()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code"
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D0D))
                    .padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = codeBody,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
