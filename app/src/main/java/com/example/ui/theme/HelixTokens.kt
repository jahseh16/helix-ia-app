package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Design tokens de Helix: única fuente de verdad para colores y estilos de texto.
 * [MyApplicationTheme] construye el ColorScheme de Material3 a partir de estos valores,
 * de modo que los componentes Material y los componentes propios comparten paleta.
 */

// Paleta base
val DarkBackground = Color(0xFF0E0F16)
val TitaniumGray = Color(0xFF1E2A29)
val GlacierCyan = Color(0xFF4CCFE1)

// Superficies
val AppBg = DarkBackground
val AppSurface = Color(0xFF161F1E)
val AppSurface2 = Color(0xFF1E2726)
val AppSurface3 = Color(0xFF24302F)
val AppStroke = Color(0xFF2B3A39)

// Texto
val AppText = Color(0xFFE4F3F5)
val AppTextMuted = Color(0xFF8BA2A5)

// Acentos y semánticos
val AppAccent = GlacierCyan
val AppAccentSoft = GlacierCyan.copy(alpha = 0.6f)
val AppCyan = GlacierCyan
val AppGreen = Color(0xFF4DDF9B)
val AppPink = Color(0xFFE26180)
val AppDanger = Color(0xFFFF627C)

// Tipografías
val UiSans = FontFamily.SansSerif
val UiMono = FontFamily.Monospace

val TitleStyle = TextStyle(
    fontFamily = UiSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.01).em,
    color = AppText
)

val BodyStyle = TextStyle(
    fontFamily = UiSans,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp,
    color = AppText
)

val MetaStyle = TextStyle(
    fontFamily = UiSans,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    color = AppTextMuted
)
