cmake_minimum_required(VERSION 3.22.1)
project("ai_engine")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ─────────────────────────────────────────────
# Optimization flags for ARM64
# ─────────────────────────────────────────────
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -march=armv8-a+fp16 -ffast-math")
    message(STATUS "Building for ARM64 with NEON + FP16")
endif()

# ─────────────────────────────────────────────
# ONNX Runtime — download from official release
# Place the AAR contents or .so + headers in:
#   app/src/main/cpp/onnxruntime/
#     ├── include/
#     │   └── onnxruntime_cxx_api.h  (and other headers)
#     └── lib/
#         └── arm64-v8a/
#             └── libonnxruntime.so
# ─────────────────────────────────────────────
set(ONNXRUNTIME_DIR ${CMAKE_SOURCE_DIR}/onnxruntime)

add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES
    IMPORTED_LOCATION ${ONNXRUNTIME_DIR}/lib/${ANDROID_ABI}/libonnxruntime.so)

include_directories(
    ${ONNXRUNTIME_DIR}/include
    ${CMAKE_SOURCE_DIR}
)

# ─────────────────────────────────────────────
# Our AI engine shared library
# ─────────────────────────────────────────────
add_library(
    ai_engine
    SHARED

    # Core engines
    llm_engine.cpp
    jni_bridge.cpp
    # hrm_engine.h is header-only, no .cpp needed
    # memory_pool.h is header-only
    # kv_cache.h is header-only
)

# ─────────────────────────────────────────────
# Link libraries
# ─────────────────────────────────────────────
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    ai_engine
    onnxruntime
    ${log-lib}
    ${android-lib}
)

# ─────────────────────────────────────────────
# Copy ONNX Runtime .so to output for packaging
# ─────────────────────────────────────────────
add_custom_command(TARGET ai_engine POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        ${ONNXRUNTIME_DIR}/lib/${ANDROID_ABI}/libonnxruntime.so
        ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libonnxruntime.so
)
