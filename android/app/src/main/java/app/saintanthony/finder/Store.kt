package app.saintanthony.finder

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One place an item was seen. [auto] = logged from a signal, not by the user. */
data class Sighting(
    val lat: Double,
    val lng: Double,
    val acc: Float,
    val t: Long,
    val note: String = "",
    val auto: Boolean = false,
) {
    val latLng get() = LatLng(lat, lng)
}

data class Item(
    val id: String,
    val name: String,
    val emoji: String,
    val sightings: List<Sighting> = emptyList(), // newest last
    val signalId: String? = null, // linked Bluetooth / Wi-Fi source
    val signalName: String? = null,
) {
    val last get() = sightings.lastOrNull()
}

/** Items are kept on the phone as a small JSON file. */
class Store(private val file: File) {
    fun load(): List<Item> = try {
        val arr = JSONArray(file.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val ss = o.getJSONArray("sightings")
            Item(
                id = o.getString("id"),
                name = o.getString("name"),
                emoji = o.getString("emoji"),
                sightings = List(ss.length()) { j ->
                    val s = ss.getJSONObject(j)
                    Sighting(
                        s.getDouble("lat"), s.getDouble("lng"), s.optDouble("acc", 10.0).toFloat(),
                        s.getLong("t"), s.optString("note"), s.optBoolean("auto"),
                    )
                },
                signalId = o.optString("signalId").ifEmpty { null },
                signalName = o.optString("signalName").ifEmpty { null },
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(items: List<Item>) {
        val arr = JSONArray()
        for (item in items) {
            val ss = JSONArray()
            for (s in item.sightings) {
                ss.put(
                    JSONObject().put("lat", s.lat).put("lng", s.lng).put("acc", s.acc.toDouble())
                        .put("t", s.t).put("note", s.note).put("auto", s.auto)
                )
            }
            arr.put(
                JSONObject().put("id", item.id).put("name", item.name).put("emoji", item.emoji)
                    .put("sightings", ss).put("signalId", item.signalId ?: "")
                    .put("signalName", item.signalName ?: "")
            )
        }
        try {
            file.writeText(arr.toString())
        } catch (e: Exception) {
            // Disk full or similar: keep working from memory.
        }
    }
}
