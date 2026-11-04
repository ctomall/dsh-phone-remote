package com.ila3.dshnative.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Text-to-speech reader for assistant messages, one voice for the whole chat. */
class Speaker(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false

    var speakingKey by mutableStateOf<String?>(null)
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                runCatching { tts?.language = Locale.CHINA }
            }
        }
    }

    fun toggle(key: String, text: String) {
        if (speakingKey == key) {
            stop()
            return
        }
        val plain = plainText(text)
        if (plain.isBlank()) return
        speakingKey = key
        tts?.stop()
        tts?.speak(plain, TextToSpeech.QUEUE_FLUSH, null, key)
    }

    fun stop() {
        speakingKey = null
        tts?.stop()
    }

    fun shutdown() {
        stateStop()
        tts?.shutdown()
        tts = null
    }

    private fun stateStop() {
        speakingKey = null
    }

    private fun plainText(text: String): String = text
        .replace(Regex("```[\\s\\S]*?```"), " 代码块 ")
        .replace(Regex("[#*`>_|]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) {
        onDispose { speaker.shutdown() }
    }
    return speaker
}
