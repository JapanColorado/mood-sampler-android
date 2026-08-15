package com.russellwhite.moodsampler.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.russellwhite.moodsampler.appGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val promptId = intent.getLongExtra(EXTRA_PROMPT_ID, -1L)
        if (promptId <= 0) return
        val action = intent.action ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val scheduler = context.appGraph.promptScheduler
                when (action) {
                    ACTION_PROMPT -> scheduler.onPromptAlarmFired(promptId)
                    ACTION_EXPIRE -> scheduler.onExpiryAlarmFired(promptId)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "alarm handling failed for prompt $promptId", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "MoodSampler"
        const val ACTION_PROMPT = "com.russellwhite.moodsampler.action.PROMPT"
        const val ACTION_EXPIRE = "com.russellwhite.moodsampler.action.EXPIRE"
        const val EXTRA_PROMPT_ID = "promptId"
    }
}
