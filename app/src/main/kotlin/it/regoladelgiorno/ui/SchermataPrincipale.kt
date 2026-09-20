package it.regoladelgiorno.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.regoladelgiorno.core.GiornoSalvato
import it.regoladelgiorno.core.Tipometria
import it.regoladelgiorno.core.segnoDi

/**
 * Una schermata, tre elementi: la data, il biglietto, il segno.
 * Nessuna barra, nessun titolo, nessun pulsante di navigazione.
 *
 * La struttura non e' ovvia, e il motivo e' questo: il contenuto deve stare
 * centrato quando ci sta, e diventare scorrevole quando non ci sta.
 * Modifier.weight NON e' utilizzabile dentro un contenitore scorrevole, perche'
 * li' l'altezza disponibile e' infinita e non esiste spazio residuo da
 * distribuire. Quindi la colonna esterna non scorre; scorre quella interna,
 * che con heightIn(min = altezza della finestra) riempie il riquadro quando il
 * contenuto e' corto e lo supera quando e' lungo. Arrangement.Center lavora
 * sull'altezza minima, non su quella infinita.
 */
@Composable
fun SchermataPrincipale(
    giorno: GiornoSalvato?,
    dataLeggibile: String,
    apriStorico: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colori = LocalColori.current
    val scorrimento = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colori.fondo)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp)
    ) {
        Data(testo = dataLeggibile, onClick = apriStorico)

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val finestra = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scorrimento)
                    .heightIn(min = finestra)
                    // centratura ottica: piu' spazio sotto che sopra, altrimenti
                    // l'occhio legge il blocco come basso
                    .padding(top = 24.dp, bottom = 72.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Biglietto(testo = giorno?.testo.orEmpty())

                SegnoStato(
                    segno = segnoDi(giorno),
                    modifier = Modifier.padding(start = 20.dp, top = 24.dp)
                )
            }
        }
    }
}

/**
 * Il biglietto lasciato da qualcuno: filo sottile, nessuna ombra, nessun
 * riempimento, e una rotazione appena percepibile. Sopra il grado diventa un
 * effetto, sotto il mezzo grado sparisce.
 */
@Composable
private fun Biglietto(testo: String) {
    val colori = LocalColori.current
    val tipi = LocalTipi.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp)
            .graphicsLayer {
                rotationZ = -0.6f
                transformOrigin = TransformOrigin(0.12f, 0.5f)
            }
            .border(1.dp, colori.filo, RectangleShape)
            // piu' aria a destra: la mano che scrive si ferma prima del bordo
            .padding(start = 24.dp, top = 30.dp, end = 34.dp, bottom = 32.dp)
    ) {
        BasicText(
            text = testo,
            style = tipi.regola.copy(
                color = colori.inchiostro,
                fontSize = Tipometria.dimensioneSp(testo).sp
            )
        )
    }
}

/**
 * L'unico modo per raggiungere lo storico. Il testo resta da 12sp, l'area
 * toccabile arriva a 48dp: clickable viene PRIMA di heightIn e padding, cosi'
 * l'ingrandimento entra nell'area sensibile invece di restarne fuori.
 */
@Composable
private fun Data(testo: String, onClick: () -> Unit) {
    val colori = LocalColori.current
    val tipi = LocalTipi.current
    val interazione = remember { MutableInteractionSource() }
    val premuto by interazione.collectIsPressedAsState()

    BasicText(
        text = testo,
        style = tipi.etichetta.copy(
            color = colori.tenue,
            textDecoration = if (premuto) TextDecoration.Underline else null
        ),
        modifier = Modifier
            .padding(top = 38.dp)
            .clickable(
                interactionSource = interazione,
                // niente ripple: appartiene al linguaggio di Material, che qui
                // non c'e'. Lo stato premuto e' la sottolineatura.
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .heightIn(min = 48.dp)
            .padding(vertical = 18.dp)
    )
}
