package com.mobilellama.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.mobilellama.ui.theme.BinaryRainColor
import kotlin.random.Random

/**
 * Data class representing a single column of binary rain.
 */
private data class BinaryColumn(
    val chars: List<String>,
    val xFraction: Float,   // Horizontal position as fraction of width (0..1)
    val speed: Float,        // Scroll speed multiplier
    val baseAlpha: Float     // Base alpha for this column
)

/**
 * A performant, Canvas-based scrolling binary/hex rain background.
 *
 * Renders multiple columns of randomly generated binary digits and hex characters
 * that scroll vertically at varying speeds. Top and bottom edges fade via gradient
 * overlay. Designed for use as a background layer on splash/onboarding screens.
 *
 * @param modifier Modifier for the composable
 * @param columnCount Number of vertical columns
 * @param alpha Overall opacity multiplier (0f..1f). Default 0.07f for subtle effect.
 * @param color The color used for binary text
 * @param cycleDurationMs Duration of one full vertical scroll cycle in milliseconds
 */
@Composable
fun ScrollingBinaryBackground(
    modifier: Modifier = Modifier,
    columnCount: Int = 14,
    alpha: Float = 0.07f,
    color: Color = BinaryRainColor,
    cycleDurationMs: Int = 12000
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Generate stable random columns
    val columns = remember {
        val rng = Random(42) // Fixed seed for consistent layout
        List(columnCount) { i ->
            val chars = List(40) { // Enough chars to cover tall screens
                if (rng.nextBoolean()) {
                    rng.nextInt(0, 2).toString() // Binary: "0" or "1"
                } else {
                    Integer.toHexString(rng.nextInt(0, 16)).uppercase() // Hex: 0-F
                }
            }
            BinaryColumn(
                chars = chars,
                xFraction = (i.toFloat() + 0.5f) / columnCount,
                speed = 0.7f + rng.nextFloat() * 0.6f, // 0.7x to 1.3x speed
                baseAlpha = 0.4f + rng.nextFloat() * 0.6f  // 0.4 to 1.0 base
            )
        }
    }

    // Single infinite scroll animation (0f → 1f)
    val infiniteTransition = rememberInfiniteTransition(label = "binary_scroll")
    val scrollProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = cycleDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scroll_progress"
    )

    val charStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        color = color.copy(alpha = alpha)
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Binary rain canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val charHeightPx = with(density) { 18.sp.toPx() }
            val totalCharsPerColumn = 40
            val totalColumnHeight = charHeightPx * totalCharsPerColumn

            columns.forEach { column ->
                val x = column.xFraction * canvasWidth
                // Offset scrolls downward; wraps using modulo
                val scrollOffset = (scrollProgress * column.speed * totalColumnHeight) % totalColumnHeight

                column.chars.forEachIndexed { idx, char ->
                    val baseY = idx * charHeightPx
                    val y = (baseY + scrollOffset) % totalColumnHeight - charHeightPx

                    // Only draw if visible
                    if (y > -charHeightPx && y < canvasHeight + charHeightPx) {
                        // Fade at edges (top and bottom 20%)
                        val fadeZone = canvasHeight * 0.2f
                        val edgeFade = when {
                            y < fadeZone -> (y / fadeZone).coerceIn(0f, 1f)
                            y > canvasHeight - fadeZone -> ((canvasHeight - y) / fadeZone).coerceIn(0f, 1f)
                            else -> 1f
                        }

                        val charAlpha = alpha * column.baseAlpha * edgeFade

                        if (charAlpha > 0.005f) {
                            val styledText = charStyle.copy(
                                color = color.copy(alpha = charAlpha)
                            )
                            val measuredText = textMeasurer.measure(char, styledText)
                            drawText(
                                textLayoutResult = measuredText,
                                topLeft = Offset(x - measuredText.size.width / 2f, y)
                            )
                        }
                    }
                }
            }
        }

        // Top gradient fade overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF10002b),           // Solid at top
                        Color(0xFF10002b).copy(alpha = 0.6f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = size.height * 0.15f
                )
            )
            // Bottom gradient fade overlay
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF10002b).copy(alpha = 0.6f),
                        Color(0xFF10002b)            // Solid at bottom
                    ),
                    startY = size.height * 0.85f,
                    endY = size.height
                )
            )
        }
    }
}
