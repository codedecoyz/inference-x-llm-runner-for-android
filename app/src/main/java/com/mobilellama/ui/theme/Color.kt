package com.mobilellama.ui.theme

import androidx.compose.ui.graphics.Color

// Brand Palette - Deep Purple Neon
val DeepBlackPurple = Color(0xFF10002b) // Background
val DarkTonalPurple = Color(0xFF240046) // Surface
val VibrantPurple = Color(0xFF7b2cbf)   // Primary
val LightLavender = Color(0xFFc77dff)   // Secondary/Accent
val HighlightWhitePurple = Color(0xFFe0aaff) // OnPrimary/Glow/Text
val SubtleDivider = Color(0xFF3c096c)   // Outline

// Semantic Mapping
val PrimaryColor = VibrantPurple
val OnPrimaryColor = HighlightWhitePurple
val SecondaryColor = LightLavender
val TertiaryColor = HighlightWhitePurple // Using Highlight for tertiary accents if needed
val BackgroundDark = DeepBlackPurple
val SurfaceDark = DarkTonalPurple
val SurfaceVariantDark = DarkTonalPurple // match surface for now or use SubtleDivider if needed for borders
val OnBackgroundDark = HighlightWhitePurple
val OnSurfaceDark = HighlightWhitePurple
val OnSurfaceVariant = LightLavender
val OutlineColor = SubtleDivider

