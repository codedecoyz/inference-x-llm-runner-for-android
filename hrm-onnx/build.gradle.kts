// app/build.gradle.kts

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yourapp.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yourapp.ai"
        minSdk = 28          // NNAPI full support from API 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Only build for ARM64 — no x86 (saves APK size, phones are ARM64)
        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    // Package the ONNX Runtime .so with the APK
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.01.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // ONNX Runtime for Android (for Java/Kotlin side if needed)
    // The C++ side links directly to libonnxruntime.so
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Logging
    implementation("androidx.core:core-ktx:1.12.0")
}
