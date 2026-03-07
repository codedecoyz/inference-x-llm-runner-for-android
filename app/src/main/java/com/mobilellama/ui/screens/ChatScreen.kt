package com.mobilellama.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilellama.R
import com.mobilellama.data.model.InferenceState
import com.mobilellama.ui.components.InputBar
import com.mobilellama.ui.components.MessageBubble
import com.mobilellama.ui.theme.*
import com.mobilellama.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenDrawer: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val currentAssistantMessage by viewModel.currentAssistantMessage.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val inferenceState by viewModel.inferenceState.collectAsState()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll logic
    LaunchedEffect(messages.size, currentAssistantMessage) {
        if (messages.isNotEmpty() || currentAssistantMessage.isNotEmpty()) {
            coroutineScope.launch {
                // Scroll to the very bottom, accounting for the streaming message
                val totalItems = messages.size + if (currentAssistantMessage.isNotEmpty()) 1 else 0
                if (totalItems > 0) {
                    listState.animateScrollToItem(totalItems - 1)
                }
            }
        }
    }

    // Snackbar logic
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    // Root Container with Gradient
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(DeepBlackPurple, DarkTonalPurple)
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = HighlightWhitePurple
                            )
                        }
                    },
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "InferenceX",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = HighlightWhitePurple
                            )
                            if (isGenerating || inferenceState !is InferenceState.Ready) {
                                Text(
                                    text = getStatusText(inferenceState, isGenerating),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = getStatusColor(inferenceState, isGenerating)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
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
            // Content Area using paddingValues
            if (inferenceState is InferenceState.Initializing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = LightLavender)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Initializing Neural Engine...",
                            style = MaterialTheme.typography.labelLarge,
                            color = HighlightWhitePurple.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
                ) {
                    // Empty State / Welcome
                    if (messages.isEmpty() && currentAssistantMessage.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.infx_logo),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .alpha(0.3f),
                                        tint = HighlightWhitePurple
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "System Online",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = HighlightWhitePurple.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }

                    // Messages
                    items(messages) { message ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically { it / 2 }
                        ) {
                            MessageBubble(message = message)
                        }
                    }

                    // Streaming Response
                    if (currentAssistantMessage.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "INF-X IS COMPUTING...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LightLavender,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                                )

                                Surface(
                                    color = SurfaceDark,
                                    shape = MaterialTheme.shapes.medium,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, VibrantPurple)
                                ) {
                                    Text(
                                        text = currentAssistantMessage,
                                        modifier = Modifier.padding(16.dp),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
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
        state is InferenceState.Error -> Color(0xFFFF5252)
        state is InferenceState.Initializing -> Color(0xFFFFAB40)
        isGenerating -> LightLavender
        state is InferenceState.Ready -> Color(0xFF69F0AE)
        else -> Color.Gray
    }
}
