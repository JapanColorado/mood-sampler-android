package com.russellwhite.moodsampler.scheduling

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.russellwhite.moodsampler.MainActivity
import com.russellwhite.moodsampler.R

class PromptNotifier(private val context: Context) {

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mood prompts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Random experience-sampling prompts"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showPrompt(promptId: Long, expiryMinutes: Int) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val contentIntent = PendingIntent.getActivity(
            context,
            promptId.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PROMPT_ID, promptId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("How are you feeling right now?")
            .setContentText("Takes ~15 seconds · expires in $expiryMinutes min")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            // UI auto-dismiss only; the expiry ALARM is what records the miss.
            .setTimeoutAfter(expiryMinutes * 60_000L)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(promptId.toInt(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between check and notify; the prompt
            // will simply expire as MISSED.
        }
    }

    fun cancel(promptId: Long) {
        NotificationManagerCompat.from(context).cancel(promptId.toInt())
    }

    companion object {
        const val CHANNEL_ID = "prompts"
    }
}
