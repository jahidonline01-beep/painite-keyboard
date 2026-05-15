package com.painite.keyboard.ui.keyboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.painite.keyboard.ui.clipboard.ClipboardPanel
import com.painite.keyboard.ui.keyboard.components.*
import com.painite.keyboard.ui.settings.QuickSettingsPanel
import com.painite.keyboard.ui.theme.KeyboardTheme
import com.painite.keyboard.ui.translate.TranslatePanel

// ── Panel overlay extracted to its own top-level composable ───────────────────
//
// WHY THIS IS SEPARATE:
// AnimatedVisibility has two overloads:
//   1. fun AnimatedVisibility(visible, ...)          — works anywhere
//   2. fun ColumnScope.AnimatedVisibility(visible, ...) — ColumnScope extension
//
// When AnimatedVisibility is called inside a Box that is itself inside a Column,
// Kotlin finds the outer Column's ColumnScope as an implicit receiver and tries
// overload #2 — then refuses to compile because the immediate receiver is BoxScope.
//
// Extracting the panel into a standalone @Composable function breaks the scope
// chain completely. There is no enclosing ColumnScope here, so Kotlin correctly
// resolves to overload #1.
@Composable
private fun PanelOverlay(
    uiState: KeyboardUiState,
    theme: KeyboardTheme,
    onCommitText: (String) -> Unit,
    onHaptic: () -> Unit,
    onHapticSpecial: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    viewModel: KeyboardViewModel
) {
    AnimatedVisibility(
        visible = uiState.activePanel != KeyboardPanel.NONE,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        when (uiState.activePanel) {
            KeyboardPanel.CLIPBOARD -> ClipboardPanel(
                theme = theme,
                items = uiState.clipboardItems,
                onPaste = { text ->
                    onHaptic(); onCommitText(text); viewModel.closePanel()
                },
                onPin = { item -> onHaptic(); viewModel.togglePinClipboard(item) },
                onDelete = { item -> onHaptic(); viewModel.deleteClipboardItem(item) },
                onClear = { onHapticSpecial(); viewModel.clearUnpinnedClipboard() },
                onClose = { viewModel.closePanel() }
            )
            KeyboardPanel.EMOJI -> EmojiPanel(
                theme = theme,
                onEmojiSelected = { emoji -> onHaptic(); onCommitText(emoji) },
                onClose = { viewModel.closePanel() }
            )
            KeyboardPanel.TRANSLATE -> TranslatePanel(
                theme = theme,
                onInsertText = { text ->
                    onHaptic(); onCommitText(text); viewModel.closePanel()
                },
                onClose = { viewModel.closePanel() }
            )
            KeyboardPanel.SETTINGS -> QuickSettingsPanel(
                theme = theme,
                currentTheme = uiState.theme,
                showNumberRow = uiState.showNumberRow,
                vibrate = uiState.vibrate,
                onThemeChange = { id -> viewModel.setTheme(id) },
                onNumberRowToggle = { viewModel.setShowNumberRow(!uiState.showNumberRow) },
                onRowbarReset = { viewModel.resetRowbarOrder() },
                onClearClipboard = { viewModel.clearUnpinnedClipboard() },
                onSwitchKeyboard = { onSwitchKeyboard() },
                onClose = { viewModel.closePanel() }
            )
            else -> {}
        }
    }
}

