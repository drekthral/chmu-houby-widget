package cz.drekthral.houby

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Nastavení widgetu: vyhledání obce a uložení jejích souřadnic.
 * Spuštěno z ikony aplikace (bez ID widgetu) jen zobrazí návod.
 */
class ConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, resultIntent())

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                        InfoScreen()
                    } else {
                        SearchScreen(onPick = ::savePlace)
                    }
                }
            }
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private fun savePlace(place: Geocoder.Place) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@ConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@ConfigActivity, glanceId) {
                it[WidgetKeys.PLACE_NAME] = place.name
                it[WidgetKeys.LAT] = place.lat
                it[WidgetKeys.LON] = place.lon
                it.remove(WidgetKeys.LEVEL)
                it.remove(WidgetKeys.DATA_LABEL)
                it.remove(WidgetKeys.CROP_PATH)
                it.remove(WidgetKeys.ERROR)
            }
            HoubyWidget().update(this@ConfigActivity, glanceId)
            RefreshWorker.ensurePeriodic(this@ConfigActivity)
            RefreshWorker.refreshNow(this@ConfigActivity)
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun InfoScreen() {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Houby ČHMÚ", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Přidej si na plochu widget „Houby ČHMÚ“ (dlouhé podržení na ploše → Widgety) " +
                "a v nastavení vyber místo. Widget jednou denně po vygenerování mapy ČHMÚ " +
                "ukáže pravděpodobnost růstu hub. Klepnutím na widget data obnovíš.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Zdroj dat: ČHMÚ, mapa Pravděpodobnost růstu hub (CC BY 4.0). Vyhledávání obcí: © OpenStreetMap.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SearchScreen(onPick: (Geocoder.Place) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Geocoder.Place>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun search() {
        if (query.isBlank() || loading) return
        loading = true
        message = null
        scope.launch {
            try {
                val found = withContext(Dispatchers.IO) { Geocoder.search(query) }
                    .filter { ChmuRepository.isInside(it.lat, it.lon) }
                results = found
                if (found.isEmpty()) message = "Nic nenalezeno."
            } catch (e: Exception) {
                message = "Vyhledávání selhalo: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text("Vyber místo pro widget", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Obec nebo místo") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.padding(4.dp))
            Button(onClick = { search() }, enabled = !loading) { Text("Hledat") }
        }
        Spacer(Modifier.height(12.dp))
        if (loading) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(results) { place ->
                ListItem(
                    headlineContent = { Text(place.name) },
                    supportingContent = { Text(place.detail, maxLines = 2) },
                    modifier = Modifier.clickable { onPick(place) },
                )
                HorizontalDivider()
            }
        }
    }
}
