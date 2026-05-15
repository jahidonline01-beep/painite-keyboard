package com.painite.keyboard.utils

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceTypingManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onStateChange: (Boolean) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var audioMuted = false
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun startListening(language: String = "en-US") {
        try {
            if (isListening) return
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("Speech recognition not available")
                return
            }

            val recognizer = ensureSpeechRecognizer()
            isListening = true
            muteRecognitionSounds()
            onStateChange(true)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1100L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 650L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
            }

            recognizer.startListening(intent)
        } catch (_: Exception) {
            isListening = false
            restoreRecognitionSounds()
            onStateChange(false)
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
            onError("Voice typing failed")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.apply {
                stopListening()
                cancel()
                destroy()
            }
        } catch (_: Exception) {}
        speechRecognizer = null
        restoreRecognitionSounds()
        if (isListening) {
            isListening = false
            onStateChange(false)
        }
    }

    fun destroy() = stopListening()

    private fun ensureSpeechRecognizer(): SpeechRecognizer {
        return speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    isListening = false
                    onStateChange(false)
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        else -> "Voice error ($error)"
                    }
                    onError(msg)
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    onStateChange(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onResult(matches[0])
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    // Keep partial speech internal so final committed text is not duplicated.
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }.also { speechRecognizer = it }
    }

    private fun muteRecognitionSounds() {
        if (audioMuted) return
        val manager = audioManager ?: return
        try {
            val streams = recognitionSoundStreams()
            streams.forEach { stream ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    manager.adjustStreamVolume(
                        stream,
                        AudioManager.ADJUST_MUTE,
                        AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                    )
                }
                @Suppress("DEPRECATION")
                manager.setStreamMute(stream, true)
            }
            audioMuted = true
        } catch (_: Exception) {}
    }

    private fun restoreRecognitionSounds() {
        if (!audioMuted) return
        val manager = audioManager ?: return
        try {
            val streams = recognitionSoundStreams()
            streams.forEach { stream ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    manager.adjustStreamVolume(
                        stream,
                        AudioManager.ADJUST_UNMUTE,
                        AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                    )
                }
                @Suppress("DEPRECATION")
                manager.setStreamMute(stream, false)
            }
        } catch (_: Exception) {
        } finally {
            audioMuted = false
        }
    }

    private fun recognitionSoundStreams(): IntArray {
        return intArrayOf(
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_DTMF,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_ACCESSIBILITY
        )
    }
}
