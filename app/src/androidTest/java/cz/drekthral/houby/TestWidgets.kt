package cz.drekthral.houby

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry

/** Pomocné funkce pro instrumentované testy: shell příkazy a vytvoření skutečného widgetu. */
object TestWidgets {
    fun sh(cmd: String): String {
        val instr = InstrumentationRegistry.getInstrumentation()
        return ParcelFileDescriptor.AutoCloseInputStream(instr.uiAutomation.executeShellCommand(cmd))
            .bufferedReader().readText().trim().also { Log.i("HoubyTest", "$ $cmd -> $it") }
    }

    /** Vytvoří widget aplikace v testovacím hostu; grantbind se někdy neuplatní hned, proto opakování. */
    fun bind(hostId: Int): Pair<AppWidgetHost, Int> {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val host = AppWidgetHost(ctx, hostId)
        val provider = ComponentName(ctx, HoubyWidgetReceiver::class.java)
        repeat(10) {
            sh("appwidget grantbind --package ${ctx.packageName} --user 0")
            val id = host.allocateAppWidgetId()
            if (AppWidgetManager.getInstance(ctx).bindAppWidgetIdIfAllowed(id, provider)) return host to id
            host.deleteAppWidgetId(id)
            Thread.sleep(1000)
        }
        error("widget se nepodařilo vytvořit")
    }
}
