package com.alice.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface MarkdownSegment {
    data class TextSegment(val blocks: List<MarkdownTextBlock>) : MarkdownSegment
    data class CodeBlockSegment(val code: String, val language: String?) : MarkdownSegment
}

sealed interface MarkdownTextBlock {
    data class Paragraph(val text: String) : MarkdownTextBlock
    data class Heading(val level: Int, val text: String) : MarkdownTextBlock
    data class Bullet(val text: String) : MarkdownTextBlock
    data class Numbered(val number: Int, val text: String) : MarkdownTextBlock
}

private sealed interface InlineSegment {
    data class Plain(val value: String) : InlineSegment
    data class Bold(val value: String) : InlineSegment
    data class Italic(val value: String) : InlineSegment
    data class Code(val value: String) : InlineSegment
}

@Composable
fun MarkdownMessage(
    text: String,
    textColor: Color,
    onCodeCopied: () -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = remember(text) { parseMarkdownSegments(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.CodeBlockSegment -> {
                    CodeBlockView(
                        code = segment.code,
                        language = segment.language,
                        onCopied = onCodeCopied
                    )
                }

                is MarkdownSegment.TextSegment -> {
                    MarkdownTextSegment(
                        blocks = segment.blocks,
                        textColor = textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTextSegment(
    blocks: List<MarkdownTextBlock>,
    textColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownTextBlock.Heading -> {
                    InlineMarkdownText(
                        text = block.text,
                        color = textColor,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                is MarkdownTextBlock.Bullet -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(textColor)
                        )
                        InlineMarkdownText(
                            text = block.text,
                            color = textColor,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownTextBlock.Numbered -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${block.number}.",
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        InlineMarkdownText(
                            text = block.text,
                            color = textColor,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownTextBlock.Paragraph -> {
                    if (block.text.isNotBlank()) {
                        InlineMarkdownText(
                            text = block.text,
                            color = textColor,
                            textStyle = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        Box(modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineMarkdownText(
    text: String,
    color: Color,
    textStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    val segments = remember(text) { parseInlineSegments(text) }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is InlineSegment.Plain -> {
                    Text(
                        text = segment.value,
                        color = color,
                        style = textStyle
                    )
                }

                is InlineSegment.Bold -> {
                    Text(
                        text = segment.value,
                        color = color,
                        style = textStyle.copy(fontWeight = FontWeight.Bold)
                    )
                }

                is InlineSegment.Italic -> {
                    Text(
                        text = segment.value,
                        color = color,
                        style = textStyle.copy(fontStyle = FontStyle.Italic)
                    )
                }

                is InlineSegment.Code -> {
                    Surface(
                        color = Color(0xFF1B2536),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = segment.value,
                            color = color,
                            style = textStyle.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

fun parseMarkdownSegments(input: String): List<MarkdownSegment> {
    val normalized = input.replace("\r\n", "\n")
    if (normalized.isBlank()) {
        return listOf(
            MarkdownSegment.TextSegment(
                blocks = listOf(MarkdownTextBlock.Paragraph(""))
            )
        )
    }

    val segments = mutableListOf<MarkdownSegment>()
    val splitParts = normalized.split("```")
    splitParts.forEachIndexed { index, part ->
        if (index % 2 == 0) {
            val blocks = parseTextBlocks(part)
            val hasMeaningfulText = blocks.any { block ->
                when (block) {
                    is MarkdownTextBlock.Paragraph -> block.text.isNotBlank()
                    is MarkdownTextBlock.Heading,
                    is MarkdownTextBlock.Bullet,
                    is MarkdownTextBlock.Numbered -> true
                }
            }
            if (hasMeaningfulText) {
                segments += MarkdownSegment.TextSegment(blocks)
            }
        } else {
            val (language, code) = extractCodeBlock(part)
            if (code.isNotBlank()) {
                segments += MarkdownSegment.CodeBlockSegment(
                    code = code,
                    language = language
                )
            }
        }
    }

    return if (segments.isEmpty()) {
        listOf(MarkdownSegment.TextSegment(parseTextBlocks(normalized)))
    } else {
        segments
    }
}

private fun parseTextBlocks(input: String): List<MarkdownTextBlock> {
    if (input.isEmpty()) {
        return emptyList()
    }
    return input.split('\n')
        .map { parseTextBlock(it) }
}

private fun extractCodeBlock(rawPart: String): Pair<String?, String> {
    if (rawPart.isEmpty()) {
        return null to ""
    }

    val normalized = rawPart.trimEnd('\n')
    if (normalized.startsWith("\n")) {
        return null to normalized.removePrefix("\n")
    }

    val firstLine = normalized.substringBefore('\n')
    val remaining = normalized.substringAfter('\n', "")
    return if (remaining.isNotEmpty()) {
        firstLine.trim().ifBlank { null } to remaining
    } else {
        null to normalized
    }
}

private fun parseTextBlock(line: String): MarkdownTextBlock {
    val trimmed = line.trim()
    if (trimmed.isBlank()) {
        return MarkdownTextBlock.Paragraph("")
    }

    if (trimmed.startsWith("### ")) {
        return MarkdownTextBlock.Heading(
            level = 3,
            text = trimmed.removePrefix("### ").trim()
        )
    }
    if (trimmed.startsWith("## ")) {
        return MarkdownTextBlock.Heading(
            level = 2,
            text = trimmed.removePrefix("## ").trim()
        )
    }
    if (trimmed.startsWith("# ")) {
        return MarkdownTextBlock.Heading(
            level = 1,
            text = trimmed.removePrefix("# ").trim()
        )
    }
    if (trimmed.startsWith("- ")) {
        return MarkdownTextBlock.Bullet(text = trimmed.removePrefix("- ").trim())
    }
    if (trimmed.startsWith("* ")) {
        return MarkdownTextBlock.Bullet(text = trimmed.removePrefix("* ").trim())
    }

    val numbered = Regex("""^(\d+)\.\s+(.+)$""").find(trimmed)
    if (numbered != null) {
        val number = numbered.groupValues[1].toIntOrNull() ?: 1
        return MarkdownTextBlock.Numbered(
            number = number,
            text = numbered.groupValues[2].trim()
        )
    }

    return MarkdownTextBlock.Paragraph(trimmed)
}

private fun parseInlineSegments(input: String): List<InlineSegment> {
    if (input.isEmpty()) {
        return emptyList()
    }

    val tokenRegex = Regex("(`[^`]+`|\\*\\*[^*]+\\*\\*|__[^_]+__|\\*[^*]+\\*)")
    val segments = mutableListOf<InlineSegment>()
    var cursor = 0

    tokenRegex.findAll(input).forEach { match ->
        if (match.range.first > cursor) {
            segments += InlineSegment.Plain(input.substring(cursor, match.range.first))
        }

        val token = match.value
        when {
            token.startsWith("`") && token.endsWith("`") -> {
                segments += InlineSegment.Code(token.removeSurrounding("`"))
            }

            token.startsWith("**") && token.endsWith("**") -> {
                segments += InlineSegment.Bold(
                    token.removePrefix("**").removeSuffix("**")
                )
            }

            token.startsWith("__") && token.endsWith("__") -> {
                segments += InlineSegment.Italic(
                    token.removePrefix("__").removeSuffix("__")
                )
            }

            token.startsWith("*") && token.endsWith("*") -> {
                segments += InlineSegment.Italic(
                    token.removePrefix("*").removeSuffix("*")
                )
            }

            else -> segments += InlineSegment.Plain(token)
        }
        cursor = match.range.last + 1
    }

    if (cursor < input.length) {
        segments += InlineSegment.Plain(input.substring(cursor))
    }
    return segments
}
