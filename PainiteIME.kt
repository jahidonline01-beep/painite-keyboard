package com.painite.keyboard.ime

import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.ClipData
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.DragEvent
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.painite.keyboard.data.AppDatabase
import com.painite.keyboard.data.ClipboardItem
import com.painite.keyboard.data.ClipboardRepository
import com.painite.keyboard.data.SettingsRepository
import com.painite.keyboard.ui.settings.SettingsActivity
import com.painite.keyboard.utils.HapticManager
import com.painite.keyboard.utils.VoiceTypingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class PainiteIME : InputMethodService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var hapticManager: HapticManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var voiceManager: VoiceTypingManager
    private var clipboardRepository: ClipboardRepository? = null
    private var clipboardRepositoryStarted = false

    private var rootView: FixedHeightInputView? = null
    private var keyboardHeightPx: Int = 0
    private var language = Language.EN
    private var mode = Mode.LETTERS
    private var shift = false
    private var showNumberRow = true
    private var vibrate = true
    private var isVoiceActive = false
    private var voiceRequested = false
    private var toolbarOrder = DEFAULT_TOOLBAR_ORDER
    private var toolbarEnabled = DEFAULT_TOOLBAR_ORDER.toSet()
    private var toolbarScrollX = 0
    private var currentThemeId = "ice_white"
    private var theme = NativeTheme.iceWhite()
    private var deleteRepeatRunnable: Runnable? = null
    private var spaceRepeatRunnable: Runnable? = null
    private var voiceRestartRunnable: Runnable? = null
    private var voiceTarget = VoiceTarget.KEYBOARD
    private var draggedToolbarId: String? = null
    private var draggedClipboardId: Long? = null
    private var clipboardScrollY = 0
    private var nextClipboardId = 1L
    private var clipboardItems = mutableListOf<NativeClipboardItem>()
    private var translateSource = ""
    private var translateResult = ""
    private var translateDirection = TranslateDirection.BN_TO_EN
    private var translateCursor = 0
    private var translateInputFocused = false
    private var translateInputEditText: EditText? = null
    private var translateOutputTextView: TextView? = null
    private var suppressTranslateWatcher = false
    private var isTranslating = false
    private var pendingTranslateAfterCurrent = false
    private var hidingWindow = false
    private var translateRunnable: Runnable? = null
    private var abcBanglaBuffer = ""
    private var shiftLocked = false
    private var activeEditorAllowsKeyboard = false
    private var keyboardDismissedByUserUntil = 0L
    private var systemOverlayRecoveryUntil = 0L
    private var pendingHideRunnable: Runnable? = null
    private var hideRequestGeneration = 0
    private var showRequestGeneration = 0
    private var translateCandidatesView: LinearLayout? = null
    private var draggedEmoji: String? = null
    private var favoriteEmojis = mutableListOf<String>()
    private var clipboardListenerAdded = false
    private val primaryClipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        capturePrimaryClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        favoriteEmojis = PREMIUM_SIDE_EMOJIS.toMutableList()
        hapticManager = HapticManager(this)
        settingsRepository = SettingsRepository(settingsContextForCurrentUser())
        voiceManager = VoiceTypingManager(
            context = this,
            onResult = { text ->
                if (voiceTarget == VoiceTarget.TRANSLATE || mode == Mode.TRANSLATE) {
                    appendVoiceToTranslate(text)
                    translateResult = ""
                    translateCurrentText()
                } else {
                    commitVoiceText(text)
                }
                if (voiceRequested) restartVoiceListening()
            },
            onError = { message ->
                if (voiceRequested) {
                    restartVoiceListening()
                } else {
                    isVoiceActive = false
                    voiceManager.stopListening()
                    showToast(message)
                    rebuildKeys()
                }
            },
            onStateChange = { active ->
                val nextVoiceActive = active || voiceRequested
                val changed = nextVoiceActive != isVoiceActive
                isVoiceActive = nextVoiceActive
                if (!active && voiceRequested) restartVoiceListening()
                if (changed) rebuildKeys()
            }
        )
        setupClipboardRepositoryIfUnlocked()

        scope.launch {
            combine(
                settingsRepository.theme,
                settingsRepository.showNumberRow,
                settingsRepository.language,
                settingsRepository.vibrate,
                combine(settingsRepository.rowbarOrder, settingsRepository.rowbarEnabled) { order, enabled ->
                    order to enabled
                }
            ) { themeId, numberRow, lang, vib, rowbar ->
                SettingsSnapshot(themeId, numberRow, lang, vib, rowbar.first, rowbar.second)
            }.collect { snapshot ->
                currentThemeId = snapshot.themeId
                theme = NativeTheme.byId(snapshot.themeId)
                showNumberRow = snapshot.showNumberRow
                language = when (snapshot.language) {
                    "BN" -> Language.BN
                    "ABC_BN" -> Language.ABC_BN
                    else -> Language.EN
                }
                vibrate = snapshot.vibrate
                toolbarOrder = sanitizeToolbarOrder(snapshot.rowbarOrder)
                toolbarEnabled = sanitizeToolbarEnabled(snapshot.rowbarEnabled)
                rootView?.post {
                    try {
                        rebuildKeys()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onEvaluateInputViewShown(): Boolean = currentEditorAllowsKeyboard()

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        activeEditorAllowsKeyboard = currentEditorAllowsKeyboard()
        if (activeEditorAllowsKeyboard) {
            requestShowKeyboardNow()
        }
        return activeEditorAllowsKeyboard
    }

    override fun onCreateInputView(): View {
        keyboardHeightPx = resolveKeyboardHeightPx()
        return buildKeyboardView().also { rootView = it }
    }

    override fun onCreateCandidatesView(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.background)
            translateCandidatesView = this
        }
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (activeEditorAllowsKeyboard) {
            ensureKeyboardVisible()
        } else {
            rootView?.visibility = View.GONE
            setCandidatesViewShown(false)
            hideKeyboardView()
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        val canShow = currentEditorAllowsKeyboard()
        val now = System.currentTimeMillis()
        if (activeEditorAllowsKeyboard &&
            canShow &&
            now > keyboardDismissedByUserUntil &&
            now < systemOverlayRecoveryUntil &&
            inputConnectionAlive()
        ) {
            scheduleKeyboardRecovery()
        } else {
            // Minimize / home / any other hide: immediately hide the view so
            // the keyboard skin is not visible after the window is gone.
            rootView?.visibility = View.GONE
            setCandidatesViewShown(false)
            if (activeEditorAllowsKeyboard && now >= systemOverlayRecoveryUntil) {
                scheduleDeferredHide(delayMs = 45L, forceWindow = true)
            } else if (!activeEditorAllowsKeyboard || !canShow) {
                activeEditorAllowsKeyboard = false
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            keyboardDismissedByUserUntil = 0L
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        cancelDeferredHide()
        activeEditorAllowsKeyboard = shouldShowForEditor(attribute)
        setupClipboardRepositoryIfUnlocked()
        registerPrimaryClipboardListener()
        if (System.currentTimeMillis() < keyboardDismissedByUserUntil) {
            setCandidatesViewShown(false)
            hideKeyboardView(forceWindow = true)
            return
        }
        if (!activeEditorAllowsKeyboard) {
            setCandidatesViewShown(false)
            hideKeyboardView(forceWindow = true)
            return
        }
        rootView?.visibility = View.VISIBLE
        keyboardDismissedByUserUntil = 0L
        requestShowKeyboardNow()
        capturePrimaryClipboard()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        cancelDeferredHide()
        activeEditorAllowsKeyboard = shouldShowForEditor(info)
        if (System.currentTimeMillis() < keyboardDismissedByUserUntil) {
            setCandidatesViewShown(false)
            hideKeyboardView(forceWindow = true)
            return
        }
        if (!activeEditorAllowsKeyboard) {
            setCandidatesViewShown(false)
            hideKeyboardView(forceWindow = true)
            return
        }
        // Make the view visible immediately — before updateInputViewShown — so
        // the system reports the correct height on the very first frame.
        rootView?.visibility = View.VISIBLE
        keyboardDismissedByUserUntil = 0L
        updateInputViewShown()
        ensureKeyboardVisible()
        requestShowKeyboardNow()
        capturePrimaryClipboard()
    }

    override fun onViewClicked(focusChanged: Boolean) {
        super.onViewClicked(focusChanged)
        cancelDeferredHide()
        translateInputFocused = false
        if (currentEditorAllowsKeyboard()) {
            if (System.currentTimeMillis() < keyboardDismissedByUserUntil) return
            activeEditorAllowsKeyboard = true
            keyboardDismissedByUserUntil = 0L
            requestShowKeyboardNow()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        stopRepeatingDelete()
        stopRepeatingSpace()
        setCandidatesViewShown(false)
        systemOverlayRecoveryUntil = 0L
        // Use a short delay so that rapid field switches (back → inbox) can
        // cancel this hide before it fires. Smaller delay = less visible lag.
        val hideDelay = if (finishingInput) 30L else 50L
        scheduleDeferredHide(delayMs = hideDelay, forceWindow = finishingInput)
    }

    override fun onUnbindInput() {
        systemOverlayRecoveryUntil = 0L
        setCandidatesViewShown(false)
        scheduleDeferredHide(delayMs = 80L, forceWindow = true)
        super.onUnbindInput()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        voiceRequested = false
        voiceTarget = VoiceTarget.KEYBOARD
        voiceRestartRunnable?.let { mainHandler.removeCallbacks(it) }
        translateRunnable?.let { mainHandler.removeCallbacks(it) }
        voiceManager.stopListening()
        stopRepeatingDelete()
        stopRepeatingSpace()
        flushAbcBanglaBuffer()
        systemOverlayRecoveryUntil = 0L
        setCandidatesViewShown(false)
        scheduleDeferredHide(delayMs = 80L, forceWindow = true)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            activeEditorAllowsKeyboard = false
            systemOverlayRecoveryUntil = 0L
            keyboardDismissedByUserUntil = 0L
            setCandidatesViewShown(false)
            cancelDeferredHide()
            hideKeyboardView(forceWindow = true)
        }
    }

    override fun onDestroy() {
        voiceRequested = false
        voiceTarget = VoiceTarget.KEYBOARD
        voiceRestartRunnable?.let { mainHandler.removeCallbacks(it) }
        translateRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingHideRunnable?.let { mainHandler.removeCallbacks(it) }
        stopRepeatingDelete()
        stopRepeatingSpace()
        mainHandler.removeCallbacksAndMessages(null)
        unregisterPrimaryClipboardListener()
        voiceManager.destroy()
        scope.cancel()
        rootView = null
        translateCandidatesView = null
        keyboardHeightPx = 0
        super.onDestroy()
    }

    private fun buildKeyboardView(): FixedHeightInputView {
        val height = currentKeyboardHeightPx()
        return FixedHeightInputView(this, height).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), 0)
            setBackgroundColor(theme.background)
            minimumHeight = height
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            rebuildKeys(this)
        }
    }

    private fun rebuildKeys(parent: LinearLayout? = rootView) {
        val target = parent ?: return
        target.removeAllViews()
        target.setBackgroundColor(theme.background)
        target.setPadding(dp(4), dp(4), dp(4), 0)
        applyKeyboardHeight()

        addToolbar(target)
        if (mode == Mode.TRANSLATE) addTranslatePanel(target)

        when (mode) {
            Mode.LETTERS -> addLetterPanel(target, includeNumberRow = showNumberRow)
            Mode.SYMBOLS -> addSymbolsPanel(target)
            Mode.NUMBERS -> addNumberPanel(target)
            Mode.EMOJI -> addEmojiPanel(target)
            Mode.CLIPBOARD -> addClipboardPanel(target)
            Mode.SETTINGS -> addSettingsPanel(target)
            Mode.TRANSLATE -> addLetterPanel(target, includeNumberRow = false)
        }

        if (mode != Mode.CLIPBOARD && mode != Mode.SETTINGS) addBottomRow(target)
        updateTranslateCandidateView()
    }

    private fun addLetterPanel(parent: LinearLayout, includeNumberRow: Boolean) {
        if (includeNumberRow) addRow(parent, NUMBER_ROW, keyHeightDp = 33)
        if (language == Language.BN) {
            val rows = if (shift) BN_ROWS_SHIFT else BN_ROWS
            addRow(parent, rows[0], textSizeSp = 17f)
            addCenteredRow(parent, rows[1], textSizeSp = 17f)
            addBanglaThirdRow(parent)
        } else {
            val rows = if (shift) EN_ROWS.map { row -> row.map { it.uppercase() } } else EN_ROWS
            addRow(parent, rows[0])
            addCenteredRow(parent, rows[1])
            addActionLetterRow(parent, rows[2])
        }
    }

    private fun addToolbar(parent: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(3), dp(3), dp(3))
            setBackgroundColor(theme.rowbarBg)
        }

        row.addView(iconToolbarButton("ic_rowbar_settings", "Settings", 44, active = mode == Mode.SETTINGS) { togglePanelMode(Mode.SETTINGS) })

        val scroller = FastToolbarScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isSmoothScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setOnScrollChangeListener { _, scrollX, _, _, _ -> toolbarScrollX = scrollX }
            }
        }

        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        toolbarOrder.filter { it in toolbarEnabled }.forEach { id ->
            toolbarAction(id)?.let { action ->
                strip.addView(
                    iconToolbarButton(
                        iconName = action.iconName,
                        fallbackLabel = action.fallbackLabel,
                        widthDp = 45,
                        active = isToolbarActive(id),
                        onClick = action.onClick
                    ).apply {
                        tag = id
                        setOnLongClickListener {
                            draggedToolbarId = id
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                startDragAndDrop(null, View.DragShadowBuilder(this), id, 0)
                            } else {
                                @Suppress("DEPRECATION")
                                startDrag(null, View.DragShadowBuilder(this), id, 0)
                            }
                            true
                        }
                        setOnDragListener { _, event -> handleToolbarDrag(id, event) }
                    }
                )
            }
        }

        scroller.addView(strip)
        scroller.post { scroller.scrollTo(toolbarScrollX, 0) }
        row.addView(scroller)
        parent.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )
    }

    private fun toolbarAction(id: String): ToolbarAction? {
        return when (id) {
            "voice" -> ToolbarAction("ic_rowbar_voice", "Voice") { toggleVoice() }
            "translate" -> ToolbarAction("ic_rowbar_translate", "G↔") { togglePanelMode(Mode.TRANSLATE) }
            "clipboard_clear" -> ToolbarAction("ic_rowbar_clear", "⌧") { clearClipboard() }
            "paste" -> ToolbarAction("ic_rowbar_paste", "Paste") { performMenuAction(android.R.id.paste) }
            "copy" -> ToolbarAction("ic_rowbar_copy", "Copy") { performMenuAction(android.R.id.copy) }
            "select_all" -> ToolbarAction("ic_rowbar_select_all", "Select") { performMenuAction(android.R.id.selectAll) }
            "clipboard" -> ToolbarAction("ic_rowbar_clipboard", "Clipboard") { toggleClipboardPanel() }
            "cut" -> ToolbarAction("ic_rowbar_cut", "Cut") { performMenuAction(android.R.id.cut) }
            "number_toggle" -> ToolbarAction("ic_rowbar_number_pad", "123") { togglePanelMode(Mode.NUMBERS) }
            "emoji" -> ToolbarAction("ic_rowbar_emoji", "Emoji") { togglePanelMode(Mode.EMOJI) }
            "switch_keyboard" -> ToolbarAction("ic_rowbar_switch_keyboard", "Keyboard") { showInputMethodPicker() }
            else -> null
        }
    }

    private fun isToolbarActive(id: String): Boolean {
        return when (id) {
            "voice" -> isVoiceActive || voiceRequested
            "settings" -> mode == Mode.SETTINGS
            "translate" -> mode == Mode.TRANSLATE
            "clipboard" -> mode == Mode.CLIPBOARD
            "number_toggle" -> mode == Mode.NUMBERS || mode == Mode.SYMBOLS
            "emoji" -> mode == Mode.EMOJI
            else -> false
        }
    }

    private fun addCenteredRow(parent: LinearLayout, keys: List<String>, textSizeSp: Float = 16f) {
        val row = newRow()
        row.addView(spacer(0.45f))
        keys.forEach { key -> row.addView(keyButton(key, 1f, textSizeSp = textSizeSp)) }
        row.addView(spacer(0.45f))
        parent.addView(row)
    }

    private fun addActionLetterRow(parent: LinearLayout, keys: List<String>) {
        val row = newRow()
        val shiftButton = actionButton(
            label = if (shiftLocked) "CAPS" else if (shift) "SHIFT" else "shift",
            weight = 1.45f,
            active = shift || shiftLocked
        ) {
            if (shiftLocked) {
                shiftLocked = false
                shift = false
            } else {
                shift = !shift
            }
            rebuildKeys()
        }.apply {
            setOnLongClickListener {
                shift = true
                shiftLocked = true
                rebuildKeys()
                true
            }
        }
        row.addView(shiftButton)
        keys.forEach { key -> row.addView(keyButton(key, 1f)) }
        row.addView(deleteButton(1.45f))
        parent.addView(row)
    }

    private fun addBanglaThirdRow(parent: LinearLayout) {
        val row = newRow()
        row.addView(actionButton(if (shift) "মূল" else "আরও", 1.35f, active = shift) {
            shift = !shift
            shiftLocked = false
            rebuildKeys()
        })
        val keys = if (shift) BN_ROW3_SHIFT.take(8) else BN_ROW3
        keys.forEach { key -> row.addView(keyButton(key, 1f, textSizeSp = 17f)) }
        row.addView(deleteButton(1.35f))
        parent.addView(row)
        if (shift) {
            val extraRow = newRow()
            extraRow.addView(spacer(0.25f))
            BN_ROW3_SHIFT.drop(8).forEach { key ->
                extraRow.addView(keyButton(key, 1f, textSizeSp = 16f))
            }
            extraRow.addView(spacer(0.25f))
            parent.addView(extraRow)
        }
    }

    private fun addBottomRow(parent: LinearLayout) {
        val row = newRow()
        when (mode) {
            Mode.NUMBERS -> row.addView(actionButton("ABC", 1.25f) { toggleMode(Mode.LETTERS) })
            Mode.SYMBOLS -> row.addView(actionButton(language.shortLabel, 1.25f, active = true) { cycleLanguage() })
            Mode.EMOJI -> row.addView(actionButton(language.shortLabel, 1.25f, active = true) { cycleLanguage() })
            else -> row.addView(actionButton("?123", 1.25f, active = mode == Mode.NUMBERS) { togglePanelMode(Mode.NUMBERS) })
        }
        if (mode != Mode.SYMBOLS && mode != Mode.EMOJI) {
            row.addView(actionButton(language.shortLabel, 1.05f, active = false) { cycleLanguage() })
        }
        row.addView(spaceButton(if (mode == Mode.SYMBOLS || mode == Mode.EMOJI) 4.45f else 3.4f))
        row.addView(actionButton(if (mode == Mode.EMOJI) "ABC" else "😊", 1f) {
            togglePanelMode(Mode.EMOJI)
        })
        row.addView(gradientButton("Enter", 1.55f) { sendEnter() })
        parent.addView(row)
    }

    private fun addRow(
        parent: LinearLayout,
        keys: List<String>,
        keyHeightDp: Int = 42,
        textSizeSp: Float = 16f
    ) {
        val row = newRow()
        keys.forEach { key -> row.addView(keyButton(key, 1f, keyHeightDp, textSizeSp)) }
        parent.addView(row)
    }

    private fun addNumberPanel(parent: LinearLayout) {
        val rows = listOf(
            listOf("+", "1", "2", "3", "%"),
            listOf("-", "4", "5", "6", "↵"),
            listOf("*", "7", "8", "9", "⌫"),
            listOf("/", ",", "!?#", "0", "=", ".")
        )
        rows.forEach { keys ->
            val row = newRow()
            keys.forEach { key ->
                when (key) {
                    "⌫" -> row.addView(deleteButton(1f))
                    "!?#" -> row.addView(actionButton("!?#", 1f) { toggleMode(Mode.SYMBOLS) })
                    "↵" -> row.addView(iconActionButton("ic_rowbar_enter", "↵", 1f) { sendEnter() })
                    else -> row.addView(keyButton(key, 1f, textSizeSp = 19f))
                }
            }
            parent.addView(row)
        }
    }

    private fun addSymbolsPanel(parent: LinearLayout) {
        SYMBOL_ROWS.forEach { row -> addRow(parent, row, textSizeSp = 15f) }
        val actionRow = newRow()
        actionRow.addView(actionButton("123", 1.05f) { toggleMode(Mode.NUMBERS) })
        actionRow.addView(actionButton("ABC", 1.05f) { toggleMode(Mode.LETTERS) })
        listOf("৳", "₹", "~", "<", ">").forEach { symbol ->
            actionRow.addView(keyButton(symbol, 0.72f, textSizeSp = 15f))
        }
        actionRow.addView(deleteButton(1.05f))
        parent.addView(actionRow)
    }

    private fun addEmojiPanel(parent: LinearLayout) {
        val panelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).withMargins(2, 2, 2, 2)
        }
        val scroller = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
            background = rounded(theme.specialKeyBg, theme.keyBorder)
        }
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        val sideEmojis = favoriteEmojis.take(20)
        val mainEmojis = EMOJI_LIST.filterNot { it in sideEmojis.toSet() }
        mainEmojis.chunked(8).forEach { chunk ->
            val row = newRow()
            chunk.forEach { emoji -> row.addView(emojiButton(emoji, 1f, heightDp = 38)) }
            repeat(8 - chunk.size) { row.addView(spacer(1f)) }
            grid.addView(row)
        }
        scroller.addView(grid)
        panelRow.addView(scroller)
        val fixedDeleteColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.MATCH_PARENT)
                .withMargins(4, 0, 0, 0)
        }
        val sideScroller = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val sideColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        sideEmojis.forEach { emoji ->
            sideColumn.addView(emojiButton(emoji, 1f, heightDp = 38).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(38)
                ).withMargins(1, 1, 1, 1)
                setOnDragListener { _, event -> handleFavoriteEmojiDrop(emoji, event) }
            })
        }
        sideColumn.setOnDragListener { _, event -> handleFavoriteEmojiDrop(null, event) }
        sideScroller.addView(sideColumn)
        fixedDeleteColumn.addView(sideScroller)
        fixedDeleteColumn.addView(deleteButton(1f).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            ).withMargins(2, 2, 2, 2)
        })
        panelRow.addView(fixedDeleteColumn)
        parent.addView(panelRow)
    }

    private fun newRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).withMargins(0, 1, 0, 1)
        }
    }

    private fun keyButton(
        label: String,
        weight: Float,
        heightDp: Int = 42,
        textSizeSp: Float = 16f
    ): Button {
        return styledButton(
            label = label,
            weight = weight,
            heightDp = heightDp,
            textSizeSp = textSizeSp,
            bgColor = theme.keyBackground,
            textColor = theme.keyText,
            onClick = {
                commitText(label)
                if (shift && language == Language.EN && !shiftLocked) {
                    shift = false
                    rebuildKeys()
                }
            }
        ).apply {
            if (mode == Mode.LETTERS && language == Language.BN && label in BANGLA_LONG_PRESS_KEYS) {
                setOnLongClickListener {
                    shift = !shift
                    shiftLocked = false
                    rebuildKeys()
                    true
                }
            }
        }
    }

    private fun emojiButton(emoji: String, weight: Float, heightDp: Int): Button {
        return keyButton(emoji, weight, heightDp = heightDp, textSizeSp = 21f).apply {
            setOnLongClickListener {
                startEmojiDrag(this, emoji)
                true
            }
        }
    }

    private fun actionButton(label: String, weight: Float, active: Boolean = false, onClick: () -> Unit): Button {
        return styledButton(label, weight, 42, 12.5f, theme.specialKeyBg, theme.accent, onClick, active = active)
    }

    private fun deleteButton(weight: Float): View {
        return iconActionButton("ic_rowbar_delete", "⌫", weight) { deleteChar() }.apply {
            setOnLongClickListener {
                startRepeatingDelete()
                true
            }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    stopRepeatingDelete()
                }
                false
            }
        }
    }

    private fun spaceButton(weight: Float): Button {
        return styledButton(
            label = "space",
            weight = weight,
            heightDp = 42,
            textSizeSp = 13f,
            bgColor = theme.specialKeyBg,
            textColor = theme.keyText,
            onClick = { commitText(" ") }
        ).apply {
            setOnLongClickListener {
                startRepeatingSpace()
                true
            }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    stopRepeatingSpace()
                }
                false
            }
        }
    }

    private fun gradientButton(label: String, weight: Float, onClick: () -> Unit): Button {
        return styledButton(label, weight, 42, 13f, theme.accent, Color.WHITE, onClick, gradient = true)
    }

    private fun toolbarButton(label: String, widthDp: Int, onClick: () -> Unit): Button {
        return styledButton(
            label = label,
            weight = 0f,
            heightDp = 36,
            textSizeSp = if (label == "123") 18f else 24f,
            bgColor = theme.keyBackground,
            textColor = theme.accent,
            onClick = onClick,
            fixedWidthDp = widthDp
        )
    }

    private fun translateSwapButton(widthDp: Int, onClick: () -> Unit): Button {
        return styledButton(
            label = "\u21c4",
            weight = 0f,
            heightDp = 31,
            textSizeSp = 22f,
            bgColor = theme.keyBackground,
            textColor = theme.accent,
            onClick = onClick,
            fixedWidthDp = widthDp
        ).apply {
            layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(31)).withMargins(4, 1, 4, 1)
        }
    }

    private fun iconToolbarButton(
        iconName: String,
        fallbackLabel: String,
        widthDp: Int,
        active: Boolean = false,
        onClick: () -> Unit
    ): View {
        return iconButton(
            iconName = iconName,
            fallbackLabel = fallbackLabel,
            contentDescription = fallbackLabel,
            widthDp = widthDp,
            heightDp = 38,
            weight = null,
            bgColor = theme.keyBackground,
            active = active,
            onClick = onClick
        )
    }

    private fun iconActionButton(
        iconName: String,
        fallbackLabel: String,
        weight: Float,
        active: Boolean = false,
        onClick: () -> Unit
    ): View {
        return iconButton(
            iconName = iconName,
            fallbackLabel = fallbackLabel,
            contentDescription = fallbackLabel,
            widthDp = null,
            heightDp = 42,
            weight = weight,
            bgColor = theme.specialKeyBg,
            active = active,
            onClick = onClick
        )
    }

    private fun iconButton(
        iconName: String,
        fallbackLabel: String,
        contentDescription: String,
        widthDp: Int?,
        heightDp: Int,
        weight: Float?,
        bgColor: Int,
        active: Boolean,
        onClick: () -> Unit
    ): View {
        val iconResId = resources.getIdentifier(iconName, "drawable", packageName)
        if (iconResId == 0) {
            return if (weight == null) {
                toolbarButton(fallbackLabel, widthDp ?: 52, onClick)
            } else {
                actionButton(fallbackLabel, weight, active, onClick)
            }
        }
        return ImageButton(this).apply {
            setImageResource(iconResId)
            scaleType = ImageView.ScaleType.FIT_XY
            background = rippleBackground(if (active) activeRounded(bgColor) else rounded(bgColor, theme.keyBorder))
            this.contentDescription = contentDescription
            setPadding(0, 0, 0, 0)
            layoutParams = if (weight == null) {
                LinearLayout.LayoutParams(dp(widthDp ?: 52), dp(heightDp)).withMargins(2, 2, 2, 2)
            } else {
                LinearLayout.LayoutParams(0, dp(heightDp), weight).withMargins(2, 2, 2, 2)
            }
            setOnClickListener {
                if (::hapticManager.isInitialized) {
                    hapticManager.keyPress(vibrate)
                }
                onClick()
            }
        }
    }

    private fun styledButton(
        label: String,
        weight: Float,
        heightDp: Int,
        textSizeSp: Float,
        bgColor: Int,
        textColor: Int,
        onClick: () -> Unit,
        fixedWidthDp: Int? = null,
        gradient: Boolean = false,
        active: Boolean = false
    ): Button {
        return Button(this).apply {
            text = label
            setAllCaps(false)
            setIncludeFontPadding(false)
            gravity = Gravity.CENTER
            textSize = textSizeSp
            setTextColor(textColor)
            typeface = Typeface.DEFAULT_BOLD
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            background = rippleBackground(
                when {
                    gradient -> gradientRounded()
                    active -> activeRounded(bgColor)
                    else -> rounded(bgColor, theme.keyBorder)
                }
            )
            layoutParams = if (fixedWidthDp == null) {
                LinearLayout.LayoutParams(0, dp(heightDp), weight).withMargins(2, 2, 2, 2)
            } else {
                LinearLayout.LayoutParams(dp(fixedWidthDp), dp(heightDp)).withMargins(2, 2, 2, 2)
            }
            setOnClickListener {
                if (::hapticManager.isInitialized) {
                    hapticManager.keyPress(vibrate)
                }
                onClick()
            }
        }
    }

    private fun spacer(weight: Float): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(42), weight)
        }
    }

    private fun verticalSpacer(weight: Float): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                weight
            )
        }
    }

    private fun toggleMode(nextMode: Mode) {
        mode = nextMode
        shift = false
        shiftLocked = false
        rebuildKeys()
    }

    private fun cycleLanguage() {
        language = when (language) {
            Language.EN -> Language.BN
            Language.BN -> Language.ABC_BN
            Language.ABC_BN -> Language.EN
        }
        abcBanglaBuffer = ""
        shift = false
        shiftLocked = false
        mode = Mode.LETTERS
        scope.launch { settingsRepository.setLanguage(language.storageValue) }
        rebuildKeys()
    }

    private fun togglePanelMode(nextMode: Mode) {
        mode = when {
            mode == nextMode -> Mode.LETTERS
            else -> nextMode
        }
        shift = false
        shiftLocked = false
        if (mode == Mode.TRANSLATE) prepareTranslatePanel()
        if (mode != Mode.TRANSLATE) translateInputFocused = false
        if (mode == Mode.CLIPBOARD) capturePrimaryClipboard()
        rebuildKeys()
        if (mode == Mode.TRANSLATE && translateSource.isNotBlank()) translateCurrentText()
    }

    private fun toggleNumberRow() {
        showNumberRow = !showNumberRow
        scope.launch { settingsRepository.setShowNumberRow(showNumberRow) }
        rebuildKeys()
    }

    private fun toggleVoice() {
        if (voiceRequested || isVoiceActive) {
            voiceRequested = false
            isVoiceActive = false
            voiceTarget = VoiceTarget.KEYBOARD
            voiceManager.stopListening()
            rebuildKeys()
            return
        }

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            showToast("Microphone permission needed")
            return
        }

        voiceTarget = if (mode == Mode.TRANSLATE && translateInputFocused) VoiceTarget.TRANSLATE else VoiceTarget.KEYBOARD
        voiceRequested = true
        isVoiceActive = true
        rebuildKeys()
        startVoiceListening()
    }

    private fun startVoiceListening() {
        try {
            voiceManager.startListening(if (language == Language.EN) "en-US" else "bn-BD")
        } catch (_: Exception) {
            voiceRequested = false
            isVoiceActive = false
            showToast("Voice typing failed")
            rebuildKeys()
        }
    }

    private fun restartVoiceListening() {
        if (!voiceRequested) return
        voiceRestartRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            if (voiceRequested) startVoiceListening()
        }
        voiceRestartRunnable = runnable
        mainHandler.postDelayed(runnable, 45L)
    }

    private fun commitText(text: String) {
        when {
            mode == Mode.TRANSLATE && translateInputFocused -> {
                insertTranslateText(text)
            }
            language == Language.ABC_BN && mode == Mode.LETTERS -> commitAbcBanglaText(text)
            else -> commitToEditor(text)
        }
    }

    private fun commitVoiceText(rawText: String) {
        val text = rawText.trim()
        if (text.isBlank()) return
        flushAbcBanglaBuffer()
        val before = currentInputConnection?.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        val needsLeadingSpace = before.isNotEmpty() &&
            !before.last().isWhitespace() &&
            text.firstOrNull()?.let { !isClosingPunctuation(it) } != false
        val needsTrailingSpace = text.lastOrNull()?.let { it.isLetterOrDigit() } == true
        val committed = buildString {
            if (needsLeadingSpace) append(' ')
            append(text)
            if (needsTrailingSpace) append(' ')
        }
        commitToEditor(committed)
    }

    private fun appendVoiceToTranslate(rawText: String) {
        val text = rawText.trim()
        if (text.isBlank()) return
        val needsLeadingSpace = translateSource.isNotBlank() &&
            !translateSource.last().isWhitespace() &&
            text.firstOrNull()?.let { !isClosingPunctuation(it) } != false
        translateSource += buildString {
            if (needsLeadingSpace) append(' ')
            append(text)
            if (text.lastOrNull()?.let { it.isLetterOrDigit() } == true) append(' ')
        }
        translateCursor = translateSource.length
    }

    private fun isClosingPunctuation(char: Char): Boolean {
        return char in listOf('.', ',', '!', '?', ';', ':', ')', ']', '}', '।')
    }

    private fun commitToEditor(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun insertTranslateText(text: String) {
        val cursor = translateCursor.coerceIn(0, translateSource.length)
        translateSource = buildString {
            append(translateSource.substring(0, cursor))
            append(text)
            append(translateSource.substring(cursor))
        }
        translateCursor = (cursor + text.length).coerceIn(0, translateSource.length)
        translateResult = ""
        refreshTranslatePanel()
        scheduleTranslate()
    }

    private fun deleteChar() {
        if (mode == Mode.TRANSLATE && translateInputFocused) {
            val cursor = translateCursor.coerceIn(0, translateSource.length)
            if (cursor > 0 && translateSource.isNotEmpty()) {
                translateSource = translateSource.removeRange(cursor - 1, cursor)
                translateCursor = cursor - 1
                translateResult = ""
                refreshTranslatePanel()
                scheduleTranslate()
            }
            return
        }
        if (language == Language.ABC_BN && abcBanglaBuffer.isNotEmpty()) {
            abcBanglaBuffer = abcBanglaBuffer.dropLast(1)
            currentInputConnection?.let { ic ->
                if (abcBanglaBuffer.isEmpty()) {
                    ic.finishComposingText()
                } else {
                    ic.setComposingText(transliterateAbcBangla(abcBanglaBuffer), 1)
                }
            }
            return
        }
        currentInputConnection?.let { ic ->
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                ic.commitText("", 1)
            } else {
                ic.deleteSurroundingText(1, 0)
            }
        }
    }

    private fun sendEnter() {
        if (mode == Mode.TRANSLATE && translateInputFocused) {
            insertTranslateText("\n")
        } else {
            flushAbcBanglaBuffer()
            val editorInfo = currentInputEditorInfo
            val actionId = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
            val hasAction = actionId != EditorInfo.IME_ACTION_NONE &&
                actionId != EditorInfo.IME_ACTION_UNSPECIFIED &&
                editorInfo?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) != EditorInfo.IME_FLAG_NO_ENTER_ACTION
            if (hasAction) {
                currentInputConnection?.performEditorAction(actionId)
            } else {
                currentInputConnection?.commitText("\n", 1)
            }
        }
    }

    private fun performMenuAction(actionId: Int) {
        try {
            currentInputConnection?.performContextMenuAction(actionId)
            if (actionId == android.R.id.copy || actionId == android.R.id.cut) {
                mainHandler.postDelayed({ capturePrimaryClipboard() }, 180)
            }
        } catch (_: Exception) {}
    }

    private fun pastePrimaryClipboard() {
        val text = readClipboardText()
        if (text.isNullOrBlank()) {
            showToast("Clipboard is empty")
        } else {
            commitText(text)
        }
    }

    private fun commitAbcBanglaText(text: String) {
        if (text.length == 1 && text[0].isLetter()) {
            abcBanglaBuffer += text.lowercase()
            currentInputConnection?.setComposingText(transliterateAbcBangla(abcBanglaBuffer), 1)
            return
        }
        flushAbcBanglaBuffer()
        commitToEditor(text)
    }

    private fun flushAbcBanglaBuffer() {
        if (language == Language.ABC_BN && abcBanglaBuffer.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            abcBanglaBuffer = ""
        }
    }

    private fun transliterateAbcBangla(input: String): String {
        val vowels = mapOf(
            "a" to "আ", "i" to "ই", "u" to "উ", "e" to "এ", "o" to "ও"
        )
        val matras = mapOf(
            "a" to "", "i" to "ি", "u" to "ু", "e" to "ে", "o" to "ো"
        )
        val consonants = mapOf(
            "kh" to "খ", "gh" to "ঘ", "ch" to "চ", "jh" to "ঝ", "th" to "থ", "dh" to "ধ",
            "ph" to "ফ", "bh" to "ভ", "sh" to "শ", "ng" to "ঙ",
            "k" to "ক", "g" to "গ", "c" to "ক", "j" to "জ", "t" to "ত", "d" to "দ",
            "n" to "ন", "p" to "প", "f" to "ফ", "b" to "ব", "v" to "ভ", "m" to "ম",
            "r" to "র", "l" to "ল", "s" to "স", "h" to "হ", "y" to "য়", "w" to "ও"
        )
        val result = StringBuilder()
        var index = 0
        while (index < input.length) {
            val two = input.substring(index, (index + 2).coerceAtMost(input.length))
            val one = input.substring(index, index + 1)
            val consonantKey = if (two in consonants) two else one
            val consonant = consonants[consonantKey]
            if (consonant != null) {
                val nextIndex = index + consonantKey.length
                val nextVowel = input.substring(nextIndex, (nextIndex + 1).coerceAtMost(input.length))
                result.append(consonant)
                if (nextVowel in matras) {
                    result.append(matras[nextVowel])
                    index = nextIndex + 1
                } else {
                    index = nextIndex
                }
            } else {
                result.append(vowels[one] ?: one)
                index++
            }
        }
        return result.toString()
    }

    private fun readClipboardText(): String? {
        return try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip ?: return null
            if (clip.itemCount <= 0) return null
            clip.getItemAt(0)?.coerceToText(this)?.toString()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun clearClipboard() {
        try {
            clipboardRepository?.let { repository ->
                scope.launch(Dispatchers.IO) { repository.clearUnpinned() }
            } ?: run {
                clipboardItems = clipboardItems.filter { it.isPinned }.toMutableList()
            }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            showToast("Unpinned clipboard cleared")
            if (mode == Mode.CLIPBOARD) rebuildKeys()
        } catch (_: Exception) {
            showToast("Clipboard clear unavailable")
        }
    }

    private fun showInputMethodPicker() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    private fun openSettings() {
        try {
            voiceRequested = false
            isVoiceActive = false
            voiceManager.stopListening()
            stopRepeatingDelete()
            stopRepeatingSpace()
            activeEditorAllowsKeyboard = false
            systemOverlayRecoveryUntil = 0L
            keyboardDismissedByUserUntil = System.currentTimeMillis() + 1200L
            val intent = Intent(this, SettingsActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                val pendingIntent = PendingIntent.getActivity(this, 3307, intent, flags)
                try {
                    pendingIntent.send()
                } catch (_: Exception) {
                    try {
                        applicationContext.startActivity(intent)
                    } catch (_: Exception) {
                        showToast("Open Painite app for settings")
                    }
                }
            }
            setCandidatesViewShown(false)
            rootView?.visibility = View.GONE
            mainHandler.postDelayed({
                try {
                    setCandidatesViewShown(false)
                    rootView?.visibility = View.GONE
                    requestHideSelf(0)
                    hideWindow()
                } catch (_: Exception) {}
            }, 120L)
            mainHandler.postDelayed({
                try {
                    setCandidatesViewShown(false)
                    rootView?.visibility = View.GONE
                    requestHideSelf(0)
                } catch (_: Exception) {}
            }, 420L)
        } catch (_: Exception) {
            showToast("Open Painite app for settings")
        }
    }

    private fun toggleClipboardPanel() {
        capturePrimaryClipboard()
        togglePanelMode(Mode.CLIPBOARD)
    }

    private fun addClipboardPanel(parent: LinearLayout) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = rounded(theme.specialKeyBg, theme.keyBorder)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).withMargins(2, 2, 2, 2)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(panelText("Clipboard", 0f, 13f, theme.accent).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f)
        })
        panel.addView(header)

        val ordered = orderedClipboardItems()
        if (ordered.isEmpty()) {
            panel.addView(panelText("Clipboard is empty", 1f, 12f, theme.keyText))
        } else {
            val scroller = ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setOnScrollChangeListener { _, _, scrollY, _, _ ->
                        clipboardScrollY = scrollY
                    }
                }
            }
            val list = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            ordered.forEach { item ->
                list.addView(clipboardItemRow(item))
            }
            scroller.addView(list)
            scroller.post { scroller.scrollTo(0, clipboardScrollY.coerceAtLeast(0)) }
            panel.addView(scroller)
        }

        parent.addView(panel)
    }

    private fun addSettingsPanel(parent: LinearLayout) {
        val scroller = ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            background = rounded(theme.specialKeyBg, theme.keyBorder)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).withMargins(2, 2, 2, 2)
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
        }

        panel.addView(settingsTitleRow())
        panel.addView(settingsSectionLabel("Themes"))
        NativeTheme.themeIds.forEach { themeId ->
            panel.addView(settingsActionRow("${settingsThemeIcon(themeId)}  ${NativeTheme.displayName(themeId)}", themeId == currentThemeId) {
                scope.launch { settingsRepository.setTheme(themeId) }
            })
        }

        panel.addView(settingsSectionLabel("Keyboard Layout"))
        panel.addView(settingsActionRow("#  Number Row: ${if (showNumberRow) "ON" else "OFF"}", showNumberRow) {
            toggleNumberRow()
        })
        panel.addView(settingsActionRow("~  Vibration: ${if (vibrate) "ON" else "OFF"}", vibrate) {
            vibrate = !vibrate
            scope.launch { settingsRepository.setVibrate(vibrate) }
            rebuildKeys()
        })

        panel.addView(settingsSectionLabel("Rowbar Buttons"))
        DEFAULT_TOOLBAR_ORDER.forEach { id ->
            val label = toolbarAction(id)?.fallbackLabel ?: id
            val enabled = id in toolbarEnabled
            panel.addView(settingsActionRow("${settingsToolbarIcon(id)}  $label: ${if (enabled) "ON" else "OFF"}", enabled) {
                toolbarEnabled = toolbarEnabled.toMutableSet().apply {
                    if (enabled) remove(id) else add(id)
                }
                scope.launch { settingsRepository.setRowbarEnabled(toolbarEnabled.joinToString(",")) }
                rebuildKeys()
            })
        }

        panel.addView(settingsSectionLabel("About"))
        panel.addView(panelText("i  Painite Keyboard 1.0.25", 0f, 13f, theme.keyText).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)).withMargins(2, 2, 2, 2)
        })
        panel.addView(settingsActionRow(">  Telegram: @JAHID_1", false) {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, URL_TELEGRAM).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) {
                showToast("Telegram link unavailable")
            }
        })

        scroller.addView(panel)
        parent.addView(scroller)
    }

    private fun settingsTitleRow(): TextView {
        return panelText("Painite Settings", 0f, 16f, theme.accent).apply {
            gravity = Gravity.CENTER
            background = rounded(theme.keyBackground, theme.keyBorder)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).withMargins(2, 2, 2, 6)
        }
    }

    private fun settingsSectionLabel(text: String): TextView {
        return panelText(text.uppercase(), 0f, 11f, theme.accent).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
            background = rippleBackground(rounded(blendColors(theme.rowbarBg, theme.accent, 0.08f), blendColors(theme.keyBorder, theme.accent, 0.35f)))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)).withMargins(2, 6, 2, 2)
        }
    }

    private fun settingsActionRow(text: String, active: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(if (active) Color.WHITE else theme.keyText)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = rippleBackground(
                if (active) {
                    activeRounded(theme.accent)
                } else {
                    rounded(
                        blendColors(theme.keyBackground, theme.accent, 0.07f),
                        blendColors(theme.keyBorder, theme.accent, 0.22f)
                    )
                }
            )
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)).withMargins(2, 2, 2, 2)
            setOnClickListener {
                if (::hapticManager.isInitialized) hapticManager.keyPress(vibrate)
                onClick()
            }
        }
    }

    private fun settingsThemeIcon(id: String): String {
        return when (id) {
            "ice_white" -> "○"
            "neon_dark" -> "●"
            "neon_pink" -> "◆"
            "glass_ocean" -> "≈"
            "purple_galaxy" -> "✦"
            "gold_luxury" -> "$"
            "red_fire" -> "▲"
            "cyber_green" -> "+"
            "sunset_vibes" -> "◐"
            "painite_original" -> "P"
            else -> "•"
        }
    }

    private fun settingsToolbarIcon(id: String): String {
        return when (id) {
            "voice" -> "mic"
            "translate" -> "A↔"
            "clipboard_clear" -> "del"
            "paste" -> "in"
            "copy" -> "cp"
            "select_all" -> "all"
            "clipboard" -> "clip"
            "cut" -> "cut"
            "number_toggle" -> "123"
            "emoji" -> ":)"
            "switch_keyboard" -> "kbd"
            else -> "*"
        }
    }

    private fun clipboardItemRow(item: NativeClipboardItem): LinearLayout {
        var downX = 0f
        var downY = 0f
        var deletedBySwipe = false
        var dragStarted = false
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(1), 0, dp(1))
            setOnDragListener { _, event -> handleClipboardDrag(item.id, event) }
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        deletedBySwipe = false
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val distanceX = event.x - downX
                        val distanceY = kotlin.math.abs(event.y - downY)
                        if (!deletedBySwipe && distanceX > dp(44) && distanceX > distanceY * 1.4f) {
                            deletedBySwipe = true
                            if (item.isPinned) showToast("Pinned item is protected") else deleteClipboardItem(item)
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!deletedBySwipe && event.x - downX > dp(44)) {
                            if (item.isPinned) showToast("Pinned item is protected") else deleteClipboardItem(item)
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
        }
        val preview = (if (item.isPinned) "★ " else "") + item.text.replace("\n", " ").take(42)
        row.addView(styledButton(preview, 1f, 34, 11f, theme.keyBackground, theme.keyText, {
            commitToEditor(item.text)
        }).apply {
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        dragStarted = false
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragStarted && kotlin.math.abs(event.y - downY) > dp(22)) {
                            dragStarted = true
                            startClipboardDrag(view, item.id)
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (event.x - downX > dp(96)) {
                            if (item.isPinned) showToast("Pinned item is protected") else deleteClipboardItem(item)
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            setOnLongClickListener {
                togglePinClipboardItem(item)
                true
            }
        })
        return row
    }

    private fun startClipboardDrag(view: View, id: Long) {
        draggedClipboardId = id
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(null, View.DragShadowBuilder(view), id, 0)
        } else {
            @Suppress("DEPRECATION")
            view.startDrag(null, View.DragShadowBuilder(view), id, 0)
        }
    }

    private fun togglePinClipboardItem(item: NativeClipboardItem) {
        val repository = clipboardRepository
        val source = item.source
        if (repository != null && source != null) {
            scope.launch(Dispatchers.IO) { repository.togglePin(source) }
        } else {
            clipboardItems = clipboardItems.map {
                if (it.id == item.id) it.copy(isPinned = !it.isPinned, sortIndex = nextClipboardSortIndex()) else it
            }.toMutableList()
            rebuildKeys()
        }
    }

    private fun capturePrimaryClipboard() {
        val text = readClipboardText()?.trim()?.takeIf { it.isNotBlank() } ?: return
        if (clipboardItems.any { it.text == text }) return
        clipboardRepository?.let { repository ->
            scope.launch(Dispatchers.IO) { repository.addItem(text) }
            return
        }
        clipboardItems.add(
            NativeClipboardItem(
                id = nextClipboardId++,
                text = text,
                sortIndex = nextClipboardSortIndex()
            )
        )
    }

    private fun orderedClipboardItems(): List<NativeClipboardItem> {
        return clipboardItems.sortedWith(
            compareBy<NativeClipboardItem> { if (it.isPinned) 1 else 0 }
                .thenBy { it.sortIndex }
                .thenByDescending { it.timestamp }
        )
    }

    private fun nextClipboardSortIndex(): Int {
        return (clipboardItems.maxOfOrNull { it.sortIndex } ?: -1) + 1
    }

    private fun handleClipboardDrag(targetId: Long, event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return true
            DragEvent.ACTION_DROP -> {
                val draggedId = draggedClipboardId ?: return true
                moveClipboardItem(draggedId, targetId)
                draggedClipboardId = null
                return true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                draggedClipboardId = null
                return true
            }
        }
        return true
    }

    private fun handleClipboardDeleteDrag(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return true
            DragEvent.ACTION_DROP -> {
                val draggedId = draggedClipboardId ?: return true
                val item = clipboardItems.firstOrNull { it.id == draggedId } ?: return true
                if (item.isPinned) {
                    showToast("Pinned item is protected")
                } else {
                    deleteClipboardItem(item)
                }
                draggedClipboardId = null
                return true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                draggedClipboardId = null
                return true
            }
        }
        return true
    }

    private fun deleteClipboardItem(item: NativeClipboardItem) {
        val repository = clipboardRepository
        if (repository != null && item.source != null) {
            scope.launch(Dispatchers.IO) { repository.deleteById(item.id) }
        } else {
            clipboardItems = clipboardItems.filterNot { it.id == item.id }.toMutableList()
            rebuildKeys()
        }
    }

    private fun moveClipboardItem(draggedId: Long, targetId: Long) {
        if (draggedId == targetId) return
        val ordered = orderedClipboardItems().toMutableList()
        val dragged = ordered.firstOrNull { it.id == draggedId } ?: return
        ordered.removeAll { it.id == draggedId }
        val targetIndex = ordered.indexOfFirst { it.id == targetId }.let { if (it < 0) ordered.size else it }
        ordered.add(targetIndex, dragged)
        clipboardItems = ordered.mapIndexed { index, item -> item.copy(sortIndex = index) }.toMutableList()
        clipboardRepository?.let { repository ->
            val sources = clipboardItems.mapNotNull { it.source?.copy(sortOrder = it.sortIndex) }
            if (sources.isNotEmpty()) {
                scope.launch(Dispatchers.IO) { repository.updateOrder(sources) }
            }
        }
        rebuildKeys()
    }

    private fun prepareTranslatePanel() {
        translateSource = currentInputConnection?.getSelectedText(0)?.toString()
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
        translateCursor = translateSource.length
        translateInputFocused = true
        translateResult = ""
        translateRunnable?.let { mainHandler.removeCallbacks(it) }
    }

    private fun addTranslatePanel(parent: LinearLayout) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = rounded(theme.specialKeyBg, theme.keyBorder)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(82)
            ).withMargins(2, 2, 2, 2)
        }

        val sourceLabel = if (translateDirection == TranslateDirection.BN_TO_EN) "বাংলা" else "English"
        val targetLabel = if (translateDirection == TranslateDirection.BN_TO_EN) "English" else "বাংলা"
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(languageChip(sourceLabel, 31))
        header.addView(translateSwapButton(50) {
            translateDirection = if (translateDirection == TranslateDirection.EN_TO_BN) {
                TranslateDirection.BN_TO_EN
            } else {
                TranslateDirection.EN_TO_BN
            }
            translateCurrentText()
            rebuildKeys()
        })
        header.addView(languageChip(targetLabel, 31))
        panel.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)))

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        inputRow.addView(translateInputBox())
        inputRow.addView(translateOutputBox())
        inputRow.addView(gradientButton("➤", 0.65f) { sendTranslateResult() })
        panel.addView(inputRow)
        parent.addView(panel)
    }

    private fun sendTranslateResult() {
        when {
            translateResult.isNotBlank() && translateResult != "Translation failed" -> commitToEditor(translateResult)
            translateSource.isNotBlank() -> translateCurrentText()
        }
    }

    private fun updateTranslateCandidateView() {
        setCandidatesViewShown(false)
    }

    private fun translateCurrentText() {
        val text = translateSource.trim()
        if (text.isBlank()) return
        if (isTranslating) {
            pendingTranslateAfterCurrent = true
            return
        }
        isTranslating = true
        pendingTranslateAfterCurrent = false
        translateResult = ""
        refreshTranslatePanel()
        val from = "auto"
        val to = if (translateDirection == TranslateDirection.EN_TO_BN) "bn" else "en"
        scope.launch {
            translateResult = translateTextWithGoogle(text, from, to)
            isTranslating = false
            if (mode == Mode.TRANSLATE) {
                refreshTranslatePanel()
                if (pendingTranslateAfterCurrent || translateSource.trim() != text) {
                    scheduleTranslate()
                }
            }
        }
    }

    private fun scheduleTranslate() {
        translateRunnable?.let { mainHandler.removeCallbacks(it) }
        if (mode != Mode.TRANSLATE || translateSource.isBlank()) return
        val runnable = Runnable {
            if (mode == Mode.TRANSLATE && translateSource.isNotBlank()) translateCurrentText()
        }
        translateRunnable = runnable
        mainHandler.postDelayed(runnable, 650L)
    }

    private suspend fun translateTextWithGoogle(text: String, from: String, to: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(text, "UTF-8")
                val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$from&tl=$to&dt=t&q=$encoded")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                try {
                    val stream = if (connection.responseCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream ?: connection.inputStream
                    }
                    val response = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    parseTranslateResponse(response)
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                "Translation failed"
            }
        }
    }

    private fun parseTranslateResponse(response: String): String {
        return try {
            val translated = StringBuilder()
            val sentences = JSONArray(response).getJSONArray(0)
            for (index in 0 until sentences.length()) {
                translated.append(sentences.getJSONArray(index).optString(0))
            }
            translated.toString().ifBlank { "Translation failed" }
        } catch (_: Exception) {
            "Translation failed"
        }
    }

    private fun refreshTranslatePanel() {
        if (mode != Mode.TRANSLATE) return
        val input = translateInputEditText
        val output = translateOutputTextView
        if (input == null || output == null) {
            rebuildKeys()
            return
        }
        suppressTranslateWatcher = true
        try {
            if (input.text?.toString() != translateSource) {
                input.setText(translateSource)
            }
            input.setSelection(translateCursor.coerceIn(0, translateSource.length))
            output.text = translateOutputText()
        } catch (_: Exception) {
        } finally {
            suppressTranslateWatcher = false
        }
    }

    private fun translateOutputText(): String {
        return when {
            isTranslating -> "Translating..."
            translateResult.isNotBlank() -> translateResult
            else -> ""
        }
    }

    private fun panelText(text: String, weight: Float, sizeSp: Float, color: Int): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(color)
            textSize = sizeSp
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = if (weight > 0f) {
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, weight).withMargins(2, 2, 2, 2)
            } else {
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).withMargins(2, 2, 2, 2)
            }
        }
    }

    private fun languageChip(text: String, heightDp: Int = 44): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(theme.keyText)
            textSize = if (heightDp < 36) 13f else 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = rippleBackground(rounded(theme.keyBackground, theme.keyBorder))
            layoutParams = LinearLayout.LayoutParams(0, dp(heightDp), 1f).withMargins(6, 1, 6, 1)
        }
    }

    private fun translateInputBox(): EditText {
        return EditText(this).apply {
            translateInputEditText = this
            setText(translateSource)
            setSelection(translateCursor.coerceIn(0, translateSource.length))
            setTextColor(theme.keyText)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = false
            showSoftInputOnFocus = false
            background = null
            setPadding(dp(14), 0, dp(14), 0)
            background = rippleBackground(rounded(theme.keyBackground, theme.accent))
            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(34),
                1.15f
            ).withMargins(4, 4, 4, 4)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (suppressTranslateWatcher) return
                    val value = s?.toString().orEmpty()
                    if (value != translateSource) {
                        translateSource = value
                        translateCursor = selectionStart.coerceIn(0, translateSource.length)
                        translateResult = ""
                        scheduleTranslate()
                    }
                }
            })
            setOnClickListener {
                translateInputFocused = true
                translateCursor = selectionStart.coerceIn(0, translateSource.length)
            }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    post {
                        translateInputFocused = true
                        translateCursor = selectionStart.coerceIn(0, translateSource.length)
                    }
                }
                false
            }
        }
    }

    private fun translateOutputBox(): TextView {
        return TextView(this).apply {
            translateOutputTextView = this
            text = translateOutputText()
            setTextColor(theme.accent)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
            setPadding(dp(10), 0, dp(10), 0)
            background = rippleBackground(rounded(theme.keyBackground, theme.keyBorder))
            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(34),
                1f
            ).withMargins(4, 4, 4, 4)
        }
    }

    private fun handleToolbarDrag(targetId: String, event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return true
            DragEvent.ACTION_DROP -> {
                val draggedId = draggedToolbarId ?: return true
                moveToolbarButton(draggedId, targetId)
                draggedToolbarId = null
                return true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                draggedToolbarId = null
                return true
            }
        }
        return true
    }

    private fun moveToolbarButton(draggedId: String, targetId: String) {
        if (draggedId == targetId) return
        val next = toolbarOrder.toMutableList()
        if (!next.remove(draggedId)) return
        val targetIndex = next.indexOf(targetId).let { if (it < 0) next.size else it }
        next.add(targetIndex, draggedId)
        toolbarOrder = sanitizeToolbarOrder(next.joinToString(","))
        scope.launch { settingsRepository.setRowbarOrder(toolbarOrder.joinToString(",")) }
        rebuildKeys()
    }

    private fun startEmojiDrag(view: View, emoji: String) {
        draggedEmoji = emoji
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(null, View.DragShadowBuilder(view), emoji, 0)
        } else {
            @Suppress("DEPRECATION")
            view.startDrag(null, View.DragShadowBuilder(view), emoji, 0)
        }
    }

    private fun handleFavoriteEmojiDrop(targetEmoji: String?, event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return true
            DragEvent.ACTION_DROP -> {
                val emoji = (event.localState as? String) ?: draggedEmoji ?: return true
                val next = favoriteEmojis.toMutableList()
                next.remove(emoji)
                val targetIndex = targetEmoji?.let { next.indexOf(it) }?.takeIf { it >= 0 } ?: next.size
                next.add(targetIndex, emoji)
                favoriteEmojis = next.distinct().take(20).toMutableList()
                draggedEmoji = null
                rebuildKeys()
                return true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                draggedEmoji = null
                return true
            }
        }
        return true
    }

    private fun startRepeatingDelete() {
        stopRepeatingDelete()
        val runnable = object : Runnable {
            override fun run() {
                deleteChar()
                mainHandler.postDelayed(this, 28L)
            }
        }
        deleteRepeatRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun stopRepeatingDelete() {
        deleteRepeatRunnable?.let { mainHandler.removeCallbacks(it) }
        deleteRepeatRunnable = null
    }

    private fun startRepeatingSpace() {
        stopRepeatingSpace()
        val runnable = object : Runnable {
            override fun run() {
                commitText(" ")
                mainHandler.postDelayed(this, 65L)
            }
        }
        spaceRepeatRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun stopRepeatingSpace() {
        spaceRepeatRunnable?.let { mainHandler.removeCallbacks(it) }
        spaceRepeatRunnable = null
    }

    private fun hideKeyboardView(forceWindow: Boolean = false) {
        showRequestGeneration++
        // Set GONE directly (no post) so it cannot race with an immediate
        // VISIBLE set from showKeyboardOnce() called right after.
        rootView?.visibility = View.GONE
        if (forceWindow && !hidingWindow) {
            hidingWindow = true
            try {
                requestHideSelf(0)
                hideWindow()
            } catch (_: Exception) {}
            mainHandler.postDelayed({ hidingWindow = false }, 120L)
        }
    }

    private fun cancelDeferredHide() {
        hideRequestGeneration++
        pendingHideRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingHideRunnable = null
    }

    private fun scheduleDeferredHide(delayMs: Long, forceWindow: Boolean) {
        val generation = ++hideRequestGeneration
        pendingHideRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            if (generation != hideRequestGeneration) return@Runnable
            activeEditorAllowsKeyboard = false
            systemOverlayRecoveryUntil = 0L
            setCandidatesViewShown(false)
            hideKeyboardView(forceWindow = forceWindow)
            if (forceWindow) {
                try { requestHideSelf(0) } catch (_: Exception) {}
            }
        }
        pendingHideRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun scheduleKeyboardRecovery() {
        listOf(40L, 140L, 300L).forEach { delay ->
            mainHandler.postDelayed({
                val now = System.currentTimeMillis()
                if (activeEditorAllowsKeyboard &&
                    currentEditorAllowsKeyboard() &&
                    inputConnectionAlive() &&
                    now > keyboardDismissedByUserUntil &&
                    now < systemOverlayRecoveryUntil
                ) {
                    requestShowKeyboardNow(requireLiveConnection = true)
                }
            }, delay)
        }
    }

    private fun requestShowKeyboardNow(requireLiveConnection: Boolean = false) {
        if (!activeEditorAllowsKeyboard ||
            !currentEditorAllowsKeyboard() ||
            (requireLiveConnection && !inputConnectionAlive())
        ) {
            activeEditorAllowsKeyboard = false
            hideKeyboardView()
            return
        }
        val generation = ++showRequestGeneration
        showKeyboardOnce()
        ensureKeyboardVisible(requireLiveConnection)
        // Retry at shorter intervals so the keyboard appears without noticeable
        // delay when quickly switching between apps or after pressing back.
        listOf(16L, 50L, 110L).forEach { delay ->
            mainHandler.postDelayed({
                if (generation == showRequestGeneration &&
                    activeEditorAllowsKeyboard &&
                    currentEditorAllowsKeyboard() &&
                    (!requireLiveConnection || inputConnectionAlive())
                ) {
                    showKeyboardOnce()
                    ensureKeyboardVisible(requireLiveConnection = false)
                }
            }, delay)
        }
    }

    private fun showKeyboardOnce() {
        try {
            requestShowSelf(InputMethodManager.SHOW_IMPLICIT)
        } catch (_: Exception) {
        }
        rootView?.let { view ->
            val height = currentKeyboardHeightPx()
            view.visibility = View.VISIBLE
            view.layoutParams = (view.layoutParams ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                this.height = height
            }
            view.setFixedHeight(height)
            updateInputViewShown()
            try {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                // SHOW_IMPLICIT lets the system decide; on immediate re-focus we
                // also send SHOW_FORCED so the keyboard is not skipped when the
                // system thinks the user hid it (e.g. back → inbox quickly).
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            } catch (_: Exception) {
            }
        }
    }

    private fun inputConnectionAlive(): Boolean {
        val connection = currentInputConnection ?: return false
        return try {
            connection.getTextBeforeCursor(0, 0) != null ||
                connection.getTextAfterCursor(0, 0) != null ||
                connection.getSelectedText(0) != null
        } catch (_: Exception) {
            false
        }
    }

    private fun shouldShowForEditor(attribute: EditorInfo?): Boolean {
        attribute ?: return false
        val inputType = attribute.inputType
        // TYPE_NULL (0) explicitly means "no keyboard" — respect that.
        if (inputType == InputType.TYPE_NULL) return false
        // For everything else (text, number, phone, datetime, and apps like
        // Google Sheets / WebView cells that set variation/flag bits without a
        // clean class) follow Gboard's approach: show the keyboard.
        return true
    }

    private fun currentEditorAllowsKeyboard(): Boolean {
        return shouldShowForEditor(currentInputEditorInfo)
    }

    private fun registerPrimaryClipboardListener() {
        if (clipboardListenerAdded) return
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.addPrimaryClipChangedListener(primaryClipChangedListener)
            clipboardListenerAdded = true
        } catch (_: Exception) {}
    }

    private fun unregisterPrimaryClipboardListener() {
        if (!clipboardListenerAdded) return
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.removePrimaryClipChangedListener(primaryClipChangedListener)
        } catch (_: Exception) {
        } finally {
            clipboardListenerAdded = false
        }
    }

    private fun settingsContextForCurrentUser(): Context {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val userManager = getSystemService(Context.USER_SERVICE) as? UserManager
            if (userManager?.isUserUnlocked == false) createDeviceProtectedStorageContext() else applicationContext
        } else {
            applicationContext
        }
    }

    private fun setupClipboardRepositoryIfUnlocked() {
        if (clipboardRepositoryStarted || !isUserUnlockedForStorage()) return
        clipboardRepositoryStarted = true
        val repository = ClipboardRepository(AppDatabase.getInstance(applicationContext).clipboardDao())
        clipboardRepository = repository
        scope.launch {
            repository.allItems.collect { items ->
                clipboardItems = items.map {
                    NativeClipboardItem(
                        id = it.id,
                        text = it.text,
                        timestamp = it.timestamp,
                        isPinned = it.isPinned,
                        sortIndex = it.sortOrder,
                        source = it
                    )
                }.toMutableList()
                if (mode == Mode.CLIPBOARD) rebuildKeys()
            }
        }
    }

    private fun isUserUnlockedForStorage(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val userManager = getSystemService(Context.USER_SERVICE) as? UserManager
            userManager?.isUserUnlocked != false
        } else {
            true
        }
    }

    private fun ensureKeyboardVisible(requireLiveConnection: Boolean = false) {
        if (!activeEditorAllowsKeyboard ||
            !currentEditorAllowsKeyboard() ||
            (requireLiveConnection && !inputConnectionAlive())
        ) {
            activeEditorAllowsKeyboard = false
            hideKeyboardView()
            return
        }
        val view = rootView ?: return
        val height = currentKeyboardHeightPx()
        view.post {
            view.visibility = View.VISIBLE
            view.layoutParams = (view.layoutParams ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                this.height = height
            }
            view.setFixedHeight(height)
            view.requestLayout()
            updateInputViewShown()
        }
    }

    private fun applyKeyboardHeight() {
        val height = currentKeyboardHeightPx()
        keyboardHeightPx = height
        rootView?.setFixedHeight(height)
        rootView?.layoutParams = (rootView?.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            height
        )).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            this.height = height
        }
    }

    private fun currentKeyboardHeightPx(): Int {
        val heightDp = when (mode) {
            Mode.TRANSLATE -> 334
            Mode.NUMBERS, Mode.SYMBOLS -> 294
            Mode.EMOJI, Mode.CLIPBOARD -> 374
            Mode.SETTINGS -> 374
            Mode.LETTERS -> {
                val banglaMoreRow = if (language == Language.BN && shift) 48 else 0
                (if (showNumberRow) 286 else 246) + banglaMoreRow
            }
        }
        return boundedKeyboardHeightPx(heightDp)
    }

    private fun boundedKeyboardHeightPx(heightDp: Int): Int {
        val density = resources.displayMetrics.density
        val desired = (density * heightDp).toInt()
        val minimum = (density * 236f).toInt()
        val maximum = (resources.displayMetrics.heightPixels * 0.58f).toInt().coerceAtLeast(minimum)
        return desired.coerceIn(minimum, maximum)
    }

    private fun resolveKeyboardHeightPx(): Int {
        return currentKeyboardHeightPx()
    }

    private fun rounded(fill: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(9).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }
    }

    private fun activeRounded(fill: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(9).toFloat()
            setColor(blendColors(fill, theme.accent, 0.22f))
            setStroke(dp(2), theme.accent)
        }
    }

    private fun rippleBackground(content: Drawable): Drawable {
        return RippleDrawable(ColorStateList.valueOf(adjustAlpha(theme.accent, 0.22f)), content, null)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        return Color.argb(
            (Color.alpha(color) * factor).toInt().coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    private fun blendColors(base: Int, overlay: Int, amount: Float): Int {
        val clamped = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(base) * (1f - clamped) + Color.red(overlay) * clamped).toInt(),
            (Color.green(base) * (1f - clamped) + Color.green(overlay) * clamped).toInt(),
            (Color.blue(base) * (1f - clamped) + Color.blue(overlay) * clamped).toInt()
        )
    }

    private fun gradientRounded(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(theme.gradientStart, theme.gradientEnd)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(9).toFloat()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun LinearLayout.LayoutParams.withMargins(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): LinearLayout.LayoutParams {
        setMargins(dp(left), dp(top), dp(right), dp(bottom))
        return this
    }

    private fun sanitizeToolbarOrder(saved: String): List<String> {
        val selected = saved.split(",")
            .map { it.trim() }
            .filter { it in DEFAULT_TOOLBAR_ORDER }
            .distinct()
        return selected + DEFAULT_TOOLBAR_ORDER.filterNot { it in selected }
    }

    private fun sanitizeToolbarEnabled(saved: String): Set<String> {
        return saved.split(",")
            .map { it.trim() }
            .filter { it in DEFAULT_TOOLBAR_ORDER }
            .toSet()
    }

    private class FixedHeightInputView(
        context: Context,
        private var fixedHeightPx: Int
    ) : LinearLayout(context) {
        fun setFixedHeight(heightPx: Int) {
            if (heightPx == fixedHeightPx) return
            fixedHeightPx = heightPx
            minimumHeight = heightPx
            layoutParams = (layoutParams ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                heightPx
            )).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = heightPx
            }
            requestLayout()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val exactHeight = View.MeasureSpec.makeMeasureSpec(fixedHeightPx, View.MeasureSpec.EXACTLY)
            super.onMeasure(widthMeasureSpec, exactHeight)
            setMeasuredDimension(View.MeasureSpec.getSize(widthMeasureSpec), fixedHeightPx)
        }
    }

    private class FastToolbarScrollView(context: Context) : HorizontalScrollView(context) {
        override fun fling(velocityX: Int) {
            super.fling((velocityX * 2.4f).toInt())
        }
    }

    private data class SettingsSnapshot(
        val themeId: String,
        val showNumberRow: Boolean,
        val language: String,
        val vibrate: Boolean,
        val rowbarOrder: String,
        val rowbarEnabled: String
    )

    private data class ToolbarAction(
        val iconName: String,
        val fallbackLabel: String,
        val onClick: () -> Unit
    )

    private data class NativeClipboardItem(
        val id: Long,
        val text: String,
        val timestamp: Long = System.currentTimeMillis(),
        val isPinned: Boolean = false,
        val sortIndex: Int = 0,
        val source: ClipboardItem? = null
    )

    private enum class TranslateDirection { EN_TO_BN, BN_TO_EN }
    private enum class VoiceTarget { KEYBOARD, TRANSLATE }

    private data class NativeTheme(
        val background: Int,
        val keyBackground: Int,
        val keyBorder: Int,
        val keyText: Int,
        val accent: Int,
        val specialKeyBg: Int,
        val rowbarBg: Int,
        val gradientStart: Int,
        val gradientEnd: Int
    ) {
        companion object {
            val themeIds = listOf(
                "ice_white", "neon_dark", "neon_pink", "glass_ocean", "purple_galaxy",
                "gold_luxury", "red_fire", "cyber_green", "sunset_vibes", "painite_original"
            )

            fun displayName(id: String): String {
                return when (id) {
                    "ice_white" -> "Ice White"
                    "neon_dark" -> "Neon Dark"
                    "neon_pink" -> "Neon Pink"
                    "glass_ocean" -> "Glass Ocean"
                    "purple_galaxy" -> "Purple Galaxy"
                    "gold_luxury" -> "Gold Luxury"
                    "red_fire" -> "Red Fire"
                    "cyber_green" -> "Cyber Green"
                    "sunset_vibes" -> "Sunset Vibes"
                    "painite_original" -> "Painite"
                    else -> id
                }
            }

            fun byId(id: String): NativeTheme {
                return when (id) {
                    "neon_pink" -> NativeTheme(
                        c(18, 0, 15), c(30, 0, 25), c(255, 45, 120), Color.WHITE,
                        c(255, 45, 120), c(32, 0, 24), c(15, 0, 13), c(255, 45, 120), c(255, 149, 0)
                    )
                    "glass_ocean" -> NativeTheme(
                        c(0, 27, 46), c(10, 37, 64), c(0, 180, 216), Color.WHITE,
                        c(0, 180, 216), c(13, 33, 55), c(0, 21, 37), c(0, 119, 182), c(0, 180, 216)
                    )
                    "purple_galaxy" -> NativeTheme(
                        c(10, 0, 21), c(22, 0, 37), c(155, 89, 182), Color.WHITE,
                        c(155, 89, 182), c(26, 0, 48), c(8, 0, 16), c(108, 52, 131), c(224, 86, 253)
                    )
                    "gold_luxury" -> NativeTheme(
                        c(13, 10, 0), c(26, 21, 0), c(255, 215, 0), c(255, 248, 220),
                        c(255, 215, 0), c(33, 27, 0), c(16, 13, 0), c(255, 215, 0), c(255, 165, 0)
                    )
                    "red_fire" -> NativeTheme(
                        c(18, 0, 0), c(30, 0, 0), c(255, 51, 0), Color.WHITE,
                        c(255, 51, 0), c(32, 0, 0), c(15, 0, 0), c(255, 0, 0), c(255, 102, 0)
                    )
                    "cyber_green" -> NativeTheme(
                        c(0, 13, 5), c(0, 26, 10), c(0, 255, 102), c(0, 255, 102),
                        c(0, 255, 102), c(0, 31, 12), c(0, 13, 5), c(0, 255, 102), c(0, 204, 82)
                    )
                    "ice_white" -> NativeTheme(
                        c(240, 244, 255), Color.WHITE, c(144, 202, 249), c(26, 35, 126),
                        c(33, 150, 243), c(227, 242, 253), c(232, 238, 255), c(33, 150, 243), c(144, 202, 249)
                    )
                    "sunset_vibes" -> NativeTheme(
                        c(18, 10, 0), c(30, 16, 0), c(255, 107, 53), Color.WHITE,
                        c(255, 107, 53), c(35, 21, 0), c(15, 8, 0), c(255, 107, 53), c(255, 230, 109)
                    )
                    "painite_original" -> NativeTheme(
                        c(10, 0, 24), c(19, 0, 37), c(123, 47, 190), Color.WHITE,
                        c(171, 71, 188), c(26, 0, 53), c(8, 0, 18), c(123, 47, 190), c(0, 229, 255)
                    )
                    else -> iceWhite()
                }
            }

            fun iceWhite(): NativeTheme {
                return NativeTheme(
                    c(240, 244, 255), Color.WHITE, c(144, 202, 249), c(26, 35, 126),
                    c(33, 150, 243), c(227, 242, 253), c(232, 238, 255), c(33, 150, 243), c(144, 202, 249)
                )
            }

            fun neonDark(): NativeTheme {
                return NativeTheme(
                    c(13, 13, 26), c(26, 26, 46), c(0, 212, 255), Color.WHITE,
                    c(0, 212, 255), c(22, 33, 62), c(15, 15, 30), c(0, 212, 255), c(178, 75, 243)
                )
            }

            private fun c(red: Int, green: Int, blue: Int): Int = Color.rgb(red, green, blue)
        }
    }

    private enum class Language(val storageValue: String, val shortLabel: String) {
        EN("EN", "EN"),
        BN("BN", "BN"),
        ABC_BN("ABC_BN", "E>B")
    }
    private enum class Mode { LETTERS, SYMBOLS, NUMBERS, EMOJI, CLIPBOARD, SETTINGS, TRANSLATE }

    companion object {
        private val URL_TELEGRAM: Uri = Uri.parse("https://t.me/JAHID_1")
        private val DEFAULT_TOOLBAR_ORDER = listOf(
            "voice", "translate", "clipboard_clear", "paste", "copy", "select_all",
            "clipboard", "cut", "number_toggle", "emoji", "switch_keyboard"
        )

        private val NUMBER_ROW = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        private val NUMBER_PAD_ROWS = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(".", "0", ","),
            listOf("+", "-", "=")
        )
        private val EN_ROWS = listOf(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("z", "x", "c", "v", "b", "n", "m")
        )
        private val BN_ROWS = listOf(
            listOf("ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ", "ঝ", "ঞ"),
            listOf("ট", "ঠ", "ড", "ঢ", "ণ", "ত", "থ", "দ", "ধ")
        )
        private val BN_ROW3 = listOf("ন", "প", "ফ", "ব", "ভ", "ম", "য")
        private val BN_ROWS_SHIFT = listOf(
            listOf("া", "ি", "ী", "ু", "ূ", "ৃ", "ে", "ৈ", "ো", "ৌ"),
            listOf("অ", "আ", "ই", "ঈ", "উ", "ঊ", "ঋ", "এ", "ঐ")
        )
        private val BN_ROW3_SHIFT = listOf("ও", "ঔ", "র", "ল", "শ", "ষ", "স", "হ", "ড়", "ঢ়", "য়", "ৎ", "ং", "ঃ", "ঁ", "্", "ক্ষ")
        private val BANGLA_LONG_PRESS_KEYS = (BN_ROWS.flatten() + BN_ROWS_SHIFT.flatten() + BN_ROW3 + BN_ROW3_SHIFT).toSet()
        private val SYMBOL_ROWS = listOf(
            listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"),
            listOf("-", "_", "=", "+", "[", "]", "{", "}", ";", ":"),
            listOf("'", "\"", ",", ".", "/", "\\", "|", "?", "€", "৳")
        )
        private val EMOJI_ROWS = listOf(
            listOf("😀", "😂", "😍", "🥰", "😎", "😭", "😡", "👍"),
            listOf("❤️", "🔥", "✨", "🎉", "🙏", "💯", "✅", "⭐"),
            listOf("🌹", "💐", "☕", "🍽", "🎂", "📌", "📱", "⌚")
        )
        private val EMOJI_LIST = emojiCodePoints(
            0x1F600..0x1F64F,
            0x1F300..0x1F5FF,
            0x1F680..0x1F6FF,
            0x1F900..0x1F9FF,
            0x1FA70..0x1FAFF
        ).take(420)
        private val PREMIUM_SIDE_EMOJIS = listOf(
            "✨", "🔥", "💎", "⭐", "💯",
            "✅", "💖", "🥰", "😍", "😎",
            "🤩", "👏", "🙏", "🎉", "🌹",
            "🚀", "⚡", "👌", "👍", "😊"
        )

        private fun emojiCodePoints(vararg ranges: IntRange): List<String> {
            return ranges.flatMap { range ->
                range.map { codePoint -> String(Character.toChars(codePoint)) }
            }
        }
    }
}
