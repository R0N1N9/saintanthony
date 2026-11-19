package app.saintanthony.finder.ui

import android.Manifest
import android.content.Context
import android.graphics.Paint
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.saintanthony.finder.AppModel
import app.saintanthony.finder.Geo
import app.saintanthony.finder.Kind
import app.saintanthony.finder.Tab
import app.saintanthony.finder.has
import app.saintanthony.finder.proximity
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

private const val CAM_HEIGHT = 1.4 // metres; phone held at chest height
private const val SPACING = 2.0 // metres between dust puffs
private const val FLOW = 1.3 // m/s the dust drifts toward the goal
private const val SIGNAL_REACH = 20.0 // metres of trail drawn toward a signal (its distance is unknown)

private val DustCore = Color(1f, 0.98f, 0.86f)
private val DustMid = Color(1f, 0.84f, 0.4f)
private val DustEdge = Color(1f, 0.7f, 0.24f)

/** Where a trail leads: [east]/[north] metres from the viewer, and what to call it. */
private class Goal(val east: Double, val north: Double, val label: String, val atGoal: Boolean)

/**
 * Camera view with a pixie-dust trail on the ground. It leads either to the
 * selected item's saved spot, or in the direction a tracked Bluetooth / Wi-Fi
 * signal is strongest.
 */
