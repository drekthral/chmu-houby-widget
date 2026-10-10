package cz.drekthral.houby

import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Vyhledání obcí přes Nominatim (OpenStreetMap), omezené na ČR. */
object Geocoder {
    data class Place(val name: String, val detail: String, val lat: Double, val lon: Double)

    fun search(query: String): List<Place> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val url = URL(
            "https://nominatim.openstreetmap.org/search" +
                "?q=$q&format=jsonv2&countrycodes=cz&limit=10&accept-language=cs"
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", ChmuRepository.USER_AGENT)
            if (conn.responseCode != 200) throw IOException("Vyhledávání vrátilo HTTP ${conn.responseCode}")
            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val display = o.optString("display_name")
                Place(
                    name = o.optString("name").ifBlank { display.substringBefore(",") },
                    detail = display,
                    lat = o.getString("lat").toDouble(),
                    lon = o.getString("lon").toDouble(),
                )
            }
        } finally {
            conn.disconnect()
        }
    }
}
