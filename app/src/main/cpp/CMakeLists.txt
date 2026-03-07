cmake_minimum_required(VERSION 3.18.1)
project(llama_jni)

# (Old prebuilt jniLibs block removed to avoid header conflicts)

# Add JNI bridge source
add_library(llama_jni SHARED
    llama_jni.cpp
    llama_jni.h
)

# Set up llama.cpp build options
set(LLAMA_STATIC ON CACHE BOOL "Build static libraries" FORCE)
set(GGML_OPENCL ON CACHE BOOL "Enable OpenCL backend" FORCE)
set(LLAMA_OPENCL ON CACHE BOOL "Enable OpenCL backend" FORCE)

# Setup OpenCL headers before adding llama.cpp
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/opencl-headers)
set(OpenCL_INCLUDE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/opencl-headers)

# Android OpenCL Library
# On Android, libOpenCL.so is provided by the device vendor at runtime.
# GGML uses `find_package(OpenCL)` which fails on Android because the NDK doesn't have it.
# We bypass the check by setting OpenCL_FOUND and OpenCL_LIBRARY manually.
set(OpenCL_FOUND TRUE CACHE BOOL "Force OpenCL Found" FORCE)
# Build our mock OpenCL library to satisfy the linker at compile time.
# At runtime, Android's dynamic linker will prioritize the system's libOpenCL.so.
add_library(OpenCL SHARED opencl_stub.cpp)
target_include_directories(OpenCL PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/opencl-headers)
set(OpenCL_LIBRARY OpenCL CACHE STRING "Mock OpenCL Library" FORCE)
set(OpenCL_INCLUDE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/opencl-headers CACHE PATH "OpenCL Headers" FORCE)

# Build llama.cpp from embedded source
add_subdirectory(llama.cpp_src)

# Link JNI bridge with llama.cpp
# The CMake target provided by llama.cpp repo is "llama"
target_link_libraries(llama_jni llama log)
