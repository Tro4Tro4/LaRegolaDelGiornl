# La Regola del Giorno — documento di progetto

Documento completo: concept, decisioni e perché, verifiche eseguite, cose
ancora aperte. Non è caricato automaticamente da Claude Code — `CLAUDE.md`
contiene la versione operativa.

---

## 1. Concept

Ogni mattina l'app genera una singola regola stravagante per la giornata. Non
un consiglio da oroscopo: uno spintone concreto fuori dalla routine. La sera
verifica, quando può con i sensori, se è stata rispettata, e lo comunica come
osservazione, mai come rimprovero.

Il registro emotivo è quello di **un biglietto lasciato da qualcuno**. Non un
algoritmo che ti misura: un messaggio che qualcuno ti ha posato lì. Questo
spiega la tipografia serif, la rotazione impercettibile del riquadro, e il
fatto che il segno di stato stia fuori dal biglietto — l'osservazione è
dell'app, il messaggio no.

### Valori vincolanti

Privacy totale: tutto locale, nessun account, nessuna rete. Nessuna
gamification. Permessi minimi, spiegati prima di essere chiesti, e rifiutabili
senza rompere niente. Leggerezza su hardware datato.

---

## 2. Decisioni di prodotto

### Tre livelli di verificabilità

La verifica automatica è molto più debole di quanto suoni. Il contapassi sa
quanti passi hai fatto, non sa che li hai fatti "in più" e "senza motivo". Se
prometti verifica e consegni un'euristica, l'app diventa il giudice che dice di
non voler essere.

- **Osservabile** — solo regole di movimento, confrontate con la *mediana
  personale* degli ultimi 14 giorni, mai con una soglia assoluta. Sono 20 su
  150, il 13%.
- **Dichiarata** — la maggioranza. Una domanda sì/no la sera, rispondibile
  dalla notifica, senza aprire l'app.
- **Non osservato** — esito di prima classe. Ignorare la notifica è una
  risposta valida.

Conseguenza accettata: l'osservazione automatica si attiva solo dopo due
settimane d'uso, perché prima la mediana personale non esiste.

### Il tempo schermo è stato eliminato

Serviva `PACKAGE_USAGE_STATS`, un accesso speciale che si concede dalle
impostazioni di sistema e che espone *tutta* la cronologia d'uso del telefono,
per un segnale debole su una manciata di regole. È il tipo di scambio che
quest'app deve rifiutare. Le regole digitali sono diventate dichiarate.

### Lo storico si ferma a trenta giorni

È l'unico punto che potrebbe diventare un feed. I giorni precedenti restano nel
database ma non si scorrono. La frase in coda lo dichiara come scelta, non come
limite subito.

### Il grafico: composizione, non andamento

Richiesta iniziale: un grafico dell'andamento. Un grafico che mostra una
**direzione** invita a migliorare la direzione, ed è un punteggio con un'altra
forma. Un grafico che mostra **composizione** invita a osservare.

Scelta finale: quali categorie di regole ti riescono, senza asse del tempo.
Ordinato per enum, mai per conteggio — ordinarlo per numero sarebbe una
classifica delle categorie. Tre stati distinti per texture (pieno, tratteggio,
contorno), non per tono: con carta e inchiostro non esiste una seconda
dimensione cromatica, e tre grigi sarebbero indistinguibili a chi ha bassa
visione.

### Le impostazioni non hanno un'icona

Stanno in fondo allo storico. Chi le cerca le trova in due gesti, chi non le
cerca non sa che esistono. Un ingranaggio in alto a destra è il primo invito a
esplorare.

Gli orari si scelgono col `TimePickerDialog` di sistema: nessuna dipendenza
aggiunta, rispetta lingua e formato 12/24 ore, funziona con gli screen reader.
È un'impostazione che si tocca due volte nella vita dell'app — un controllo su
misura sarebbe peggio di quello che sostituisce.

### Il permesso si spiega prima di chiederlo

Accendere l'osservazione automatica mostra prima una frase dell'app: il
contapassi letto due volte al giorno, niente altro, niente che esce dal
dispositivo. Solo dopo "Continua" arriva la richiesta di sistema. "Non ora"
chiude senza far comparire nessun dialogo.

---

## 3. Vincoli tecnici verificati

Ognuno di questi ha cambiato il progetto.

**Compose richiede minSdk 23.** Dalla BOM 2025.12.00 l'API minima è 23, e il
minSdk di default di AndroidX è 23. Il brief chiedeva API 21, che avrebbe
costretto a Views XML. API 21–22 vale frazioni di punto percentuale; API 23
introduce anche i permessi a runtime, quindi c'è un solo modello di permessi da
gestire invece di due.

**La sveglia esatta è un problema di policy.** `USE_EXACT_ALARM` è riservata ad
app la cui funzionalità core richiede timing preciso, e Play non consente di
pubblicare con quella permission senza qualificarsi. `SCHEDULE_EXACT_ALARM` è
negata di default da Android 14. Soluzione: `setAndAllowWhileIdle`, inesatto,
attraversa il Doze, non scatta mai prima dell'orario, consegnato entro circa
un'ora. Per "oggi non guardare l'ora fino a mezzogiorno" la precisione al
secondo è irrilevante.

