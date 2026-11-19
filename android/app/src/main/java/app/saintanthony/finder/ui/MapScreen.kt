package app.saintanthony.finder.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.saintanthony.finder.AppModel
import app.saintanthony.finder.Geo
import app.saintanthony.finder.LatLng
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/** Map of you, every item's last known spot, the line to the selected item and its history. */
@Composable
fun MapScreen(m: AppModel) {
    val ctx = LocalContext.current
    val map = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(3.0)
        }
    }
    // Whether the view has been framed for the current target; plain holder so
    // changing it doesn't trigger a redraw.
    val framed = remember(m.targetId) { BooleanArray(1) }
    val icons = remember { HashMap<String, BitmapDrawable>() }

    DisposableEffect(Unit) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }

    val pos = m.location.pos
    val items = m.items
    val target = m.target
    val picking = m.picking

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize(), update = { mv ->
            mv.overlays.clear()
            mv.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    val d = m.picking ?: return false
                    m.save(d, LatLng(p.latitude, p.longitude), 10f)
                    m.picking = null
                    Toast.makeText(ctx, "${d.emoji} ${d.name} saved", Toast.LENGTH_SHORT).show()
                    return true
                }
                override fun longPressHelper(p: GeoPoint) = false
            }))

            val tLoc = target?.last
            if (pos != null) {
                mv.overlays.add(Polygon(mv).apply {
                    points = Polygon.pointsAsCircle(GeoPoint(pos.lat, pos.lng), pos.acc.toDouble().coerceAtLeast(3.0))
                    fillPaint.color = 0x226AB7FF
                    outlinePaint.color = 0x886AB7FF.toInt()
                    outlinePaint.strokeWidth = 2f
                    setOnClickListener { _, _, _ -> false }
                })
            }
            if (target != null && target.sightings.size > 1) {
                mv.overlays.add(Polyline(mv).apply {
                    setPoints(target.sightings.map { GeoPoint(it.lat, it.lng) })
                    outlinePaint.color = 0xCCB9A7FF.toInt()
                    outlinePaint.strokeWidth = 4f
                })
            }
            if (pos != null && tLoc != null) {
                mv.overlays.add(Polyline(mv).apply {
                    setPoints(listOf(GeoPoint(pos.lat, pos.lng), GeoPoint(tLoc.lat, tLoc.lng)))
                    outlinePaint.color = Gold.toArgb()
                    outlinePaint.strokeWidth = 10f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(2f, 26f), 0f)
                })
            }
            for (item in items) {
                val loc = item.last ?: continue
                val active = item.id == m.targetId
                mv.overlays.add(Marker(mv).apply {
                    position = GeoPoint(loc.lat, loc.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = icons.getOrPut("${item.emoji}$active") { emojiIcon(ctx, item.emoji, active) }
                    setOnMarkerClickListener { _, _ -> m.selectTarget(item.id); true }
                })
            }
            if (pos != null) {
                mv.overlays.add(Marker(mv).apply {
                    position = GeoPoint(pos.lat, pos.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = icons.getOrPut("me") { meIcon(ctx) }
                    setOnMarkerClickListener { _, _ -> true }
                })
            }

            if (!framed[0] && pos != null) {
                framed[0] = true
                val here = GeoPoint(pos.lat, pos.lng)
                if (tLoc == null || Geo.distance(pos.latLng, tLoc.latLng) < 40) {
                    mv.controller.setZoom(19.0)
                    mv.controller.setCenter(here)
                } else {
                    val box = BoundingBox.fromGeoPoints(listOf(here, GeoPoint(tLoc.lat, tLoc.lng)))
                    mv.post { mv.zoomToBoundingBox(box.increaseByScale(1.6f), false) }
                }
            }
            mv.invalidate()
        })

        if (picking != null) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(10.dp).fillMaxWidth()
                    .background(Gold, RoundedCornerShape(14.dp)).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tap the map where you left it", color = GoldInk, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { m.picking = null }) { Text("Cancel", color = GoldInk) }
            }
        }
        FilledTonalButton(
            onClick = {
                val p = m.location.pos
                if (p != null) {
                    map.controller.setZoom(19.0)
                    map.controller.animateTo(GeoPoint(p.lat, p.lng))
                }
            },
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 28.dp),
        ) { Text("◎") }
    }
}

private fun emojiIcon(ctx: Context, emoji: String, active: Boolean): BitmapDrawable {
    val d = ctx.resources.displayMetrics.density
    val size = (48 * d).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val mid = size / 2f
    if (active) {
        p.color = Gold.toArgb()
        c.drawCircle(mid, mid, mid, p)
    }
    p.color = 0xFFFFFFFF.toInt()
    c.drawCircle(mid, mid, mid - 4 * d, p)
    p.textSize = 22 * d
    p.textAlign = Paint.Align.CENTER
    c.drawText(emoji, mid, mid - (p.descent() + p.ascent()) / 2, p)
    return BitmapDrawable(ctx.resources, bmp)
}

private fun meIcon(ctx: Context): BitmapDrawable {
    val d = ctx.resources.displayMetrics.density
    val size = (24 * d).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xFFFFFFFF.toInt()
    c.drawCircle(size / 2f, size / 2f, size / 2f, p)
    p.color = 0xFF2F8FFF.toInt()
    c.drawCircle(size / 2f, size / 2f, size / 2f - 3 * d, p)
    return BitmapDrawable(ctx.resources, bmp)
}
