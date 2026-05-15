package com.painite.keyboard.ui.settings

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.painite.keyboard.data.SettingsRepository
import com.painite.keyboard.ui.theme.PainiteThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var container: LinearLayout
    private var currentThemeId = "ice_white"
    private var currentNumberRow = true
    private var currentVibrate = true
    private var currentRowbarEnabled = SettingsRepository.DEFAULT_ROWBAR_ORDER.split(",").toSet()
    private var palette = settingsPalette("ice_white")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )
        settingsRepository = SettingsRepository(applicationContext)
        setContentView(buildRoot())
        loadSettings()
    }

    private fun buildRoot(): View {
        val scrollView = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setBackgroundColor(palette.background)
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scrollView.addView(
            container,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        rebuildContent()
        return scrollView
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            currentThemeId = settingsRepository.theme.first()
            palette = settingsPalette(currentThemeId)
            currentNumberRow = settingsRepository.showNumberRow.first()
            currentVibrate = settingsRepository.vibrate.first()
            currentRowbarEnabled = settingsRepository.rowbarEnabled.first()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            rebuildContent()
        }
    }

    private fun rebuildContent() {
        container.removeAllViews()
        palette = settingsPalette(currentThemeId)
        container.setBackgroundColor(palette.background)

        container.addView(headerRow())
        container.addView(section("Themes").apply {
            PainiteThemes.all.chunked(2).forEach { row ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    row.forEach { theme ->
                        addView(themeButton(theme.id, theme.displayName), LinearLayout.LayoutParams(0, dp(50), 1f).withMargins(4, 4, 4, 4))
                    }
                    if (row.size == 1) addView(TextView(context), LinearLayout.LayoutParams(0, dp(50), 1f))
                })
            }
        })

        container.addView(section("Keyboard Layout").apply {
            addView(switchRow("Show Number Row", "Display 1-0 number row above keyboard", currentNumberRow) { checked ->
                currentNumberRow = checked
                lifecycleScope.launch(Dispatchers.IO) { settingsRepository.setShowNumberRow(checked) }
            })
            addView(switchRow("Vibration Feedback", "Haptic feedback on key press", currentVibrate) { checked ->
                currentVibrate = checked
                lifecycleScope.launch(Dispatchers.IO) { settingsRepository.setVibrate(checked) }
            })
        })

        container.addView(section("Rowbar").apply {
            addView(actionRow("Reset Rowbar Order", "Restore default rowbar button order") {
                currentRowbarEnabled = SettingsRepository.DEFAULT_ROWBAR_ORDER.split(",").toSet()
                lifecycleScope.launch(Dispatchers.IO) { settingsRepository.resetRowbarOrder() }
                rebuildContent()
            })
            addView(actionRow("Reset Rowbar Buttons", "Turn every rowbar button back on") {
                currentRowbarEnabled = SettingsRepository.DEFAULT_ROWBAR_ORDER.split(",").toSet()
                lifecycleScope.launch(Dispatchers.IO) { settingsRepository.resetRowbarButtons() }
                rebuildContent()
            })
            rowbarButtonSettings().forEach { item ->
                addView(switchRow(item.label, item.description, item.id in currentRowbarEnabled) { checked ->
                    currentRowbarEnabled = currentRowbarEnabled.toMutableSet().apply {
                        if (checked) add(item.id) else remove(item.id)
                    }
                    lifecycleScope.launch(Dispatchers.IO) {
                        settingsRepository.setRowbarEnabled(currentRowbarEnabled.joinToString(","))
                    }
                })
            }
        })

        container.addView(section("About").apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(titleText("Painite Keyboard", 16f, palette.accent).apply {
                gravity = Gravity.CENTER
            })
            addView(bodyText("Version 1.0.25"))
            addView(actionButton("Telegram: @JAHID_1") {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/JAHID_1")))
            })
        })
    }

    private fun headerRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
            addView(actionButton("Back") { finish() }, LinearLayout.LayoutParams(dp(74), dp(42)).withMargins(0, 0, dp(10), 0))
            addView(titleText("Painite Settings", 20f, palette.accent), LinearLayout.LayoutParams(0, dp(42), 1f))
        }
    }

    private fun section(title: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = rounded(palette.card, palette.border, dp(14))
            addView(titleText(title.uppercase(), 12f, palette.accent))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).withMargins(0, 0, 0, dp(14))
        }
    }

    private fun themeButton(id: String, name: String): TextView {
        val selected = id == currentThemeId
        return TextView(this).apply {
            text = name
            gravity = Gravity.CENTER
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (selected) palette.selectedText else palette.text)
            background = rounded(
                if (selected) palette.accent else palette.button,
                palette.border,
                dp(10)
            )
            setOnClickListener {
                currentThemeId = id
                palette = settingsPalette(id)
                lifecycleScope.launch(Dispatchers.IO) { settingsRepository.setTheme(id) }
                rebuildContent()
            }
        }
    }

    private fun switchRow(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(titleText(label, 14f, palette.text))
                addView(bodyText(description))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Switch(context).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
            })
        }
    }

    private fun actionRow(label: String, description: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = "$label\n$description"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.text)
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { onClick() }
        }
    }

    private fun actionButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setAllCaps(false)
            setTextColor(Color.WHITE)
            textSize = 12f
            background = rounded(palette.accent, palette.accent, dp(9))
            setOnClickListener { onClick() }
        }
    }

    private fun titleText(text: String, size: Float, color: Int): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color)
            setPadding(0, dp(3), 0, dp(3))
        }
    }

    private fun bodyText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(palette.subtext)
            setPadding(0, dp(2), 0, dp(4))
        }
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun LinearLayout.LayoutParams.withMargins(left: Int, top: Int, right: Int, bottom: Int): LinearLayout.LayoutParams {
        setMargins(left, top, right, bottom)
        return this
    }

    private data class SettingsPalette(
        val background: Int,
        val card: Int,
        val button: Int,
        val border: Int,
        val text: Int,
        val subtext: Int,
        val accent: Int,
        val selectedText: Int = Color.WHITE
    )

    private fun settingsPalette(id: String): SettingsPalette {
        return when (id) {
            "neon_dark" -> SettingsPalette(
                Color.rgb(13, 13, 26), Color.rgb(22, 33, 62), Color.rgb(26, 26, 46),
                Color.rgb(0, 212, 255), Color.WHITE, Color.rgb(170, 200, 215), Color.rgb(0, 212, 255)
            )
            "neon_pink" -> SettingsPalette(
                Color.rgb(18, 0, 15), Color.rgb(32, 0, 24), Color.rgb(30, 0, 25),
                Color.rgb(255, 45, 120), Color.WHITE, Color.rgb(245, 190, 215), Color.rgb(255, 45, 120)
            )
            "glass_ocean" -> SettingsPalette(
                Color.rgb(0, 27, 46), Color.rgb(13, 33, 55), Color.rgb(10, 37, 64),
                Color.rgb(0, 180, 216), Color.WHITE, Color.rgb(175, 225, 240), Color.rgb(0, 180, 216)
            )
            "purple_galaxy", "painite_original" -> SettingsPalette(
                Color.rgb(10, 0, 24), Color.rgb(26, 0, 53), Color.rgb(19, 0, 37),
                Color.rgb(171, 71, 188), Color.WHITE, Color.rgb(220, 190, 235), Color.rgb(171, 71, 188)
            )
            "gold_luxury" -> SettingsPalette(
                Color.rgb(18, 14, 0), Color.rgb(34, 26, 0), Color.rgb(30, 23, 0),
                Color.rgb(255, 215, 0), Color.rgb(255, 245, 190), Color.rgb(230, 205, 120), Color.rgb(255, 215, 0), Color.BLACK
            )
            "red_fire" -> SettingsPalette(
                Color.rgb(21, 0, 0), Color.rgb(48, 0, 0), Color.rgb(37, 0, 0),
                Color.rgb(255, 23, 68), Color.WHITE, Color.rgb(245, 180, 190), Color.rgb(255, 23, 68)
            )
            "cyber_green" -> SettingsPalette(
                Color.rgb(0, 13, 5), Color.rgb(0, 31, 12), Color.rgb(0, 26, 10),
                Color.rgb(0, 255, 102), Color.rgb(0, 255, 102), Color.rgb(150, 235, 180), Color.rgb(0, 255, 102), Color.BLACK
            )
            "sunset_vibes" -> SettingsPalette(
                Color.rgb(18, 10, 0), Color.rgb(35, 21, 0), Color.rgb(30, 16, 0),
                Color.rgb(255, 107, 53), Color.WHITE, Color.rgb(245, 205, 160), Color.rgb(255, 107, 53)
            )
            else -> SettingsPalette(
                Color.rgb(240, 244, 255), Color.WHITE, Color.rgb(227, 242, 253),
                Color.rgb(144, 202, 249), Color.rgb(26, 35, 126), Color.rgb(70, 90, 120), Color.rgb(33, 150, 243)
            )
        }
    }

    private data class RowbarButtonSetting(val id: String, val label: String, val description: String)

    private fun rowbarButtonSettings(): List<RowbarButtonSetting> = listOf(
        RowbarButtonSetting("voice", "Voice Button", "Show or hide voice typing shortcut"),
        RowbarButtonSetting("translate", "Translate Button", "Show or hide translate shortcut"),
        RowbarButtonSetting("clipboard_clear", "Clear Button", "Clear unpinned clipboard messages"),
        RowbarButtonSetting("paste", "Paste Button", "Show or hide paste shortcut"),
        RowbarButtonSetting("copy", "Copy Button", "Show or hide copy shortcut"),
        RowbarButtonSetting("select_all", "Select All Button", "Show or hide select all shortcut"),
        RowbarButtonSetting("clipboard", "Clipboard Button", "Show or hide clipboard shortcut"),
        RowbarButtonSetting("cut", "Cut Button", "Show or hide cut shortcut"),
        RowbarButtonSetting("number_toggle", "Number Pad Button", "Show or hide number pad shortcut"),
        RowbarButtonSetting("emoji", "Emoji Button", "Show or hide emoji shortcut"),
        RowbarButtonSetting("switch_keyboard", "Switch Keyboard Button", "Show or hide keyboard switcher")
    )
}
