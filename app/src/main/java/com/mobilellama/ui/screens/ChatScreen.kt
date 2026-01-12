package com.mobilellama.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilellama.data.model.InferenceState
import com.mobilellama.ui.components.InputBar
import com.mobilellama.ui.components.MessageBubble
import com.mobilellama.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val currentAssistantMessage by viewModel.currentAssistantMessage.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val inferenceState by viewModel.inferenceState.collectAsState()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, currentAssistantMessage) {
        if (messages.isNotEmpty() || currentAssistantMessage.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size)
            }
        }
    }

    // Show error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Mobile Llama",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = getStatusText(inferenceState, isGenerating),
                            fontSize = 12.sp,
                            color = getStatusColor(inferenceState, isGenerating)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            InputBar(
                isGenerating = isGenerating,
                onSendMessage = { viewModel.sendMessage(it) },
                onStopGeneration = { viewModel.stopGeneration() }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (inferenceState is InferenceState.Initializing) {
            // Show loading while model initializes
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading model...")
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message = message)
                }

                // Show streaming assistant message
                if (currentAssistantMessage.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Text(
                                    text = "ASSISTANT • GENERATING...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Surface(
                                modifier = Modifier.widthIn(max = 300.dp),
                                color = Color(0xFFE0E0E0),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = currentAssistantMessage,
                                    modifier = Modifier.padding(12.dp),
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }

                // Empty state
                if (messages.isEmpty() && currentAssistantMessage.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Start a conversation with your offline AI assistant!",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getStatusText(state: InferenceState, isGenerating: Boolean): String {
    return when {
        state is InferenceState.Uninitialized -> "NOT READY"
        state is InferenceState.Initializing -> "LOADING..."
        state is InferenceState.Error -> "ERROR"
        isGenerating -> "GENERATING..."
        state is InferenceState.Ready -> "READY • OFFLINE"
        else -> "READY"
    }
}

private fun getStatusColor(state: InferenceState, isGenerating: Boolean): Color {
    return when {
        state is InferenceState.Error -> Color(0xFFD32F2F) // Red
        state is InferenceState.Initializing -> Color(0xFFFFA000) // Yellow/Orange
        isGenerating -> Color(0xFF1976D2) // Blue
        state is InferenceState.Ready -> Color(0xFF388E3C) // Green
        else -> Color.Gray
    }
}
