package com.mobilellama

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mobilellama.data.repository.ModelRepository
import com.mobilellama.ui.screens.ChatScreen
import com.mobilellama.ui.screens.OnboardingScreen
import com.mobilellama.ui.screens.SplashScreen
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

    // Start with Splash Screen
    val startDestination = "splash"
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            com.mobilellama.ui.components.Sidebar(
                onModelSelected = { 
                    scope.launch { drawerState.close() }
                    // ChatViewModel will react to repo change hopefully, or we might need to force reload
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
                            modelDownloaded -> "chat"
                            else -> "manager" // Go to manager instead of old download screen
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
                        val next = if (modelRepository.isModelDownloaded()) "chat" else "manager"
                        navController.navigate(next) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }
    
            composable("download") {
                // Keep for backward compat or redirect to manager
                com.mobilellama.ui.screens.ModelManagerScreen(
                    onBack = { 
                        if (navController.previousBackStackEntry != null) navController.popBackStack()
                        else navController.navigate("chat") 
                    }
                )
            }
            
            composable("manager") {
                com.mobilellama.ui.screens.ModelManagerScreen(
                    onBack = { 
                        if (navController.previousBackStackEntry != null) navController.popBackStack() 
                        else navController.navigate("chat")
                    }
                )
            }
    
            composable("chat") {
                ChatScreen(
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    }
                )
            }
        }
    }
}
