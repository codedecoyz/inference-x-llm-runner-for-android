package com.mobilellama.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilellama.data.model.DownloadState
import com.mobilellama.viewmodel.DownloadViewModel

@Composable
fun DownloadScreen(
    onDownloadComplete: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val downloadState by viewModel.downloadState.collectAsState()

    // Navigate when download succeeds
    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Success) {
            onDownloadComplete()
        }
    }

    // Start download automatically if idle
    LaunchedEffect(Unit) {
        if (downloadState is DownloadState.Idle) {
            viewModel.startDownload()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Mobile Llama",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(48.dp))

        when (val state = downloadState) {
            is DownloadState.Idle -> {
                Text("Preparing download...")
            }

            is DownloadState.Checking -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Checking existing model...")
            }

            is DownloadState.Downloading -> {
                LinearProgressIndicator(
                    progress = state.progress,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                val progressPercent = (state.progress * 100).toInt()
                val downloadedMB = state.bytesDownloaded / (1024 * 1024)
                val totalMB = state.totalBytes / (1024 * 1024)

                Text(
                    text = "Downloading TinyLLaMA model...",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$progressPercent% ($downloadedMB MB / $totalMB MB)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is DownloadState.Verifying -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Verifying download...")
            }

            is DownloadState.Success -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Download complete! Loading...")
            }

            is DownloadState.Error -> {
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.message,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error
                )

                if (state.isRetryable) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { viewModel.retryDownload() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}
