package it.regoladelgiorno.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.regoladelgiorno.core.Motore
import it.regoladelgiorno.core.Pianificatore
import it.regoladelgiorno.core.Tempo
import it.regoladelgiorno.core.Verdetto
import java.time.Instant
import java.time.ZoneId

/**
 * I ricevitori hanno pochi secondi di vita: delegano subito qui.
 * Ogni operazione e' idempotente lato Motore, quindi un doppio scatto o un
 * retry di WorkManager non fanno danni.
 */
object LavoroGiornata {

    private const val OPERAZIONE = "operazione"
    private const val GIORNO = "giorno"
    private const val RISPOSTA = "risposta"

    fun apri(context: Context, giorno: Long) = accoda(context, "apri", giorno)
    fun chiudi(context: Context, giorno: Long) = accoda(context, "chiudi", giorno)
    fun riallinea(context: Context) = accoda(context, "riallinea", 0)

    fun rispondi(context: Context, giorno: Long, risposta: Boolean) =
        accoda(context, "rispondi", giorno, risposta)

    private fun accoda(context: Context, operazione: String, giorno: Long, risposta: Boolean = false) {
        val dati = Data.Builder()
            .putString(OPERAZIONE, operazione)
            .putLong(GIORNO, giorno)
            .putBoolean(RISPOSTA, risposta)
            .build()
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<LavoroEsecutore>().setInputData(dati).build()
        )
    }

    internal fun operazioneDi(dati: Data) = Triple(
        dati.getString(OPERAZIONE).orEmpty(),
        dati.getLong(GIORNO, 0),
        dati.getBoolean(RISPOSTA, false)
    )
}

class LavoroEsecutore(context: Context, parametri: WorkerParameters) :
    CoroutineWorker(context, parametri) {

    override suspend fun doWork(): Result {
        val (operazione, giorno, risposta) = LavoroGiornata.operazioneDi(inputData)
        val app = Dipendenze.di(applicationContext)
        val orari = app.orari()
        val zona = ZoneId.systemDefault()

        Notifiche.assicuraCanale(applicationContext)

        when (operazione) {
            "apri" -> {
                val g = app.motore.apri(giorno)
                Notifiche.mattutina(applicationContext, g.testo)
            }
            "chiudi" -> {
                val g = app.motore.chiudi(giorno)
                // Si chiede solo quando c'e' davvero qualcosa da chiedere.
                if (g != null && g.verdetto == Verdetto.IGNOTO) {
                    Notifiche.serale(applicationContext, giorno, g.testo)
                }
            }
            "rispondi" -> app.motore.rispondi(giorno, risposta)
            "riallinea" -> Unit // la riprogrammazione avviene comunque sotto
        }

        Sveglie.riprogrammaTutto(applicationContext, orari, zona)
        return Result.success()
    }
}

/**
 * Rete di sicurezza contro i produttori che terminano i processi in background.
 * Ogni sei ore controlla se la giornata e' nello stato che dovrebbe avere.
 * Non manda mai una notifica fuori tempo: apre solo cio' che era da aprire e
 * chiude solo cio' che e' gia' scaduto.
 */
class LavoroControllo(context: Context, parametri: WorkerParameters) :
    CoroutineWorker(context, parametri) {

    override suspend fun doWork(): Result {
        val app = Dipendenze.di(applicationContext)
        val orari = app.orari()
        val zona = ZoneId.systemDefault()
        val adesso = Instant.now()
        val oggi = Tempo.giornoLogico(adesso, zona, orari.confine)

        val mattinoPassato =
            Pianificatore.prossimoMattino(adesso, zona, orari).giorno != oggi
        if (mattinoPassato && app.archivio.leggi(oggi) == null) {
            val g = app.motore.apri(oggi)
            Notifiche.assicuraCanale(applicationContext)
            Notifiche.mattutina(applicationContext, g.testo)
        }

        val ieri = oggi - 1
        if (Pianificatore.seraPassata(adesso, zona, orari, ieri)) {
            app.archivio.leggi(ieri)?.takeIf { it.aperto }?.let { app.motore.chiudi(ieri) }
        }

        Sveglie.riprogrammaTutto(applicationContext, orari, zona)
        return Result.success()
    }
}
