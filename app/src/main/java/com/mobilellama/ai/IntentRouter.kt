package com.mobilellama.ai

import android.util.Log

/**
 * Intent Router — classifies user queries and routes to the appropriate engine.
 *
 * - Reasoning tasks (math, logic, puzzles) → HRM Engine
 * - Language tasks (chat, creative, Q&A) → Existing llama.cpp (or Layer Streaming LLM)
 *
 * Plugs into the existing inference pipeline without modifying it.
 * The ChatViewModel can use this to decide which engine handles a query.
 */
object IntentRouter {

    private const val TAG = "IntentRouter"

    enum class TaskType {
        REASONING,  // → HRM Engine
        LANGUAGE    // → llama.cpp or Layer Streaming LLM
    }

    // Keywords that trigger HRM routing
    private val reasoningKeywords = setOf(
        "solve", "calculate", "math", "logic", "puzzle",
        "find the", "shortest", "optimal", "how many",
        "prove", "pattern", "sequence", "sudoku", "maze",
        "step by step", "reason", "derive", "equation",
        "what is", "compute", "simplify", "evaluate",
        "factorial", "fibonacci", "prime", "algorithm",
        "sort", "search", "graph", "tree", "binary",
        "probability", "statistics", "regression"
    )

    // Keywords that override to language even if reasoning keywords match
    private val languageOverrides = setOf(
        "write", "story", "poem", "essay", "translate",
        "summarize", "explain like", "describe", "imagine",
        "creative", "fiction", "dialogue"
    )

    /**
     * Classify a user query into REASONING or LANGUAGE task type.
     */
    fun classify(input: String): TaskType {
        val lower = input.lowercase().trim()

        // Check language overrides first (higher priority)
        if (languageOverrides.any { lower.contains(it) }) {
            Log.d(TAG, "🗣️ Language override detected: \"$input\"")
            return TaskType.LANGUAGE
        }

        // Check reasoning keywords
        if (reasoningKeywords.any { lower.contains(it) }) {
            Log.d(TAG, "🧠 Reasoning task detected: \"$input\"")
            return TaskType.REASONING
        }

        // Default to language
        Log.d(TAG, "💬 Default language task: \"$input\"")
        return TaskType.LANGUAGE
    }

    /**
     * Check if HRM engine is available for reasoning tasks.
     * Falls back to llama.cpp if HRM is not initialized.
     */
    fun shouldUseHRM(input: String): Boolean {
        if (!AIBridge.loaded) return false
        if (!AIBridge.isHRMReady) return false
        return classify(input) == TaskType.REASONING
    }

    /**
     * Check if layer streaming LLM is available.
     * Falls back to llama.cpp if layer streaming is not initialized.
     */
    fun shouldUseLayerStreaming(): Boolean {
        if (!AIBridge.loaded) return false
        return AIBridge.isLLMReady
    }
}
