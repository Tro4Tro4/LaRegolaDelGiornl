package it.regoladelgiorno.core

import java.time.Instant
import java.time.ZoneId

/**
 * Cosa deve fare la rete di sicurezza quando riprende il controllo dopo che il
 * sistema ha ucciso l'app: quali giornate rimaste aperte vanno archiviate, e per
 * quale di esse ha ancora senso porre la domanda della sera.
 *
 * Sta nel core e non nel worker perche' e' aritmetica di calendario. Nel worker
 * sarebbe verificabile solo con un emulatore e un orologio finto, cioe' non
 * sarebbe verificata.
 */
object Recupero {

    /** Un giorno da chiudere, e se all'utente va ancora chiesto com'e' andata. */
    data class Chiusura(val giorno: Long, val conDomanda: Boolean)

    /**
     * [aperti] sono i giorni logici rimasti senza verdetto, in qualunque ordine.
     * Tornano in ordine cronologico: si archivia dal piu' vecchio, cosi' ogni
     * chiusura entra nella mediana personale prima che si valuti la successiva.
     *
     * La domanda spetta al solo giorno appena passato, e solo se e' davvero
     * appena passato: chiederla per un giorno di una settimana fa sarebbe un
     * interrogatorio, non un'osservazione. Gli altri si archiviano dichiarando
     * il vero, cioe' che nessuno ha mai chiesto.
     */
    fun daChiudere(
        oggi: Long,
        aperti: List<Long>,
        adesso: Instant,
        zona: ZoneId,
        orari: OrariUtente
    ): List<Chiusura> {
        val scaduti = aperti
            .filter { Pianificatore.seraPassata(adesso, zona, orari, it) }
            .distinct()
            .sorted()

        val interrogabile = scaduti.lastOrNull()?.takeIf { it >= oggi - 1 }
        return scaduti.map { Chiusura(it, conDomanda = it == interrogabile) }
    }
}
