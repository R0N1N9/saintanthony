package app.saintanthony.finder.ui

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.saintanthony.finder.AppModel
import app.saintanthony.finder.Geo
import app.saintanthony.finder.ThemeMode
import kotlinx.coroutines.delay

// Two colour families: a calm indigo for the interface, and "dust" gold kept
// for the things that guide you — the arrow, the trail, the signal meter.
private val Light = lightColorScheme(
    primary = Color(0xFF4A3FD0), onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E4FF), onPrimaryContainer = Color(0xFF17124D),
    secondaryContainer = Color(0xFFECEBF5), onSecondaryContainer = Color(0xFF26244A),
    background = Color(0xFFF6F6FA), onBackground = Color(0xFF16152B),
    surface = Color.White, onSurface = Color(0xFF16152B),
    surfaceVariant = Color(0xFFEEEEF4), onSurfaceVariant = Color(0xFF62617A),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF3F3F8),
    surfaceContainerHighest = Color(0xFFEAEAF2),
    outline = Color(0xFFC9C8D6), outlineVariant = Color(0xFFE3E2EC),
    error = Color(0xFFC7373F),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFABA4FF), onPrimary = Color(0xFF1A1552),
    primaryContainer = Color(0xFF2F2A6B), onPrimaryContainer = Color(0xFFE4E1FF),
    secondaryContainer = Color(0xFF262536), onSecondaryContainer = Color(0xFFE2E0F5),
    background = Color(0xFF0F0E17), onBackground = Color(0xFFECEBF7),
    surface = Color(0xFF191824), onSurface = Color(0xFFECEBF7),
    surfaceVariant = Color(0xFF232232), onSurfaceVariant = Color(0xFFA3A1BA),
    surfaceContainerLowest = Color(0xFF0F0E17), surfaceContainerLow = Color(0xFF191824),
    surfaceContainer = Color(0xFF191824), surfaceContainerHigh = Color(0xFF232232),
    surfaceContainerHighest = Color(0xFF2B2A3B),
    outline = Color(0xFF45445A), outlineVariant = Color(0xFF2B2A3B),
    error = Color(0xFFFF8A8A),
)

/** The guiding gold. Deeper in light mode so it still reads on white. */
val LocalDust = staticCompositionLocalOf { Color(0xFFFFD666) }
val LocalDark = staticCompositionLocalOf { true }

@Composable
fun StAnthonyTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Keep the clock and navigation icons readable against our background.
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(
        LocalDust provides if (dark) Color(0xFFFFD666) else Color(0xFFF0A81C),
        LocalDark provides dark,
    ) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(32.dp),
            ),
            content = content,
        )
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = modifier,
    )
}

/** An icon sitting in a soft tinted circle. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 46.dp, active: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier.size(size).background(if (active) c.primary else c.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) c.onPrimary else c.onPrimaryContainer, modifier = Modifier.size(size * 0.5f))
    }
}

/** The selected item: what it is, when it was last seen, how far away. */
@Composable
fun TargetCard(m: AppModel, modifier: Modifier = Modifier) {
    val target = m.target ?: return
    val loc = target.last ?: return
    val pos = m.location.pos
    val now = rememberNow()
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(itemIcon(target.icon), size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(target.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Last seen ${Geo.timeAgo(loc.t, now)}" + if (loc.note.isNotEmpty()) " · ${loc.note}" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                Geo.fmtDist(pos?.let { Geo.distance(it.latLng, loc.latLng) }),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Hot/cold bar: fills and warms as the signal strengthens. [fraction] is 0..1. */
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier) {
    val dust = LocalDust.current
    Box(modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, dust, Color(0xFFFF8A4C))))
        )
    }
}

/** Signal strength (dBm) as a 0..1 fill for [Meter]. */
fun meterFraction(rssi: Float): Float = ((rssi + 100f) / 60f).coerceIn(0.04f, 1f)

/** Four little bars, like a phone's reception icon. */
@Composable
fun SignalBars(rssi: Int?, modifier: Modifier = Modifier) {
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant
    val level = when {
        rssi == null -> 0
        rssi > -60 -> 4
        rssi > -72 -> 3
        rssi > -84 -> 2
        else -> 1
    }
    Canvas(modifier.size(22.dp, 18.dp)) {
        val gap = size.width * 0.1f
        val w = (size.width - gap * 3) / 4
        for (i in 0 until 4) {
            val h = size.height * (0.4f + 0.2f * i)
            drawRoundRect(
                if (i < level) on else off,
                topLeft = Offset(i * (w + gap), size.height - h), size = Size(w, h),
                cornerRadius = CornerRadius(w / 2),
            )
        }
    }
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

/** The pointer arrow, centred in the drawing area, sized to [radius] and turned by [degrees]. */
fun DrawScope.arrow(degrees: Float, radius: Float, color: Color, alpha: Float = 1f) {
    val c = center
    val s = radius / 50f
    val path = Path().apply {
        moveTo(c.x, c.y - 46 * s)
        lineTo(c.x + 27 * s, c.y + 32 * s)
        lineTo(c.x, c.y + 16 * s)
        lineTo(c.x - 27 * s, c.y + 32 * s)
        close()
    }
    rotate(degrees, c) { drawPath(path, color, alpha = alpha) }
}
