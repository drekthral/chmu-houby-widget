package cz.drekthral.houby

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end: skutečný widget na emulátoru, nastavené místo, obnova přes WorkManager
 * stejně jako po výběru místa v nastavení.
 */
@RunWith(AndroidJUnit4::class)
class WidgetRefreshTest {

    @Test
    fun refreshFillsWidgetState() = runBlocking {
        val instr = InstrumentationRegistry.getInstrumentation()
        val ctx = instr.targetContext
        val out = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instr.uiAutomation.executeShellCommand("appwidget grantbind --package ${ctx.packageName} --user 0")
        ).bufferedReader().readText()
        Log.i("HoubyTest", "grantbind: $out")

        val host = AppWidgetHost(ctx, 4242)
        val appWidgetId = host.allocateAppWidgetId()
        val bound = AppWidgetManager.getInstance(ctx)
            .bindAppWidgetIdIfAllowed(appWidgetId, ComponentName(ctx, HoubyWidgetReceiver::class.java))
        assertTrue("widget se nepodařilo vytvořit", bound)

        val glanceId = GlanceAppWidgetManager(ctx).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(ctx, glanceId) {
            it[WidgetKeys.PLACE_NAME] = "Telč"
            it[WidgetKeys.LAT] = 49.1848
            it[WidgetKeys.LON] = 15.4525
        }
        HoubyWidget().update(ctx, glanceId)
        RefreshWorker.ensurePeriodic(ctx)
        RefreshWorker.refreshNow(ctx)

        var state: Preferences? = null
        for (i in 1..60) {
            delay(1000)
            state = getAppWidgetState(ctx, PreferencesGlanceStateDefinition, glanceId)
            if (state!![WidgetKeys.DATA_LABEL] != null || state!![WidgetKeys.ERROR] != null) break
        }
        Log.i("HoubyTest", "widget state = $state")
        try {
            assertTrue("chyba: ${state?.get(WidgetKeys.ERROR)}", state?.get(WidgetKeys.ERROR) == null)
            assertNotNull("widget nedostal data", state?.get(WidgetKeys.DATA_LABEL))
            assertTrue("stupeň chybí", (state?.get(WidgetKeys.LEVEL) ?: 0) in 1..5)
        } finally {
            host.deleteAppWidgetId(appWidgetId)
        }
    }
}
