package com.flex.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.flex.ui.about.AboutScreen
import com.flex.ui.analytics.AnalyticsScreen
import com.flex.ui.backup.BackupScreen
import com.flex.ui.dashboard.DashboardScreen
import com.flex.ui.home.HomeScreen
import com.flex.ui.month.MonthScreen
import com.flex.ui.onboarding.OnboardingScreen
import com.flex.ui.planning.PlanningScreen
import com.flex.ui.settings.CalendarSettingsScreen
import com.flex.ui.settings.GeofenceSettingsScreen
import com.flex.ui.settings.QuotaRulesScreen
import com.flex.ui.settings.SettingsScreen
import com.flex.ui.settings.WifiSettingsScreen
import com.flex.ui.settings.WorkTimeRulesScreen
import com.flex.ui.year.YearOverviewScreen
import kotlinx.coroutines.launch

val LocalFloatingNavVisible = compositionLocalOf { true }

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val outlinedIcon: ImageVector = icon
) {
    data object Home : Screen("home", "Heute", Icons.Default.Home, Icons.Outlined.Home)
    data object Month : Screen("month", "Monat", Icons.Default.CalendarMonth, Icons.Outlined.CalendarMonth)
    data object Planning : Screen("planning", "Planung", Icons.Default.EditCalendar, Icons.Outlined.EditCalendar)
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard, Icons.Outlined.Dashboard)
    data object Quota : Screen("quota", "Quote", Icons.Default.Dashboard, Icons.Outlined.Dashboard)
    data object Analytics : Screen("analytics", "Analytics", Icons.Default.BarChart)
    data object Year : Screen("year", "Jahr", Icons.Default.CalendarViewMonth)
    data object Settings : Screen("settings", "Einstellungen", Icons.Default.Settings)
    data object Backup : Screen("backup", "Datensicherung", Icons.Default.Settings)
    data object About : Screen("about", "Info", Icons.Default.Settings)
    data object GeofenceSettings : Screen("geofence_settings", "Geofencing", Icons.Default.LocationOn)
    data object WifiSettings : Screen("wifi_settings", "WLAN", Icons.Default.Wifi)
    data object CalendarSettings : Screen("calendar_settings", "Kalender", Icons.Default.CalendarToday)
    data object QuotaRules : Screen("quota_rules", "Quoten-Zeiträume", Icons.Default.DateRange)
    data object WorkTimeRules : Screen("work_time_rules", "Arbeitszeit-Zeiträume", Icons.Default.DateRange)
    data object Onboarding : Screen("onboarding", "Onboarding", Icons.Default.Home)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Month,
    Screen.Planning,
    Screen.Dashboard
)

