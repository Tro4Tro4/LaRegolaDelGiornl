package it.regoladelgiorno.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.regoladelgiorno.R

/**
 * Prima si spiega, poi il sistema chiede. "Non ora" chiude senza far comparire
 * nessun dialogo: rifiutare non deve costare un passaggio in piu'.
 */
@Composable
fun SpiegazioneOsservazione(
    annulla: () -> Unit,
    continua: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colori = LocalColori.current
    val tipi = LocalTipi.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colori.fondo)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center
    ) {
        BasicText(
            text = stringResource(R.string.osservazione_spiegazione),
            style = tipi.regola.copy(color = colori.inchiostro, fontSize = 18.sp),
            modifier = Modifier.widthIn(max = 320.dp)
        )

        Row(
            modifier = Modifier.padding(top = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Azione(stringResource(R.string.non_ora), colori.tenue, annulla)
            Azione(stringResource(R.string.continua), colori.inchiostro, continua)
        }
    }
}

@Composable
private fun Azione(
    testo: String,
    colore: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    BasicText(
        text = testo,
        style = LocalTipi.current.voce.copy(color = colore),
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 13.dp)
    )
}
