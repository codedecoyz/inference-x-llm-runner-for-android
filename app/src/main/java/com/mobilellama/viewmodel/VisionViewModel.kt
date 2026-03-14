package com.mobilellama.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilellama.ai.AIBridge
import com.mobilellama.data.model.AiModel
import com.mobilellama.data.model.PromptType
import com.mobilellama.data.repository.ModelRepository
import com.mobilellama.data.repository.InferenceRepository
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
class VisionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
    private val inferenceRepository: InferenceRepository
) : ViewModel() {

    private val _visionState = MutableStateFlow<VisionState>(VisionState.Idle)
    val visionState: StateFlow<VisionState> = _visionState.asStateFlow()

    private val _selectedImage = MutableStateFlow<Bitmap?>(null)
    val selectedImage: StateFlow<Bitmap?> = _selectedImage.asStateFlow()

    private val _promptText = MutableStateFlow("")
    val promptText: StateFlow<String> = _promptText.asStateFlow()

    fun setPromptText(text: String) {
        _promptText.value = text
    }

    fun setImageUri(uri: android.net.Uri) {
        _visionState.value = VisionState.Idle
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (originalBitmap != null) {
                    val maxDim = 1024f
                    val scale = kotlin.math.min(maxDim / originalBitmap.width, maxDim / originalBitmap.height)
                    val safeBitmap = if (scale < 1.0f) {
                        Bitmap.createScaledBitmap(originalBitmap, (originalBitmap.width * scale).toInt(), (originalBitmap.height * scale).toInt(), true)
                    } else {
                        originalBitmap
                    }
                    _selectedImage.value = safeBitmap
                }
            } catch (e: Exception) {
                Log.e("VisionViewModel", "Failed to load image", e)
                _visionState.value = VisionState.Error("Failed to load image: ${e.message}")
            }
        }
    }

    fun setImage(bitmap: Bitmap) {
        _selectedImage.value = bitmap
        _visionState.value = VisionState.Idle
    }

    fun analyzeImage() {
        val bitmap = _selectedImage.value ?: return
        
        viewModelScope.launch {
            _visionState.value = VisionState.Analyzing

            // 1. Find the Vision Model
            val visionModel = modelRepository.selectedModel.value.let {
                if (it.promptType == PromptType.VISION) it 
                else com.mobilellama.data.model.ModelRegistry.availableModels.find { m -> m.promptType == PromptType.VISION }
            }

            if (visionModel == null || !modelRepository.isModelDownloaded(visionModel)) {
                _visionState.value = VisionState.Error("Vision model (LLaVA) not downloaded. Please manage models.")
                return@launch
            }

            // 2. Initialize Engine if needed
            val path = java.io.File(context.getExternalFilesDir(null), "models/${visionModel.filename}").absolutePath
            val mmprojPath = visionModel.mmprojFilename?.let { java.io.File(context.getExternalFilesDir(null), "models/${it}").absolutePath }
            
            val loadResult = inferenceRepository.initializeModel(path, mmprojPath)
            if (loadResult.isFailure) {
                _visionState.value = VisionState.Error("Failed to initialize Vision Engine: ${loadResult.exceptionOrNull()?.message}")
                return@launch
            }

            try {
                // 3. Infer
                val start = System.currentTimeMillis()
                val responseBuilder = java.lang.StringBuilder()
                _visionState.value = VisionState.Success("Generating...", 0)

                val promptMsg = _promptText.value.takeIf { it.isNotBlank() } ?: "Describe this image in detail."

                inferenceRepository.generateResponse(
                    prompt = promptMsg,
                    imageBitmap = bitmap
                ) { token ->
                    responseBuilder.append(token)
                    val timeMs = System.currentTimeMillis() - start
                    _visionState.value = VisionState.Success(responseBuilder.toString(), timeMs)
                }

            } catch (e: Exception) {
                Log.e("VisionViewModel", "Analysis crash", e)
                _visionState.value = VisionState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class VisionState {
    object Idle : VisionState()
    object Analyzing : VisionState()
    data class Success(val resultText: String, val inferenceTimeMs: Long) : VisionState()
    data class Error(val message: String) : VisionState()
}
