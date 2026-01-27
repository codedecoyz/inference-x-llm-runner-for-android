package com.mobilellama.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.StatFs
import android.util.Log
import com.mobilellama.data.model.AiModel
import com.mobilellama.data.model.DownloadState
import com.mobilellama.data.model.ModelRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    // Default to TinyLlama if nothing selected
    private val _selectedModel = MutableStateFlow(getSelectedModelFromPrefs())
    val selectedModel: StateFlow<AiModel> = _selectedModel.asStateFlow()

    // Track state for EACH model by filename
    private val _modelStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val modelStates: StateFlow<Map<String, DownloadState>> = _modelStates.asStateFlow()

    // Deprecated single state accessor (returns state of SELECTED model)
    // We keep this for backward compatibility with ViewModels until they are fully migrated
    val downloadState: StateFlow<DownloadState> = _modelStates.asStateFlow().mapState { states ->
        // Default to Idle if not found
        states[_selectedModel.value.filename] ?: DownloadState.Idle
    }
    
    // Helper to map Flow
    private fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> {
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main)
        val initial = transform(value)
        val flow = this.map(transform)
        return flow.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, initial)
    }

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private const val TAG = "ModelRepository"
        private const val PREF_MODEL_DOWNLOADED = "model_downloaded"
        private const val PREF_MODEL_PATH = "model_path"
        private const val PREF_SELECTED_MODEL = "selected_model_name"
    }
    
    fun getModelState(model: AiModel): DownloadState {
        return _modelStates.value[model.filename] ?: if (isModelDownloaded(model)) DownloadState.Success else DownloadState.Idle
    }
    
    private fun updateModelState(filename: String, state: DownloadState) {
        val newMap = _modelStates.value.toMutableMap()
        newMap[filename] = state
        _modelStates.value = newMap
    }

    fun selectModel(model: AiModel) {
        _selectedModel.value = model
        prefs.edit().putString(PREF_SELECTED_MODEL, model.name).apply()
    }

    private fun getSelectedModelFromPrefs(): AiModel {
        val name = prefs.getString(PREF_SELECTED_MODEL, ModelRegistry.getDefault().name)
        return ModelRegistry.availableModels.find { it.name == name } ?: ModelRegistry.getDefault()
    }

    // Check if the CURRENT selected model is on disk
    fun isModelDownloaded(): Boolean {
        return isModelDownloaded(selectedModel.value)
    }

    // Check if SPECIFIC model is on disk
    fun isModelDownloaded(model: AiModel): Boolean {
        val file = File(context.getExternalFilesDir(null), "models/${model.filename}")
        return file.exists() && file.length() > 0
    }

    fun getModelPath(): String {
        return File(context.getExternalFilesDir(null), "models/${selectedModel.value.filename}").absolutePath
    }

    // Initial check for ALL models
    suspend fun checkAllModels() = withContext(Dispatchers.IO) {
        val newStates = _modelStates.value.toMutableMap()
        for (model in ModelRegistry.availableModels) {
            if (isModelDownloaded(model)) {
                newStates[model.filename] = DownloadState.Success
            } else {
                newStates[model.filename] = DownloadState.Idle
            }
        }
        _modelStates.value = newStates
    }

    // Kept for backward compatibility (downloads SELECTED model)
    suspend fun downloadModel() {
        downloadModel(_selectedModel.value)
    }

    suspend fun downloadModel(targetModel: AiModel) = withContext(Dispatchers.IO) {
        try {
            updateModelState(targetModel.filename, DownloadState.Checking)

            // Check available storage
            val availableBytes = getAvailableStorageBytes()
            if (availableBytes < targetModel.expectedSize + 50_000_000) { 
                val errorMsg = "Storage full. Need ${(targetModel.expectedSize / 1024 / 1024)} MB."
                updateModelState(targetModel.filename, DownloadState.Error(errorMsg, false))
                return@withContext
            }

            val modelDir = File(context.getExternalFilesDir(null), "models")
            if (!modelDir.exists()) modelDir.mkdirs()

            val tempFile = File(context.cacheDir, "${targetModel.filename}.tmp")
            val finalFile = File(modelDir, targetModel.filename)
            val downloadUrl = targetModel.url
            val expectedSize = targetModel.expectedSize

            Log.i(TAG, "Starting download for ${targetModel.name}")

            val existingLength = if (tempFile.exists()) tempFile.length() else 0L
            val isResume = existingLength > 0 && existingLength < expectedSize

            val requestBuilder = Request.Builder().url(downloadUrl)
            if (isResume) {
                requestBuilder.header("Range", "bytes=$existingLength-")
            }

            val response = client.newCall(requestBuilder.build()).execute()

            if (!response.isSuccessful) {
                if (response.code == 416) tempFile.delete()
                val errorMsg = "Network error: ${response.code}"
                updateModelState(targetModel.filename, DownloadState.Error(errorMsg, true))
                return@withContext
            }

            val isResumed = isResume && response.code == 206
            val contentLength = response.body?.contentLength() ?: 0L
            val totalExpected = if (isResumed) existingLength + contentLength else contentLength

            Log.i(TAG, "Download started: $contentLength bytes")

            response.body?.byteStream()?.use { input ->
                FileOutputStream(tempFile, isResumed).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = if (isResumed) existingLength else 0L
                    var lastProgress = 0f

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        val progress = if (totalExpected > 0) totalBytesRead.toFloat() / totalExpected else 0f
                        if (progress - lastProgress >= 0.01f || progress >= 1.0f) {
                            updateModelState(targetModel.filename, DownloadState.Downloading(progress, totalBytesRead, totalExpected))
                            lastProgress = progress
                        }
                    }
                }
            }

            updateModelState(targetModel.filename, DownloadState.Verifying)

            val actualSize = tempFile.length()
            if (actualSize != totalExpected && totalExpected > 0) {
                 val msg = "Download incomplete. Expected $totalExpected, got $actualSize"
                 updateModelState(targetModel.filename, DownloadState.Error(msg, true))
                 return@withContext
            }

            if (finalFile.exists()) finalFile.delete()
            if (tempFile.renameTo(finalFile) || (tempFile.copyTo(finalFile, true).also { tempFile.delete() }.exists())) {
                 updateModelState(targetModel.filename, DownloadState.Success)
                 Log.i(TAG, "Download success: ${targetModel.name}")
            } else {
                 updateModelState(targetModel.filename, DownloadState.Error("Failed to save file", true))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            updateModelState(targetModel.filename, DownloadState.Error(e.message ?: "Error", true))
        }
    }

    private fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) { 0L }
    }
    
    fun resetDownloadState() {
        // Deprecated, resets SELECTED model state
        updateModelState(_selectedModel.value.filename, DownloadState.Idle)
    }
}
