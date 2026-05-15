package com.painite.keyboard.ui.clipboard

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.painite.keyboard.data.ClipboardItem
import com.painite.keyboard.ui.theme.KeyboardTheme

@Composable
fun ClipboardPanel(
    theme: KeyboardTheme,
    items: List<ClipboardItem>,
    onPaste: (String) -> Unit,
    onPin: (ClipboardItem) -> Unit,
    onDelete: (ClipboardItem) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp, max = 260.dp)
            .background(theme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.rowbarBg)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Clipboard",
                color = theme.accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear, contentPadding = PaddingValues(4.dp)) {
                    Text("Clear", color = Color(0xFFFF5722), fontSize = 12.sp)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.keyTextColor, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = theme.keyTextColor.copy(alpha = 0.3f), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Clipboard is empty", color = theme.keyTextColor.copy(alpha = 0.4f), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    ClipboardItemRow(
                        item = item,
                        theme = theme,
                        onPaste = { onPaste(item.text) },
                        onPin = { onPin(item) },
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipboardItemRow(
    item: ClipboardItem,
    theme: KeyboardTheme,
    onPaste: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(theme.keyBackground)
            .border(
                0.5.dp,
                if (item.isPinned) theme.accentColor.copy(alpha = 0.5f) else theme.keyBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable { onPaste() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.isPinned) {
            Icon(
                Icons.Default.PushPin,
                contentDescription = "Pinned",
                tint = theme.accentColor,
                modifier = Modifier.size(12.dp).padding(end = 2.dp)
            )
        }
        Text(
            text = item.text,
            color = theme.keyTextColor,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(4.dp))
        // Pin button
        IconButton(onClick = onPin, modifier = Modifier.size(28.dp)) {
            Icon(
                if (item.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                contentDescription = if (item.isPinned) "Unpin" else "Pin",
                tint = if (item.isPinned) theme.accentColor else theme.keyTextColor.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp)
            )
        }
        // Delete button
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
        }
    }
}
