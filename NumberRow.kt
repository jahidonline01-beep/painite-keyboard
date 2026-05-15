package com.painite.keyboard.ui.keyboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.painite.keyboard.ui.theme.KeyboardTheme

private val NUMBERS = listOf("1","2","3","4","5","6","7","8","9","0")

@Composable
fun NumberRow(
    theme: KeyboardTheme,
    onKey: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        NUMBERS.forEach { num ->
            KeyButton(
                label = num,
                theme = theme,
                modifier = Modifier.weight(1f),
                onClick = { onKey(num) },
                height = 36.dp
            )
        }
    }
}
