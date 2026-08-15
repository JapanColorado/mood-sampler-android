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

/**
 * Alarms don't survive reboot, and wall-clock/zone changes can invalidate the
 * armed alarm — re-derive everything from DB truth.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> Unit
            else -> return
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                context.appGraph.promptScheduler.ensureScheduled()
            } catch (t: Throwable) {
                Log.e("MoodSampler", "reschedule after ${intent.action} failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
