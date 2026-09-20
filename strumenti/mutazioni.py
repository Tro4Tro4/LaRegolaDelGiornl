#!/usr/bin/env python3
"""Rompe deliberatamente il core e verifica che la suite se ne accorga."""
import json, shutil, subprocess, sys, pathlib, os

RADICE = pathlib.Path(__file__).resolve().parent.parent
SRC_MAIN = RADICE / "core/src/main/kotlin/it/regoladelgiorno/core"
SRC_TEST = RADICE / "core/src/test/kotlin/it/regoladelgiorno/core"
WORK = pathlib.Path("/tmp/mut")
# kotlinc standalone: gira senza Gradle e senza SDK Android, quindi un giro
# completo di mutanti costa una compilazione ciascuno e nient'altro.
KOTLINC = os.environ.get("KOTLINC", "kotlinc")
STDLIB = os.environ.get("KOTLIN_STDLIB", "")

MUTAZIONI = [
 ("Tempo",  "Tempo.kt", "locale.toLocalTime() < confine", "locale.toLocalTime() <= confine",
  "confine esclusivo invece che inclusivo"),
 ("Tempo",  "Tempo.kt", "data.minusDays(1).toEpochDay()", "data.toEpochDay()",
  "prima del confine non si torna a ieri"),
 ("Tempo",  "Tempo.kt", "if (ora < confine) base.plusDays(1) else base", "base",
  "istanteDi ignora il confine"),
 ("Tempo",  "Tempo.kt", "if (gds >= 6)", "if (gds >= 7)",
  "il sabato non e' piu' festivo"),
 ("Catalogo","Catalogo.kt", ".filterValues { it.size > 1 }", ".filterValues { it.size > 2 }",
  "id duplicati non rilevati"),
 ("Catalogo","Catalogo.kt", "if (r.metrica != null || r.soglia != null)", "if (r.metrica != null && r.soglia != null)",
  "dichiarata con sola soglia accettata"),
 ("Selezione","Selezione.kt", "return giorno - ultimo < r.cooldownGiorni", "return giorno - ultimo > r.cooldownGiorni",
  "cooldown invertito"),
 ("Selezione","Selezione.kt", "r.categoria != ieri.categoria", "r.categoria == ieri.categoria",
  "categoria di ieri forzata invece che vietata"),
 ("Selezione","Selezione.kt", "{ r -> ieri == null || !(ieri.intensita == 3 && r.intensita == 3) }", "{ _ -> true }",
  "vincolo di intensita rimosso"),
 ("Selezione","Selezione.kt", "it.contesto == Contesto.QUALSIASI || it.contesto == contestoOggi", "true",
  "contesto feriale/festivo ignorato"),
 ("Selezione","Selezione.kt", "candidati.sortedBy { it.id }", "candidati",
  "ordinamento rimosso: l'ordine del catalogo conta"),
 ("Selezione","Selezione.kt", "Math.floorMod(mescola(giorno, salt), ordinati.size.toLong())", "Math.floorMod(giorno, ordinati.size.toLong())",
  "hash rimosso: indice sequenziale"),
 ("Selezione","Selezione.kt", "for (quanti in livelli.size downTo 0)", "for (quanti in livelli.size downTo 1)",
  "i vincoli non si rilassano mai del tutto"),
 ("Valutazione","Valutazione.kt", "if (s.realtimeMs < m.realtimeMs) return null", "if (false) return null",
  "riavvio non rilevato"),
 ("Valutazione","Valutazione.kt", "if (s.passi < m.passi) return null", "if (false) return null",
  "azzeramento del contatore non rilevato"),
 ("Valutazione","Valutazione.kt", "if (campioni.size < minimo) return null", "if (false) return null",
  "baseline calcolata anche con pochi campioni"),
 ("Valutazione","Valutazione.kt", "storia.takeLast(finestra)", "storia.take(finestra)",
  "finestra presa dall'inizio invece che dalla fine"),
 ("Valutazione","Valutazione.kt", "d * 100 >= baseline * (100 + (regola.soglia ?: 0))", "d * 100 > baseline * (100 + (regola.soglia ?: 0))",
  "soglia percentuale: confine >= diventa >"),
 ("Valutazione","Valutazione.kt", "d >= baseline + (regola.soglia ?: 0)", "d >= baseline",
  "soglia assoluta ignorata"),
 ("Valutazione","Valutazione.kt", "if (d != null && baseline != null) {", "if (d != null && baseline != null && rispostaUtente == null) {",
  "la risposta dell'utente scavalca il sensore"),
 ("Motore","Motore.kt", "archivio.leggi(giorno)?.let { return it }", "archivio.leggi(giorno)",
  "apri non e' piu' idempotente"),
 ("Motore","Motore.kt", "if (!corrente.aperto) return corrente", "if (false) return corrente",
  "chiudi rivaluta un giorno gia' chiuso"),
 ("Motore","Motore.kt", "if (corrente.fonte == Fonte.SENSORE) return corrente", "if (false) return corrente",
  "la risposta sovrascrive il verdetto del sensore"),
 ("Pianificatore","Pianificatore.kt", "if (i.isAfter(adesso))", "if (!i.isBefore(adesso))",
  "sveglia allo stesso istante: riscatto immediato"),
 ("Pianificatore","Pianificatore.kt", "for (g in (corrente - 1)..(corrente + 2))", "for (g in corrente..(corrente + 2))",
  "il giorno logico precedente non e' candidato"),
]

def esegui(i):
    modulo, file, vecchio, nuovo, desc = MUTAZIONI[i]
    if WORK.exists(): shutil.rmtree(WORK)
    WORK.mkdir(parents=True)
    for d in (SRC_MAIN, SRC_TEST):
        for f in d.glob("*.kt"):
            if f.name != "SuiteCore.kt":   # dipende da JUnit, qui non serve
                shutil.copy(f, WORK / f.name)
    p = WORK / file
    testo = p.read_text()
    n = testo.count(vecchio)
    if n != 1:
        return (i, modulo, desc, "ERRORE", f"{n} occorrenze del frammento")
    p.write_text(testo.replace(vecchio, nuovo))

    c = subprocess.run([KOTLINC] + [str(f) for f in WORK.glob("*.kt")] + ["-d", str(WORK/"out")],
                       capture_output=True, text=True)
    if not (WORK/"out").exists():
        return (i, modulo, desc, "NON COMPILA", c.stderr[-200:])
    cp = f"{WORK/'out'}" + (f":{STDLIB}" if STDLIB else "")
    r = subprocess.run(["java", "-cp", cp, "it.regoladelgiorno.core.HarnessKt"],
                       capture_output=True, text=True)
    ucciso = r.returncode != 0
    riga = [l for l in r.stdout.splitlines() if "passati" in l]
    return (i, modulo, desc, "ucciso" if ucciso else "SOPRAVVISSUTO", riga[-1] if riga else "")

if __name__ == "__main__":
    a, b = int(sys.argv[1]), int(sys.argv[2])
    for i in range(a, min(b, len(MUTAZIONI))):
        i, modulo, desc, stato, nota = esegui(i)
        print(f"[{i:2d}] {stato:14s} {modulo:13s} {desc}  ({nota})", flush=True)
