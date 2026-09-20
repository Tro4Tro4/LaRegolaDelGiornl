# Regole di manutenzione

Come si tiene vero quello che il progetto dice di sé.

Questo documento esiste per una ragione precisa. Per mesi la documentazione ha
dichiarato «99 test, 27 mutanti su 27». Lo script ne conteneva venticinque, e
tre di essi cercavano frammenti di codice che non esistevano più: non venivano
applicati, non verificavano nulla, e lo script li segnalava proseguendo senza
fallire. Nessun test poteva accorgersene, perché nessun test guarda la
documentazione. La cifra è rimasta sbagliata finché qualcuno non è andato a
contare a mano.

Le regole che seguono servono a impedire che succeda di nuovo. Non sono buone
intenzioni: quasi tutte sono verificate da `strumenti/coerenza.py`, che esce con
codice diverso da zero quando una di esse viene violata.

---

## Chi dice cosa

Quattro documenti, quattro compiti. Scrivere la cosa giusta nel posto sbagliato
è il modo più comune di far invecchiare la documentazione.

| Documento | Dice | Tempo verbale |
|---|---|---|
| `CLAUDE.md` | comandi, metodo, invarianti da non violare | **presente**: è sempre vero |
| `README.md` | cos'è il progetto, com'è messo, come si avvia | **presente** |
| `docs/PROGETTO.md` | perché le decisioni sono state prese, cosa si è trovato | **passato**: è un racconto |
| `docs/MANUTENZIONE.md` | queste regole | presente |
| `RIPRISTINO.md` | come si ricostruisce il progetto da zero | presente |

`PROGETTO.md` **non si riscrive a posteriori**. Se un numero al suo interno è
datato, è corretto che lo sia: racconta cosa si sapeva allora. Quando qualcosa
cambia, si aggiunge; non si corregge il passato. Per questo il cancello controlla
i numeri solo in `CLAUDE.md` e `README.md`.

---

## Le regole

**1. Un numero si scrive solo se qualcuno lo può contare.**
Ogni cifra in `CLAUDE.md` e `README.md` — test, mutanti, regole del catalogo — è
verificata da `coerenza.py` contro la realtà. Se vuoi scrivere una cifra che il
cancello non sa contare, insegnaglielo o non scriverla.

**2. Chi cambia un comportamento aggiorna il mutante che lo proteggeva.**
Un mutante che non si applica è peggio di un mutante che non esiste: occupa il
posto di una verifica e conta come superato. `coerenza.py` verifica che ogni
frammento da mutare compaia **esattamente una volta** nel sorgente.

**3. Chi aggiunge un comportamento aggiunge il mutante che lo rompe.**
Il test dimostra che il codice funziona; il mutante dimostra che il test se ne
accorgerebbe se smettesse. Un comportamento nuovo senza mutante è un test di cui
nessuno ha verificato la presa.

**4. Chi cambia un invariante lo cambia in `CLAUDE.md`, nella stessa commit.**
Gli invarianti sono le cose che «sembrano errori e verranno corrette». Se uno
smette di valere e resta scritto, la prossima sessione lo difenderà a vuoto; se
ne nasce uno e non viene scritto, la prossima sessione lo romperà in buona fede.

**5. Il catalogo non si tocca a mano.**
`strumenti/regole.py` è l'unica sorgente, e produce due artefatti che devono
restare identici a ciò che genera. `coerenza.py` li rigenera e confronta: se
differiscono, qualcuno ha modificato il file invece dello script.

**6. Prima di ogni commit: i test e il cancello. Prima di chiudere una fase: i mutanti.**

```bash
./gradlew :core:test                                  # sempre
python3 strumenti/coerenza.py                         # sempre
KOTLINC=<percorso> KOTLIN_STDLIB=<percorso> \
  python3 strumenti/mutazioni.py                      # a fine fase
```

**7. Una decisione si scrive quando viene presa.**
In `docs/PROGETTO.md`, con il motivo e il costo accettato. Scritta dopo è una
ricostruzione, e le ricostruzioni assomigliano sempre troppo a ciò che si voleva
decidere.

**8. Se una regola non è verificabile, o la rendi verificabile o la cancelli.**
È la regola che tiene in piedi le altre sette. Una regola che nessuno controlla
non è ferrea: è un desiderio, e i desideri scritti in un file invecchiano peggio
del codice.

---

## Cosa il cancello verifica

Eseguendo `python3 strumenti/coerenza.py`:

- i numeri di test e mutanti dichiarati in `CLAUDE.md` e `README.md`
- che ogni mutante si applichi ancora a un frammento esistente e unico
- che i due artefatti del catalogo siano quelli che `regole.py` genera
- che il manifest non chieda `INTERNET` né `USE_EXACT_ALARM`, che
  `allowBackup` sia `false` e che `RicevitoreRipristino` sia `exported="true"`
- che `:core` non importi Android, androidx o `Flow`, e non usi `Random`
- che l'app non importi Material, non derivi date fuori da `Tempo.kt` e non usi
  `clickable` senza `indication = null`
- che ogni carattere usato in `strings.xml` esista nel font sottoinsiemato
- che i percorsi citati nei documenti esistano davvero

Il cancello è stato provato al contrario: introdotta una violazione per volta,
le ha intercettate tutte. Se ne aggiungi un controllo, provalo allo stesso modo —
un cancello che non morde è peggio di nessun cancello, perché rassicura.

## Cosa il cancello non può verificare

Resta responsabilità di chi lavora:

- che il modulo `app` compili: serve l'SDK Android, e il cancello non compila nulla
- che i mutanti vengano **uccisi**: `coerenza.py` controlla solo che si applichino,
  ucciderli è compito di `mutazioni.py`
- che una regola del catalogo sia giusta da proporre a una persona
- che il testo di un invariante dica ancora il vero, e non solo che esista
- tutto ciò che richiede un dispositivo: notifiche, sensori, ibernazione

Queste non sono lacune da colmare con più script. Sono il confine fra ciò che una
macchina può garantire e ciò che deve garantire qualcuno.