**Play richiede targetSdk 36** dal 31 agosto 2026 per nuove app e
aggiornamenti.

**AGP 9.0** (gennaio 2026) supporta al massimo API 36, richiede Gradle 9.1 e
JDK 17, e ha il **supporto Kotlin integrato**: il plugin
`org.jetbrains.kotlin.android` non va applicato e non è compatibile col nuovo
DSL.

**`exported="false"` su un receiver di broadcast di sistema lo rende
irraggiungibile**, in silenzio. Esiste un bug report di notifiche che smettono
di riprogrammarsi dopo il riavvio esattamente per questo, e AOSP stessa usa
`true`.

**`ACTION_MY_PACKAGE_REPLACED`** cancella gli allarmi pendenti come un riavvio.

**Lovable non serve**: genera applicazioni web React, non binari Android
nativi, e il suo output non può diventare nativo senza riscrittura.

---

## 4. Architettura

Due moduli. `:core` è Kotlin puro e non può dipendere da Android: è
architetturale, non stilistico, ed è il motivo per cui la suite gira in un
secondo. `app` contiene tutto ciò che tocca il sistema.

Room, DataStore, sensori e allarmi stanno dietro interfacce definite nel core
(`ArchivioGiorni`, `Contapassi`). I `Flow` restano fuori: `OsservatoreGiorni`
nel modulo `app` traduce da entità a dominio per l'interfaccia.

### Il giorno logico

`Tempo.kt` è l'unico posto autorizzato a derivare una data da un istante. Il
confine del giorno è configurabile e tocca tre cose insieme: quando il giorno
passa nello storico, la finestra di misura dei passi, e a quale giorno
appartiene una risposta data all'una di notte.

### Le tre operazioni

`apri`, `chiudi`, `rispondi` — tutte idempotenti, perché su Android verranno
invocate più volte. `apri` è anche il recupero: viene chiamata dall'allarme del
mattino **e** all'apertura dell'app, così se la notifica è saltata la regola
compare comunque.

Un giorno mai aperto non viene chiuso: non si valuta una regola che l'utente
non ha mai visto, e non si inventa storia a posteriori. I buchi restano buchi.

### Difese contro i produttori

Xiaomi, Oppo e Huawei terminano i processi in background con criteri non
documentati. Sopra gli allarmi c'è una rete di sicurezza ogni sei ore che apre
solo ciò che era da aprire e chiude solo ciò che è già scaduto.

---

## 5. Verifiche eseguite

**99 test**, compilati con Kotlin 2.1 ed eseguiti, non solo scritti.
Dopo la revisione successiva sono 111.

**27 mutanti su 27 uccisi.** Il mutation testing ha trovato cose che il
RED-first non avrebbe trovato: RED-first prova che un test fallisce una volta,
il mutation testing prova che fallisce per la ragione giusta su ogni ramo.

Alcune verifiche di proprietà, non solo di esempi:

- andata e ritorno su 365 giorni × 3 orari × 2 confini fra `istanteDi` e
  `giornoLogico`, incluse le date di cambio ora legale
- 400 giorni di riprogrammazione a catena: nessun giorno saltato o ripetuto,
  ogni intervallo fra 23 e 25 ore
- tre anni di uso simulato del catalogo reale: nessun vincolo violato, ogni
  regola esce almeno una volta, nessuna più di 2,2 volte la media

### Difetti trovati, in ordine di gravità

**`exported="false"` sul receiver di boot** — avrebbe rotto la
riprogrammazione degli allarmi dopo ogni riavvio, senza un solo errore in log.

**`Modifier.weight` in un contenitore scorrevole** — la schermata principale
non avrebbe funzionato.

**`ACTION_MY_PACKAGE_REPLACED` mancante** — ogni aggiornamento dell'app
avrebbe spento le notifiche fino alla successiva apertura.

**Tre test che misuravano la cosa sbagliata**, tutti scoperti dai mutanti:
l'ordine del catalogo non era coperto perché i cataloghi sintetici erano già
ordinati; il confine esatto della soglia percentuale non era testato;
l'ordinamento della composizione usava conteggi uguali, quindi non poteva
distinguere una classifica da un ordine fisso.

**Tre punti di codice morto** che un lettore avrebbe creduto portanti: la
guardia sul sensore nel `Motore`, il livello 0 del ciclo in `Selezione`, il
giorno precedente nel `Pianificatore`.

**Contrasto sotto norma**: il tono tenue era a 4,29:1 contro i 4,5 richiesti
per il testo piccolo. Portato a 5,74:1.

**`contentDescription = ""` non nasconde nulla** — i numeri delle barre
sarebbero stati letti due volte da uno screen reader.

**Le azioni "Sì" e "No" delle notifiche erano scritte a mano** mentre le
stringhe esistevano inutilizzate: l'app sarebbe rimasta in italiano anche
tradotta.

### Difetti trovati nella revisione successiva

La logica di dominio ha retto: nessun difetto nel `:core`. Tutto quanto segue
stava nella cintura Android, mai eseguita, o negli strumenti di verifica.

