package app.saintanthony.finder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot

fun Context.has(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/**
 * Which way the phone points, from Android's fused rotation sensor
 * (gyro + accelerometer + magnetometer), corrected to true north.
 */
class OrientationTracker(ctx: Context) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magnet = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val r = FloatArray(9)

    val available = rotation != null

    /** Degrees from true north; NaN until the first reading. */
    var heading by mutableFloatStateOf(Float.NaN)
        internal set

    /** Degrees the rear camera is tilted above the horizon. */
    var pitch by mutableFloatStateOf(0f)
        internal set
    var faceDown by mutableStateOf(false)
        internal set
    var needsCalibration by mutableStateOf(false)
        internal set

    /** Magnetic-to-true-north correction for where we are; set from the GPS fix. */
    var declination = 0f

    fun start() {
        rotation?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        // Only listened to for its accuracy reports (the calibration warning).
        magnet?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() = sm.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(r, e.values)
        // World frame is (east, north, up). Direction of the rear camera...
        val bx = -r[2]; val by = -r[5]; val bz = -r[8]
        // ...and of the phone's top edge.
        val tx = r[1]; val ty = r[4]

        pitch = Math.toDegrees(asin(bz.coerceIn(-1f, 1f).toDouble())).toFloat()
        faceDown = bz > 0.8f // camera at the sky = screen on the table

        // Held upright: use where the camera looks. Held flat: use the top edge.
        val upright = hypot(bx, by) > hypot(tx, ty)
        val raw = Math.toDegrees(
            if (upright) atan2(bx.toDouble(), by.toDouble()) else atan2(tx.toDouble(), ty.toDouble())
        ).toFloat() + declination
        val h = (raw % 360f + 360f) % 360f
        heading = if (heading.isNaN()) h else ((heading + Geo.angleDiff(h, heading) * 0.2f) % 360f + 360f) % 360f
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            needsCalibration = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
        }
    }
}

data class Fix(val lat: Double, val lng: Double, val acc: Float, val alt: Double) {
    val latLng get() = LatLng(lat, lng)
}

/** Position from Google's fused location (GPS + Wi-Fi + motion sensors). */
class LocationTracker(private val ctx: Context, private val onFix: (Fix) -> Unit) {
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(ctx)
    private var running = false

    var pos by mutableStateOf<Fix?>(null)
        internal set

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val l = result.lastLocation ?: return
            val fix = Fix(l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy else 20f, l.altitude)
            pos = fix
            onFix(fix)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running || !ctx.has(Manifest.permission.ACCESS_FINE_LOCATION)) return
        running = true
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    fun stop() {
        if (!running) return
        running = false
        client.removeLocationUpdates(callback)
    }
}
