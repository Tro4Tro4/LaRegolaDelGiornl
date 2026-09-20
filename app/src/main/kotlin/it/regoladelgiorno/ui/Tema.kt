package it.regoladelgiorno.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import it.regoladelgiorno.R

/**
 * Nessun Material. L'app usa solo compose-foundation e compose-ui: niente
 * MaterialTheme, niente Scaffold, niente BasicText avvolto in Text.
 * Material3 pesa qualche centinaio di kilobyte e porterebbe un linguaggio
 * visivo che qui andrebbe comunque disattivato riga per riga.
 */

@Immutable
data class Colori(
    val fondo: Color,
    val inchiostro: Color,
    val tenue: Color,
    val filo: Color
)

/** Carta e inchiostro puri. Contrasto misurato: 21:1 il testo, 5,74:1 il tenue. */
val CARTA = Colori(
    fondo = Color(0xFFFFFFFF),
    inchiostro = Color(0xFF000000),
    tenue = Color(0xFF000000).copy(alpha = 0.60f),
    filo = Color(0xFF000000).copy(alpha = 0.18f)
)

/**
 * Il fondo scuro non e' nero assoluto: su AMOLED datati il nero pieno produce
 * uno smearing visibile nello scorrimento. Contrasto: 19,8:1 e 6,44:1.
 */
val INCHIOSTRO = Colori(
    fondo = Color(0xFF0A0A0A),
    inchiostro = Color(0xFFFFFFFF),
    tenue = Color(0xFFFFFFFF).copy(alpha = 0.56f),
    filo = Color(0xFFFFFFFF).copy(alpha = 0.24f)
)

/**
 * Literata e' incorporata come risorsa, non scaricata da Google Fonts:
 * il provider scaricabile richiede i Play Services, che su molti dispositivi
 * datati o dismessi non ci sono.
 *
 * Il file in res/font e' un'istanza STATICA (peso 400, dimensione ottica 16)
 * sottoinsiemata al latino: 76 KB contro i 955 KB del font variabile completo.
 */
val Literata = FontFamily(Font(R.font.literata_regular, FontWeight.Normal))

private val base = TextStyle(fontFamily = Literata, fontWeight = FontWeight.Normal)

@Immutable
data class Tipi(
    /** La dimensione viene decisa a runtime da Tipometria.dimensioneSp. */
    val regola: TextStyle,
    val etichetta: TextStyle,
    val voce: TextStyle,
    val minuta: TextStyle
)

/**
 * Maiuscoletto VERO, non maiuscolo simulato: la feature OpenType "smcp"
 * sopravvive al sottoinsieme (verificato sul file in res/font, 76 KB, statico).
 * Le stringhe vanno quindi passate in minuscolo: smcp trasforma le minuscole
 * in capitali piccole e lascia intatte le maiuscole gia' presenti.
 */
val TIPI = Tipi(
    regola = base.copy(lineHeight = 1.35.em, letterSpacing = (-0.005).em),
    etichetta = base.copy(
        fontSize = 12.sp, lineHeight = 1.3.em, letterSpacing = 0.14.em,
        fontFeatureSettings = "smcp"
    ),
    voce = base.copy(fontSize = 15.sp, lineHeight = 1.4.em),
    minuta = base.copy(fontSize = 12.sp, lineHeight = 1.55.em)
)

val LocalColori = staticCompositionLocalOf { CARTA }
val LocalTipi = staticCompositionLocalOf { TIPI }

@Composable
fun TemaRegola(
    scuro: Boolean = isSystemInDarkTheme(),
    contenuto: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalColori provides if (scuro) INCHIOSTRO else CARTA,
        LocalTipi provides TIPI,
        contenuto = contenuto
    )
}
