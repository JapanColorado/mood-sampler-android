package com.russellwhite.moodsampler.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.russellwhite.moodsampler.appGraph
import com.russellwhite.moodsampler.data.SkipReason
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
                    ACTION_SNOOZE -> scheduler.onSnoozeRequested(promptId)
                    ACTION_RESHOW -> scheduler.onSnoozeElapsed(promptId)
                    ACTION_SKIP -> scheduler.onPromptSkipped(promptId, SkipReason.NOTIFICATION)
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
        /** Notification action: hide the prompt and re-post it in 5 minutes. */
        const val ACTION_SNOOZE = "com.russellwhite.moodsampler.action.SNOOZE"
        /** Alarm: the snooze elapsed — re-post if the prompt is still live. */
        const val ACTION_RESHOW = "com.russellwhite.moodsampler.action.RESHOW"
        /** Notification action: record the prompt as skipped. */
        const val ACTION_SKIP = "com.russellwhite.moodsampler.action.SKIP"
        const val EXTRA_PROMPT_ID = "promptId"
    }
}
