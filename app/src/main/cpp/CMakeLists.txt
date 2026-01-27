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

# Link prebuilt libggml.so (dependency of llama)
add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml.so)

# Link JNI bridge with llama.cpp
target_link_libraries(llama_jni llama ggml log)
