package com.mobilellama.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility to copy ONNX model files from the APK assets folder
 * into the app's internal filesDir where the C++ engine can load them.
 */
object AssetHelper {

    private const val TAG = "AssetHelper"

    /**
     * Copies all ONNX files from the given asset folder to the destination directory.
     * Only copies if the file doesn't already exist or if we force overwrite.
     */
    suspend fun copyAssetsToFilesDir(
        context: Context,
        assetFolderName: String,
        destFolderName: String,
        forceOverwrite: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val assetManager = context.assets
            val files = assetManager.list(assetFolderName) ?: return@withContext false
            
            val destDir = File(context.filesDir, destFolderName)
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            var copiedAny = false
            for (filename in files) {
                // For HRM/LLM, we only care about .onnx files and maybe config.json
                if (!filename.endsWith(".onnx") && !filename.endsWith(".json")) continue

                val assetPath = "\$assetFolderName/\$filename"
                val outFile = File(destDir, filename)

                if (outFile.exists() && !forceOverwrite) {
                    continue // Skip already copied files
                }

                Log.i(TAG, "Copying asset \$assetPath to \${outFile.absolutePath}")
                
                var inStream: InputStream? = null
                var outStream: FileOutputStream? = null
                try {
                    inStream = assetManager.open(assetPath)
                    outStream = FileOutputStream(outFile)
                    
                    val buffer = ByteArray(1024 * 1024) // 1MB buffer
                    var read: Int
                    while (inStream.read(buffer).also { read = it } != -1) {
                        outStream.write(buffer, 0, read)
                    }
                    outStream.flush()
                    copiedAny = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy \$assetPath", e)
                } finally {
                    inStream?.close()
                    outStream?.close()
                }
            }
            return@withContext copiedAny
        } catch (e: Exception) {
            Log.e(TAG, "Error copying assets from \$assetFolderName", e)
            return@withContext false
        }
    }
}