val moreItems = listOf(
    Screen.Year,
    Screen.Analytics,
    Screen.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlexNavGraph(
    onboardingCompleted: Boolean = true,
    onOnboardingFinished: () -> Unit = {},
    onOnboardingReset: () -> Unit = {},
    initialRoute: String? = null
) {
    val navController = rememberNavController()
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }
    var isNavBarVisible by remember { mutableStateOf(true) }
    var cumulativeScroll by remember { mutableFloatStateOf(0f) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    LaunchedEffect(currentDestination?.route) {
        isNavBarVisible = true
        cumulativeScroll = 0f
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0f) { // Scrolling down (content moving up)
                    cumulativeScroll = (cumulativeScroll + delta).coerceIn(-150f, 50f)
                    if (cumulativeScroll <= -70f) {
                        isNavBarVisible = false
                    }
                } else if (delta > 0f) { // Scrolling up (content moving down)
                    cumulativeScroll = (cumulativeScroll + delta).coerceIn(-150f, 50f)
                    if (delta > 15f || cumulativeScroll >= 0f) {
                        isNavBarVisible = true
                        cumulativeScroll = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600

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

    @Composable
    fun AppNavHost(modifier: Modifier = Modifier) {
        NavHost(
            navController = navController,
            startDestination = if (onboardingCompleted) (initialRoute ?: Screen.Home.route) else Screen.Onboarding.route,
            modifier = modifier,
            enterTransition = {
                fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { 40 }
            },
            exitTransition = { fadeOut(tween(200, easing = FastOutSlowInEasing)) },
            popEnterTransition = {
                fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { -40 }
            },
            popExitTransition = { fadeOut(tween(200, easing = FastOutSlowInEasing)) }
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
                HomeScreen(
                    onNavigateToAbout = { navController.navigate(Screen.About.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Month.route) { MonthScreen() }
            composable(Screen.Planning.route) { PlanningScreen() }
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.Quota.route) { DashboardScreen() }
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
                    onNavigateToWorkTimeRules = { navController.navigate(Screen.WorkTimeRules.route) },
                    onShowOnboarding = {
                        onOnboardingReset()
                        navController.navigate(Screen.Onboarding.route)
                    }
                )
            }
            subScreenComposable(Screen.Backup.route) {
                BackupScreen(onNavigateBack = { navController.popBackStack() })
            }
            subScreenComposable(Screen.About.route) {
                AboutScreen(onNavigateBack = { navController.popBackStack() })
            }
            subScreenComposable(Screen.GeofenceSettings.route) {
                GeofenceSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            subScreenComposable(Screen.WifiSettings.route) {
                WifiSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            subScreenComposable(Screen.CalendarSettings.route) {
                CalendarSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            subScreenComposable(Screen.QuotaRules.route) {
                QuotaRulesScreen(onNavigateBack = { navController.popBackStack() })
            }
            subScreenComposable(Screen.WorkTimeRules.route) {
                WorkTimeRulesScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }

    val topLevelRoutes = remember {
        setOf(
            Screen.Home.route,
            Screen.Month.route,
            Screen.Planning.route,
            Screen.Dashboard.route,
            Screen.Quota.route,
            Screen.Analytics.route,
            Screen.Year.route,
            Screen.Settings.route
        )
    }
    val isTopLevelDestination = currentDestination?.route in topLevelRoutes

    CompositionLocalProvider(LocalFloatingNavVisible provides (isTopLevelDestination && isNavBarVisible)) {
        if (isExpandedScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail {
                    bottomNavItems.forEach { screen ->
                        val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationRailItem(
                            icon = {
                                AnimatedNavIcon(
                                    selected = isSelected,
                                    filledIcon = screen.icon,
                                    outlinedIcon = screen.outlinedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) },
                            selected = isSelected,
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

                    val isMoreSelected = currentDestination?.hierarchy?.any { dest ->
                        moreItems.any { it.route == dest.route }
                    } == true
                    NavigationRailItem(
                        icon = {
                            AnimatedNavIcon(
                                selected = isMoreSelected,
                                filledIcon = Icons.Default.Menu,
                                outlinedIcon = Icons.Outlined.Menu,
                                contentDescription = "Mehr"
                            )
                        },
                        label = { Text("Mehr", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) },
                        selected = isMoreSelected,
                        onClick = { showBottomSheet = true }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    AppNavHost()
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
            ) {
                AppNavHost()

                androidx.compose.animation.AnimatedVisibility(
                    visible = isTopLevelDestination && isNavBarVisible,
                    enter = androidx.compose.animation.slideInVertically(
                        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                    ) { it } + fadeIn(tween(200)),
                    exit = androidx.compose.animation.slideOutVertically(
                        tween(200, easing = FastOutSlowInEasing)
                    ) { it } + fadeOut(tween(180)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val isMoreSelected = currentDestination?.hierarchy?.any { dest ->
                        moreItems.any { it.route == dest.route }
                    } == true
                    FloatingNavigationBar(
                        items = bottomNavItems,
                        currentDestination = currentDestination,
                        moreSelected = isMoreSelected,
                        onItemClick = { screen ->
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onMoreClick = { showBottomSheet = true }
                    )
                }
            }
        }
    }
}

private fun NavGraphBuilder.subScreenComposable(
    route: String,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300)) },
        popExitTransition = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(150)) },
        content = content
    )
}

@Composable
private fun AnimatedNavIcon(
    selected: Boolean,
    filledIcon: ImageVector,
    outlinedIcon: ImageVector,
    contentDescription: String?
) {
    val scale = remember { Animatable(1f) }

    LaunchedEffect(selected) {
        if (selected) {
            scale.animateTo(
                targetValue = 0.82f,
                animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing)
            )
            scale.animateTo(
                targetValue = 1.22f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            scale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        } else {
            scale.snapTo(1f)
        }
    }

    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = selected,
            animationSpec = tween(durationMillis = 150),
            label = "nav_icon_crossfade"
        ) { isSelected ->
            Icon(
                imageVector = if (isSelected) filledIcon else outlinedIcon,
                contentDescription = contentDescription
            )
        }
    }
}
