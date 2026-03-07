cmake_minimum_required(VERSION 3.18.1)
project(llama_jni)

# Include directories for llama.cpp headers
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/include)

# Add JNI bridge source
add_library(llama_jni SHARED
    llama_jni.cpp
    llama_jni.h
)

# Link prebuilt libllama.so
add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libllama.so)

# Link prebuilt libomp.so
add_library(omp SHARED IMPORTED)
set_target_properties(omp PROPERTIES IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libomp.so)

# Link prebuilt libggml-base.so
add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-base.so)

# Link prebuilt libggml-cpu.so
add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-cpu.so)

# Link prebuilt libggml-vulkan.so
add_library(ggml-vulkan SHARED IMPORTED)
set_target_properties(ggml-vulkan PROPERTIES IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-vulkan.so)

# Link prebuilt libggml.so (dependency of llama)
add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml.so)

# Link JNI bridge with llama.cpp
# 🔥 CRITICAL: Removed ggml-vulkan from target_link_libraries!
# If we link it here, Android's dynamic linker will forcefully load libggml-vulkan.so 
# and bypass our Kotlin device-detection fallback. It must be conditionally loaded via System.loadLibrary.
target_link_libraries(llama_jni llama ggml log)

# ═══════════════════════════════════════════════
# AI ENGINE — HRM + Layer Streaming LLM (ONNX Runtime)
# Separate .so from llama_jni — does NOT touch existing code
# ═══════════════════════════════════════════════

set(ONNXRUNTIME_DIR ${CMAKE_CURRENT_SOURCE_DIR}/onnxruntime)

# Check if ONNX Runtime is available
if(EXISTS "${ONNXRUNTIME_DIR}/lib/${ANDROID_ABI}/libonnxruntime.so"
   AND EXISTS "${ONNXRUNTIME_DIR}/include/onnxruntime_cxx_api.h")

    message(STATUS "✅ ONNX Runtime found — building ai_engine")

    add_library(onnxruntime SHARED IMPORTED)
    set_target_properties(onnxruntime PROPERTIES
        IMPORTED_LOCATION ${ONNXRUNTIME_DIR}/lib/${ANDROID_ABI}/libonnxruntime.so)

    # AI Engine shared library
    add_library(ai_engine SHARED
        llm_engine.cpp
        ai_jni_bridge.cpp
    )

    target_include_directories(ai_engine PRIVATE
        ${ONNXRUNTIME_DIR}/include
        ${CMAKE_CURRENT_SOURCE_DIR}
    )

    target_compile_definitions(ai_engine PRIVATE HAS_ONNXRUNTIME=1)

    # ARM64 optimizations
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        target_compile_options(ai_engine PRIVATE -O3 -ffast-math)
    endif()

    target_link_libraries(ai_engine
        onnxruntime
        log
        android
    )

    # Copy ORT .so to output for APK packaging
    add_custom_command(TARGET ai_engine POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
            ${ONNXRUNTIME_DIR}/lib/${ANDROID_ABI}/libonnxruntime.so
            ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libonnxruntime.so
    )

else()
    message(WARNING "⚠️ ONNX Runtime NOT found at ${ONNXRUNTIME_DIR} — ai_engine will NOT be built")
    message(WARNING "   Download ONNX Runtime Android from: https://github.com/microsoft/onnxruntime/releases")
    message(WARNING "   Place headers in: ${ONNXRUNTIME_DIR}/include/")
    message(WARNING "   Place .so in: ${ONNXRUNTIME_DIR}/lib/${ANDROID_ABI}/libonnxruntime.so")
endif()
