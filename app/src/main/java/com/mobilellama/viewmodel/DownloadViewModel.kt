package com.mobilellama.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    val downloadState: StateFlow<DownloadState> = modelRepository.downloadState

    init {
        checkExistingModel()
    }

    private fun checkExistingModel() {
        viewModelScope.launch {
            modelRepository.checkModel()
        }
    }

    fun startDownload() {
        viewModelScope.launch {
            modelRepository.downloadModel()
        }
    }

    fun retryDownload() {
        modelRepository.resetDownloadState()
        startDownload()
    }
}
