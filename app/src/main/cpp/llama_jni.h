#ifndef LLAMA_JNI_H
#define LLAMA_JNI_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL
Java_com_mobilellama_native_LlamaEngine_nativeInit(
    JNIEnv* env, jobject thiz, jstring modelPath, jint contextSize, jint numThreads);

JNIEXPORT jboolean JNICALL
Java_com_mobilellama_native_LlamaEngine_nativeGenerate(
    JNIEnv* env, jobject thiz, jlong handle, jstring prompt, jint maxTokens, jobject callback);

JNIEXPORT void JNICALL
Java_com_mobilellama_native_LlamaEngine_nativeStop(
    JNIEnv* env, jobject thiz, jlong handle);

JNIEXPORT void JNICALL
Java_com_mobilellama_native_LlamaEngine_nativeFree(
    JNIEnv* env, jobject thiz, jlong handle);

JNIEXPORT void JNICALL
Java_com_mobilellama_native_LlamaEngine_nativeClearCache(
    JNIEnv* env, jobject thiz, jlong handle);

#ifdef __cplusplus
}
#endif

#endif // LLAMA_JNI_H
