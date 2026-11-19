package app.saintanthony.finder

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.saintanthony.finder.ui.ArScreen
import app.saintanthony.finder.ui.CompassScreen
import app.saintanthony.finder.ui.IconBadge
import app.saintanthony.finder.ui.ItemsScreen
import app.saintanthony.finder.ui.MapScreen
import app.saintanthony.finder.ui.SignalScreen
import app.saintanthony.finder.ui.StAnthonyTheme
import org.osmdroid.config.Configuration

enum class Tab(val icon: ImageVector, val label: String, val title: String) {
    ITEMS(Icons.Rounded.GridView, "Items", "Your things"),
    COMPASS(Icons.Rounded.Explore, "Compass", "Compass"),
    AR(Icons.Rounded.AutoAwesome, "AR", "AR trail"),
    SIGNAL(Icons.Rounded.Sensors, "Signals", "Signals"),
    MAP(Icons.Rounded.Map, "Map", "Map"),
}

class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Map setup: keep downloaded tiles in the app's own storage, and identify
        // the app to the tile server as it requires.
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        setContent { StAnthonyTheme(model.theme) { Root(model) } }
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
    var settings by remember { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(tab) {
        view.keepScreenOn = tab != Tab.ITEMS // don't let the screen sleep mid-search
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { Header(m, tab) { settings = true } },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                for (t in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.ITEMS -> ItemsScreen(m) { tab = it }
                Tab.COMPASS -> CompassScreen(m) { tab = Tab.ITEMS }
                Tab.AR -> ArScreen(m) { tab = it }
                Tab.SIGNAL -> SignalScreen(m) { tab = it }
                Tab.MAP -> MapScreen(m)
            }
        }
    }
    if (settings) SettingsSheet(m) { settings = false }
}

@Composable
private fun Header(m: AppModel, tab: Tab, onSettings: () -> Unit) {
    val pos = m.location.pos
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.statusBarsPadding().fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tab.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        // Location status: a quiet dot and the current accuracy.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(if (pos != null) Color(0xFF3DBE7A) else c.outline, CircleShape))
            Text(
                if (pos != null) "±${pos.acc.toInt()} m" else "Locating…",
                style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings", tint = c.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(m: AppModel, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            val options = listOf(
                Triple(ThemeMode.SYSTEM, "System", Icons.Rounded.BrightnessAuto),
                Triple(ThemeMode.LIGHT, "Light", Icons.Rounded.LightMode),
                Triple(ThemeMode.DARK, "Dark", Icons.Rounded.DarkMode),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (mode, label, icon) ->
                    SegmentedButton(
                        selected = m.theme == mode,
                        onClick = { m.chooseTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        icon = { Icon(icon, null, Modifier.size(18.dp)) },
                    ) { Text(label) }
                }
            }
            Text(
                "System follows your phone's light or dark setting.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "St. Anthony ${BuildInfo.VERSION} · Your items and their locations stay on this phone.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

object BuildInfo {
    const val VERSION = "0.2"
}

@Composable
private fun Welcome(onStart: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(96.dp).background(c.primary, CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_mark), null, Modifier.size(52.dp), colorFilter = ColorFilter.tint(c.onPrimary))
        }
        Spacer(Modifier.height(24.dp))
        Text("St. Anthony", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Find what's lost.", style = MaterialTheme.typography.titleMedium, color = c.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Promise(Icons.Rounded.Place, "Remember", "Save where you set things down, with one tap.")
            Promise(Icons.Rounded.Explore, "Follow", "An arrow, a trail in your camera, or a map leads you back.")
            Promise(Icons.Rounded.Sensors, "Home in", "Track down Bluetooth and Wi-Fi devices by their signal.")
        }
        Spacer(Modifier.weight(1.4f))
        Text(
            "You'll be asked for location and nearby-device access. Everything stays on this phone.",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Button(onClick = onStart, modifier = Modifier.padding(top = 16.dp).fillMaxWidth().height(54.dp)) {
            Text("Get started", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Promise(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconBadge(icon)
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
