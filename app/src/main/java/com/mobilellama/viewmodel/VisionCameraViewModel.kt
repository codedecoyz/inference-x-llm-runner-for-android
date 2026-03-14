package com.mobilellama.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilellama.data.model.PromptType
import com.mobilellama.data.repository.InferenceRepository
import com.mobilellama.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class VisionCameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
    private val inferenceRepository: InferenceRepository
) : ViewModel() {

    private val _cameraState = MutableStateFlow<VisionCameraState>(VisionCameraState.Idle)
    val cameraState: StateFlow<VisionCameraState> = _cameraState.asStateFlow()

    private val _capturedImage = MutableStateFlow<Bitmap?>(null)
    val capturedImage: StateFlow<Bitmap?> = _capturedImage.asStateFlow()

    private val _promptText = MutableStateFlow("What is this?")
    val promptText: StateFlow<String> = _promptText.asStateFlow()

    fun setPromptText(text: String) {
        _promptText.value = text
    }

    fun onImageCaptured(bitmap: Bitmap) {
        // Scale down for memory safety
        val maxDim = 1024f
        val scale = kotlin.math.min(maxDim / bitmap.width, maxDim / bitmap.height)
        val safeBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        _capturedImage.value = safeBitmap
        _cameraState.value = VisionCameraState.Idle
    }

    fun clearCapture() {
        _capturedImage.value = null
        _cameraState.value = VisionCameraState.Idle
    }

    fun analyzeImage() {
        val bitmap = _capturedImage.value
        if (bitmap == null) {
            _cameraState.value = VisionCameraState.Error("No image captured yet.")
            return
        }

        viewModelScope.launch {
            _cameraState.value = VisionCameraState.Analyzing

            try {
                // 1. Find the Vision Model
                val visionModel = modelRepository.selectedModel.value.let {
                    if (it.promptType == PromptType.VISION) it
                    else com.mobilellama.data.model.ModelRegistry.availableModels.find { m -> m.promptType == PromptType.VISION }
                }

                if (visionModel == null) {
                    _cameraState.value = VisionCameraState.Error("No Vision model found. Please download one from Model Manager.")
                    return@launch
                }

                if (!modelRepository.isModelDownloaded(visionModel)) {
                    _cameraState.value = VisionCameraState.Error("Vision model '${visionModel.name}' not downloaded yet.")
                    return@launch
                }

                // 2. Initialize engine
                val path = java.io.File(context.getExternalFilesDir(null), "models/${visionModel.filename}").absolutePath
                val mmprojPath = visionModel.mmprojFilename?.let {
                    java.io.File(context.getExternalFilesDir(null), "models/${it}").absolutePath
                }

                val loadResult = inferenceRepository.initializeModel(path, mmprojPath)
                if (loadResult.isFailure) {
                    _cameraState.value = VisionCameraState.Error("Engine init failed: ${loadResult.exceptionOrNull()?.message}")
                    return@launch
                }

                // 3. Generate response
                val start = System.currentTimeMillis()
                val responseBuilder = StringBuilder()
                val promptMsg = _promptText.value.takeIf { it.isNotBlank() } ?: "Describe this image in detail."

                inferenceRepository.generateResponse(
                    prompt = promptMsg,
                    imageBitmap = bitmap
                ) { token ->
                    responseBuilder.append(token)
                    val timeMs = System.currentTimeMillis() - start
                    _cameraState.value = VisionCameraState.Streaming(responseBuilder.toString(), timeMs)
                }

            } catch (e: Exception) {
                Log.e("VisionCameraVM", "Analysis failed", e)
                _cameraState.value = VisionCameraState.Error(e.message ?: "Unknown error during analysis")
            }
        }
    }
}

sealed class VisionCameraState {
    object Idle : VisionCameraState()
    object Analyzing : VisionCameraState()
    data class Streaming(val partialResult: String, val elapsedMs: Long) : VisionCameraState()
    data class Error(val message: String) : VisionCameraState()
}
