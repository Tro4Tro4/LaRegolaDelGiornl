package it.regoladelgiorno.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.regoladelgiorno.R
import it.regoladelgiorno.core.RigaCategoria
import it.regoladelgiorno.core.Segno

/**
 * Quali regole ti riescono. Non un andamento: nessun asse del tempo, nessuna
 * percentuale, nessuna serie consecutiva. L'ordine e' quello delle categorie,
 * mai quello dei conteggi, altrimenti diventa una classifica.
 *
 * I tre stati si distinguono per TEXTURE, non per tono: con carta e inchiostro
 * non esiste una seconda dimensione cromatica, e tre grigi diversi sarebbero
 * indistinguibili per chi ha bassa visione.
 */
@Composable
fun GraficoComposizione(righe: List<RigaCategoria>, modifier: Modifier = Modifier) {
    if (righe.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        righe.forEach { riga ->
            RigaComposizione(riga)
        }
    }
}

@Composable
private fun RigaComposizione(riga: RigaCategoria) {
    val colori = LocalColori.current
    val tipi = LocalTipi.current
    val nome = stringResource(nomeCategoria(riga))

    val descrizione = stringResource(
        R.string.composizione_descrizione, nome, riga.si, riga.no, riga.nonOsservati
    )

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = nome,
                style = tipi.etichetta.copy(color = colori.inchiostro)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                // i numeri ripetono la descrizione della barra: vanno tolti
                // dall'albero di accessibilita, non svuotati
                modifier = Modifier.clearAndSetSemantics { }
            ) {
                Conteggio(Segno.RISPETTATA, riga.si)
                Conteggio(Segno.NON_RISPETTATA, riga.no)
                Conteggio(Segno.NON_VALUTABILE, riga.nonOsservati)
            }
        }

        Barra(
            riga = riga,
            colore = colori.inchiostro,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .padding(top = 0.dp)
                .semantics { contentDescription = descrizione }
        )
    }
}

@Composable
private fun Conteggio(segno: Segno, quanti: Int) {
    val colori = LocalColori.current
    val tipi = LocalTipi.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SegnoStato(segno = segno, lato = 9.dp)
        BasicText(text = quanti.toString(), style = tipi.minuta.copy(color = colori.tenue))
    }
}

/**
 * Le larghezze sono proporzionali reali. Un SVG stirato, o un Canvas con
 * proporzioni forzate, deformerebbe l'angolo del tratteggio: a 45 gradi deve
 * restare a 45 gradi su qualunque larghezza di schermo.
 */
@Composable
private fun Barra(riga: RigaCategoria, colore: Color, modifier: Modifier = Modifier) {
    val totale = riga.totale.coerceAtLeast(1)

    Canvas(modifier = modifier) {
        val pieno = size.width * riga.si / totale
        val rigato = size.width * riga.no / totale
        val vuoto = size.width - pieno - rigato

        drawRect(colore, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(pieno, size.height))
        tratteggio(colore, pieno, rigato)
        if (vuoto > 0.5f) {
            val bordo = 1.dp.toPx()
            drawRect(
                color = colore.copy(alpha = colore.alpha * 0.5f),
                topLeft = Offset(pieno + rigato + bordo / 2f, bordo / 2f),
                size = androidx.compose.ui.geometry.Size(vuoto - bordo, size.height - bordo),
                style = Stroke(width = bordo)
            )
        }
    }
}

/** Righe a 45 gradi, ritagliate al segmento: passo e spessore in dp, non in pixel. */
private fun DrawScope.tratteggio(colore: Color, inizio: Float, larghezza: Float) {
    if (larghezza <= 0f) return
    val passo = 4.5.dp.toPx()
    val spessore = 1.5.dp.toPx()
    val h = size.height

    clipRect(left = inizio, top = 0f, right = inizio + larghezza, bottom = h) {
        var x = inizio - h
        while (x < inizio + larghezza + h) {
            drawLine(colore, Offset(x, h), Offset(x + h, 0f), strokeWidth = spessore)
            x += passo
        }
    }
}

private fun nomeCategoria(riga: RigaCategoria): Int = when (riga.categoria) {
    it.regoladelgiorno.core.Categoria.SOCIALE -> R.string.categoria_sociale
    it.regoladelgiorno.core.Categoria.FISICO -> R.string.categoria_fisico
    it.regoladelgiorno.core.Categoria.DIGITALE -> R.string.categoria_digitale
    it.regoladelgiorno.core.Categoria.CREATIVO -> R.string.categoria_creativo
    it.regoladelgiorno.core.Categoria.INTROSPETTIVO -> R.string.categoria_introspettivo
}
