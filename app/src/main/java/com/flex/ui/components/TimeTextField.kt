package com.flex.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

fun formatTimeInput(input: TextFieldValue): TextFieldValue {
    val text = input.text
    if (":" in text) return input
    val digits = text.filter { it.isDigit() }
    val formatted = when {
        digits.length == 3 && digits.substring(0, 2).toInt() > 23 ->
            "0${digits[0]}:${digits.substring(1)}"
        digits.length == 4 ->
            "${digits.substring(0, 2)}:${digits.substring(2)}"
        else -> return input
    }
    return TextFieldValue(formatted, TextRange(formatted.length))
}
