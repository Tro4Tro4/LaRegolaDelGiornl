package it.regoladelgiorno.core

/**
 * A1 - dimensione del testo della regola.
 * A2 - composizione dello storico per categoria.
 */

object Tipometria {
    const val MIN_SP = 26
    const val MAX_SP = 34

    /**
     * Il conteggio dei caratteri e' un surrogato dell'area occupata, non l'area.
     * E' sufficiente perche' le regole vivono in una banda stretta di lunghezze
     * e il catalogo ne scoraggia le estreme.
     *
     * Scalini di 2sp: sotto si perderebbe l'effetto, sopra si noterebbe il salto
     * fra due giorni consecutivi.
     */
    fun dimensioneSp(testo: String): Int = when (testo.length) {
        in 0..30 -> 34
        in 31..42 -> 32
        in 43..56 -> 30
        in 57..72 -> 28
        else -> MIN_SP
    }
}

data class RigaCategoria(
    val categoria: Categoria,
    val si: Int,
    val no: Int,
    val nonOsservati: Int
) {
    val risolti: Int get() = si + no
    val totale: Int get() = si + no + nonOsservati
}

object Composizione {
    /** Sotto questa soglia di giorni risolti la composizione e' rumore, non informazione. */
    const val MINIMO_RISOLTI = 20

    /**
     * L'ordine e' quello delle categorie, non dei conteggi: ordinare per
     * numero trasformerebbe la composizione in una classifica.
     */
    fun componi(
        giorni: List<GiornoSalvato>,
        minimoRisolti: Int = MINIMO_RISOLTI
    ): List<RigaCategoria> {
        val risolti = giorni.count { it.verdetto != Verdetto.IGNOTO }
        if (risolti < minimoRisolti) return emptyList()

        return Categoria.entries.mapNotNull { categoria ->
            val suoi = giorni.filter { it.categoria == categoria }
            if (suoi.isEmpty()) null else RigaCategoria(
                categoria = categoria,
                si = suoi.count { it.verdetto == Verdetto.SI },
                no = suoi.count { it.verdetto == Verdetto.NO },
                nonOsservati = suoi.count { it.verdetto == Verdetto.IGNOTO }
            )
        }
    }
}

/** I quattro segni della schermata principale e dello storico. */
enum class Segno { ATTESA, RISPETTATA, NON_RISPETTATA, NON_VALUTABILE }

/**
 * Traduzione da esito a segno. Vive nel core, non in Compose, perche' la
 * differenza fra "ancora da vedere" e "non si e' potuto sapere" e' una
 * distinzione di dominio, non una scelta grafica.
 */
fun segnoDi(giorno: GiornoSalvato?): Segno = when {
    giorno == null -> Segno.ATTESA
    giorno.verdetto == Verdetto.SI -> Segno.RISPETTATA
    giorno.verdetto == Verdetto.NO -> Segno.NON_RISPETTATA
    giorno.aperto -> Segno.ATTESA
    else -> Segno.NON_VALUTABILE
}
