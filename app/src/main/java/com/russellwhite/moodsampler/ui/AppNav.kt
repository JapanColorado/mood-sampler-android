package com.russellwhite.moodsampler.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.russellwhite.moodsampler.ui.capture.CaptureScreen
import com.russellwhite.moodsampler.ui.dashboard.DashboardScreen
import com.russellwhite.moodsampler.ui.history.EditSampleScreen
import com.russellwhite.moodsampler.ui.history.HistoryScreen
import com.russellwhite.moodsampler.ui.home.HomeScreen
import com.russellwhite.moodsampler.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val DASHBOARD = "dashboard"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val CAPTURE = "capture/{promptId}"
    const val EDIT = "edit/{promptId}"
    fun capture(promptId: Long) = "capture/$promptId"
    fun edit(promptId: Long) = "edit/$promptId"
}

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "Home", Icons.Filled.Home),
    BottomTab(Routes.DASHBOARD, "Insights", Icons.Filled.Insights),
    BottomTab(Routes.HISTORY, "History", Icons.Filled.History),
    BottomTab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

/** Focused, single-purpose flows that hide the tab bar. */
private val fullScreenRoutes = setOf(Routes.CAPTURE, Routes.EDIT)

@Composable
fun AppNav(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute !in fullScreenRoutes) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) { HomeScreen() }
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
