package com.mobilellama.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilellama.data.model.AiModel
import com.mobilellama.data.model.DownloadState
import com.mobilellama.ui.components.InferenceCard
import com.mobilellama.ui.components.InferenceXActionButton
import com.mobilellama.ui.components.NeonProgressIndicator
import com.mobilellama.ui.theme.*
import com.mobilellama.viewmodel.DownloadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    onBack: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val modelStates by viewModel.modelStates.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val availableModels = viewModel.availableModels

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        CenterAlignedTopAppBar(
            title = { 
                Text(
                    "Manage Models",
                    color = com.mobilellama.ui.theme.HighlightWhitePurple,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                ) 
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack, 
                        contentDescription = "Back",
                        tint = com.mobilellama.ui.theme.HighlightWhitePurple
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent
            )
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(availableModels) { model ->
                val state = modelStates[model.filename] ?: DownloadState.Idle
                val isSelected = model.name == selectedModel.name
                
                ModelCard(
                    model = model,
                    state = state,
                    isSelected = isSelected,
                    onDownload = { viewModel.startDownload(model) },
                    onSelect = { viewModel.selectModel(model) }
                )
            }
        }
    }
}

@Composable
fun ModelCard(
    model: AiModel,
    state: DownloadState,
    isSelected: Boolean,
    onDownload: () -> Unit,
    onSelect: () -> Unit
) {
    InferenceCard {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Model Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Bold,
                    color = com.mobilellama.ui.theme.HighlightWhitePurple
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Size: ${model.expectedSize / 1024 / 1024} MB",
                    style = MaterialTheme.typography.labelMedium,
                    color = com.mobilellama.ui.theme.HighlightWhitePurple.copy(alpha = 0.6f)
                )
            }
            
            // Status / Action
            Box {
                when (state) {
                    is DownloadState.Idle, is DownloadState.Error -> {
                        InferenceXActionButton(
                            text = if (state is DownloadState.Error) "Retry" else "Download",
                            onClick = onDownload,
                            modifier = Modifier.width(100.dp).height(40.dp)
                        )
                    }
                    
                    is DownloadState.Checking, is DownloadState.Verifying -> {
                         Text(
                            "Processing...", 
                            color = com.mobilellama.ui.theme.HighlightWhitePurple.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    is DownloadState.Downloading -> {
                        // Handled below row for progress bar
                    }
                    
                    is DownloadState.Success -> {
                        if (isSelected) {
                            // ACTIVE Badge (Filled)
                            Box(
                                modifier = Modifier
                                    .background(com.mobilellama.ui.theme.VibrantPurple, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            // READY Badge (Outlined)
                            // Clickable to select
                            Box(
                                modifier = Modifier
                                    .border(1.dp, com.mobilellama.ui.theme.LightLavender, RoundedCornerShape(8.dp))
                                    .clickable { onSelect() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "READY",
                                    color = com.mobilellama.ui.theme.LightLavender,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Progress Bar for downloading state
        if (state is DownloadState.Downloading) {
            Spacer(modifier = Modifier.height(16.dp))
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Downloading...", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = com.mobilellama.ui.theme.HighlightWhitePurple.copy(alpha = 0.7f)
                    )
                    Text(
                        "${(state.progress * 100).toInt()}%", 
                        style = MaterialTheme.typography.labelSmall,
                        color = com.mobilellama.ui.theme.HighlightWhitePurple
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                NeonProgressIndicator(
                    progress = state.progress,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        if (state is DownloadState.Error) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
