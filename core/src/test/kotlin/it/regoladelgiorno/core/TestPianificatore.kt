package it.regoladelgiorno.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private val RM: ZoneId = ZoneId.of("Europe/Rome")
private val KATHMANDU: ZoneId = ZoneId.of("Asia/Kathmandu") // offset +05:45, non intero
private val ORARI = OrariUtente()

private fun rm(iso: String): Instant = LocalDateTime.parse(iso).atZone(RM).toInstant()
private fun gg(iso: String): Long = LocalDate.parse(iso).toEpochDay()

internal fun testPianificatore() {

    test("mattino: se le 7 non sono ancora passate, e' oggi") {
        val s = Pianificatore.prossimoMattino(rm("2026-05-04T06:00"), RM, ORARI)
        eq(gg("2026-05-04"), s.giorno)
        eq(rm("2026-05-04T07:00"), s.istante)
    }

    test("mattino: se le 7 sono passate, e' domani") {
        val s = Pianificatore.prossimoMattino(rm("2026-05-04T07:01"), RM, ORARI)
        eq(gg("2026-05-05"), s.giorno)
    }

    test("mattino: all'istante esatto si passa a domani, niente riscatto immediato") {
        val s = Pianificatore.prossimoMattino(rm("2026-05-04T07:00"), RM, ORARI)
        eq(gg("2026-05-05"), s.giorno)
    }

    test("sera: il giorno logico e' quello giusto") {
        val s = Pianificatore.prossimaSera(rm("2026-05-04T08:00"), RM, ORARI)
        eq(gg("2026-05-04"), s.giorno)
        eq(rm("2026-05-04T21:30"), s.istante)
    }

    test("confine 04:00 e sera alle 02:00: scatta sul calendario del giorno dopo") {
        val orari = OrariUtente(sera = LocalTime.of(2, 0), confine = LocalTime.of(4, 0))
        val s = Pianificatore.prossimaSera(rm("2026-05-04T23:00"), RM, orari)
        eq(gg("2026-05-04"), s.giorno, "e' ancora il giorno logico del 4")
        eq(rm("2026-05-05T02:00"), s.istante)
    }

    test("confine 04:00: alle 03:00 la sera di ieri e' ancora davanti") {
        val orari = OrariUtente(sera = LocalTime.of(2, 0), confine = LocalTime.of(4, 0))
        val s = Pianificatore.prossimaSera(rm("2026-05-05T01:00"), RM, orari)
        eq(gg("2026-05-04"), s.giorno)
    }

    test("ora legale in avanti: un mattino inesistente viene spostato, non saltato") {
        // 2026-03-29 Roma: le 02:00 diventano le 03:00
        val orari = OrariUtente(mattino = LocalTime.of(2, 30))
        val s = Pianificatore.prossimoMattino(rm("2026-03-29T00:30"), RM, orari)
        eq(gg("2026-03-29"), s.giorno)
        eq(LocalTime.of(3, 30), s.istante.atZone(RM).toLocalTime())
    }

    test("ora legale indietro: l'ora doppia non produce due sveglie") {
        // 2026-10-25 Roma: le 02:30 esistono due volte
        val orari = OrariUtente(mattino = LocalTime.of(2, 30))
        val prima = Instant.parse("2026-10-25T00:30:00Z") // prima occorrenza
        val s = Pianificatore.prossimoMattino(prima, RM, orari)
        eq(gg("2026-10-26"), s.giorno, "la sveglia di oggi e' gia' scattata")
    }

    test("fuso con offset non intero") {
        val s = Pianificatore.prossimoMattino(
            LocalDateTime.parse("2026-05-04T06:00").atZone(KATHMANDU).toInstant(), KATHMANDU, ORARI
        )
        eq(LocalTime.of(7, 0), s.istante.atZone(KATHMANDU).toLocalTime())
    }

    test("seraPassata: prima no, dopo si") {
        vero(!Pianificatore.seraPassata(rm("2026-05-04T21:29"), RM, ORARI, gg("2026-05-04")))
        vero(Pianificatore.seraPassata(rm("2026-05-04T21:30"), RM, ORARI, gg("2026-05-04")))
        vero(Pianificatore.seraPassata(rm("2026-05-05T09:00"), RM, ORARI, gg("2026-05-04")))
    }

    test("ciclo di riprogrammazione su 400 giorni: nessun giorno saltato o ripetuto") {
        for (orari in listOf(ORARI, OrariUtente(sera = LocalTime.of(2, 0), confine = LocalTime.of(4, 0)))) {
            var adesso = rm("2025-12-01T05:00")
            var atteso: Long? = null
            var precedente: Instant? = null
            repeat(400) {
                val s = Pianificatore.prossimoMattino(adesso, RM, orari)
                if (atteso != null) eq(atteso, s.giorno, "giorno fuori sequenza con $orari")
                precedente?.let {
                    val ore = Duration.between(it, s.istante).toHours()
                    vero(ore in 23..25, "salto anomalo di $ore ore")
                }
                atteso = s.giorno + 1
                precedente = s.istante
                adesso = s.istante // lo scatto riprogramma da se stesso
            }
        }
    }

    test("sera e mattino non si scavalcano mai nell'arco di un anno") {
        var adesso = rm("2026-01-01T05:00")
        repeat(365) {
            val m = Pianificatore.prossimoMattino(adesso, RM, ORARI)
            val s = Pianificatore.prossimaSera(m.istante, RM, ORARI)
            eq(m.giorno, s.giorno, "la sera dopo il mattino deve chiudere lo stesso giorno logico")
            adesso = s.istante
        }
    }
}
