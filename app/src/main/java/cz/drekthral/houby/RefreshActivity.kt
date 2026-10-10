package cz.drekthral.houby

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Neviditelná aktivita spouštěná klepnutím na widget. Stahuje v popředí, takže funguje
 * i se zapnutým Spořičem dat (ten blokuje síť jen aplikacím na pozadí).
 */
class RefreshActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(this, "Stahuji mapu ČHMÚ…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val msg = when (val o = WidgetUpdater.refresh(this@RefreshActivity, reportError = true)) {
                is WidgetUpdater.Outcome.Updated -> "Aktualizováno – mapa ${o.timeLabel}"
                is WidgetUpdater.Outcome.Failed -> "Chyba: ${WidgetUpdater.describe(o.error)}"
                else -> null
            }
            msg?.let { Toast.makeText(applicationContext, it, Toast.LENGTH_LONG).show() }
            finish()
        }
    }
}
