# La Regola del Giorno

App Android: una regola stravagante al giorno, osservata senza giudizio.
Tutto locale. Nessun account, nessuna rete, nessuna telemetria.

Contesto completo, storia delle decisioni e verifiche: `docs/PROGETTO.md`.
Non caricarlo salvo che serva: qui sotto c'è ciò che non si deduce dal codice.

Come si tiene vero quello che il progetto dice di sé: `docs/MANUTENZIONE.md`.

## Comandi

```bash
./gradlew :core:test            # 111 test, gira senza SDK Android
./gradlew :app:assembleDebug
python3 strumenti/coerenza.py   # cancello: numeri, invarianti, catalogo, font
python3 strumenti/regole.py     # rigenera il catalogo (due artefatti)
KOTLINC=<percorso> KOTLIN_STDLIB=<percorso> python3 strumenti/mutazioni.py
```

## Metodo di lavoro richiesto

**TDD RED-first, stretto.** Prima il test, verificato che fallisca su
un'asserzione vera e non su un errore di compilazione. Se serve uno stub che
restituisca un valore fisso, scrivilo. Codice di implementazione scritto prima
del test va cancellato e rifatto.

**Cancello dei mutanti prima di chiudere una fase.** Un test verde non basta:
va dimostrato che fallisce rompendo il codice che dovrebbe proteggere. Ha già
trovato tre volte test che passavano misurando la cosa sbagliata.

**Debug in quattro passi**, senza scorciatoie: riproduci e risali alla causa
reale, cerca lo stesso difetto altrove, formula l'ipotesi e verificala, solo
allora correggi. Dopo tre tentativi falliti, fermati e proponi una revisione
architetturale invece di continuare a rattoppare.

**Il cancello prima di ogni commit.** `./gradlew :core:test` e
`python3 strumenti/coerenza.py`, sempre; i mutanti prima di chiudere una fase.
Il cancello verifica che quello che i documenti dichiarano sia ancora vero: è
nato perché per mesi qui c'è stato scritto un numero di mutanti che non
corrispondeva a quelli esistenti, e nessun test poteva accorgersene.

**Chi cambia un comportamento aggiorna il mutante che lo proteggeva**, e chi ne
aggiunge uno aggiunge il mutante che lo rompe. Le altre regole di manutenzione
stanno in `docs/MANUTENZIONE.md`.

**Revisione finale** da revisore esterno, problemi ordinati per gravità.

## Invarianti — non violare

**`:core` non può dipendere da Android.** È il motivo per cui 111 test girano in
un secondo senza emulatore. Niente `Flow`, niente `Context`, niente androidx.
Se serve osservare dati, il posto è `OsservatoreGiorni` nel modulo `app`.

**Niente Material.** Solo `compose-foundation` e `compose-ui`, con `BasicText`.
Conseguenza voluta: niente ripple, lo stato premuto è la sottolineatura.

**Niente permesso `INTERNET`.** La sua assenza nel manifest è l'unica prova
credibile che l'app non mandi niente da nessuna parte.

**`exported="true"` su `RicevitoreRipristino` è obbligatorio.** Sembra un errore
di sicurezza e verrà "corretto": un receiver con `intent-filter` per broadcast
di sistema non viene raggiunto se è `false`, e il fallimento è silenzioso. Le
quattro azioni sono protected broadcast, solo il sistema può inviarle.

**Il permesso delle notifiche si chiede all'avvio.** Da API 33
`POST_NOTIFICATIONS` non è concesso all'installazione, e `notify()` non segnala
nulla quando manca: senza la richiesta l'app resta muta per sempre, in silenzio,
e con lei sparisce il canale su cui è costruita. Si chiede senza preamboli, al
contrario dell'osservazione automatica: quella è una lettura di sensori e va
spiegata prima, questa è l'app stessa.

**`allowBackup="false"`.** Sembra una dimenticanza e verrà "corretto". Con il
backup automatico lo storico finirebbe su Google Drive, e la frase «niente esce
dal dispositivo», che l'app mostra all'utente in due punti, sarebbe falsa. Il
costo accettato è ripartire da zero cambiando telefono.

**Allarmi inesatti.** Mai `USE_EXACT_ALARM`: Google Play la riserva a sveglie e
calendari, e pubblicarla senza qualificarsi fa rifiutare l'app.

