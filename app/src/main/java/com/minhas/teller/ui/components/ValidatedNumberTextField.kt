package com.minhas.teller.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

@Composable
fun ValidatedNumberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isDecimal: Boolean = true,
    placeholder: String? = null,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
    isLightTheme: Boolean = false,
    height: androidx.compose.ui.unit.Dp = 56.dp
) {
    var errorText by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        ModernTextField(
            value = value,
            onValueChange = { newValue ->
                if (readOnly) return@ModernTextField
                if (newValue.isEmpty()) {
                    errorText = null
                    onValueChange("")
                    return@ModernTextField
                }

                val isValidChar = if (isDecimal) {
                    newValue.all { it.isDigit() || it == '.' }
                } else {
                    newValue.all { it.isDigit() }
                }

                if (!isValidChar) {
                    errorText = "Invalid character entered"
                } else {
                    if (isDecimal && newValue.count { it == '.' } > 1) {
                        errorText = "Multiple decimal points"
                    } else {
                        errorText = null
                        onValueChange(newValue)
                    }
                }
            },
            placeholder = label,
            modifier = Modifier.fillMaxWidth(),
            readOnly = readOnly,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number
            ),
            singleLine = singleLine,
            isLightTheme = isLightTheme,
            height = height
        )
        
        if (errorText != null) {
            Text(
                text = errorText!!, 
                color = MaterialTheme.colorScheme.error, 
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }
    }
}
