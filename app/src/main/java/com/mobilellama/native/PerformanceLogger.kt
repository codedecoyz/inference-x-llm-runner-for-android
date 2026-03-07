package com.mobilellama.native

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Exports performance metrics to CSV files for paper evaluation
 */
object PerformanceLogger {
    private const val TAG = "PerformanceLogger"
    private const val METRICS_DIR = "inference_metrics"
    
    /**
     * Export single session to CSV
     */
    suspend fun exportSession(context: Context, metrics: InferenceMetrics.SessionMetrics) = 
        withContext(Dispatchers.IO) {
            try {
                val metricsDir = File(context.filesDir, METRICS_DIR)
                if (!metricsDir.exists()) metricsDir.mkdirs()
                
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(Date(metrics.timestamp))
                val fileName = "session_${timestamp}_${metrics.sessionId.take(8)}.csv"
                val file = File(metricsDir, fileName)
                
                file.bufferedWriter().use { writer ->
                    // Header
                    writer.write("session_id,model_path,quantization,device_model,android_version,")
                    writer.write("total_tokens,avg_tok_s,peak_tok_s,ttft_ms,avg_memory_mb,")
                    writer.write("peak_memory_mb,avg_power_mw,session_duration_ms,timestamp\n")
                    
                    // Data row
                    writer.write("${metrics.sessionId},")
                    writer.write("${metrics.modelPath},")
                    writer.write("${metrics.quantization},")
                    writer.write("${metrics.deviceModel},")
                    writer.write("${metrics.androidVersion},")
                    writer.write("${metrics.totalTokens},")
                    writer.write("${metrics.avgTokensPerSecond},")
                    writer.write("${metrics.peakTokensPerSecond},")
                    writer.write("${metrics.timeToFirstTokenMs},")
                    writer.write("${metrics.avgMemoryMb},")
                    writer.write("${metrics.peakMemoryMb},")
                    writer.write("${metrics.avgPowerMw},")
                    writer.write("${metrics.sessionDurationMs},")
                    writer.write("${metrics.timestamp}\n")
                }
                
                Log.i(TAG, "Session exported to: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export session", e)
            }
        }
    
    /**
     * Export all sessions to summary CSV (for paper tables)
     */
    suspend fun exportAllSessions(
        context: Context, 
        sessions: List<InferenceMetrics.SessionMetrics>
    ): File? = withContext(Dispatchers.IO) {
        try {
            val metricsDir = File(context.filesDir, METRICS_DIR)
            if (!metricsDir.exists()) metricsDir.mkdirs()
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "summary_$timestamp.csv"
            val file = File(metricsDir, fileName)
            
            file.bufferedWriter().use { writer ->
                // Header
                writer.write("session_id,model_path,quantization,device_model,android_version,")
                writer.write("total_tokens,avg_tok_s,peak_tok_s,ttft_ms,avg_memory_mb,")
                writer.write("peak_memory_mb,avg_power_mw,session_duration_ms,timestamp\n")
                
                // All sessions
                sessions.forEach { metrics ->
                    writer.write("${metrics.sessionId},")
                    writer.write("${metrics.modelPath},")
                    writer.write("${metrics.quantization},")
                    writer.write("${metrics.deviceModel},")
                    writer.write("${metrics.androidVersion},")
                    writer.write("${metrics.totalTokens},")
                    writer.write("${metrics.avgTokensPerSecond},")
                    writer.write("${metrics.peakTokensPerSecond},")
                    writer.write("${metrics.timeToFirstTokenMs},")
                    writer.write("${metrics.avgMemoryMb},")
                    writer.write("${metrics.peakMemoryMb},")
                    writer.write("${metrics.avgPowerMw},")
                    writer.write("${metrics.sessionDurationMs},")
                    writer.write("${metrics.timestamp}\n")
                }
            }
            
            Log.i(TAG, "Summary exported to: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export summary", e)
            null
        }
    }
    
    /**
     * Get all exported metric files
     */
    fun getMetricFiles(context: Context): List<File> {
        val metricsDir = File(context.filesDir, METRICS_DIR)
        return if (metricsDir.exists()) {
            metricsDir.listFiles { file -> file.extension == "csv" }?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    /**
     * Generate paper-ready markdown table from sessions
     */
    fun generateMarkdownTable(sessions: List<InferenceMetrics.SessionMetrics>): String {
        val grouped = sessions.groupBy { it.modelPath }
        
        val sb = StringBuilder()
        sb.append("| Model | Quantization | Device | Avg Tok/s | Peak Tok/s | TTFT (ms) | Memory (MB) | Power (mW) |\n")
        sb.append("|-------|--------------|--------|-----------|------------|-----------|-------------|------------|\n")
        
        grouped.forEach { (model, modelSessions) ->
            val avg = modelSessions.first() // Use first session as representative
            sb.append("| ${avg.modelPath} | ${avg.quantization} | ${avg.deviceModel} | ")
            sb.append("${avg.avgTokensPerSecond} | ${avg.peakTokensPerSecond} | ")
            sb.append("${avg.timeToFirstTokenMs} | ${avg.avgMemoryMb} | ${avg.avgPowerMw} |\n")
        }
        
        return sb.toString()
    }
}
