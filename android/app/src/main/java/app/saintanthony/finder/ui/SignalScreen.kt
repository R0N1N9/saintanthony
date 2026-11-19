package app.saintanthony.finder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.saintanthony.finder.AppModel
import app.saintanthony.finder.Geo
import app.saintanthony.finder.Kind
import app.saintanthony.finder.Source
import app.saintanthony.finder.proximity
import kotlin.math.roundToInt

@Composable
fun SignalScreen(m: AppModel) {
    if (m.signals.trackedId != null) Tracking(m) else Picker(m)
}

// ---------- choosing what to follow ----------

@Composable
private fun Picker(m: AppModel) {
    val s = m.signals
    var showUnnamed by rememberSaveable { mutableStateOf(false) }
    val all = s.sources
    val paired = all.filter { it.paired }
    val ble = all.filter { it.kind == Kind.BLE && !it.paired && (showUnnamed || it.name != null) }
    val wifi = all.filter { it.kind == Kind.WIFI && (showUnnamed || it.name != null) }
    val hidden = all.count { !it.paired && it.name == null }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Hint(
                "Pick a device to home in on by its signal. It has to be switched on and in range.",
                Modifier.fillMaxWidth().padding(8.dp),
            )
        }
        if (!s.bluetoothOn) item { Text("Bluetooth is off — turn it on to see Bluetooth devices.", color = Gold, fontSize = 14.sp) }

        if (paired.isNotEmpty()) item { Section("Your paired devices") }
        items(paired, key = { it.id }) { SourceRow(it) { s.track(it.id, it.name) } }

        item { Section("Nearby Bluetooth") }
        if (ble.isEmpty()) item { Hint("Listening…") }
        items(ble, key = { it.id }) { SourceRow(it) { s.track(it.id, it.name) } }

        item { Section("Wi-Fi networks") }
        if (wifi.isEmpty()) item { Hint("Scanning… this can take up to half a minute.") }
        items(wifi, key = { it.id }) { SourceRow(it) { s.track(it.id, it.name) } }

        if (hidden > 0 || showUnnamed) item {
            TextButton(onClick = { showUnnamed = !showUnnamed }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showUnnamed) "Hide unnamed devices" else "Show $hidden unnamed devices")
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun SourceRow(src: Source, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (src.kind == Kind.WIFI) "📶" else "🔵", fontSize = 20.sp)
        Column(Modifier.weight(1f)) {
            Text(src.name ?: "Unnamed (${src.id.takeLast(5)})", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    src.rssi == null -> "Not broadcasting right now"
                    src.connected -> "Connected · ${proximity(src.rssi.toFloat())}"
                    else -> proximity(src.rssi.toFloat())
                },
                fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (src.rssi != null) Text("${src.rssi} dBm", fontSize = 12.sp, color = Muted)
    }
}

// ---------- following one signal ----------

@Composable
private fun Tracking(m: AppModel) {
    val s = m.signals
    val now = rememberNow()
    val rssi = s.rssi
    val lost = rssi == null || now - s.lastHeard > 8000
    val heading = m.orientation.heading.let { if (it.isNaN()) 0f else it }
    val sweep = s.sweep
    val dir = s.direction
    val wifi = s.trackedKind == Kind.WIFI
    val target = m.target
    val linkedItem = m.items.find { it.signalId == s.trackedId }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(s.trackedName, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

        // Hot/cold meter.
        val pct = if (lost || rssi == null) 0f else ((rssi + 100f) / 60f).coerceIn(0.04f, 1f)
        Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(50)).background(Panel)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(pct).clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF6A8DFF), Gold, Color(0xFFFF8A4C))))
            )
        }
        Text(
            when {
                rssi == null -> "Listening for it…"
                lost -> "Lost the signal — out of range, switched off, or asleep"
                else -> proximity(rssi)
            },
            fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
        if (!lost && rssi != null) {
            val t = s.trend
            Hint("${if (t > 2.5f) "🔥 Getting warmer" else if (t < -2.5f) "❄️ Getting colder" else "Steady"} · ${rssi.roundToInt()} dBm")
        }

        if (sweep != null || dir != null) {
            Canvas(Modifier.fillMaxWidth(0.55f).aspectRatio(1f)) {
                val r = size.minDimension / 2
                val stroke = r * 0.16f
                val inner = r - stroke / 2
                if (sweep != null) {
                    // Ring of slices that light up as you turn; fixed to the world, not the phone.
                    rotate(-heading) {
                        val step = 360f / sweep.size
                        sweep.forEachIndexed { i, done ->
                            drawArc(
                                if (done) Gold else Panel2, startAngle = i * step - 90f + 1.5f, sweepAngle = step - 3f,
                                useCenter = false, topLeft = Offset(center.x - inner, center.y - inner),
                                size = Size(inner * 2, inner * 2), style = Stroke(stroke),
                            )
                        }
                    }
                } else if (dir != null) {
                    drawCircle(Panel2, inner, style = Stroke(stroke))
                    arrow(Geo.angleDiff(dir.bearing, heading), r * 0.62f, if (dir.clear) 1f else 0.25f)
                }
            }
        }

        Hint(
            when {
                sweep != null && wifi ->
                    "Hold the phone flat against your stomach, top edge pointing away, and turn VERY slowly — " +
                        "Wi-Fi strength only updates every few seconds, so allow about a minute for the circle."
                sweep != null ->
                    "Hold the phone flat against your stomach, top edge pointing away from you, and turn slowly " +
                        "in a full circle until the ring fills."
                dir != null && dir.clear ->
                    "Strongest toward ${Geo.cardinal(dir.bearing)}. Walk that way, watch the meter, and spin again when you're closer."
                dir != null ->
                    "No clear direction — the signal was about the same all the way round. Walk a few steps and try again, turning more slowly."
                wifi && s.sources.find { it.id == s.trackedId }?.connected != true ->
                    "Tip: connect to this network for faster readings. Otherwise it only updates about every 30 seconds."
                else -> "Walk around and follow the meter, or tap Find direction for a rough bearing."
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (sweep != null) s.cancelSweep() else s.startSweep() }) {
                Text(if (sweep != null) "Cancel" else if (dir != null) "🧭 Try again" else "🧭 Find direction", fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(onClick = { s.untrack() }) { Text("Stop") }
        }

        // Tie this signal to an item so its location updates itself.
        if (linkedItem != null) {
            FilterChip(
                selected = true,
                onClick = { m.link(linkedItem.id, null, null) },
                label = { Text("Linked to ${linkedItem.emoji} ${linkedItem.name} — tap to unlink") },
            )
            Hint("While the app is open and this signal is close, ${linkedItem.name}'s last-seen spot updates by itself.")
        } else if (target != null) {
            FilterChip(
                selected = false,
                onClick = { m.link(target.id, s.trackedId, s.trackedName) },
                label = { Text("🔗 Link to ${target.emoji} ${target.name}") },
            )
        } else {
            Hint("Select an item on the Items tab to link this signal to it.")
        }
    }
}
