package com.alice.ai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

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
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    Text(
                        text = buildInlineMarkdown(block.text),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        },
                        color = textColor
                    )
                }

                is MarkdownTextBlock.Bullet -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "\u2022",
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = buildInlineMarkdown(block.text),
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                is MarkdownTextBlock.Numbered -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${block.number}.",
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = buildInlineMarkdown(block.text),
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                is MarkdownTextBlock.Paragraph -> {
                    if (block.text.isNotBlank()) {
                        Text(
                            text = buildInlineMarkdown(block.text),
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        Text(
                            text = "",
                            modifier = Modifier.padding(vertical = 2.dp)
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
        return listOf(MarkdownSegment.TextSegment(listOf(MarkdownTextBlock.Paragraph(""))))
    }

    val lines = normalized.split('\n')
    val segments = mutableListOf<MarkdownSegment>()
    val textBuffer = mutableListOf<String>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        if (line.trimStart().startsWith("```")) {
            flushTextBuffer(textBuffer, segments)
            val language = line.trim().removePrefix("```").trim().ifBlank { null }
            val codeLines = mutableListOf<String>()
            index += 1
            while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                codeLines += lines[index]
                index += 1
            }
            segments += MarkdownSegment.CodeBlockSegment(
                code = codeLines.joinToString("\n"),
                language = language
            )
            if (index < lines.size && lines[index].trimStart().startsWith("```")) {
                index += 1
            }
            continue
        }

        textBuffer += line
        index += 1
    }

    flushTextBuffer(textBuffer, segments)
    return if (segments.isEmpty()) {
        listOf(MarkdownSegment.TextSegment(listOf(MarkdownTextBlock.Paragraph(normalized))))
    } else {
        segments
    }
}

private fun flushTextBuffer(
    textBuffer: MutableList<String>,
    segments: MutableList<MarkdownSegment>
) {
    if (textBuffer.isEmpty()) {
        return
    }

    val blocks = textBuffer.map { line ->
        parseTextBlock(line)
    }
    segments += MarkdownSegment.TextSegment(blocks)
    textBuffer.clear()
}

private fun parseTextBlock(line: String): MarkdownTextBlock {
    val trimmed = line.trim()
    if (trimmed.isBlank()) {
        return MarkdownTextBlock.Paragraph("")
    }

    if (trimmed.startsWith("### ")) {
        return MarkdownTextBlock.Heading(level = 3, text = trimmed.removePrefix("### ").trim())
    }
    if (trimmed.startsWith("## ")) {
        return MarkdownTextBlock.Heading(level = 2, text = trimmed.removePrefix("## ").trim())
    }
    if (trimmed.startsWith("# ")) {
        return MarkdownTextBlock.Heading(level = 1, text = trimmed.removePrefix("# ").trim())
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
        return MarkdownTextBlock.Numbered(number = number, text = numbered.groupValues[2].trim())
    }

    return MarkdownTextBlock.Paragraph(trimmed)
}

private fun buildInlineMarkdown(input: String): AnnotatedString {
    val inlineRegex = Regex("(`[^`]+`|\\*\\*[^*]+\\*\\*|__[^_]+__|\\*[^*]+\\*)")
    var cursor = 0

    return buildAnnotatedString {
        inlineRegex.findAll(input).forEach { match ->
            if (match.range.first > cursor) {
                append(input.substring(cursor, match.range.first))
            }

            val token = match.value
            when {
                token.startsWith("`") && token.endsWith("`") -> {
                    withStyle(
                        style = SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x332A3A48)
                        )
                    ) {
                        append(token.removeSurrounding("`"))
                    }
                }

                token.startsWith("**") && token.endsWith("**") -> {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(token.removePrefix("**").removeSuffix("**"))
                    }
                }

                token.startsWith("__") && token.endsWith("__") -> {
                    withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(token.removePrefix("__").removeSuffix("__"))
                    }
                }

                token.startsWith("*") && token.endsWith("*") -> {
                    withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(token.removePrefix("*").removeSuffix("*"))
                    }
                }

                else -> append(token)
            }

            cursor = match.range.last + 1
        }

        if (cursor < input.length) {
            append(input.substring(cursor))
        }
    }
}
