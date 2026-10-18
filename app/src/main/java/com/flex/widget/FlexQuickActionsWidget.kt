package com.flex.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
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
import androidx.glance.layout.fillMaxHeight
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

enum class QuickActionType {
    OFFICE,
    HOME_OFFICE,
    PAUSE,
    RESUME,
    STOP,
    SWITCH_LOCATION
}

data class QuickActionButtonSpec(
    val type: QuickActionType,
    val iconRes: Int,
    val label: String,
    val isLead: Boolean,
    val isEnabled: Boolean
)

enum class QuickActionsLayoutMode {
    COMPACT_1X1,
    VERTICAL,
    GRID_2X2,
    HORIZONTAL
}

enum class QuickActionsHorizontalTier {
    COMPACT_PILL,
    FOUR_ACTIONS,
    FULL_BAR_WITH_TIME
}

enum class StatusChipColorRole {
    RUNNING,
    PAUSED,
    CLOCKED_OUT
}

enum class ActionButtonColorRole {
    PRIMARY,
    SECONDARY,
    SECONDARY_CONTAINER,
    TERTIARY,
    ERROR_CONTAINER,
    DISABLED,
    DEFAULT
}

data class CompactTileSpec(
    val iconRes: Int,
    val label: String,
    val colorRole: ActionButtonColorRole
)

