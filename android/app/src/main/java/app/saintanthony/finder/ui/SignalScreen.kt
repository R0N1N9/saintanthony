package app.saintanthony.finder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.saintanthony.finder.AppModel
import app.saintanthony.finder.Geo
import app.saintanthony.finder.Kind
import app.saintanthony.finder.Source
import app.saintanthony.finder.Tab
import app.saintanthony.finder.proximity
import kotlin.math.roundToInt

@Composable
fun SignalScreen(m: AppModel, go: (Tab) -> Unit) {
    if (m.signals.trackedId != null) Tracking(m, go) else Picker(m)
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Everything broadcasting around you. Pick one to home in on it.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!s.bluetoothOn) item {
            Text("Bluetooth is off. Turn it on to see Bluetooth devices.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        if (paired.isNotEmpty()) item { Section("Your devices") }
        items(paired, key = { it.id }) { SourceRow(it) { s.track(it.id, it.name) } }

        item { Section("Nearby Bluetooth") }
        if (ble.isEmpty()) item { Quiet("Listening…") }
        items(ble, key = { it.id }) { SourceRow(it) { s.track(it.id, it.name) } }

        item { Section("Wi-Fi") }
        if (wifi.isEmpty()) item { Quiet("Scanning… this can take up to half a minute.") }
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
    Text(
        title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, start = 4.dp),
    )
}

@Composable
private fun Quiet(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun SourceRow(src: Source, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = c.surface, shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(if (src.kind == Kind.WIFI) Icons.Rounded.Wifi else Icons.Rounded.Bluetooth, size = 38.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    src.name ?: "Unnamed · ${src.id.takeLast(5)}",
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        src.rssi == null -> "Not broadcasting right now"
                        src.connected -> "Connected · ${proximity(src.rssi.toFloat())}"
                        else -> proximity(src.rssi.toFloat())
                    },
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            SignalBars(src.rssi)
        }
    }
}

// ---------- following one signal ----------

@Composable
private fun Tracking(m: AppModel, go: (Tab) -> Unit) {
    val s = m.signals
    val c = MaterialTheme.colorScheme
    val dust = LocalDust.current
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = c.surface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(if (wifi) Icons.Rounded.Wifi else Icons.Rounded.Bluetooth, size = 40.dp)
                    Text(s.trackedName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Meter(if (lost || rssi == null) 0f else meterFraction(rssi))
                Text(
                    when {
                        rssi == null -> "Listening for it…"
                        lost -> "Lost the signal. It may be out of range, switched off, or asleep."
                        else -> proximity(rssi)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!lost && rssi != null) {
                    val t = s.trend
                    Text(
                        "${if (t > 2.5f) "Getting warmer" else if (t < -2.5f) "Getting colder" else "Steady"} · ${rssi.roundToInt()} dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (t > 2.5f) c.primary else c.onSurfaceVariant,
                    )
                }
            }
        }

        if (sweep != null || dir != null) {
            Canvas(Modifier.fillMaxWidth(0.5f).aspectRatio(1f)) {
                sweepRing(sweep, heading, size.minDimension / 2, dust, c.outlineVariant)
                if (sweep == null && dir != null) {
                    arrow(Geo.angleDiff(dir.bearing, heading), size.minDimension * 0.28f, dust, if (dir.clear) 1f else 0.25f)
                }
            }
        }

        Hint(
            when {
                sweep != null && wifi ->
                    "Hold the phone flat against your stomach, top edge pointing away, and turn very slowly. " +
                        "Wi-Fi strength only updates every few seconds, so allow about a minute for the circle."
                sweep != null ->
                    "Hold the phone flat against your stomach, top edge pointing away from you, and turn slowly " +
                        "in a full circle until the ring fills."
                dir != null && dir.clear ->
                    "Strongest toward ${Geo.cardinal(dir.bearing)}. Walk that way, watch the meter, and scan again when you're closer."
                dir != null ->
                    "No clear direction — the signal was about the same all the way round. Walk a few steps and try again, turning more slowly."
                wifi && s.sources.find { it.id == s.trackedId }?.connected != true ->
                    "Connect to this network for faster readings. Otherwise it only updates about every 30 seconds."
                else -> "Walk around and follow the meter, or find its direction."
            },
        )

        Button(
            onClick = { if (sweep != null) s.cancelSweep() else s.startSweep() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (sweep == null) Icon(Icons.Rounded.Explore, null, Modifier.size(20.dp))
            Text(if (sweep != null) "Cancel" else if (dir != null) "Scan again" else "Find direction", Modifier.padding(start = 8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = { m.arSignal = true; go(Tab.AR) }, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Rounded.ViewInAr, null, Modifier.size(20.dp))
                Text("See in AR", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { s.untrack(); m.arSignal = false }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Stop") }
        }

        // Tie this signal to an item so its location updates itself.
        if (linkedItem != null) {
            TextButton(onClick = { m.link(linkedItem.id, null, null) }) {
                Icon(Icons.Rounded.LinkOff, null, Modifier.size(18.dp))
                Text("Unlink from ${linkedItem.name}", Modifier.padding(start = 6.dp))
            }
            Text(
                "While the app is open and this signal is close, ${linkedItem.name}'s last-seen spot updates by itself.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        } else if (target != null) {
            TextButton(onClick = { m.link(target.id, s.trackedId, s.trackedName) }) {
                Icon(Icons.Rounded.Link, null, Modifier.size(18.dp))
                Text("Link this signal to ${target.name}", Modifier.padding(start = 6.dp))
            }
        }
    }
}

/**
 * The ring that fills as you turn. Slices are fixed to the world (north up
 * when [heading] is 0), so the one you're facing is always at the top.
 */
fun DrawScope.sweepRing(sweep: List<Boolean>?, heading: Float, radius: Float, on: Color, off: Color, at: Offset = center) {
    val stroke = radius * 0.13f
    val inner = radius - stroke / 2
    if (sweep == null) {
        drawCircle(off, inner, at, style = Stroke(stroke * 0.5f))
        return
    }
    rotate(-heading, at) {
        val step = 360f / sweep.size
        sweep.forEachIndexed { i, done ->
            drawArc(
                if (done) on else off, startAngle = i * step - 90f + 4f, sweepAngle = step - 8f,
                useCenter = false, topLeft = Offset(at.x - inner, at.y - inner),
                size = Size(inner * 2, inner * 2), style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}
