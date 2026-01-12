package com.mobilellama.data.model

sealed class DownloadState {
    data object Idle : DownloadState()
    data object Checking : DownloadState()
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadState()
    data object Verifying : DownloadState()
    data object Success : DownloadState()
    data class Error(val message: String, val isRetryable: Boolean) : DownloadState()
}
