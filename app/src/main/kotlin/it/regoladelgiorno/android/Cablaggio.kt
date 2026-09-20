package it.regoladelgiorno.android

import android.content.Context
import androidx.room.Room
import it.regoladelgiorno.BuildConfig
import it.regoladelgiorno.core.ArchivioGiorni
import it.regoladelgiorno.core.Catalogo
import it.regoladelgiorno.core.Categoria
import it.regoladelgiorno.core.Contapassi
import it.regoladelgiorno.core.Contesto
import it.regoladelgiorno.core.GiornoSalvato
import it.regoladelgiorno.core.Livello
import it.regoladelgiorno.core.Metrica
import it.regoladelgiorno.core.Misurazione
import it.regoladelgiorno.core.Motore
import it.regoladelgiorno.core.OrariUtente
import it.regoladelgiorno.core.Regola
import it.regoladelgiorno.data.ArchivioDb
import it.regoladelgiorno.data.ArchivioRoom
import it.regoladelgiorno.data.DepositoImpostazioni
import it.regoladelgiorno.data.GiornoDao
import it.regoladelgiorno.data.aDominio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Il catalogo si legge da assets con org.json, che e' dentro Android da
 * sempre: nessuna dipendenza aggiunta per una lettura sola all'avvio.
 */
object CaricatoreCatalogo {

    fun carica(context: Context, nomeFile: String = "regole.json"): List<Regola> {
        val testo = context.assets.open(nomeFile).bufferedReader().use { it.readText() }
        val elenco = JSONObject(testo).getJSONArray("regole")

        val regole = (0 until elenco.length()).map { i ->
            val o = elenco.getJSONObject(i)
            Regola(
                id = o.getString("id"),
                testo = o.getString("testo"),
                categoria = Categoria.valueOf(o.getString("categoria")),
                livello = Livello.valueOf(o.getString("livello")),
                contesto = Contesto.valueOf(o.optString("contesto", Contesto.QUALSIASI.name)),
                intensita = o.optInt("intensita", 1),
                metrica = o.optString("metrica").takeIf { it.isNotEmpty() }?.let(Metrica::valueOf),
                soglia = if (o.has("soglia")) o.getInt("soglia") else null,
                cooldownGiorni = o.optInt("cooldownGiorni", 60)
            )
        }

        // In debug il catalogo rotto deve fermare l'app subito e rumorosamente.
        // In release si spedisce cio' che c'e': meglio una regola strana che
        // un'applicazione che non parte.
        if (BuildConfig.DEBUG) {
            val referto = Catalogo.valida(regole)
            check(referto.valido) {
                "catalogo non valido:\n" + referto.errori.joinToString("\n") {
                    "  ${it.regolaId}: ${it.descrizione}"
                }
            }
            referto.avvertimenti.forEach {
                android.util.Log.w("Catalogo", "${it.regolaId}: ${it.descrizione}")
            }
        }
        return regole
    }
}

/**
 * Il contapassi risponde solo se l'utente ha acceso l'osservazione automatica.
 * Il permesso di sistema da solo non basta: potrebbe essere rimasto concesso
 * da prima che l'opzione venisse spenta.
 */
class ContapassiSeConsentito(
    private val impostazioni: DepositoImpostazioni,
    private val reale: Contapassi
) : Contapassi {
    override suspend fun leggi(): Misurazione? =
        if (impostazioni.flusso.first().osservazioneAutomatica) reale.leggi() else null
}

/**
 * I flussi restano fuori dal core: quello e' Kotlin puro e non conosce Room.
 * Qui si traduce da entita' a dominio per l'interfaccia.
 */
class OsservatoreGiorni(private val dao: GiornoDao) {
    fun giorno(giornoLogico: Long): Flow<GiornoSalvato?> =
        dao.flussoGiorno(giornoLogico).map { it?.aDominio() }

    fun storico(): Flow<List<GiornoSalvato>> =
        dao.flussoStorico().map { righe -> righe.map { it.aDominio() } }
}

/**
 * Composizione manuale: l'app ha cinque oggetti, un framework di dependency
 * injection sarebbe piu' codice di quello che elimina.
 */
class Contenitore(
    val motore: Motore,
    val archivio: ArchivioGiorni,
    val osservatore: OsservatoreGiorni,
    val impostazioni: DepositoImpostazioni
) {
    suspend fun orari(): OrariUtente = impostazioni.flusso.first().let {
        OrariUtente(it.oraMattino, it.oraSera, it.confineGiorno)
    }
}

object Dipendenze {
    private val chiave = Mutex()
    @Volatile private var istanza: Contenitore? = null

    /**
     * Sospesa e non bloccante: il salt va letto da DataStore, e i chiamanti
     * (worker e ViewModel) sono gia' dentro una coroutine. Un runBlocking qui
     * bloccherebbe un thread del pool proprio all'avvio.
     */
    suspend fun di(context: Context): Contenitore {
        istanza?.let { return it }
        return chiave.withLock {
            istanza ?: costruisci(context.applicationContext).also { istanza = it }
        }
    }

    private suspend fun costruisci(app: Context): Contenitore {
        val db = Room.databaseBuilder(app, ArchivioDb::class.java, "regola.db").build()
        val archivio = ArchivioRoom(db.giorni())
        val impostazioni = DepositoImpostazioni(app)
        val contapassi = ContapassiSeConsentito(impostazioni, ContapassiAndroid(app))

        return Contenitore(
            motore = Motore(
                archivio = archivio,
                catalogo = CaricatoreCatalogo.carica(app),
                salt = impostazioni.assicuraSalt(),
                contapassi = contapassi
            ),
            archivio = archivio,
            osservatore = OsservatoreGiorni(db.giorni()),
            impostazioni = impostazioni
        )
    }
}
