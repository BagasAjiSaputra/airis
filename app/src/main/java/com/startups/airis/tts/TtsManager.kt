package com.startups.airis.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    // Map to store the last time an object was spoken (debounce logic)
    private val lastSpokenTimes = mutableMapOf<String, Long>()
    
    // Block other speech for a duration to prioritize critical messages
    private var priorityUntil: Long = 0L

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.forLanguageTag("id-ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TtsManager", "Indonesian language is not supported or missing data")
            } else {
                isInitialized = true
                Log.d("TtsManager", "TTS Initialized in Indonesian")
            }
        } else {
            Log.e("TtsManager", "TTS Initialization failed")
        }
    }

    fun speak(objectName: String, distance: Double, includeDistance: Boolean = true, debounceMs: Long = 1500L) {
        if (!isInitialized) return
        if (System.currentTimeMillis() < priorityUntil) return

        val currentTime = System.currentTimeMillis()
        val lastSpoken = lastSpokenTimes[objectName] ?: 0L

        if (currentTime - lastSpoken > debounceMs) {
            // Update last spoken time
            lastSpokenTimes[objectName] = currentTime
            
            val textToSpeak = if (includeDistance) {
                // Mengubah format angka titik (1.21) menjadi koma (1 koma 21)
                val distanceStr = distance.toString().replace(".", " koma ")
                "$objectName, jarak $distanceStr meter"
            } else {
                objectName
            }
            
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
            Log.d("TtsManager", "Speaking: $textToSpeak")
        }
    }

    fun speakMode(modeName: String) {
        if (!isInitialized) return
        if (System.currentTimeMillis() < priorityUntil) return
        val textToSpeak = "Mode $modeName"
        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
        Log.d("TtsManager", "Speaking Mode: $textToSpeak")
    }

    fun speakRaw(text: String) {
        if (!isInitialized) return
        if (System.currentTimeMillis() < priorityUntil) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        Log.d("TtsManager", "Speaking Raw: $text")
    }

    fun speakPriority(text: String) {
        if (!isInitialized) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        priorityUntil = System.currentTimeMillis() + 2500L // Beri waktu 2.5 detik agar selesai diucapkan
        Log.d("TtsManager", "Speaking Priority: $text")
    }

    fun clearHistory() {
        lastSpokenTimes.clear()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
