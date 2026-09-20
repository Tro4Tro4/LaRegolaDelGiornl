package it.regoladelgiorno.ui

import android.Manifest
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.regoladelgiorno.android.Dipendenze
import it.regoladelgiorno.android.Notifiche
import it.regoladelgiorno.android.Sveglie
import it.regoladelgiorno.core.GiornoSalvato
import it.regoladelgiorno.core.Tempo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class StatoUi(
    val oggi: GiornoSalvato? = null,
    val storico: List<GiornoSalvato> = emptyList(),
    val orari: OrariVisibili = OrariVisibili(
        LocalTime.of(7, 0), LocalTime.of(21, 30), LocalTime.MIDNIGHT
    ),
    val osservazione: Boolean = false,
    /** Valore iniziale sensato: a 0 la schermata mostrerebbe il 1 gennaio 1970
     *  per la frazione di secondo che precede la lettura delle impostazioni. */
    val giornoLogico: Long = LocalDate.now().toEpochDay()
)

class ModelloRegola(app: Application) : AndroidViewModel(app) {

    private val _stato = MutableStateFlow(StatoUi())
    val stato: StateFlow<StatoUi> = _stato

    init {
        viewModelScope.launch {
            val c = Dipendenze.di(getApplication())

            // apri() e' idempotente: qui serve da recupero, per i dispositivi
            // dove l'allarme del mattino non e' mai arrivato.
            launch {
                c.impostazioni.flusso.collectLatest { imp ->
                    val oggi = Tempo.giornoLogico(
                        Instant.now(), ZoneId.systemDefault(), imp.confineGiorno
                    )
                    c.motore.apri(oggi)
                    _stato.value = _stato.value.copy(
                        orari = OrariVisibili(imp.oraMattino, imp.oraSera, imp.confineGiorno),
                        osservazione = imp.osservazioneAutomatica,
                        giornoLogico = oggi
                    )
                    c.osservatore.giorno(oggi).collectLatest { g ->
                        _stato.value = _stato.value.copy(oggi = g)
                    }
                }
            }
            launch {
                c.osservatore.storico().collectLatest { s ->
                    _stato.value = _stato.value.copy(storico = s)
                }
            }
        }
    }

    fun cambiaOrario(chiave: ChiaveOrario, ora: LocalTime) = viewModelScope.launch {
        val c = Dipendenze.di(getApplication())
        when (chiave) {
            ChiaveOrario.MATTINO -> c.impostazioni.imposta(mattino = ora)
            ChiaveOrario.SERA -> c.impostazioni.imposta(sera = ora)
            ChiaveOrario.CONFINE -> c.impostazioni.imposta(confine = ora)
        }
        // gli allarmi in coda puntano ancora ai vecchi orari
        Sveglie.riprogrammaTutto(getApplication(), c.orari())
    }

    fun impostaOsservazione(attiva: Boolean) = viewModelScope.launch {
        Dipendenze.di(getApplication()).impostazioni.imposta(osservazione = attiva)
    }
}

class SchermataPrincipaleActivity : ComponentActivity() {

    override fun onCreate(salvato: Bundle?) {
        super.onCreate(salvato)
        enableEdgeToEdge()
        setContent { TemaRegola { Applicazione() } }
    }
}

@Composable
private fun Applicazione(modello: ModelloRegola = viewModel()) {
    val stato by modello.stato.collectAsStateWithLifecycle()
    // due schermate: una libreria di navigazione sarebbe piu' pesante
    // del problema che risolve
    var suStorico by rememberSaveable { mutableStateOf(false) }
    var spiegazioneAperta by rememberSaveable { mutableStateOf(false) }

    val richiestaPermesso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concesso -> modello.impostaOsservazione(concesso) }

    // L'app vive nelle notifiche: la regola arriva la mattina e la domanda la sera,
    // senza che aprire l'app sia necessario. Da API 33 il permesso non e' concesso
    // all'installazione, e senza richiederlo l'app resterebbe muta per sempre —
    // in silenzio, perche' notify() non segnala nulla quando il permesso manca.
    //
    // Si chiede all'avvio e senza preamboli, al contrario dell'osservazione
    // automatica: quella e' una lettura di sensori e va spiegata prima, questa e'
    // il canale stesso dell'app. Rifiutarla non rompe niente: la regola resta
    // visibile aprendo l'app, ed e' il sistema a non riproporre piu' la richiesta.
    val contesto = LocalContext.current
    val richiestaNotifiche = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* concesso o no, l'app funziona lo stesso */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Notifiche.permessoNotifiche(contesto)
        ) {
            richiestaNotifiche.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = suStorico || spiegazioneAperta) {
        if (spiegazioneAperta) spiegazioneAperta = false else suStorico = false
    }

    when {
        spiegazioneAperta -> SpiegazioneOsservazione(
            annulla = { spiegazioneAperta = false },
            continua = {
                spiegazioneAperta = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    richiestaPermesso.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                } else {
                    modello.impostaOsservazione(true) // prima di API 29 non serviva
                }
            }
        )

        suStorico -> SchermataStorico(
            giorni = stato.storico,
            orari = stato.orari,
            osservazioneAttiva = stato.osservazione,
            dataBreve = ::dataBreve,
            cambiaOrario = modello::cambiaOrario,
            commutaOsservazione = {
                if (stato.osservazione) modello.impostaOsservazione(false)
                else spiegazioneAperta = true // prima si spiega, poi il sistema chiede
            },
            torna = { suStorico = false }
        )

        else -> SchermataPrincipale(
            giorno = stato.oggi,
            dataLeggibile = dataLeggibile(stato.giornoLogico),
            apriStorico = { suStorico = true }
        )
    }
}

private val LUNGA = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
private val BREVE = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

private fun dataLeggibile(giorno: Long): String =
    LocalDate.ofEpochDay(giorno).format(LUNGA)

private fun dataBreve(giorno: Long): String =
    LocalDate.ofEpochDay(giorno).format(BREVE)
