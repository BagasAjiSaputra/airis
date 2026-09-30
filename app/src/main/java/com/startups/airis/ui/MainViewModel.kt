package com.startups.airis.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.startups.airis.bluetooth.BluetoothService
import com.startups.airis.bluetooth.ConnectionState
import com.startups.airis.ml.DetectedObjectResult
import com.startups.airis.ml.ObjectPosition
import com.startups.airis.tts.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class DetectionMode {
    INDOOR, FAST, OUTDOOR
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val bluetoothService = BluetoothService(application)
    private val ttsManager = TtsManager(application)
    private val locationTracker = com.startups.airis.location.LocationTracker(application)

    init {
        locationTracker.startTracking()
        
        viewModelScope.launch {
            var prevState = bluetoothService.connectionState.value
            bluetoothService.connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED && prevState != ConnectionState.CONNECTED) {
                    ttsManager.speakPriority("Alat terhubung")
                } else if ((state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR) 
                           && (prevState == ConnectionState.CONNECTED || prevState == ConnectionState.CONNECTING)) {
                    ttsManager.speakPriority("Alat terputus")
                }
                prevState = state
            }
        }
    }

    val connectionState: StateFlow<ConnectionState> = bluetoothService.connectionState
    val sensorData = bluetoothService.sensorData

    private val _latestDetectedObject = MutableStateFlow<DetectedObjectResult?>(null)
    val latestDetectedObject: StateFlow<DetectedObjectResult?> = _latestDetectedObject

    private val _currentMode = MutableStateFlow(DetectionMode.FAST)
    val currentMode: StateFlow<DetectionMode> = _currentMode

    fun cycleMode() {
        val nextMode = when (_currentMode.value) {
            DetectionMode.INDOOR -> DetectionMode.FAST
            DetectionMode.FAST -> DetectionMode.OUTDOOR
            DetectionMode.OUTDOOR -> DetectionMode.INDOOR
        }
        setMode(nextMode)
    }

    fun setMode(mode: DetectionMode) {
        _currentMode.value = mode
        
        // Reset riwayat TTS dan objek agar langsung dibacakan ulang saat pindah mode
        ttsManager.clearHistory()
        lastSpokenText = ""
        currentCenterObject = null
        centerObjectStartTime = 0L

        val modeName = when (mode) {
            DetectionMode.INDOOR -> "Deteksi Objek"
            DetectionMode.FAST -> "Deteksi Cepat"
            DetectionMode.OUTDOOR -> "Mode O C R"
        }
        ttsManager.speakMode(modeName)
    }

    fun connectBluetooth() {
        viewModelScope.launch {
            bluetoothService.connectToDevice()
        }
    }

    private var currentCenterObject: String? = null
    private var centerObjectStartTime: Long = 0L

    fun onObjectsDetected(results: List<DetectedObjectResult>) {
        if (connectionState.value != ConnectionState.CONNECTED) {
            _latestDetectedObject.value = null
            currentCenterObject = null
            return
        }

        if (_currentMode.value == DetectionMode.OUTDOOR) {
            _latestDetectedObject.value = null
            return
        }

        // Hanya fokus pada objek di tengah
        val centerResults = results.filter { it.position == ObjectPosition.CENTER }

        val filteredResults = when (_currentMode.value) {
            DetectionMode.INDOOR -> centerResults.filter { it.isIndoorFacility }
            DetectionMode.OUTDOOR -> centerResults.filter { it.isOutdoorObject }
            DetectionMode.FAST -> centerResults
        }

        val centerObject = filteredResults.firstOrNull()
        _latestDetectedObject.value = centerObject

        if (centerObject != null) {
            val now = System.currentTimeMillis()
            
            if (currentCenterObject != centerObject.name) {
                // Object changed
                currentCenterObject = centerObject.name
                centerObjectStartTime = now
            }

            val timeInCenter = now - centerObjectStartTime
            val currentSensorData = sensorData.value
            val distance = currentSensorData?.depanM ?: 999.0
            val includeDistance = _currentMode.value != DetectionMode.FAST
            
            // Jeda sedikit (500ms) sebelum bicara untuk Mode Objek, Mode Cepat instan (0ms)
            val delayRequired = if (_currentMode.value == DetectionMode.INDOOR) 500L else 0L
            
            if (timeInCenter >= delayRequired) {
                val debounceMs = if (_currentMode.value == DetectionMode.INDOOR) 2500L else 1500L
                ttsManager.speak(centerObject.name, distance, includeDistance, debounceMs)
            }
        } else {
            // No object in center, reset tracking
            currentCenterObject = null
            centerObjectStartTime = 0L
        }
    }

    private var lastSpokenText: String = ""

    fun onTextDetected(text: String) {
        if (connectionState.value != ConnectionState.CONNECTED) return

        if (_currentMode.value != DetectionMode.OUTDOOR) {
            lastSpokenText = ""
            return
        }

        val normalizedText = text.lowercase().trim()
        if (normalizedText.isNotEmpty() && normalizedText != lastSpokenText) {
            ttsManager.speakRaw(text)
            lastSpokenText = normalizedText
        } else if (normalizedText.isEmpty()) {
            lastSpokenText = ""
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationTracker.stopTracking()
        bluetoothService.closeConnection()
        ttsManager.shutdown()
    }
}
