package app.saintanthony.finder.ui

import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.saintanthony.finder.AppModel
import app.saintanthony.finder.Draft
import app.saintanthony.finder.Geo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun CompassScreen(m: AppModel, onFound: () -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val dust = LocalDust.current
    val target = m.target
    if (target == null) {
        Hint("Choose something on the Items tab and it will appear here.", Modifier.fillMaxWidth().padding(40.dp))
        return
    }
    val loc = target.last!!
    val pos = m.location.pos
    val heading = m.orientation.heading
    val h = if (heading.isNaN()) 0f else heading
    val dist = pos?.let { Geo.distance(it.latLng, loc.latLng) }
    val brg = pos?.let { Geo.bearing(it.latLng, loc.latLng) }
    val rel = brg?.let { Geo.angleDiff(it, h) }
    val near = dist != null && pos != null && dist < max(8f, pos.acc)
    val north = remember { Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; isFakeBoldText = true } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TargetCard(m)

        Canvas(Modifier.padding(top = 8.dp).fillMaxWidth(0.7f).aspectRatio(1f)) {
            val r = size.minDimension / 2
            drawCircle(c.surface, r)
            drawCircle(if (near) dust else c.outlineVariant, r - 1.dp.toPx(), style = Stroke(if (near) 4.dp.toPx() else 1.5.dp.toPx()))
            // Tick marks and N turn against the phone, so north stays north.
            rotate(-h) {
                for (i in 0 until 12) {
                    rotate(i * 30f) {
                        val major = i % 3 == 0
                        drawLine(
                            if (major) c.onSurfaceVariant else c.outline,
                            Offset(center.x, center.y - r + 10.dp.toPx()),
                            Offset(center.x, center.y - r + (if (major) 22 else 17).dp.toPx()),
                            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                        )
                    }
                }
                north.color = c.error.toArgb()
                north.textSize = 14.sp.toPx()
                drawContext.canvas.nativeCanvas.drawText("N", center.x, center.y - r + 40.dp.toPx(), north)
            }
            if (rel != null && !heading.isNaN()) arrow(rel, r * 0.5f, dust, if (abs(rel) < 15) 1f else 0.6f)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Geo.fmtDist(dist), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    rel == null -> "Waiting for your location…"
                    heading.isNaN() -> "Waiting for the compass…"
                    near -> "You're right on top of it. Look around here."
                    abs(rel) < 15 -> "Straight ahead"
                    abs(rel) > 150 -> "It's behind you — turn around"
                    else -> "Turn ${if (rel > 0) "right" else "left"} ${abs(rel).roundToInt()}°"
                },
                style = MaterialTheme.typography.titleMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            if (brg != null) {
                Text("${Geo.cardinal(brg)} · ${brg.roundToInt()}°", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            }
        }
        if (m.orientation.needsCalibration) {
            Text(
                "Compass needs calibrating — move the phone in a figure-8.",
                style = MaterialTheme.typography.bodySmall, color = c.error, textAlign = TextAlign.Center,
            )
        }

        Surface(shape = MaterialTheme.shapes.large, color = c.surface, shadowElevation = 1.dp) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Vibration, null, tint = c.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text("Guide by buzz and beep", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Works face-down: sweep the phone around and it double-buzzes when it points at your item.",
                        style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                    )
                }
                Switch(checked = m.guide.on, onCheckedChange = { m.guide.set(it) })
            }
        }

        Button(
            onClick = {
                if (pos != null) m.save(Draft(target.id, target.name, target.icon, "Found here"), pos.latLng, pos.acc)
                m.guide.set(false)
                Toast.makeText(ctx, "Found ${target.name}. Location updated.", Toast.LENGTH_SHORT).show()
                onFound()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp))
            Text("Found it", Modifier.padding(start = 8.dp))
        }
    }
}
