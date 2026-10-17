package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun getAccentColors(accent: String, isDark: Boolean): Triple<Color, Color, Color> {
    return when (accent) {
        "BLUE" -> Triple(BluePrimary, BlueSecondary, if (isDark) BlueContainer else Color(0xFFDBEAFE))
        "PURPLE" -> Triple(PurplePrimary, PurpleSecondary, if (isDark) PurpleContainer else Color(0xFFF3E8FF))
        "ORANGE" -> Triple(OrangePrimary, OrangeSecondary, if (isDark) OrangeContainer else Color(0xFFFFEDD5))
        "EMERALD" -> Triple(EmeraldPrimary, EmeraldSecondary, if (isDark) EmeraldContainer else Color(0xFFD1FAE5))
        else -> Triple(TealPrimary, TealSecondary, if (isDark) TealContainer else Color(0xFFCCFBF1))
    }
}

@Composable
fun TaskFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColorName: String = "TEAL",
    content: @Composable () -> Unit,
) {
    val (primary, secondary, container) = getAccentColors(accentColorName, darkTheme)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.Black,
            primaryContainer = container,
            onPrimaryContainer = secondary,
            secondary = secondary,
            onSecondary = Color.Black,
            background = DarkBackground,
            onBackground = DarkTextPrimary,
            surface = DarkSurface,
            onSurface = DarkTextPrimary,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkTextSecondary,
            outline = DarkBorder
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = container,
            onPrimaryContainer = primary,
            secondary = secondary,
            onSecondary = Color.White,
            background = LightBackground,
            onBackground = LightTextPrimary,
            surface = LightSurface,
            onSurface = LightTextPrimary,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightTextSecondary,
            outline = LightBorder
        )
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
