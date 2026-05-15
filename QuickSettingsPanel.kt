package com.painite.keyboard.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.painite.keyboard.ui.theme.KeyboardTheme
import com.painite.keyboard.ui.theme.PainiteThemes
import com.painite.keyboard.ui.theme.PremiumPIcon

@Composable
fun QuickSettingsPanel(
    theme: KeyboardTheme,
    currentTheme: KeyboardTheme,
    showNumberRow: Boolean,
    vibrate: Boolean,
    onThemeChange: (String) -> Unit,
    onNumberRowToggle: () -> Unit,
    onRowbarReset: () -> Unit,
    onClearClipboard: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 260.dp)
            .background(theme.background)
    ) {
        // ── Header with P icon ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.rowbarBg)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPIcon(size = 24.dp, cornerRadius = 6.dp, fontSize = 13.sp)
                Text(
                    "Painite Settings",
                    color = theme.accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = theme.keyTextColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Theme picker
            Text(
                "Theme",
                color = theme.keyTextColor.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PainiteThemes.all) { t ->
                    ThemeChip(
                        t = t,
                        isSelected = t.id == currentTheme.id,
                        onClick = { onThemeChange(t.id) }
                    )
                }
            }

            HorizontalDivider(color = theme.keyBorder)

            // Toggles row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingToggleChip(
                    label = "Number Row",
                    icon = Icons.Default.Numbers,
                    enabled = showNumberRow,
                    theme = theme,
                    modifier = Modifier.weight(1f),
                    onClick = onNumberRowToggle
                )
                SettingToggleChip(
                    label = "Reset Rowbar",
                    icon = Icons.Default.Refresh,
                    enabled = false,
                    theme = theme,
                    modifier = Modifier.weight(1f),
                    onClick = onRowbarReset
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingToggleChip(
                    label = "Clear Clipboard",
                    icon = Icons.Default.DeleteSweep,
                    enabled = false,
                    theme = theme,
                    modifier = Modifier.weight(1f),
                    onClick = onClearClipboard
                )
                SettingToggleChip(
                    label = "Switch Keyboard",
                    icon = Icons.Default.Keyboard,
                    enabled = false,
                    theme = theme,
                    modifier = Modifier.weight(1f),
                    onClick = onSwitchKeyboard
                )
            }
        }
    }
}

@Composable
private fun ThemeChip(t: KeyboardTheme, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(t.gradient)
                .border(
                    width = if (isSelected) 2.5.dp else 0.dp,
                    color = Color.White,
                    shape = CircleShape
                )
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = t.displayName.take(8),
            color = if (isSelected) t.accentColor else Color.White.copy(alpha = 0.5f),
            fontSize = 8.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SettingToggleChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    theme: KeyboardTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) theme.accentColor.copy(alpha = 0.2f) else theme.keyBackground)
            .border(
                0.5.dp,
                if (enabled) theme.accentColor.copy(alpha = 0.6f) else theme.keyBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) theme.accentColor else theme.keyTextColor.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp)
        )
        Text(
            label,
            color = if (enabled) theme.accentColor else theme.keyTextColor.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
