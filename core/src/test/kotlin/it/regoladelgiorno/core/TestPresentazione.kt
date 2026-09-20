package it.regoladelgiorno.core

private fun testoDi(lunghezza: Int) = "O".repeat(lunghezza)

private fun giornoCon(
    g: Long,
    categoria: Categoria,
    verdetto: Verdetto
) = GiornoSalvato(
    giornoLogico = g, regolaId = "x$g", testo = "Oggi qualcosa.",
    categoria = categoria, livello = Livello.DICHIARATA, intensita = 1,
    verdetto = verdetto,
    fonte = if (verdetto == Verdetto.IGNOTO) Fonte.NESSUNA else Fonte.UTENTE,
    motivo = if (verdetto == Verdetto.IGNOTO) MotivoIgnoto.NESSUNA_RISPOSTA else null
)

/** n giorni consecutivi della stessa categoria e dello stesso esito. */
private fun serie(da: Long, quanti: Int, categoria: Categoria, verdetto: Verdetto) =
    (0 until quanti).map { giornoCon(da + it, categoria, verdetto) }

internal fun testTipometria() {

    test("dimensione sempre dentro l'intervallo dichiarato") {
        for (n in 0..300) {
            val d = Tipometria.dimensioneSp(testoDi(n))
            vero(d in Tipometria.MIN_SP..Tipometria.MAX_SP, "lunghezza $n ha dato $d")
        }
    }

    test("piu' testo non puo' mai dare un corpo piu' grande") {
        for (n in 0 until 300) {
            val a = Tipometria.dimensioneSp(testoDi(n))
            val b = Tipometria.dimensioneSp(testoDi(n + 1))
            vero(b <= a, "da $n a ${n + 1}: $a -> $b")
        }
    }

    test("nessuno scalino piu' grande di 2sp") {
        for (n in 0 until 300) {
            val salto = Tipometria.dimensioneSp(testoDi(n)) - Tipometria.dimensioneSp(testoDi(n + 1))
            vero(salto <= 2, "scalino di $salto sp a lunghezza $n")
        }
    }

    test("una regola corta e una lunga non ricevono lo stesso corpo") {
        val corta = Tipometria.dimensioneSp("Oggi telefona a chi avresti scritto.")
        val lunga = Tipometria.dimensioneSp(
            "Oggi fai un pezzo del percorso di sempre da una strada che non hai mai preso."
        )
        vero(corta > lunga, "corta $corta, lunga $lunga")
    }

    test("gli estremi dell'intervallo sono raggiungibili davvero") {
        val tutte = (0..300).map { Tipometria.dimensioneSp(testoDi(it)) }.toSet()
        vero(Tipometria.MAX_SP in tutte, "il corpo massimo non viene mai usato")
        vero(Tipometria.MIN_SP in tutte, "il corpo minimo non viene mai usato")
    }

    test("stesso testo, stessa dimensione") {
        val t = "Oggi ringrazia qualcuno per una cosa di un anno fa."
        eq(Tipometria.dimensioneSp(t), Tipometria.dimensioneSp(t))
    }
}

internal fun testComposizione() {

    test("storico vuoto: nessuna riga") {
        eq(emptyList(), Composizione.componi(emptyList()))
    }

    test("sotto la soglia di giorni risolti non si mostra nulla") {
        val pochi = serie(0, Composizione.MINIMO_RISOLTI - 1, Categoria.FISICO, Verdetto.SI)
        eq(emptyList(), Composizione.componi(pochi))
    }

    test("alla soglia esatta la composizione compare") {
        val esatti = serie(0, Composizione.MINIMO_RISOLTI, Categoria.FISICO, Verdetto.SI)
        eq(1, Composizione.componi(esatti).size)
    }

    test("i giorni non osservati non contano per la soglia") {
        val misti = serie(0, 10, Categoria.FISICO, Verdetto.SI) +
            serie(100, 50, Categoria.SOCIALE, Verdetto.IGNOTO)
        eq(emptyList(), Composizione.componi(misti), "50 ignoti non devono sbloccare la soglia")
    }

    test("le categorie senza dati sono escluse") {
        val righe = Composizione.componi(serie(0, 25, Categoria.CREATIVO, Verdetto.NO))
        eq(1, righe.size)
        eq(Categoria.CREATIVO, righe.first().categoria)
    }

    test("conteggi separati per si, no e non osservati") {
        val dati = serie(0, 12, Categoria.SOCIALE, Verdetto.SI) +
            serie(100, 9, Categoria.SOCIALE, Verdetto.NO) +
            serie(200, 4, Categoria.SOCIALE, Verdetto.IGNOTO)
        val r = Composizione.componi(dati).single()
        eq(12, r.si); eq(9, r.no); eq(4, r.nonOsservati)
        eq(21, r.risolti); eq(25, r.totale)
    }

    test("l'ordine e' quello delle categorie, non una classifica") {
        // Conteggi DIVERSI e crescenti nell'ordine dell'enum: se l'ordinamento
        // dipendesse dai numeri, INTROSPETTIVO verrebbe prima.
        val dati = serie(0, 20, Categoria.INTROSPETTIVO, Verdetto.SI) +
            serie(100, 8, Categoria.SOCIALE, Verdetto.SI)
        val righe = Composizione.componi(dati)
        eq(listOf(Categoria.SOCIALE, Categoria.INTROSPETTIVO), righe.map { it.categoria },
            "l'ordine non deve dipendere dai conteggi")
    }

    test("l'ordine di ingresso non cambia il risultato") {
        val dati = serie(0, 18, Categoria.FISICO, Verdetto.SI) +
            serie(100, 7, Categoria.DIGITALE, Verdetto.NO)
        eq(Composizione.componi(dati), Composizione.componi(dati.reversed()))
    }

    test("i totali coincidono con i giorni in ingresso") {
        val dati = serie(0, 15, Categoria.FISICO, Verdetto.SI) +
            serie(100, 10, Categoria.DIGITALE, Verdetto.NO) +
            serie(200, 7, Categoria.CREATIVO, Verdetto.IGNOTO)
        eq(32, Composizione.componi(dati).sumOf { it.totale })
    }
}

internal fun testSegno() {
    val base = GiornoSalvato(1, "x", "Oggi qualcosa.", Categoria.SOCIALE, Livello.DICHIARATA, 1)

    test("giorno assente: in attesa") { eq(Segno.ATTESA, segnoDi(null)) }
    test("giorno appena aperto: in attesa") { eq(Segno.ATTESA, segnoDi(base)) }
    test("verdetto si") {
        eq(Segno.RISPETTATA, segnoDi(base.copy(verdetto = Verdetto.SI, fonte = Fonte.UTENTE)))
    }
    test("verdetto no") {
        eq(Segno.NON_RISPETTATA, segnoDi(base.copy(verdetto = Verdetto.NO, fonte = Fonte.SENSORE)))
    }
    test("ignoto con motivo: non valutabile, non in attesa") {
        for (m in MotivoIgnoto.entries) {
            eq(Segno.NON_VALUTABILE, segnoDi(base.copy(motivo = m)), "motivo $m")
        }
    }
}
