package com.flex.ui.yearchange

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun YearChangeDialog(
    state: YearChangeState,
    onDismiss: () -> Unit,
    onConfirm: (carryOverDays: Int, annualDays: Int) -> Unit
) {
    var carryOverInput by remember(state.remainingVacationDays) {
        mutableStateOf(state.remainingVacationDays.toString())
    }
    var annualInput by remember(state.currentAnnualVacationDays) {
        mutableStateOf(state.currentAnnualVacationDays.toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jahr ${state.sourceYear} abschließen") },
        text = {
            Column {
                // Summary
                InfoRow("Urlaubstage genutzt", "${state.usedVacationDays} Tage")
                InfoRow("Verbleibender Urlaub", "${state.remainingVacationDays} Tage")
                InfoRow("Flextime-Saldo", formatMinutes(state.flextimeMinutes))

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Einstellungen für ${state.targetYear}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = carryOverInput,
                    onValueChange = { carryOverInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Resturlaub übertragen (Tage)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = annualInput,
                    onValueChange = { annualInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Jahresurlaub ${state.targetYear} (Tage)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Flextime-Saldo (${formatMinutes(state.flextimeMinutes)}) wird automatisch als Startsaldo übernommen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Jetzt nicht") }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val carryOver = carryOverInput.toIntOrNull() ?: state.remainingVacationDays
                    val annual = annualInput.toIntOrNull() ?: state.currentAnnualVacationDays
                    onConfirm(carryOver, annual)
                }
            ) { Text("Übernehmen") }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatMinutes(minutes: Long): String {
    val sign = if (minutes < 0) "-" else "+"
    val abs = abs(minutes)
    val h = abs / 60
    val m = abs % 60
    return if (m == 0L) "${sign}${h}h" else "${sign}${h}h ${m}min"
}
