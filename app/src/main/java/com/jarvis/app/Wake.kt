package com.jarvis.app

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable
fun WakeToggle() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(false) }
    val perms = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.RECORD_AUDIO)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.RECORD_AUDIO] == true) {
            ctx.startForegroundService(Intent(ctx, JarvisService::class.java))
            on = true
        }
    }
    Button(onClick = {
        if (on) {
            ctx.stopService(Intent(ctx, JarvisService::class.java))
            on = false
        } else launcher.launch(perms)
    }) { Text(if (on) "Detener escucha Jarvis" else "Activar escucha Jarvis") }
}
