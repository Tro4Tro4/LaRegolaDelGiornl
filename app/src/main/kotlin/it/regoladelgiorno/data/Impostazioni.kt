package it.regoladelgiorno.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import kotlin.random.Random

/**
 * Le impostazioni non sono storia: stanno in DataStore, non in Room.
 * Sono quattro, e si raggiungono solo dal fondo dello storico.
 */
data class Impostazioni(
    val oraMattino: LocalTime = LocalTime.of(7, 0),
    val oraSera: LocalTime = LocalTime.of(21, 30),
    val confineGiorno: LocalTime = LocalTime.MIDNIGHT,
    val osservazioneAutomatica: Boolean = false,
    /** Fissato alla prima apertura: rende la sequenza di regole diversa per ogni installazione. */
    val salt: Long = 0L
)

private val Context.store by preferencesDataStore("impostazioni")

class DepositoImpostazioni(private val context: Context) {

    private object Chiavi {
        val mattino = intPreferencesKey("ora_mattino_minuti")
        val sera = intPreferencesKey("ora_sera_minuti")
        val confine = intPreferencesKey("confine_minuti")
        val osservazione = booleanPreferencesKey("osservazione_automatica")
        val salt = longPreferencesKey("salt")
    }

    val flusso: Flow<Impostazioni> = context.store.data.map { p -> leggi(p) }

    private fun leggi(p: Preferences) = Impostazioni(
        oraMattino = oraDa(p[Chiavi.mattino], 7 * 60),
        oraSera = oraDa(p[Chiavi.sera], 21 * 60 + 30),
        confineGiorno = oraDa(p[Chiavi.confine], 0),
        osservazioneAutomatica = p[Chiavi.osservazione] ?: false,
        salt = p[Chiavi.salt] ?: 0L
    )

    private fun oraDa(minuti: Int?, predefinito: Int): LocalTime =
        LocalTime.ofSecondOfDay((minuti ?: predefinito) * 60L)

    /** Chiamata una sola volta, alla prima apertura. */
    suspend fun assicuraSalt(): Long {
        var valore = 0L
        context.store.edit { p ->
            valore = p[Chiavi.salt] ?: Random.nextLong().also { p[Chiavi.salt] = it }
        }
        return valore
    }

    suspend fun imposta(
        mattino: LocalTime? = null,
        sera: LocalTime? = null,
        confine: LocalTime? = null,
        osservazione: Boolean? = null
    ) {
        context.store.edit { p ->
            mattino?.let { p[Chiavi.mattino] = it.toSecondOfDay() / 60 }
            sera?.let { p[Chiavi.sera] = it.toSecondOfDay() / 60 }
            confine?.let { p[Chiavi.confine] = it.toSecondOfDay() / 60 }
            osservazione?.let { p[Chiavi.osservazione] = it }
        }
    }
}
