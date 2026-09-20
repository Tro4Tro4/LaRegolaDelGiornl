package it.regoladelgiorno.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

// --- fixture ----------------------------------------------------------------

private val ROMA: ZoneId = ZoneId.of("Europe/Rome")
private val NEWYORK: ZoneId = ZoneId.of("America/New_York")
private val MEZZANOTTE: LocalTime = LocalTime.MIDNIGHT
private val QUATTRO: LocalTime = LocalTime.of(4, 0)

private fun giorno(iso: String): Long = LocalDate.parse(iso).toEpochDay()

private fun istanteRoma(iso: String): Instant =
    LocalDateTime.parse(iso).atZone(ROMA).toInstant()

private fun catalogoFinto(n: Int): List<Regola> = (0 until n).map { i ->
    Regola(
        id = "r%03d".format(i),
        testo = "Oggi fai la cosa numero $i.",
        categoria = Categoria.entries[i % Categoria.entries.size],
        livello = Livello.DICHIARATA,
        contesto = when {
            i % 10 == 0 -> Contesto.FERIALE
            i % 25 == 0 -> Contesto.FESTIVO
            else -> Contesto.QUALSIASI
        },
        intensita = (i % 3) + 1
    )
}

private fun regolaPassi(soglia: Int, metrica: Metrica) = Regola(
    id = "p1", testo = "Oggi cammina venti minuti senza destinazione.",
    categoria = Categoria.FISICO, livello = Livello.OSSERVABILE,
    metrica = metrica, soglia = soglia
)

private val regolaSemplice = Regola(
    id = "d1", testo = "Oggi telefona a chi avresti scritto.",
    categoria = Categoria.DIGITALE, livello = Livello.DICHIARATA
)

// --- fase 1: tempo ----------------------------------------------------------

internal fun testTempo() {
    test("mezzanotte: le 23:59 appartengono al giorno stesso") {
        eq(giorno("2026-03-14"), Tempo.giornoLogico(istanteRoma("2026-03-14T23:59"), ROMA, MEZZANOTTE))
    }
    test("mezzanotte: le 00:00 aprono il giorno nuovo") {
        eq(giorno("2026-03-15"), Tempo.giornoLogico(istanteRoma("2026-03-15T00:00"), ROMA, MEZZANOTTE))
    }
    test("confine 04:00: l'01:30 appartiene ancora a ieri") {
        eq(giorno("2026-03-14"), Tempo.giornoLogico(istanteRoma("2026-03-15T01:30"), ROMA, QUATTRO))
    }
    test("confine 04:00: il confine e' inclusivo") {
        eq(giorno("2026-03-15"), Tempo.giornoLogico(istanteRoma("2026-03-15T04:00"), ROMA, QUATTRO))
    }
    test("confine 04:00: le 03:59 sono ancora ieri") {
        eq(giorno("2026-03-14"), Tempo.giornoLogico(istanteRoma("2026-03-15T03:59"), ROMA, QUATTRO))
    }
    test("stesso istante, fuso diverso: giorno logico diverso") {
        val i = Instant.parse("2026-06-10T23:00:00Z")
        eq(giorno("2026-06-11"), Tempo.giornoLogico(i, ROMA, MEZZANOTTE))
        eq(giorno("2026-06-10"), Tempo.giornoLogico(i, NEWYORK, MEZZANOTTE))
    }
    test("ora legale in avanti: un orario inesistente viene spostato, non perso") {
        // 2026-03-29 Roma: le 02:00 diventano le 03:00
        val i = Tempo.istanteDi(giorno("2026-03-29"), LocalTime.of(2, 30), ROMA, MEZZANOTTE)
        eq(LocalTime.of(3, 30), i.atZone(ROMA).toLocalTime())
    }
    test("ora legale indietro: l'ora ambigua resta nello stesso giorno logico") {
        // 2026-10-25 Roma: le 02:30 esistono due volte
        val prima = Instant.parse("2026-10-25T00:30:00Z")
        val dopo = Instant.parse("2026-10-25T01:30:00Z")
        eq(LocalTime.of(2, 30), prima.atZone(ROMA).toLocalTime())
        eq(LocalTime.of(2, 30), dopo.atZone(ROMA).toLocalTime())
        eq(
            Tempo.giornoLogico(prima, ROMA, MEZZANOTTE),
            Tempo.giornoLogico(dopo, ROMA, MEZZANOTTE)
        )
    }
    test("istanteDi: la notifica del mattino cade sulla data di calendario attesa") {
        val i = Tempo.istanteDi(giorno("2026-05-04"), LocalTime.of(7, 0), ROMA, MEZZANOTTE)
        eq(LocalDate.parse("2026-05-04"), i.atZone(ROMA).toLocalDate())
    }
    test("istanteDi: con confine 04:00 la sera delle 02:00 e' il giorno di calendario dopo") {
        val i = Tempo.istanteDi(giorno("2026-05-04"), LocalTime.of(2, 0), ROMA, QUATTRO)
        eq(LocalDate.parse("2026-05-05"), i.atZone(ROMA).toLocalDate())
    }
    test("andata e ritorno: istanteDi e giornoLogico sono coerenti su un anno intero") {
        val base = giorno("2026-01-01")
        for (d in 0 until 365) {
            for (ora in listOf(LocalTime.of(7, 0), LocalTime.of(21, 30), LocalTime.of(2, 0))) {
                for (confine in listOf(MEZZANOTTE, QUATTRO)) {
                    val g = base + d
                    val i = Tempo.istanteDi(g, ora, ROMA, confine)
                    eq(g, Tempo.giornoLogico(i, ROMA, confine), "giorno $g ora $ora confine $confine")
                }
            }
        }
    }
    test("contesto: sabato e domenica sono festivi") {
        eq(Contesto.FESTIVO, Tempo.contestoDi(giorno("2026-09-19"))) // sabato
        eq(Contesto.FESTIVO, Tempo.contestoDi(giorno("2026-09-20"))) // domenica
        eq(Contesto.FERIALE, Tempo.contestoDi(giorno("2026-09-21"))) // lunedi
    }
}

