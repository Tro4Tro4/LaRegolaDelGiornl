package it.regoladelgiorno.core

/**
 * Il catalogo vive in assets/regole.json, non in Room: e' versionato con l'app,
 * ispezionabile in git, e non richiede migrazioni quando cambia.
 * Questo file valida la struttura; il parsing JSON sta fuori dal core.
 */
object Catalogo {

    const val MAX_PAROLE_CONSIGLIATO = 15
    private const val APERTURA = "Oggi "

    data class Problema(val regolaId: String, val descrizione: String)

    data class Referto(
        val errori: List<Problema>,
        val avvertimenti: List<Problema>
    ) {
        val valido: Boolean get() = errori.isEmpty()
    }

    fun valida(regole: List<Regola>): Referto {
        val errori = mutableListOf<Problema>()
        val avvertimenti = mutableListOf<Problema>()

        if (regole.isEmpty()) {
            errori += Problema("-", "catalogo vuoto")
            return Referto(errori, avvertimenti)
        }

        regole.groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
            .sorted()
            .forEach { errori += Problema(it, "id duplicato") }

        for (r in regole) {
            if (r.id.isBlank()) errori += Problema("(vuoto)", "id vuoto")
            if (r.testo.isBlank()) errori += Problema(r.id, "testo vuoto")
            if (r.intensita !in 1..3) errori += Problema(r.id, "intensita fuori da 1..3: ${r.intensita}")
            if (r.cooldownGiorni < 1) errori += Problema(r.id, "cooldown non positivo")

            when (r.livello) {
                Livello.OSSERVABILE -> {
                    if (r.metrica == null) errori += Problema(r.id, "osservabile senza metrica")
                    if (r.soglia == null) errori += Problema(r.id, "osservabile senza soglia")
                    else if (r.soglia <= 0) errori += Problema(r.id, "soglia non positiva")
                }
                Livello.DICHIARATA -> {
                    if (r.metrica != null || r.soglia != null) {
                        errori += Problema(r.id, "dichiarata ma con metrica o soglia")
                    }
                }
            }

            // Stile: non blocca la build, ma deve essere rumoroso in debug.
            if (r.testo.isNotBlank() && !r.testo.startsWith(APERTURA)) {
                avvertimenti += Problema(r.id, "non inizia con \"Oggi \"")
            }
            if (r.testo.contains('!')) {
                avvertimenti += Problema(r.id, "punto esclamativo")
            }
            if (r.testo.any { it.code > 0x2100 || Character.isSurrogate(it) }) {
                avvertimenti += Problema(r.id, "simbolo o emoji nel testo")
            }
            val parole = r.testo.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            if (parole > MAX_PAROLE_CONSIGLIATO) {
                avvertimenti += Problema(r.id, "$parole parole (consigliate max $MAX_PAROLE_CONSIGLIATO)")
            }
        }

        return Referto(errori, avvertimenti)
    }
}
