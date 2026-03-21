package com.flex.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

@Composable
fun QuotaDetailScreen(status: WearStatus) {
    val green = Color(0xFF4CAF50)
    val amber = Color(0xFFFFC107)
    val quotaColor = if (status.quotaMet) green else amber
    val remainingDays = (status.requiredOfficeDays - status.officeDays).coerceAtLeast(0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Büroquote",
                fontSize = 11.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Text(
                text = status.officePctFormatted,
                fontSize = 28.sp,
                color = quotaColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${status.officeDays}d von ${status.requiredOfficeDays}d",
                fontSize = 13.sp,
                color = quotaColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (status.quotaMet) "Quote erfüllt" else "$remainingDays Tage fehlen",
                fontSize = 11.sp,
                color = quotaColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
