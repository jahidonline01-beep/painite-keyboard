package com.painite.keyboard.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class KeyboardTheme(
    val id: String,
    val displayName: String,
    val background: Color,
    val keyBackground: Color,
    val keyBorder: Color,
    val keyTextColor: Color,
    val accentColor: Color,
    val accentSecondary: Color,
    val specialKeyBg: Color,
    val rowbarBg: Color,
    val glowColor: Color,
    val gradient: Brush
)

object PainiteThemes {

    val NeonDark = KeyboardTheme(
        id = "neon_dark",
        displayName = "Neon Dark",
        background = Color(0xFF0D0D1A),
        keyBackground = Color(0xFF1A1A2E),
        keyBorder = Color(0xFF00D4FF).copy(alpha = 0.3f),
        keyTextColor = Color.White,
        accentColor = Color(0xFF00D4FF),
        accentSecondary = Color(0xFFB24BF3),
        specialKeyBg = Color(0xFF16213E),
        rowbarBg = Color(0xFF0F0F1E),
        glowColor = Color(0xFF00D4FF),
        gradient = Brush.linearGradient(listOf(Color(0xFF00D4FF), Color(0xFFB24BF3)))
    )

    val NeonPink = KeyboardTheme(
        id = "neon_pink",
        displayName = "Neon Pink",
        background = Color(0xFF12000F),
        keyBackground = Color(0xFF1E0019),
        keyBorder = Color(0xFFFF2D78).copy(alpha = 0.35f),
        keyTextColor = Color.White,
        accentColor = Color(0xFFFF2D78),
        accentSecondary = Color(0xFFFF9500),
        specialKeyBg = Color(0xFF200018),
        rowbarBg = Color(0xFF0F000D),
        glowColor = Color(0xFFFF2D78),
        gradient = Brush.linearGradient(listOf(Color(0xFFFF2D78), Color(0xFFFF9500)))
    )

    val GlassOcean = KeyboardTheme(
        id = "glass_ocean",
        displayName = "Glass Ocean",
        background = Color(0xFF001B2E),
        keyBackground = Color(0xFF0A2540).copy(alpha = 0.9f),
        keyBorder = Color(0xFF00B4D8).copy(alpha = 0.4f),
        keyTextColor = Color.White,
        accentColor = Color(0xFF00B4D8),
        accentSecondary = Color(0xFF48CAE4),
        specialKeyBg = Color(0xFF0D2137),
        rowbarBg = Color(0xFF001525),
        glowColor = Color(0xFF00B4D8),
        gradient = Brush.linearGradient(listOf(Color(0xFF0077B6), Color(0xFF00B4D8)))
    )

    val PurpleGalaxy = KeyboardTheme(
        id = "purple_galaxy",
        displayName = "Purple Galaxy",
        background = Color(0xFF0A0015),
        keyBackground = Color(0xFF160025),
        keyBorder = Color(0xFF9B59B6).copy(alpha = 0.4f),
        keyTextColor = Color.White,
        accentColor = Color(0xFF9B59B6),
        accentSecondary = Color(0xFFE056FD),
        specialKeyBg = Color(0xFF1A0030),
        rowbarBg = Color(0xFF080010),
        glowColor = Color(0xFFE056FD),
        gradient = Brush.linearGradient(listOf(Color(0xFF6C3483), Color(0xFFE056FD)))
    )

