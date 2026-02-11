package com.mobilellama.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark-First Design
// We use the same deep palette for both light/dark system settings to enforce branding
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryColor,
    onPrimary = OnPrimaryColor,
    primaryContainer = SurfaceDark, // Using SurfaceDark as container base
    onPrimaryContainer = OnPrimaryColor,
    secondary = SecondaryColor,
    onSecondary = DeepBlackPurple, // Contrast on secondary
    tertiary = TertiaryColor,
    onTertiary = DeepBlackPurple,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceDark, // Keep consistent
    onSurfaceVariant = OnSurfaceVariant,
    outline = OutlineColor,
    error = Color(0xFFCF6679),
    onError = Color.Black
)

private val LightColorScheme = DarkColorScheme // Enforce dark theme branding

@Composable
fun MobileLlamaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
