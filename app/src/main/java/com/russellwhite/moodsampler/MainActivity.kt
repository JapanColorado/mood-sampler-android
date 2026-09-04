package com.russellwhite.moodsampler

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.russellwhite.moodsampler.ui.AppNav
import com.russellwhite.moodsampler.ui.Routes
import com.russellwhite.moodsampler.ui.theme.MoodSamplerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pendingPromptId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPromptId.value = intent?.promptIdOrNull()
        setContent {
            MoodSamplerTheme {
                // NavHost can't change its start destination after first composition,
                // so resolve it first and paint a blank surface meanwhile (no Home flash).
                val start by produceState<String?>(initialValue = null) {
                    value = if (appGraph.onboardingGate.isDone()) Routes.HOME else Routes.ONBOARDING
                }
                val startDestination = start
                if (startDestination == null) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                } else {
                    val navController = rememberNavController()
                    val promptId = pendingPromptId.value
                    LaunchedEffect(promptId) {
                        if (promptId != null) {
                            navController.navigate(Routes.capture(promptId))
                            pendingPromptId.value = null
                        }
                    }
                    AppNav(navController, startDestination)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Idempotent: sweeps overdue prompts, tops up the 2-day schedule, arms
        // the next alarm. Cheap enough to run on every foreground.
        lifecycleScope.launch { appGraph.promptScheduler.ensureScheduled() }
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
