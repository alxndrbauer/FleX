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
fun FlextimeScreen(status: WearStatus) {
    val green = Color(0xFF4CAF50)
    val red = Color(0xFFE53935)
    val flextimeColor = if (status.flextimeMinutes >= 0) green else red
    val overtimeColor = if (status.overtimeMinutes >= 0) green else red

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
                text = "Flexzeit",
                fontSize = 11.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Text(
                text = status.flextimeFormatted,
                fontSize = 28.sp,
                color = flextimeColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Überstunden",
                fontSize = 11.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Text(
                text = status.overtimeFormatted,
                fontSize = 28.sp,
                color = overtimeColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
