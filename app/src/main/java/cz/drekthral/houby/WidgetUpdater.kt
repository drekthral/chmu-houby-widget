package cz.drekthral.houby

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.UnknownHostException

/** Stažení mapy a zápis výsledku do všech nastavených widgetů. Volá ho worker i aktivita v popředí. */
object WidgetUpdater {
    private const val TAG = "HoubyRefresh"

    sealed interface Outcome {
        data object NoWidgets : Outcome
        data object Skipped : Outcome
        data class Updated(val timeLabel: String) : Outcome
        data class Failed(val error: Exception) : Outcome
    }

    suspend fun configuredWidgets(ctx: Context): List<Pair<GlanceId, Preferences>> =
        GlanceAppWidgetManager(ctx).getGlanceIds(HoubyWidget::class.java)
            .map { it to getAppWidgetState(ctx, PreferencesGlanceStateDefinition, it) }
            .filter { (_, prefs) -> prefs[WidgetKeys.LAT] != null }

    /**
     * @param shouldFetch rozhodne podle stavu widgetů, jestli stahovat (null = vždy)
     * @param reportError zapsat chybu stažení do widgetů
     */
    suspend fun refresh(
        ctx: Context,
        reportError: Boolean,
        shouldFetch: ((List<Preferences>) -> Boolean)? = null,
    ): Outcome {
        val configured = configuredWidgets(ctx)
        if (configured.isEmpty()) return Outcome.NoWidgets
        if (shouldFetch != null && !shouldFetch(configured.map { it.second })) return Outcome.Skipped

        val data = try {
            withContext(Dispatchers.IO) { ChmuRepository.fetch() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Stažení mapy selhalo", e)
            if (reportError) {
                val msg = errorMessage(ctx, e)
                for ((id, _) in configured) {
                    updateAppWidgetState(ctx, id) { it[WidgetKeys.ERROR] = msg }
                    HoubyWidget().update(ctx, id)
                }
            }
            return Outcome.Failed(e)
        }

        for ((id, prefs) in configured) {
            val appWidgetId = GlanceAppWidgetManager(ctx).getAppWidgetId(id)
            try {
                val sample = withContext(Dispatchers.Default) {
                    ChmuRepository.sample(data.png, prefs[WidgetKeys.LAT]!!, prefs[WidgetKeys.LON]!!)
                }
                val file = File(ctx.filesDir, "crop_$appWidgetId.png")
                withContext(Dispatchers.IO) {
                    file.outputStream().use { sample.crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                sample.crop.recycle()
                updateAppWidgetState(ctx, id) {
                    it[WidgetKeys.LEVEL] = sample.level?.value ?: 0
                    it[WidgetKeys.DATA_LABEL] = data.timeLabel
                    it[WidgetKeys.CROP_PATH] = file.absolutePath
                    it[WidgetKeys.UPDATED_AT] = System.currentTimeMillis()
                    it.remove(WidgetKeys.ERROR)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Zpracování mapy selhalo", e)
                updateAppWidgetState(ctx, id) { it[WidgetKeys.ERROR] = "Chyba čtení mapy: ${describe(e)}" }
            }
            HoubyWidget().update(ctx, id)
        }
        return Outcome.Updated(data.timeLabel)
    }

    fun describe(e: Throwable) = "${e.javaClass.simpleName}: ${e.message ?: ""}".take(120)

    /** Text chyby pro widget, s nápovědou, když Android blokuje aplikaci data na pozadí. */
    fun errorMessage(ctx: Context, e: Exception): String {
        val restriction = backgroundRestriction(ctx)
        return when {
            restriction != null -> "Blokováno: $restriction. V aplikaci Houby ČHMÚ povol data na pozadí."
            e is UnknownHostException -> "Nelze se spojit s ČHMÚ (DNS). Klepni pro obnovu, případně otevři aplikaci."
            else -> "Chyba stažení: ${describe(e)}"
        }
    }

    /** Popis omezení, kvůli kterému aplikace nemusí mít na pozadí přístup k síti, nebo null. */
    fun backgroundRestriction(ctx: Context): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        if (cm?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED) {
            return "zapnutý Spořič dat"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            ctx.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
        ) {
            return "omezená baterie aplikace"
        }
        return null
    }
}
