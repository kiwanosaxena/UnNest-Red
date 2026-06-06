package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PaperSurface,             // Soft ivory primary accents
    secondary = InkGold,                // Ochred highlights
    tertiary = InkRedWax,               // Wax stamp accent
    background = Color(0xFF181715),     // Deep charcoal/walnut stained paper background
    surface = Color(0xFF24221F),        // Matte walnut bookboard
    onPrimary = Color(0xFF181715),
    onSecondary = Color(0xFF181715),
    onBackground = Color(0xFFECE6DA),
    onSurface = Color(0xFFECE6DA)
)

private val LightColorScheme = lightColorScheme(
    primary = ThemeRed,                 // Signature red for main elements
    secondary = InkGold,                // Ochre/Gold highlight
    tertiary = InkRedWax,               // High precision red warning/error accent
    background = PaperBackground,       // Clean off-white background
    surface = PaperSurface,             // Pure white cards as per mockup
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = InkCharcoal,         // Sharp slate black text
    onSurface = InkCharcoal
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic system color to preserve signature Classic Paper finish
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
