package dev.russell.moodsampler.ui.capture

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.russell.moodsampler.ui.rememberMicrophonePermission

/** Live state of in-app dictation, for the mic button and the "listening…" line. */
@Stable
class Dictation internal constructor() {
    internal var onToggle: () -> Unit = {}

    var listening by mutableStateOf(false)
        internal set

    /** Words heard so far in the current utterance; replaced by the final text when it ends. */
    var partial by mutableStateOf("")
        internal set

    /** Why the last attempt produced nothing, or null. */
    var error by mutableStateOf<String?>(null)
        internal set

    fun toggle() = onToggle()
}

/**
 * Dictation through the platform speech recognizer: each finished utterance goes to
 * [onFinal]; tap again to keep talking. Null when the phone has no recognizer.
 * Asks for the microphone permission on first use.
 */
@Composable
internal fun rememberDictation(onFinal: (String) -> Unit): Dictation? {
    val context = LocalContext.current
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val permission = rememberMicrophonePermission()
    val latestOnFinal by rememberUpdatedState(onFinal)
    var startWhenGranted by remember { mutableStateOf(false) }
    val recognizer = remember { if (available) SpeechRecognizer.createSpeechRecognizer(context) else null }
    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    val dictation = remember { Dictation() }
    fun start() {
        dictation.error = null
        dictation.partial = ""
        dictation.listening = true
        recognizer?.startListening(intent)
    }
    dictation.onToggle = {
        when {
            dictation.listening -> recognizer?.stopListening()
            permission.granted -> start()
            else -> {
                // A second tap without the permission: Android may no longer show the dialog.
                if (startWhenGranted) dictation.error = "Allow the microphone for Mood Sampler in system settings to dictate."
                startWhenGranted = true
                permission.request()
            }
        }
    }

    LaunchedEffect(permission.granted) {
        if (permission.granted && startWhenGranted) {
            startWhenGranted = false
            start()
        }
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                dictation.listening = false
                dictation.partial = ""
                if (text.isNotBlank()) latestOnFinal(text.trim())
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.let { dictation.partial = it }
            }

            override fun onError(error: Int) {
                dictation.listening = false
                dictation.partial = ""
                dictation.error = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Tap the mic to try again."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Dictation needs a connection right now."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is off."
                    SpeechRecognizer.ERROR_CLIENT -> null  // stopped or cancelled by us
                    else -> "Dictation stopped (error $error)."
                }
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose { recognizer?.destroy() }
    }

    return dictation.takeIf { available }
}
