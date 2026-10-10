package cz.drekthral.houby

import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Klíče stavu jednotlivých widgetů (Glance ukládá stav zvlášť pro každý widget). */
object WidgetKeys {
    val PLACE_NAME = stringPreferencesKey("place_name")
    val LAT = doublePreferencesKey("lat")
    val LON = doublePreferencesKey("lon")

    /** Stupeň 1–5, 0 = bod nemá v mapě hodnotu. */
    val LEVEL = intPreferencesKey("level")
    val DATA_LABEL = stringPreferencesKey("data_label")
    val CROP_PATH = stringPreferencesKey("crop_path")
    val UPDATED_AT = longPreferencesKey("updated_at")
    val ERROR = stringPreferencesKey("error")
}
