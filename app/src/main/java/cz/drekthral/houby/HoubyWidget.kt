package cz.drekthral.houby

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.io.File

class HoubyWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent { Content(context, appWidgetId) }
    }

    @Composable
    private fun Content(context: Context, appWidgetId: Int) {
        val prefs = currentState<Preferences>()
        val size = LocalSize.current

        if (prefs[WidgetKeys.LAT] == null) {
            val configure = Intent(context, ConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            Column(
                modifier = GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(16.dp)
                    .background(Color(0xFFE6E6E6)).padding(12.dp)
                    .clickable(actionStartActivity(configure)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Klepni a vyber místo", style = TextStyle(fontSize = 14.sp))
            }
            return
        }

        val level = prefs[WidgetKeys.LEVEL]?.let { HoubyLevel.of(it) }
        val bg = level?.color?.let { Color(it) } ?: Color(0xFFBDBDBD)
        val fg = if (level != null && !level.darkText) Color.White else Color(0xFF1B1B1B)
        val fgProvider = ColorProvider(fg)
        val dataLabel = prefs[WidgetKeys.DATA_LABEL]
        val error = prefs[WidgetKeys.ERROR]

        // 1×1: jen barva a číslo stupně.
        if (size.width < 100.dp || size.height < 70.dp) {
            Column(
                modifier = GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(16.dp)
                    .background(bg).padding(4.dp)
                    .clickable(actionRunCallback<RefreshAction>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = level?.value?.toString() ?: if (error != null) "!" else "…",
                    style = TextStyle(color = fgProvider, fontSize = 30.sp, fontWeight = FontWeight.Bold),
                )
                if (size.height >= 60.dp) {
                    Text(
                        text = level?.label ?: if (error != null) "chyba" else "",
                        style = TextStyle(color = fgProvider, fontSize = 10.sp),
                        maxLines = 1,
                    )
                }
            }
            return
        }

        Column(
            modifier = GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(16.dp)
                .background(bg).padding(10.dp)
                .clickable(actionRunCallback<RefreshAction>()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = level?.value?.toString() ?: "–",
                    style = TextStyle(color = fgProvider, fontSize = 34.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.width(8.dp))
                Column {
                    Text(
                        text = when {
                            level != null -> level.label
                            dataLabel == null -> "načítám…"
                            else -> "bez dat"
                        },
                        style = TextStyle(color = fgProvider, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    Text(
                        text = error ?: dataLabel?.let { "mapa: $it" } ?: "",
                        style = TextStyle(color = fgProvider, fontSize = 11.sp),
                        maxLines = if (error != null) 3 else 1,
                    )
                }
            }

            val cropPath = prefs[WidgetKeys.CROP_PATH]
            if (size.height >= 120.dp && cropPath != null) {
                val bitmap = File(cropPath).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
                if (bitmap != null) {
                    Spacer(GlanceModifier.height(6.dp))
                    Image(
                        provider = ImageProvider(bitmap),
                        contentDescription = "Výřez mapy kolem vybraného místa",
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(10.dp),
                    )
                }
            }
        }
    }
}

/** Klepnutí na widget = okamžitá obnova dat. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshWorker.refreshNow(context)
    }
}

class HoubyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HoubyWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.ensurePeriodic(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RefreshWorker.ensurePeriodic(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { File(context.filesDir, "crop_$it.png").delete() }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshWorker.cancelAll(context)
    }
}