@Composable
fun ArScreen(m: AppModel, go: (Tab) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val c = MaterialTheme.colorScheme
    val s = m.signals
    var granted by remember { mutableStateOf(ctx.has(Manifest.permission.CAMERA)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    val vfov = remember { cameraVerticalFov(ctx) }

    val target = m.target
    val pos = m.location.pos
    val hasSignal = s.trackedId != null
    val signalMode = hasSignal && (m.arSignal || target == null)
    val dir = s.direction
    val sweep = s.sweep

    // Entering signal mode with no bearing yet: start scanning straight away.
    LaunchedEffect(signalMode, s.trackedId) {
        if (signalMode && s.direction == null && s.sweep == null) s.startSweep()
    }

    val itemPainter = rememberVectorPainter(itemIcon(target?.icon ?: "box"))
    val signalPainter = rememberVectorPainter(if (s.trackedKind == Kind.WIFI) Icons.Rounded.Wifi else Icons.Rounded.Bluetooth)

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF07051A), Color(0xFF141028), Color(0xFF3B2F7A))))) {
        if (granted) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { cx ->
                PreviewView(cx).also { pv ->
                    val future = ProcessCameraProvider.getInstance(cx)
                    future.addListener({
                        try {
                            val provider = future.get()
                            val preview = Preview.Builder().build()
                            preview.setSurfaceProvider(pv.surfaceProvider)
                            provider.unbindAll()
                            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                        } catch (e: Exception) {
                            // No usable camera: the gradient backdrop stays.
                        }
                    }, ContextCompat.getMainExecutor(cx))
                }
            })
            DisposableEffect(Unit) {
                onDispose {
                    try { ProcessCameraProvider.getInstance(ctx).get().unbindAll() } catch (e: Exception) { /* never bound */ }
                }
            }
        }

        // ---------- what's drawn over the camera ----------
        if (signalMode) {
            if (sweep != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val heading = m.orientation.heading.let { if (it.isNaN()) 0f else it }
                    sweepRing(sweep, heading, 96.dp.toPx(), DustMid, Color.White.copy(alpha = 0.28f), Offset(size.width / 2, size.height * 0.38f))
                }
            } else if (dir != null && dir.clear) {
                Trail(m, vfov, signalPainter) {
                    val b = Math.toRadians(dir.bearing.toDouble())
                    Goal(sin(b) * SIGNAL_REACH, cos(b) * SIGNAL_REACH, "${s.trackedName} · this way", atGoal = false)
                }
            }
        } else if (target != null) {
            Trail(m, vfov, itemPainter) {
                val p = m.location.pos
                val loc = m.target?.last
                if (p == null || loc == null) null else {
                    val (e, n) = Geo.toLocal(p.latLng, loc.latLng)
                    Goal(e, n, "${m.target?.name} · ${Geo.fmtDist(hypot(e, n))}", atGoal = true)
                }
            }
        }

        // ---------- item / signal switch ----------
        if (target != null && hasSignal) {
            Row(Modifier.align(Alignment.TopCenter).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val chip = FilterChipDefaults.filterChipColors(containerColor = c.surface.copy(alpha = 0.9f))
                FilterChip(selected = !signalMode, onClick = { m.arSignal = false }, label = { Text(target.name, maxLines = 1) }, colors = chip)
                FilterChip(selected = signalMode, onClick = { m.arSignal = true }, label = { Text(s.trackedName, maxLines = 1) }, colors = chip)
            }
        }

        // ---------- bottom panel ----------
        Surface(
            Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.large, color = c.surface.copy(alpha = 0.94f),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    signalMode -> {
                        val now = rememberNow()
                        val rssi = s.rssi
                        val lost = rssi == null || now - s.lastHeard > 8000
                        Text(s.trackedName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Meter(if (lost || rssi == null) 0f else meterFraction(rssi))
                        Text(
                            when {
                                lost || rssi == null -> "No signal right now. It may be out of range or asleep."
                                sweep != null && s.trackedKind == Kind.WIFI ->
                                    "Hold the phone upright, close to your chest, and turn very slowly in a full circle — Wi-Fi only updates every few seconds."
                                sweep != null -> "Hold the phone upright, close to your chest, and turn slowly in a full circle until the ring fills."
                                dir == null -> proximity(rssi)
                                !dir.clear -> "No clear direction from here. Take a few steps and scan again."
                                else -> "${proximity(rssi)}. The trail shows where the signal is strongest, not how far — scan again as you get closer."
                            },
                            style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(onClick = { if (sweep != null) s.cancelSweep() else s.startSweep() }, modifier = Modifier.weight(1f)) {
                                Text(if (sweep != null) "Cancel" else "Scan again")
                            }
                            OutlinedButton(onClick = { go(Tab.SIGNAL) }, modifier = Modifier.weight(1f)) { Text("Signal details") }
                        }
                    }
                    target != null -> {
                        val dist = pos?.let { Geo.distance(it.latLng, target.last!!.latLng) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconBadge(itemIcon(target.icon), size = 38.dp)
                            Text(target.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Geo.fmtDist(dist), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        val msg = when {
                            pos == null || dist == null -> "Waiting for your location…"
                            dist < max(8f, pos.acc) -> "You're as close as GPS can tell. Look around here."
                            m.orientation.pitch > 50 || m.orientation.pitch < -60 -> "Hold the phone upright to see the trail."
                            !granted -> "Camera access is off, so the trail is shown without the camera view."
                            else -> null
                        }
                        if (msg != null) Text(msg, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                    }
                    else -> {
                        Text("Nothing to follow yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Choose one of your things, or pick a Bluetooth or Wi-Fi signal, and a trail will lead you toward it.",
                            style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(onClick = { go(Tab.ITEMS) }, modifier = Modifier.weight(1f)) { Text("Your things") }
                            FilledTonalButton(onClick = { m.arSignal = true; go(Tab.SIGNAL) }, modifier = Modifier.weight(1f)) { Text("Signals") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Trail(m: AppModel, vfov: Float, icon: VectorPainter, goal: () -> Goal?) {
    val seconds by produceState(0.0) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1e9 }
    }
    val text = remember { Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; isFakeBoldText = true } }

    Canvas(Modifier.fillMaxSize()) {
        val t = seconds
        val g = goal() ?: return@Canvas
        val heading = m.orientation.heading
        if (heading.isNaN()) return@Canvas
        val pitch = m.orientation.pitch
        val w = size.width
        val h = size.height
        val f = h / 2 / tan(Math.toRadians(vfov / 2.0)).toFloat()

        /** Screen position of a point [e] metres east and [n] north of us, [height] metres above ground. */
        fun project(e: Double, n: Double, height: Double): Triple<Float, Float, Double>? {
            val d = hypot(e, n)
            val rel = Geo.angleDiff(Math.toDegrees(atan2(e, n)).toFloat(), heading)
            if (abs(rel) > 80) return null
            val dv = Math.toDegrees(atan2(height - CAM_HEIGHT, d)).toFloat() - pitch
            if (abs(dv) > 80) return null
            return Triple(
                w / 2 + tan(Math.toRadians(rel.toDouble())).toFloat() * f,
                h / 2 - tan(Math.toRadians(dv.toDouble())).toFloat() * f,
                d,
            )
        }

        val dist = hypot(g.east, g.north)
        if (dist < 0.5) return@Canvas
        val ue = g.east / dist
        val un = g.north / dist

        // --- pixie dust, far to near so the close puffs draw on top ---
        val reach = min(dist, 45.0)
        val offset = (t * FLOW) % SPACING
        val count = floor((reach - offset) / SPACING).toInt()
        for (i in count downTo 0) {
            val along = offset + i * SPACING
            if (along < 1.2) continue
            val s = project(ue * along, un * along, 0.15) ?: continue
            val radius = (f * 0.16f / s.third.toFloat()).coerceIn(4f, 60f)
            val fade = (min(1.0, (along - 1.2) / 2) * min(1.0, (reach + 1 - along) / 6)).toFloat().coerceIn(0f, 1f)
            glow(Offset(s.first, s.second), radius, 0.85f * fade)
            val id = i + floor(t * FLOW / SPACING).toInt() // stays with the puff as it drifts
            for (k in 0 until 4) {
                val seed = id * 7 + k
                val tw = (0.5 + 0.5 * sin(t * (3 + rnd(seed) * 4) + seed)).toFloat()
                val ox = (rnd(seed + 1) - 0.5f) * radius * 5
                val oy = (rnd(seed + 2) - 0.8f) * radius * 3 - tw * radius * 0.6f
                sparkle(Offset(s.first + ox, s.second + oy), radius * 0.22f * (0.5f + tw), 0.9f * tw * fade)
            }
        }

        // --- waypoint at the end of the trail ---
        val b = project(g.east, g.north, 1.0)
        val pad = 30.dp.toPx()
        val canvas = drawContext.canvas.nativeCanvas
        if (b != null && b.first > pad && b.first < w - pad) {
            val x = b.first
            val y = b.second.coerceIn(90.dp.toPx(), h - 200.dp.toPx())
            if (g.atGoal) {
                val tall = 200.dp.toPx()
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, DustMid.copy(alpha = 0.55f)), y - tall, y),
                    topLeft = Offset(x - 4.dp.toPx(), y - tall), size = Size(8.dp.toPx(), tall),
                )
            }
            val disc = 26.dp.toPx()
            glow(Offset(x, y), disc * 2.2f * (1f + 0.12f * sin(t * 4).toFloat()), 0.9f)
            drawCircle(Color.White, disc, Offset(x, y))
            val iconSize = disc * 1.1f
            translate(x - iconSize / 2, y - iconSize / 2) {
                with(icon) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(Color(0xFF2B2670))) }
            }
            text.color = Color.White.toArgb()
            text.textSize = 15.sp.toPx()
            text.setShadowLayer(8f, 0f, 0f, Color.Black.toArgb())
            canvas.drawText(g.label, x, y - disc - 16.dp.toPx(), text) // above, clear of the dust
        } else {
            // Off-screen: nudge the user to turn.
            val rel = Geo.angleDiff(Math.toDegrees(atan2(g.east, g.north)).toFloat(), heading)
            val right = rel > 0
            val x = if (right) w - 44.dp.toPx() else 44.dp.toPx()
            val y = h * 0.4f
            val bob = sin(t * 5).toFloat() * 5.dp.toPx() * (if (right) 1 else -1)
            drawCircle(Color(0xCC141028), 30.dp.toPx(), Offset(x, y))
            text.clearShadowLayer()
            text.color = DustMid.toArgb()
            text.textSize = 36.sp.toPx()
            canvas.drawText(if (right) "›" else "‹", x + bob, y + text.textSize / 3, text)
            text.color = Color.White.toArgb()
            text.textSize = 13.sp.toPx()
            text.setShadowLayer(6f, 0f, 0f, Color.Black.toArgb())
            canvas.drawText("turn ${if (right) "right" else "left"}", x, y + 50.dp.toPx(), text)
        }
    }
}

