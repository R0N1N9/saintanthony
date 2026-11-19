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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.saintanthony.finder.AppModel
import app.saintanthony.finder.Geo
import app.saintanthony.finder.has
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

private const val CAM_HEIGHT = 1.4 // metres; phone held at chest height
private const val SPACING = 2.0 // metres between dust puffs
private const val FLOW = 1.3 // m/s the dust drifts toward the item

/** Camera view with a pixie-dust trail on the ground leading to a beacon over the item. */
@Composable
fun ArScreen(m: AppModel) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ctx.has(Manifest.permission.CAMERA)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    val vfov = remember { cameraVerticalFov(ctx) }

    val target = m.target
    val pos = m.location.pos
    val dist = if (target != null && pos != null) Geo.distance(pos.latLng, target.last!!.latLng) else null

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF07051A), Bg, Color(0xFF3B2F7A)))
        )
    ) {
        if (granted) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
                PreviewView(c).also { pv ->
                    val future = ProcessCameraProvider.getInstance(c)
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
                    }, ContextCompat.getMainExecutor(c))
                }
            })
            DisposableEffect(Unit) {
                onDispose {
                    try { ProcessCameraProvider.getInstance(ctx).get().unbindAll() } catch (e: Exception) { /* never bound */ }
                }
            }
        }

        if (target != null && pos != null) Trail(m, vfov)

        val msg = when {
            target == null -> "Pick an item from Items first."
            pos == null || dist == null -> "Waiting for GPS…"
            dist < max(8f, pos.acc) -> "You're within GPS accuracy of it — look around here ✨"
            m.orientation.pitch > 50 || m.orientation.pitch < -60 -> "Hold the phone upright to see the trail."
            !granted -> "Camera permission is off, so the trail is shown without the camera view."
            else -> null
        }
        if (msg != null) {
            Text(
                msg, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth()
                    .background(Color(0xCC141028), RoundedCornerShape(14.dp)).padding(12.dp),
            )
        }
    }
}

@Composable
private fun Trail(m: AppModel, vfov: Float) {
    val seconds by produceState(0.0) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1e9 }
    }
    val text = remember { Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER } }

    Canvas(Modifier.fillMaxSize()) {
        val t = seconds
        val item = m.target ?: return@Canvas
        val loc = item.last ?: return@Canvas
        val pos = m.location.pos ?: return@Canvas
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

        val (te, tn) = Geo.toLocal(pos.latLng, loc.latLng)
        val dist = hypot(te, tn)
        if (dist < 0.5) return@Canvas
        val ue = te / dist
        val un = tn / dist

        // --- pixie dust, far to near so the close puffs draw on top ---
        val reach = min(dist, 45.0)
        val offset = (t * FLOW) % SPACING
        val count = floor((reach - offset) / SPACING).toInt()
        for (i in count downTo 0) {
            val along = offset + i * SPACING
            if (along < 1.2) continue
            val s = project(ue * along, un * along, 0.15) ?: continue
            val radius = (f * 0.16f / s.third.toFloat()).coerceIn(4f, 60f)
            val fade = (min(1.0, (along - 1.2) / 2) * min(1.0, (46 - along) / 8)).toFloat().coerceIn(0f, 1f)
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

        // --- beacon over the item ---
        val b = project(te, tn, 1.0)
        val pad = 30.dp.toPx()
        if (b != null && b.first > pad && b.first < w - pad) {
            val x = b.first
            val y = b.second.coerceIn(90.dp.toPx(), h - 120.dp.toPx())
            val tall = 220.dp.toPx()
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Gold.copy(alpha = 0.55f)), y - tall, y),
                topLeft = Offset(x - 5.dp.toPx(), y - tall), size = Size(10.dp.toPx(), tall),
            )
            glow(Offset(x, y), 46.dp.toPx() * (1f + 0.15f * sin(t * 4).toFloat()), 0.9f)
            val canvas = drawContext.canvas.nativeCanvas
            text.clearShadowLayer()
            text.textSize = 34.sp.toPx()
            text.color = Color.White.toArgb()
            canvas.drawText(item.emoji, x, y + text.textSize / 3, text)
            text.textSize = 15.sp.toPx()
            text.isFakeBoldText = true
            text.setShadowLayer(8f, 0f, 0f, Color.Black.toArgb())
            canvas.drawText("${item.name} · ${Geo.fmtDist(dist)}", x, y + 54.dp.toPx(), text)
        } else {
            // Off-screen: nudge the user to turn.
            val rel = Geo.angleDiff(Math.toDegrees(atan2(te, tn)).toFloat(), heading)
            val right = rel > 0
            val x = if (right) w - 44.dp.toPx() else 44.dp.toPx()
            val bob = sin(t * 5).toFloat() * 6.dp.toPx() * (if (right) 1 else -1)
            drawCircle(Color(0xB3141028), 34.dp.toPx(), Offset(x, h / 2))
            val canvas = drawContext.canvas.nativeCanvas
            text.clearShadowLayer()
            text.isFakeBoldText = true
            text.color = Gold.toArgb()
            text.textSize = 40.sp.toPx()
            canvas.drawText(if (right) "›" else "‹", x + bob, h / 2 + text.textSize / 3, text)
            text.color = Color.White.toArgb()
            text.textSize = 13.sp.toPx()
            text.setShadowLayer(6f, 0f, 0f, Color.Black.toArgb())
            canvas.drawText("turn ${if (right) "right" else "left"}", x, h / 2 + 54.dp.toPx(), text)
        }
    }
}

private fun DrawScope.glow(at: Offset, radius: Float, alpha: Float) {
    drawCircle(
        Brush.radialGradient(
            0f to Color(1f, 0.98f, 0.86f, alpha),
            0.3f to Color(1f, 0.84f, 0.4f, alpha * 0.7f),
            1f to Color(1f, 0.7f, 0.24f, 0f),
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
