package app.saintanthony.finder

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

/** Small-distance geo maths. Bearings are degrees clockwise from north. */
object Geo {
    private const val R = 6371000.0
    private fun rad(d: Double) = Math.toRadians(d)

    fun distance(a: LatLng, b: LatLng): Double {
        val dLat = rad(b.lat - a.lat)
        val dLng = rad(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * R * asin(sqrt(h))
    }

    fun bearing(a: LatLng, b: LatLng): Float {
        val y = sin(rad(b.lng - a.lng)) * cos(rad(b.lat))
        val x = cos(rad(a.lat)) * sin(rad(b.lat)) -
            sin(rad(a.lat)) * cos(rad(b.lat)) * cos(rad(b.lng - a.lng))
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }

    /** Offset of [p] from [origin] in metres: first = east, second = north. */
    fun toLocal(origin: LatLng, p: LatLng): Pair<Double, Double> = Pair(
        rad(p.lng - origin.lng) * R * cos(rad(origin.lat)),
        rad(p.lat - origin.lat) * R,
    )

    /** Signed smallest difference a - b, in -180..180. */
    fun angleDiff(a: Float, b: Float): Float = ((a - b + 540f) % 360f + 360f) % 360f - 180f

    fun fmtDist(m: Double?): String = when {
        m == null || m.isNaN() -> "—"
        m < 1000 -> "${m.roundToInt()} m"
        m < 10000 -> String.format("%.1f km", m / 1000)
        else -> "${(m / 1000).roundToInt()} km"
    }

    fun cardinal(b: Float): String =
        listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((b / 45f).roundToInt() % 8 + 8) % 8]

    fun timeAgo(t: Long, now: Long = System.currentTimeMillis()): String {
        val s = (now - t) / 1000
        return when {
            s < 60 -> "just now"
            s < 3600 -> "${s / 60} min ago"
            s < 86400 -> "${s / 3600} h ago"
            else -> "${s / 86400} d ago"
        }
    }
}
