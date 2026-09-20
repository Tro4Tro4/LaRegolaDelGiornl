package it.regoladelgiorno.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private val ZONA: ZoneId = ZoneId.of("Europe/Rome")
private val ORARI_R = OrariUtente()

private fun ist(iso: String): Instant = LocalDateTime.parse(iso).atZone(ZONA).toInstant()
private fun gio(iso: String): Long = LocalDate.parse(iso).toEpochDay()

/**
 * Quali giorni rimasti aperti vanno archiviati, e per quale di essi ha ancora
 * senso porre la domanda. E' la decisione della rete di sicurezza: sta qui e non
 * nel worker perche' e' aritmetica di calendario, e nel worker non sarebbe
 * verificabile senza un emulatore.
 */
internal fun testRecupero() {

    test("recupero: senza giorni aperti non c'e' niente da chiudere") {
        eq(emptyList(), Recupero.daChiudere(gio("2026-05-10"), emptyList(), ist("2026-05-10T23:00"), ZONA, ORARI_R))
    }

    test("recupero: un giorno la cui sera non e' ancora passata non si tocca") {
        val oggi = gio("2026-05-10")
        eq(emptyList(), Recupero.daChiudere(oggi, listOf(oggi), ist("2026-05-10T20:00"), ZONA, ORARI_R))
    }

    test("recupero: oggi si chiude se la sua sera e' gia' passata, e si chiede") {
        val oggi = gio("2026-05-10")
        val r = Recupero.daChiudere(oggi, listOf(oggi), ist("2026-05-10T22:00"), ZONA, ORARI_R)
        eq(listOf(Recupero.Chiusura(oggi, conDomanda = true)), r)
    }

    test("recupero: piu' giorni arretrati si chiudono tutti, non solo ieri") {
        val oggi = gio("2026-05-10")
        val aperti = listOf(oggi - 4, oggi - 3, oggi - 2, oggi - 1)
        val r = Recupero.daChiudere(oggi, aperti, ist("2026-05-10T09:00"), ZONA, ORARI_R)
        eq(aperti, r.map { it.giorno }, "nessun giorno arretrato deve restare aperto")
    }

    test("recupero: la domanda spetta solo al giorno appena passato") {
        val oggi = gio("2026-05-10")
        val aperti = listOf(oggi - 4, oggi - 3, oggi - 2, oggi - 1)
        val r = Recupero.daChiudere(oggi, aperti, ist("2026-05-10T09:00"), ZONA, ORARI_R)
        eq(listOf(oggi - 1), r.filter { it.conDomanda }.map { it.giorno })
    }

    test("recupero: se il piu' recente e' piu' vecchio di ieri, non si chiede a nessuno") {
        val oggi = gio("2026-05-10")
        val r = Recupero.daChiudere(oggi, listOf(oggi - 6, oggi - 5), ist("2026-05-10T09:00"), ZONA, ORARI_R)
        eq(2, r.size)
        vero(r.none { it.conDomanda }, "nessuna domanda su giorni cosi' arretrati")
    }

    test("recupero: l'ordine in ingresso non conta, si chiude dal piu' vecchio") {
        val oggi = gio("2026-05-10")
        val r = Recupero.daChiudere(oggi, listOf(oggi - 1, oggi - 3, oggi - 2), ist("2026-05-10T09:00"), ZONA, ORARI_R)
        eq(listOf(oggi - 3, oggi - 2, oggi - 1), r.map { it.giorno })
    }

    test("recupero: con confine 04:00 la sera delle 02:00 di ieri e' passata solo dopo") {
        val orari = OrariUtente(sera = java.time.LocalTime.of(2, 0), confine = java.time.LocalTime.of(4, 0))
        val ieri = gio("2026-05-09")
        // le 01:00 del 10 maggio: il giorno logico e' ancora il 9, e la sua sera
        // (le 02:00 del 10) non e' ancora arrivata
        eq(emptyList(), Recupero.daChiudere(ieri, listOf(ieri), ist("2026-05-10T01:00"), ZONA, orari))
        // un'ora dopo si'
        eq(1, Recupero.daChiudere(ieri, listOf(ieri), ist("2026-05-10T02:30"), ZONA, orari).size)
    }
}
