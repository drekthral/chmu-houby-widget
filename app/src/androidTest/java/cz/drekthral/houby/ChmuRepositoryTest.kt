package cz.drekthral.houby

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Běží na emulátoru proti živému API ČHMÚ – ověřuje stažení a čtení mapy na skutečném Androidu. */
@RunWith(AndroidJUnit4::class)
class ChmuRepositoryTest {

    @Test
    fun fetchAndSampleRealMap() {
        val data = ChmuRepository.fetch()
        Log.i("HoubyTest", "timeLabel=${data.timeLabel} png=${data.png.size} B")
        assertTrue("prázdné PNG", data.png.size > 1000)

        for ((name, lat, lon) in listOf(
            Triple("Praha", 50.08, 14.42),
            Triple("Brno", 49.19, 16.61),
            Triple("Šumava", 48.98, 13.5),
        )) {
            val sample = ChmuRepository.sample(data.png, lat, lon)
            Log.i("HoubyTest", "$name -> ${sample.level}")
            assertNotNull("$name nemá stupeň", sample.level)
        }
    }

    @Test
    fun geocoderFindsTown() {
        val places = Geocoder.search("Telč")
        Log.i("HoubyTest", "Telč -> ${places.firstOrNull()}")
        assertTrue(places.isNotEmpty())
    }
}
