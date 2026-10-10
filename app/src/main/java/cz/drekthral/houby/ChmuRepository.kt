package cz.drekthral.houby

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.util.Base64
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Stahuje mapu „Pravděpodobnost růstu hub“ z ČHMÚ a čte z ní hodnotu pro zadané souřadnice.
 *
 * Endpoint vrací JSON `{"timeLabel": "so 10. 10.", "img": "data:image/png;base64,..."}`.
 * PNG pokrývá obdélník [LON_MIN..LON_MAX] × [LAT_MIN..LAT_MAX] ve Web Mercatoru (EPSG:3857),
 * viz `boundingBoxes.main` v https://data-provider.chmi.cz/api/map/init/bio-pocasi.bio-pocasi-rust-hub
 */
object ChmuRepository {
    const val DATA_URL = "https://data-provider.chmi.cz/api/data/rizika/houby"

    private const val LON_MIN = 12.0424913
    private const val LAT_MIN = 48.403695
    private const val LON_MAX = 19.1177213
    private const val LAT_MAX = 51.0596505

    /** Strana výřezu mapy v pixelech zdrojového obrázku (1 px ≈ 190 m, tj. výřez ≈ 40 km). */
    private const val CROP_SIZE = 210

    /** Do jaké vzdálenosti (px) hledat nejbližší barvu legendy, když bod padne na hranici. */
    private const val SEARCH_RADIUS = 8

    class MapData(val timeLabel: String, val png: ByteArray)

    class Sample(val level: HoubyLevel?, val crop: Bitmap)

    fun fetch(): MapData {
        val conn = URL(DATA_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (conn.responseCode != 200) throw IOException("ČHMÚ vrátil HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val img = json.getString("img")
            val png = Base64.decode(img.substringAfter("base64,"), Base64.DEFAULT)
            return MapData(json.optString("timeLabel"), png)
        } finally {
            conn.disconnect()
        }
    }

    fun isInside(lat: Double, lon: Double) =
        lat in LAT_MIN..LAT_MAX && lon in LON_MIN..LON_MAX

    fun sample(png: ByteArray, lat: Double, lon: Double): Sample {
        val decoder = newDecoder(png)
        try {
            val w = decoder.width
            val h = decoder.height
            val px = ((lon - LON_MIN) / (LON_MAX - LON_MIN) * w).roundToInt()
            val py = ((mercY(LAT_MAX) - mercY(lat)) / (mercY(LAT_MAX) - mercY(LAT_MIN)) * h).roundToInt()

            val half = CROP_SIZE / 2
            val src = Rect(px - half, py - half, px + half, py + half)
            val clipped = Rect(src).apply { if (!intersect(0, 0, w, h)) setEmpty() }

            val crop = Bitmap.createBitmap(CROP_SIZE, CROP_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(crop)
            canvas.drawColor(0xFFE6E6E6.toInt())
            var level: HoubyLevel? = null
            if (!clipped.isEmpty) {
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val region = decoder.decodeRegion(clipped, opts)
                level = findLevel(region, px - clipped.left, py - clipped.top)
                canvas.drawBitmap(region, (clipped.left - src.left).toFloat(), (clipped.top - src.top).toFloat(), null)
                region.recycle()
            }
            drawMarker(canvas, half.toFloat(), half.toFloat())
            return Sample(level, crop)
        } finally {
            decoder.recycle()
        }
    }

    /** Barva přesně v bodě, případně nejbližší barva legendy v okolí (hranice krajů apod.). */
    private fun findLevel(bmp: Bitmap, x: Int, y: Int): HoubyLevel? {
        for (r in 0..SEARCH_RADIUS) {
            for (dy in -r..r) for (dx in -r..r) {
                if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != r) continue
                val sx = x + dx
                val sy = y + dy
                if (sx !in 0 until bmp.width || sy !in 0 until bmp.height) continue
                HoubyLevel.fromPixel(bmp.getPixel(sx, sy))?.let { return it }
            }
        }
        return null
    }

    private fun drawMarker(canvas: Canvas, cx: Float, cy: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.BLACK
        canvas.drawCircle(cx, cy, 10f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, 6.5f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(cx, cy, 2.5f, paint)
    }

    private fun mercY(lat: Double) = ln(tan(Math.PI / 4 + Math.toRadians(lat) / 2))

    @Suppress("DEPRECATION")
    private fun newDecoder(png: ByteArray): BitmapRegionDecoder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(png, 0, png.size)
        } else {
            BitmapRegionDecoder.newInstance(png, 0, png.size, false)!!
        }

    const val USER_AGENT = "chmu-houby-widget/0.1 (+https://github.com/drekthral/chmu-houby-widget)"
}
