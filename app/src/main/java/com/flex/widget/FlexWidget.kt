package com.flex.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.flex.MainActivity
import com.flex.domain.model.WorkLocation
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.abs

class FlexWidget : GlanceAppWidget() {

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
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(20.dp)
                .padding(14.dp)
        ) {
            // Header Row: Tap opens App
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FleX",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = GlanceModifier.defaultWeight())

                // Status Pill / Badge
                val (statusLabel, statusBg, statusColor) = when {
                    state.isClockRunning -> {
                        val loc = if (state.currentLocation == WorkLocation.OFFICE) "Büro" else "Home-Office"
                        Triple("🟢 $loc", GlanceTheme.colors.primaryContainer, GlanceTheme.colors.onPrimaryContainer)
                    }
                    state.isPaused -> {
                        Triple("⏸ Pause", GlanceTheme.colors.tertiaryContainer, GlanceTheme.colors.onTertiaryContainer)
                    }
                    else -> {
                        Triple("⚪ Ausgestempelt", GlanceTheme.colors.surfaceVariant, GlanceTheme.colors.onSurfaceVariant)
                    }
                }

                Box(
                    modifier = GlanceModifier
                        .cornerRadius(10.dp)
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
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

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Main Metrics Row: Today's Time + Flextime
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
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = "/ ${formatMinutes(state.targetMinutesToday)} Std.",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                // Badges: Flextime & Office Quota
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
                        Text(
                            text = "Flex $flextimeFormatted",
                            style = TextStyle(
                                color = flexColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

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
                        Text(
                            text = "🏢 $daysText · $quotaFormatted",
                            style = TextStyle(
                                color = quotaColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Action Buttons Row: Modern Material 3 Pills
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    state.isClockRunning -> {
                        // 1. Pause
                        WidgetActionButton(
                            text = "⏸ Pause",
                            onClick = actionRunCallback<PauseAction>(),
                            backgroundColor = GlanceTheme.colors.surfaceVariant,
                            contentColor = GlanceTheme.colors.onSurfaceVariant,
                            modifier = GlanceModifier.defaultWeight()
                        )

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        // 2. Standort wechseln
                        val switchText = if (state.currentLocation == WorkLocation.OFFICE) "🏠 HO" else "🏢 Büro"
                        WidgetActionButton(
                            text = "🔄 $switchText",
                            onClick = actionRunCallback<SwitchLocationAction>(),
                            backgroundColor = GlanceTheme.colors.secondaryContainer,
                            contentColor = GlanceTheme.colors.onSecondaryContainer,
                            modifier = GlanceModifier.defaultWeight()
                        )

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        // 3. Ausstempeln
                        WidgetActionButton(
                            text = "⏹ Stopp",
                            onClick = actionRunCallback<ClockOutAction>(),
                            backgroundColor = GlanceTheme.colors.errorContainer,
                            contentColor = GlanceTheme.colors.onErrorContainer,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                    state.isPaused -> {
                        // 1. Weiterarbeiten
                        WidgetActionButton(
                            text = "▶ Weiterarbeiten",
                            onClick = actionRunCallback<PauseAction>(),
                            backgroundColor = GlanceTheme.colors.primary,
                            contentColor = GlanceTheme.colors.onPrimary,
                            modifier = GlanceModifier.defaultWeight()
                        )

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        // 2. Feierabend
                        WidgetActionButton(
                            text = "⏹ Feierabend",
                            onClick = actionRunCallback<ClockOutAction>(),
                            backgroundColor = GlanceTheme.colors.errorContainer,
                            contentColor = GlanceTheme.colors.onErrorContainer,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                    else -> {
                        // Ausgestempelt
                        val officeParam = androidx.glance.action.ActionParameters.Key<String>(ClockInAction.KEY_LOCATION)

                        // 1. Büro starten
                        WidgetActionButton(
                            text = "🏢 Büro",
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
                            text = "🏠 Home-Office",
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

    @Composable
    private fun WidgetActionButton(
        text: String,
        onClick: Action,
        backgroundColor: ColorProvider,
        contentColor: ColorProvider,
        modifier: GlanceModifier = GlanceModifier
    ) {
        Box(
            modifier = modifier
                .height(40.dp)
                .cornerRadius(12.dp)
                .background(backgroundColor)
                .clickable(onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = TextStyle(
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
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
}
