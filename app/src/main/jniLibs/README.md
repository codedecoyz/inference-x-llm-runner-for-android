# Native Libraries Setup

This directory should contain the prebuilt native libraries and headers for llama.cpp.

## Required Structure

```
jniLibs/
├── arm64-v8a/
│   └── libllama.so       (Prebuilt llama.cpp library for arm64-v8a)
└── include/
    ├── llama.h           (llama.cpp header from llama.cpp repo)
    └── ggml.h            (GGML header from llama.cpp repo)
```

## How to Obtain These Files

### Option 1: Build from llama.cpp source

1. Clone llama.cpp repository:
   ```bash
   git clone https://github.com/ggerganov/llama.cpp
   cd llama.cpp
   ```

2. Build for Android arm64-v8a using CMake with Android NDK:
   ```bash
   mkdir build-android
   cd build-android
   cmake .. \
     -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
     -DANDROID_ABI=arm64-v8a \
     -DANDROID_PLATFORM=android-28 \
     -DCMAKE_BUILD_TYPE=Release
   make -j8
   ```

3. Copy the resulting `libllama.so` to `app/src/main/jniLibs/arm64-v8a/`

4. Copy headers `llama.h` and `ggml.h` to `app/src/main/jniLibs/include/`

### Option 2: Download prebuilt libraries

Check llama.cpp releases or community-built Android binaries.

## Verification

After placing the files:
- `libllama.so` should be ~5-10 MB
- Headers should match the llama.cpp version you built
- CMake should be able to link against these files during build

## Important Notes

- The project is configured for **arm64-v8a only** (64-bit ARM devices)
- Ensure API level compatibility (minimum API 28)
- The library must export the C API functions used in llama_jni.cpp
