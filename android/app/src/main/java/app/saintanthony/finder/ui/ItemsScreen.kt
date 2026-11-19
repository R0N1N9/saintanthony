package app.saintanthony.finder.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.saintanthony.finder.AppModel
import app.saintanthony.finder.Draft
import app.saintanthony.finder.Geo
import app.saintanthony.finder.Item
import app.saintanthony.finder.Tab

private val EMOJIS = listOf("🔑", "👛", "📱", "🎧", "👓", "🎒", "🚲", "🚗", "☂️", "💻", "🧸", "📦")

@Composable
fun ItemsScreen(m: AppModel, go: (Tab) -> Unit) {
    var dialogFor by remember { mutableStateOf<Item?>(null) }
    var dialogOpen by remember { mutableStateOf(false) }
    val pos = m.location.pos
    val now = rememberNow()

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (m.items.isEmpty()) {
                item {
                    Hint(
                        "Nothing here yet. Add the things you tend to lose — keys, wallet, bike, car — " +
                            "and tap 📍 whenever you set one down.",
                        Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            }
            items(m.items, key = { it.id }) { item ->
                val loc = item.last
                val selected = item.id == m.targetId
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Panel)
                        .border(BorderStroke(2.dp, if (selected) Gold else Color.Transparent), RoundedCornerShape(16.dp))
                        .clickable { m.selectTarget(item.id) }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(item.emoji, fontSize = 30.sp)
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val meta = if (loc == null) "No location yet" else buildString {
                                if (pos != null) append(Geo.fmtDist(Geo.distance(pos.latLng, loc.latLng))).append(" away · ")
                                append("seen ").append(Geo.timeAgo(loc.t, now))
                                if (loc.note.isNotEmpty()) append(" · ").append(loc.note)
                            }
                            Text(meta, fontSize = 13.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FilledTonalButton(onClick = { dialogFor = item; dialogOpen = true }) { Text("📍") }
                        Button(onClick = { m.selectTarget(item.id); go(Tab.COMPASS) }, enabled = loc != null) {
                            Text("Find", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (item.signalId != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "📡 ${item.signalName ?: "Linked signal"}",
                                fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { m.signals.track(item.signalId, item.signalName); go(Tab.SIGNAL) }) {
                                Text("Find by signal")
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = { dialogFor = null; dialogOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Text("＋ Add an item", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp))
        }
    }

    if (dialogOpen) ItemDialog(m, dialogFor, onPickOnMap = { go(Tab.MAP) }, onClose = { dialogOpen = false })
}

@Composable
private fun ItemDialog(m: AppModel, item: Item?, onPickOnMap: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(item?.name ?: "") }
    var note by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf(item?.emoji ?: EMOJIS[0]) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun draft(): Draft? {
        val n = name.trim()
        if (n.isEmpty()) {
            Toast.makeText(ctx, "Give it a name first", Toast.LENGTH_SHORT).show()
            return null
        }
        return Draft(item?.id, n, emoji, note.trim())
    }

    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = Panel) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (item == null) "Add an item" else "Update ${item.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                for (row in EMOJIS.chunked(6)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (e in row) {
                            Box(
                                Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Bg)
                                    .border(BorderStroke(2.dp, if (e == emoji) Gold else Color.Transparent), RoundedCornerShape(10.dp))
                                    .clickable { emoji = e },
                                contentAlignment = Alignment.Center,
                            ) { Text(e, fontSize = 22.sp) }
                        }
                    }
                }
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("What is it?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it.take(80) }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        val d = draft() ?: return@Button
                        val pos = m.location.pos
                        if (pos == null) {
                            Toast.makeText(ctx, "No GPS fix yet — pick the spot on the map instead", Toast.LENGTH_LONG).show()
                        } else {
                            m.save(d, pos.latLng, pos.acc)
                            Toast.makeText(ctx, "${d.emoji} ${d.name} saved", Toast.LENGTH_SHORT).show()
                            onClose()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("📍 It's right here", fontWeight = FontWeight.Bold) }
                FilledTonalButton(
                    onClick = {
                        val d = draft() ?: return@FilledTonalButton
                        m.picking = d
                        onClose()
                        onPickOnMap()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("🗺️ Pick the spot on the map") }
                if (item != null) {
                    FilledTonalButton(
                        onClick = {
                            val d = draft() ?: return@FilledTonalButton
                            m.rename(item.id, d.name, d.emoji)
                            onClose()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save name only") }
                    TextButton(
                        onClick = {
                            if (confirmDelete) { m.delete(item.id); onClose() } else confirmDelete = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (confirmDelete) "Tap again to delete for good" else "Delete item", color = Danger) }
                }
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}
