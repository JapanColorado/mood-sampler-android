package com.russellwhite.moodsampler.ui.onboarding

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.russellwhite.moodsampler.ui.rememberLocationPermission
import androidx.lifecycle.viewmodel.compose.viewModel
import com.russellwhite.moodsampler.ui.settings.SamplingControls
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory),
) {
    val pager = rememberPagerState { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    var finishing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (page) {
                    0 -> WhyPage()
                    1 -> PermissionsPage()
                    2 -> SchedulePage(viewModel)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                enabled = pager.currentPage > 0,
            ) { Text("Back") }
            Spacer(Modifier.weight(1f))
            PageDots(pager.currentPage)
            Spacer(Modifier.weight(1f))
            if (pager.currentPage < PAGE_COUNT - 1) {
                Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                    Text("Next")
                }
            } else {
                Button(
                    onClick = { finishing = true; viewModel.finish(onFinished) },
                    enabled = !finishing,
                ) { Text("Start sampling") }
            }
        }
    }
}

@Composable
private fun PageDots(current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(PAGE_COUNT) { i ->
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}

@Composable
private fun WhyPage() {
    Text("Random moments, honest picture", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Mood Sampler asks how you feel a few times a day, at random moments you " +
            "can't predict. Each check-in takes about 15 seconds: two quick 0–10 mood " +
            "taps, a pain rating, and what you're doing, where, and with whom.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        "Why random? Diaries remember the memorable. Random sampling catches the " +
            "ordinary Tuesday afternoon too, so over a few weeks the averages actually " +
            "describe your life.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        "Prompts expire after a while and count as missed — that's fine and expected. " +
            "The app never shows when the next one is coming; knowing would change the answer.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PermissionsPage() {
    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val alarmManager = remember { context.getSystemService(AlarmManager::class.java) }
    var exactOn by remember { mutableStateOf(alarmManager.canScheduleExactAlarms()) }
    val location = rememberLocationPermission()
    LifecycleResumeEffect(Unit) {
        notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        exactOn = alarmManager.canScheduleExactAlarms()
        onPauseOrDispose { }
    }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    Text("Let prompts reach you", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Prompts arrive as notifications. Without them nothing gets collected.",
        style = MaterialTheme.typography.bodyLarge,
    )
    PermissionCard(
        title = "Notifications",
        granted = notificationsOn,
        grantedText = "On — prompts can reach you.",
        missingText = "Off — allow notifications to receive prompts.",
        actionLabel = "Allow notifications",
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }
    PermissionCard(
        title = "Exact timing",
        granted = exactOn,
        grantedText = "On — prompts land at their exact random minute.",
        missingText = "Off — prompts would drift by up to ±10 min. Still works, but exact is better.",
        actionLabel = "Allow exact alarms",
    ) {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
    }
    PermissionCard(
        title = "Location (optional)",
        granted = location.granted,
        grantedText = "On — each answer records where you were and pre-selects your place.",
        missingText = "Off — you'll pick your place by hand. One fix per answer, only while answering; " +
            "no background tracking. You can change this later in Settings.",
        actionLabel = "Allow location",
        onRequest = location.request,
    )
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    grantedText: String,
    missingText: String,
    actionLabel: String,
    onRequest: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                if (granted) grantedText else missingText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (granted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            if (!granted) OutlinedButton(onClick = onRequest) { Text(actionLabel) }
        }
    }
}

@Composable
private fun SchedulePage(viewModel: OnboardingViewModel) {
    val settings by viewModel.settings.collectAsState()
    Text("Your sampling day", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Choose how many prompts you want and the hours you're awake. Each prompt " +
            "lands at a random moment inside its own slice of the day. You can change " +
            "this any time in Settings.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Card {
        Column(Modifier.padding(16.dp)) {
            SamplingControls(
                settings = settings,
                onWindowsPerDay = viewModel::setWindowsPerDay,
                onDayStart = viewModel::setDayStart,
                onDayEnd = viewModel::setDayEnd,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}