class FlexQuickActionsWidget : GlanceAppWidget() {

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
                QuickActionsContent(state = state)
            }
        }
    }

    @Composable
    private fun QuickActionsContent(state: WidgetState) {
        val size = LocalSize.current
        val width = size.width
        val height = size.height

        val locationKey = ActionParameters.Key<String>(QuickLocationAction.KEY_LOCATION)
        val officeAction = actionRunCallback<QuickLocationAction>(
            actionParametersOf(locationKey to WorkLocation.OFFICE.name)
        )
        val homeAction = actionRunCallback<QuickLocationAction>(
            actionParametersOf(locationKey to WorkLocation.HOME_OFFICE.name)
        )
        val pauseAction = actionRunCallback<PauseAction>()
        val stopAction = actionRunCallback<ClockOutAction>()

        when (resolveLayoutMode(width, height)) {
            // 1. Ultra-compact 1x1 mode (Google Keep single-action style)
            QuickActionsLayoutMode.COMPACT_1X1 -> {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(18.dp)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val tileSpec = resolveCompactTileSpec(state)
                    val tileAction = when {
                        state.isClockRunning || state.isPaused -> pauseAction
                        else -> officeAction
                    }
                    val (bg, fg) = when (tileSpec.colorRole) {
                        ActionButtonColorRole.PRIMARY -> GlanceTheme.colors.primary to GlanceTheme.colors.onPrimary
                        ActionButtonColorRole.SECONDARY -> GlanceTheme.colors.secondary to GlanceTheme.colors.onSecondary
                        ActionButtonColorRole.TERTIARY -> GlanceTheme.colors.tertiary to GlanceTheme.colors.onTertiary
                        else -> GlanceTheme.colors.surfaceVariant to GlanceTheme.colors.onSurfaceVariant
                    }

                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .cornerRadius(14.dp)
                            .background(bg)
                            .clickable(tileAction)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val iconSize = if (height >= 56.dp) ACTION_BUTTON_ICON_SIZE else WIDE_ACTION_ICON_SIZE
                            Image(
                                provider = ImageProvider(tileSpec.iconRes),
                                contentDescription = tileSpec.label,
                                modifier = GlanceModifier.size(iconSize),
                                colorFilter = ColorFilter.tint(fg)
                            )
                            if (height >= 56.dp) {
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = tileSpec.label,
                                    style = TextStyle(
                                        color = fg,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 2. Vertical layout: Tall, narrow widget (e.g. 1x2, 1x3, 1x4)
            QuickActionsLayoutMode.VERTICAL -> {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(18.dp)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val buttonCount = resolveVerticalButtonCount(height)
                    val showLabels = height >= 200.dp && width >= 90.dp
                    val buttonSpecs = resolveActionButtons(buttonCount, state, isWideLabel = false)

                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        buttonSpecs.forEachIndexed { index, spec ->
                            if (index > 0) {
                                Spacer(modifier = GlanceModifier.height(5.dp))
                            }
                            RenderActionButton(
                                spec = spec,
                                showLabel = showLabels,
                                officeAction = officeAction,
                                homeAction = homeAction,
                                pauseAction = pauseAction,
                                stopAction = stopAction,
                                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                            )
                        }
                    }
                }
            }

            // 3. 2x2 Grid layout: Square widget (e.g. 2x2, 3x2, 3x3)
            QuickActionsLayoutMode.GRID_2X2 -> {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(18.dp)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val buttonSpecs = resolveActionButtons(4, state, isWideLabel = false)
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RenderActionButton(
                                spec = buttonSpecs[0],
                                showLabel = true,
                                officeAction = officeAction,
                                homeAction = homeAction,
                                pauseAction = pauseAction,
                                stopAction = stopAction,
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                            )
                            Spacer(modifier = GlanceModifier.width(5.dp))
                            RenderActionButton(
                                spec = buttonSpecs[1],
                                showLabel = true,
                                officeAction = officeAction,
                                homeAction = homeAction,
                                pauseAction = pauseAction,
                                stopAction = stopAction,
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                            )
                        }
                        Spacer(modifier = GlanceModifier.height(5.dp))
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RenderActionButton(
                                spec = buttonSpecs[2],
                                showLabel = true,
                                officeAction = officeAction,
                                homeAction = homeAction,
                                pauseAction = pauseAction,
                                stopAction = stopAction,
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                            )
                            Spacer(modifier = GlanceModifier.width(5.dp))
                            RenderActionButton(
                                spec = buttonSpecs[3],
                                showLabel = true,
                                officeAction = officeAction,
                                homeAction = homeAction,
                                pauseAction = pauseAction,
                                stopAction = stopAction,
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                            )
                        }
                    }
                }
            }

            // 4. Horizontal layout: Compact Pill (<260dp), 4-Action Bar (260-339dp) or Full Bar with Time (>=340dp)
            QuickActionsLayoutMode.HORIZONTAL -> {
                if (isCompactPill(width)) {
                    CompactPillContent(state = state, width = width)
                } else {
                    WideBarContent(
                        state = state,
                        width = width,
                        officeAction = officeAction,
                        homeAction = homeAction,
                        pauseAction = pauseAction,
                        stopAction = stopAction
                    )
                }
            }
        }
    }

    @Composable
    private fun CompactPillContent(
        state: WidgetState,
        width: Dp
    ) {
        val (iconRes, label) = resolvePillContent(state)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(GlanceTheme.colors.primaryContainer)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left area: App Icon + Status / FleX Label -> opens App
                Row(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .clickable(actionStartActivity<MainActivity>())
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(iconRes),
                        contentDescription = null,
                        modifier = GlanceModifier.size(ACTION_BUTTON_ICON_SIZE),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = label,
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimaryContainer,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = if (width < 150.dp) 2 else 1
                    )
                }

                // Right area: Circular button with "+" icon -> opens FleX Actions Dialog
                Box(
                    modifier = GlanceModifier
                        .size(44.dp)
                        .cornerRadius(100.dp)
                        .background(GlanceTheme.colors.primary)
                        .clickable(actionStartActivity<FlexQuickActionsDialogActivity>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_add),
                        contentDescription = "Aktionen öffnen",
                        modifier = GlanceModifier.size(ACTION_BUTTON_ICON_SIZE),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary)
                    )
                }
            }
        }
    }

    @Composable
    private fun WideBarContent(
        state: WidgetState,
        width: Dp,
        officeAction: Action,
        homeAction: Action,
        pauseAction: Action,
        stopAction: Action
    ) {
        val buttonSpecs = resolveActionButtons(4, state, isWideLabel = false)
        val showStatusChip = hasStatusChip(width)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tier 3: Widest row (>=340dp, 5 cells) - Digital stacked time badge with progress fill
                if (showStatusChip) {
                    StatusProgressTimeChip(state = state)
                    Spacer(modifier = GlanceModifier.width(5.dp))
                }

                // 4 Action Icons (Büro, Home-Office, Pause/Weiter, Stopp)
                // In 4-cell tier (260-339dp), "+" is removed and only these 4 icons are shown.
                // Button positions are 100% stable across all states.
                buttonSpecs.forEachIndexed { index, spec ->
                    if (index > 0) {
                        Spacer(modifier = GlanceModifier.width(5.dp))
                    }
                    WideActionTile(
                        spec = spec,
                        officeAction = officeAction,
                        homeAction = homeAction,
                        pauseAction = pauseAction,
                        stopAction = stopAction,
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                    )
                }
            }
        }
    }

    @Composable
    private fun StatusProgressTimeChip(
        state: WidgetState,
        modifier: GlanceModifier = GlanceModifier
    ) {
        val progress = resolveDayProgress(state.netMinutesToday, state.targetMinutesToday)
        val role = resolveStatusChipColorRole(state)
        val isProgressDominant = shouldInvertChipText(progress)

        val (trackColor, progressColor, chipFg) = when (role) {
            StatusChipColorRole.RUNNING -> Triple(
                GlanceTheme.colors.surface,
                GlanceTheme.colors.primary,
                if (isProgressDominant) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurface
            )
            StatusChipColorRole.PAUSED -> Triple(
                GlanceTheme.colors.surface,
                GlanceTheme.colors.tertiary,
                if (isProgressDominant) GlanceTheme.colors.onTertiary else GlanceTheme.colors.onSurface
            )
            StatusChipColorRole.CLOCKED_OUT -> Triple(
                GlanceTheme.colors.surface,
                GlanceTheme.colors.primaryContainer,
                if (isProgressDominant) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant
            )
        }

        Box(
            modifier = modifier
                .width(STATUS_CHIP_WIDTH)
                .fillMaxHeight()
                .cornerRadius(16.dp)
                .background(trackColor)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.CenterStart
        ) {
            if (progress > 0f) {
                val fillWidth = resolveProgressFillWidth(progress, STATUS_CHIP_WIDTH).coerceIn(8.dp, STATUS_CHIP_WIDTH)
                Box(
                    modifier = GlanceModifier
                        .width(fillWidth)
                        .fillMaxHeight()
                        .cornerRadius(16.dp)
                        .background(progressColor)
                ) {}
            }

            val size = LocalSize.current
            val isCompactHeight = size.height < 56.dp
            val timeFontSize = resolveTimeFontSize(size.height)
            val stdFontSize = resolveStdFontSize(size.height)
            val verticalPadding = if (isCompactHeight) 1.dp else 2.dp

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = verticalPadding),
                contentAlignment = Alignment.Center
            ) {
                val (hours, minutes) = formatDigitalTime(state.netMinutesToday)
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = hours,
                            style = TextStyle(
                                color = chipFg,
                                fontSize = timeFontSize,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = minutes,
                            style = TextStyle(
                                color = chipFg,
                                fontSize = timeFontSize,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(2.dp))
                    Text(
                        text = "Std",
                        style = TextStyle(
                            color = chipFg,
                            fontSize = stdFontSize,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }

    @Composable
    private fun WideActionTile(
        spec: QuickActionButtonSpec,
        officeAction: Action,
        homeAction: Action,
        pauseAction: Action,
        stopAction: Action,
        modifier: GlanceModifier
    ) {
        val action: Action? = if (!spec.isEnabled) {
            null
        } else {
            when (spec.type) {
                QuickActionType.OFFICE -> officeAction
                QuickActionType.HOME_OFFICE -> homeAction
                QuickActionType.PAUSE, QuickActionType.RESUME -> pauseAction
                QuickActionType.STOP -> stopAction
                QuickActionType.SWITCH_LOCATION -> officeAction
            }
        }

        val (bg, fg) = when (resolveButtonColorRole(spec)) {
            ActionButtonColorRole.PRIMARY -> GlanceTheme.colors.primary to GlanceTheme.colors.onPrimary
            ActionButtonColorRole.SECONDARY -> GlanceTheme.colors.secondary to GlanceTheme.colors.onSecondary
            ActionButtonColorRole.SECONDARY_CONTAINER -> GlanceTheme.colors.secondaryContainer to GlanceTheme.colors.onSecondaryContainer
            ActionButtonColorRole.TERTIARY -> GlanceTheme.colors.tertiary to GlanceTheme.colors.onTertiary
            ActionButtonColorRole.ERROR_CONTAINER -> GlanceTheme.colors.errorContainer to GlanceTheme.colors.onErrorContainer
            ActionButtonColorRole.DISABLED -> GlanceTheme.colors.surface to GlanceTheme.colors.outline
            ActionButtonColorRole.DEFAULT -> GlanceTheme.colors.surface to GlanceTheme.colors.onSurfaceVariant
        }

        val clickModifier = if (action != null && spec.isEnabled) GlanceModifier.clickable(action) else GlanceModifier

        Box(
            modifier = modifier
                .cornerRadius(16.dp)
                .background(bg)
                .then(clickModifier)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(spec.iconRes),
                contentDescription = spec.label,
                modifier = GlanceModifier.size(WIDE_ACTION_ICON_SIZE),
                colorFilter = ColorFilter.tint(fg)
            )
        }
    }

    @Composable
    private fun RenderActionButton(
        spec: QuickActionButtonSpec,
        showLabel: Boolean,
        officeAction: Action,
        homeAction: Action,
        pauseAction: Action,
        stopAction: Action,
        modifier: GlanceModifier
    ) {
        val action: Action? = if (!spec.isEnabled) {
            null
        } else {
            when (spec.type) {
                QuickActionType.OFFICE -> officeAction
                QuickActionType.HOME_OFFICE -> homeAction
                QuickActionType.PAUSE, QuickActionType.RESUME -> pauseAction
                QuickActionType.STOP -> stopAction
                QuickActionType.SWITCH_LOCATION -> officeAction
            }
        }

        val (bg, fg) = when (resolveButtonColorRole(spec)) {
            ActionButtonColorRole.PRIMARY -> GlanceTheme.colors.primary to GlanceTheme.colors.onPrimary
            ActionButtonColorRole.SECONDARY -> GlanceTheme.colors.secondary to GlanceTheme.colors.onSecondary
            ActionButtonColorRole.SECONDARY_CONTAINER -> GlanceTheme.colors.secondaryContainer to GlanceTheme.colors.onSecondaryContainer
            ActionButtonColorRole.TERTIARY -> GlanceTheme.colors.tertiary to GlanceTheme.colors.onTertiary
            ActionButtonColorRole.ERROR_CONTAINER -> GlanceTheme.colors.errorContainer to GlanceTheme.colors.onErrorContainer
            ActionButtonColorRole.DISABLED -> GlanceTheme.colors.surfaceVariant to GlanceTheme.colors.outline
            ActionButtonColorRole.DEFAULT -> GlanceTheme.colors.surfaceVariant to GlanceTheme.colors.onSurfaceVariant
        }

        ActionButton(
            iconRes = spec.iconRes,
            label = spec.label,
            showLabel = showLabel,
            onClick = action,
            backgroundColor = bg,
            contentColor = fg,
            modifier = modifier
        )
    }

    @Composable
    private fun ActionButton(
        iconRes: Int,
        label: String,
        showLabel: Boolean,
        onClick: Action?,
        backgroundColor: ColorProvider,
        contentColor: ColorProvider,
        modifier: GlanceModifier = GlanceModifier
    ) {
        val clickModifier = if (onClick != null) GlanceModifier.clickable(onClick) else GlanceModifier
        val iconSize = if (showLabel) ACTION_BUTTON_ICON_SIZE else WIDE_ACTION_ICON_SIZE
        Box(
            modifier = modifier
                .cornerRadius(12.dp)
                .background(backgroundColor)
                .then(clickModifier)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = label,
                    modifier = GlanceModifier.size(iconSize),
                    colorFilter = ColorFilter.tint(contentColor)
                )
                if (showLabel) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = label,
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
    }

    companion object {
        fun resolveCompactTileSpec(state: WidgetState): CompactTileSpec = when {
            state.isClockRunning -> CompactTileSpec(
                iconRes = R.drawable.ic_widget_pause,
                label = "Pause",
                colorRole = ActionButtonColorRole.PRIMARY
            )
            state.isPaused -> CompactTileSpec(
                iconRes = R.drawable.ic_widget_play,
                label = "Weiter",
                colorRole = ActionButtonColorRole.TERTIARY
            )
            else -> CompactTileSpec(
                iconRes = R.drawable.ic_widget_office,
                label = "Büro",
                colorRole = ActionButtonColorRole.DEFAULT
            )
        }

        fun resolvePillContent(state: WidgetState): Pair<Int, String> = when {
            state.isClockRunning -> {
                val isOffice = state.currentLocation == WorkLocation.OFFICE
                val icon = if (isOffice) R.drawable.ic_widget_office else R.drawable.ic_widget_home
                val text = if (isOffice) "Büro" else "Home-Office"
                icon to text
            }
            state.isPaused -> R.drawable.ic_widget_pause to "Pause"
            else -> R.drawable.ic_notification to "FleX"
        }

        fun resolveLayoutMode(width: Dp, height: Dp): QuickActionsLayoutMode = when {
            width < 120.dp && height < 110.dp -> QuickActionsLayoutMode.COMPACT_1X1
            width < 130.dp -> QuickActionsLayoutMode.VERTICAL
            height >= 110.dp -> QuickActionsLayoutMode.GRID_2X2
            else -> QuickActionsLayoutMode.HORIZONTAL
        }

        fun isCompactPill(width: Dp): Boolean = width < 260.dp

        fun resolveHorizontalTier(width: Dp): QuickActionsHorizontalTier = when {
            width < 260.dp -> QuickActionsHorizontalTier.COMPACT_PILL
            width < 340.dp -> QuickActionsHorizontalTier.FOUR_ACTIONS
            else -> QuickActionsHorizontalTier.FULL_BAR_WITH_TIME
        }

        fun hasPlusButton(width: Dp): Boolean = width < 260.dp

        fun hasStatusChip(width: Dp): Boolean = width >= 340.dp

        val STATUS_CHIP_WIDTH = 92.dp
        val WIDE_ACTION_ICON_SIZE = 26.dp
        val ACTION_BUTTON_ICON_SIZE = 24.dp

        fun formatDigitalTime(minutes: Long): Pair<String, String> {
            val safe = minutes.coerceAtLeast(0L)
            val h = safe / 60
            val m = safe % 60
            return "%02d".format(h) to "%02d".format(m)
        }

        fun formatDigitalTime(minutes: Int): Pair<String, String> = formatDigitalTime(minutes.toLong())

        fun resolveDayProgress(netMinutesToday: Long, targetMinutesToday: Long): Float {
            if (targetMinutesToday <= 0L) return 0f
            val safeNet = netMinutesToday.coerceAtLeast(0L)
            return (safeNet.toFloat() / targetMinutesToday.toFloat()).coerceIn(0f, 1f)
        }

        fun resolveDayProgress(netMinutesToday: Int, targetMinutesToday: Int): Float =
            resolveDayProgress(netMinutesToday.toLong(), targetMinutesToday.toLong())

        fun resolveProgressFillWidth(progress: Float, chipWidth: Dp = STATUS_CHIP_WIDTH): Dp {
            if (progress.isNaN()) return 0.dp
            val clamped = progress.coerceIn(0f, 1f)
            val safeWidth = chipWidth.value.coerceAtLeast(0f)
            return (safeWidth * clamped).dp
        }

        fun resolveStatusChipColorRole(state: WidgetState): StatusChipColorRole = when {
            state.isClockRunning -> StatusChipColorRole.RUNNING
            state.isPaused -> StatusChipColorRole.PAUSED
            else -> StatusChipColorRole.CLOCKED_OUT
        }

        fun shouldInvertChipText(progress: Float): Boolean = progress >= 0.5f

        fun formatWorkedHours(minutes: Long): String {
            val safe = minutes.coerceAtLeast(0L)
            val h = safe / 60
            val m = safe % 60
            return "%02d:%02d Std.".format(h, m)
        }

        fun formatWorkedHours(minutes: Int): String = formatWorkedHours(minutes.toLong())

        fun resolveHorizontalButtonCount(width: Dp): Int = when {
            width < 180.dp -> 2
            width < 260.dp -> 3
            else -> 4
        }

        fun resolveVerticalButtonCount(height: Dp): Int = when {
            height < 160.dp -> 2
            height < 230.dp -> 3
            else -> 4
        }

        fun resolveActionButtons(
            buttonCount: Int,
            state: WidgetState,
            isWideLabel: Boolean = false
        ): List<QuickActionButtonSpec> {
            val homeLabel = if (isWideLabel) "Home-Office" else "HO"
            val isOfficeActive = state.isClockRunning && !state.isPaused && state.currentLocation == WorkLocation.OFFICE
            val isHomeActive = state.isClockRunning && !state.isPaused && state.currentLocation == WorkLocation.HOME_OFFICE
            val isClockedOut = !state.isClockRunning && !state.isPaused

            val fullList = listOf(
                QuickActionButtonSpec(
                    type = QuickActionType.OFFICE,
                    iconRes = R.drawable.ic_widget_office,
                    label = "Büro",
                    isLead = isOfficeActive,
                    isEnabled = true
                ),
                QuickActionButtonSpec(
                    type = QuickActionType.HOME_OFFICE,
                    iconRes = R.drawable.ic_widget_home,
                    label = homeLabel,
                    isLead = isHomeActive,
                    isEnabled = true
                ),
                QuickActionButtonSpec(
                    type = if (state.isPaused) QuickActionType.RESUME else QuickActionType.PAUSE,
                    iconRes = if (state.isPaused) R.drawable.ic_widget_play else R.drawable.ic_widget_pause,
                    label = if (state.isPaused) "Weiter" else "Pause",
                    isLead = state.isPaused,
                    isEnabled = !isClockedOut
                ),
                QuickActionButtonSpec(
                    type = QuickActionType.STOP,
                    iconRes = R.drawable.ic_widget_stop,
                    label = "Stopp",
                    isLead = false,
                    isEnabled = !isClockedOut
                )
            )

            return fullList.take(buttonCount.coerceIn(1, 4))
        }

        fun resolveButtonColorRole(spec: QuickActionButtonSpec): ActionButtonColorRole = when {
            !spec.isEnabled -> ActionButtonColorRole.DISABLED
            spec.isLead -> when (spec.type) {
                QuickActionType.RESUME -> ActionButtonColorRole.TERTIARY
                QuickActionType.HOME_OFFICE -> ActionButtonColorRole.SECONDARY
                else -> ActionButtonColorRole.PRIMARY
            }
            spec.type == QuickActionType.STOP -> ActionButtonColorRole.ERROR_CONTAINER
            else -> ActionButtonColorRole.DEFAULT
        }

        fun resolveTimeFontSize(height: Dp): TextUnit = when {
            height < 56.dp -> 15.sp
            height < 70.dp -> 17.sp
            else -> 19.sp
        }

        fun resolveStdFontSize(height: Dp): TextUnit = when {
            height < 56.dp -> 10.sp
            height < 70.dp -> 11.sp
            else -> 12.sp
        }
    }
}
