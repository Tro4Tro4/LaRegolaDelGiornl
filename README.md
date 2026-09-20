# La Regola del Giorno

App Android: ogni giorno una sola regola, mostrata come un biglietto scritto a mano.
Nessun punteggio, nessuna serie da non interrompere. Solo il segno di come è andata.

## Cosa c'è in questo repository

| Percorso | Contenuto |
|---|---|
| `prototipo/B4/index.html` | Prototipo navigabile B4 (HTML+JS autonomo, apribile in un browser). È la fonte di verità del design. |
| `docs/specifica.md` | Specifica dell'interfaccia e delle regole di comportamento, ricavata dal prototipo B4. |

## Il prototipo B4

Si apre direttamente nel browser: `prototipo/B4/index.html`.

La cornice grigia in cima (l'"impalcatura") **non fa parte dell'app**: serve solo a
pilotare il prototipo — schermata, stato della regola, lunghezza del testo, tema,
scala dei caratteri di sistema al 100% o 200%.

Due schermate:

- **Principale** — la data in alto (tocco → storico), il biglietto con la regola del
  giorno, e sotto il biglietto il segno dell'esito.
- **Storico** — gli ultimi trenta giorni, la composizione per categoria di regola,
  e le impostazioni.

## Stato del lavoro

Il prototipo è completo e rispecchia le decisioni prese. **Il codice Android
(Kotlin) non è ancora in questo repository**: al momento dell'importazione
esisteva solo il prototipo. Il prototipo cita `Tipometria.kt` come sorgente
della funzione a scalini dei corpi tipografici — quel file va (ri)scritto sul
lato Android, e la specifica ne riporta la funzione esatta.

## Origine

Importato dall'artifact "La Regola del Giorno — prototipo B4" (19 settembre 2026),
byte per byte, senza modifiche.
