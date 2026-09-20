package it.regoladelgiorno.core

/**
 * Selezione deterministica: stesso giorno + stesso salt => stessa regola, sempre.
 * Nessuno stato, nessun random, nessun modo di "ritirare i dadi".
 */
object Selezione {

    class CatalogoVuoto : IllegalArgumentException("nessuna regola disponibile")

    /**
     * I vincoli sono applicati in ordine di importanza decrescente e rilassati
     * uno alla volta se non resta nessun candidato. L'unico incomprimibile e'
     * il contesto: una regola feriale di domenica non ha senso.
     */
    fun scegli(
        giorno: Long,
        salt: Long,
        catalogo: List<Regola>,
        storico: List<VoceStorico>
    ): Regola {
        if (catalogo.isEmpty()) throw CatalogoVuoto()

        val contestoOggi = Tempo.contestoDi(giorno)
        val perContesto = catalogo.filter {
            it.contesto == Contesto.QUALSIASI || it.contesto == contestoOggi
        }
        if (perContesto.isEmpty()) throw CatalogoVuoto()

        val ieri = storico.firstOrNull { it.giornoLogico == giorno - 1 }
        val usoRecente: Map<String, Long> = storico
            .filter { it.giornoLogico < giorno }
            .groupBy { it.regolaId }
            .mapValues { (_, v) -> v.maxOf { it.giornoLogico } }

        fun inCooldown(r: Regola): Boolean {
            val ultimo = usoRecente[r.id] ?: return false
            return giorno - ultimo < r.cooldownGiorni
        }

        val livelli: List<(Regola) -> Boolean> = listOf(
            { r -> !inCooldown(r) },
            { r -> ieri == null || r.categoria != ieri.categoria },
            { r -> ieri == null || !(ieri.intensita == 3 && r.intensita == 3) }
        )

        // Tutti i vincoli, poi si molla dal meno importante.
        for (quanti in livelli.size downTo 1) {
            val attivi = livelli.take(quanti)
            val candidati = perContesto.filter { r -> attivi.all { v -> v(r) } }
            if (candidati.isNotEmpty()) return estrai(candidati, giorno, salt)
        }

        return estrai(perContesto, giorno, salt) // nessun vincolo soddisfacibile
    }

    private fun estrai(candidati: List<Regola>, giorno: Long, salt: Long): Regola {
        val ordinati = candidati.sortedBy { it.id } // l'ordine del catalogo non deve contare
        val indice = Math.floorMod(mescola(giorno, salt), ordinati.size.toLong()).toInt()
        return ordinati[indice]
    }

    /** splitmix64: giorni consecutivi devono dare indici scorrelati. */
    internal fun mescola(giorno: Long, salt: Long): Long {
        var z = giorno * -0x61c8864680b583ebL + salt
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
}
