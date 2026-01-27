package com.mobilellama.data.model

enum class PromptType {
    CHATML,     // <|im_start|>system...
    PHI3,       // <|system|>...<|end|>
    MISTRAL,    // <s>[INST]...[/INST]
    TINYLLAMA   // <|system|>... (ChatML-like but specific)
}

data class AiModel(
    val name: String,
    val filename: String,
    val url: String,
    val expectedSize: Long,
    val promptType: PromptType,
    val description: String,
    val ramRequiredGB: Int
)

object ModelRegistry {
    val availableModels = listOf(
        AiModel(
            name = "TinyLlama 1.1B",
            filename = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            url = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            expectedSize = 669000000L, // ~638 MB
            promptType = PromptType.TINYLLAMA,
            description = "Fastest. Good for older phones.",
            ramRequiredGB = 2
        ),
        AiModel(
            name = "Qwen 2.5 1.5B",
            filename = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            expectedSize = 986000000L, // ~940 MB (Estimate for 1.5B Q4)
            promptType = PromptType.CHATML,
            description = "Smart & Efficient. Best all-rounder.",
            ramRequiredGB = 3
        ),
        AiModel(
            name = "Phi-3 Mini 3.8B",
            filename = "Phi-3-mini-4k-instruct-q4.gguf",
            url = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
            expectedSize = 2390000000L, // ~2.2 GB
            promptType = PromptType.PHI3,
            description = "High Intelligence. Needs power.",
            ramRequiredGB = 4
        ),
        AiModel(
            name = "Mistral 7B v0.3",
            filename = "mistral-7b-instruct-v0.3.Q4_K_M.gguf",
            url = "https://huggingface.co/maziyarpanahi/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/Mistral-7B-Instruct-v0.3.Q4_K_M.gguf",
            expectedSize = 4370000000L, // ~4.1 GB
            promptType = PromptType.MISTRAL,
            description = "Pro Level. Flagship phones only.",
            ramRequiredGB = 6
        )
    )
    
    fun getDefault() = availableModels[0] // TinyLlama
}
