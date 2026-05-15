package com.painite.keyboard.ui.keyboard.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.painite.keyboard.ui.keyboard.Language
import com.painite.keyboard.ui.keyboard.ShiftState
import com.painite.keyboard.ui.theme.KeyboardTheme

private val EN_ROW3 = listOf("z","x","c","v","b","n","m")

@Composable
fun BottomRow(
    theme: KeyboardTheme,
    language: Language,
    shiftState: ShiftState,
    onShift: () -> Unit,
    onDelete: () -> Unit,
    onKey: (String) -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onEmoji: () -> Unit,
    onLanguageToggle: () -> Unit,
    onSymbolToggle: () -> Unit
) {
    val shiftBg by animateColorAsState(
        targetValue = when (shiftState) {
            ShiftState.OFF -> theme.keyBackground
            ShiftState.ON -> theme.accentColor.copy(alpha = 0.3f)
            ShiftState.LOCKED -> theme.accentColor
        },
        label = "shiftBg"
    )

    val row3 = when {
        language == Language.BN && shiftState != ShiftState.OFF -> BN_ROW3_SHIFT
        language == Language.BN -> BN_ROW3
        shiftState != ShiftState.OFF -> EN_ROW3.map { it.uppercase() }
        else -> EN_ROW3
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        // ── Row 3: [Shift] Z X C V B N M [Delete] ──────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shift
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(shiftBg)
                    .border(0.5.dp, theme.keyBorder, RoundedCornerShape(10.dp))
                    .clickable { onShift() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (shiftState) {
                        ShiftState.LOCKED -> Icons.Default.KeyboardCapslock
                        else -> Icons.Default.KeyboardArrowUp
                    },
                    contentDescription = "Shift",
                    tint = if (shiftState != ShiftState.OFF) theme.accentColor else theme.keyTextColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Letter keys (row 3)
            row3.forEach { key ->
                KeyButton(
                    label = key,
                    theme = theme,
                    modifier = Modifier.weight(1f),
                    onClick = { onKey(key) },
                    fontSize = if (language == Language.BN) 17.sp else 16.sp
                )
            }

            // Delete / Backspace
            KeyButton(
                label = "⌫",
                theme = theme,
                modifier = Modifier.weight(1.5f),
                onClick = onDelete,
                onLongClick = onDelete,
                isSpecial = true,
                fontSize = 18.sp,
                height = 44.dp
            )
        }

        // ── Row 4: [Emoji] [BN/EN] [Space] [Globe] [Enter] ──────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji
            KeyButton(
                label = "Emoji",
                theme = theme,
                modifier = Modifier.weight(1.8f),
                onClick = onEmoji,
                fontSize = 12.sp,
                bgColor = theme.specialKeyBg,
                height = 44.dp
            )

            // Language toggle BN/EN
            KeyButton(
                label = if (language == Language.BN) "BN/EN" else "EN/BN",
                theme = theme,
                modifier = Modifier.weight(1.8f),
                onClick = onLanguageToggle,
                fontSize = 12.sp,
                bgColor = theme.specialKeyBg,
                textColor = theme.accentSecondary,
                fontWeight = FontWeight.Bold,
                height = 44.dp
            )

            // Space bar
            KeyButton(
                label = "space",
                theme = theme,
                modifier = Modifier.weight(3.5f),
                onClick = onSpace,
                fontSize = 13.sp,
                bgColor = theme.specialKeyBg,
                height = 44.dp
            )

            // Globe / Symbol toggle
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.specialKeyBg)
                    .border(0.5.dp, theme.keyBorder, RoundedCornerShape(10.dp))
                    .clickable { onSymbolToggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Symbol/Letter toggle",
                    tint = theme.accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Enter — gradient
            GradientKeyButton(
                label = "Enter",
                theme = theme,
                modifier = Modifier.weight(2f),
                onClick = onEnter,
                height = 44.dp,
                fontSize = 13.sp
            )
        }
    }
}
