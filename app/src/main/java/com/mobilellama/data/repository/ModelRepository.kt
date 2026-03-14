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
        val hasMain = file.exists() && file.length() > 0
        if (!hasMain) return false
        
        if (model.mmprojFilename != null) {
            val projFile = File(context.getExternalFilesDir(null), "models/${model.mmprojFilename}")
            return projFile.exists() && projFile.length() > 0
        }
        return true
    }

    fun getModelPath(): String {
        return File(context.getExternalFilesDir(null), "models/${selectedModel.value.filename}").absolutePath
    }
    
    fun getMmprojPath(model: AiModel): String? {
        if (model.mmprojFilename == null) return null
        return File(context.getExternalFilesDir(null), "models/${model.mmprojFilename}").absolutePath
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
            val totalExpected = targetModel.expectedSize + (targetModel.mmprojExpectedSize ?: 0L)
            
            if (availableBytes < totalExpected + 50_000_000) { 
                val errorMsg = "Storage full. Need ${(totalExpected / 1024 / 1024)} MB."
                updateModelState(targetModel.filename, DownloadState.Error(errorMsg, false))
                return@withContext
            }

            val modelDir = File(context.getExternalFilesDir(null), "models")
            if (!modelDir.exists()) modelDir.mkdirs()

            val successMain = downloadSingleFile(
                targetModel.url, targetModel.filename, targetModel.expectedSize, 
                targetModel.filename, 0f, if (targetModel.mmprojUrl != null) 0.8f else 1.0f
            )
            if (!successMain) return@withContext

            if (targetModel.mmprojUrl != null && targetModel.mmprojFilename != null) {
                val successProj = downloadSingleFile(
                    targetModel.mmprojUrl, targetModel.mmprojFilename, targetModel.mmprojExpectedSize ?: 0L,
                    targetModel.filename, 0.8f, 0.2f
                )
                if (!successProj) return@withContext
            }

            updateModelState(targetModel.filename, DownloadState.Success)
            Log.i(TAG, "Download success: ${targetModel.name}")

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            updateModelState(targetModel.filename, DownloadState.Error(e.message ?: "Error", true))
        }
    }

    private fun downloadSingleFile(
        url: String, filename: String, expectedSize: Long, trackerName: String,
        progressOffset: Float, progressScale: Float
    ): Boolean {
        val modelDir = File(context.getExternalFilesDir(null), "models")
        val tempFile = File(context.cacheDir, "$filename.tmp")
        val finalFile = File(modelDir, filename)

        if (finalFile.exists() && finalFile.length() == expectedSize) return true

        Log.i(TAG, "Starting download for $filename")

        val existingLength = if (tempFile.exists()) tempFile.length() else 0L
        val isResume = existingLength > 0 && existingLength < expectedSize

        val requestBuilder = Request.Builder().url(url)
        if (isResume) {
            requestBuilder.header("Range", "bytes=$existingLength-")
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (!response.isSuccessful) {
            if (response.code == 416) tempFile.delete()
            val errorMsg = "Network error: ${response.code} for $filename"
            updateModelState(trackerName, DownloadState.Error(errorMsg, true))
            return false
        }

        val isResumed = isResume && response.code == 206
        val contentLength = response.body?.contentLength() ?: 0L
        val totalExpected = if (isResumed) existingLength + contentLength else contentLength

        response.body?.byteStream()?.use { input ->
            FileOutputStream(tempFile, isResumed).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = if (isResumed) existingLength else 0L
                var lastProgress = 0f

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    val fileProgress = if (totalExpected > 0) totalBytesRead.toFloat() / totalExpected else 0f
                    val overallProgress = progressOffset + (fileProgress * progressScale)
                    
                    if (overallProgress - lastProgress >= 0.01f || fileProgress >= 1.0f) {
                        updateModelState(trackerName, DownloadState.Downloading(overallProgress, (overallProgress * 100).toLong(), 100L))
                        lastProgress = overallProgress
                    }
                }
            }
        }

        updateModelState(trackerName, DownloadState.Verifying)

        val actualSize = tempFile.length()
        if (actualSize != totalExpected && totalExpected > 0) {
             val msg = "Download incomplete. Expected $totalExpected, got $actualSize"
             updateModelState(trackerName, DownloadState.Error(msg, true))
             return false
        }

        if (finalFile.exists()) finalFile.delete()
        if (tempFile.renameTo(finalFile) || (tempFile.copyTo(finalFile, true).also { tempFile.delete() }.exists())) {
             return true
        } else {
             updateModelState(trackerName, DownloadState.Error("Failed to save file", true))
             return false
        }
    }

    private fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) { 0L }
    }
    
    suspend fun deleteModel(model: AiModel) = withContext(Dispatchers.IO) {
        try {
            val modelDir = File(context.getExternalFilesDir(null), "models")
            
            // Delete main model file
            val mainFile = File(modelDir, model.filename)
            if (mainFile.exists()) {
                mainFile.delete()
                Log.i(TAG, "Deleted model file: ${model.filename}")
            }
            
            // Delete mmproj file if it exists
            if (model.mmprojFilename != null) {
                val projFile = File(modelDir, model.mmprojFilename)
                if (projFile.exists()) {
                    projFile.delete()
                    Log.i(TAG, "Deleted mmproj file: ${model.mmprojFilename}")
                }
            }
            
            // Reset state to Idle
            updateModelState(model.filename, DownloadState.Idle)
            
            // If the deleted model was selected, switch to the default
            if (_selectedModel.value.name == model.name) {
                val defaultModel = ModelRegistry.getDefault()
                _selectedModel.value = defaultModel
                prefs.edit().putString(PREF_SELECTED_MODEL, defaultModel.name).apply()
                Log.i(TAG, "Switched to default model: ${defaultModel.name}")
            }
            
            Log.i(TAG, "Model deleted: ${model.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete model: ${model.name}", e)
        }
    }

    fun resetDownloadState() {
        // Deprecated, resets SELECTED model state
        updateModelState(_selectedModel.value.filename, DownloadState.Idle)
    }
}
