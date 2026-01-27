package com.mobilellama.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilellama.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onAnimationFinished: () -> Unit
) {
    val dotAlpha = remember { Animatable(0f) }
    val lineProgress = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    // Animation Sequence
    LaunchedEffect(Unit) {
        // 1. Dot Fade In
        dotAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
        
        // 2. Expand into X
        lineProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
        
        // 3. Text Fade In
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600)
        )
        
        // 4. Hold & Finish
        delay(500)
        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Logo Image
        Image(
            painter = painterResource(id = R.drawable.infx_logo),
            contentDescription = "InferenceX Logo",
            modifier = Modifier
                .size(120.dp) // Start small, scale up? Or fixed size? Let's scale.
                .scale(lineProgress.value) // Use the 0->1 progress for scale
                .alpha(dotAlpha.value) // Fade in
        )
        
        // Brand Text
        Text(
            text = "INF-X",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = textAlpha.value),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 100.dp) // Below the logo
        )
    }
}