// ── Main keyboard screen ──────────────────────────────────────────────────────
@Composable
fun KeyboardScreen(
    viewModel: KeyboardViewModel,
    onCommitText: (String) -> Unit,
    onDelete: () -> Unit,
    onEnter: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onVoiceStart: (String) -> Unit,
    onVoiceStop: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    onHaptic: () -> Unit,
    onHapticSpecial: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val theme = uiState.theme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp)
            .background(theme.background)
            .padding(bottom = 4.dp)
    ) {
        // ── Rowbar ────────────────────────────────────────────────────────────
        RowBarSection(
            theme = theme,
            rowbarOrder = uiState.rowbarOrder,
            isVoiceActive = uiState.isVoiceActive,
            onVoiceClick = {
                onHapticSpecial()
                if (uiState.isVoiceActive) onVoiceStop()
                else onVoiceStart(if (uiState.language == Language.BN) "bn-BD" else "en-US")
            },
            onTranslateClick = {
                onHapticSpecial(); viewModel.togglePanel(KeyboardPanel.TRANSLATE)
            },
            onClipboardClearClick = {
                onHapticSpecial(); viewModel.clearUnpinnedClipboard()
            },
            onPasteClick = { onHapticSpecial(); onPaste() },
            onCopyClick = { onHapticSpecial(); onCopy() },
            onSelectAllClick = { onHapticSpecial(); onSelectAll() },
            onClipboardClick = {
                onHapticSpecial(); viewModel.togglePanel(KeyboardPanel.CLIPBOARD)
            },
            onCutClick = { onHapticSpecial(); onCut() },
            onNumberToggleClick = {
                onHapticSpecial(); viewModel.setShowNumberRow(!uiState.showNumberRow)
            },
            onEmojiClick = { onHapticSpecial(); viewModel.togglePanel(KeyboardPanel.EMOJI) },
            onSwitchKeyboardClick = { onHapticSpecial(); onSwitchKeyboard() },
            onSettingsClick = {
                onHapticSpecial(); viewModel.togglePanel(KeyboardPanel.SETTINGS)
            },
            onReorder = { newOrder -> viewModel.updateRowbarOrder(newOrder) }
        )

        // ── Keyboard body + panel overlay ─────────────────────────────────────
        //
        // ROOT CAUSE OF "KEYBOARD INVISIBLE" BUG:
        // The old code wrapped the keyboard inside AnimatedVisibility(visible=false)
        // when a panel was shown. Compose measures AnimatedVisibility(visible=false)
        // as height=0. Android's IME system reads the view height on the first frame
        // and allocates a zero-height keyboard window — the keyboard never appears.
        //
        // FIX: Use a Box so the keyboard Column is ALWAYS in the layout tree and
        // always has its real height. Panels are shown as overlays on top.
        // PanelOverlay is a separate composable to avoid the ColumnScope scope
        // leakage issue described above.
        Box(modifier = Modifier.fillMaxWidth()) {

            // Keyboard — always rendered so Android always sees a real height
            Column(modifier = Modifier.fillMaxWidth()) {

                // Number row — inside a Column so ColumnScope.AnimatedVisibility
                // is the correct overload here (no scope issue)
                AnimatedVisibility(
                    visible = uiState.showNumberRow && uiState.mode == KeyboardMode.LETTERS
                ) {
                    NumberRow(
                        theme = theme,
                        onKey = { k -> onHaptic(); onCommitText(k) }
                    )
                }

                // Letter / Symbol / Bangla key rows
                when (uiState.mode) {
                    KeyboardMode.LETTERS -> {
                        if (uiState.language == Language.BN) {
                            BanglaKeys(
                                theme = theme,
                                shiftState = uiState.shiftState,
                                onKey = { k ->
                                    onHaptic()
                                    onCommitText(k)
                                    viewModel.onLetterTyped()
                                }
                            )
                        } else {
                            LetterKeys(
                                theme = theme,
                                shiftState = uiState.shiftState,
                                onKey = { k ->
                                    onHaptic()
                                    val out = if (uiState.shiftState != ShiftState.OFF)
                                        k.uppercase() else k.lowercase()
                                    onCommitText(out)
                                    viewModel.onLetterTyped()
                                }
                            )
                        }
                    }
                    KeyboardMode.SYMBOLS -> SymbolKeys(
                        theme = theme,
                        isSecondary = false,
                        onKey = { k -> onHaptic(); onCommitText(k) },
                        onToggle = { viewModel.setMode(KeyboardMode.SYMBOLS2) }
                    )
                    KeyboardMode.SYMBOLS2 -> SymbolKeys(
                        theme = theme,
                        isSecondary = true,
                        onKey = { k -> onHaptic(); onCommitText(k) },
                        onToggle = { viewModel.setMode(KeyboardMode.SYMBOLS) }
                    )
                }

                // Bottom row — always visible
                BottomRow(
                    theme = theme,
                    language = uiState.language,
                    shiftState = uiState.shiftState,
                    onShift = { onHapticSpecial(); viewModel.toggleShift() },
                    onDelete = { onHaptic(); onDelete() },
                    onKey = { k ->
                        onHaptic()
                        val out = if (uiState.language == Language.EN &&
                            uiState.shiftState != ShiftState.OFF
                        ) k.uppercase() else k
                        onCommitText(out)
                        viewModel.onLetterTyped()
                    },
                    onSpace = { onHaptic(); onCommitText(" ") },
                    onEnter = { onHaptic(); onEnter() },
                    onEmoji = { onHapticSpecial(); viewModel.togglePanel(KeyboardPanel.EMOJI) },
                    onLanguageToggle = { onHapticSpecial(); viewModel.toggleLanguage() },
                    onSymbolToggle = {
                        onHapticSpecial()
                        viewModel.setMode(
                            if (uiState.mode == KeyboardMode.LETTERS)
                                KeyboardMode.SYMBOLS
                            else
                                KeyboardMode.LETTERS
                        )
                    }
                )
            }

            // Panel overlay — separate composable; see PanelOverlay above for why
            PanelOverlay(
                uiState = uiState,
                theme = theme,
                onCommitText = onCommitText,
                onHaptic = onHaptic,
                onHapticSpecial = onHapticSpecial,
                onSwitchKeyboard = onSwitchKeyboard,
                viewModel = viewModel
            )
        }
    }
}
