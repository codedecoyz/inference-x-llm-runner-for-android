package com.mobilellama.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilellama.R
import com.mobilellama.ui.components.ScrollingBinaryBackground
import com.mobilellama.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Data class for a terminal boot log line.
 */
private data class TerminalLine(
    val tag: String,
    val message: String,
    val isFinal: Boolean = false
)

/**
 * Neural Boot Splash Screen.
 * 
 * A premium startup experience featuring:
 * - Scrolling binary rain background
 * - Dot grid pattern overlay
 * - Pulsing logo with outer glow
 * - Sequential terminal boot messages
 * - Animated progress bar
 */
@Composable
fun SplashScreen(
    onAnimationFinished: () -> Unit
) {
    // ---- Animation State ----
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.3f) }
    val titleAlpha = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val statusBarAlpha = remember { Animatable(0f) }
    val progressWidth = remember { Animatable(0f) }
    
    // Terminal lines state — which lines are visible
    var visibleLines by remember { mutableIntStateOf(0) }

    // Pulse animation for logo glow
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    
    // Blinking cursor
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_blink"
    )

    // Terminal boot log lines
    val terminalLines = remember {
        listOf(
            TerminalLine("[SYST]", "Initializing NPU... OK"),
            TerminalLine("[MEM]", "Allocating VRAM (Local Path)... 8.2GB"),
            TerminalLine("[CORE]", "Mapping Neural Pathways... DONE"),
            TerminalLine("[WGT]", "Loading 8-bit Quantized Weights... 94%"),
            TerminalLine("[BOOT]", "Synthesizing Interface", isFinal = true)
        )
    }

    // ---- Animation Sequence ----
    LaunchedEffect(Unit) {
        // Status bar fades in
        statusBarAlpha.animateTo(0.4f, tween(400))
        
        // Logo entrance
        launch {
            logoAlpha.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        }
        logoScale.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
        
        // Title + subtitle
        titleAlpha.animateTo(1f, tween(600))
        delay(200)
        subtitleAlpha.animateTo(1f, tween(500))
        
        // Terminal lines appear sequentially
        delay(300)
        for (i in terminalLines.indices) {
            visibleLines = i + 1
            // Progress bar advances with each line
            launch {
                progressWidth.animateTo(
                    (i + 1).toFloat() / terminalLines.size,
                    tween(400, easing = FastOutSlowInEasing)
                )
            }
            delay(350)
        }
        
        // Hold and finish
        delay(1200)
        onAnimationFinished()
    }

    // ---- UI Layout ----
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlackPurple)
    ) {
        // Layer 1: Scrolling binary rain
        ScrollingBinaryBackground(
            modifier = Modifier.fillMaxSize(),
            alpha = 0.05f,
            cycleDurationMs = 15000
        )

        // Layer 2: Dot grid pattern
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.03f)) {
            val spacing = 24.dp.toPx()
            val dotRadius = 0.8.dp.toPx()
            val cols = (size.width / spacing).toInt() + 1
            val rows = (size.height / spacing).toInt() + 1
            for (row in 0..rows) {
                for (col in 0..cols) {
                    drawCircle(
                        color = VibrantPurple,
                        radius = dotRadius,
                        center = Offset(col * spacing, row * spacing)
                    )
                }
            }
        }

        // Layer 3: Radial vignette overlay
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.3f)) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, DeepBlackPurple),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = size.width * 0.8f
                )
            )
        }

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ---- Top Status Bar ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(statusBarAlpha.value),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INF-X // OS 1.0.4",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                    color = HighlightWhitePurple.copy(alpha = 0.6f)
                )
                Text(
                    text = "◉ Neural Link Active",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    color = HighlightWhitePurple.copy(alpha = 0.6f)
                )
            }

            // ---- Central Logo & Branding ----
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(180.dp)
                ) {
                    // Outer glow ring
                    Canvas(
                        modifier = Modifier
                            .size(180.dp)
                            .scale(pulseScale)
                    ) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    VibrantPurple.copy(alpha = glowAlpha * 0.4f),
                                    VibrantPurple.copy(alpha = glowAlpha * 0.15f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.minDimension / 2
                        )
                    }

                    // Logo image
                    Image(
                        painter = painterResource(id = R.drawable.infx_logo),
                        contentDescription = "InferenceX Logo",
                        modifier = Modifier
                            .size(100.dp)
                            .scale(logoScale.value * pulseScale)
                            .alpha(logoAlpha.value)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Brand name
                Text(
                    text = "INFERENCEX",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp,
                    color = Color.White.copy(alpha = 0.9f * titleAlpha.value)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = "NEURAL PROCESSING UNIT",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 5.sp,
                    color = VibrantPurple.copy(alpha = subtitleAlpha.value)
                )
            }

            // ---- Terminal Output Section ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                terminalLines.forEachIndexed { index, line ->
                    if (index < visibleLines) {
                        val lineAlpha = when {
                            // Older lines are dimmer
                            visibleLines - index > 4 -> 0.25f
                            visibleLines - index > 3 -> 0.35f
                            visibleLines - index > 2 -> 0.5f
                            visibleLines - index > 1 -> 0.7f
                            else -> 1f // Latest line is brightest
                        }

                        Row(
                            modifier = Modifier.alpha(lineAlpha),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Tag
                            Text(
                                text = line.tag,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = VibrantPurple
                            )
                            // Message
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = line.message,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (line.isFinal) GlowAccent else TerminalAccent.copy(alpha = 0.8f),
                                    fontWeight = if (line.isFinal) FontWeight.Medium else FontWeight.Normal
                                )
                                // Blinking cursor on final line
                                if (line.isFinal) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(14.dp)
                                            .alpha(cursorAlpha)
                                            .background(GlowAccent)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- Bottom Progress Bar ----
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .background(VibrantPurple.copy(alpha = 0.1f))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progressWidth.value)
            ) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            VibrantPurple,
                            GlowAccent,
                            Color.White.copy(alpha = 0.9f)
                        )
                    ),
                    cornerRadius = CornerRadius(2f, 2f),
                    size = Size(size.width, size.height)
                )
            }
        }
    }
}
