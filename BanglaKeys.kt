package com.painite.keyboard.ui.keyboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.painite.keyboard.ui.keyboard.ShiftState
import com.painite.keyboard.ui.theme.KeyboardTheme

// Bangla keyboard layout — Probhat-inspired
private val BN_ROW1       = listOf("ক","খ","গ","ঘ","ঙ","চ","ছ","জ","ঝ","ঞ")
private val BN_ROW2       = listOf("ট","ঠ","ড","ঢ","ণ","ত","থ","দ","ধ")
private val BN_ROW1_SHIFT = listOf("র","ল","শ","ষ","স","হ","ড়","ঢ়","য়","ৎ")
private val BN_ROW2_SHIFT = listOf("ং","ঃ","ঁ","া","ি","ী","ু","ূ","ে")

// Row 3 keys — passed back to BottomRow via the exported lists below
val BN_ROW3       = listOf("ন","প","ফ","ব","ভ","ম","য")
val BN_ROW3_SHIFT = listOf("ৈ","ো","ৌ","্","ৃ","ড","ঠ")

@Composable
fun BanglaKeys(
    theme: KeyboardTheme,
    shiftState: ShiftState,
    onKey: (String) -> Unit
) {
    val row1 = if (shiftState != ShiftState.OFF) BN_ROW1_SHIFT else BN_ROW1
    val row2 = if (shiftState != ShiftState.OFF) BN_ROW2_SHIFT else BN_ROW2

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            row1.forEach { key ->
                KeyButton(
                    label = key,
                    theme = theme,
                    modifier = Modifier.weight(1f),
                    onClick = { onKey(key) },
                    fontSize = 17.sp
                )
            }
        }

        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(0.5f))
            row2.forEach { key ->
                KeyButton(
                    label = key,
                    theme = theme,
                    modifier = Modifier.weight(1f),
                    onClick = { onKey(key) },
                    fontSize = 17.sp
                )
            }
            Spacer(Modifier.weight(0.5f))
        }
    }
}