**`POST_NOTIFICATIONS` non veniva mai richiesto.** Era dichiarato nel manifest e
controllato prima di ogni `notify()`, ma nessuno lo chiedeva all'utente. Da API
33 non è concesso all'installazione: su ogni telefono moderno l'app sarebbe
rimasta muta per sempre, senza un errore in log, e con lei sarebbe sparito il
canale su cui è costruita.

**`allowBackup` era `true`.** Il backup automatico avrebbe copiato lo storico su
Google Drive, mentre l'interfaccia dice in due punti che niente esce dal
dispositivo. L'assenza di `INTERNET` rende vera quella frase per l'app, non per
il sistema che la ospita.

**`rispondi()` poteva ribaltare la risposta dell'utente.** Ricalcolava la
baseline al momento della risposta, e la baseline si muove. Riprodotto: l'app
chiede perché il contapassi non sa, l'utente risponde «sì», e si vede registrare
il «no» del contapassi. Il commento nel codice affermava l'opposto, e nessun test
lo copriva perché tutti usavano uno storico immobile.

**La rete di sicurezza guardava solo ieri, e chiudeva in silenzio.** Due giorni
di app ferma bastavano a lasciare aperti per sempre i giorni precedenti, fuori
dallo storico valutato e fuori dalla mediana. E archiviava senza mai porre la
domanda della sera, proprio sui dispositivi per cui esiste, registrando come
«nessuna risposta» un silenzio che nessuno aveva chiesto di rompere. Da qui
`MotivoIgnoto.MAI_CHIESTO` e `core/Recupero.kt`, che porta nel core la decisione
su quali giorni chiudere e a chi chiedere: nel worker non sarebbe verificabile
senza un emulatore.

**Tre mutanti su venticinque non si applicavano più.** Lo script ne dichiarava
ventisette; ne conteneva venticinque, e tre cercavano frammenti che il codice non
conteneva più — due erano rimasti indietro quando il codice morto era stato
tolto, il terzo cercava una riga mai esistita. Lo script li segnalava come
`ERRORE` e proseguiva senza fallire, quindi la cifra «27 su 27» non era mai stata
smentita da nessuno. Ora lo script fallisce se un mutante sopravvive o non si
applica.

**Un mutante sopravvissuto.** Riparando il primo dei tre è emerso che nessun test
copriva l'ultimo gradino del rilassamento dei vincoli, quello in cui resta solo
il cooldown: il ciclo poteva fermarsi un gradino prima e servire una regola che
stava ancora riposando. Il caso mancava, ed è stato aggiunto.

**Il glifo `←` non è nel font.** Il sottoinsieme mappa 327 codepoint e non
include `U+2190`. L'unico comando di ritorno dello storico cade sul fallback di
sistema, con peso e allineamento diversi da Literata. Non ancora risolto.

### Una decisione ribaltata

Avevo ripiegato sul maiuscolo con spaziatura temendo che la feature OpenType
`smcp` non sopravvivesse al sottoinsieme del font. Sopravvive: verificato
sottoinsiemando Literata a mano. Il maiuscoletto è vero.

---

## 6. Numeri

| | |
|---|---|
| Regole | 150, trenta per categoria |
| Osservabili | 20 (13%), tutte fisiche |
| Contesti | 139 sempre, 5 feriali, 6 festive |
| Intensità | 59 / 70 / 21 |
| Catalogo | 37 KB |
| Font | 76 KB (da 955 KB) |
| Test | 99 |
| Mutanti | 33 dichiarati |
| Contrasto | 21:1 testo, 5,74:1 tenue chiaro, 6,44:1 tenue scuro |

---

## 7. Aperto

### Da fare

- Primo build reale: il modulo `app` non è mai stato compilato
- Icona del launcher, wrapper Gradle
- CI che rigenera il catalogo e fallisce se il diff non è vuoto
- Schema Room v1 committato in git (`exportSchema` è già attivo)

### Da misurare su dispositivo

Se rispondere alla notifica serale conta come interazione ai fini
dell'ibernazione. Android sospende le app inattive da mesi cancellando allarmi
e permessi, e quest'app è progettata per non essere aperta. Se non conta,
l'unica difesa è chiedere all'utente di disattivarla — una concessione
fastidiosa in un'app che si vende sull'assenza di attriti.

### Da rivedere a mano

Tre regole del catalogo su cui il giudizio è aperto:

- *"Oggi chiediti chi ti manca, e basta."* — per chi ha perso qualcuno da
  poco arriva alle sette del mattino senza preavviso.
- *"Oggi cancella un'applicazione che apri per abitudine."* — l'unica
  irreversibile: le altre finiscono a mezzanotte.
- *"Oggi vai a trovare qualcuno che non vedi da tempo."* — ammorbidita da una
  versione che diceva "senza avvisare prima", potenzialmente invadente verso
  una persona che non ha scelto di usare l'app.

### Distribuzione

Un solo binario per Google Play e F-Droid, costruito rispettando le regole più
severe: targetSdk 36, nessun permesso speciale, nessuna dipendenza
proprietaria, nessun Play Services.
