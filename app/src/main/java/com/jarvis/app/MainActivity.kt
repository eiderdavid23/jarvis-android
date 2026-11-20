package com.jarvis.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val Bg = Color(0xFF050A10)
val Cyan = Color(0xFF38BDF8)
val Card = Color(0xFF0F1B27)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        setContent { App(prefs) }
    }
}

@Composable
fun App(prefs: android.content.SharedPreferences) {
    var screen by remember { mutableStateOf("home") }
    var apiKey by remember { mutableStateOf(prefs.getString("groq_key", "") ?: "") }
    BackHandler(screen != "home") { screen = "home" }
    Box(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
        when (screen) {
            "home" -> Home { screen = it }
            "Chat" -> Chat(apiKey)
            "Ajustes" -> Settings(apiKey) { apiKey = it; prefs.edit().putString("groq_key", it).apply() }
            else -> Center("$screen: próximamente")
        }
    }
}

@Composable
fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Cyan, fontSize = 18.sp)
    }
}

@Composable
fun Home(onOpen: (String) -> Unit) {
    val items = listOf("Chat", "Gestos", "Música", "Voz", "Conectores", "Ajustes")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        Text("JARVIS", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
        Text("TU ASISTENTE INTELIGENTE", color = Cyan, fontSize = 12.sp, letterSpacing = 3.sp)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(90.dp).clip(CircleShape).background(Cyan.copy(alpha = 0.25f)))
            Box(Modifier.size(50.dp).clip(CircleShape).background(Cyan))
            items.forEachIndexed { i, name ->
                val a = 2 * PI * i / items.size - PI / 2
                Column(
                    Modifier.offset(x = (125 * cos(a)).dp, y = (125 * sin(a)).dp)
                        .clickable { onOpen(name) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Card), contentAlignment = Alignment.Center) {
                        Text(name.take(1), color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(name, color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun Chat(apiKey: String) {
    val msgs = remember { mutableStateListOf<Pair<String, String>>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(msgs) { m ->
                val mine = m.first == "user"
                Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(
                        m.second, color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp))
                            .background(if (mine) Cyan.copy(alpha = 0.3f) else Card).padding(12.dp)
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Habla con Jarvis...") }
            )
            Spacer(Modifier.width(8.dp))
            Button(enabled = !busy && input.isNotBlank(), onClick = {
                val q = input; input = ""
                msgs.add("user" to q); busy = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        if (apiKey.isBlank()) "Falta tu API key de Groq en Ajustes." else askGroq(apiKey, msgs.toList())
                    }
                    msgs.add("assistant" to r); busy = false
                }
            }) { Text("Enviar") }
        }
    }
}

@Composable
fun Settings(apiKey: String, onSave: (String) -> Unit) {
    var key by remember { mutableStateOf(apiKey) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("AJUSTES", color = Cyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("API key de Groq") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onSave(key.trim()) }) { Text("Guardar") }
    }
}

fun askGroq(key: String, msgs: List<Pair<String, String>>): String {
    return try {
        val arr = JSONArray()
        arr.put(JSONObject().put("role", "system").put("content", "Eres Jarvis, un asistente personal en español. Responde breve y claro."))
        msgs.forEach { arr.put(JSONObject().put("role", it.first).put("content", it.second)) }
        val body = JSONObject().put("model", "llama-3.3-70b-versatile").put("messages", arr).toString()
        val c = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.setRequestProperty("Authorization", "Bearer $key")
        c.setRequestProperty("Content-Type", "application/json")
        c.doOutput = true
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
        if (code !in 200..299) "Error $code: $text"
        else JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
