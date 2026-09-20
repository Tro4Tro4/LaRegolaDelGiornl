package it.regoladelgiorno.core

// --- finte ------------------------------------------------------------------

private class ArchivioFinto : ArchivioGiorni {
    val righe = sortedMapOf<Long, GiornoSalvato>()
    var scritture = 0
    override suspend fun leggi(giorno: Long) = righe[giorno]
    override suspend fun precedenti(finoA: Long, quanti: Int) =
        righe.headMap(finoA).values.toList().takeLast(quanti)
    override suspend fun salva(giorno: GiornoSalvato) { righe[giorno.giornoLogico] = giorno; scritture++ }
}

private class ContapassiFinto(var valore: Misurazione?) : Contapassi {
    var letture = 0
    override suspend fun leggi(): Misurazione? { letture++; return valore }
}

private val CAT_MOTORE = listOf(
    Regola("f1", "Oggi cammina venti minuti senza destinazione.", Categoria.FISICO,
        Livello.OSSERVABILE, metrica = Metrica.PASSI_SOPRA_BASELINE_ASSOLUTI, soglia = 2000),
    Regola("s1", "Oggi ringrazia qualcuno per una cosa di un anno fa.", Categoria.SOCIALE,
        Livello.DICHIARATA),
    Regola("c1", "Oggi scrivi a mano qualcosa che avresti digitato.", Categoria.CREATIVO,
        Livello.DICHIARATA)
)

private const val G = 20_000L

private fun motore(
    archivio: ArchivioFinto,
    passi: ContapassiFinto = ContapassiFinto(null),
    catalogo: List<Regola> = CAT_MOTORE
) = Motore(archivio, catalogo, salt = 42L, contapassi = passi)

/** Riempie i giorni precedenti con delta costanti, per far esistere la baseline. */
private fun ArchivioFinto.conStoria(fino: Long, giorni: Int, passiAlGiorno: Long) {
    for (i in 1..giorni) {
        val g = fino - i
        righe[g] = GiornoSalvato(
            giornoLogico = g, regolaId = "f1", testo = "x", categoria = Categoria.FISICO,
            livello = Livello.OSSERVABILE, intensita = 1,
            metrica = Metrica.PASSI_SOPRA_BASELINE_ASSOLUTI, soglia = 2000,
            verdetto = Verdetto.NO, fonte = Fonte.SENSORE,
            mattina = Misurazione(0, 1_000),
            sera = Misurazione(passiAlGiorno, 60_000)
        )
    }
}

// --- test -------------------------------------------------------------------