// --- fase 2: catalogo -------------------------------------------------------

internal fun testCatalogo() {
    test("catalogo valido non produce errori") {
        val r = Catalogo.valida(catalogoFinto(30))
        vero(r.valido, r.errori.toString())
    }
    test("catalogo vuoto e' un errore") {
        vero(!Catalogo.valida(emptyList()).valido)
    }
    test("id duplicato e' un errore") {
        val r = Catalogo.valida(listOf(regolaSemplice, regolaSemplice.copy(testo = "Oggi altro.")))
        vero(r.errori.any { it.descrizione.contains("duplicato") })
    }
    test("regola osservabile senza soglia e' un errore") {
        val r = Catalogo.valida(listOf(regolaSemplice.copy(livello = Livello.OSSERVABILE)))
        vero(r.errori.any { it.descrizione.contains("soglia") })
    }
    test("regola dichiarata con soglia e' un errore") {
        val r = Catalogo.valida(listOf(regolaSemplice.copy(soglia = 10)))
        vero(r.errori.any { it.descrizione.contains("dichiarata") })
    }
    test("intensita fuori scala e' un errore") {
        vero(!Catalogo.valida(listOf(regolaSemplice.copy(intensita = 4))).valido)
    }
    test("stile: testo lungo, emoji e punto esclamativo avvertono ma non bloccano") {
        val r = Catalogo.valida(listOf(regolaSemplice.copy(testo = "Ciao mondo! \uD83D\uDE00")))
        vero(r.valido, "gli avvertimenti non devono bloccare")
        vero(r.avvertimenti.size >= 3, r.avvertimenti.toString())
    }
}

// --- fase 2: selezione ------------------------------------------------------

