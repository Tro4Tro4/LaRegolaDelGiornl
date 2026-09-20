# La Regola del Giorno — specifica dell'interfaccia

Ricavata interamente dal prototipo `prototipo/B4/index.html`. Dove il prototipo
non dice nulla (persistenza, sorgente delle regole, notifiche vere) questo
documento tace: quelle decisioni non sono ancora state prese qui.

## 1. Principio

Una regola al giorno, presentata come un biglietto. L'app osserva, non premia:
non ci sono punteggi né serie consecutive. Il segno dell'esito sta **fuori** dal
biglietto, perché è l'osservazione dell'app, non parte del messaggio.

## 2. Schermata principale

- **Data** in alto a sinistra (`sabato 19 settembre`): maiuscoletto, 12sp,
  spaziatura `.14em`, colore tenue. È un pulsante verso lo storico; il testo
  resta a 12sp ma l'area toccabile è di almeno 48dp.
- **Biglietto**: filo di 1px, nessuna ombra, nessun riempimento, ruotato di
  −0,6° attorno a `12% 50%`. Più aria a destra che a sinistra
  (padding `30px 34px 32px 24px`) perché la mano si ferma prima del bordo.
- **Regola**: interlinea 1,35; crenatura −0,005em; peso normale.
- **Segno dell'esito**: 13px, sotto il biglietto, a 24px di distanza.
- Il blocco centrale è centrato con `safe center`: al 200% di scala dei caratteri
  non viene tagliato in alto.

### 2.1 Tipometria (funzione a scalini)

Il corpo della regola dipende dal numero di caratteri del testo. Stessa funzione
di `Tipometria.kt`:

| Lunghezza del testo | Corpo |
|---|---|
| ≤ 30 | 34sp |
| ≤ 42 | 32sp |
| ≤ 56 | 30sp |
| ≤ 72 | 28sp |
| oltre | 26sp |

```kotlin
fun dimensioneSp(testo: String): Int = when {
    testo.length <= 30 -> 34
    testo.length <= 42 -> 32
    testo.length <= 56 -> 30
    testo.length <= 72 -> 28
    else -> 26
}
```

## 3. I quattro stati della regola

| Stato | Segno | Nome accessibile |
|---|---|---|
| in attesa | cerchio vuoto | `in attesa` |
| rispettata | cerchio pieno | `rispettata` |
| non rispettata | cerchio con barra (dal basso a sinistra all'alto a destra) | `non rispettata` |
| non valutabile | trattino orizzontale | `non valutabile` |

I segni sono **disegnati** (SVG), non caratteri tipografici. Spessore del tratto:
9,5% del lato, con minimo 1px. Ogni segno porta il proprio nome accessibile,
anche dentro la lista dello storico.

## 4. Schermata storico

Tre livelli di stacco, distinti fra loro (52px, poi 64px con filo):

1. **gli ultimi trenta giorni** — righe `data · testo · segno`, separate da un
   filo sopra (non sulla prima). Chiusa dalla nota: «I giorni precedenti restano
   registrati, ma non si scorrono da qui.»
2. **quali regole ti riescono** — una barra per categoria (sociale, fisico,
   digitale, creativo, introspettivo) con tre segmenti a larghezza proporzionale
   reale: pieno = rispettate, tratteggio a 45° = non rispettate, contorno al 50%
   di opacità = non valutabili. La barra è composta da `div` proporzionali e non
   da un SVG stirato, che deformerebbe l'angolo del tratteggio. Ogni barra ha una
   `aria-label` con i tre conteggi per esteso.
3. **impostazioni** — vedi sotto.

## 5. Impostazioni

| Voce | Valore iniziale |
|---|---|
| Notifica del mattino | 07:00 |
| Notifica della sera | 21:30 |
| Il giorno inizia a | 00:00 |
| Osservazione automatica | non attiva |

Ogni voce è alta almeno 48dp. Gli orari si scelgono con il **selettore di
sistema** (`android.app.TimePickerDialog`), non con un selettore disegnato
dall'app: così rispetta lingua, formato 12/24 ore e screen reader del dispositivo.
Nel prototipo il selettore è mostrato come segnaposto tratteggiato, mai imitato.

### 5.1 Osservazione automatica

Sequenza in due tempi — **prima si spiega, poi il sistema chiede**:

1. Schermata dell'app: «Per osservare da solo le regole legate al movimento, il
   telefono legge il contapassi due volte al giorno: la mattina e la sera.
   Nient'altro, e niente esce dal dispositivo.» Due azioni: *Non ora* (tenue) e
   *Continua*.
2. Solo dopo *Continua*, la richiesta di sistema `ACTIVITY_RECOGNITION`
   (permesso a runtime da API 29).

Disattivarla non richiede conferma: si spegne subito.

## 6. Temi

| Tema | Fondo | Inchiostro | Tenue | Filo |
|---|---|---|---|---|
| Carta | `#ffffff` | `#000000` | nero al 60% | nero al 18% |
| Inchiostro | `#0a0a0a` | `#ffffff` | bianco al 56% | bianco al 24% |

Il tenue del tema Carta è al **60%**, non al 52%: a 52% il contrasto era 4,29:1,
sotto il minimo di 4,5:1 richiesto per il testo piccolo.

## 7. Accessibilità

- Tutti i bersagli toccabili ad almeno 48dp, anche quando il testo è più piccolo.
- Testo ridimensionabile fino al 200% senza tagli né sovrapposizioni (tutti i
  corpi sono moltiplicati per il fattore di scala di sistema).
- Contrasto verificato sul testo piccolo (≥ 4,5:1).
- Segni con nome accessibile; barre con etichetta discorsiva completa.
- L'animazione di entrata (opacità, 0,2s) è disattivata sotto
  `prefers-reduced-motion: reduce`.

## 8. Tipografia

- App: **Literata** (ripiego: Georgia, Times New Roman, serif).
- Ciò che appartiene al sistema operativo resta sans-serif di sistema e nel
  prototipo è reso come segnaposto, mai disegnato per somiglianza.

## 9. Non ancora deciso

Il prototipo non copre: da dove arrivano le regole, come si registra l'esito
(il prototipo lo cambia dall'impalcatura), la persistenza, la consegna reale
delle notifiche e il modo in cui il contapassi decide un esito.
