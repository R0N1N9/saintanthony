package app.saintanthony.finder.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
import org.osmdroid.views.overlay.TilesOverlay

/** Map of you, every item's last known spot, the line to the selected item and its history. */
@Composable
fun MapScreen(m: AppModel) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val dust = LocalDust.current
    val dark = LocalDark.current
    val density = LocalDensity.current
    val painters = ITEM_ICONS.associate { (key, vector) -> key to rememberVectorPainter(vector) }

    val map = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(3.0)
        }
    }
    // Whether the view has been framed for the current target; a plain holder
    // so changing it doesn't trigger a redraw.
    val framed = remember(m.targetId) { BooleanArray(1) }
    val icons = remember(dark) { HashMap<String, BitmapDrawable>() }

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
            // The map tiles are drawn for daylight; invert them for dark mode.
            mv.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
            mv.overlays.clear()
            mv.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    val d = m.picking ?: return false
                    m.save(d, LatLng(p.latitude, p.longitude), 10f)
                    m.picking = null
                    Toast.makeText(ctx, "${d.name} saved", Toast.LENGTH_SHORT).show()
                    return true
                }
                override fun longPressHelper(p: GeoPoint) = false
            }))

            val tLoc = target?.last
            if (pos != null) {
                mv.overlays.add(Polygon(mv).apply {
                    points = Polygon.pointsAsCircle(GeoPoint(pos.lat, pos.lng), pos.acc.toDouble().coerceAtLeast(3.0))
                    fillPaint.color = c.primary.copy(alpha = 0.12f).toArgb()
                    outlinePaint.color = c.primary.copy(alpha = 0.4f).toArgb()
                    outlinePaint.strokeWidth = 2f
                    setOnClickListener { _, _, _ -> false }
                })
            }
            if (target != null && target.sightings.size > 1) {
                mv.overlays.add(Polyline(mv).apply {
                    setPoints(target.sightings.map { GeoPoint(it.lat, it.lng) })
                    outlinePaint.color = c.primary.copy(alpha = 0.55f).toArgb()
                    outlinePaint.strokeWidth = 4f
                })
            }
            if (pos != null && tLoc != null) {
                mv.overlays.add(Polyline(mv).apply {
                    setPoints(listOf(GeoPoint(pos.lat, pos.lng), GeoPoint(tLoc.lat, tLoc.lng)))
                    outlinePaint.color = dust.toArgb()
                    outlinePaint.strokeWidth = 12f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(2f, 28f), 0f)
                })
            }
            for (item in items) {
                val loc = item.last ?: continue
                val active = item.id == m.targetId
                val key = itemIconKey(item.icon)
                mv.overlays.add(Marker(mv).apply {
                    position = GeoPoint(loc.lat, loc.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = icons.getOrPut("$key$active") {
                        pinIcon(
                            ctx, density, painters.getValue(key),
                            fill = if (active) c.primary else c.surface,
                            tint = if (active) c.onPrimary else c.onSurface,
                            ring = if (active) c.surface else c.outline,
                        )
                    }
                    setOnMarkerClickListener { _, _ -> m.selectTarget(item.id); true }
                })
            }
            if (pos != null) {
                mv.overlays.add(Marker(mv).apply {
                    position = GeoPoint(pos.lat, pos.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = icons.getOrPut("me") { pinIcon(ctx, density, null, fill = Color(0xFF2F8FFF), tint = Color.White, ring = Color.White, sizeDp = 22f) }
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
                    mv.post { mv.zoomToBoundingBox(box.increaseByScale(1.8f), false) }
                }
            }
            mv.invalidate()
        })

        if (picking != null) {
            Surface(
                Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.large, color = c.primary, shadowElevation = 3.dp,
            ) {
                Row(Modifier.padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tap where you left ${picking.name}", color = c.onPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { m.picking = null }) { Text("Cancel", color = c.onPrimary) }
                }
            }
        } else {
            TargetCard(m, Modifier.align(Alignment.TopCenter).padding(12.dp))
        }
        SmallFloatingActionButton(
            onClick = {
                val p = m.location.pos
                if (p != null) {
                    map.controller.setZoom(19.0)
                    map.controller.animateTo(GeoPoint(p.lat, p.lng))
                }
            },
            containerColor = c.surface, contentColor = c.primary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 32.dp),
        ) { Icon(Icons.Rounded.MyLocation, "Centre on me") }
    }
}

/** A round map marker: filled circle with a ring, and optionally an icon in the middle. */
private fun pinIcon(
    ctx: Context, density: Density, painter: VectorPainter?,
    fill: Color, tint: Color, ring: Color, sizeDp: Float = 44f,
): BitmapDrawable {
    val px = (sizeDp * density.density).toInt()
    val image = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(px.toFloat(), px.toFloat())) {
        val r = px / 2f
        drawCircle(ring, r, Offset(r, r))
        drawCircle(fill, r - 2.5f * density.density, Offset(r, r))
        if (painter != null) {
            val icon = px * 0.52f
            translate((px - icon) / 2, (px - icon) / 2) {
                with(painter) { draw(Size(icon, icon), colorFilter = ColorFilter.tint(tint)) }
            }
        }
    }
    return BitmapDrawable(ctx.resources, image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false))
}