internal fun testSelezione() {
    val cat = catalogoFinto(120)
    val salt = 987654321L

    test("determinismo: mille chiamate, stessa regola") {
        val g = giorno("2026-04-07")
        val atteso = Selezione.scegli(g, salt, cat, emptyList())
        repeat(1000) { eq(atteso.id, Selezione.scegli(g, salt, cat, emptyList()).id) }
    }
    test("salt diverso: la sequenza cambia") {
        val g = giorno("2026-04-07")
        val a = (0 until 20).map { Selezione.scegli(g + it, 1L, cat, emptyList()).id }
        val b = (0 until 20).map { Selezione.scegli(g + it, 2L, cat, emptyList()).id }
        vero(a != b)
    }
    test("l'ordine del catalogo non influenza la scelta") {
        val g = giorno("2026-04-07")
        val mescolato = cat.shuffled(kotlin.random.Random(7))
        vero(mescolato.map { it.id } != cat.map { it.id }, "il rimescolamento non ha cambiato nulla")
        for (d in 0 until 30) {
            eq(
                Selezione.scegli(g + d, salt, cat, emptyList()).id,
                Selezione.scegli(g + d, salt, mescolato, emptyList()).id,
                "giorno ${g + d}"
            )
        }
    }
    test("distribuzione: 60 giorni senza storico danno almeno 30 regole distinte") {
        val g = giorno("2026-01-01")
        val distinte = (0 until 60).map { Selezione.scegli(g + it, salt, cat, emptyList()).id }.toSet()
        vero(distinte.size >= 30, "solo ${distinte.size} distinte")
    }
    test("catalogo con una sola regola: la restituisce, niente ciclo infinito") {
        val uno = listOf(regolaSemplice)
        val storico = listOf(VoceStorico(giorno("2026-04-06"), "d1", Categoria.DIGITALE, 3))
        eq("d1", Selezione.scegli(giorno("2026-04-07"), salt, uno, storico).id)
    }
    test("catalogo vuoto: eccezione esplicita") {
        try {
            Selezione.scegli(giorno("2026-04-07"), salt, emptyList(), emptyList())
            throw AssertionError("doveva lanciare")
        } catch (_: Selezione.CatalogoVuoto) { }
    }
    test("simulazione 120 giorni: cooldown, categoria e intensita rispettati") {
        var g = giorno("2026-01-05")
        val storico = mutableListOf<VoceStorico>()
        var precedente: VoceStorico? = null
        repeat(120) {
            val r = Selezione.scegli(g, salt, cat, storico)

            if (r.contesto != Contesto.QUALSIASI) {
                eq(Tempo.contestoDi(g), r.contesto, "contesto violato al giorno $g")
            }
            val ultimoUso = storico.filter { it.regolaId == r.id }.maxOfOrNull { it.giornoLogico }
            if (ultimoUso != null) {
                vero(g - ultimoUso >= r.cooldownGiorni, "cooldown violato per ${r.id}")
            }
            precedente?.let { p ->
                vero(r.categoria != p.categoria, "categoria ripetuta al giorno $g")
                vero(!(p.intensita == 3 && r.intensita == 3), "intensita 3 due volte al giorno $g")
            }
            val voce = VoceStorico(g, r.id, r.categoria, r.intensita)
            storico += voce
            precedente = voce
            g += 1
        }
        vero(storico.map { it.regolaId }.toSet().size > 60, "troppa ripetizione complessiva")
    }
    test("quando resta solo il cooldown, il cooldown regge comunque") {
        // Tutte le regole libere sono della categoria di ieri: i vincoli su
        // categoria e intensita' devono cadere, ma quello sul cooldown no.
        // E' l'ultimo gradino del rilassamento, e senza questo caso nessuno lo
        // percorre: il ciclo potrebbe fermarsi un gradino prima e servire
        // proprio la regola che sta riposando.
        val catalogo = listOf(
            Regola("s1", "Oggi telefona a chi avresti scritto.", Categoria.SOCIALE, Livello.DICHIARATA),
            Regola("s2", "Oggi mangia con qualcuno invece che da solo.", Categoria.SOCIALE, Livello.DICHIARATA),
            Regola("f1", "Oggi cammina venti minuti senza destinazione.", Categoria.FISICO, Livello.DICHIARATA)
        )
        val base = giorno("2026-03-02")
        for (d in 0 until 30) {
            val g = base + d
            val storico = listOf(
                VoceStorico(g - 1, "s0", Categoria.SOCIALE, 1),  // ieri era sociale
                VoceStorico(g - 2, "f1", Categoria.FISICO, 1)    // f1 usata l'altro ieri
            )
            val scelta = Selezione.scegli(g, salt, catalogo, storico)
            vero(scelta.id != "f1", "giorno $g: servita una regola ancora in cooldown")
        }
    }

    test("regola feriale mai servita di domenica") {
        val soloFeriali = catalogoFinto(40).map { it.copy(contesto = Contesto.FERIALE) } +
            regolaSemplice.copy(id = "zzz", contesto = Contesto.QUALSIASI)
        val domenica = giorno("2026-09-20")
        eq("zzz", Selezione.scegli(domenica, salt, soloFeriali, emptyList()).id)
    }
}

// --- fase 2: valutazione ----------------------------------------------------

