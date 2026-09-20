package it.regoladelgiorno.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.regoladelgiorno.R
import it.regoladelgiorno.core.Contapassi
import it.regoladelgiorno.core.Misurazione
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object Notifiche {

    private const val CANALE = "regola"
    private const val ID_MATTINO = 100
    private const val ID_SERA = 101

    /** I canali esistono da API 26; sotto e' un no-op. */
    fun assicuraCanale(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canale = NotificationChannel(
            CANALE, "La regola del giorno", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Una notifica la mattina, una la sera."
            setShowBadge(false) // niente pallino rosso: non e' una cosa da smaltire
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canale)
    }

    /** La regola sta tutta nella notifica: aprire l'app non deve essere necessario. */
    fun mattutina(context: Context, testo: String) {
        mostra(context, ID_MATTINO, NotificationCompat.Builder(context, CANALE)
            .setContentTitle(testo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
            .setSmallIcon(R.drawable.ic_notifica)
            .setContentIntent(aperturaApp(context))
            .setAutoCancel(true))
    }

    /**
     * La domanda serale si risponde dalla notifica, senza aprire nulla.
     * Ignorarla e' una risposta valida: non insistiamo.
     */
    fun serale(context: Context, giorno: Long, testo: String) {
        mostra(context, ID_SERA, NotificationCompat.Builder(context, CANALE)
            .setContentTitle(testo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
            .setSmallIcon(R.drawable.ic_notifica)
            .addAction(0, context.getString(R.string.azione_si), rispostaPending(context, giorno, true))
            .addAction(0, context.getString(R.string.azione_no), rispostaPending(context, giorno, false))
            .setContentIntent(aperturaApp(context))
            .setAutoCancel(true))
    }

    fun chiudiSerale(context: Context) =
        NotificationManagerCompat.from(context).cancel(ID_SERA)

    private fun mostra(context: Context, id: Int, b: NotificationCompat.Builder) {
        if (!permessoNotifiche(context)) return
        NotificationManagerCompat.from(context).notify(id, b.build())
    }

    /** POST_NOTIFICATIONS e' runtime da API 33; sotto e' sempre concesso. */
    fun permessoNotifiche(context: Context): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun rispostaPending(context: Context, giorno: Long, risposta: Boolean): PendingIntent {
        val intent = Intent(context, RicevitoreRisposta::class.java).apply {
            action = RicevitoreRisposta.AZIONE
            putExtra(Sveglie.EXTRA_GIORNO, giorno)
            putExtra(RicevitoreRisposta.EXTRA_RISPOSTA, risposta)
        }
        // requestCode distinto per si e no, altrimenti il secondo PendingIntent
        // riuserebbe gli extra del primo.
        return PendingIntent.getBroadcast(
            context, if (risposta) 10 else 11, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun aperturaApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

/**
 * TYPE_STEP_COUNTER conta in hardware anche ad app morta e restituisce il
 * cumulativo dal boot: bastano due letture al giorno, senza alcun servizio
 * attivo. Molti dispositivi datati non hanno il sensore: si restituisce null,
 * e la giornata diventa una domanda.
 */
class ContapassiAndroid(private val context: Context) : Contapassi {

    override suspend fun leggi(): Misurazione? {
        if (!permessoConcesso()) return null
        val gestore = context.getSystemService(SensorManager::class.java) ?: return null
        val sensore = gestore.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null

        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val ascoltatore = object : SensorEventListener {
                    override fun onSensorChanged(evento: SensorEvent) {
                        gestore.unregisterListener(this)
                        if (cont.isActive) {
                            cont.resume(Misurazione(
                                passi = evento.values.firstOrNull()?.toLong() ?: 0L,
                                realtimeMs = SystemClock.elapsedRealtime()
                            ))
                        }
                    }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
                }
                gestore.registerListener(ascoltatore, sensore, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { gestore.unregisterListener(ascoltatore) }
            }
        }
    }

    /** ACTIVITY_RECOGNITION e' runtime da API 29; prima non serviva. */
    private fun permessoConcesso(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED

    private companion object { const val TIMEOUT_MS = 5_000L }
}
