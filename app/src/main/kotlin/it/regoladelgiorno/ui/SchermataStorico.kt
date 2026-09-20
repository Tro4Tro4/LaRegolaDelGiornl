package it.regoladelgiorno.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import it.regoladelgiorno.R
import it.regoladelgiorno.core.Composizione
import it.regoladelgiorno.core.GiornoSalvato
import it.regoladelgiorno.core.segnoDi
import java.time.LocalTime

/**
 * Una sola schermata con tre livelli di stacco, non tre sezioni uguali:
 * la lista e' il contenuto, la composizione un approfondimento separato solo
 * da spazio, le impostazioni manutenzione, con la loro linea.
 *
 * Column con verticalScroll invece di LazyColumn: trenta righe non giustificano
 * il costo di un elenco pigro, e su hardware datato la composizione anticipata
 * di tutto in un colpo e' piu' rapida del riciclo.
 */
@Composable
fun SchermataStorico(
    giorni: List<GiornoSalvato>,
    orari: OrariVisibili,
    osservazioneAttiva: Boolean,
    dataBreve: (Long) -> String,
    cambiaOrario: (ChiaveOrario, LocalTime) -> Unit,
    commutaOsservazione: () -> Unit,
    torna: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colori = LocalColori.current
    val tipi = LocalTipi.current
    val composizione = remember(giorni) { Composizione.componi(giorni) }
    val visibili = remember(giorni) { giorni.take(GIORNI_VISIBILI) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colori.fondo)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp)
    ) {
        Etichetta(
            testo = stringResource(R.string.torna_a_oggi),
            onClick = torna,
            modifier = Modifier.padding(top = 8.dp)
        )

        Titolo(stringResource(R.string.storico_titolo), Modifier.padding(top = 22.dp))

        visibili.forEachIndexed { indice, giorno ->
            RigaGiorno(giorno, dataBreve(giorno.giornoLogico), primaRiga = indice == 0)
        }

        BasicText(
            text = stringResource(R.string.storico_coda),
            style = tipi.minuta.copy(color = colori.tenue),
            modifier = Modifier.padding(top = 20.dp)
        )

        if (composizione.isNotEmpty()) {
            Titolo(stringResource(R.string.composizione_titolo), Modifier.padding(top = 52.dp))
            GraficoComposizione(composizione)
        }

        Impostazioni(
            orari = orari,
            osservazioneAttiva = osservazioneAttiva,
            cambiaOrario = cambiaOrario,
            commutaOsservazione = commutaOsservazione,
            modifier = Modifier.padding(top = 64.dp)
        )
    }
}

private const val GIORNI_VISIBILI = 30

@Composable
private fun RigaGiorno(giorno: GiornoSalvato, data: String, primaRiga: Boolean) {
    val colori = LocalColori.current
    val tipi = LocalTipi.current

    Column {
        if (!primaRiga) Filo()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
            verticalAlignment = Alignment.Top
        ) {
            BasicText(
                text = data,
                style = tipi.minuta.copy(color = colori.tenue),
                modifier = Modifier.width(44.dp)
            )
            BasicText(
                text = giorno.testo,
                style = tipi.voce.copy(color = colori.inchiostro),
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            SegnoStato(segno = segnoDi(giorno), lato = 11.dp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// --- impostazioni ------------------------------------------------------------

enum class ChiaveOrario { MATTINO, SERA, CONFINE }

data class OrariVisibili(val mattino: LocalTime, val sera: LocalTime, val confine: LocalTime)

@Composable
private fun Impostazioni(
    orari: OrariVisibili,
    osservazioneAttiva: Boolean,
    cambiaOrario: (ChiaveOrario, LocalTime) -> Unit,
    commutaOsservazione: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colori = LocalColori.current
    val tipi = LocalTipi.current
    val contesto = LocalContext.current
    val ore24 = remember { DateFormat.is24HourFormat(contesto) }

    fun apriSelettore(chiave: ChiaveOrario, attuale: LocalTime) {
        // Selettore di sistema: nessuna dipendenza aggiunta, rispetta lingua,
        // formato orario e screen reader del dispositivo. E' un'impostazione
        // che si tocca due volte nella vita dell'app: un controllo su misura
        // sarebbe peggio di quello che sostituisce.
        TimePickerDialog(
            contesto,
            { _, ora, minuto -> cambiaOrario(chiave, LocalTime.of(ora, minuto)) },
            attuale.hour, attuale.minute, ore24
        ).show()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Filo()
        Titolo(stringResource(R.string.impostazioni_titolo), Modifier.padding(top = 22.dp))

        Opzione(stringResource(R.string.imp_mattino), formatta(orari.mattino, ore24)) {
            apriSelettore(ChiaveOrario.MATTINO, orari.mattino)
        }
        Opzione(stringResource(R.string.imp_sera), formatta(orari.sera, ore24)) {
            apriSelettore(ChiaveOrario.SERA, orari.sera)
        }
        Opzione(stringResource(R.string.imp_confine), formatta(orari.confine, ore24)) {
            apriSelettore(ChiaveOrario.CONFINE, orari.confine)
        }
        Opzione(
            stringResource(R.string.imp_osservazione),
            stringResource(if (osservazioneAttiva) R.string.attiva else R.string.non_attiva),
            commutaOsservazione
        )

        BasicText(
            text = stringResource(R.string.imp_nota),
            style = tipi.minuta.copy(color = colori.tenue),
            modifier = Modifier.padding(top = 22.dp)
        )
    }
}

private fun formatta(ora: LocalTime, ore24: Boolean): String =
    if (ore24) "%02d:%02d".format(ora.hour, ora.minute)
    else {
        val h = if (ora.hour % 12 == 0) 12 else ora.hour % 12
        "%d:%02d %s".format(h, ora.minute, if (ora.hour < 12) "AM" else "PM")
    }

@Composable
private fun Opzione(nome: String, valore: String, onClick: () -> Unit) {
    val colori = LocalColori.current
    val tipi = LocalTipi.current
    val interazione = remember { MutableInteractionSource() }
    val premuto by interazione.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interazione,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .heightIn(min = 48.dp)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(text = nome, style = tipi.voce.copy(color = colori.inchiostro))
        Spacer(Modifier.width(12.dp))
        BasicText(
            text = valore,
            style = tipi.voce.copy(
                color = colori.tenue,
                textDecoration = if (premuto) TextDecoration.Underline else null
            )
        )
    }
}

// --- elementi condivisi ------------------------------------------------------

@Composable
private fun Titolo(testo: String, modifier: Modifier = Modifier) {
    val colori = LocalColori.current
    BasicText(
        text = testo,
        style = LocalTipi.current.etichetta.copy(color = colori.tenue),
        modifier = modifier.padding(bottom = 20.dp)
    )
}

@Composable
private fun Filo() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalColori.current.filo)
    )
}

@Composable
private fun Etichetta(testo: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colori = LocalColori.current
    val interazione = remember { MutableInteractionSource() }
    val premuto by interazione.collectIsPressedAsState()

    BasicText(
        text = testo,
        style = LocalTipi.current.etichetta.copy(
            color = colori.tenue,
            textDecoration = if (premuto) TextDecoration.Underline else null
        ),
        modifier = modifier
            .clickable(
                interactionSource = interazione,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .heightIn(min = 48.dp)
            .padding(vertical = 18.dp)
    )
}
