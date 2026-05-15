package com.painite.keyboard.ui.keyboard.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.painite.keyboard.ui.theme.KeyboardTheme

data class RowBarButton(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val tint: Color
)

@Composable
fun RowBarSection(
    theme: KeyboardTheme,
    rowbarOrder: List<String>,
    isVoiceActive: Boolean,
    onVoiceClick: () -> Unit,
    onTranslateClick: () -> Unit,
    onClipboardClearClick: () -> Unit,
    onPasteClick: () -> Unit,
    onCopyClick: () -> Unit,
    onSelectAllClick: () -> Unit,
    onClipboardClick: () -> Unit,
    onCutClick: () -> Unit,
    onNumberToggleClick: () -> Unit,
    onEmojiClick: () -> Unit,
    onSwitchKeyboardClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onReorder: (List<String>) -> Unit
) {
    val buttonDefs = mapOf(
        "voice" to RowBarButton("voice", if (isVoiceActive) Icons.Default.MicOff else Icons.Default.Mic, "Voice", if (isVoiceActive) theme.accentColor else theme.keyTextColor),
        "translate" to RowBarButton("translate", Icons.Default.GTranslate, "Translate", Color(0xFFFFD700)),
        "clipboard_clear" to RowBarButton("clipboard_clear", Icons.Default.ContentPaste, "Clear", Color(0xFFFF5722)),
        "paste" to RowBarButton("paste", Icons.Default.ContentPaste, "Paste", Color(0xFF4CAF50)),
        "copy" to RowBarButton("copy", Icons.Default.ContentCopy, "Copy", theme.accentSecondary),
        "select_all" to RowBarButton("select_all", Icons.Default.SelectAll, "All", Color(0xFF9C27B0)),
        "clipboard" to RowBarButton("clipboard", Icons.Default.Assignment, "Clipboard", Color(0xFFFF9800)),
        "cut" to RowBarButton("cut", Icons.Default.ContentCut, "Cut", Color(0xFFF44336)),
        "number_toggle" to RowBarButton("number_toggle", Icons.Default.Numbers, "123", theme.accentColor),
        "emoji" to RowBarButton("emoji", Icons.Default.EmojiEmotions, "Emoji", Color(0xFFFFEB3B)),
        "switch_keyboard" to RowBarButton("switch_keyboard", Icons.Default.Keyboard, "Switch", Color(0xFF03A9F4))
    )

    var currentOrder by remember(rowbarOrder) { mutableStateOf(rowbarOrder) }
    var dragIndex by remember { mutableStateOf(-1) }
    var dragOverIndex by remember { mutableStateOf(-1) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.rowbarBg)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Settings (three-dot) button always on left
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(theme.keyBackground)
                .clickable { onSettingsClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "Settings",
                tint = theme.keyTextColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(4.dp))

        // Gradient divider line
        Box(
            modifier = Modifier
                .width(1.5.dp)
                .height(28.dp)
                .background(theme.gradient)
        )

        Spacer(Modifier.width(4.dp))

        // Scrollable reorderable rowbar
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            state = rememberLazyListState()
        ) {
            itemsIndexed(currentOrder, key = { _, id -> id }) { index, btnId ->
                val btn = buttonDefs[btnId] ?: return@itemsIndexed
                val isBeingDragged = dragIndex == index
                val isDropTarget = dragOverIndex == index

                RowBarItem(
                    button = btn,
                    theme = theme,
                    isHighlighted = isDropTarget && !isBeingDragged,
                    modifier = Modifier.pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragIndex = index },
                            onDragEnd = {
                                if (dragOverIndex >= 0 && dragIndex >= 0 && dragOverIndex != dragIndex) {
                                    val newOrder = currentOrder.toMutableList()
                                    val item = newOrder.removeAt(dragIndex)
                                    newOrder.add(dragOverIndex, item)
                                    currentOrder = newOrder
                                    onReorder(newOrder)
                                }
                                dragIndex = -1
                                dragOverIndex = -1
                            },
                            onDragCancel = {
                                dragIndex = -1
                                dragOverIndex = -1
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val x = change.position.x
                                dragOverIndex = (x / 52.dp.toPx()).toInt().coerceIn(0, currentOrder.size - 1)
                            }
                        )
                    },
                    onClick = {
                        when (btn.id) {
                            "voice" -> onVoiceClick()
                            "translate" -> onTranslateClick()
                            "clipboard_clear" -> onClipboardClearClick()
                            "paste" -> onPasteClick()
                            "copy" -> onCopyClick()
                            "select_all" -> onSelectAllClick()
                            "clipboard" -> onClipboardClick()
                            "cut" -> onCutClick()
                            "number_toggle" -> onNumberToggleClick()
                            "emoji" -> onEmojiClick()
                            "switch_keyboard" -> onSwitchKeyboardClick()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RowBarItem(
    button: RowBarButton,
    theme: KeyboardTheme,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isHighlighted) theme.accentColor.copy(alpha = 0.2f)
                else theme.keyBackground
            )
            .border(
                width = if (isHighlighted) 1.dp else 0.5.dp,
                color = if (isHighlighted) theme.accentColor else theme.keyBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = button.icon,
                contentDescription = button.label,
                tint = button.tint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = button.label,
                color = theme.keyTextColor.copy(alpha = 0.6f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
