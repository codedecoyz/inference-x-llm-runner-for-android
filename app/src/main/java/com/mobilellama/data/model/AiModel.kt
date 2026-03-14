package com.mobilellama.data.model

enum class PromptType {
    CHATML,     // <|im_start|>system...
    PHI3,       // <|system|>...<|end|>
    MISTRAL,    // <s>[INST]...[/INST]
    TINYLLAMA,  // <|system|>... (ChatML-like but specific)
    VISION      // Image Classification / Object Detection
}

data class AiModel(
    val name: String,
    val filename: String,
    val url: String,
    val expectedSize: Long,
    val promptType: PromptType,
    val description: String,
    val ramRequiredGB: Int,
    
    // Optional fields for Vision-Language Models (VLM)
    val mmprojFilename: String? = null,
    val mmprojUrl: String? = null,
    val mmprojExpectedSize: Long? = null
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
            url = "https://huggingface.co/maziyarpanahi/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/mistral-7b-instruct-v0.3.Q4_K_M.gguf",
            expectedSize = 4370000000L, // ~4.1 GB
            promptType = PromptType.MISTRAL,
            description = "Pro Level. Flagship phones only.",
            ramRequiredGB = 6
        ),

        // ── Vision Models ──────────────────────────────────────────────────────
        AiModel(
            name = "SmolVLM 500M (Tiny Vision)",
            filename = "SmolVLM-500M-Instruct-Q8_0.gguf",
            url = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/SmolVLM-500M-Instruct-Q8_0.gguf",
            expectedSize = 530000000L, // ~530 MB
            promptType = PromptType.VISION,
            description = "Smallest Vision Model (~530MB). Runs on any phone.",
            ramRequiredGB = 2,
            mmprojFilename = "mmproj-SmolVLM-500M-Instruct-f16.gguf",
            mmprojUrl = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/mmproj-SmolVLM-500M-Instruct-f16.gguf",
            mmprojExpectedSize = 380000000L // ~380 MB
        ),
        AiModel(
            name = "Moondream2 1.8B (Fast Vision)",
            filename = "moondream2-text-model-f16_ct-vicuna.gguf",
            url = "https://huggingface.co/ggml-org/moondream2-20250414-GGUF/resolve/main/moondream2-text-model-f16_ct-vicuna.gguf",
            expectedSize = 2870000000L, // ~2.84 GB
            promptType = PromptType.VISION,
            description = "Lightweight Vision Model. Very fast, perfect for mobile.",
            ramRequiredGB = 4,
            mmprojFilename = "moondream2-mmproj-f16-20250414.gguf",
            mmprojUrl = "https://huggingface.co/ggml-org/moondream2-20250414-GGUF/resolve/main/moondream2-mmproj-f16-20250414.gguf",
            mmprojExpectedSize = 910000000L // ~910 MB
        ),
        AiModel(
            name = "MiniCPM-V 2.6 (Smart Vision)",
            filename = "ggml-model-Q4_K_M.gguf",
            url = "https://huggingface.co/openbmb/MiniCPM-V-2_6-gguf/resolve/main/ggml-model-Q4_K_M.gguf",
            expectedSize = 5600000000L, // ~5.6 GB
            promptType = PromptType.VISION,
            description = "High quality Vision QA. Flagship devices recommended.",
            ramRequiredGB = 8,
            mmprojFilename = "mmproj-model-f16.gguf",
            mmprojUrl = "https://huggingface.co/openbmb/MiniCPM-V-2_6-gguf/resolve/main/mmproj-model-f16.gguf",
            mmprojExpectedSize = 1070000000L // ~1.07 GB
        ),
        AiModel(
            name = "LLaVA 1.5 7B (Vision QA)",
            filename = "ggml-model-q4_k.gguf",
            url = "https://huggingface.co/mys/ggml_llava-v1.5-7b/resolve/main/ggml-model-q4_k.gguf",
            expectedSize = 4080000000L, // ~4.08 GB
            promptType = PromptType.VISION,
            description = "Advanced Vision Model. Can see images and answer questions.",
            ramRequiredGB = 7,
            mmprojFilename = "mmproj-model-f16.gguf",
            mmprojUrl = "https://huggingface.co/mys/ggml_llava-v1.5-7b/resolve/main/mmproj-model-f16.gguf",
            mmprojExpectedSize = 624000000L // ~624 MB
        )
    )
    
    fun getDefault() = availableModels[0] // TinyLlama
}
