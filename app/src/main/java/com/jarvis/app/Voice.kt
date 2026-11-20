package com.jarvis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun Voice(apiKey: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Toca el núcleo para hablar") }
    var heard by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    val history = remember { mutableListOf<Pair<String, String>>() }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var t: TextToSpeech? = null
        t = TextToSpeech(ctx) { s ->
            if (s == TextToSpeech.SUCCESS) t?.language = Locale("es", "ES")
        }
        tts = t
        onDispose { t?.shutdown() }
    }

    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(ctx) }
    DisposableEffect(Unit) { onDispose { recognizer.destroy() } }

    fun handle(text: String) {
        heard = text
        status = "Pensando..."
        history.add("user" to text)
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                if (apiKey.isBlank()) "Falta tu API key de Groq en Ajustes."
                else askGroq(apiKey, history.toList())
            }
            history.add("assistant" to r)
            answer = r
            status = "Toca el núcleo para hablar"
            tts?.speak(r, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        }
    }

    fun listen() {
        tts?.stop()
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { status = "Escuchando..." }
            override fun onResults(r: Bundle?) {
                val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) status = "No te escuché, intenta otra vez" else handle(t)
            }
            override fun onError(e: Int) { status = "Error de voz ($e). Toca para reintentar" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
        }
        recognizer.startListening(i)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) listen() else status = "Necesito el permiso de micrófono"
    }

    fun onTap() {
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
        else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(140.dp).clip(CircleShape).background(Cyan.copy(alpha = 0.25f)).clickable { onTap() },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(80.dp).clip(CircleShape).background(Cyan))
        }
        Spacer(Modifier.height(20.dp))
        Text(status, color = Cyan, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        if (heard.isNotBlank()) Text("Tú: $heard", color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        if (answer.isNotBlank()) Text("Jarvis: $answer", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}
