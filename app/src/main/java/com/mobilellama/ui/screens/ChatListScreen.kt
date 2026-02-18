package com.mobilellama.ui.screens

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilellama.R
import com.mobilellama.data.model.Chat
import com.mobilellama.ui.theme.*
import com.mobilellama.viewmodel.ChatListViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(
    onOpenDrawer: () -> Unit,
    onChatSelected: (String) -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val chats by viewModel.chats.collectAsState(initial = emptyList())
    val currentModelId by viewModel.currentModelId.collectAsState()
    val scope = rememberCoroutineScope()

    // Bottom sheet state for long-press actions
    var selectedChat by remember { mutableStateOf<Chat?>(null) }
    val sheetState = rememberModalBottomSheetState()

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
                            Text(
                                text = currentModelId.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = LightLavender
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        val chatId = viewModel.createNewChat()
                        onChatSelected(chatId)
                    },
                    containerColor = VibrantPurple,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Chat")
                }
            }
        ) { paddingValues ->
            if (chats.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
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
                            text = "No Conversations",
                            style = MaterialTheme.typography.titleLarge,
                            color = HighlightWhitePurple.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap + to start a new chat",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LightLavender.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = chats,
                        key = { it.id }
                    ) { chat ->
                        ChatListItem(
                            chat = chat,
                            onClick = { onChatSelected(chat.id) },
                            onLongClick = { selectedChat = chat }
                        )
                    }
                }
            }
        }

        // Bottom sheet for chat actions
        if (selectedChat != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedChat = null },
                sheetState = sheetState,
                containerColor = DarkTonalPurple,
                contentColor = HighlightWhitePurple
            ) {
                val chat = selectedChat!!
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = chat.title.ifBlank { "New Chat" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = HighlightWhitePurple,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Pin/Unpin
                    TextButton(
                        onClick = {
                            viewModel.togglePin(chat)
                            selectedChat = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = null,
                            tint = LightLavender,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (chat.isPinned) "Unpin" else "Pin",
                            color = HighlightWhitePurple,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Delete
                    TextButton(
                        onClick = {
                            viewModel.deleteChat(chat)
                            selectedChat = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Delete",
                            color = Color(0xFFFF5252),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListItem(
    chat: Chat,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        chat.lastMessageAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()

    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(16.dp),
        border = if (chat.isPinned) {
            androidx.compose.foundation.BorderStroke(1.dp, VibrantPurple)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, SubtleDivider)
        },
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chat icon
            Surface(
                color = if (chat.isPinned) VibrantPurple.copy(alpha = 0.3f) else SubtleDivider.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.infx_logo),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (chat.isPinned) LightLavender else HighlightWhitePurple.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.title.ifBlank { "New Chat" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = HighlightWhitePurple,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = relativeTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = LightLavender.copy(alpha = 0.6f)
                )
            }

            if (chat.isPinned) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = "Pinned",
                    tint = LightLavender,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
