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

// Fondo negro puro (sobre él se dibuja el campo de estrellas) y superficies "glass" estilo iOS:
// translúcidas y neutras, para que las estrellas se intuyan detrás. El azul queda solo para acentos.
val DarkBackground = Color(0xFF000000)
val AppBg = DarkBackground
val AppSurface = Color(0xB8121318)    // paneles, hojas y tarjetas (≈72 % opaco)
val AppSurface2 = Color(0xD11C1D23)   // controles y diálogos (≈82 % opaco)
val AppSurface3 = Color(0xFF2A2B33)   // elementos elevados opacos
val AppStroke = Color(0x26FFFFFF)     // borde fino blanco 15 %: el "filo" del cristal
val AppCodeBg = Color(0xE6060608)

// Cristal: relleno y brillo superior para paneles glass
val GlassFill = Color(0x14FFFFFF)
val GlassHighlight = Color(0x1FFFFFFF)

// Texto (etiquetas del sistema iOS en modo oscuro)
val AppText = Color(0xFFF2F2F7)
val AppTextMuted = Color(0xFF98989F)

// Acento azul
val AppAccent = Color(0xFF0A84FF)
val AppAccentSoft = Color(0xFF64A8FF)
val AppCyan = Color(0xFF64D2FF)
val AppBubbleUser = Color(0xFF0A84FF)

// Semánticos
val AppGreen = Color(0xFF30D158)
val AppPink = Color(0xFFFF6482)
val AppDanger = Color(0xFFFF453A)
val AppWarning = Color(0xFFFFB340)

// Resaltado de sintaxis
val SynKeyword = Color(0xFFC792EA)   // morado
val SynType = Color(0xFF6CB6FF)      // azul
val SynFunction = Color(0xFF64D2FF)  // cian
val SynString = Color(0xFF8CE99A)    // verde
val SynNumber = Color(0xFFFFB454)    // ámbar
val SynComment = Color(0xFF6B7794)   // gris tenue
val SynAnnotation = Color(0xFFFF8FB1)

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
