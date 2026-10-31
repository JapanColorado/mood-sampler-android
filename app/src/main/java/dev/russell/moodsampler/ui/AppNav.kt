package dev.russell.moodsampler.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import dev.russell.moodsampler.ui.capture.CaptureScreen
import dev.russell.moodsampler.ui.components.Rule
import dev.russell.moodsampler.ui.dashboard.DashboardScreen
import dev.russell.moodsampler.ui.history.EditSampleScreen
import dev.russell.moodsampler.ui.history.HistoryScreen
import dev.russell.moodsampler.ui.home.HomeScreen
import dev.russell.moodsampler.ui.onboarding.OnboardingScreen
import dev.russell.moodsampler.ui.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val DASHBOARD = "dashboard"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val CAPTURE = "capture/{promptId}"
    const val EDIT = "edit/{promptId}"
    fun capture(promptId: Long) = "capture/$promptId"
    fun edit(promptId: Long) = "edit/$promptId"
}

private data class BottomTab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    BottomTab(Routes.DASHBOARD, "Insights", Icons.Outlined.Insights, Icons.Filled.Insights),
    BottomTab(Routes.HISTORY, "History", Icons.Outlined.AutoStories, Icons.Filled.AutoStories),
    BottomTab(Routes.SETTINGS, "Settings", Icons.Outlined.Tune, Icons.Filled.Tune),
)

/** Focused, single-purpose flows that hide the tab bar. */
private val fullScreenRoutes = setOf(Routes.ONBOARDING, Routes.CAPTURE, Routes.EDIT)

/** @param startDestination [Routes.HOME], or [Routes.ONBOARDING] on a fresh install. */
@Composable
fun AppNav(navController: NavHostController, startDestination: String = Routes.HOME) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        // Bottom only: each screen pads for the status bar itself, so moods can wash under it.
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = {
            if (currentRoute !in fullScreenRoutes) {
                Column {
                    Rule()
                    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                        bottomTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                })
            }
            composable(Routes.HOME) {
                HomeScreen(onAnswerPrompt = { navController.navigate(Routes.capture(it)) })
            }
            composable(Routes.DASHBOARD) { DashboardScreen() }
            composable(Routes.HISTORY) {
                HistoryScreen(onEdit = { navController.navigate(Routes.edit(it)) })
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                Routes.CAPTURE,
                arguments = listOf(navArgument("promptId") { type = NavType.LongType }),
            ) { entry ->
                val promptId = entry.arguments?.getLong("promptId") ?: return@composable
                CaptureScreen(promptId = promptId, onDone = { navController.popBackStack(Routes.HOME, false) })
            }
            composable(
                Routes.EDIT,
                arguments = listOf(navArgument("promptId") { type = NavType.LongType }),
            ) { entry ->
                val promptId = entry.arguments?.getLong("promptId") ?: return@composable
                EditSampleScreen(promptId = promptId, onDone = { navController.popBackStack() })
            }
        }
    }
}
