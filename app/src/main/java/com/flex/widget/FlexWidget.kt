package com.flex.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
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
        val isTall = height >= 160.dp

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(20.dp)
                .padding(if (isCompactHeight) 10.dp else 14.dp)
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

            Spacer(modifier = GlanceModifier.height(if (isCompactHeight) 4.dp else 8.dp))

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
                            .cornerRadius(6.dp)
                            .background(flexBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_widget_trending),
                                contentDescription = null,
                                modifier = GlanceModifier.size(11.dp),
                                colorFilter = ColorFilter.tint(flexColor)
                            )
                            Spacer(modifier = GlanceModifier.width(4.dp))
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
                        Spacer(modifier = GlanceModifier.height(3.dp))

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
                                .cornerRadius(6.dp)
                                .background(quotaBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    provider = ImageProvider(R.drawable.ic_widget_quota),
                                    contentDescription = null,
                                    modifier = GlanceModifier.size(11.dp),
                                    colorFilter = ColorFilter.tint(quotaColor)
                                )
                                Spacer(modifier = GlanceModifier.width(4.dp))
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
                val progress = if (state.targetMinutesToday > 0) {
                    (state.netMinutesToday.toFloat() / state.targetMinutesToday.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Spacer(modifier = GlanceModifier.height(10.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                    color = GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.surfaceVariant
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                val progressPercent = (progress * 100).toInt()
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

            Spacer(modifier = GlanceModifier.defaultWeight())

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
                .height(38.dp)
                .cornerRadius(12.dp)
                .background(backgroundColor)
                .clickable(onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = text,
                    modifier = GlanceModifier.size(16.dp),
                    colorFilter = ColorFilter.tint(contentColor)
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = text,
                    style = TextStyle(
                        color = contentColor,
                        fontSize = 12.sp,
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
