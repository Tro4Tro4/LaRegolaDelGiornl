package it.regoladelgiorno.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import it.regoladelgiorno.core.*
import kotlinx.coroutines.flow.Flow

/**
 * Nessun TypeConverter: gli enum sono salvati come TEXT e convertiti nel mapper.
 * Costa venti righe in piu' e rende lo schema leggibile con qualunque browser
 * SQLite, il che a tempo di migrazione vale molto.
 *
 * Le misurazioni sono nullable e appiattite in colonne: quattro Long invece di
 * un oggetto serializzato, cosi' restano interrogabili.
 */
@Entity(tableName = "giorno")
data class GiornoEntity(
    @PrimaryKey @ColumnInfo(name = "giorno_logico") val giornoLogico: Long,
    @ColumnInfo(name = "regola_id") val regolaId: String,
    val testo: String,
    val categoria: String,
    val livello: String,
    val intensita: Int,
    val metrica: String?,
    val soglia: Int?,
    val verdetto: String,
    val fonte: String,
    val motivo: String?,
    @ColumnInfo(name = "passi_mattina") val passiMattina: Long?,
    @ColumnInfo(name = "realtime_mattina") val realtimeMattina: Long?,
    @ColumnInfo(name = "passi_sera") val passiSera: Long?,
    @ColumnInfo(name = "realtime_sera") val realtimeSera: Long?
)

fun GiornoEntity.aDominio() = GiornoSalvato(
    giornoLogico = giornoLogico,
    regolaId = regolaId,
    testo = testo,
    categoria = Categoria.valueOf(categoria),
    livello = Livello.valueOf(livello),
    intensita = intensita,
    metrica = metrica?.let { Metrica.valueOf(it) },
    soglia = soglia,
    verdetto = Verdetto.valueOf(verdetto),
    fonte = Fonte.valueOf(fonte),
    motivo = motivo?.let { MotivoIgnoto.valueOf(it) },
    mattina = misura(passiMattina, realtimeMattina),
    sera = misura(passiSera, realtimeSera)
)

private fun misura(passi: Long?, realtime: Long?) =
    if (passi != null && realtime != null) Misurazione(passi, realtime) else null

fun GiornoSalvato.aEntita() = GiornoEntity(
    giornoLogico = giornoLogico,
    regolaId = regolaId,
    testo = testo,
    categoria = categoria.name,
    livello = livello.name,
    intensita = intensita,
    metrica = metrica?.name,
    soglia = soglia,
    verdetto = verdetto.name,
    fonte = fonte.name,
    motivo = motivo?.name,
    passiMattina = mattina?.passi,
    realtimeMattina = mattina?.realtimeMs,
    passiSera = sera?.passi,
    realtimeSera = sera?.realtimeMs
)

@Dao
interface GiornoDao {

    @Query("SELECT * FROM giorno WHERE giorno_logico = :giorno")
    suspend fun leggi(giorno: Long): GiornoEntity?

    /** Restituisce in ordine DESC: il chiamante inverte. Cosi' LIMIT prende i piu' recenti. */
    @Query("SELECT * FROM giorno WHERE giorno_logico < :finoA ORDER BY giorno_logico DESC LIMIT :quanti")
    suspend fun precedentiDesc(finoA: Long, quanti: Int): List<GiornoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salva(riga: GiornoEntity)

    @Query("SELECT * FROM giorno WHERE giorno_logico = :giorno")
    fun flussoGiorno(giorno: Long): Flow<GiornoEntity?>

    @Query("SELECT * FROM giorno ORDER BY giorno_logico DESC")
    fun flussoStorico(): Flow<List<GiornoEntity>>
}

/**
 * exportSchema = true e' il motivo per cui alla versione 1 non scrivo un test di
 * migrazione: non c'e' niente da migrare. Lo schema JSON va committato in git
 * ORA, perche' il test da 1 a 2 sia possibile quando servira'.
 */
@Database(entities = [GiornoEntity::class], version = 1, exportSchema = true)
abstract class ArchivioDb : RoomDatabase() {
    abstract fun giorni(): GiornoDao
}

class ArchivioRoom(private val dao: GiornoDao) : ArchivioGiorni {

    override suspend fun leggi(giorno: Long): GiornoSalvato? =
        dao.leggi(giorno)?.aDominio()

    override suspend fun precedenti(finoA: Long, quanti: Int): List<GiornoSalvato> =
        dao.precedentiDesc(finoA, quanti).asReversed().map { it.aDominio() }

    override suspend fun salva(giorno: GiornoSalvato) =
        dao.salva(giorno.aEntita())
}
