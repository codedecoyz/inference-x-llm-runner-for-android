package com.mobilellama.native

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.roundToInt

/**
 * Performance metrics collector for Inference-x
 * Captures tokens/sec, memory, power, and latency for paper evaluation
 */
object InferenceMetrics {
    private const val TAG = "InferenceMetrics"
    
    // Thread-safe collections for metrics
    private val tokenTimings = ConcurrentLinkedQueue<Long>() // nanoseconds per token
    private val memorySnapshots = ConcurrentLinkedQueue<Long>() // bytes
    private val sessionMetrics = ConcurrentLinkedQueue<SessionMetrics>()
    
    private val mutex = Mutex()
    private var sessionStartTime = 0L
    private var firstTokenTime = 0L
    private var totalTokensGenerated = 0
    
    // Current session tracking
    data class SessionMetrics(
        val sessionId: String,
        val modelPath: String,
        val quantization: String,
        val deviceModel: String,
        val androidVersion: Int,
        val totalTokens: Int,
        val avgTokensPerSecond: Double,
        val peakTokensPerSecond: Double,
        val timeToFirstTokenMs: Long,
        val avgMemoryMb: Double,
        val peakMemoryMb: Long,
        val avgPowerMw: Double,
        val sessionDurationMs: Long,
        val timestamp: Long
    )
    
    data class TokenEvent(
        val tokenIndex: Int,
        val latencyNs: Long,
        val memoryBytes: Long,
        val powerMw: Double,
        val timestamp: Long
    )
    
    /**
     * Start a new inference session
     * Call this before model initialization
     */
    fun startSession(modelPath: String, quantization: String = "Q4_K_M") {
        sessionStartTime = System.currentTimeMillis()
        firstTokenTime = 0L
        totalTokensGenerated = 0
        tokenTimings.clear()
        memorySnapshots.clear()
        
        Log.i(TAG, "Session started: model=$modelPath, quant=$quantization")
    }
    
    /**
     * Record first token received (for time-to-first-token metric)
     */
    fun recordFirstToken() {
        if (firstTokenTime == 0L) {
            firstTokenTime = System.currentTimeMillis()
            val ttft = firstTokenTime - sessionStartTime
            Log.i(TAG, "Time-to-first-token: ${ttft}ms")
        }
    }
    
    /**
     * Record each token generated with timing and memory
     * Call this from native callback for each token
     */
    fun recordToken(latencyNs: Long) {
        tokenTimings.offer(latencyNs)
        totalTokensGenerated++
        
        // Sample memory every 10 tokens to reduce overhead
        if (totalTokensGenerated % 10 == 0) {
            recordMemorySnapshot()
        }
    }
    
    /**
     * Record memory usage snapshot
     */
    fun recordMemorySnapshot() {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory() - runtime.freeMemory()
        memorySnapshots.offer(totalMemory)
    }
    
    /**
     * Get current tokens per second (rolling average)
     */
    fun getCurrentTokensPerSecond(): Double {
        if (tokenTimings.isEmpty()) return 0.0
        
        val recentTimings = tokenTimings.takeLast(20) // Last 20 tokens
        val avgLatencyNs = recentTimings.average()
        return if (avgLatencyNs > 0) 1_000_000_000.0 / avgLatencyNs else 0.0
    }
    
    /**
     * Get average tokens per second for entire session
     */
    fun getAverageTokensPerSecond(): Double {
        if (tokenTimings.isEmpty() || sessionStartTime == 0L) return 0.0
        
        val avgLatencyNs = tokenTimings.average()
        return if (avgLatencyNs > 0) 1_000_000_000.0 / avgLatencyNs else 0.0
    }
    
    /**
     * Get peak tokens per second
     */
    fun getPeakTokensPerSecond(): Double {
        if (tokenTimings.isEmpty()) return 0.0
        
        val minLatency = tokenTimings.minOrNull() ?: return 0.0
        return if (minLatency > 0) 1_000_000_000.0 / minLatency else 0.0
    }
    
    /**
     * Get time to first token in milliseconds
     */
    fun getTimeToFirstTokenMs(): Long {
        return if (firstTokenTime > 0) firstTokenTime - sessionStartTime else -1L
    }
    
    /**
     * Get average memory usage in MB
     */
    fun getAverageMemoryMb(): Double {
        if (memorySnapshots.isEmpty()) return 0.0
        return memorySnapshots.average() / (1024.0 * 1024.0)
    }
    
    /**
     * Get peak memory usage in MB
     */
    fun getPeakMemoryMb(): Long {
        if (memorySnapshots.isEmpty()) return 0L
        return (memorySnapshots.maxOrNull() ?: 0L) / (1024L * 1024L)
    }
    
    /**
     * Get total tokens generated in session
     */
    fun getTotalTokens(): Int = totalTokensGenerated
    
    /**
     * Estimate power consumption (requires Android 5.0+)
     * This is approximate - uses battery current if available
     */
    fun getEstimatedPowerMw(context: Context): Double {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val current = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val voltage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_VOLTAGE)
            if (current != 0 && voltage != 0) {
                (current.toDouble() * voltage.toDouble()) / 1000.0 // mW
            } else {
                1500.0 // Default estimate during inference
            }
        } else {
            1500.0
        }
    }
    
    /**
     * End session and save metrics
     */
    suspend fun endSession(context: Context, modelPath: String, quantization: String = "Q4_K_M") = 
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            mutex.withLock {
                val sessionDuration = System.currentTimeMillis() - sessionStartTime
                
                val metrics = SessionMetrics(
                    sessionId = UUID.randomUUID().toString(),
                    modelPath = modelPath.substringAfterLast('/'),
                    quantization = quantization,
                    deviceModel = Build.MODEL,
                    androidVersion = Build.VERSION.SDK_INT,
                    totalTokens = totalTokensGenerated,
                    avgTokensPerSecond = getAverageTokensPerSecond().roundToDecimals(2),
                    peakTokensPerSecond = getPeakTokensPerSecond().roundToDecimals(2),
                    timeToFirstTokenMs = getTimeToFirstTokenMs(),
                    avgMemoryMb = getAverageMemoryMb().roundToDecimals(2),
                    peakMemoryMb = getPeakMemoryMb(),
                    avgPowerMw = getEstimatedPowerMw(context).roundToDecimals(2),
                    sessionDurationMs = sessionDuration,
                    timestamp = System.currentTimeMillis()
                )
                
                sessionMetrics.offer(metrics)
                
                Log.i(TAG, "Session ended: ${metrics.totalTokens} tokens, " +
                        "${metrics.avgTokensPerSecond} tok/s avg, " +
                        "${metrics.timeToFirstTokenMs}ms TTFT, " +
                        "${metrics.avgMemoryMb}MB avg memory")
                
                // Auto-export after each session
                PerformanceLogger.exportSession(context, metrics)
            }
        }
    
    /**
     * Export all sessions to CSV for paper evaluation
     */
    suspend fun exportAllSessions(context: Context): File? {
        return PerformanceLogger.exportAllSessions(context, sessionMetrics.toList())
    }
    
    /**
     * Reset all metrics
     */
    fun reset() {
        tokenTimings.clear()
        memorySnapshots.clear()
        sessionMetrics.clear()
        sessionStartTime = 0L
        firstTokenTime = 0L
        totalTokensGenerated = 0
    }
    
    private fun Double.roundToDecimals(digits: Int): Double {
        val multiplier = Math.pow(10.0, digits.toDouble())
        return (this * multiplier).roundToInt() / multiplier
    }
}
