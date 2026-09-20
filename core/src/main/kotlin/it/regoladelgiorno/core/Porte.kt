package it.regoladelgiorno.core

/**
 * Il core non conosce Room, i sensori, i permessi.
 * Il layer Android implementa queste interfacce; i test le sostituiscono con finte.
 */

/** Riga completa dello storico. Il testo e' congelato: se domani riscrivo la regola,
 *  i giorni passati continuano a dire quello che dicevano davvero. */
data class GiornoSalvato(
    val giornoLogico: Long,
    val regolaId: String,
    val testo: String,
    val categoria: Categoria,
    val livello: Livello,
    val intensita: Int,
    val metrica: Metrica? = null,
    val soglia: Int? = null,
    val verdetto: Verdetto = Verdetto.IGNOTO,
    val fonte: Fonte = Fonte.NESSUNA,
    val motivo: MotivoIgnoto? = null,
    val mattina: Misurazione? = null,
    val sera: Misurazione? = null
) {
    val aperto: Boolean get() = verdetto == Verdetto.IGNOTO && fonte == Fonte.NESSUNA && motivo == null
}

interface ArchivioGiorni {
    suspend fun leggi(giorno: Long): GiornoSalvato?
    /** In ordine cronologico crescente, esclusi i giorni successivi a [finoA]. */
    suspend fun precedenti(finoA: Long, quanti: Int): List<GiornoSalvato>
    suspend fun salva(giorno: GiornoSalvato)
}

/** null quando il sensore non c'e', il permesso manca o la lettura fallisce. */
interface Contapassi {
    suspend fun leggi(): Misurazione?
}
