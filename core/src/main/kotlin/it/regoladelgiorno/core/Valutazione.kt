package it.regoladelgiorno.core

/**
 * Nessuna soglia assoluta: il confronto e' sempre con la mediana personale.
 * Finche' la mediana non esiste, l'app non osserva e chiede.
 */
object Valutazione {

    const val FINESTRA_BASELINE = 14
    const val MINIMO_CAMPIONI = 7

    /**
     * Passi fatti fra la lettura del mattino e quella della sera.
     * TYPE_STEP_COUNTER e' cumulativo dal boot: se il telefono e' stato
     * riavviato nel mezzo il delta e' spazzatura, e lo diciamo.
     */
    fun delta(g: GiornoMisurato): Long? {
        val m = g.mattina ?: return null
        val s = g.sera ?: return null
        if (s.realtimeMs < m.realtimeMs) return null // riavvio: clock monotono ripartito
        if (s.passi < m.passi) return null           // contatore azzerato
        return s.passi - m.passi
    }

    private fun motivoDelta(g: GiornoMisurato): MotivoIgnoto =
        if (g.mattina == null || g.sera == null) MotivoIgnoto.SENSORE_ASSENTE
        else MotivoIgnoto.RIAVVIO

    /**
     * Mediana dei delta validi nella finestra recente.
     * [storia] va passata in ordine cronologico crescente.
     */
    fun baseline(
        storia: List<Long?>,
        finestra: Int = FINESTRA_BASELINE,
        minimo: Int = MINIMO_CAMPIONI
    ): Long? {
        val campioni = storia.takeLast(finestra).filterNotNull().sorted()
        if (campioni.size < minimo) return null
        val n = campioni.size
        return if (n % 2 == 1) campioni[n / 2]
        else (campioni[n / 2 - 1] + campioni[n / 2]) / 2
    }

    fun valuta(
        regola: Regola,
        giorno: GiornoMisurato,
        baseline: Long?,
        rispostaUtente: Boolean? = null
    ): Esito {
        if (regola.livello == Livello.OSSERVABILE) {
            val d = delta(giorno)
            if (d != null && baseline != null) {
                val superata = when (regola.metrica) {
                    Metrica.PASSI_SOPRA_BASELINE_PERCENTUALE ->
                        d * 100 >= baseline * (100 + (regola.soglia ?: 0))
                    Metrica.PASSI_SOPRA_BASELINE_ASSOLUTI ->
                        d >= baseline + (regola.soglia ?: 0)
                    null -> return dichiarata(rispostaUtente) // catalogo invalido: non inventiamo
                }
                return Esito(if (superata) Verdetto.SI else Verdetto.NO, Fonte.SENSORE)
            }
            // Il sensore non ha risposto: si ripiega sulla domanda, se e' stata data.
            if (rispostaUtente != null) return dichiarata(rispostaUtente)
            val motivo = if (d == null) motivoDelta(giorno) else MotivoIgnoto.BASELINE_INSUFFICIENTE
            return Esito(Verdetto.IGNOTO, Fonte.NESSUNA, motivo)
        }
        return dichiarata(rispostaUtente)
    }

    /**
     * L'esito di una risposta data (o non data) dall'utente. Pubblica perche'
     * e' anche la porta che il Motore usa quando la domanda e' gia' stata posta:
     * a quel punto il sensore ha gia' avuto la sua occasione.
     */
    fun dichiarata(risposta: Boolean?): Esito = when (risposta) {
        true -> Esito(Verdetto.SI, Fonte.UTENTE)
        false -> Esito(Verdetto.NO, Fonte.UTENTE)
        null -> Esito(Verdetto.IGNOTO, Fonte.NESSUNA, MotivoIgnoto.NESSUNA_RISPOSTA)
    }
}
