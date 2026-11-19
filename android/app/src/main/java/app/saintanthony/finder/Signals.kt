package app.saintanthony.finder

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class Kind { BLE, WIFI }

/** Something that broadcasts a radio signal we can measure. */
data class Source(
    val id: String, // "ble:<address>" or "wifi:<bssid>"
    val name: String?,
    val kind: Kind,
    val rssi: Int?, // dBm; null = known but not heard right now
    val lastSeen: Long,
    val paired: Boolean = false,
    val connected: Boolean = false,
)

data class Direction(val bearing: Float, val clear: Boolean)

/**
 * Scans for Bluetooth LE devices and Wi-Fi access points, and for one tracked
 * source turns signal strength into a hot/cold reading and a rough direction.
 *
 * A phone has one antenna, so a single reading carries no direction. The
 * "spin sweep" gets one anyway: hold the phone against your body, turn a full
 * circle, and your body blocks part of the signal when it is behind you.
 */
class SignalEngine(
    private val ctx: Context,
    private val heading: () -> Float,
    private val onHeard: (id: String, rssi: Int) -> Unit,
) {
    companion object {
        private const val BINS = 12 // 30-degree slices of the compass
        private const val NEED = 10 // slices to cover before estimating
    }

    var sources by mutableStateOf<List<Source>>(emptyList())
        private set
    var trackedId by mutableStateOf<String?>(null)
        private set
    var trackedName by mutableStateOf("")
        private set
    var trackedKind by mutableStateOf(Kind.BLE)
        private set
    var rssi by mutableStateOf<Float?>(null)
        private set
    var lastHeard by mutableLongStateOf(0L)
        private set
    var trend by mutableFloatStateOf(0f) // dB over the last few seconds; + = warmer
        private set
    var sweep by mutableStateOf<List<Boolean>?>(null) // which slices are covered, while spinning
        private set
    var direction by mutableStateOf<Direction?>(null)
        private set
    var bluetoothOn by mutableStateOf(true)
        private set

    private val bt = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val handler = Handler(Looper.getMainLooper())
    private val live = HashMap<String, Source>()
    private val history = ArrayDeque<Pair<Long, Float>>()
    private var sums = FloatArray(BINS)
    private var counts = IntArray(BINS)
    private var running = false
    private var bleScanning = false
    private var wifiRegistered = false
    private var ticks = 0
    private var lastWifiRssi = 0
    private var lastWifiAt = 0L

    // ---------- lifecycle ----------

    fun start() {
        if (running) return
        running = true
        startBle()
        startWifi()
        handler.post(tick)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(tick)
        stopBle()
        if (wifiRegistered) {
            try { ctx.unregisterReceiver(wifiReceiver) } catch (e: Exception) { /* not registered */ }
            wifiRegistered = false
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            pollConnectedWifi(now)
            if (ticks % 60 == 0) requestWifiScan() // Android allows about 4 scans per 2 minutes
            if (ticks % 10 == 0 && !bleScanning) startBle() // Bluetooth may have been switched on since
            ticks++

            val it = live.entries.iterator()
            while (it.hasNext()) {
                val s = it.next().value
                val age = now - s.lastSeen
                when {
                    s.kind == Kind.BLE && age > 30_000 && !s.paired -> it.remove()
                    s.kind == Kind.WIFI && age > 90_000 -> it.remove()
                }
            }
            sources = live.values
                .map { if (it.rssi != null && now - it.lastSeen > 30_000) it.copy(rssi = null) else it }
                .sortedWith(compareByDescending<Source> { it.rssi ?: -999 }.thenBy { it.name ?: "~" })
            handler.postDelayed(this, 500)
        }
    }

    // ---------- Bluetooth ----------

    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val id = "ble:" + result.device.address
            val prev = live[id]
            val name = result.scanRecord?.deviceName ?: prev?.name ?: nameOf(result.device)
            live[id] = Source(id, name, Kind.BLE, result.rssi, System.currentTimeMillis(), paired = prev?.paired == true)
            reading(id, result.rssi)
        }
    }

    private fun nameOf(d: BluetoothDevice): String? = try { d.name } catch (e: SecurityException) { null }

    @SuppressLint("MissingPermission")
    private fun startBle() {
        val adapter = bt?.adapter
        bluetoothOn = adapter?.isEnabled == true
        if (adapter == null || !bluetoothOn) return
        val canScan = if (Build.VERSION.SDK_INT >= 31) ctx.has(Manifest.permission.BLUETOOTH_SCAN)
        else ctx.has(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!canScan) return
        try {
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            adapter.bluetoothLeScanner?.startScan(null, settings, bleCallback)
            bleScanning = true
        } catch (e: Exception) {
            bleScanning = false
        }
        // Devices already paired with this phone, so they can be picked by name
        // even while silent.
        if (Build.VERSION.SDK_INT < 31 || ctx.has(Manifest.permission.BLUETOOTH_CONNECT)) {
            try {
                for (d in adapter.bondedDevices) {
                    val id = "ble:" + d.address
                    val prev = live[id]
                    live[id] = prev?.copy(paired = true, name = prev.name ?: d.name)
                        ?: Source(id, d.name, Kind.BLE, null, 0L, paired = true)
                }
            } catch (e: SecurityException) { /* permission revoked mid-run */ }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBle() {
        if (!bleScanning) return
        bleScanning = false
        try { bt?.adapter?.bluetoothLeScanner?.stopScan(bleCallback) } catch (e: Exception) { /* Bluetooth off */ }
    }

    // ---------- Wi-Fi ----------

    private val wifiReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context, intent: Intent) {
            val results = try { wifi?.scanResults } catch (e: SecurityException) { null } ?: return
            val now = System.currentTimeMillis()
            for (r in results) {
                val bssid = r.BSSID ?: continue
                val id = "wifi:$bssid"
                if (live[id]?.connected == true) continue // the live poll below is fresher
                @Suppress("DEPRECATION")
                val ssid = r.SSID?.ifEmpty { null }
                live[id] = Source(id, ssid, Kind.WIFI, r.level, now)
                reading(id, r.level)
            }
        }
    }

    private fun startWifi() {
        if (wifi == null || wifiRegistered) return
        ContextCompat.registerReceiver(
            ctx, wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED, // only the system sends this broadcast
        )
        wifiRegistered = true
    }

    private fun requestWifiScan() {
        if (!ctx.has(Manifest.permission.ACCESS_FINE_LOCATION)) return
        try {
            @Suppress("DEPRECATION")
            wifi?.startScan()
        } catch (e: Exception) { /* throttled or Wi-Fi off */ }
    }

    /** The network the phone is connected to reports its strength far more often than a scan does. */
    private fun pollConnectedWifi(now: Long) {
        @Suppress("DEPRECATION")
        val info = try { wifi?.connectionInfo } catch (e: Exception) { null } ?: return
        val bssid = info.bssid ?: return
        if (bssid == "02:00:00:00:00:00" || info.rssi <= -127) return // no permission / not connected
        val id = "wifi:$bssid"
        @Suppress("DEPRECATION")
        val ssid = info.ssid?.trim('"')?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
        for (s in live.values.filter { it.connected && it.id != id }) live[s.id] = s.copy(connected = false)
        live[id] = Source(id, ssid ?: live[id]?.name, Kind.WIFI, info.rssi, now, connected = true)
        // Android refreshes this value every few seconds; don't count repeats as new readings.
        if (info.rssi != lastWifiRssi || now - lastWifiAt > 3000) {
            lastWifiRssi = info.rssi
            lastWifiAt = now
            reading(id, info.rssi)
        }
    }

    // ---------- tracking one source ----------

    fun track(id: String, name: String?) {
        trackedId = id
        trackedName = name ?: "Unnamed device"
        trackedKind = if (id.startsWith("wifi:")) Kind.WIFI else Kind.BLE
        rssi = null
        lastHeard = 0L
        trend = 0f
        sweep = null
        direction = null
        history.clear()
    }

    fun untrack() {
        trackedId = null
        sweep = null
        direction = null
    }

    fun startSweep() {
        sums = FloatArray(BINS)
        counts = IntArray(BINS)
        direction = null
        sweep = List(BINS) { false }
    }

    fun cancelSweep() {
        sweep = null
    }

    private fun reading(id: String, r: Int) {
        onHeard(id, r)
        if (id != trackedId) return
        val now = System.currentTimeMillis()
        val smooth = rssi?.let { it * 0.7f + r * 0.3f } ?: r.toFloat()
        rssi = smooth
        lastHeard = now
        history.addLast(now to smooth)
        while (history.isNotEmpty() && now - history.first().first > 4000) history.removeFirst()
        trend = smooth - history.first().second

        val h = heading()
        if (sweep != null && !h.isNaN()) {
            val b = (h / (360f / BINS)).toInt().coerceIn(0, BINS - 1)
            sums[b] += r
            counts[b]++
            val min = if (trackedKind == Kind.WIFI) 1 else 2
            val covered = List(BINS) { counts[it] >= min }
            if (covered.count { it } >= NEED) finishSweep() else sweep = covered
        }
    }

    private fun finishSweep() {
        val known = List(BINS) { if (counts[it] > 0) sums[it] / counts[it] else null }
        val floor = known.filterNotNull().min()
        // Fill the odd missed slice from its neighbours.
        val mean = List(BINS) { i ->
            known[i] ?: listOfNotNull(known[(i + BINS - 1) % BINS], known[(i + 1) % BINS])
                .let { if (it.isEmpty()) floor else it.average().toFloat() }
        }
        val lo = mean.min()
        val hi = mean.max()
        var x = 0.0
        var y = 0.0
        var total = 0.0
        mean.forEachIndexed { i, v ->
            val w = (v - lo).toDouble()
            val a = Math.toRadians((i + 0.5) * (360.0 / BINS))
            x += w * sin(a)
            y += w * cos(a)
            total += w
        }
        val sharp = if (total > 0) hypot(x, y) / total else 0.0 // 0 = same all round, 1 = one clear peak
        direction = Direction(
            bearing = ((Math.toDegrees(atan2(x, y)) + 360) % 360).toFloat(),
            clear = hi - lo >= 4f && sharp >= 0.15,
        )
        sweep = null
        buzz(ctx, 120)
    }
}

/** Signal strength is only a loose guide to distance, so describe it in bands, not metres. */
fun proximity(rssi: Float): String = when {
    rssi > -55 -> "Right next to you"
    rssi > -68 -> "Very close — within a few steps"
    rssi > -80 -> "Nearby — probably this room"
    rssi > -92 -> "Further off, or behind a wall"
    else -> "Faint — at the edge of range"
}

fun buzz(ctx: Context, ms: Long) {
    try {
        @Suppress("DEPRECATION")
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (e: Exception) { /* no vibrator */ }
}
