package com.startups.airis.ml

import android.content.Context
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class VisionAnalyzer(
    context: Context,
    private val onObjectsDetected: (List<DetectedObjectResult>) -> Unit,
    private val onTextDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val objectDetectorHelper = ObjectDetectorHelper(context, onObjectsDetected)
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var lastTextAnalysisTime = 0L
    private val textAnalysisIntervalMs = 1000L // 1 second

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        if (bitmap == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // 1. Sinkron: Object Detection
        objectDetectorHelper.detect(bitmap, rotationDegrees)

        // 2. Asinkron: ML Kit OCR (throttled)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTextAnalysisTime >= textAnalysisIntervalMs) {
            lastTextAnalysisTime = currentTime

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                textRecognizer.process(inputImage)
                    .addOnSuccessListener { textResult ->
                        val imageWidth = inputImage.width
                        val imageHeight = inputImage.height
                        val recognizedText = textResult.textBlocks
                            .filter { block ->
                                val boundingBox = block.boundingBox
                                if (boundingBox != null) {
                                    val centerX = boundingBox.centerX()
                                    val centerY = boundingBox.centerY()
                                    // Memperluas area baca (25% - 75%) agar teks yang posisinya agak ke bawah tetap terdeteksi
                                    val inX = centerX > imageWidth * 0.25f && centerX < imageWidth * 0.75f
                                    val inY = centerY > imageHeight * 0.25f && centerY < imageHeight * 0.75f
                                    inX && inY
                                } else {
                                    false
                                }
                            }
                            .map { it.text.trim() }
                            .joinToString(" ")
                        
                        onTextDetected(recognizedText)
                    }
                    .addOnFailureListener { e ->
                        Log.e("VisionAnalyzer", "Text recognition failed", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
                return // imageProxy will be closed by addOnCompleteListener
            }
        }

        // Jika tidak menjalankan OCR, close imageProxy langsung
        imageProxy.close()
    }
}
