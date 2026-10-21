package com.flex.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.flex.MainActivity
import com.flex.R
import com.flex.domain.model.WorkLocation
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

class FlexWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            FlexWidgetEntryPoint::class.java
        )
        val dataProvider = entryPoint.flexWidgetDataProvider()
        val initialState = dataProvider.getWidgetState()

        provideContent {
            GlanceTheme {
                val state by dataProvider.getWidgetStateFlow().collectAsState(initial = initialState)
                FlexWidgetContent(state = state)
            }
        }
    }

    @Composable
    private fun FlexWidgetContent(state: WidgetState) {
        val size = LocalSize.current
        val width = size.width
        val height = size.height
        val isCompactHeight = height < 100.dp
        val isNarrow = width < 260.dp
        val isTall = height >= 140.dp
        val isHeroLayout = isTall && !isNarrow

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(20.dp)
                .padding(
                    horizontal = if (isHeroLayout && width >= 340.dp) 8.dp else 10.dp,
                    vertical = if (isCompactHeight) 6.dp else 8.dp
                )
        ) {
            // Header Row: Tap opens App
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isNarrow) {
                    Text(
                        text = "FleX",
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_notification),
                            contentDescription = null,
                            modifier = GlanceModifier.size(16.dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Text(
                            text = "FleX",
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        val todayDateFormatted = formatHeaderDate(LocalDate.now())
                        Text(
                            text = "• $todayDateFormatted",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                // Modern Status Pill with Vector Icon
                val (statusLabel, statusIconRes, statusBg, statusColor) = when {
                    state.isClockRunning -> {
                        val isOffice = state.currentLocation == WorkLocation.OFFICE
                        val loc = if (isOffice) "Büro" else "Home-Office"
                        val icon = if (isOffice) R.drawable.ic_widget_office else R.drawable.ic_widget_home
                        val bg = if (isOffice) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.secondaryContainer
                        val fg = if (isOffice) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSecondaryContainer
                        StatusPillData(loc, icon, bg, fg)
                    }
                    state.isPaused -> {
                        StatusPillData(
                            "Pause",
                            R.drawable.ic_widget_pause,
                            GlanceTheme.colors.tertiaryContainer,
                            GlanceTheme.colors.onTertiaryContainer
                        )
                    }
                    else -> {
                        StatusPillData(
                            "Ausgestempelt",
                            R.drawable.ic_widget_dot,
                            GlanceTheme.colors.surfaceVariant,
                            GlanceTheme.colors.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = GlanceModifier
                        .cornerRadius(10.dp)
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(statusIconRes),
                            contentDescription = null,
                            modifier = GlanceModifier.size(12.dp),
                            colorFilter = ColorFilter.tint(statusColor)
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            text = statusLabel,
                            style = TextStyle(
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(if (isCompactHeight) 3.dp else 4.dp))

            if (isHeroLayout) {
                // Hero 2-Column Section matching HomeScreen Hero
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dailyProgress = WidgetDailyProgress.calculate(
                        state.netMinutesToday,
                        state.targetMinutesToday
                    )
                    val progress = dailyProgress.progress
                    val progressPercent = dailyProgress.percent
                    val isGoalReached = state.targetMinutesToday > 0 && state.netMinutesToday >= state.targetMinutesToday

                    val netH = state.netMinutesToday / 60
                    val netM = state.netMinutesToday % 60
                    val netTimeFormatted = if (netH > 0) "${netH}h ${netM}m" else "${netM}min"

                    val targetH = state.targetMinutesToday / 60
                    val targetM = state.targetMinutesToday % 60
                    val targetFormatted = if (state.targetMinutesToday > 0) "von ${targetH}h ${targetM}min" else "Kein Soll"

                    val prognosisText = when {
                        state.targetMinutesToday <= 0 -> null
                        isGoalReached -> "$progressPercent % ✓"
                        state.isClockRunning && state.projectedEndTime != null -> {
                            "~%02d:%02d".format(state.projectedEndTime.hour, state.projectedEndTime.minute)
                        }
                        state.isClockRunning -> "Läuft"
                        else -> "$progressPercent %"
                    }

                    val context = LocalContext.current
                    val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    // Gauge colours follow GlanceTheme (same roles as CircularWorkGauge in the app)
                    val gaugeColors = GaugeColors(
                        ring = GlanceTheme.colors.primary.getColor(context).toArgb(),
                        overtimeBand = GlanceTheme.colors.tertiary.getColor(context).toArgb(),
                        center = GlanceTheme.colors.primaryContainer.getColor(context).toArgb(),
                        track = GlanceTheme.colors.surfaceVariant.getColor(context).toArgb(),
                        text = GlanceTheme.colors.onPrimaryContainer.getColor(context).toArgb(),
                        outline = GlanceTheme.colors.surface.getColor(context).toArgb(),
                        overtimeText = GlanceTheme.colors.onTertiary.getColor(context).toArgb(),
                    )
                    val gaugeSize = minOf(
                        136.dp,
                        maxOf(102.dp, width * 0.34f),
                        maxOf(102.dp, height - 80.dp)
                    )

                    // Overtime label on the band, same format as the in-app gauge (+2h 54m / +54m)
                    val overtimeMinutes = (state.netMinutesToday - state.targetMinutesToday).coerceAtLeast(0L)
                    val overtimeText = if (state.targetMinutesToday > 0 && overtimeMinutes > 0) {
                        val otH = overtimeMinutes / 60
                        val otM = overtimeMinutes % 60
                        if (otH > 0) "+${otH}h ${otM}m" else "+${otM}m"
                    } else {
                        null
                    }

                    val gaugeBitmap = remember(
                        netTimeFormatted,
                        targetFormatted,
                        prognosisText,
                        progress,
                        dailyProgress.overtimeProgress,
                        overtimeText,
                        state.isClockRunning,
                        isGoalReached,
                        isDark,
                        gaugeColors
                    ) {
                        CircularWorkGaugeBitmapRenderer.renderGauge(
                            netTimeText = netTimeFormatted,
                            targetText = targetFormatted,
                            prognosisText = prognosisText,
                            progress = progress,
                            overtimeProgress = dailyProgress.overtimeProgress,
                            isClockRunning = state.isClockRunning,
                            dailyGoalReached = isGoalReached,
                            isDark = isDark,
                            overtimeText = overtimeText,
                            colors = gaugeColors
                        )
                    }

                    // Left Column: The Circular Work Gauge Ring matching HomeScreen Hero
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(gaugeBitmap),
                            contentDescription = "Arbeitszeit Ring",
                            modifier = GlanceModifier.size(gaugeSize)
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(10.dp))

                    // Right Column: 3 Metric Cards matching HomeScreen HeroMetricCard in Image 2
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Gleitzeit (Saldo)
                        val bMin = state.flextimeBalanceMinutes
                        val bH = kotlin.math.abs(bMin) / 60
                        val bM = kotlin.math.abs(bMin) % 60
                        val bSign = if (bMin >= 0) "+" else "-"
                        val balanceText = "$bSign${bH}h ${bM}m"
                        val isPos = bMin >= 0

                        val flexContainer = if (isPos) {
                            dayNightColor(Color(0xFFF2E8FA), Color(0xFF331E48))
                        } else {
                            dayNightColor(Color(0xFFFFEBEE), Color(0xFF491414))
                        }
                        val flexIconBg = if (isPos) {
                            dayNightColor(Color(0xFFE4D0F8), Color(0xFF4C2F6E))
                        } else {
                            dayNightColor(Color(0xFFFFCDD2), Color(0xFF6B1B1B))
                        }
                        val flexIconTint = if (isPos) {
                            dayNightColor(Color(0xFF3A1D5C), Color(0xFFE8D5FF))
                        } else {
                            dayNightColor(Color(0xFFB71C1C), Color(0xFFFFB4AB))
                        }
                        val flexValueColor = if (isPos) {
                            GlanceTheme.colors.onSurface
                        } else {
                            dayNightColor(Color(0xFFB71C1C), Color(0xFFFFB4AB))
                        }

                        HeroMetricCardGlance(
                            iconRes = R.drawable.ic_widget_trending,
                            title = "Gleitzeit",
                            value = balanceText,
                            containerColor = flexContainer,
                            iconBgColor = flexIconBg,
                            iconTint = flexIconTint,
                            valueColor = flexValueColor,
                            modifier = GlanceModifier.defaultWeight()
                        )

                        Spacer(modifier = GlanceModifier.height(3.dp))

                        // 2. Pause
                        val breakText = if (state.breakMinutesToday > 0) "${state.breakMinutesToday} min" else "–"
                        HeroMetricCardGlance(
                            iconRes = R.drawable.ic_widget_coffee,
                            title = "Pause",
                            value = breakText,
                            containerColor = dayNightColor(Color(0xFFE1EDFD), Color(0xFF1A3358)),
                            iconBgColor = dayNightColor(Color(0xFFCCE0FF), Color(0xFF244A80)),
                            iconTint = dayNightColor(Color(0xFF193C6F), Color(0xFFD0E2FF)),
                            valueColor = GlanceTheme.colors.onSurface,
                            modifier = GlanceModifier.defaultWeight()
                        )

                        Spacer(modifier = GlanceModifier.height(3.dp))

                        // 3. Verbleibend
                        val remainingMin = (state.targetMinutesToday - state.netMinutesToday).coerceAtLeast(0)
                        val (remText, remIcon) = when {
                            state.targetMinutesToday <= 0 -> "–" to R.drawable.ic_widget_schedule
                            isGoalReached -> "Erreicht ✓" to R.drawable.ic_widget_check
                            else -> {
                                val rH = remainingMin / 60
                                val rM = remainingMin % 60
                                val text = if (rH > 0) "${rH}h ${rM}m" else "${rM}min"
                                text to R.drawable.ic_widget_hourglass
                            }
                        }
                        HeroMetricCardGlance(
                            iconRes = remIcon,
                            title = "Verbleibend",
                            value = remText,
                            containerColor = dayNightColor(Color(0xFFE7EDF8), Color(0xFF222F3E)),
                            iconBgColor = dayNightColor(Color(0xFFD4E2F4), Color(0xFF31455E)),
                            iconTint = dayNightColor(Color(0xFF253448), Color(0xFFD4E3F8)),
                            valueColor = GlanceTheme.colors.onSurface,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                }
            } else {
                // Main Metrics Row: Today's Time + Flextime & Quota Badges
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Large work time display
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = formatMinutes(state.netMinutesToday),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = if (isCompactHeight) 20.sp else 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            text = "/ ${formatMinutes(state.targetMinutesToday)} Std.",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    // Badges: Flextime & Office Quota with Vector Icons
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        val flextimeFormatted = formatFlextime(state.flextimeBalanceMinutes)
                        val (flexBg, flexColor) = if (state.flextimeBalanceMinutes >= 0) {
                            GlanceTheme.colors.secondaryContainer to GlanceTheme.colors.onSecondaryContainer
                        } else {
                            GlanceTheme.colors.errorContainer to GlanceTheme.colors.onErrorContainer
                        }

                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(8.dp)
                                .background(flexBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    provider = ImageProvider(R.drawable.ic_widget_trending),
                                    contentDescription = null,
                                    modifier = GlanceModifier.size(12.dp),
                                    colorFilter = ColorFilter.tint(flexColor)
                                )
                                Spacer(modifier = GlanceModifier.width(5.dp))
                                val badgeLabel = if (isNarrow) "Saldo" else "Gleitzeit"
                                Text(
                                    text = "$badgeLabel $flextimeFormatted",
                                    style = TextStyle(
                                        color = flexColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        if (!isCompactHeight) {
                            Spacer(modifier = GlanceModifier.height(5.dp))

                            val quotaFormatted = "%.0f%%".format(state.officeQuotaPercent)
                            val daysText = if (state.targetOfficeDays > 0) {
                                "${state.officeDays}/${state.targetOfficeDays} T."
                            } else {
                                "${state.officeDays} T."
                            }
                            val (quotaBg, quotaColor) = if (state.isQuotaMet) {
                                GlanceTheme.colors.primaryContainer to GlanceTheme.colors.onPrimaryContainer
                            } else {
                                GlanceTheme.colors.surfaceVariant to GlanceTheme.colors.onSurfaceVariant
                            }

                            Box(
                                modifier = GlanceModifier
                                    .cornerRadius(8.dp)
                                    .background(quotaBg)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        provider = ImageProvider(R.drawable.ic_widget_quota),
                                        contentDescription = null,
                                        modifier = GlanceModifier.size(12.dp),
                                        colorFilter = ColorFilter.tint(quotaColor)
                                    )
                                    Spacer(modifier = GlanceModifier.width(5.dp))
                                    Text(
                                        text = "$daysText · $quotaFormatted",
                                        style = TextStyle(
                                            color = quotaColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                if (isTall) {
                    val dailyProgress = WidgetDailyProgress.calculate(
                        state.netMinutesToday,
                        state.targetMinutesToday
                    )
                    val progress = dailyProgress.progress

                    Spacer(modifier = GlanceModifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                        color = GlanceTheme.colors.primary,
                        backgroundColor = GlanceTheme.colors.surfaceVariant
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    val progressPercent = dailyProgress.percent
                    val progressDetail = when {
                        state.targetMinutesToday <= 0 -> {
                            if (state.netMinutesToday > 0) {
                                "${formatMinutes(state.netMinutesToday)} Std. (kein Soll)"
                            } else {
                                "Kein Tagessoll"
                            }
                        }
                        state.netMinutesToday >= state.targetMinutesToday -> "$progressPercent% (Ziel erreicht)"
                        else -> {
                            val remaining = state.targetMinutesToday - state.netMinutesToday
                            val endSuffix = if (state.isClockRunning && state.projectedEndTime != null) {
                                " · Ende %02d:%02d".format(state.projectedEndTime.hour, state.projectedEndTime.minute)
                            } else {
                                ""
                            }
                            "$progressPercent% (noch ${formatMinutes(remaining)} Std.$endSuffix)"
                        }
                    }
                    val displayText = if (isNarrow) {
                        if (state.targetMinutesToday <= 0) {
                            if (state.netMinutesToday > 0) "${formatMinutes(state.netMinutesToday)} Std." else "Kein Soll"
                        } else {
                            "$progressPercent%"
                        }
                    } else {
                        progressDetail
                    }
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tagesfortschritt",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = displayText,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Action Buttons Row: Modern Material 3 Pills with Vector Icons
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    state.isClockRunning -> {
                        // 1. Pause
                        WidgetActionButton(
                            iconRes = R.drawable.ic_widget_pause,
                            text = "Pause",
                            onClick = actionRunCallback<PauseAction>(),
                            backgroundColor = GlanceTheme.colors.surfaceVariant,
                            contentColor = GlanceTheme.colors.onSurfaceVariant,
                            modifier = GlanceModifier.defaultWeight()
                        )

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        // 2. Standort wechseln
                        val isOfficeNow = state.currentLocation == WorkLocation.OFFICE
                        val switchText = if (isNarrow) {
                            if (isOfficeNow) "HO" else "Büro"
                        } else {
                            if (isOfficeNow) "Home-Office" else "Büro"
                        }
                        val switchIcon = if (isOfficeNow) R.drawable.ic_widget_home else R.drawable.ic_widget_office
                        WidgetActionButton(
                            iconRes = switchIcon,
                            text = switchText,
                            onClick = actionRunCallback<SwitchLocationAction>(),
                            backgroundColor = GlanceTheme.colors.secondaryContainer,
                            contentColor = GlanceTheme.colors.onSecondaryContainer,
                            modifier = GlanceModifier.defaultWeight()
                        )

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        // 3. Ausstempeln
                        WidgetActionButton(
                            iconRes = R.drawable.ic_widget_stop,
                            text = "Stopp",
                            onClick = actionRunCallback<ClockOutAction>(),
                            backgroundColor = GlanceTheme.colors.errorContainer,
                            contentColor = GlanceTheme.colors.onErrorContainer,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                    state.isPaused -> {
                        // 1. Weiterarbeiten
                        val resumeText = if (isNarrow) "Weiter" else "Weiterarbeiten"
                        WidgetActionButton(
                            iconRes = R.drawable.ic_widget_play,
                            text = resumeText,
                            onClick = actionRunCallback<PauseAction>(),
                            backgroundColor = GlanceTheme.colors.primary,
                            contentColor = GlanceTheme.colors.onPrimary,
                            modifier = GlanceModifier.defaultWeight()
                        )

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        // 2. Feierabend
                        val stopText = if (isNarrow) "Stopp" else "Feierabend"
                        WidgetActionButton(
                            iconRes = R.drawable.ic_widget_stop,
                            text = stopText,
                            onClick = actionRunCallback<ClockOutAction>(),
                            backgroundColor = GlanceTheme.colors.errorContainer,
                            contentColor = GlanceTheme.colors.onErrorContainer,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                    else -> {
                        // Ausgestempelt
                        val officeParam = ActionParameters.Key<String>(ClockInAction.KEY_LOCATION)
                        val hoText = if (isNarrow) "HO" else "Home-Office"

                        // 1. Büro starten
                        WidgetActionButton(
                            iconRes = R.drawable.ic_widget_office,
                            text = "Büro",
                            onClick = actionRunCallback<ClockInAction>(
                                actionParametersOf(officeParam to WorkLocation.OFFICE.name)
                            ),
                            backgroundColor = GlanceTheme.colors.primary,
                            contentColor = GlanceTheme.colors.onPrimary,
                            modifier = GlanceModifier.defaultWeight()
                        )

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        // 2. Home-Office starten
                        WidgetActionButton(
                            iconRes = R.drawable.ic_widget_home,
                            text = hoText,
                            onClick = actionRunCallback<ClockInAction>(
                                actionParametersOf(officeParam to WorkLocation.HOME_OFFICE.name)
                            ),
                            backgroundColor = GlanceTheme.colors.secondaryContainer,
                            contentColor = GlanceTheme.colors.onSecondaryContainer,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                }
            }
        }
    }

    private data class StatusPillData(
        val label: String,
        val iconRes: Int,
        val bg: ColorProvider,
        val color: ColorProvider
    )

    private fun dayNightColor(day: Color, night: Color): ColorProvider =
        androidx.glance.color.ColorProvider(day = day, night = night)

    @Composable
    private fun HeroMetricCardGlance(
        iconRes: Int,
        title: String,
        value: String,
        containerColor: ColorProvider,
        iconBgColor: ColorProvider,
        iconTint: ColorProvider,
        valueColor: ColorProvider = GlanceTheme.colors.onSurface,
        modifier: GlanceModifier = GlanceModifier
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .cornerRadius(12.dp)
                .background(containerColor)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular icon container matching HomeScreen Hero in Image 2
                Box(
                    modifier = GlanceModifier
                        .size(24.dp)
                        .cornerRadius(12.dp)
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(iconRes),
                        contentDescription = null,
                        modifier = GlanceModifier.size(13.dp),
                        colorFilter = ColorFilter.tint(iconTint)
                    )
                }

                Spacer(modifier = GlanceModifier.width(6.dp))

                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = value,
                        style = TextStyle(
                            color = valueColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }

    @Composable
    private fun WidgetActionButton(
        iconRes: Int,
        text: String,
        onClick: Action,
        backgroundColor: ColorProvider,
        contentColor: ColorProvider,
        modifier: GlanceModifier = GlanceModifier
    ) {
        Box(
            modifier = modifier
                .height(33.dp)
                .cornerRadius(11.dp)
                .background(backgroundColor)
                .clickable(onClick)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = text,
                    modifier = GlanceModifier.size(14.dp),
                    colorFilter = ColorFilter.tint(contentColor)
                )
                Spacer(modifier = GlanceModifier.width(5.dp))
                Text(
                    text = text,
                    style = TextStyle(
                        color = contentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }
    }

    private fun formatMinutes(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return "%02d:%02d".format(h, m)
    }

    private fun formatFlextime(minutes: Long): String {
        val sign = if (minutes >= 0) "+" else "-"
        val absMinutes = abs(minutes)
        val h = absMinutes / 60
        val m = absMinutes % 60
        return "%s%02d:%02d Std.".format(sign, h, m)
    }

    companion object {
        fun resolveBadgeLabel(isNarrow: Boolean): String = if (isNarrow) "Saldo" else "Gleitzeit"

        fun formatHeaderDate(date: LocalDate): String {
            return DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN).format(date)
        }
    }
}
