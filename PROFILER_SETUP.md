# Android Studio Profiler Setup Guide

This guide details how to configure Android Studio Profilers to gather precise metrics for your Inference-x research paper.

## 1. CPU Profiler Configuration (Native + JVM Tracking)

To accurately measure where time is spent during inference (especially in the JNI and `llama.cpp` layers), you need to configure the CPU profiler to capture native traces.

### Setup Instructions:
1. Open your project in Android Studio.
2. Select **Run > Profile 'app'** (or click the Profiler icon in the toolbar).
3. Once the app starts and the profiler attaches, click anywhere on the **CPU** timeline.
4. In the CPU configuration dropdown (top left of the CPU view), select **Edit Configurations...**.
5. Click the **+** (Add) button to create a new configuration.
6. Name it "Native Inference Trace".
7. Select **Trace C/C++ Functions** (Simpleperf).
8. **Sampling interval:** Set to a high frequency (e.g., 1000 microseconds) for detailed resolution.
9. Click **Apply** and **OK**.
10. To record: Select "Native Inference Trace", click **Record**, generate a response in the app, and click **Stop**.

### What to Look For:
- `nativeGenerateBatched` execution time.
- `llama_decode` and matrix multiplication hotspots inside `llama.cpp`.

---

## 2. Memory Profiler (Detecting JNI Leaks)

Native memory isn't fully managed by the Dalvik/ART Garbage Collector. We must ensure `nativeFree` properly deallocates memory.

### Setup Instructions:
1. Open the **Memory** timeline in the Profiler.
2. Ensure you are on a device running Android 10 (API level 29) or higher to get detailed native memory breakdowns.
3. Check the **Native** category in the memory breakdown.
4. **Leak Detection Scenario:**
   - Load a model (watch Native memory spike).
   - Generate a long chat.
   - Click "Clear Cache" or "Release" in your app's lifecycle.
   - Force a Garbage Collection by clicking the **Trash Can icon** in the profiler.
   - Native memory should return to the baseline level. If it staircase-climbs over multiple generations, you have a leak in `llama_jni.cpp`.

---

## 3. Energy Profiler (Power Measurements)

To claim battery friendliness in your paper, you need empirical power data.

### Setup Instructions:
1. Open the **Energy** timeline in the Profiler (Android 8.0+ required).
2. The energy profiler categorizes usage into Light, Medium, and Heavy.
3. For exact milliwatt (mW) readings, rely on the `InferenceMetrics.kt` BatteryManager implementation during your benchmark runs.
4. The Energy Profiler UI is best used to identify *constant wakes* or *unnecessary background work* when the app is idle.

---

## 4. Custom Traces for Inference Calls

To map Profiler data directly to your code logic, use the `android.os.Trace` API. This will draw custom blocks in the CPU profiler timeline.

### Implementation Snippet (Add to `LlamaEngine.kt`):
```kotlin
import android.os.Trace

suspend fun generate(...) {
    Trace.beginSection("InferenceX_Generate")
    try {
        // ... nativeGenerateBatched call ...
    } finally {
        Trace.endSection()
    }
}
```
*Note: Ensure you compile with API level 29+ to view these sections in the System Trace.*

---

## ✅ Screenshot Checklist for Paper

As you gather data for your MobiSys/MLSys paper, ensure you capture the following visual evidence:

- [ ] **Native CPU Trace Flame Chart:** Showing the dominance of `llama_decode` over JVM overhead.
- [ ] **Memory Allocation Tracker:** A graph showing stable native footprint during continuous generation (proof of zero-leak architecture).
- [ ] **Batched JNI vs Single JNI Comparison:** Two side-by-side CPU traces showing the reduced JNI context-switching overhead in Task 2.1.
- [ ] **System Trace Timeline:** Showing the exact `InferenceX_Generate` slice spanning the duration between token batches.
