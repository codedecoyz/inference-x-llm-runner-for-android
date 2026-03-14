package com.mobilellama

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mobilellama.data.repository.ModelRepository
import com.mobilellama.ui.screens.ChatListScreen
import com.mobilellama.ui.screens.ChatScreen
import com.mobilellama.ui.screens.OnboardingScreen
import com.mobilellama.ui.screens.SplashScreen
import com.mobilellama.ui.screens.VisionCameraScreen
import com.mobilellama.ui.theme.MobileLlamaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            com.mobilellama.ui.components.Sidebar(
                onModelSelected = { model ->
                    scope.launch { drawerState.close() }
                    // Vision models go to Vision Camera, text models to Chat List
                    if (model.promptType == com.mobilellama.data.model.PromptType.VISION) {
                        navController.navigate("vision") {
                            popUpTo("chatList") { inclusive = true }
                        }
                    } else {
                        navController.navigate("chatList") {
                            popUpTo("chatList") { inclusive = true }
                        }
                    }
                },
                onManageModels = {
                    scope.launch { drawerState.close() }
                    navController.navigate("manager")
                }
            )
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = "splash"
        ) {
            composable("splash") {
                SplashScreen(
                    onAnimationFinished = {
                        val onboardingComplete = prefs.getBoolean("onboarding_complete", false)
                        val modelDownloaded = modelRepository.isModelDownloaded()

                        val nextScreen = when {
                            !onboardingComplete -> "onboarding"
                            modelDownloaded -> "chatList"
                            else -> "manager"
                        }

                        navController.navigate(nextScreen) {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                )
            }

            composable("onboarding") {
                OnboardingScreen(
                    onOnboardingComplete = {
                        prefs.edit().putBoolean("onboarding_complete", true).apply()
                        val next = if (modelRepository.isModelDownloaded()) "chatList" else "manager"
                        navController.navigate(next) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }

            composable("download") {
                com.mobilellama.ui.screens.ModelManagerScreen(
                    onBack = {
                        if (navController.previousBackStackEntry != null) navController.popBackStack()
                        else navController.navigate("chatList")
                    }
                )
            }

            composable("manager") {
                com.mobilellama.ui.screens.ModelManagerScreen(
                    onBack = {
                        if (navController.previousBackStackEntry != null) navController.popBackStack()
                        else navController.navigate("chatList")
                    }
                )
            }

            // Vision Camera Screen
            composable("vision") {
                VisionCameraScreen(
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    }
                )
            }

            // Chat List — new entry point for conversations
            composable("chatList") {
                ChatListScreen(
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    },
                    onChatSelected = { chatId ->
                        navController.navigate("chat/$chatId")
                    }
                )
            }

            // Chat Screen — per-conversation
            composable(
                route = "chat/{chatId}",
                arguments = listOf(navArgument("chatId") { type = NavType.StringType })
            ) {
                ChatScreen(
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
