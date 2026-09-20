package it.regoladelgiorno.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import it.regoladelgiorno.core.OrariUtente
import it.regoladelgiorno.core.Pianificatore
import it.regoladelgiorno.core.Sveglia
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Allarmi INESATTI. Non e' un ripiego: l'app non ha bisogno di precisione al
 * secondo, e USE_EXACT_ALARM e' una permission che Google Play riserva a
 * sveglie, timer e calendari. SCHEDULE_EXACT_ALARM sarebbe concedibile ma e'
 * negata di default da Android 14 e costerebbe un passaggio nelle impostazioni
 * di sistema per un guadagno nullo.
 *
 * setAndAllowWhileIdle attraversa il Doze; il sistema consegna entro circa
 * un'ora dall'orario richiesto, e mai prima.
 */
object Sveglie {

    const val AZIONE_MATTINO = "it.regoladelgiorno.MATTINO"
    const val AZIONE_SERA = "it.regoladelgiorno.SERA"
    const val EXTRA_GIORNO = "giorno"

    private const val CODICE_MATTINO = 1
    private const val CODICE_SERA = 2
    private const val LAVORO_RETE = "rete_di_sicurezza"

    fun riprogrammaTutto(context: Context, orari: OrariUtente, zona: ZoneId = ZoneId.systemDefault()) {
        val adesso = Instant.now()
        programma(context, AZIONE_MATTINO, CODICE_MATTINO,
            Pianificatore.prossimoMattino(adesso, zona, orari))
        programma(context, AZIONE_SERA, CODICE_SERA,
            Pianificatore.prossimaSera(adesso, zona, orari))
        reteDiSicurezza(context)
    }

    private fun programma(context: Context, azione: String, codice: Int, sveglia: Sveglia) {
        val gestore = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, RicevitoreSveglia::class.java).apply {
            action = azione
            putExtra(EXTRA_GIORNO, sveglia.giorno)
        }
        // FLAG_IMMUTABLE e' obbligatorio da API 31 ed esiste da API 23:
        // con minSdk 23 non serve nessun ramo condizionale.
        val pending = PendingIntent.getBroadcast(
            context, codice, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        gestore.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, sveglia.istante.toEpochMilli(), pending
        )
    }

    /**
     * Xiaomi, Oppo e Huawei terminano i processi in background con criteri
     * propri e non documentati: l'allarme puo' non arrivare affatto.
     * Un lavoro periodico ogni sei ore ricontrolla lo stato della giornata.
     * Non e' ridondanza inutile, e' l'unica difesa possibile.
     */
    private fun reteDiSicurezza(context: Context) {
        val lavoro = PeriodicWorkRequestBuilder<LavoroControllo>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            LAVORO_RETE, ExistingPeriodicWorkPolicy.KEEP, lavoro
        )
    }
}

/** Scatto di un allarme: delega subito a WorkManager e restituisce il controllo. */
class RicevitoreSveglia : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val giorno = intent.getLongExtra(Sveglie.EXTRA_GIORNO, Long.MIN_VALUE)
        if (giorno == Long.MIN_VALUE) return
        when (intent.action) {
            Sveglie.AZIONE_MATTINO -> LavoroGiornata.apri(context, giorno)
            Sveglie.AZIONE_SERA -> LavoroGiornata.chiudi(context, giorno)
        }
    }
}

/**
 * Gli allarmi vengono azzerati dal riavvio, dall'aggiornamento dell'app e
 * possono restare disallineati dopo un cambio di fuso orario.
 * ACTION_MY_PACKAGE_REPLACED e' quello che si dimentica sempre.
 */
class RicevitoreRipristino : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> LavoroGiornata.riallinea(context)
        }
    }
}

/** Tap su "Si" o "No" nella notifica serale. */
class RicevitoreRisposta : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val giorno = intent.getLongExtra(Sveglie.EXTRA_GIORNO, Long.MIN_VALUE)
        if (giorno == Long.MIN_VALUE) return
        val risposta = intent.getBooleanExtra(EXTRA_RISPOSTA, false)
        LavoroGiornata.rispondi(context, giorno, risposta)
        Notifiche.chiudiSerale(context)
    }

    companion object {
        const val EXTRA_RISPOSTA = "risposta"
        const val AZIONE = "it.regoladelgiorno.RISPOSTA"
    }
}
