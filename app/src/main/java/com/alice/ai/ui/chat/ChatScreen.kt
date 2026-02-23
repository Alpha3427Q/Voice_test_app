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
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var autoScrollEnabled by rememberSaveable { mutableStateOf(true) }
    var selectedUserMessage by remember { mutableStateOf<ChatMessage?>(null) }

    val visibleMessages = remember(messages, isWaitingForResponse) {
        messages.filterNot { message ->
            isWaitingForResponse &&
                message.role == ChatRole.Assistant &&
                message.content.isBlank()
        }
    }
    val showStatusCard = activeContextCount > 0 || isGenerating || statusMessage != null || errorMessage != null
    val showSiriOrb = isWaitingForResponse
    val totalItems = visibleMessages.size +
        (if (showStatusCard) 1 else 0) +
        (if (showSiriOrb) 1 else 0)
    val streamUpdateKey = remember(visibleMessages) {
        visibleMessages.lastOrNull()?.let { "${it.id}:${it.content.length}" }.orEmpty()
    }

    val isAtBottom by remember(listState, totalItems) {
        derivedStateOf {
            if (totalItems <= 0) {
                true
            } else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisible >= totalItems - 1
            }
        }
    }

    val showScrollToBottomButton = !autoScrollEnabled && !isAtBottom && totalItems > 0

    LaunchedEffect(streamUpdateKey, isGenerating, isWaitingForResponse, autoScrollEnabled, totalItems) {
        if (autoScrollEnabled && totalItems > 0 && (isGenerating || isWaitingForResponse || visibleMessages.isNotEmpty())) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    LaunchedEffect(listState.isScrollInProgress, isAtBottom) {
        if (listState.isScrollInProgress && !isAtBottom) {
            autoScrollEnabled = false
        }
        if (isAtBottom) {
            autoScrollEnabled = true
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
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showStatusCard) {
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

                items(items = visibleMessages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        onReadAloud = onReadAloud,
                        onUserLongPress = { selectedUserMessage = it }
                    )
                }

                if (showSiriOrb) {
                    item(key = "siri_orb") {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 8.dp),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                SiriOrb(size = 32.dp)
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showScrollToBottomButton,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 84.dp),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            FloatingActionButton(
                onClick = {
                    autoScrollEnabled = true
                    scope.launch {
                        if (totalItems > 0) {
                            listState.animateScrollToItem(totalItems - 1)
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowDownward,
                    contentDescription = "Scroll to bottom"
                )
            }
        }
    }

    selectedUserMessage?.let { targetMessage ->
        ModalBottomSheet(
            onDismissRequest = { selectedUserMessage = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(targetMessage.content))
                        selectedUserMessage = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copy")
                }
                TextButton(
                    onClick = {
                        onDeleteUserMessage(targetMessage.id)
                        selectedUserMessage = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete")
                }
                TextButton(
                    onClick = {
                        onShareMessage(targetMessage.content)
                        selectedUserMessage = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share")
                }
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
    onReadAloud: (String) -> Unit,
    onUserLongPress: (ChatMessage) -> Unit
) {
    val isAssistant = message.role == ChatRole.Assistant
    val clipboard = LocalClipboardManager.current
    var assistantMenuExpanded by remember { mutableStateOf(false) }
    val alignment = if (isAssistant) Alignment.CenterStart else Alignment.CenterEnd

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Box {
            val bubbleModifier = if (isAssistant) {
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { assistantMenuExpanded = true }
                )
            } else {
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { onUserLongPress(message) }
                )
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
                val textColor = if (isAssistant) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
                MarkdownMessage(
                    text = message.content,
                    textColor = textColor,
                    onCodeCopied = {}
                )
            }

            if (isAssistant) {
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
        }
    }
}
