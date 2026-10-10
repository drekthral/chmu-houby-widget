package cz.drekthral.houby

import android.app.ActivityManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.net.ConnectivityManager
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Diagnostika: chování obnovy se zapnutým Spořičem dat, s výjimkou i bez ní. Jen loguje. */
@RunWith(AndroidJUnit4::class)
class DataSaverDiagnosticTest {
    private val instr = InstrumentationRegistry.getInstrumentation()
    private val ctx = instr.targetContext

    private fun sh(cmd: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instr.uiAutomation.executeShellCommand(cmd)
    ).bufferedReader().readText().trim().also { Log.i("HoubyTest", "$ $cmd -> $it") }

    @Test
    fun dataSaverScenarios() = runBlocking {
        val uid = ctx.applicationInfo.uid
        sh("svc wifi disable")
        sh("svc data enable")
        delay(3000)
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        Log.i("HoubyTest", "metered=${cm.isActiveNetworkMetered} importance=" +
            ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }.importance)

        sh("appwidget grantbind --package ${ctx.packageName} --user 0")
        val host = AppWidgetHost(ctx, 4343)
        val appWidgetId = host.allocateAppWidgetId()
        AppWidgetManager.getInstance(ctx)
            .bindAppWidgetIdIfAllowed(appWidgetId, ComponentName(ctx, HoubyWidgetReceiver::class.java))
        val glanceId = GlanceAppWidgetManager(ctx).getGlanceIdBy(appWidgetId)

        suspend fun scenario(name: String) {
            updateAppWidgetState(ctx, glanceId) {
                it[WidgetKeys.LAT] = 49.1848
                it[WidgetKeys.LON] = 15.4525
                it.remove(WidgetKeys.DATA_LABEL)
                it.remove(WidgetKeys.ERROR)
            }
            Log.i("HoubyTest", "[$name] restrictStatus=${cm.restrictBackgroundStatus} restriction=${WidgetUpdater.backgroundRestriction(ctx)}")
            val direct = try { "OK ${ChmuRepository.fetch().timeLabel}" } catch (e: Exception) { WidgetUpdater.describe(e) }
            Log.i("HoubyTest", "[$name] direct fetch from test: $direct")
            RefreshWorker.refreshNow(ctx)
            for (i in 1..45) {
                delay(1000)
                val s = getAppWidgetState(ctx, PreferencesGlanceStateDefinition, glanceId)
                if (s[WidgetKeys.DATA_LABEL] != null || s[WidgetKeys.ERROR] != null) break
            }
            val s = getAppWidgetState(ctx, PreferencesGlanceStateDefinition, glanceId)
            Log.i("HoubyTest", "[$name] worker: label=${s[WidgetKeys.DATA_LABEL]} error=${s[WidgetKeys.ERROR]}")
        }

        try {
            sh("cmd netpolicy set restrict-background false")
            scenario("saver OFF")
            sh("cmd netpolicy set restrict-background true")
            sh("cmd netpolicy remove restrict-background-whitelist $uid")
            scenario("saver ON, bez vyjimky")
            sh("cmd netpolicy add restrict-background-whitelist $uid")
            scenario("saver ON, s vyjimkou")
            sh("cmd netpolicy list restrict-background-whitelist")
        } finally {
            sh("cmd netpolicy set restrict-background false")
            host.deleteAppWidgetId(appWidgetId)
        }
    }
}
