# Ricostruzione del progetto

Cosa fare dopo aver scompattato l'archivio, in ordine.

---

## 1. Scompatta

```bash
unzip regola-del-giorno.zip
cd progetto
```

La cartella si chiama `progetto`: rinominala come preferisci, niente dipende
dal nome.

**La struttura è già corretta.** Non serve spostare file: le cartelle
rispettano il layout che Gradle si aspetta, e i sorgenti stanno sotto
`src/main/kotlin` (non `src/main/java`, che pure funzionerebbe).

## 2. Inizializza git

```bash
git init && git add -A && git commit -m "prima importazione"
```

Fallo **prima** di lanciare Claude Code. Senza cronologia non puoi vedere cosa
ha cambiato né tornare indietro, ed è la rete di sicurezza più importante che
hai quando deleghi modifiche.

## 3. Genera il wrapper Gradle

Non è incluso: sono binari che è meglio generare sulla propria macchina.

```bash
gradle wrapper --gradle-version 9.1
```

Serve Gradle installato. Se non ce l'hai, apri il progetto una volta in Android
Studio: lo genera da solo.

## 4. Verifica che il core giri

```bash
./gradlew :core:test
```

**Questo comando non richiede l'SDK Android.** Deve dare 99 test verdi. Se
fallisce, il problema è il toolchain (serve JDK 17), non il codice: quei test
sono stati eseguiti davvero.

È il primo controllo da fare, perché separa nettamente "l'ambiente è a posto"
da "il codice Android ha problemi".

## 5. Il primo build dell'app

```bash
./gradlew :app:assembleDebug
```

**Aspettati che fallisca.** Il modulo `app` non è mai stato compilato: niente
SDK Android nell'ambiente in cui è stato scritto. Gli errori sono previsti,
non sono un sintomo che qualcosa sia andato storto nella migrazione.

Dove guardare per primo, in ordine di probabilità:

1. **Il suffisso di KSP** in `gradle/libs.versions.toml`. Cambia a ogni patch
   di Kotlin ed è l'unica versione che non è stato possibile verificare.
2. **La firma di `clickable` senza `indication`**, cambiata fra le versioni di
   Compose. In `Spiegazione.kt` è usata la forma breve.
3. **Le API di `WindowInsets`**, i cui nomi si sono spostati più volte.

Mancano due risorse: l'icona del launcher (generala con Image Asset di Android
Studio) e, se vuoi, un `values-v31` per lo splash.

## 6. Avvia Claude Code

```bash
claude
```

`CLAUDE.md` nella radice viene letto automaticamente a ogni sessione e
rimane in contesto anche dopo una compattazione. Contiene i comandi, il metodo
di lavoro richiesto e gli invarianti da non violare.

`docs/PROGETTO.md` **non** viene caricato da solo, ed è voluto: è lungo e serve
di rado. Chiedilo esplicitamente quando ti serve il perché di una decisione.

Una prima richiesta sensata:

> Leggi CLAUDE.md. Poi lancia `./gradlew :app:assembleDebug` e correggi gli
> errori di compilazione uno alla volta, spiegandomi ogni causa prima di
> toccare il codice. Non modificare il modulo `:core` senza dirmelo.

---

## Cosa non toccare a mano

**Il catalogo è generato.** `strumenti/regole.py` è l'unica sorgente e produce
due file che devono restare allineati:

- `app/src/main/assets/regole.json` — ciò che viene spedito
- `core/src/test/.../CatalogoReale.kt` — ciò che i test validano

Per cambiare una regola si modifica lo script e si rigenera:

```bash
python3 strumenti/regole.py
```

Serve ancora una riga di CI che rigeneri e fallisca se il diff non è vuoto.
Senza, fra sei mesi i test valideranno un catalogo diverso da quello in mano
agli utenti.

## Mutation testing senza Gradle

Gira con `kotlinc` standalone, senza SDK Android:

```bash
KOTLINC=/percorso/kotlinc KOTLIN_STDLIB=/percorso/kotlin-stdlib.jar \
  python3 strumenti/mutazioni.py 0 27
```

Su una macchina a un core sono circa dieci minuti. Va lanciato prima di
considerare chiusa qualunque modifica al modulo `:core`.

---

## Requisiti

JDK 17. SDK Android con build tools 36. Python 3 per gli script. Gradle 9.1 o
il wrapper. Android Studio per il primo build — dopo, VS Code va benissimo.
