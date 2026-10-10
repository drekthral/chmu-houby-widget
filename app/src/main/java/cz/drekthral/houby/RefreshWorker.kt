package cz.drekthral.houby

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Stáhne mapu ČHMÚ a aktualizuje všechny widgety.
 *
 * Běží periodicky každých 30 minut, ale na síť sahá jen tehdy, když widgety ještě nemají
 * dnešní mapu a je po [FETCH_AFTER] (mapa se generuje dopoledne, kolem 10:45).
 * V praxi se tak data stahují jednou denně, zhruba 15–45 minut po vygenerování mapy.
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val force = inputData.getBoolean(KEY_FORCE, false)
        val outcome = WidgetUpdater.refresh(
            applicationContext,
            reportError = force,
            shouldFetch = if (force) null else ::shouldFetch,
        )
        // Periodický běh to zkusí znovu za 30 minut, ruční obnovu zopakuje WorkManager.
        if (force && outcome is WidgetUpdater.Outcome.Failed && outcome.error is IOException && runAttemptCount < 3) {
            return Result.retry()
        }
        return Result.success()
    }

    private fun shouldFetch(states: List<Preferences>): Boolean {
        val now = LocalTime.now(PRAGUE)
        val today = LocalDate.now(PRAGUE)
        // Widget bez dat (nově přidaný nebo po chybě) – stáhnout hned.
        if (states.any { it[WidgetKeys.DATA_LABEL] == null }) return true
        if (states.all { isLabelFor(it[WidgetKeys.DATA_LABEL], today) }) return false
        return now >= FETCH_AFTER
    }

    companion object {
        private const val KEY_FORCE = "force"
        private const val PERIODIC_NAME = "houby-periodic"
        private const val NOW_NAME = "houby-now"

        /** Mapa ČHMÚ se generuje kolem 10:45 místního času, stahujeme po 11:00. */
        val FETCH_AFTER: LocalTime = LocalTime.of(11, 0)
        val PRAGUE: ZoneId = ZoneId.of("Europe/Prague")

        private val LABEL_DATE = Regex("""(\d{1,2})\.\s*(\d{1,2})\.""")

        /** `timeLabel` má tvar „so 10. 10.“ – den a měsíc, ke kterým mapa platí. */
        fun isLabelFor(label: String?, date: LocalDate): Boolean {
            val m = LABEL_DATE.find(label ?: return false) ?: return false
            return m.groupValues[1].toInt() == date.dayOfMonth && m.groupValues[2].toInt() == date.monthValue
        }

        private val networkConstraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun ensurePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInputData(workDataOf(KEY_FORCE to true))
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(NOW_NAME)
        }
    }
}
