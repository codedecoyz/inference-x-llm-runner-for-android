# Artificial Intelligence Handover Document: Dual Engine Integration

**To Future AI Agent:** This document provides a complete technical overview of the recent architectural changes made to support the Hierarchical Reasoning Model (HRM) and Layer Streaming LLM engines within the `mobile-llama-compyle` Android application.

## 1. Architectural Overview

The application now supports **three distinct execution paths** for user queries:
1. **Existing llama.cpp (untouched)**: CPU/Vulkan inference for standard chat.
2. **HRM Engine (new)**: 27M parameter recurrent reasoning model via ONNX Runtime & NNAPI.
3. **Layer Streaming LLM (new)**: AirLLM-style per-layer chunked transformer inference via ONNX Runtime to minimize RAM usage (<500MB).

### Engine Routing
Routing is handled dynamically via `com.mobilellama.ai.IntentRouter`.
- **Reasoning Intents** (e.g., "solve", "math", "logical"): intercepted by `ChatViewModel`, routed instantly to HRM.
- **Language Intents** (e.g., "write a story"): bypassed to the existing llama.cpp pipeline.

---

## 2. Directory & Component Structure

### C++ Native Layer (`app/src/main/cpp/`)
A completely isolated shared library (`libai_engine.so`) was created to prevent conflicts with the existing `libllama_jni.so`.
- `memory_pool.h`: Fixed-block memory allocator to eliminate `malloc`/`free` overhead during layer streaming.
- `kv_cache.h`: GQA-aware KV cache state manager for cross-layer generation.
- `llm_engine.h` / `llm_engine.cpp`: The layer streaming engine implementation.
- `hrm_engine.h`: The header-only recurrent reasoning engine targeting the NPU.
- `ai_jni_bridge.cpp`: Unified JNI interface linking to `com_mobilellama_ai_AIBridge`.
- `onnxruntime/`: Contains the C++ headers (`include/`) and prebuilt Android arm64 binaries (`lib/arm64-v8a/libonnxruntime.so`).

### CMake Configuration (`app/src/main/cpp/CMakeLists.txt`)
- The `ai_engine` target is **conditional**. It checks for the presence of ONNX Runtime headers and `.so` files.
- If missing, it gracefully skips building `ai_engine.so` and leaves `llama_jni.so` unaffected.
- **Critical Note:** `libggml-vulkan.so` was explicitly removed from `llama_jni`'s `target_link_libraries` to prevent Android's dynamic linker from forcing a Vulkan crash on unsupported GPUs (like Dimensity 6020). It relies on `System.loadLibrary` fallbacks in Kotlin.

### Kotlin UI Layer (`app/src/main/java/com/mobilellama/`)
- `ai/AIBridge.kt`: The JNI bridge mapping to `ai_engine.so`. Handles `UnsatisfiedLinkError` gracefully if ONNX binaries are missing.
- `ai/IntentRouter.kt`: Keyword-based intent classification.
- `util/AssetHelper.kt`: Utility to copy ONNX models from the APK's read-only `assets/` to the application's accessible `filesDir/`.
- `MobileLlamaApplication.kt`: Triggers `AssetHelper.copyAssetsToFilesDir("hrm", "hrm")` on startup.
- `viewmodel/ChatViewModel.kt`: Modified to inject `IntentRouter`. If `shouldUseHRM` is true, it bypassing `InferenceRepository` and calls `AIBridge.inferHRM()` directly on the main/IO threads (since HRM response is near-instant), formats the result, and injects it into the `_messages.value` snapshot.

---

## 3. Current State & Pending Steps

**What IS Working:**
- The entire C++ dual-engine pipeline compiles flawlessly bridging through `ai_engine.so`.
- The intent router is fully integrated into the UI lifecycle.
- Asset extraction is automatic on boot.
- The `feature/hrm-onnx-integration` branch was pushed to Git.

**What is MISSING (Next Steps for You):**

1. **Deploy Model Files:**
   The user needs to place their exported ONNX files into the project:
   - `hrm.onnx` must be placed in `app/src/main/assets/hrm/hrm.onnx`. (The Application class will handle moving it to `filesDir` automatically).
   - If Layer Streaming is activated, `embed.onnx`, `layer_XX.onnx`, and `head.onnx` must be generated and deployed.

2. **Refine Intent Routing (Optional):**
   `IntentRouter` uses basic keyword matching (`"solve"`, `"math"`). This could be replaced with an ML classifier (like a tiny BERT intent detector) or hooked directly into a multi-agent system if desired.

3. **Layer Streaming UI Wiring:**
   The `AIBridge` supports `generateLLM(..., callback)` for the layer engine, but `ChatViewModel` is currently only hooked up to use `HRM` or `llama.cpp`. The layer-streaming LLM still needs to be wired into the ViewModel as an alternative to `llama.cpp` for low-RAM devices.

4. **Testing on Physical Device:**
   Ensure the device's NPU supports the operations in `hrm.onnx` via the NNAPI execution provider. If NNAPI fails, ONNX Runtime will fall back to the CPU automatically.
