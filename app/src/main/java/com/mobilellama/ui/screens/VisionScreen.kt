package com.mobilellama.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilellama.ui.components.InferenceXActionButton
import com.mobilellama.ui.theme.*
import com.mobilellama.viewmodel.VisionState
import com.mobilellama.viewmodel.VisionViewModel
import androidx.compose.ui.graphics.Brush

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionScreen(
    onOpenDrawer: () -> Unit,
    viewModel: VisionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val visionState by viewModel.visionState.collectAsState()
    val selectedImage by viewModel.selectedImage.collectAsState()
    val promptText by viewModel.promptText.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setImageUri(it)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(DeepBlackPurple, DarkTonalPurple)
                )
            )
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { 
                        Text(
                            "Vision Engine", 
                            color = HighlightWhitePurple, 
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                            letterSpacing = 1.sp
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, "Menu", tint = HighlightWhitePurple)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Image Preview / Placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable { launcher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (selectedImage != null) {
                    Image(
                        bitmap = selectedImage!!.asImageBitmap(),
                        contentDescription = "Selected Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(64.dp), tint = HighlightWhitePurple)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Tap to select image", color = HighlightWhitePurple)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = promptText,
                onValueChange = { viewModel.setPromptText(it) },
                label = { Text("Ask about this image...", color = HighlightWhitePurple.copy(alpha=0.7f)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LightLavender,
                    unfocusedBorderColor = HighlightWhitePurple.copy(alpha=0.5f),
                    focusedTextColor = HighlightWhitePurple,
                    unfocusedTextColor = HighlightWhitePurple
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Status Display
            when (val state = visionState) {
                is VisionState.Idle -> {
                    InferenceXActionButton(
                        text = "Ask Vision Model",
                        onClick = { viewModel.analyzeImage() },
                        enabled = selectedImage != null,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    )
                }
                is VisionState.Analyzing -> {
                    CircularProgressIndicator(color = LightLavender)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Running VLM Inference...", color = HighlightWhitePurple)
                }
                is VisionState.Success -> {
                    InferenceXActionButton(
                        text = "Ask Another Question",
                        onClick = { viewModel.analyzeImage() },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LightLavender.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Response", style = MaterialTheme.typography.titleMedium, color = LightLavender, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(state.resultText, color = HighlightWhitePurple)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Inference Time: ${state.inferenceTimeMs}ms", color = HighlightWhitePurple.copy(alpha=0.7f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                is VisionState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    InferenceXActionButton(
                        text = "Retry",
                        onClick = { viewModel.analyzeImage() },
                        enabled = selectedImage != null,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    )
                }
            }
        }
    }
}
}
