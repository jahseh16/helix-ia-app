package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AppAccent
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private class Star(val x: Float, val y: Float, val radius: Float, val base: Float, val phase: Float, val speed: Int, val blue: Boolean)

/**
 * Fondo negro puro con un campo de estrellas que titilan suavemente y un resplandor azul muy tenue
 * arriba. Las posiciones son fijas (semilla constante) para que no "salten" al recomponer.
 */
@Composable
fun StarfieldBackground(modifier: Modifier = Modifier, starCount: Int = 140, content: @Composable BoxScope.() -> Unit) {
    val stars = remember(starCount) {
        val rnd = Random(42)
        List(starCount) {
            val big = rnd.nextFloat() < 0.08f
            Star(
                x = rnd.nextFloat(), y = rnd.nextFloat(),
                radius = if (big) 1.6f + rnd.nextFloat() * 0.8f else 0.6f + rnd.nextFloat() * 0.8f,
                base = 0.25f + rnd.nextFloat() * 0.55f,
                phase = rnd.nextFloat(),
                speed = 1 + rnd.nextInt(3),
                blue = rnd.nextFloat() < 0.18f
            )
        }
    }
    val time by rememberInfiniteTransition(label = "stars").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "twinkle"
    )

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(AppAccent.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width / 2f, 0f),
                    radius = size.width * 1.1f
                )
            )
            stars.forEach { s ->
                val twinkle = 0.55f + 0.45f * sin(2 * PI * (time * s.speed + s.phase)).toFloat()
                val color = if (s.blue) Color(0xFF9CC8FF) else Color.White
                drawCircle(
                    color = color.copy(alpha = (s.base * twinkle).coerceIn(0f, 1f)),
                    radius = s.radius * density,
                    center = Offset(s.x * size.width, s.y * size.height)
                )
            }
        }
        content()
    }
}
