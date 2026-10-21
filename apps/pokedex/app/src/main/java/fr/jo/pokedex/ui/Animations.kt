package fr.jo.pokedex.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.jo.pokedex.ui.theme.PokedexColors

@Composable
fun Pokeball(modifier: Modifier = Modifier, spinning: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "pokeball")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing)),
        label = "angle",
    )
    Canvas(modifier.rotate(if (spinning) angle else 0f)) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        val stroke = r * 0.08f
        val topLeft = Offset(c.x - r, c.y - r)
        val box = Size(r * 2, r * 2)
        drawArc(PokedexColors.Red, 180f, 180f, useCenter = true, topLeft = topLeft, size = box)
        drawArc(Color.White, 0f, 180f, useCenter = true, topLeft = topLeft, size = box)
        drawRect(PokedexColors.Ink, Offset(c.x - r, c.y - stroke / 2), Size(r * 2, stroke))
        drawCircle(PokedexColors.Ink, r, c, style = Stroke(stroke))
        drawCircle(PokedexColors.Ink, r * 0.3f, c)
        drawCircle(Color.White, r * 0.18f, c)
    }
}

@Composable
fun ListeningAnimation(modifier: Modifier = Modifier, iconSize: Dp = 96.dp) {
    val transition = rememberInfiniteTransition(label = "listening")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2
            for (i in 0 until 3) {
                val p = (phase + i / 3f) % 1f
                drawCircle(
                    color = PokedexColors.Blue.copy(alpha = (1f - p) * 0.6f),
                    radius = maxRadius * (0.35f + 0.65f * p),
                    style = Stroke(width = 10f),
                )
            }
            drawCircle(PokedexColors.Blue, maxRadius * 0.33f)
        }
        Icon(PokedexIcons.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}
