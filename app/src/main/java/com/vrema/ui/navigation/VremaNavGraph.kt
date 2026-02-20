package com.vrema.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vrema.ui.analytics.AnalyticsScreen
import com.vrema.ui.backup.BackupScreen
import com.vrema.ui.home.HomeScreen
import com.vrema.ui.month.MonthScreen
import com.vrema.ui.planning.PlanningScreen
import com.vrema.ui.quota.QuotaScreen
import com.vrema.ui.settings.SettingsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Heute", Icons.Default.Home)
    data object Month : Screen("month", "Monat", Icons.Default.CalendarMonth)
    data object Planning : Screen("planning", "Planung", Icons.Default.EditCalendar)
    data object Quota : Screen("quota", "Quote", Icons.Default.Dashboard)
    data object Analytics : Screen("analytics", "Analytics", Icons.Default.BarChart)
    data object Settings : Screen("settings", "Einstellungen", Icons.Default.Settings)
    data object Backup : Screen("backup", "Datensicherung", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Month,
    Screen.Planning,
    Screen.Quota,
    Screen.Analytics,
    Screen.Settings
)

@Composable
fun VremaNavGraph() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Month.route) { MonthScreen() }
            composable(Screen.Planning.route) { PlanningScreen() }
            composable(Screen.Quota.route) { QuotaScreen() }
            composable(Screen.Analytics.route) { AnalyticsScreen() }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToBackup = { navController.navigate(Screen.Backup.route) }
                )
            }
            composable(Screen.Backup.route) {
                BackupScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
