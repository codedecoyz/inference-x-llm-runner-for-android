package com.mobilellama.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mobilellama.ui.theme.DeepBlackPurple
import com.mobilellama.ui.theme.LightLavender
import com.mobilellama.ui.theme.HighlightWhitePurple
import com.mobilellama.ui.theme.VibrantPurple

@Composable
fun InputBar(
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit,
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Custom TextField
        // #10002b container (DeepBlackPurple), #7b2cbf outline (VibrantPurple)
        OutlinedTextField(
            value = messageText,
            onValueChange = { messageText = it },
            placeholder = { Text("Ask anything...", color = HighlightWhitePurple.copy(alpha = 0.5f)) },
            enabled = !isGenerating,
            modifier = Modifier
                .weight(1f)
                .background(DeepBlackPurple, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DeepBlackPurple,
                unfocusedContainerColor = DeepBlackPurple,
                disabledContainerColor = DeepBlackPurple,
                focusedBorderColor = VibrantPurple,
                unfocusedBorderColor = VibrantPurple.copy(alpha = 0.5f),
                cursorColor = LightLavender,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true
        )

        // Circular Send Button
        if (isGenerating) {
            IconButton(
                onClick = onStopGeneration,
                modifier = Modifier
                    .size(50.dp)
                    .background(Color(0xFF2C0B0E), CircleShape) // Dark red background
                    .border(1.dp, Color.Red, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop generation",
                    tint = Color.Red
                )
            }
        } else {
            IconButton(
                onClick = {
                    val text = messageText.trim()
                    if (text.isNotEmpty()) {
                        onSendMessage(text)
                        messageText = ""
                    }
                },
                enabled = messageText.trim().isNotEmpty(),
                modifier = Modifier
                    .size(50.dp)
                    .background(VibrantPurple, CircleShape) // Filled #7b2cbf
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send message",
                    tint = Color.White
                )
            }
        }
    }
}