internal fun testMotore() {

    test("apri: assegna una regola e la salva") {
        val a = ArchivioFinto()
        val g = bloccante { motore(a).apri(G) }
        vero(g.aperto)
        eq(1, a.righe.size)
        eq(g.regolaId, a.righe[G]!!.regolaId)
    }

    test("apri: e' idempotente, non riassegna ne' risovrascrive il mattino") {
        val a = ArchivioFinto()
        val p = ContapassiFinto(Misurazione(500, 1_000))
        val m = motore(a, p)
        val primo = bloccante { m.apri(G) }
        p.valore = Misurazione(9999, 2_000)
        val secondo = bloccante { m.apri(G) }
        eq(primo, secondo)
        eq(1, a.scritture)
    }

    test("apri: il testo viene congelato nella riga, non referenziato") {
        val a = ArchivioFinto()
        val g = bloccante { motore(a).apri(G) }
        vero(g.testo.startsWith("Oggi "), g.testo)
        eq(CAT_MOTORE.first { it.id == g.regolaId }.testo, g.testo)
    }

    test("apri: legge il contapassi solo per le regole osservabili") {
        val soloDichiarate = CAT_MOTORE.filter { it.livello == Livello.DICHIARATA }
        val p = ContapassiFinto(Misurazione(100, 1_000))
        bloccante { motore(ArchivioFinto(), p, soloDichiarate).apri(G) }
        eq(0, p.letture)

        val soloOsservabili = CAT_MOTORE.filter { it.livello == Livello.OSSERVABILE }
        val p2 = ContapassiFinto(Misurazione(100, 1_000))
        bloccante { motore(ArchivioFinto(), p2, soloOsservabili).apri(G) }
        eq(1, p2.letture)
    }

    test("chiudi: un giorno mai aperto non viene inventato") {
        val a = ArchivioFinto()
        eq(null, bloccante { motore(a).chiudi(G) })
        eq(0, a.righe.size)
    }

    test("chiudi: regola dichiarata senza risposta resta IGNOTO e si chiede") {
        val a = ArchivioFinto()
        val m = motore(a, catalogo = listOf(CAT_MOTORE[1]))
        bloccante { m.apri(G) }
        val c = bloccante { m.chiudi(G) }!!
        eq(Verdetto.IGNOTO, c.verdetto)
        eq(MotivoIgnoto.NESSUNA_RISPOSTA, c.motivo)
        vero(!c.aperto, "il giorno risulta chiuso")
    }

    test("chiudi: osservabile con baseline e passi sufficienti => SI dal sensore") {
        val a = ArchivioFinto()
        a.conStoria(G, giorni = 10, passiAlGiorno = 5000)
        val p = ContapassiFinto(Misurazione(0, 1_000))
        val m = motore(a, p, catalogo = listOf(CAT_MOTORE[0]))
        bloccante { m.apri(G) }
        p.valore = Misurazione(7500, 60_000) // 7500 >= 5000 + 2000
        val c = bloccante { m.chiudi(G) }!!
        eq(Verdetto.SI, c.verdetto)
        eq(Fonte.SENSORE, c.fonte)
    }

    test("chiudi: osservabile sotto soglia => NO dal sensore") {
        val a = ArchivioFinto()
        a.conStoria(G, giorni = 10, passiAlGiorno = 5000)
        val p = ContapassiFinto(Misurazione(0, 1_000))
        val m = motore(a, p, catalogo = listOf(CAT_MOTORE[0]))
        bloccante { m.apri(G) }
        p.valore = Misurazione(5100, 60_000)
        eq(Verdetto.NO, bloccante { m.chiudi(G) }!!.verdetto)
    }

    test("chiudi: prime due settimane senza baseline => si ripiega sulla domanda") {
        val a = ArchivioFinto()
        a.conStoria(G, giorni = 3, passiAlGiorno = 5000)
        val p = ContapassiFinto(Misurazione(0, 1_000))
        val m = motore(a, p, catalogo = listOf(CAT_MOTORE[0]))
        bloccante { m.apri(G) }
        p.valore = Misurazione(20_000, 60_000)
        val c = bloccante { m.chiudi(G) }!!
        eq(MotivoIgnoto.BASELINE_INSUFFICIENTE, c.motivo)
        vero(Esito(c.verdetto, c.fonte, c.motivo).daChiedere)
    }

    test("chiudi: senza domanda il giorno non risulta ignorato, risulta mai chiesto") {
        // La rete di sicurezza archivia i giorni vecchi rimasti aperti. Registrarli
        // come NESSUNA_RISPOSTA direbbe che l'utente ha ignorato una domanda che
        // nessuno gli ha mai fatto, e falserebbe la composizione.
        val a = ArchivioFinto()
        val m = motore(a, catalogo = listOf(CAT_MOTORE[1]))
        bloccante { m.apri(G) }
        val c = bloccante { m.chiudi(G, conDomanda = false) }!!
        eq(Verdetto.IGNOTO, c.verdetto)
        eq(MotivoIgnoto.MAI_CHIESTO, c.motivo)
        vero(!Esito(c.verdetto, c.fonte, c.motivo).daChiedere, "non si chiede piu' per un giorno archiviato")
        vero(!c.aperto, "il giorno risulta chiuso")
    }

    test("chiudi: con la domanda il silenzio resta silenzio") {
        val a = ArchivioFinto()
        val m = motore(a, catalogo = listOf(CAT_MOTORE[1]))
        bloccante { m.apri(G) }
        eq(MotivoIgnoto.NESSUNA_RISPOSTA, bloccante { m.chiudi(G) }!!.motivo)
    }

    test("chiudi: e' idempotente, non rivaluta un giorno gia' chiuso") {
        val a = ArchivioFinto()
        val m = motore(a, catalogo = listOf(CAT_MOTORE[1]))
        bloccante { m.apri(G) }
        val primo = bloccante { m.chiudi(G) }!!
        val scritture = a.scritture
        eq(primo, bloccante { m.chiudi(G) })
        eq(scritture, a.scritture)
    }

    test("rispondi: si e no arrivano dall'utente") {
        for ((risposta, atteso) in listOf(true to Verdetto.SI, false to Verdetto.NO)) {
            val a = ArchivioFinto()
            val m = motore(a, catalogo = listOf(CAT_MOTORE[1]))
            bloccante { m.apri(G) }
            bloccante { m.chiudi(G) }
            val r = bloccante { m.rispondi(G, risposta) }!!
            eq(atteso, r.verdetto)
            eq(Fonte.UTENTE, r.fonte)
            eq(null, r.motivo)
        }
    }

    test("rispondi: non sovrascrive mai il verdetto del sensore") {
        val a = ArchivioFinto()
        a.conStoria(G, giorni = 10, passiAlGiorno = 5000)
        val p = ContapassiFinto(Misurazione(0, 1_000))
        val m = motore(a, p, catalogo = listOf(CAT_MOTORE[0]))
        bloccante { m.apri(G) }
        p.valore = Misurazione(5100, 60_000)
        bloccante { m.chiudi(G) }
        val r = bloccante { m.rispondi(G, true) }!!
        eq(Verdetto.NO, r.verdetto)
        eq(Fonte.SENSORE, r.fonte)
    }

    test("rispondi: la risposta arriva dopo che la baseline e' nata, e vale lo stesso") {
        // Il giorno viene chiuso quando la baseline non esiste ancora: l'app CHIEDE.
        // Poi i giorni precedenti, rimasti aperti, vengono chiusi a posteriori dalla
        // rete di sicurezza e la baseline nasce. Solo allora l'utente risponde.
        // La domanda e' stata posta: la risposta deve contare, e il sensore non deve
        // piu' entrare in un giorno gia' chiuso.
        val a = ArchivioFinto()
        for (i in 1..7) {
            a.righe[G - i] = GiornoSalvato(
                giornoLogico = G - i, regolaId = "f1", testo = "x",
                categoria = Categoria.FISICO, livello = Livello.OSSERVABILE, intensita = 1,
                metrica = Metrica.PASSI_SOPRA_BASELINE_ASSOLUTI, soglia = 2000,
                mattina = Misurazione(0, 1_000) // aperto: la sera non e' mai stata letta
            )
        }
        val p = ContapassiFinto(Misurazione(0, 1_000))
        val m = motore(a, p, catalogo = listOf(CAT_MOTORE[0]))
        bloccante { m.apri(G) }
        p.valore = Misurazione(1_000, 60_000) // pochi passi: il sensore direbbe NO
        val chiuso = bloccante { m.chiudi(G) }!!
        eq(MotivoIgnoto.BASELINE_INSUFFICIENTE, chiuso.motivo)
        vero(Esito(chiuso.verdetto, chiuso.fonte, chiuso.motivo).daChiedere, "l'app deve aver chiesto")

        for (i in 1..7) {
            a.righe[G - i] = a.righe[G - i]!!.copy(
                sera = Misurazione(5_000, 60_000), verdetto = Verdetto.SI, fonte = Fonte.SENSORE
            )
        }

        val r = bloccante { m.rispondi(G, true) }!!
        eq(Verdetto.SI, r.verdetto)
        eq(Fonte.UTENTE, r.fonte)
        eq(null, r.motivo)
    }

    test("rispondi: la seconda risposta non conta") {
        val a = ArchivioFinto()
        val m = motore(a, catalogo = listOf(CAT_MOTORE[1]))
        bloccante { m.apri(G) }
        bloccante { m.rispondi(G, true) }
        eq(Verdetto.SI, bloccante { m.rispondi(G, false) }!!.verdetto)
    }

    test("rispondi: su un giorno mai aperto non fa nulla") {
        val a = ArchivioFinto()
        eq(null, bloccante { motore(a).rispondi(G, true) })
        eq(0, a.righe.size)
    }

    test("giorni saltati: nessun buco viene riempito a posteriori") {
        val a = ArchivioFinto()
        val m = motore(a)
        bloccante { m.apri(G) }
        bloccante { m.apri(G + 5) } // l'app e' rimasta chiusa per quattro giorni
        eq(listOf(G, G + 5), a.righe.keys.toList())
    }

    test("recupero: il cooldown guarda i giorni salvati, non quelli trascorsi") {
        val a = ArchivioFinto()
        val m = motore(a)
        val primo = bloccante { m.apri(G) }
        val dopo = bloccante { m.apri(G + 1) }
        vero(primo.regolaId != dopo.regolaId, "regola ripetuta il giorno dopo")
        vero(primo.categoria != dopo.categoria, "categoria ripetuta il giorno dopo")
    }
}
