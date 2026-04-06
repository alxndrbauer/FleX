package com.flex.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.flex.ui.about.AboutScreen
import com.flex.ui.analytics.AnalyticsScreen
import com.flex.ui.backup.BackupScreen
import com.flex.ui.home.HomeScreen
import com.flex.ui.month.MonthScreen
import com.flex.ui.onboarding.OnboardingScreen
import com.flex.ui.planning.PlanningScreen
import com.flex.ui.quota.QuotaScreen
import com.flex.ui.settings.CalendarSettingsScreen
import com.flex.ui.settings.GeofenceSettingsScreen
import com.flex.ui.settings.QuotaRulesScreen
import com.flex.ui.settings.SettingsScreen
import com.flex.ui.settings.WifiSettingsScreen
import com.flex.ui.year.YearOverviewScreen
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Heute", Icons.Default.Home)
    data object Month : Screen("month", "Monat", Icons.Default.CalendarMonth)
    data object Planning : Screen("planning", "Planung", Icons.Default.EditCalendar)
    data object Quota : Screen("quota", "Quote", Icons.Default.Dashboard)
    data object Analytics : Screen("analytics", "Analytics", Icons.Default.BarChart)
    data object Year : Screen("year", "Jahr", Icons.Default.CalendarViewMonth)
    data object Settings : Screen("settings", "Einstellungen", Icons.Default.Settings)
    data object Backup : Screen("backup", "Datensicherung", Icons.Default.Settings)
    data object About : Screen("about", "Info", Icons.Default.Settings)
    data object GeofenceSettings : Screen("geofence_settings", "Geofencing", Icons.Default.LocationOn)
    data object WifiSettings : Screen("wifi_settings", "WLAN", Icons.Default.Wifi)
    data object CalendarSettings : Screen("calendar_settings", "Kalender", Icons.Default.CalendarToday)
    data object QuotaRules : Screen("quota_rules", "Quoten-Zeiträume", Icons.Default.DateRange)
    data object Onboarding : Screen("onboarding", "Onboarding", Icons.Default.Home)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Month,
    Screen.Planning,
    Screen.Year
)

val moreItems = listOf(
    Screen.Quota,
    Screen.Analytics,
    Screen.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlexNavGraph(
    onboardingCompleted: Boolean = true,
    onOnboardingFinished: () -> Unit = {},
    onOnboardingReset: () -> Unit = {}
) {
    val navController = rememberNavController()
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            moreItems.forEach { screen ->
                NavigationDrawerItem(
                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                    label = { Text(screen.title) },
                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                        }
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

    Scaffold(
        bottomBar = {
            NavigationBar {
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

                NavigationBarItem(
                    icon = { Icon(Icons.Default.Menu, contentDescription = "Mehr") },
                    label = { Text("Mehr", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) },
                    selected = currentDestination?.hierarchy?.any { dest ->
                        moreItems.any { it.route == dest.route }
                    } == true,
                    onClick = { showBottomSheet = true }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (onboardingCompleted) Screen.Home.route else Screen.Onboarding.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onFinish = {
                        onOnboardingFinished()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(onNavigateToAbout = { navController.navigate(Screen.About.route) })
            }
            composable(Screen.Month.route) { MonthScreen() }
            composable(Screen.Planning.route) { PlanningScreen() }
            composable(Screen.Quota.route) { QuotaScreen() }
            composable(Screen.Analytics.route) { AnalyticsScreen() }
            composable(Screen.Year.route) { YearOverviewScreen() }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToBackup = { navController.navigate(Screen.Backup.route) },
                    onNavigateToAbout = { navController.navigate(Screen.About.route) },
                    onNavigateToGeofence = { navController.navigate(Screen.GeofenceSettings.route) },
                    onNavigateToWifi = { navController.navigate(Screen.WifiSettings.route) },
                    onNavigateToCalendar = { navController.navigate(Screen.CalendarSettings.route) },
                    onNavigateToQuotaRules = { navController.navigate(Screen.QuotaRules.route) },
                    onShowOnboarding = {
                        onOnboardingReset()
                        navController.navigate(Screen.Onboarding.route)
                    }
                )
            }
            composable(Screen.Backup.route) {
                BackupScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.About.route) {
                AboutScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.GeofenceSettings.route) {
                GeofenceSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.WifiSettings.route) {
                WifiSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.CalendarSettings.route) {
                CalendarSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.QuotaRules.route) {
                QuotaRulesScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
