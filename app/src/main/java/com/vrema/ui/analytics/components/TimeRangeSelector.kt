package com.vrema.ui.analytics.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vrema.domain.model.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRangeSelector(
    selectedRange: TimeRange,
    onRangeChanged: (TimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember {
        mutableIntStateOf(
            when (selectedRange) {
                is TimeRange.Month -> 0
                is TimeRange.Year -> 1
                is TimeRange.Custom -> 2
            }
        )
    }
    var showDateRangePicker by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = {
                    selectedTab = 0
                    if (selectedRange !is TimeRange.Month) {
                        onRangeChanged(TimeRange.Month(YearMonth.now()))
                    }
                },
                text = { Text("Monat") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    if (selectedRange !is TimeRange.Year) {
                        onRangeChanged(TimeRange.Year(LocalDate.now().year))
                    }
                },
                text = { Text("Jahr") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = {
                    selectedTab = 2
                    if (selectedRange !is TimeRange.Custom) {
                        val now = LocalDate.now()
                        onRangeChanged(TimeRange.Custom(now.minusMonths(1), now))
                    }
                },
                text = { Text("Custom") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> MonthSelector(
                yearMonth = (selectedRange as? TimeRange.Month)?.yearMonth ?: YearMonth.now(),
                onYearMonthChanged = { onRangeChanged(TimeRange.Month(it)) }
            )
            1 -> YearSelector(
                year = (selectedRange as? TimeRange.Year)?.year ?: LocalDate.now().year,
                onYearChanged = { onRangeChanged(TimeRange.Year(it)) }
            )
            2 -> CustomSelector(
                customRange = selectedRange as? TimeRange.Custom,
                onShowPicker = { showDateRangePicker = true }
            )
        }
    }

    if (showDateRangePicker) {
        DateRangePickerDialog(
            onDismiss = { showDateRangePicker = false },
            onConfirm = { start, end ->
                if (start != null && end != null) {
                    onRangeChanged(TimeRange.Custom(start, end))
                }
                showDateRangePicker = false
            }
        )
    }
}

@Composable
private fun MonthSelector(
    yearMonth: YearMonth,
    onYearMonthChanged: (YearMonth) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onYearMonthChanged(yearMonth.minusMonths(1)) }) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Vorheriger Monat")
        }
        Text(
            text = yearMonth.format(
                DateTimeFormatter.ofPattern("MMM yyyy", Locale.GERMAN)
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = { onYearMonthChanged(yearMonth.plusMonths(1)) }) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Nächster Monat")
        }
    }
}

@Composable
private fun YearSelector(
    year: Int,
    onYearChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onYearChanged(year - 1) }) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Vorheriges Jahr")
        }
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = { onYearChanged(year + 1) }) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Nächstes Jahr")
        }
    }
}

@Composable
private fun CustomSelector(
    customRange: TimeRange.Custom?,
    onShowPicker: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onShowPicker) {
            Text("Zeitraum wählen")
        }

        if (customRange != null) {
            val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            Text(
                text = "${customRange.start.format(formatter)} - ${customRange.end.format(formatter)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?, LocalDate?) -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp)
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.weight(1f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Abbrechen")
                }
                TextButton(
                    onClick = {
                        val startMillis = dateRangePickerState.selectedStartDateMillis
                        val endMillis = dateRangePickerState.selectedEndDateMillis
                        val start = startMillis?.let {
                            Instant.ofEpochMilli(it)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        val end = endMillis?.let {
                            Instant.ofEpochMilli(it)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        onConfirm(start, end)
                    }
                ) {
                    Text("OK")
                }
            }
        }
    }
}
