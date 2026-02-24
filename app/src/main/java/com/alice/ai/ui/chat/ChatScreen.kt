package com.alice.ai.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
    isWaitingForResponse: Boolean,
    activeContextCount: Int,
    statusMessage: String?,
    errorMessage: String?,
    onInputTextChange: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onModelDropdownOpened: () -> Unit,
    onUploadClick: () -> Unit,
    onSendClick: () -> Unit,
    onClearError: () -> Unit,
    onReadAloud: (String) -> Unit,
    onDeleteUserMessage: (String) -> Unit,
    onShareMessage: (String) -> Unit,
    onHistoryClick: () -> Unit,
    onNewChatClick: () -> Unit,
    audioTopBar: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var autoScrollEnabled by rememberSaveable { mutableStateOf(true) }

    val showStatusCard = activeContextCount > 0 || isGenerating || statusMessage != null || errorMessage != null

    val isNearBottom by remember(listState, messages.size) {
        derivedStateOf {
            if (messages.isEmpty()) {
                true
            } else {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisibleIndex >= messages.lastIndex - 1
            }
        }
    }

    val isUserScrollingUp by remember(listState, messages.size, isNearBottom) {
        derivedStateOf {
            messages.isNotEmpty() && !isNearBottom && listState.isScrollInProgress
        }
    }

    val showScrollToBottomFab by remember(autoScrollEnabled, isNearBottom, messages.size) {
        derivedStateOf {
            messages.isNotEmpty() && (!autoScrollEnabled || !isNearBottom)
        }
    }

    LaunchedEffect(isUserScrollingUp) {
        if (isUserScrollingUp) {
            autoScrollEnabled = false
        }
    }

    LaunchedEffect(isNearBottom, isUserScrollingUp) {
        if (!isUserScrollingUp && isNearBottom) {
            autoScrollEnabled = true
        }
    }

    val scrollSignal = remember(messages, isGenerating, isWaitingForResponse) {
        val last = messages.lastOrNull()
        "${messages.size}:${last?.id.orEmpty()}:${last?.content?.length ?: 0}:$isGenerating:$isWaitingForResponse"
    }

    LaunchedEffect(scrollSignal, autoScrollEnabled) {
        if (!autoScrollEnabled || messages.isEmpty()) {
            return@LaunchedEffect
        }
        withFrameMillis { }
        scope.launch {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        ModelSelector(
                            selectedModel = selectedModel,
                            models = availableModels,
                            onModelSelected = onModelSelected,
                            onExpanded = onModelDropdownOpened
                        )
                    },
                    actions = {
                        IconButton(onClick = onHistoryClick) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = "Chat history"
                            )
                        }
                        IconButton(onClick = onNewChatClick) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = "New chat"
                            )
                        }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showStatusCard) {
                    ChatStatusCard(
                        contextSize = activeContextCount,
                        isGenerating = isGenerating,
                        statusMessage = statusMessage,
                        errorMessage = errorMessage,
                        onClearError = onClearError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = messages,
                            key = { it.id }
                        ) { message ->
                            ChatBubble(
                                message = message,
                                onReadAloud = onReadAloud,
                                onDeleteUserMessage = onDeleteUserMessage,
                                onShareMessage = onShareMessage
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isWaitingForResponse,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 28.dp, bottom = 12.dp),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        SiriOrb(size = 32.dp)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showScrollToBottomFab,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 84.dp),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            FloatingActionButton(
                onClick = {
                    if (messages.isEmpty()) {
                        autoScrollEnabled = true
                        return@FloatingActionButton
                    }
                    scope.launch {
                        listState.animateScrollToItem(messages.lastIndex)
                        autoScrollEnabled = true
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowDownward,
                    contentDescription = "Scroll to bottom"
                )
            }
        }

        if (audioTopBar != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                audioTopBar()
            }
        }
    }
}

@Composable
private fun ModelSelector(
    selectedModel: String,
    models: List<String>,
    onModelSelected: (String) -> Unit,
    onExpanded: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(
            onClick = {
                onExpanded()
                expanded = true
            }
        ) {
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
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
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
    onReadAloud: (String) -> Unit,
    onDeleteUserMessage: (String) -> Unit,
    onShareMessage: (String) -> Unit
) {
    val isUser = message.role == ChatRole.User
    val clipboard = LocalClipboardManager.current
    var assistantMenuExpanded by remember { mutableStateOf(false) }
    var userMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isUser) 64.dp else 16.dp,
                end = if (isUser) 16.dp else 64.dp
            ),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val maxBubbleWidth = maxWidth * if (isUser) 0.80f else 0.85f

            val bubbleModifier = if (isUser) {
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { userMenuExpanded = true }
                )
            } else {
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { assistantMenuExpanded = true }
                )
            }

            Box {
                Surface(
                    modifier = bubbleModifier.widthIn(max = maxBubbleWidth),
                    shape = MaterialTheme.shapes.large,
                    color = if (isUser) {
                        Color(0xFF2A1E52)
                    } else {
                        Color(0xFF1A2338)
                    }
                ) {
                    val textColor = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    MarkdownMessage(
                        text = message.content,
                        textColor = textColor,
                        onCodeCopied = {},
                        modifier = Modifier.padding(12.dp)
                    )
                }

                if (!isUser) {
                    DropdownMenu(
                        expanded = assistantMenuExpanded,
                        onDismissRequest = { assistantMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            onClick = {
                                assistantMenuExpanded = false
                                clipboard.setText(AnnotatedString(message.content))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Read Aloud") },
                            onClick = {
                                assistantMenuExpanded = false
                                onReadAloud(message.content)
                            }
                        )
                    }
                }

                if (isUser) {
                    DropdownMenu(
                        expanded = userMenuExpanded,
                        onDismissRequest = { userMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            onClick = {
                                userMenuExpanded = false
                                clipboard.setText(AnnotatedString(message.content))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                userMenuExpanded = false
                                onDeleteUserMessage(message.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                userMenuExpanded = false
                                onShareMessage(message.content)
                            }
                        )
                    }
                }
            }
        }
    }
}
