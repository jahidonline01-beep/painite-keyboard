package com.painite.keyboard.ui.keyboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.painite.keyboard.ui.theme.KeyboardTheme

private val SYMBOLS_PAGE1 = listOf(
    listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"),
    listOf("-", "_", "=", "+", "[", "]", "{", "}", ";", ":"),
    listOf("'", "\"", ",", ".", "/", "\\", "|", "~", "`", "€")
)

private val SYMBOLS_PAGE2 = listOf(
    listOf("₹", "£", "¥", "©", "®", "™", "°", "•", "…", "—"),
    listOf("×", "÷", "±", "≠", "≤", "≥", "∞", "√", "π", "Σ"),
    listOf("←", "→", "↑", "↓", "↔", "♥", "★", "☆", "✓", "✗")
)

@Composable
fun SymbolKeys(
    theme: KeyboardTheme,
    isSecondary: Boolean,
    onKey: (String) -> Unit,
    onToggle: () -> Unit
) {
    val rows = if (isSecondary) SYMBOLS_PAGE2 else SYMBOLS_PAGE1
    val toggleLabel = if (isSecondary) "1/2" else "2/2"

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                row.forEach { sym ->
                    KeyButton(
                        label = sym,
                        theme = theme,
                        modifier = Modifier.weight(1f),
                        onClick = { onKey(sym) },
                        fontSize = 14.sp
                    )
                }
            }
        }
        // Toggle row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            KeyButton(
                label = toggleLabel,
                theme = theme,
                modifier = Modifier.weight(1.5f),
                onClick = onToggle,
                isSpecial = true,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
