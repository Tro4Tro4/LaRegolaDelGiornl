package it.regoladelgiorno.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.regoladelgiorno.R
import it.regoladelgiorno.core.Segno

/**
 * Disegnati, non scritti. Un glifo tipografico cambierebbe peso, allineamento
 * e posizione della linea di base al variare della famiglia o del fallback,
 * e il segno deve restare identico a se stesso su qualunque dispositivo.
 *
 * La dimensione NON scala con la dimensione dei caratteri di sistema: e' un
 * indicatore, non testo, e crescerebbe fuori proporzione.
 */
@Composable
fun SegnoStato(
    segno: Segno,
    modifier: Modifier = Modifier,
    lato: Dp = 13.dp
) {
    val colore = LocalColori.current.inchiostro
    val descrizione = stringResource(
        when (segno) {
            Segno.ATTESA -> R.string.segno_attesa
            Segno.RISPETTATA -> R.string.segno_rispettata
            Segno.NON_RISPETTATA -> R.string.segno_non_rispettata
            Segno.NON_VALUTABILE -> R.string.segno_non_valutabile
        }
    )

    Canvas(
        modifier = modifier
            .size(lato)
            .semantics { contentDescription = descrizione }
    ) {
        val spessore = size.minDimension * 0.095f
        val centro = Offset(size.width / 2f, size.height / 2f)
        val raggio = size.minDimension / 2f - spessore / 2f
        val contorno = Stroke(width = spessore)

        when (segno) {
            Segno.ATTESA ->
                drawCircle(colore, radius = raggio, center = centro, style = contorno)

            Segno.RISPETTATA ->
                drawCircle(colore, radius = raggio, center = centro)

            Segno.NON_RISPETTATA -> {
                drawCircle(colore, radius = raggio, center = centro, style = contorno)
                val braccio = raggio * 0.72f
                drawLine(
                    color = colore,
                    start = Offset(centro.x - braccio, centro.y + braccio),
                    end = Offset(centro.x + braccio, centro.y - braccio),
                    strokeWidth = spessore
                )
            }

            Segno.NON_VALUTABILE ->
                drawLine(
                    color = colore,
                    start = Offset(spessore / 2f, centro.y),
                    end = Offset(size.width - spessore / 2f, centro.y),
                    strokeWidth = spessore
                )
        }
    }
}
