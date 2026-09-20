package it.regoladelgiorno.core

/**
 * Le tre cose che possono succedere in un giorno:
 * il mattino apre, la sera chiude, l'utente eventualmente risponde.
 *
 * Tutte e tre sono idempotenti, perche' su Android verranno invocate piu' volte:
 * l'allarme puo' ripetersi, l'utente puo' aprire l'app in qualsiasi momento,
 * e su alcuni dispositivi l'allarme non arriva affatto.
 */
class Motore(
    private val archivio: ArchivioGiorni,
    private val catalogo: List<Regola>,
    private val salt: Long,
    private val contapassi: Contapassi
) {

    /**
     * Assegna la regola del giorno se non c'e' gia'. Chiamata dall'allarme del
     * mattino E all'apertura dell'app: e' il recupero per i dispositivi che
     * uccidono i processi in background.
     */
    suspend fun apri(giorno: Long): GiornoSalvato {
        archivio.leggi(giorno)?.let { return it }

        val storico = archivio.precedenti(giorno, FINESTRA_STORICO).map {
            VoceStorico(it.giornoLogico, it.regolaId, it.categoria, it.intensita)
        }
        val regola = Selezione.scegli(giorno, salt, catalogo, storico)
        val nuovo = GiornoSalvato(
            giornoLogico = giorno,
            regolaId = regola.id,
            testo = regola.testo,
            categoria = regola.categoria,
            livello = regola.livello,
            intensita = regola.intensita,
            metrica = regola.metrica,
            soglia = regola.soglia,
            mattina = if (regola.livello == Livello.OSSERVABILE) contapassi.leggi() else null
        )
        archivio.salva(nuovo)
        return nuovo
    }

    /**
     * Chiude la giornata. Restituisce null se quel giorno non ha mai avuto una
     * regola: non si valuta una regola che l'utente non ha mai visto, e non si
     * inventa storia a posteriori.
     */
    suspend fun chiudi(giorno: Long): GiornoSalvato? {
        val corrente = archivio.leggi(giorno) ?: return null
        if (!corrente.aperto) return corrente // gia' chiuso: non si rivaluta

        val sera = if (corrente.livello == Livello.OSSERVABILE) contapassi.leggi() else null
        val conMisura = corrente.copy(sera = sera)
        val esito = Valutazione.valuta(regolaDi(conMisura), misureDi(conMisura), baseline(giorno))
        val chiuso = conMisura.copy(
            verdetto = esito.verdetto, fonte = esito.fonte, motivo = esito.motivo
        )
        archivio.salva(chiuso)
        return chiuso
    }

    /**
     * Risposta si/no dalla notifica serale. Non sovrascrive mai un verdetto
     * gia' ottenuto dal sensore.
     */
    suspend fun rispondi(giorno: Long, risposta: Boolean): GiornoSalvato? {
        val corrente = archivio.leggi(giorno) ?: return null
        // Nessuna guardia sul sensore: e' Valutazione a preferirlo sempre alla
        // risposta, quindi ricalcolare da' lo stesso verdetto. La guardia sulla
        // risposta gia' data invece serve, ed e' l'unica.
        if (corrente.fonte == Fonte.UTENTE) return corrente

        val esito = Valutazione.valuta(
            regolaDi(corrente), misureDi(corrente), baseline(giorno), risposta
        )
        val chiuso = corrente.copy(
            verdetto = esito.verdetto, fonte = esito.fonte, motivo = esito.motivo
        )
        archivio.salva(chiuso)
        return chiuso
    }

    /** Mediana personale calcolata solo sui giorni con delta valido. */
    private suspend fun baseline(giorno: Long): Long? {
        val storia = archivio.precedenti(giorno, Valutazione.FINESTRA_BASELINE)
            .map { Valutazione.delta(misureDi(it)) }
        return Valutazione.baseline(storia)
    }

    private fun misureDi(g: GiornoSalvato) = GiornoMisurato(g.giornoLogico, g.mattina, g.sera)

    /** La regola come era il giorno in cui e' stata servita, non come e' nel catalogo oggi. */
    private fun regolaDi(g: GiornoSalvato) = Regola(
        id = g.regolaId, testo = g.testo, categoria = g.categoria,
        livello = g.livello, intensita = g.intensita,
        metrica = g.metrica, soglia = g.soglia
    )

    companion object {
        /** Basta a coprire il cooldown piu' lungo previsto dal catalogo. */
        const val FINESTRA_STORICO = 90
    }
}
