package com.startups.airis.ui

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.startups.airis.bluetooth.ConnectionState
import com.startups.airis.ml.ObjectDetectorHelper
import java.util.concurrent.Executors

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val sensorData by viewModel.sensorData.collectAsState()
    val latestObject by viewModel.latestDetectedObject.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    if (!hasPermissions) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Permissions required (Camera, Bluetooth)")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequestPermissions) {
                Text("Grant Permissions")
            }
        }
        return
    }


    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.Black.copy(alpha = 0.8f)) {
                NavigationBarItem(
                    selected = currentMode == DetectionMode.INDOOR,
                    onClick = { viewModel.setMode(DetectionMode.INDOOR) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Deteksi Objek", tint = if (currentMode == DetectionMode.INDOOR) Color.White else Color.Gray) },
                    label = { Text("Objek", color = if (currentMode == DetectionMode.INDOOR) Color.White else Color.Gray) }
                )
                NavigationBarItem(
                    selected = currentMode == DetectionMode.FAST,
                    onClick = { viewModel.setMode(DetectionMode.FAST) },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = "Deteksi Cepat", tint = if (currentMode == DetectionMode.FAST) Color.White else Color.Gray) },
                    label = { Text("Cepat", color = if (currentMode == DetectionMode.FAST) Color.White else Color.Gray) }
                )
                NavigationBarItem(
                    selected = currentMode == DetectionMode.OUTDOOR,
                    onClick = { viewModel.setMode(DetectionMode.OUTDOOR) },
                    icon = { Icon(Icons.Filled.Place, contentDescription = "Mode OCR", tint = if (currentMode == DetectionMode.OUTDOOR) Color.White else Color.Gray) },
                    label = { Text("OCR", color = if (currentMode == DetectionMode.OUTDOOR) Color.White else Color.Gray) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { viewModel.cycleMode() },
                        onLongPress = { viewModel.connectBluetooth() }
                    )
                }
        ) {
        // Camera Preview Background
        key(lensFacing) {
            CameraPreview(
                lensFacing = lensFacing,
                onObjectsDetected = { objects ->
                    viewModel.onObjectsDetected(objects)
                },
                onTextDetected = { text ->
                    viewModel.onTextDetected(text)
                }
            )
        }

        // OCR Target Reticle (Only in OCR mode)
        if (currentMode == DetectionMode.OUTDOOR) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .border(width = 2.dp, color = Color(0xFF0A84FF).copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                )
            }
        }

        // Overlay UI (Apple-Style Minimalist)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Bluetooth Status (Pill shaped) + Flip Camera
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                if (connectionState != ConnectionState.CONNECTED) viewModel.connectBluetooth()
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statusColor = when (connectionState) {
                            ConnectionState.CONNECTED -> Color(0xFF34C759) // Apple Green
                            ConnectionState.ERROR -> Color(0xFFFF3B30) // Apple Red
                            else -> Color(0xFFFF9500) // Apple Orange
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(statusColor, androidx.compose.foundation.shape.CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (connectionState == ConnectionState.CONNECTED) "Connected to Hat" else "Tap to Connect",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    }
                }
                
                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Flip Camera",
                        tint = Color.White
                    )
                }
            }

            // Bottom Panel: Info Card
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                color = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Object detected text
                    Text(
                        text = if (latestObject?.isPublicFacility == true) "FASILITAS PUBLIK:\n${latestObject?.name?.uppercase()}" else latestObject?.name?.uppercase() ?: "DETECTED...",
                        style = if (latestObject?.isPublicFacility == true) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = if (latestObject?.isPublicFacility == true) Color(0xFFFF9500) else if (latestObject != null) Color(0xFF0A84FF) else Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "POSISI: ${latestObject?.position?.name ?: "-"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.LightGray,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp)
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Distances
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DistanceItem(label = "KIRI", value = sensorData?.kiriM)
                        DistanceItem(label = "DEPAN", value = sensorData?.depanM, isCenter = true)
                        DistanceItem(label = "KANAN", value = sensorData?.kananM)
                    }
                }
            }
        }
    }
}
}

@Composable
fun DistanceItem(label: String, value: Double?, isCenter: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (value != null && value < 900.0) "${value}m" else "--",
            style = if (isCenter) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = if (value != null && value <= 1.0) Color(0xFFFF3B30) else Color.White
        )
    }
}

@Composable
fun CameraPreview(
    lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    onObjectsDetected: (List<com.startups.airis.ml.DetectedObjectResult>) -> Unit,
    onTextDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Use remember to ensure we only instantiate the executor once
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                this.scaleType = PreviewView.ScaleType.FILL_CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // Preview Use Case
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // ImageAnalysis Use Case
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            cameraExecutor,
                            com.startups.airis.ml.VisionAnalyzer(
                                ctx,
                                onObjectsDetected = { detectedObjects: List<com.startups.airis.ml.DetectedObjectResult> ->
                                    onObjectsDetected(detectedObjects)
                                },
                                onTextDetected = { text: String ->
                                    onTextDetected(text)
                                }
                            )
                        )
                    }

                val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
    
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}
