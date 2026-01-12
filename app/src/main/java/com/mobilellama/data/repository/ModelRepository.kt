package com.mobilellama.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.StatFs
import android.util.Log
import com.mobilellama.BuildConfig
import com.mobilellama.data.model.DownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPreferences
) {
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private const val TAG = "ModelRepository"
        private const val MODEL_FILENAME = "tinyllama-1.1b-chat-q4_k_m.gguf"
        private const val TEMP_MODEL_FILENAME = "tinyllama.gguf.tmp"
        private const val PREF_MODEL_DOWNLOADED = "model_downloaded"
        private const val PREF_MODEL_PATH = "model_path"
        private const val MIN_REQUIRED_SPACE_BYTES = 1_000_000_000L // 1 GB
    }

    fun isModelDownloaded(): Boolean {
        val downloaded = prefs.getBoolean(PREF_MODEL_DOWNLOADED, false)
        val modelPath = getModelPath()
        val modelFile = File(modelPath)
        return downloaded && modelFile.exists() && modelFile.length() > 0
    }

    fun getModelPath(): String {
        val savedPath = prefs.getString(PREF_MODEL_PATH, null)
        return savedPath ?: File(context.filesDir, "models/$MODEL_FILENAME").absolutePath
    }

    suspend fun checkModel(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            _downloadState.value = DownloadState.Checking

            val modelPath = getModelPath()
            val modelFile = File(modelPath)

            if (!modelFile.exists()) {
                Log.i(TAG, "Model file does not exist")
                _downloadState.value = DownloadState.Idle
                return@withContext Result.success(false)
            }

            val expectedSize = BuildConfig.MODEL_SIZE_BYTES
            val actualSize = modelFile.length()

            if (actualSize != expectedSize) {
                Log.w(TAG, "Model file size mismatch. Expected: $expectedSize, Actual: $actualSize")
                // Delete invalid file
                modelFile.delete()
                prefs.edit().putBoolean(PREF_MODEL_DOWNLOADED, false).apply()
                _downloadState.value = DownloadState.Idle
                return@withContext Result.success(false)
            }

            Log.i(TAG, "Model file verified successfully")
            _downloadState.value = DownloadState.Success
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking model", e)
            _downloadState.value = DownloadState.Error(e.message ?: "Unknown error", true)
            Result.failure(e)
        }
    }

    suspend fun downloadModel(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Check available storage
            val availableBytes = getAvailableStorageBytes()
            if (availableBytes < MIN_REQUIRED_SPACE_BYTES) {
                val errorMsg = "Not enough space. Need ~1 GB free. Please free up space and try again."
                _downloadState.value = DownloadState.Error(errorMsg, false)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val modelDir = File(context.filesDir, "models")
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            val tempFile = File(context.cacheDir, TEMP_MODEL_FILENAME)
            val finalFile = File(modelDir, MODEL_FILENAME)

            val downloadUrl = BuildConfig.MODEL_DOWNLOAD_URL
            val expectedSize = BuildConfig.MODEL_SIZE_BYTES

            Log.i(TAG, "Starting download from: $downloadUrl")

            val request = Request.Builder()
                .url(downloadUrl)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    403, 429 -> "Download temporarily unavailable. Please try again in a few minutes."
                    404 -> "Model file not found. Please check configuration."
                    else -> "Network error. Please check your connection."
                }
                _downloadState.value = DownloadState.Error(errorMsg, true)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val body = response.body ?: throw Exception("Response body is null")
            val contentLength = body.contentLength()

            Log.i(TAG, "Download started. Content length: $contentLength")

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    var lastProgressUpdate = 0f

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        val progress = if (contentLength > 0) {
                            totalBytesRead.toFloat() / contentLength.toFloat()
                        } else {
                            0f
                        }

                        // Update progress every 1-2%
                        if (progress - lastProgressUpdate >= 0.01f || progress >= 1.0f) {
                            _downloadState.value = DownloadState.Downloading(
                                progress = progress,
                                bytesDownloaded = totalBytesRead,
                                totalBytes = contentLength
                            )
                            lastProgressUpdate = progress
                        }
                    }
                }
            }

            Log.i(TAG, "Download complete. Verifying...")
            _downloadState.value = DownloadState.Verifying

            // Verify file size
            val actualSize = tempFile.length()
            if (actualSize != expectedSize && expectedSize > 0) {
                Log.e(TAG, "File size mismatch. Expected: $expectedSize, Actual: $actualSize")
                tempFile.delete()
                val errorMsg = "Download corrupted. Please try again."
                _downloadState.value = DownloadState.Error(errorMsg, true)
                return@withContext Result.failure(Exception(errorMsg))
            }

            // Move to final location
            if (finalFile.exists()) {
                finalFile.delete()
            }
            tempFile.renameTo(finalFile)

            // Save preferences
            prefs.edit()
                .putBoolean(PREF_MODEL_DOWNLOADED, true)
                .putString(PREF_MODEL_PATH, finalFile.absolutePath)
                .apply()

            Log.i(TAG, "Model downloaded and verified successfully")
            _downloadState.value = DownloadState.Success

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            val errorMsg = e.message ?: "Unknown error occurred"
            _downloadState.value = DownloadState.Error(errorMsg, true)
            Result.failure(e)
        }
    }

    private fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            Log.e(TAG, "Error checking storage", e)
            0L
        }
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
    }
}