    val GoldLuxury = KeyboardTheme(
        id = "gold_luxury",
        displayName = "Gold Luxury",
        background = Color(0xFF0D0A00),
        keyBackground = Color(0xFF1A1500),
        keyBorder = Color(0xFFFFD700).copy(alpha = 0.3f),
        keyTextColor = Color(0xFFFFF8DC),
        accentColor = Color(0xFFFFD700),
        accentSecondary = Color(0xFFFFA500),
        specialKeyBg = Color(0xFF211B00),
        rowbarBg = Color(0xFF100D00),
        glowColor = Color(0xFFFFD700),
        gradient = Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500)))
    )

    val RedFire = KeyboardTheme(
        id = "red_fire",
        displayName = "Red Fire",
        background = Color(0xFF120000),
        keyBackground = Color(0xFF1E0000),
        keyBorder = Color(0xFFFF3300).copy(alpha = 0.35f),
        keyTextColor = Color.White,
        accentColor = Color(0xFFFF3300),
        accentSecondary = Color(0xFFFF6600),
        specialKeyBg = Color(0xFF200000),
        rowbarBg = Color(0xFF0F0000),
        glowColor = Color(0xFFFF3300),
        gradient = Brush.linearGradient(listOf(Color(0xFFFF0000), Color(0xFFFF6600)))
    )

    val CyberGreen = KeyboardTheme(
        id = "cyber_green",
        displayName = "Cyber Green",
        background = Color(0xFF000D05),
        keyBackground = Color(0xFF001A0A),
        keyBorder = Color(0xFF00FF66).copy(alpha = 0.35f),
        keyTextColor = Color(0xFF00FF66),
        accentColor = Color(0xFF00FF66),
        accentSecondary = Color(0xFF00CC52),
        specialKeyBg = Color(0xFF001F0C),
        rowbarBg = Color(0xFF000D05),
        glowColor = Color(0xFF00FF66),
        gradient = Brush.linearGradient(listOf(Color(0xFF00FF66), Color(0xFF00CC52)))
    )

    val IceWhite = KeyboardTheme(
        id = "ice_white",
        displayName = "Ice White",
        background = Color(0xFFF0F4FF),
        keyBackground = Color(0xFFFFFFFF),
        keyBorder = Color(0xFF90CAF9).copy(alpha = 0.5f),
        keyTextColor = Color(0xFF1A237E),
        accentColor = Color(0xFF2196F3),
        accentSecondary = Color(0xFF90CAF9),
        specialKeyBg = Color(0xFFE3F2FD),
        rowbarBg = Color(0xFFE8EEFF),
        glowColor = Color(0xFF2196F3),
        gradient = Brush.linearGradient(listOf(Color(0xFF2196F3), Color(0xFF90CAF9)))
    )

    val SunsetVibes = KeyboardTheme(
        id = "sunset_vibes",
        displayName = "Sunset Vibes",
        background = Color(0xFF120A00),
        keyBackground = Color(0xFF1E1000),
        keyBorder = Color(0xFFFF6B35).copy(alpha = 0.35f),
        keyTextColor = Color.White,
        accentColor = Color(0xFFFF6B35),
        accentSecondary = Color(0xFFFFE66D),
        specialKeyBg = Color(0xFF231500),
        rowbarBg = Color(0xFF0F0800),
        glowColor = Color(0xFFFF6B35),
        gradient = Brush.linearGradient(listOf(Color(0xFFFF6B35), Color(0xFFFFE66D)))
    )

    val PainiteOriginal = KeyboardTheme(
        id = "painite_original",
        displayName = "Painite",
        background = Color(0xFF0A0018),
        keyBackground = Color(0xFF130025),
        keyBorder = Color(0xFF7B2FBE).copy(alpha = 0.5f),
        keyTextColor = Color.White,
        accentColor = Color(0xFFAB47BC),
        accentSecondary = Color(0xFF00E5FF),
        specialKeyBg = Color(0xFF1A0035),
        rowbarBg = Color(0xFF080012),
        glowColor = Color(0xFFAB47BC),
        gradient = Brush.linearGradient(listOf(Color(0xFF7B2FBE), Color(0xFF00E5FF)))
    )

    val all = listOf(
        NeonDark, NeonPink, GlassOcean, PurpleGalaxy, GoldLuxury,
        RedFire, CyberGreen, IceWhite, SunsetVibes, PainiteOriginal
    )

    fun getById(id: String): KeyboardTheme = all.find { it.id == id } ?: IceWhite
}
