package app.saintanthony.finder.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.saintanthony.finder.AppModel
import app.saintanthony.finder.Draft
import app.saintanthony.finder.Geo
import app.saintanthony.finder.Item
import app.saintanthony.finder.R
import app.saintanthony.finder.Tab

@Composable
fun ItemsScreen(m: AppModel, go: (Tab) -> Unit) {
    var sheetFor by remember { mutableStateOf<Item?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    val pos = m.location.pos
    val now = rememberNow()
    val c = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize()) {
        if (m.items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(40.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(88.dp).background(c.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.ic_mark), null, Modifier.size(46.dp), colorFilter = ColorFilter.tint(c.primary))
                }
                Spacer(Modifier.height(20.dp))
                Text("Nothing to lose yet", style = MaterialTheme.typography.titleLarge)
                Hint(
                    "Add the things you tend to misplace — keys, wallet, bike, car — and save their spot whenever you set one down.",
                    Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(m.items, key = { it.id }) { item ->
                    val loc = item.last
                    val selected = item.id == m.targetId
                    Surface(
                        onClick = { m.selectTarget(item.id) },
                        shape = MaterialTheme.shapes.large,
                        color = c.surface,
                        border = if (selected) BorderStroke(2.dp, c.primary) else null,
                        shadowElevation = 1.dp,
                    ) {
                        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconBadge(itemIcon(item.icon), active = selected)
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val meta = if (loc == null) "No location yet" else buildString {
                                        if (pos != null) append(Geo.fmtDist(Geo.distance(pos.latLng, loc.latLng))).append(" away · ")
                                        append(Geo.timeAgo(loc.t, now))
                                        if (loc.note.isNotEmpty()) append(" · ").append(loc.note)
                                    }
                                    Text(meta, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { sheetFor = item; sheetOpen = true }) {
                                    Icon(Icons.Rounded.EditLocationAlt, "Update location or edit", tint = c.onSurfaceVariant)
                                }
                                FilledTonalButton(onClick = { m.selectTarget(item.id); go(Tab.COMPASS) }, enabled = loc != null) { Text("Find") }
                            }
                            if (item.signalId != null) {
                                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Sensors, null, Modifier.size(16.dp), tint = c.onSurfaceVariant)
                                    Text(
                                        item.signalName ?: "Linked signal", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                                        modifier = Modifier.weight(1f).padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    TextButton(onClick = { m.signals.track(item.signalId, item.signalName); go(Tab.SIGNAL) }) { Text("Find by signal") }
                                }
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { sheetFor = null; sheetOpen = true },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Add item") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (sheetOpen) ItemSheet(m, sheetFor, onPickOnMap = { go(Tab.MAP) }, onClose = { sheetOpen = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemSheet(m: AppModel, item: Item?, onPickOnMap: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(item?.name ?: "") }
    var note by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(itemIconKey(item?.icon ?: "key")) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun draft(): Draft? {
        val n = name.trim()
        if (n.isEmpty()) {
            Toast.makeText(ctx, "Give it a name first", Toast.LENGTH_SHORT).show()
            return null
        }
        return Draft(item?.id, n, icon, note.trim())
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (item == null) "New item" else item.name, style = MaterialTheme.typography.titleLarge)
            for (row in ITEM_ICONS.chunked(6)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for ((key, vector) in row) {
                        val on = key == icon
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(if (on) c.primary else c.surfaceVariant).clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) { Icon(vector, key, tint = if (on) c.onPrimary else c.onSurfaceVariant, modifier = Modifier.size(24.dp)) }
                    }
                }
            }
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it.take(80) }, label = { Text("Where exactly? (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val d = draft() ?: return@Button
                    val pos = m.location.pos
                    if (pos == null) {
                        Toast.makeText(ctx, "No location fix yet — choose the spot on the map instead", Toast.LENGTH_LONG).show()
                    } else {
                        m.save(d, pos.latLng, pos.acc)
                        Toast.makeText(ctx, "${d.name} saved here", Toast.LENGTH_SHORT).show()
                        onClose()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Rounded.Place, null, Modifier.size(20.dp))
                Text("It's right here", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = {
                    val d = draft() ?: return@OutlinedButton
                    m.picking = d
                    onClose()
                    onPickOnMap()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Rounded.Map, null, Modifier.size(20.dp))
                Text("Choose the spot on the map", Modifier.padding(start = 8.dp))
            }
            if (item != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        if (confirmDelete) { m.delete(item.id); onClose() } else confirmDelete = true
                    }) { Text(if (confirmDelete) "Tap again to delete" else "Delete", color = c.error) }
                    TextButton(onClick = {
                        val d = draft() ?: return@TextButton
                        m.rename(item.id, d.name, d.icon)
                        onClose()
                    }) { Text("Save name and icon only") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
