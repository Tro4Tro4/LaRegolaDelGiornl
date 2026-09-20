package it.regoladelgiorno.core

import java.time.LocalDate

/**
 * Il catalogo e' dato, non codice, ma sbagliarlo rompe l'app come un bug.
 * Questi test sono il suo cancello.
 */
internal fun testCatalogoReale() {

    test("il catalogo reale supera la validazione") {
        val r = Catalogo.valida(CATALOGO_REALE)
        vero(r.valido, r.errori.joinToString("\n") { "${it.regolaId}: ${it.descrizione}" })
    }

    test("nessun avvertimento di stile") {
        val a = Catalogo.valida(CATALOGO_REALE).avvertimenti
        vero(a.isEmpty(), a.joinToString("\n") { "${it.regolaId}: ${it.descrizione}" })
    }

    test("centocinquanta regole, trenta per categoria") {
        eq(150, CATALOGO_REALE.size)
        Categoria.entries.forEach { c ->
            eq(30, CATALOGO_REALE.count { it.categoria == c }, "categoria $c")
        }
    }

    test("solo le regole fisiche sono osservabili dal contapassi") {
        val fuoriposto = CATALOGO_REALE
            .filter { it.livello == Livello.OSSERVABILE && it.categoria != Categoria.FISICO }
        eq(emptyList(), fuoriposto.map { it.id })
    }

    test("gli osservabili sono una minoranza dichiarata") {
        val n = CATALOGO_REALE.count { it.livello == Livello.OSSERVABILE }
        vero(n in 15..40, "$n osservabili su 150")
    }

    test("ogni categoria ha abbastanza regole per qualunque giorno della settimana") {
        for (contesto in listOf(Contesto.FERIALE, Contesto.FESTIVO)) {
            Categoria.entries.forEach { c ->
                val n = CATALOGO_REALE.count {
                    it.categoria == c && (it.contesto == Contesto.QUALSIASI || it.contesto == contesto)
                }
                vero(n >= 25, "categoria $c in $contesto ha solo $n regole")
            }
        }
    }

    test("tre anni di uso reale: nessun vincolo violato, nessuna eccezione") {
        var g = LocalDate.parse("2026-01-01").toEpochDay()
        val storico = mutableListOf<VoceStorico>()
        var precedente: VoceStorico? = null

        repeat(1095) {
            val r = Selezione.scegli(g, 20260101L, CATALOGO_REALE, storico.takeLast(90))

            if (r.contesto != Contesto.QUALSIASI) {
                eq(Tempo.contestoDi(g), r.contesto, "contesto violato il giorno $g")
            }
            storico.filter { it.regolaId == r.id }.maxOfOrNull { it.giornoLogico }?.let { ultimo ->
                vero(g - ultimo >= r.cooldownGiorni, "${r.id} ripetuta dopo ${g - ultimo} giorni")
            }
            precedente?.let { p ->
                vero(r.categoria != p.categoria, "categoria ripetuta il giorno $g")
                vero(!(p.intensita == 3 && r.intensita == 3), "due intensita 3 di fila il giorno $g")
            }
            val voce = VoceStorico(g, r.id, r.categoria, r.intensita)
            storico += voce
            precedente = voce
            g += 1
        }

        val usi = storico.groupingBy { it.regolaId }.eachCount()
        eq(150, usi.size, "in tre anni qualche regola non esce mai")
        val media = 1095.0 / 150
        val max = usi.values.max()
        vero(max <= media * 2.2, "una regola esce $max volte contro una media di ${"%.1f".format(media)}")
    }
}
