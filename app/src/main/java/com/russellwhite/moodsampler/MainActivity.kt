package com.russellwhite.moodsampler

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.russellwhite.moodsampler.ui.AppNav
import com.russellwhite.moodsampler.ui.Routes
import com.russellwhite.moodsampler.ui.theme.MoodSamplerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pendingPromptId = mutableStateOf<Long?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPromptId.value = intent?.promptIdOrNull()
        requestNotificationPermissionIfNeeded()
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun Intent.promptIdOrNull(): Long? =
        getLongExtra(EXTRA_PROMPT_ID, -1L).takeIf { it > 0 }

    companion object {
        const val EXTRA_PROMPT_ID = "extra_prompt_id"
    }
}
