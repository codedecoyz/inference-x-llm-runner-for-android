package com.mobilellama

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mobilellama.data.repository.ModelRepository
import com.mobilellama.ui.screens.ChatScreen
import com.mobilellama.ui.screens.DownloadScreen
import com.mobilellama.ui.theme.MobileLlamaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var modelRepository: ModelRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MobileLlamaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MobileLlamaApp(modelRepository)
                }
            }
        }
    }
}

@Composable
fun MobileLlamaApp(modelRepository: ModelRepository) {
    val navController = rememberNavController()

    // Determine initial screen based on whether model is downloaded
    val startDestination = if (modelRepository.isModelDownloaded()) {
        "chat"
    } else {
        "download"
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("download") {
            DownloadScreen(
                onDownloadComplete = {
                    navController.navigate("chat") {
                        popUpTo("download") { inclusive = true }
                    }
                }
            )
        }

        composable("chat") {
            ChatScreen()
        }
    }
}
