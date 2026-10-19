package dev.russell.moodsampler.scheduling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.russell.moodsampler.MainActivity
import dev.russell.moodsampler.R

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

    /**
     * Post (or re-post after a snooze) the prompt notification.
     *
     * @param expiresAtMs when the prompt turns MISSED; drives the "expires in" text and the UI timeout.
     * @param snoozeIntent action shown only when there is enough time left to snooze (caller decides).
     * @param skipIntent records the prompt as skipped without opening the app.
     */
    fun showPrompt(
        promptId: Long,
        nowMs: Long,
        expiresAtMs: Long,
        snoozeIntent: PendingIntent?,
        skipIntent: PendingIntent,
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val contentIntent = PendingIntent.getActivity(
            context,
            promptId.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PROMPT_ID, promptId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val remaining = SnoozePolicy.remainingMinutes(nowMs, expiresAtMs)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("How are you feeling right now?")
            .setContentText("Takes ~15 seconds · expires in $remaining min")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            // UI auto-dismiss only; the expiry ALARM is what records the miss.
            .setTimeoutAfter((expiresAtMs - nowMs).coerceAtLeast(1_000L))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(R.drawable.ic_notification, "Skip", skipIntent)
        if (snoozeIntent != null) {
            builder.addAction(R.drawable.ic_notification, "Remind me in 5 min", snoozeIntent)
        }

        try {
            NotificationManagerCompat.from(context).notify(promptId.toInt(), builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between check and notify; the prompt
            // will simply expire as MISSED.
        }
    }

    fun cancel(promptId: Long) {
        NotificationManagerCompat.from(context).cancel(promptId.toInt())
    }

    fun cancelAll() {
        NotificationManagerCompat.from(context).cancelAll()
    }

    companion object {
        const val CHANNEL_ID = "prompts"
    }
}
