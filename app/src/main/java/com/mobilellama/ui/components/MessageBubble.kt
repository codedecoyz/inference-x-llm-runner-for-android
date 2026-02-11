package com.mobilellama.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilellama.data.model.Message
import com.mobilellama.ui.theme.LightLavender
import com.mobilellama.ui.theme.VibrantPurple
import com.mobilellama.ui.theme.HighlightWhitePurple
import com.mobilellama.ui.theme.SurfaceDark
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(message: Message) {
    val isUser = message.role == "user"
    
    // Styling
    val shape = if (isUser) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    val backgroundModifier = if (isUser) {
        Modifier.background(
            brush = Brush.linearGradient(
                colors = listOf(VibrantPurple, LightLavender) // Gradient for User: Vibrant -> Lavender
            ),
            shape = shape
        )
    } else {
        Modifier
            .background(SurfaceDark, shape) // Tonal for AI
            .border(BorderStroke(1.dp, VibrantPurple), shape) // Border for AI
    }

    val alignment = if (isUser) Alignment.End else Alignment.Start

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timeString = timeFormat.format(Date(message.timestamp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .then(backgroundModifier)
                .padding(16.dp)
        ) {
            Text(
                text = message.content,
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 22.sp
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = timeString,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.6f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
