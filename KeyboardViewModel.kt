package com.painite.keyboard.ui.keyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.painite.keyboard.data.ClipboardItem
import com.painite.keyboard.data.ClipboardRepository
import com.painite.keyboard.data.SettingsRepository
import com.painite.keyboard.ui.theme.KeyboardTheme
import com.painite.keyboard.ui.theme.PainiteThemes
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class KeyboardPanel { NONE, CLIPBOARD, EMOJI, TRANSLATE, SETTINGS }
enum class Language { EN, BN }
enum class ShiftState { OFF, ON, LOCKED }
enum class KeyboardMode { LETTERS, SYMBOLS, SYMBOLS2 }

data class KeyboardUiState(
    val language: Language = Language.EN,
    val shiftState: ShiftState = ShiftState.OFF,
    val mode: KeyboardMode = KeyboardMode.LETTERS,
    val activePanel: KeyboardPanel = KeyboardPanel.NONE,
    val theme: KeyboardTheme = PainiteThemes.IceWhite,
    val showNumberRow: Boolean = true,
    val rowbarOrder: List<String> = SettingsRepository.DEFAULT_ROWBAR_ORDER.split(","),
    val rowbarEnabled: Set<String> = SettingsRepository.DEFAULT_ROWBAR_ORDER.split(",").toSet(),
    val isVoiceActive: Boolean = false,
    val clipboardItems: List<ClipboardItem> = emptyList(),
    val vibrate: Boolean = true
)

class KeyboardViewModel(
    private val clipboardRepository: ClipboardRepository?,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KeyboardUiState())
    val uiState: StateFlow<KeyboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.theme,
                settingsRepository.showNumberRow,
                combine(settingsRepository.rowbarOrder, settingsRepository.rowbarEnabled) { order, enabled ->
                    order to enabled
                },
                settingsRepository.language,
                settingsRepository.vibrate
            ) { theme, showNum, rowbarSettings, lang, vibrate ->
                _uiState.update { state ->
                    state.copy(
                        theme = PainiteThemes.getById(theme),
                        showNumberRow = showNum,
                        rowbarOrder = sanitizeRowbarOrder(rowbarSettings.first),
                        rowbarEnabled = sanitizeRowbarEnabled(rowbarSettings.second),
                        language = if (lang == "BN") Language.BN else Language.EN,
                        vibrate = vibrate
                    )
                }
            }.collect()
        }

        clipboardRepository?.let { repository ->
            viewModelScope.launch {
                repository.allItems.catch {
                    emit(emptyList())
                }.collect { items ->
                    _uiState.update { it.copy(clipboardItems = items) }
                }
            }
        }
    }

    fun toggleLanguage() {
        val newLang = if (_uiState.value.language == Language.EN) Language.BN else Language.EN
        _uiState.update { it.copy(language = newLang) }
        viewModelScope.launch {
            settingsRepository.setLanguage(if (newLang == Language.BN) "BN" else "EN")
        }
    }

    fun toggleShift() {
        val newShift = when (_uiState.value.shiftState) {
            ShiftState.OFF -> ShiftState.ON
            ShiftState.ON -> ShiftState.LOCKED
            ShiftState.LOCKED -> ShiftState.OFF
        }
        _uiState.update { it.copy(shiftState = newShift) }
    }

    fun onLetterTyped() {
        if (_uiState.value.shiftState == ShiftState.ON) {
            _uiState.update { it.copy(shiftState = ShiftState.OFF) }
        }
    }

    fun setMode(mode: KeyboardMode) = _uiState.update { it.copy(mode = mode) }

    fun togglePanel(panel: KeyboardPanel) {
        val current = _uiState.value.activePanel
        _uiState.update { it.copy(activePanel = if (current == panel) KeyboardPanel.NONE else panel) }
    }

    fun closePanel() = _uiState.update { it.copy(activePanel = KeyboardPanel.NONE) }

    fun setVoiceActive(active: Boolean) = _uiState.update { it.copy(isVoiceActive = active) }

    fun setTheme(themeId: String) {
        viewModelScope.launch { settingsRepository.setTheme(themeId) }
    }

    fun setShowNumberRow(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowNumberRow(show) }
    }

    fun setVibrate(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVibrate(enabled) }
    }

    fun updateRowbarOrder(order: List<String>) {
        viewModelScope.launch { settingsRepository.setRowbarOrder(sanitizeRowbarOrder(order).joinToString(",")) }
    }

    fun resetRowbarOrder() {
        viewModelScope.launch { settingsRepository.resetRowbarOrder() }
    }

    fun setRowbarButtonEnabled(id: String, enabled: Boolean) {
        val defaults = SettingsRepository.DEFAULT_ROWBAR_ORDER.split(",")
        if (id !in defaults) return
        val next = _uiState.value.rowbarEnabled.toMutableSet().apply {
            if (enabled) add(id) else remove(id)
        }
        viewModelScope.launch { settingsRepository.setRowbarEnabled(next.joinToString(",")) }
    }

    fun resetRowbarButtons() {
        viewModelScope.launch { settingsRepository.resetRowbarButtons() }
    }

    fun addClipboardItem(text: String) {
        viewModelScope.launch { clipboardRepository?.addItem(text) }
    }

    fun deleteClipboardItem(item: ClipboardItem) {
        viewModelScope.launch { clipboardRepository?.deleteItem(item) }
    }

    fun togglePinClipboard(item: ClipboardItem) {
        viewModelScope.launch { clipboardRepository?.togglePin(item) }
    }

    fun clearUnpinnedClipboard() {
        viewModelScope.launch { clipboardRepository?.clearUnpinned() }
    }

    private fun sanitizeRowbarOrder(saved: String): List<String> {
        return sanitizeRowbarOrder(saved.split(","))
    }

    private fun sanitizeRowbarOrder(saved: List<String>): List<String> {
        val defaults = SettingsRepository.DEFAULT_ROWBAR_ORDER.split(",")
        val selected = saved
            .map { it.trim() }
            .filter { it in defaults }
            .distinct()
        return selected + defaults.filterNot { it in selected }
    }

    private fun sanitizeRowbarEnabled(saved: String): Set<String> {
        val defaults = SettingsRepository.DEFAULT_ROWBAR_ORDER.split(",")
        return saved.split(",")
            .map { it.trim() }
            .filter { it in defaults }
            .toSet()
    }

    class Factory(
        private val clipboardRepository: ClipboardRepository?,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return KeyboardViewModel(clipboardRepository, settingsRepository) as T
        }
    }
}
