package app.saintanthony.finder

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.saintanthony.finder.ui.ArScreen
import app.saintanthony.finder.ui.CompassScreen
import app.saintanthony.finder.ui.Gold
import app.saintanthony.finder.ui.ItemsScreen
import app.saintanthony.finder.ui.MapScreen
import app.saintanthony.finder.ui.Muted
import app.saintanthony.finder.ui.Panel
import app.saintanthony.finder.ui.SignalScreen
import app.saintanthony.finder.ui.StAnthonyTheme
import org.osmdroid.config.Configuration

enum class Tab(val icon: String, val label: String) {
    ITEMS("🎒", "Items"),
    COMPASS("🧭", "Compass"),
    AR("✨", "AR Trail"),
    SIGNAL("📡", "Signal"),
    MAP("🗺️", "Map"),
}

class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Map setup: keep downloaded tiles in the app's own storage, and identify
        // the app to the tile server as it requires.
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        setContent { StAnthonyTheme { Root(model) } }
    }

    override fun onStart() {
        super.onStart()
        model.start()
    }

    override fun onStop() {
        super.onStop()
        model.stop()
    }
}

private fun wantedPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 31) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
}.toTypedArray()

@Composable
private fun Root(m: AppModel) {
    val ctx = LocalContext.current
    var ready by remember { mutableStateOf(ctx.has(Manifest.permission.ACCESS_FINE_LOCATION)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ready = true // carry on even if some were refused; each screen explains what's missing
        m.stop()
        m.start()
    }
    if (!ready) {
        Welcome { ask.launch(wantedPermissions()) }
        return
    }

    var tab by rememberSaveable { mutableStateOf(Tab.ITEMS) }
    val view = LocalView.current
    DisposableEffect(tab) {
        view.keepScreenOn = tab != Tab.ITEMS // don't let the screen sleep mid-search
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { Header(m, tab) },
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                for (t in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(t.icon, fontSize = 20.sp) },
                        label = { Text(t.label, fontSize = 11.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.ITEMS -> ItemsScreen(m) { tab = it }
                Tab.COMPASS -> CompassScreen(m) { tab = Tab.ITEMS }
                Tab.AR -> ArScreen(m)
                Tab.SIGNAL -> SignalScreen(m)
                Tab.MAP -> MapScreen(m)
            }
        }
    }
}

@Composable
private fun Header(m: AppModel, tab: Tab) {
    val pos = m.location.pos
    val target = m.target
    Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("✨ St. Anthony", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                if (pos != null) "GPS ±${pos.acc.toInt()} m" else "Finding GPS…",
                fontSize = 12.sp,
                color = if (pos != null) Muted else Gold,
            )
        }
        if (target != null && tab != Tab.ITEMS && tab != Tab.SIGNAL) {
            val loc = target.last!!
            Row(
                Modifier.padding(top = 8.dp).fillMaxWidth().background(Panel, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(target.emoji, fontSize = 26.sp)
                Column(Modifier.weight(1f)) {
                    Text(target.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Last seen ${Geo.timeAgo(loc.t)}" + if (loc.note.isNotEmpty()) " · ${loc.note}" else "",
                        fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    Geo.fmtDist(pos?.let { Geo.distance(it.latLng, loc.latLng) }),
                    color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✨", fontSize = 64.sp)
        Text("St. Anthony", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "Remember where your things are, and follow a trail of pixie dust back to them.",
            color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp),
        )
        Text(
            "Needs your location, plus Bluetooth and Wi-Fi scanning to find devices by their signal. " +
                "Everything stays on this phone.",
            color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        Button(onClick = onStart, modifier = Modifier.padding(top = 24.dp).fillMaxWidth()) {
            Text("Start", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}
