package com.alice.ai.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String
)

enum class ChatRole {
    User,
    Assistant
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    inputText: String,
    selectedModel: String,
    availableModels: List<String>,
    isGenerating: Boolean,
    activeContextCount: Int,
    statusMessage: String?,
    errorMessage: String?,
    onInputTextChange: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onUploadClick: () -> Unit,
    onSendClick: () -> Unit,
    onClearError: () -> Unit,
    onReadAloud: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    ModelSelector(
                        selectedModel = selectedModel,
                        models = availableModels,
                        onModelSelected = onModelSelected
                    )
                },
                actions = {
                    IconButton(onClick = onUploadClick) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Upload"
                        )
                    }
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = inputText,
                onInputTextChange = onInputTextChange,
                onSendClick = onSendClick,
                isGenerating = isGenerating
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (activeContextCount > 0 || isGenerating || statusMessage != null || errorMessage != null) {
                item(key = "status") {
                    ChatStatusCard(
                        contextSize = activeContextCount,
                        isGenerating = isGenerating,
                        statusMessage = statusMessage,
                        errorMessage = errorMessage,
                        onClearError = onClearError
                    )
                }
            }

            items(items = messages, key = { it.id }) { message ->
                ChatBubble(
                    message = message,
                    onReadAloud = onReadAloud
                )
            }
        }
    }
}

@Composable
private fun ModelSelector(
    selectedModel: String,
    models: List<String>,
    onModelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(onClick = { expanded = true }) {
            Text(
                text = selectedModel,
                maxLines = 1
            )
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = "Select model"
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        expanded = false
                        onModelSelected(model)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isGenerating: Boolean
) {
    Surface(tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Alice...") },
                maxLines = 4
            )
            IconButton(
                onClick = onSendClick,
                enabled = inputText.isNotBlank() && !isGenerating
            ) {
                Icon(
                    imageVector = Icons.Outlined.Send,
                    contentDescription = "Send"
                )
            }
        }
    }
}

@Composable
private fun ChatStatusCard(
    contextSize: Int,
    isGenerating: Boolean,
    statusMessage: String?,
    errorMessage: String?,
    onClearError: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Active context: $contextSize / 7 messages",
                style = MaterialTheme.typography.labelLarge
            )

            if (isGenerating) {
                Text(
                    text = "Alice is generating a reply...",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = onClearError) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    message: ChatMessage,
    onReadAloud: (String) -> Unit
) {
    val isAssistant = message.role == ChatRole.Assistant
    val clipboard = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    val alignment = if (isAssistant) Alignment.CenterStart else Alignment.CenterEnd

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Box {
            val bubbleModifier = if (isAssistant) {
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { menuExpanded = true }
                )
            } else {
                Modifier
            }

            Surface(
                modifier = bubbleModifier.widthIn(max = 360.dp),
                shape = MaterialTheme.shapes.large,
                color = if (isAssistant) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            ) {
                MarkdownMessage(
                    text = message.content,
                    role = message.role
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = {
                        menuExpanded = false
                        clipboard.setText(AnnotatedString(message.content))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Read Aloud") },
                    onClick = {
                        menuExpanded = false
                        onReadAloud(message.content)
                    }
                )
            }
        }
    }
}

@Composable
private fun MarkdownMessage(
    text: String,
    role: ChatRole
) {
    val segments = remember(text) { splitMarkdownSegments(text) }
    val textColor = if (role == ChatRole.Assistant) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Text -> {
                    if (segment.value.isNotBlank()) {
                        Text(
                            text = buildInlineMarkdown(segment.value),
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                is MarkdownSegment.Code -> {
                    CodeBlock(
                        language = segment.language,
                        code = segment.code
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(
    language: String?,
    code: String
) {
    val clipboard = LocalClipboardManager.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0A1016),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language?.ifBlank { "code" } ?: "code",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(code)) }
                ) {
                    Text("Copy")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = code.trimEnd(),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp),
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private sealed class MarkdownSegment {
    data class Text(val value: String) : MarkdownSegment()
    data class Code(val language: String?, val code: String) : MarkdownSegment()
}

private fun splitMarkdownSegments(input: String): List<MarkdownSegment> {
    val codeBlockRegex = Regex("(?s)```([A-Za-z0-9_+-]*)\\n(.*?)```")
    val segments = mutableListOf<MarkdownSegment>()
    var cursor = 0

    codeBlockRegex.findAll(input).forEach { match ->
        if (match.range.first > cursor) {
            segments += MarkdownSegment.Text(input.substring(cursor, match.range.first))
        }
        val language = match.groups[1]?.value?.ifBlank { null }
        val code = match.groups[2]?.value.orEmpty()
        segments += MarkdownSegment.Code(language = language, code = code)
        cursor = match.range.last + 1
    }

    if (cursor < input.length) {
        segments += MarkdownSegment.Text(input.substring(cursor))
    }

    if (segments.isEmpty()) {
        segments += MarkdownSegment.Text(input)
    }

    return segments
}

private fun buildInlineMarkdown(input: String): AnnotatedString {
    val inlineRegex = Regex("(`[^`]+`|\\*\\*[^*]+\\*\\*|\\*[^*]+\\*)")
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
