package com.mobilellama.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilellama.ui.theme.*
import com.mobilellama.viewmodel.VisionCameraViewModel
import com.mobilellama.viewmodel.VisionCameraState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionCameraScreen(
    onOpenDrawer: () -> Unit,
    viewModel: VisionCameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraState by viewModel.cameraState.collectAsState()
    val capturedImage by viewModel.capturedImage.collectAsState()
    val promptText by viewModel.promptText.collectAsState()

    var hasCameraPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    // Check permission on launch
    LaunchedEffect(Unit) {
        val result = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (result == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                @Suppress("DEPRECATION")
                val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                viewModel.onImageCaptured(bitmap)
            } catch (e: Exception) {
                Log.e("VisionCamera", "Failed to load gallery image", e)
            }
        }
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Unbind camera when image is captured (prevents BufferQueue errors)
    LaunchedEffect(capturedImage) {
        if (capturedImage != null) {
            cameraProviderRef?.unbindAll()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProviderRef?.unbindAll()
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlackPurple)
    ) {
        if (!hasCameraPermission) {
            // Permission denied state
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Camera permission is required\nfor Vision Engine",
                    color = HighlightWhitePurple,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = VibrantPurple)
                ) {
                    Text("Grant Permission")
                }
            }
        } else {
            // ── Camera Viewfinder ──
            if (capturedImage == null) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            cameraProviderRef = cameraProvider
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                            } catch (e: Exception) {
                                Log.e("VisionCamera", "Camera bind failed", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // ── Frozen captured image ──
                Image(
                    bitmap = capturedImage!!.asImageBitmap(),
                    contentDescription = "Captured Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // ── Gradient overlays for readability ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                DeepBlackPurple.copy(alpha = 0.7f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                DeepBlackPurple.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // ── Top bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, "Menu", tint = HighlightWhitePurple)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Vision Engine",
                    color = HighlightWhitePurple,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                // Clear button to reset
                if (capturedImage != null) {
                    IconButton(onClick = { viewModel.clearCapture() }) {
                        Icon(Icons.Default.Refresh, "Reset", tint = HighlightWhitePurple)
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            // ── Bottom controls ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Floating Answer Sheet ──
                AnimatedVisibility(
                    visible = cameraState !is VisionCameraState.Idle,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = DarkTonalPurple.copy(alpha = 0.92f)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .heightIn(max = 250.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            when (val state = cameraState) {
                                is VisionCameraState.Analyzing -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = LightLavender,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            "Analyzing image...",
                                            color = HighlightWhitePurple,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                is VisionCameraState.Streaming -> {
                                    Text(
                                        state.partialResult,
                                        color = HighlightWhitePurple,
                                        style = MaterialTheme.typography.bodyMedium,
                                        lineHeight = 22.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "${state.elapsedMs}ms",
                                        color = LightLavender.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                is VisionCameraState.Error -> {
                                    Text(
                                        "⚠ ${state.message}",
                                        color = Color(0xFFFF6B6B),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }

                // ── Prompt text field ──
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { viewModel.setPromptText(it) },
                    placeholder = { Text("Ask about what you see...", color = HighlightWhitePurple.copy(alpha = 0.5f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LightLavender,
                        unfocusedBorderColor = HighlightWhitePurple.copy(alpha = 0.3f),
                        focusedTextColor = HighlightWhitePurple,
                        unfocusedTextColor = HighlightWhitePurple,
                        cursorColor = LightLavender
                    ),
                    shape = RoundedCornerShape(28.dp),
                    maxLines = 2,
                    singleLine = false
                )

                // ── Shutter / Gallery / Analyze Button ──
                if (capturedImage == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gallery button
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .border(2.dp, LightLavender.copy(alpha = 0.6f), CircleShape)
                                .background(DarkTonalPurple.copy(alpha = 0.5f))
                                .clickable {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "🖼",
                                fontSize = 22.sp
                            )
                        }

                        // Shutter button
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .border(3.dp, LightLavender, CircleShape)
                                .clickable {
                                    imageCapture.takePicture(
                                        cameraExecutor,
                                        object : ImageCapture.OnImageCapturedCallback() {
                                            override fun onCaptureSuccess(image: ImageProxy) {
                                                val bmp = image.toBitmap()
                                                image.close()
                                                viewModel.onImageCaptured(bmp)
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                Log.e(
                                                    "VisionCamera",
                                                    "Capture failed",
                                                    exception
                                                )
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(HighlightWhitePurple)
                            )
                        }

                        // Spacer to balance the row
                        Spacer(modifier = Modifier.size(52.dp))
                    }
                } else {
                    // Analyze button (after capture)
                    Button(
                        onClick = { viewModel.analyzeImage() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VibrantPurple),
                        enabled = cameraState !is VisionCameraState.Analyzing
                    ) {
                        Text(
                            if (cameraState is VisionCameraState.Streaming || cameraState is VisionCameraState.Error) "Ask Again" else "Ask Vision Model",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
