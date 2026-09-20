package it.regoladelgiorno.core

enum class Categoria { SOCIALE, FISICO, DIGITALE, CREATIVO, INTROSPETTIVO }

enum class Contesto { QUALSIASI, FERIALE, FESTIVO }

enum class Livello { OSSERVABILE, DICHIARATA }

enum class Metrica {
    /** soglia = punti percentuali sopra la mediana personale */
    PASSI_SOPRA_BASELINE_PERCENTUALE,
    /** soglia = passi in piu' rispetto alla mediana personale */
    PASSI_SOPRA_BASELINE_ASSOLUTI
}

data class Regola(
    val id: String,
    val testo: String,
    val categoria: Categoria,
    val livello: Livello,
    val contesto: Contesto = Contesto.QUALSIASI,
    val intensita: Int = 1,
    val metrica: Metrica? = null,
    val soglia: Int? = null,
    val cooldownGiorni: Int = 60
)

/** Riga minima dello storico necessaria alla selezione. */
data class VoceStorico(
    val giornoLogico: Long,
    val regolaId: String,
    val categoria: Categoria,
    val intensita: Int
)

/** Lettura del contapassi cumulativo, con il clock monotono per rilevare i riavvii. */
data class Misurazione(val passi: Long, val realtimeMs: Long)

data class GiornoMisurato(
    val giornoLogico: Long,
    val mattina: Misurazione? = null,
    val sera: Misurazione? = null
)

enum class Verdetto { SI, NO, IGNOTO }

enum class Fonte { SENSORE, UTENTE, NESSUNA }

enum class MotivoIgnoto {
    SENSORE_ASSENTE,
    RIAVVIO,
    BASELINE_INSUFFICIENTE,
    /** La domanda e' stata posta e l'utente non ha risposto. Ignorare e' una risposta. */
    NESSUNA_RISPOSTA,
    /** Il giorno e' stato archiviato senza che la domanda venisse mai posta:
     *  l'app non stava girando quando sarebbe stato il momento di chiedere. */
    MAI_CHIESTO
}

data class Esito(
    val verdetto: Verdetto,
    val fonte: Fonte,
    val motivo: MotivoIgnoto? = null
) {
    val daChiedere: Boolean get() = verdetto == Verdetto.IGNOTO && motivo !in PORTA_CHIUSA

    private companion object {
        /** Motivi dopo i quali non si chiede piu': il silenzio e' gia' una risposta,
         *  e un giorno archiviato a posteriori non si riapre. */
        val PORTA_CHIUSA = setOf(MotivoIgnoto.NESSUNA_RISPOSTA, MotivoIgnoto.MAI_CHIESTO)
    }
}