private fun DrawScope.glow(at: Offset, radius: Float, alpha: Float) {
    drawCircle(
        Brush.radialGradient(
            0f to DustCore.copy(alpha = alpha),
            0.3f to DustMid.copy(alpha = alpha * 0.7f),
            1f to DustEdge.copy(alpha = 0f),
            center = at, radius = radius,
        ),
        radius, at, blendMode = BlendMode.Plus,
    )
}

private fun DrawScope.sparkle(at: Offset, r: Float, alpha: Float) {
    val c = Color.White.copy(alpha = alpha.coerceIn(0f, 1f))
    val stroke = max(1.5f, r / 4)
    drawLine(c, Offset(at.x - r, at.y), Offset(at.x + r, at.y), stroke, blendMode = BlendMode.Plus)
    drawLine(c, Offset(at.x, at.y - r), Offset(at.x, at.y + r), stroke, blendMode = BlendMode.Plus)
}

/** Deterministic pseudo-random, so sparkles twinkle instead of flickering. */
private fun rnd(n: Int): Float {
    val x = sin(n * 127.1) * 43758.5453
    return (x - floor(x)).toFloat()
}

/** Field of view along the long side of the rear camera's sensor, in degrees. */
private fun cameraVerticalFov(ctx: Context): Float = try {
    val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val id = cm.cameraIdList.first {
        cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }
    val ch = cm.getCameraCharacteristics(id)
    val sensor = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)!!
    val focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)!![0]
    Math.toDegrees(2.0 * atan(max(sensor.width, sensor.height) / (2.0 * focal))).toFloat().coerceIn(45f, 90f)
} catch (e: Exception) {
    66f
}
