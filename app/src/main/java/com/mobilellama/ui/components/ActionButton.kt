package com.mobilellama.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


@Composable
fun InferenceXActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF5a189a),
            Color(0xFF9d4edd)
        )
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color(0xFF7b2cbf),
                ambientColor = Color(0xFF7b2cbf)
            )
            .background(
                brush = gradient,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Color.White.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(0.dp),
            enabled = enabled
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
