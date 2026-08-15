package com.russellwhite.moodsampler

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.compose.rememberNavController
import com.russellwhite.moodsampler.ui.AppNav
import com.russellwhite.moodsampler.ui.Routes
import com.russellwhite.moodsampler.ui.theme.MoodSamplerTheme

class MainActivity : ComponentActivity() {

    private val pendingPromptId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPromptId.value = intent?.promptIdOrNull()
        setContent {
            MoodSamplerTheme {
                val navController = rememberNavController()
                val promptId = pendingPromptId.value
                LaunchedEffect(promptId) {
                    if (promptId != null) {
                        navController.navigate(Routes.capture(promptId))
                        pendingPromptId.value = null
                    }
                }
                AppNav(navController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.promptIdOrNull()?.let { pendingPromptId.value = it }
    }

    private fun Intent.promptIdOrNull(): Long? =
        getLongExtra(EXTRA_PROMPT_ID, -1L).takeIf { it > 0 }

    companion object {
        const val EXTRA_PROMPT_ID = "extra_prompt_id"
    }
}