**La selezione è deterministica.** Nessun `Random` dentro `:core`: stesso giorno
più stesso salt danno sempre la stessa regola. Serve a rendere la logica
testabile e a impedire di "ritirare i dadi". L'unico sorteggio dell'app è il
salt, estratto una volta sola alla prima apertura in `DepositoImpostazioni`:
è quello che rende diversa la sequenza fra due installazioni, e sta fuori dalla
selezione proprio per non renderla imprevedibile.

**Solo `Tempo.kt` deriva una data da un istante.** Nessun `LocalDate.now()`
altrove. Il confine del giorno è configurabile e tocca tre cose insieme: il
passaggio nello storico, la finestra di misura dei passi, e a quale giorno
appartiene una risposta data dopo mezzanotte.

**Una risposta data non si rivaluta.** `rispondi()` non ricalcola nulla: la
baseline si muove, e un giorno precedente chiuso in ritardo ne fa nascere una che
al momento della domanda non esisteva. Rivalutare significa registrare il «no»
del contapassi a chi ha risposto «sì» a una domanda posta proprio perché il
contapassi non sapeva. Chi ha già deciso decide; se nessuno ha deciso, vale la
risposta.

**`MAI_CHIESTO` non è `NESSUNA_RISPOSTA`.** Il primo dice che l'app non stava
girando quando sarebbe stato il momento di chiedere, il secondo che l'utente ha
scelto di non rispondere. Confonderli attribuisce all'utente un silenzio che non
gli appartiene e falsa la composizione.

**Il testo della regola è copiato nello storico**, non referenziato per id.
Non sostituirlo con una ricerca nel catalogo: riscrivere una regola
riscriverebbe il passato.

**`Modifier.weight` non funziona dentro un contenitore scorrevole.** L'altezza
disponibile è infinita e non c'è spazio residuo da distribuire. Per centrare un
contenuto che deve anche poter scorrere, vedi `SchermataPrincipale.kt`.

## Il catalogo è generato

`strumenti/regole.py` è l'unica sorgente. Produce due artefatti che devono
restare allineati:

- `app/src/main/assets/regole.json` — ciò che viene spedito
- `core/src/test/.../CatalogoReale.kt` — ciò che i test validano

**Non modificarli a mano.** Serve ancora la riga di CI che rigenera e fallisce
se il diff non è vuoto.

Stile delle regole, imposto da `Catalogo.valida()`: iniziano con "Oggi", una
sola azione, imperativo, seconda persona, sotto le 15 parole, niente punti
esclamativi, niente emoji, mai la spiegazione del perché, mai una promessa di
benefici.

## Scelte di prodotto da difendere

Nessuna gamification. Niente punteggi, niente serie consecutive, niente
percentuali, niente classifiche. La composizione per categoria è ordinata per
enum, mai per conteggio: ordinarla per numero la trasformerebbe in una
classifica, e c'è un test che lo impedisce.

"Non osservato" è un esito legittimo, non un buco da colmare insistendo.
Ignorare la notifica serale è una risposta.

Niente rosso e verde: gli stati si distinguono per forma e riempimento, non per
temperatura morale.

Nessun elemento che inviti a restare nell'app più di dieci secondi. La regola
sta tutta nella notifica, e la risposta serale si dà dalla notifica.

## Tipografia

Le etichette usano maiuscoletto OpenType vero (`smcp`), non maiuscolo simulato.
**Passa le stringhe in minuscolo**: `smcp` trasforma le minuscole in capitali
piccole e lascia intatte le maiuscole già presenti. Un `.uppercase()` lo
annullerebbe.

Il font in `res/font` è un'istanza statica sottoinsiemata, 76 KB. Rigenerarla
con `fontTools` se serve un peso diverso.

## Stato della verifica

`:core` è compilato ed eseguito davvero: 111 test, 33 mutanti su 33.

Tieni i mutanti allineati al codice: uno che non si applica non verifica nulla e
non lo dice. Tre lo erano diventati e la cifra è rimasta sbagliata per mesi. Ora
lo script fallisce anche per quello.

Il modulo `app` non è **mai stato compilato**. Il primo build troverà errori.
Candidati più probabili: il suffisso di KSP nel version catalog, la firma di
`clickable` senza `indication`, e il `collectLatest` annidato nel ViewModel, che
riavvia l'osservazione del giorno a ogni cambio di impostazione.

Da misurare su dispositivo, non deducibile: se rispondere alla notifica serale
conta come interazione ai fini dell'ibernazione. L'app è progettata per non
essere aperta, quindi è il profilo che Android sospende dopo qualche mese.
