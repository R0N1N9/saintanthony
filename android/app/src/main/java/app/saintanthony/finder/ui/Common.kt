package app.saintanthony.finder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

val Bg = Color(0xFF141028)
val Panel = Color(0xFF1F1A3A)
val Panel2 = Color(0xFF2A2450)
val Ink = Color(0xFFF4F1FF)
val Muted = Color(0xFFA9A2CC)
val Gold = Color(0xFFFFD666)
val GoldInk = Color(0xFF2B2100)
val Danger = Color(0xFFFF7A7A)

@Composable
fun StAnthonyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold, onPrimary = GoldInk,
            secondaryContainer = Panel2, onSecondaryContainer = Gold,
            background = Bg, onBackground = Ink,
            surface = Panel, onSurface = Ink,
            surfaceVariant = Panel2, onSurfaceVariant = Muted,
            error = Danger,
        ),
        content = content,
    )
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = modifier)
}

/** Wall-clock time that ticks once a second, for "5 min ago" labels and signal timeouts. */
@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

/** The pointer arrow, drawn to fill a circle of [radius] and turned by [degrees]. */
fun DrawScope.arrow(degrees: Float, radius: Float, alpha: Float = 1f) {
    val c = center
    val s = radius / 50f
    val path = Path().apply {
        moveTo(c.x, c.y - 44 * s)
        lineTo(c.x + 26 * s, c.y + 30 * s)
        lineTo(c.x, c.y + 14 * s)
        lineTo(c.x - 26 * s, c.y + 30 * s)
        close()
    }
    rotate(degrees, c) { drawPath(path, Gold, alpha = alpha) }
}

@Composable
fun ArrowDial(degrees: Float, alpha: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawCircle(Panel2, radius = size.minDimension / 2, center = Offset(size.width / 2, size.height / 2))
        arrow(degrees, size.minDimension * 0.36f, alpha)
    }
}
