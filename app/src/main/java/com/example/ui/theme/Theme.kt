package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB3392F),
    onPrimary = Color(0xFFFFFFFF),
    surface = Color(0xFF2A2724),
    surfaceContainerHigh = Color(0xFF2A2724),
    onSurface = Color(0xFFECE6DA),
    background = Color(0xFF181715),
    onBackground = Color(0xFFECE6DA),
    secondary = Color(0xFFE0B57A),
    error = Color(0xFFEF8A80)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFC52F24),
    secondary = Color(0xFFB37E40),
    tertiary = Color(0xFF9E3E37),
    background = Color(0xFFF5F3ED),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1D1B19),
    onSurface = Color(0xFF1D1B19)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic system color to preserve signature Classic Paper finish
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val unNestColors = if (darkTheme) DarkUnNestColors else LightUnNestColors

    CompositionLocalProvider(LocalUnNestColors provides unNestColors) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
