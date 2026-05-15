package com.painite.keyboard.ui.keyboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.painite.keyboard.ui.keyboard.ShiftState
import com.painite.keyboard.ui.theme.KeyboardTheme

private val ROW1 = listOf("q","w","e","r","t","y","u","i","o","p")
private val ROW2 = listOf("a","s","d","f","g","h","j","k","l")

@Composable
fun LetterKeys(
    theme: KeyboardTheme,
    shiftState: ShiftState,
    onKey: (String) -> Unit
) {
    val upper = shiftState != ShiftState.OFF

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1 — 10 keys
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ROW1.forEach { key ->
                KeyButton(
                    label = if (upper) key.uppercase() else key,
                    theme = theme,
                    modifier = Modifier.weight(1f),
                    onClick = { onKey(key) }
                )
            }
        }

        // Row 2 — 9 keys centered
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(0.5f))
            ROW2.forEach { key ->
                KeyButton(
                    label = if (upper) key.uppercase() else key,
                    theme = theme,
                    modifier = Modifier.weight(1f),
                    onClick = { onKey(key) }
                )
            }
            Spacer(Modifier.weight(0.5f))
        }
    }
}
