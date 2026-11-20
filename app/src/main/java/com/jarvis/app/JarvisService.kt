package com.jarvis.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class JarvisService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speaking = false
    private var listening = false
    private var awaitingCommand = false
    private var running = false
    private val history = mutableListOf<Pair<String, String>>()

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            startForegroundNow()
            tts = TextToSpeech(this) { s ->
                if (s == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("es", "ES")
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) { main.post { speaking = false; listen() } }
                        override fun onError(id: String?) { main.post { speaking = false; listen() } }
                    })
                    ttsReady = true
                }
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(listener)
            listen()
        }
        return START_STICKY
    }

    private fun startForegroundNow() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "jarvis")
            .setContentTitle("Jarvis escuchando")
            .setContentText("Di \"Jarvis\" y tu pregunta")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        if (Build.VERSION.SDK_INT >= 30) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
    }

    private fun listen() {
        if (!running || speaking || listening) return
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
        }
        try {
            listening = true
            recognizer?.startListening(i)
        } catch (e: Exception) {
            listening = false
            main.postDelayed({ listen() }, 1500)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onResults(r: Bundle?) {
            listening = false
            val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            handle(t)
        }
        override fun onError(e: Int) {
            listening = false
            main.postDelayed({ listen() }, 700)
        }
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(b: Bundle?) {}
        override fun onEvent(t: Int, b: Bundle?) {}
    }

    private fun handle(text: String) {
        if (text.isBlank()) { listen(); return }
        val idx = text.lowercase(Locale.ROOT).indexOf("jarvis")
        val command: String
        if (awaitingCommand) {
            command = text
            awaitingCommand = false
        } else if (idx >= 0) {
            command = text.substring(idx + 6).trim(' ', ',', '.', '¿', '?')
        } else {
            listen(); return
        }
        if (command.isBlank()) { awaitingCommand = true; say("Dime"); return }
        ask(command)
    }

    private fun ask(q: String) {
        val key = getSharedPreferences("jarvis", Context.MODE_PRIVATE).getString("groq_key", "") ?: ""
        history.add("user" to q)
        Thread {
            val r = if (key.isBlank()) "Falta tu API key de Groq en Ajustes." else askGroq(key, history.toList())
            main.post { history.add("assistant" to r); say(r) }
        }.start()
    }

    private fun say(t: String) {
        if (!ttsReady) { main.postDelayed({ listen() }, 500); return }
        speaking = true
        tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
