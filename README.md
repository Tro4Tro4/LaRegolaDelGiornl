# La Regola del Giorno

Una regola stravagante al giorno, osservata senza giudizio.
Tutto locale: nessun account, nessuna rete, nessuna telemetria.

L'assenza del permesso `INTERNET` nel manifest è verificabile da chiunque
scarichi l'APK, ed è l'unica prova credibile che l'app non mandi niente da
nessuna parte. Per lo stesso motivo `allowBackup` è `false`: con il backup
automatico lo storico finirebbe su Google Drive, e la frase che l'app mostra
all'utente sarebbe falsa.

---

## Stato

| | |
|---|---|
| Logica di dominio | 111 test, 33 mutanti uccisi su 33 |
| Catalogo | 150 regole validate, simulate su tre anni d'uso |
| Layer Android | scritto, **mai compilato** |
| Interfaccia Compose | scritta, **mai compilata** |

Il modulo `:core` è stato compilato ed eseguito davvero. Tutto ciò che
dipende dall'SDK Android no: il primo build troverà errori, ed è previsto.

**Migrazione su Claude Code:** leggi `RIPRISTINO.md`.
Contesto completo e storia delle decisioni: `docs/PROGETTO.md`.
Istruzioni permanenti per Claude Code: `CLAUDE.md`.

---

## Struttura

```
core/    Kotlin puro, zero dipendenze Android. Tempo, selezione,
         valutazione, motore, pianificatore. Qui vivono i test.
app/     Tutto ciò che tocca Android: Room, DataStore, allarmi,
         notifiche, sensori, Compose.
strumenti/
  regole.py      unica sorgente del catalogo
  mutazioni.py   mutation testing senza Gradle
docs/
  PROGETTO.md    decisioni, verifiche, cose aperte
```

La separazione non è estetica. `:core` non può dipendere da Android per
costruzione, ed è il motivo per cui 111 test girano in un secondo senza
emulatore. Se qualcuno ci aggiunge una dipendenza Android, il modulo smette
di compilare — effetto voluto.

---

## Primo avvio

Serve JDK 17 e l'SDK Android con le build tools 36.

```bash
gradle wrapper --gradle-version 9.1     # il wrapper non è incluso
./gradlew :core:test                    # gira senza SDK Android
./gradlew :app:assembleDebug
```

### Android Studio o VS Code

Onestamente: **Android Studio**, almeno per il primo build.

VS Code apre il progetto, l'estensione Kotlin dà completamento di base e
i task in `.vscode/tasks.json` lanciano Gradle dal terminale. Ma non ha
le anteprime Compose, non ha il layout inspector, non ha l'AGP Upgrade
Assistant e non gestisce l'SDK. Con AGP 9, che ha cambiato il DSL,
l'assistente di upgrade è esattamente ciò che serve quando qualcosa non
torna.

Una volta che il progetto compila, VS Code è perfettamente utilizzabile
per scriverci dentro.

---

## Cosa manca

- `res/mipmap/ic_launcher` — generalo con Image Asset di Android Studio
- Il wrapper Gradle
- La riga di CI che rigenera il catalogo e fallisce se il diff non è vuoto
- La riga di CI che lancia `strumenti/mutazioni.py`: ora lo script esce con
  codice diverso da zero se un mutante sopravvive **o se non si applica**, e un
  mutante che non si applica è il modo in cui una suite si svuota in silenzio —
  tre lo erano diventati, e nessuno se n'era accorto
- Il glifo `←` di `torna_a_oggi` non è nel font sottoinsiemato: cade sul
  fallback di sistema, con peso e allineamento diversi da Literata

`strumenti/regole.py` produce **due** artefatti che devono restare allineati:
il JSON spedito e lo specchio Kotlin che i test validano. Senza quel
controllo, fra sei mesi i test valideranno un catalogo diverso da quello
in mano agli utenti.

---

## Versioni

Verificate a settembre 2026. AGP 9.0 è uscito a gennaio 2026: supporta al
massimo API 36, richiede Gradle 9.1 e JDK 17, e porta il **supporto Kotlin
integrato** — il plugin `org.jetbrains.kotlin.android` non va applicato e
non è compatibile con il nuovo DSL.

L'unica versione che non ho potuto verificare è il suffisso di KSP, che
cambia a ogni patch di Kotlin. È il primo posto dove guardare se il build
fallisce sulla risoluzione dei plugin.

---

## Note tecniche non ovvie

**Allarmi inesatti.** `setAndAllowWhileIdle` attraversa il Doze e non scatta
mai prima dell'orario. `USE_EXACT_ALARM` è riservata da Google Play a sveglie
e calendari; `SCHEDULE_EXACT_ALARM` è negata di default da Android 14. Per
una notifica del mattino la precisione al secondo non serve.

**`exported="true"` sul receiver di ripristino è obbligatorio.** Un receiver
con `intent-filter` per i broadcast di sistema non viene raggiunto se è
`false`, e il fallimento è silenzioso. Le quattro azioni sono *protected
broadcast*: solo il sistema può inviarle.

**`ACTION_MY_PACKAGE_REPLACED`** cancella gli allarmi come un riavvio. È
quello che si dimentica sempre.

**Rete di sicurezza ogni sei ore.** Xiaomi, Oppo e Huawei terminano i
processi in background con criteri non documentati: l'allarme può non
arrivare. `apri()` è idempotente ed è chiamata anche all'apertura dell'app.

**Ibernazione.** Android sospende le app inattive da mesi, cancellando
allarmi e permessi. Questa app è progettata per non essere aperta, quindi
è il profilo che finisce ibernato. Se rispondere alla notifica serale conti
come interazione è **da misurare su dispositivo**: è il primo test da fare.

**Niente Material.** Solo `compose-foundation` e `compose-ui`, con
`BasicText`. Conseguenza: niente ripple, lo stato premuto è la sottolineatura.

**Il testo della regola è copiato nello storico**, non referenziato per id:
riscrivere una regola non riscrive il passato.

**Literata statica e sottoinsiemata**, 76 KB contro 955 KB. Include la
feature OpenType `smcp`, quindi il maiuscoletto è vero — le stringhe delle
etichette vanno passate in minuscolo.
