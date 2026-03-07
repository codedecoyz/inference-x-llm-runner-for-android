package com.mobilellama

import android.app.Application
import android.util.Log
import com.mobilellama.util.AssetHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class MobileLlamaApplication : Application() {
    
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        
        // Copy ONNX models from the APK's assets folder to internal storage
        // so the C++ engine can load them via standard file paths.
        GlobalScope.launch(Dispatchers.IO) {
            val copied = AssetHelper.copyAssetsToFilesDir(
                context = this@MobileLlamaApplication,
                assetFolderName = "hrm",
                destFolderName = "hrm"
            )
            if (copied) {
                Log.i("MobileLlamaApp", "✅ ONNX models copied to filesDir/hrm")
            }
        }
    }
}

