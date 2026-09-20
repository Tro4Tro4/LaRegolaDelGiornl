package it.regoladelgiorno.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Conversione fra istanti reali e "giorno logico" dell'app.
 *
 * Il confine del giorno e' configurabile dall'utente (default 00:00).
 * Con confine 04:00, una risposta data all'01:30 appartiene ancora a ieri.
 *
 * Regola: questo file e' l'UNICO posto autorizzato a derivare una data da un
 * istante. Nessun LocalDate.now() altrove nel progetto.
 */
object Tempo {

    /**
     * Il giorno logico a cui appartiene [istante].
     * Il confine e' inclusivo: alle 04:00 esatte inizia il giorno nuovo.
     */
    fun giornoLogico(istante: Instant, zona: ZoneId, confine: LocalTime): Long {
        val locale = istante.atZone(zona)
        val data = locale.toLocalDate()
        return if (locale.toLocalTime() < confine) {
            data.minusDays(1).toEpochDay()
        } else {
            data.toEpochDay()
        }
    }

    /**
     * L'istante reale in cui cade [ora] durante il giorno logico [giorno].
     *
     * Se l'ora richiesta e' prima del confine, cade sulla data di calendario
     * successiva: con confine 04:00, la "sera" delle 02:00 del giorno logico
     * del 14 e' in realta' il 15 alle 02:00.
     *
     * I salti di ora legale sono risolti da java.time: un orario inesistente
     * viene spostato in avanti della durata del salto.
     */
    fun istanteDi(giorno: Long, ora: LocalTime, zona: ZoneId, confine: LocalTime): Instant {
        val base = LocalDate.ofEpochDay(giorno)
        val data = if (ora < confine) base.plusDays(1) else base
        return data.atTime(ora).atZone(zona).toInstant()
    }

    /** Sabato e domenica sono FESTIVO. Non gestiamo le festivita' civili: sono locali e cambiano. */
    fun contestoDi(giorno: Long): Contesto {
        val gds = LocalDate.ofEpochDay(giorno).dayOfWeek.value // 1=lun .. 7=dom
        return if (gds >= 6) Contesto.FESTIVO else Contesto.FERIALE
    }
}