internal fun testValutazione() {
    test("delta: giornata normale") {
        eq(3000L, Valutazione.delta(GiornoMisurato(1, Misurazione(1000, 1_000), Misurazione(4000, 50_000))))
    }
    test("delta: riavvio rilevato dal clock monotono") {
        eq(null, Valutazione.delta(GiornoMisurato(1, Misurazione(1000, 90_000), Misurazione(4000, 5_000))))
    }
    test("delta: contatore azzerato senza calo del monotono") {
        eq(null, Valutazione.delta(GiornoMisurato(1, Misurazione(9000, 1_000), Misurazione(10, 50_000))))
    }
    test("delta: lettura mancante") {
        eq(null, Valutazione.delta(GiornoMisurato(1, Misurazione(1000, 1_000), null)))
    }
    test("baseline: meno di sette campioni non basta") {
        eq(null, Valutazione.baseline(listOf(1000L, 2000L, 3000L, null, 4000L, 5000L)))
    }
    test("baseline: mediana dispari") {
        eq(4000L, Valutazione.baseline(listOf(1000L, 2000L, 3000L, 4000L, 5000L, 6000L, 7000L)))
    }
    test("baseline: mediana pari e' la media dei due centrali") {
        eq(4500L, Valutazione.baseline(List(8) { (it + 1) * 1000L }))
    }
    test("baseline: la finestra scarta i giorni vecchi") {
        val vecchi = List(10) { 100L }
        val recenti = List(14) { 5000L }
        eq(5000L, Valutazione.baseline(vecchi + recenti))
    }
    test("osservabile percentuale: sopra soglia => SI da sensore") {
        val g = GiornoMisurato(1, Misurazione(0, 0), Misurazione(6100, 60_000))
        val e = Valutazione.valuta(regolaPassi(20, Metrica.PASSI_SOPRA_BASELINE_PERCENTUALE), g, 5000)
        eq(Verdetto.SI, e.verdetto); eq(Fonte.SENSORE, e.fonte)
    }
    test("osservabile percentuale: il valore esatto della soglia vale come raggiunto") {
        val g = GiornoMisurato(1, Misurazione(0, 0), Misurazione(6000, 60_000)) // 5000 + 20%
        val e = Valutazione.valuta(regolaPassi(20, Metrica.PASSI_SOPRA_BASELINE_PERCENTUALE), g, 5000)
        eq(Verdetto.SI, e.verdetto)
    }
    test("osservabile percentuale: al pelo sotto soglia => NO") {
        val g = GiornoMisurato(1, Misurazione(0, 0), Misurazione(5999, 60_000))
        val e = Valutazione.valuta(regolaPassi(20, Metrica.PASSI_SOPRA_BASELINE_PERCENTUALE), g, 5000)
        eq(Verdetto.NO, e.verdetto)
    }
    test("osservabile assoluta: soglia in passi") {
        val g = GiornoMisurato(1, Misurazione(0, 0), Misurazione(7000, 60_000))
        val e = Valutazione.valuta(regolaPassi(2000, Metrica.PASSI_SOPRA_BASELINE_ASSOLUTI), g, 5000)
        eq(Verdetto.SI, e.verdetto)
    }
    test("osservabile senza baseline: IGNOTO e si chiede") {
        val g = GiornoMisurato(1, Misurazione(0, 0), Misurazione(9000, 60_000))
        val e = Valutazione.valuta(regolaPassi(20, Metrica.PASSI_SOPRA_BASELINE_PERCENTUALE), g, null)
        eq(Verdetto.IGNOTO, e.verdetto)
        eq(MotivoIgnoto.BASELINE_INSUFFICIENTE, e.motivo)
        vero(e.daChiedere)
    }
    test("osservabile con riavvio: ripiega sulla risposta dell'utente") {
        val g = GiornoMisurato(1, Misurazione(0, 90_000), Misurazione(9000, 1_000))
        val e = Valutazione.valuta(regolaPassi(20, Metrica.PASSI_SOPRA_BASELINE_PERCENTUALE), g, 5000, true)
        eq(Verdetto.SI, e.verdetto); eq(Fonte.UTENTE, e.fonte)
    }
    test("osservabile con sensore assente e nessuna risposta") {
        val e = Valutazione.valuta(
            regolaPassi(20, Metrica.PASSI_SOPRA_BASELINE_PERCENTUALE),
            GiornoMisurato(1), 5000, null
        )
        eq(MotivoIgnoto.SENSORE_ASSENTE, e.motivo); vero(e.daChiedere)
    }
    test("il sensore ha la precedenza sulla risposta dell'utente") {
        val g = GiornoMisurato(1, Misurazione(0, 0), Misurazione(100, 60_000))
        val e = Valutazione.valuta(regolaPassi(20, Metrica.PASSI_SOPRA_BASELINE_PERCENTUALE), g, 5000, true)
        eq(Verdetto.NO, e.verdetto); eq(Fonte.SENSORE, e.fonte)
    }
    test("dichiarata: si, no, silenzio") {
        eq(Verdetto.SI, Valutazione.valuta(regolaSemplice, GiornoMisurato(1), null, true).verdetto)
        eq(Verdetto.NO, Valutazione.valuta(regolaSemplice, GiornoMisurato(1), null, false).verdetto)
        val muto = Valutazione.valuta(regolaSemplice, GiornoMisurato(1), null, null)
        eq(Verdetto.IGNOTO, muto.verdetto)
        eq(MotivoIgnoto.NESSUNA_RISPOSTA, muto.motivo)
        vero(!muto.daChiedere, "non si richiede a chi ha gia' ignorato")
    }
}
