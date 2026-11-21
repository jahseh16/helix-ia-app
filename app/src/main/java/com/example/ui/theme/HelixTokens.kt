package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Design tokens de Helix: única fuente de verdad para colores, formas y estilos de texto.
 * [MyApplicationTheme] construye el ColorScheme de Material3 a partir de estos valores.
 *
 * Estilo: negro azulado profundo, acento azul (sistema iOS en modo oscuro) y esquinas
 * muy redondeadas.
 */

// Fondos y superficies (de más profundo a más elevado)
val DarkBackground = Color(0xFF05070D)
val AppBg = DarkBackground
val AppSurface = Color(0xFF0D1220)
val AppSurface2 = Color(0xFF141B2D)
val AppSurface3 = Color(0xFF1D2640)
val AppStroke = Color(0xFF222C47)
val AppCodeBg = Color(0xFF080B14)

// Texto
val AppText = Color(0xFFEAF0FF)
val AppTextMuted = Color(0xFF8D97B0)

// Acento azul
val AppAccent = Color(0xFF0A84FF)
val AppAccentSoft = Color(0xFF64A8FF)
val AppCyan = Color(0xFF64D2FF)
val AppBubbleUser = Color(0xFF0A6CF0)

// Semánticos
val AppGreen = Color(0xFF30D158)
val AppPink = Color(0xFFFF6482)
val AppDanger = Color(0xFFFF453A)

// Tipografías
val UiSans = FontFamily.SansSerif
val UiMono = FontFamily.Monospace

// Formas estilo iOS: radios generosos
val ShapeSmall = RoundedCornerShape(12.dp)
val ShapeMedium = RoundedCornerShape(18.dp)
val ShapeCard = RoundedCornerShape(24.dp)
val ShapeSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
val ShapePill = RoundedCornerShape(percent = 50)

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
