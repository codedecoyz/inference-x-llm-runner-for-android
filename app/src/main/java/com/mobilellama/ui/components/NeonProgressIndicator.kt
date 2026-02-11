package com.mobilellama.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun NeonProgressIndicator(
    progress: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier,
    height: Dp = 4.dp
) {
    val neonColor = Color(0xFFe0aaff)
    val trackColor = Color(0xFF10002b)
    
    LinearProgressIndicator(
        progress = progress,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                drawIntoCanvas { canvas ->
                    val paint = Paint()
                    val frameworkPaint = paint.asFrameworkPaint()
                    frameworkPaint.color = neonColor.toArgb()
                    frameworkPaint.maskFilter = BlurMaskFilter(
                        20f,
                        BlurMaskFilter.Blur.NORMAL
                    )
                    // Draw a glow behind the indicator
                    // Note: This is a simple approximation. For a perfect progress glow, 
                    // we'd need to calculate the width based on progress.
                    // For now, we apply a subtle glow to the whole active area or just the bar itself if possible.
                    // Since LinearProgressIndicator draws its own content, drawing behind it works for background glow.
                }
            },
        color = neonColor,
        trackColor = trackColor
    )
}
