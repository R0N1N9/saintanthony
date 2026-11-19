package app.saintanthony.finder.ui

import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
    val target = m.target
    if (target == null) {
        Hint("Pick an item from Items to start finding it.", Modifier.fillMaxWidth().padding(32.dp))
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

    val white = remember { Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; isFakeBoldText = true } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Canvas(Modifier.fillMaxWidth(0.78f).aspectRatio(1f)) {
            val r = size.minDimension / 2
            drawCircle(Panel2, r)
            drawCircle(if (near) Gold else Panel, r, style = Stroke(if (near) 8f else 3f))
            // Compass rose turns against the phone so N stays on north.
            rotate(-h) {
                white.textSize = 13.sp.toPx()
                val inset = r - 12.dp.toPx()
                listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (label, a) ->
                    rotate(a) {
                        white.color = (if (label == "N") Danger else Muted).toArgb()
                        drawContext.canvas.nativeCanvas.drawText(label, center.x, center.y - inset + white.textSize, white)
                    }
                }
            }
            if (rel != null) arrow(rel, r * 0.82f, if (abs(rel) < 15) 1f else 0.55f)
            drawCircle(Bg, r * 0.44f, Offset(center.x, center.y + r * 0.12f))
            white.color = Ink.toArgb()
            white.textSize = 24.sp.toPx()
            drawContext.canvas.nativeCanvas.drawText(Geo.fmtDist(dist), center.x, center.y + r * 0.14f, white)
            if (brg != null) {
                white.color = Muted.toArgb()
                white.textSize = 12.sp.toPx()
                drawContext.canvas.nativeCanvas.drawText(
                    "${Geo.cardinal(brg)} · ${brg.roundToInt()}°", center.x, center.y + r * 0.14f + 18.sp.toPx(), white,
                )
            }
        }

        Text(
            when {
                rel == null -> "Waiting for GPS…"
                heading.isNaN() -> "Waiting for the compass…"
                near -> "You're right on top of it — look around here ✨"
                abs(rel) < 15 -> "Straight ahead — follow the arrow"
                abs(rel) > 150 -> "It's behind you — turn around"
                else -> "Turn ${if (rel > 0) "right" else "left"} ${abs(rel).roundToInt()}°"
            },
            fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
        if (m.orientation.needsCalibration) {
            Text("Compass needs calibrating — wave the phone in a figure-8.", color = Gold, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        FilterChip(
            selected = m.guide.on,
            onClick = { m.guide.set(!m.guide.on) },
            label = { Text("🔔 Buzz & beep guide") },
        )
        Hint(
            "With the guide on you can turn the phone face-down: slowly sweep it around and it " +
                "double-buzzes when it points at your item.",
        )
        Button(onClick = {
            if (pos != null) m.save(Draft(target.id, target.name, target.emoji, "Found here"), pos.latLng, pos.acc)
            m.guide.set(false)
            Toast.makeText(ctx, "🎉 Found ${target.name}! Location updated.", Toast.LENGTH_SHORT).show()
            onFound()
        }) { Text("🎉 Found it!", fontWeight = FontWeight.Bold) }
    }
}
