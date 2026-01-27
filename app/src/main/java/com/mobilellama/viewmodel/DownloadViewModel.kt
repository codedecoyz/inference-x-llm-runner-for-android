package com.mobilellama.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilellama.data.model.AiModel
import com.mobilellama.data.model.DownloadState
import com.mobilellama.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val modelRepository: ModelRepository
) : ViewModel() {

    // Expose the partial map or full map
    val modelStates: StateFlow<Map<String, DownloadState>> = modelRepository.modelStates
    val selectedModel: StateFlow<AiModel> = modelRepository.selectedModel
    val availableModels = com.mobilellama.data.model.ModelRegistry.availableModels

    // Backward compatibility for single state UI (optional)
    val downloadState: StateFlow<DownloadState> = modelRepository.downloadState

    init {
        checkAllModels()
    }

    private fun checkAllModels() {
        viewModelScope.launch {
            modelRepository.checkAllModels()
        }
    }
    
    fun selectModel(model: com.mobilellama.data.model.AiModel) {
        modelRepository.selectModel(model)
        // No need to reset download state globally anymore
    }

    fun startDownload(model: AiModel) {
        viewModelScope.launch {
            modelRepository.downloadModel(model)
        }
    }

    fun retryDownload(model: AiModel) {
        // modelRepository.resetDownloadState(model) // Not strictly needed if we just overwrite status
        startDownload(model)
    }
    
    // Legacy support for the main "Big Button" if needed
    fun startSelectedDownload() {
        startDownload(selectedModel.value)
    }
}
