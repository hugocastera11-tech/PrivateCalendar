package com.example.privatecalendar.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppTheme {
    DEFAULT, FOREST, LAVENDER, MIDNIGHT, ROSE
}

// Neutrals for Minimalism
private val LightBackground = Color(0xFFFBFBFB)
private val LightSurface = Color(0xFFFFFFFF)
private val DarkBackground = Color(0xFF0F0F0F)
private val DarkSurface = Color(0xFF181818)

@Composable
fun PrivateCalendarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    theme: AppTheme = AppTheme.DEFAULT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getCustomColorScheme(darkTheme, theme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private fun getCustomColorScheme(darkTheme: Boolean, theme: AppTheme): ColorScheme {
    val themeColor = when(theme) {
        AppTheme.DEFAULT -> if (darkTheme) Color(0xFFF2F2F2) else Color(0xFF1A1A1A)
        AppTheme.FOREST -> Color(0xFF2E7D32)
        AppTheme.LAVENDER -> Color(0xFF673AB7)
        AppTheme.MIDNIGHT -> Color(0xFF2196F3)
        AppTheme.ROSE -> Color(0xFFD81B60)
    }

    return if (darkTheme) {
        darkColorScheme(
            primary = themeColor,
            onPrimary = if (theme == AppTheme.DEFAULT) Color.Black else Color.White,
            primaryContainer = themeColor.copy(alpha = 0.2f),
            onPrimaryContainer = themeColor,
            secondary = Color(0xFF9E9E9E),
            background = if (theme == AppTheme.MIDNIGHT) Color.Black else DarkBackground,
            surface = DarkSurface,
            surfaceVariant = Color(0xFF242424),
            outlineVariant = Color(0xFF333333)
        )
    } else {
        lightColorScheme(
            primary = themeColor,
            onPrimary = if (theme == AppTheme.DEFAULT) Color.White else Color.White,
            primaryContainer = themeColor.copy(alpha = 0.1f),
            onPrimaryContainer = themeColor,
            secondary = Color(0xFF757575),
            background = LightBackground,
            surface = LightSurface,
            surfaceVariant = Color(0xFFF5F5F5),
            outlineVariant = Color(0xFFEEEEEE)
        )
    }
}
