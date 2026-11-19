package app.saintanthony.finder

import android.app.Application
import android.hardware.GeomagneticField
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** An item being added or edited, before it has a location. */
data class Draft(val id: String?, val name: String, val icon: String, val note: String)

/** Everything the screens share: items, sensors, signal scanning, guidance. */
class AppModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(File(app.filesDir, "items.json"))
    private val prefs = app.getSharedPreferences("st-anthony", 0)

    var items by mutableStateOf(store.load())
        private set
    var targetId by mutableStateOf(prefs.getString("target", null))
        private set

    /** Set while the user is choosing an item's spot on the map. */
    var picking by mutableStateOf<Draft?>(null)

    /** Light, dark, or follow the phone. */
    var theme by mutableStateOf(
        try { ThemeMode.valueOf(prefs.getString("theme", null) ?: "SYSTEM") } catch (e: Exception) { ThemeMode.SYSTEM }
    )
        private set

    fun chooseTheme(mode: ThemeMode) {
        theme = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    /** True when the AR view should follow the tracked signal instead of the selected item. */
    var arSignal by mutableStateOf(false)

    val orientation = OrientationTracker(app)
    val location = LocationTracker(app) { fix ->
        orientation.declination = GeomagneticField(
            fix.lat.toFloat(), fix.lng.toFloat(), fix.alt.toFloat(), System.currentTimeMillis()
        ).declination
    }
    val signals = SignalEngine(app, { orientation.heading }, ::onHeard)
    val guide = Guide(app, this)

    /** The item being searched for, if it has a known location. */
    val target: Item? get() = items.find { it.id == targetId }?.takeIf { it.last != null }

    fun start() {
        orientation.start()
        location.start()
        signals.start()
    }

    fun stop() {
        orientation.stop()
        location.stop()
        signals.stop()
        guide.set(false)
    }

    fun selectTarget(id: String?) {
        targetId = id
        prefs.edit().putString("target", id).apply()
    }

    private fun commit(next: List<Item>) {
        items = next
        store.save(next)
    }

    fun save(d: Draft, at: LatLng, acc: Float): Item {
        val sighting = Sighting(at.lat, at.lng, acc, System.currentTimeMillis(), d.note)
        val old = items.find { it.id == d.id }
        val item = (old ?: Item(UUID.randomUUID().toString(), d.name, d.icon)).let {
            it.copy(name = d.name, icon = d.icon, sightings = (it.sightings + sighting).takeLast(50))
        }
        commit(if (old == null) items + item else items.map { if (it.id == item.id) item else it })
        if (target == null) selectTarget(item.id)
        return item
    }

    fun rename(id: String, name: String, icon: String) =
        commit(items.map { if (it.id == id) it.copy(name = name, icon = icon) else it })

    fun delete(id: String) {
        commit(items.filter { it.id != id })
        if (targetId == id) selectTarget(null)
    }

    /** Tie a Bluetooth / Wi-Fi source to an item (or untie with null). */
    fun link(itemId: String, sourceId: String?, sourceName: String?) =
        commit(items.map { if (it.id == itemId) it.copy(signalId = sourceId, signalName = sourceName) else it })

    // Automatic last-seen pins: whenever a linked source is heard clearly while
    // the app is open, note where the phone is.
    private val lastAuto = HashMap<String, Long>()

    private fun onHeard(id: String, rssi: Int) {
        if (rssi < -80) return
        val now = System.currentTimeMillis()
        if (now - (lastAuto[id] ?: 0L) < 20_000) return
        val pos = location.pos ?: return
        if (pos.acc > 50f || items.none { it.signalId == id }) return
        lastAuto[id] = now
        commit(items.map { item ->
            if (item.signalId != id) return@map item
            val s = Sighting(pos.lat, pos.lng, pos.acc, now, "Signal heard nearby", auto = true)
            val last = item.last
            // Still in the same spot: refresh the existing pin instead of piling up history.
            val keep = if (last != null && last.auto && Geo.distance(last.latLng, pos.latLng) < 25) {
                item.sightings.dropLast(1)
            } else item.sightings
            item.copy(sightings = (keep + s).takeLast(50))
        })
    }
}

/**
 * Eyes-free guidance: buzzes and beeps when the phone points at the item, so
 * it works face-down or in a pocket-height hand without looking at the screen.
 */
class Guide(private val app: Application, private val m: AppModel) {
    var on by mutableStateOf(false)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null
    private var lastPulse = 0L
    private var arrived = false

    fun set(enable: Boolean) {
        on = enable
        handler.removeCallbacks(tick)
        if (enable) {
            arrived = false
            if (tone == null) tone = try { ToneGenerator(AudioManager.STREAM_MUSIC, 70) } catch (e: Exception) { null }
            handler.post(tick)
        }
    }

    private fun vibrate(pattern: LongArray) {
        try {
            @Suppress("DEPRECATION")
            val v = app.getSystemService(Application.VIBRATOR_SERVICE) as? Vibrator
            v?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (e: Exception) { /* no vibrator */ }
    }

    private val tick = object : Runnable {
        override fun run() {
            handler.postDelayed(this, 150)
            val item = m.target ?: return
            val loc = item.last ?: return
            val pos = m.location.pos ?: return
            val heading = m.orientation.heading
            if (heading.isNaN()) return

            val d = Geo.distance(pos.latLng, loc.latLng)
            if (d < max(8f, pos.acc)) {
                if (!arrived) {
                    arrived = true
                    vibrate(longArrayOf(0, 200, 100, 200, 100, 400))
                    tone?.startTone(ToneGenerator.TONE_PROP_ACK, 300)
                }
                return
            }
            arrived = false
            val err = abs(Geo.angleDiff(Geo.bearing(pos.latLng, loc.latLng), heading))
            val now = System.currentTimeMillis()
            if (err < 15 && now - lastPulse > 700) {
                lastPulse = now
                vibrate(longArrayOf(0, 50, 60, 50)) // "this way!"
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 90)
            } else if (err < 45 && now - lastPulse > 1500) {
                lastPulse = now
                vibrate(longArrayOf(0, 25)) // "getting warmer"
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
            }
        }
    }
}
