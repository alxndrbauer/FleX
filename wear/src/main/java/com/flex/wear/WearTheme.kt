package com.flex.wear

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme

@Composable
fun WearTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
