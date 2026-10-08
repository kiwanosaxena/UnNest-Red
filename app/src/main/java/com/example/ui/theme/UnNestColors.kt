package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class UnNestColors(
    val background: Color,
    val card: Color,
    val cardBorder: Color,
    val segmentTrack: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentTint: Color,
    val accentOnTint: Color,
    val success: Color,
    val successTint: Color,
    val warning: Color,
    val warningTint: Color,
    val error: Color,
    val errorTint: Color,
    val terminalBg: Color,
    val terminalText: Color,
    val folderArt: Color,
    val folderArtMuted: Color,
    val ledgerLine: Color,
    val marginLine: Color
)

val LightUnNestColors = UnNestColors(
    background = Color(0xFFF5F3ED),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFDDD8CC),
    segmentTrack = Color(0xFFEEEAE0),
    textPrimary = Color(0xFF1D1B19),
    textSecondary = Color(0xFF6C6963),
    accent = Color(0xFFC52F24),
    onAccent = Color(0xFFFFFFFF),
    accentTint = Color(0xFFFAF1F0),
    accentOnTint = Color(0xFFC52F24),
    success = Color(0xFF4A5F4E),
    successTint = Color(0xFFEEF3EE),
    warning = Color(0xFFB37E40),
    warningTint = Color(0xFFFAF2E6),
    error = Color(0xFF9E3E37),
    errorTint = Color(0xFFFAF1F0),
    terminalBg = Color(0xFF1E211F),
    terminalText = Color(0xFFCBE2D4),
    folderArt = Color(0xFFD32F2F),
    folderArtMuted = Color(0xFFEF9A9A),
    ledgerLine = Color(0xFF1D1B19).copy(alpha = 0.05f),
    marginLine = Color(0xFF9E3E37).copy(alpha = 0.08f)
)

val DarkUnNestColors = UnNestColors(
    background = Color(0xFF181715),
    card = Color(0xFF2A2724),
    cardBorder = Color(0xFF3D3934),
    segmentTrack = Color(0xFF221F1C),
    textPrimary = Color(0xFFECE6DA),
    textSecondary = Color(0xFFB5AEA3),
    accent = Color(0xFFB3392F),
    onAccent = Color(0xFFFFFFFF),
    accentTint = Color(0xFF3A2422),
    accentOnTint = Color(0xFFEF8A80),
    success = Color(0xFF9DBFA3),
    successTint = Color(0xFF1F2A22),
    warning = Color(0xFFE0B57A),
    warningTint = Color(0xFF2E2618),
    error = Color(0xFFEF8A80),
    errorTint = Color(0xFF3A2422),
    terminalBg = Color(0xFF0F1110),
    terminalText = Color(0xFFCBE2D4),
    folderArt = Color(0xFFEF8A80),
    folderArtMuted = Color(0xFF7A4A45),
    ledgerLine = Color(0xFFECE6DA).copy(alpha = 0.04f),
    marginLine = Color(0xFFEF8A80).copy(alpha = 0.10f)
)

val LocalUnNestColors = staticCompositionLocalOf { LightUnNestColors }

object UnNestTheme {
    val colors: UnNestColors
        @Composable
        @ReadOnlyComposable
        get() = LocalUnNestColors.current
}
