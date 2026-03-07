package com.mobilellama.ui.screens

import androidx.compose.animation.core.*
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

    // Pulse Animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Animation Sequence
    LaunchedEffect(Unit) {
        // 1. Dot Fade In
        dotAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
        
        // 2. Expand into X / Scale Up
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
        delay(1500) // Reduced hold time slightly or kept appropriate
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
                .size(120.dp)
                .scale(lineProgress.value * pulseScale) // Combine entrance scale with pulse
                .alpha(dotAlpha.value)
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
