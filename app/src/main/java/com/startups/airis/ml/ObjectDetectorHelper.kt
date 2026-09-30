package com.startups.airis.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.Detection

enum class ObjectPosition {
    LEFT, CENTER, RIGHT, UNKNOWN
}

// Map terjemahan 80 kelas COCO dari bahasa Inggris ke bahasa Indonesia
val cocoTranslations = mapOf(
    "person" to "orang", "bicycle" to "sepeda", "car" to "mobil", "motorcycle" to "sepeda motor", 
    "airplane" to "pesawat terbang", "bus" to "bus", "train" to "kereta", "truck" to "truk", 
    "boat" to "perahu", "traffic light" to "lampu lalu lintas", "fire hydrant" to "hidran kebakaran", 
    "stop sign" to "rambu berhenti", "parking meter" to "meteran parkir", "bench" to "bangku", 
    "bird" to "burung", "cat" to "kucing", "dog" to "anjing", "horse" to "kuda", 
    "sheep" to "domba", "cow" to "sapi", "elephant" to "gajah", "bear" to "beruang", 
    "zebra" to "zebra", "giraffe" to "jerapah", "backpack" to "ransel", "umbrella" to "payung", 
    "handbag" to "tas tangan", "tie" to "dasi", "suitcase" to "koper", "frisbee" to "frisbee", 
    "skis" to "alat ski", "snowboard" to "papan seluncur salju", "sports ball" to "bola", 
    "kite" to "layang-layang", "baseball bat" to "tongkat bisbol", "baseball glove" to "sarung tangan bisbol", 
    "skateboard" to "papan luncur", "surfboard" to "papan selancar", "tennis racket" to "raket tenis", 
    "bottle" to "botol", "wine glass" to "gelas anggur", "cup" to "cangkir", "fork" to "garpu", 
    "knife" to "pisau", "spoon" to "sendok", "bowl" to "mangkuk", "banana" to "pisang", 
    "apple" to "apel", "sandwich" to "roti isi", "orange" to "jeruk", "broccoli" to "brokoli", 
    "carrot" to "wortel", "hot dog" to "hot dog", "pizza" to "pizza", "donut" to "donat", 
    "cake" to "kue", "chair" to "kursi", "couch" to "sofa", "potted plant" to "tanaman hias", 
    "bed" to "tempat tidur", "dining table" to "meja makan", "toilet" to "toilet", "tv" to "tv", 
    "laptop" to "laptop", "mouse" to "mouse", "remote" to "remot", "keyboard" to "keyboard", 
    "cell phone" to "handphone", "microwave" to "microwave", "oven" to "oven", "toaster" to "pemanggang roti", 
    "sink" to "wastafel", "refrigerator" to "kulkas", "book" to "buku", "clock" to "jam", 
    "vase" to "vas bunga", "scissors" to "gunting", "teddy bear" to "boneka beruang", 
    "hair drier" to "pengering rambut", "toothbrush" to "sikat gigi"
)
val publicFacilitiesSet = setOf(
    "toilet", "lampu lalu lintas", "rambu berhenti", "meteran parkir", 
    "hidran kebakaran", "bangku", "bus"
)

val outdoorObjectsSet = setOf(
    "orang", "mobil", "sepeda motor", "sepeda", "truk", "kereta", 
    "anjing", "kucing", "payung", "tas tangan", "koper"
) + publicFacilitiesSet

val indoorFacilitiesSet = setOf(
    "kursi", "sofa", "meja makan", "tempat tidur", "tv", "laptop", "mouse", 
    "remot", "keyboard", "handphone", "microwave", "oven", "pemanggang roti", 
    "wastafel", "kulkas", "buku", "jam", "vas bunga", "gunting", "pengering rambut", 
    "sikat gigi", "panci", "gelas anggur", "cangkir", "garpu", "pisau", "sendok", "mangkuk"
)

data class DetectedObjectResult(
    val name: String,
    val confidence: Float,
    val position: ObjectPosition,
    val isPublicFacility: Boolean = false,
    val isOutdoorObject: Boolean = false,
    val isIndoorFacility: Boolean = false
)

class ObjectDetectorHelper(
    context: Context,
    private val onObjectsDetected: (List<DetectedObjectResult>) -> Unit
) {

    private var objectDetector: ObjectDetector? = null

    init {
        try {
            val baseOptions = BaseOptions.builder().setNumThreads(2).build()
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setMaxResults(3)
                .setScoreThreshold(0.6f) // 60% confidence
                .build()

            // Memuat file TFLite dari folder assets
            objectDetector = ObjectDetector.createFromFileAndOptions(
                context, 
                "ssd_mobilenet_v1.tflite", 
                options
            )
        } catch (e: Exception) {
            Log.e("ObjectDetectorHelper", "TFLite failed to load model", e)
        }
    }

    fun detect(bitmap: Bitmap, rotationDegrees: Int) {
        if (objectDetector == null) return

        val imageProcessor = ImageProcessor.Builder()
            .add(Rot90Op(-rotationDegrees / 90))
            .build()
        
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(bitmap))

        try {
            val results: List<Detection> = objectDetector!!.detect(tensorImage)

            val imageWidth = tensorImage.width

            val detectedList = mutableListOf<DetectedObjectResult>()

            for (result in results) {
                val category = result.categories.firstOrNull()
                if (category != null) {
                    val boundingBox = result.boundingBox
                    val boxCenterX = boundingBox.centerX()
                    
                    val position = when {
                        boxCenterX < imageWidth / 3 -> ObjectPosition.LEFT
                        boxCenterX > (imageWidth * 2) / 3 -> ObjectPosition.RIGHT
                        else -> ObjectPosition.CENTER
                    }

                    val translatedName = cocoTranslations[category.label] ?: category.label
                    val isPublicFacility = publicFacilitiesSet.contains(translatedName)
                    val isOutdoorObject = outdoorObjectsSet.contains(translatedName)
                    val isIndoorFacility = indoorFacilitiesSet.contains(translatedName)

                    detectedList.add(
                        DetectedObjectResult(
                            name = translatedName,
                            confidence = category.score,
                            position = position,
                            isPublicFacility = isPublicFacility,
                            isOutdoorObject = isOutdoorObject,
                            isIndoorFacility = isIndoorFacility
                        )
                    )
                }
            }
            onObjectsDetected(detectedList)
        } catch (e: Exception) {
            Log.e("ObjectDetectorHelper", "Detection failed", e)
        }
    }
}
