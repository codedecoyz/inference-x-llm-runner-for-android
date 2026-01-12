package com.mobilellama.data.model

sealed class InferenceState {
    data object Uninitialized : InferenceState()
    data object Initializing : InferenceState()
    data object Ready : InferenceState()
    data class Generating(val tokensGenerated: Int) : InferenceState()
    data class Error(val message: String) : InferenceState()
}
