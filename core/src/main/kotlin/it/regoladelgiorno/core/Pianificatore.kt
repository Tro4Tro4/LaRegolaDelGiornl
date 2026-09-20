package it.regoladelgiorno.core

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class OrariUtente(
    val mattino: LocalTime = LocalTime.of(7, 0),
    val sera: LocalTime = LocalTime.of(21, 30),
    val confine: LocalTime = LocalTime.MIDNIGHT
)

/** Quale giorno logico serve risvegliare, e quando. */
data class Sveglia(val giorno: Long, val istante: Instant)

/**
 * Nessun AlarmManager qui dentro: solo aritmetica di calendario.
 * E' il punto dove l'ora legale rompe le app, quindi e' il punto che deve
 * essere testabile senza un emulatore.
 */
object Pianificatore {

    fun prossimoMattino(adesso: Instant, zona: ZoneId, orari: OrariUtente): Sveglia =
        prossima(adesso, zona, orari.mattino, orari.confine)

    fun prossimaSera(adesso: Instant, zona: ZoneId, orari: OrariUtente): Sveglia =
        prossima(adesso, zona, orari.sera, orari.confine)

    /**
     * La prima occorrenza strettamente successiva ad [adesso].
     * Strettamente: riprogrammando subito dopo lo scatto, "adesso" coincide con
     * l'istante appena passato e non deve riproporlo.
     *
     * Si parte dal giorno logico corrente: istanteDi e' monotona in g, quindi
     * se l'occorrenza di ieri fosse ancora futura lo sarebbe anche quella di
     * oggi, e giornoLogico non direbbe "oggi".
     */
    private fun prossima(adesso: Instant, zona: ZoneId, ora: LocalTime, confine: LocalTime): Sveglia {
        val corrente = Tempo.giornoLogico(adesso, zona, confine)
        for (g in corrente..(corrente + 2)) {
            val i = Tempo.istanteDi(g, ora, zona, confine)
            if (i.isAfter(adesso)) return Sveglia(g, i)
        }
        error("nessuna sveglia trovata attorno a $adesso") // irraggiungibile: istanteDi e' monotona
    }

    /** Usata dalla rete di sicurezza: la sera di [giorno] e' gia' passata? */
    fun seraPassata(adesso: Instant, zona: ZoneId, orari: OrariUtente, giorno: Long): Boolean =
        !Tempo.istanteDi(giorno, orari.sera, zona, orari.confine).isAfter(adesso)
}
