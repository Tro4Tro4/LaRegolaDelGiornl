#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Cancello della documentazione e degli invarianti.

Verifica meccanicamente cio' che altrimenti invecchia in silenzio. Non sostituisce
i test: controlla che quello che il progetto DICE di se' sia ancora vero.

E' nato da due bugie scoperte in una revisione: la documentazione dichiarava "99
test, 27 mutanti su 27" mentre lo script ne conteneva 25, tre dei quali cercavano
frammenti che il codice non aveva piu'. Nessun test poteva accorgersene, perche'
nessun test guarda la documentazione.

    python3 strumenti/coerenza.py

Esce con codice diverso da zero al primo controllo fallito. Non ha dipendenze:
solo la libreria standard, come il resto di strumenti/.
"""
import hashlib
import importlib.util
import pathlib
import re
import struct
import subprocess
import sys

RADICE = pathlib.Path(__file__).resolve().parent.parent
CORE_MAIN = RADICE / "core/src/main/kotlin/it/regoladelgiorno/core"
CORE_TEST = RADICE / "core/src/test/kotlin/it/regoladelgiorno/core"
APP_KT = RADICE / "app/src/main/kotlin"
MANIFEST = RADICE / "app/src/main/AndroidManifest.xml"
STRINGS = RADICE / "app/src/main/res/values/strings.xml"
FONT = RADICE / "app/src/main/res/font/literata_regular.ttf"
ASSET = RADICE / "app/src/main/assets/regole.json"
SPECCHIO = CORE_TEST / "CatalogoReale.kt"

# CLAUDE.md e README.md dichiarano lo stato CORRENTE e vanno tenuti veri.
# docs/PROGETTO.md racconta la storia: i suoi numeri sono datati per costruzione
# e non vanno riscritti a posteriori.
DOC_CORRENTI = [RADICE / "CLAUDE.md", RADICE / "README.md"]

problemi = []


def fallisce(controllo, dettaglio):
    problemi.append((controllo, dettaglio))


def leggi(p):
    return p.read_text(encoding="utf-8")


def kt(dir_):
    return sorted(dir_.rglob("*.kt"))


# --- 1. i numeri dichiarati ---------------------------------------------------

def conta_test():
    """Ogni caso e' una chiamata a test("...") registrata da raccogliCasi()."""
    return sum(len(re.findall(r'^\s*test\("', leggi(f), re.M)) for f in kt(CORE_TEST))


def carica_mutazioni():
    spec = importlib.util.spec_from_file_location("mut", RADICE / "strumenti/mutazioni.py")
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m.MUTAZIONI


def controlla_numeri():
    test_reali = conta_test()
    mutanti_reali = len(carica_mutazioni())
    for doc in DOC_CORRENTI:
        testo = leggi(doc)
        for dichiarato in {int(n) for n in re.findall(r"(\d+)\s+test\b", testo)}:
            if dichiarato != test_reali:
                fallisce("numeri", f"{doc.name} dichiara {dichiarato} test, ne esistono {test_reali}")
        for dichiarato in {int(n) for n in re.findall(r"(\d+)\s+mutanti", testo)}:
            if dichiarato != mutanti_reali:
                fallisce("numeri", f"{doc.name} dichiara {dichiarato} mutanti, ne esistono {mutanti_reali}")


# --- 2. i mutanti mordono ancora ----------------------------------------------

def controlla_mutanti():
    for i, (_, file, cerca, _, descr) in enumerate(carica_mutazioni()):
        sorgente = CORE_MAIN / file
        if not sorgente.exists():
            fallisce("mutanti", f"#{i} punta a {file}, che non esiste")
            continue
        n = leggi(sorgente).count(cerca)
        if n != 1:
            fallisce("mutanti", f"#{i} ({descr}): il frammento compare {n} volte in {file}, "
                                "quindi il mutante non viene applicato e non verifica nulla")


# --- 3. il catalogo e' quello che lo script genera -----------------------------

def controlla_catalogo():
    prima = {p: hashlib.sha256(p.read_bytes()).hexdigest() for p in (ASSET, SPECCHIO)}
    esito = subprocess.run([sys.executable, str(RADICE / "strumenti/regole.py")],
                           capture_output=True, text=True, cwd=RADICE)
    if esito.returncode != 0:
        fallisce("catalogo", f"regole.py fallisce: {esito.stderr.strip()[-200:]}")
        return
    for p, atteso in prima.items():
        if hashlib.sha256(p.read_bytes()).hexdigest() != atteso:
            fallisce("catalogo", f"{p.name} non e' quello che regole.py genera "
                                 "(il file e' stato rigenerato: guarda git diff)")


# --- 4. gli invarianti che si possono leggere ---------------------------------

def controlla_invarianti():
    manifest = leggi(MANIFEST)
    if "android.permission.INTERNET" in manifest:
        fallisce("invarianti", "il manifest chiede INTERNET: e' l'unica prova che l'app non parla con nessuno")
    if 'android:allowBackup="false"' not in manifest:
        fallisce("invarianti", "allowBackup non e' false: lo storico finirebbe nel backup di sistema, "
                               "e la frase «niente esce dal dispositivo» sarebbe falsa")
    if not re.search(r'RicevitoreRipristino"\s+android:exported="true"', manifest):
        fallisce("invarianti", "RicevitoreRipristino non e' exported=true: i broadcast di sistema "
                               "non lo raggiungerebbero, in silenzio")
    if "USE_EXACT_ALARM" in manifest:
        fallisce("invarianti", "USE_EXACT_ALARM nel manifest: Google Play la riserva a sveglie e calendari")

    for f in kt(CORE_MAIN) + kt(CORE_TEST):
        testo = leggi(f)
        for vietato, perche in [
            (r"^import android", "il :core non puo' dipendere da Android"),
            (r"^import androidx", "il :core non puo' dipendere da androidx"),
            (r"^import kotlinx\.coroutines\.flow", "niente Flow nel :core: l'osservazione sta in OsservatoreGiorni"),
        ]:
            if re.search(vietato, testo, re.M):
                fallisce("invarianti", f"{f.name}: {perche}")
        if f.parent == CORE_MAIN and re.search(r"\bRandom\b", testo):
            fallisce("invarianti", f"{f.name}: Random dentro :core, la selezione deve restare deterministica")

    for f in kt(APP_KT):
        testo = leggi(f)
        if re.search(r"^import androidx\.compose\.material", testo, re.M):
            fallisce("invarianti", f"{f.name}: Material. L'app usa solo foundation e ui")
        if f.name != "Tempo.kt" and re.search(r"LocalDate\.now\(\)|LocalTime\.now\(\)", testo):
            fallisce("invarianti", f"{f.name}: deriva una data da un istante fuori da Tempo.kt, "
                                   "e con un confine diverso da mezzanotte sbaglia giorno")
        if re.search(r"\.clickable\(\s*(role|onClick)", testo):
            fallisce("invarianti", f"{f.name}: clickable senza indication = null prende l'indicazione "
                                   "di sistema, e qui non c'e' Material da cui farla derivare")


# --- 5. i glifi usati esistono nel font ---------------------------------------

def codepoint_del_font():
    dati = FONT.read_bytes()
    n = struct.unpack(">H", dati[4:6])[0]
    tabelle = {}
    for i in range(n):
        o = 12 + 16 * i
        tag = dati[o:o + 4].decode("latin1")
        off, ln = struct.unpack(">II", dati[o + 8:o + 16])
        tabelle[tag] = (off, ln)
    off, _ = tabelle["cmap"]
    quante = struct.unpack(">H", dati[off + 2:off + 4])[0]
    cps = set()
    for i in range(quante):
        p = off + 4 + 8 * i
        sub = off + struct.unpack(">I", dati[p + 4:p + 8])[0]
        formato = struct.unpack(">H", dati[sub:sub + 2])[0]
        if formato == 4:
            segx2 = struct.unpack(">H", dati[sub + 6:sub + 8])[0]
            seg = segx2 // 2
            fine = struct.unpack(">%dH" % seg, dati[sub + 14:sub + 14 + segx2])
            inizio = struct.unpack(">%dH" % seg, dati[sub + 16 + segx2:sub + 16 + 2 * segx2])
            for a, b in zip(inizio, fine):
                if b != 0xFFFF:
                    cps.update(range(a, b + 1))
        elif formato == 12:
            gruppi = struct.unpack(">I", dati[sub + 12:sub + 16])[0]
            for g in range(gruppi):
                p2 = sub + 16 + 12 * g
                a, b, _ = struct.unpack(">III", dati[p2:p2 + 12])
                cps.update(range(a, min(b, 0x2FFF) + 1))
    return cps


def controlla_font():
    """Il font e' sottoinsiemato a 76 KB: un segno che non c'e' cade sul fallback
    di sistema, con peso e allineamento diversi, e nessuno lo segnala."""
    cps = codepoint_del_font()
    testi = re.findall(r"<string name=\"([^\"]+)\">(.*?)</string>", leggi(STRINGS), re.S)
    for nome, testo in testi:
        testo = testo.replace("\\'", "'")
        for ch in testo:
            if ord(ch) > 0x7E and ord(ch) not in cps:
                fallisce("font", f"la stringa {nome} usa U+{ord(ch):04X} ({ch!r}), "
                                 "che non e' nel font sottoinsiemato")


# --- 6. la documentazione cita file che esistono ------------------------------

def controlla_riferimenti():
    for doc in DOC_CORRENTI + [RADICE / "docs/PROGETTO.md", RADICE / "RIPRISTINO.md"]:
        for percorso in set(re.findall(r"`([a-zA-Z0-9_./-]+\.(?:kt|py|json|xml|md|kts|toml|pro))`", leggi(doc))):
            if "/" not in percorso:
                continue
            if "..." in percorso:
                continue  # abbreviazione marcata: core/.../Recupero.kt
            if not (RADICE / percorso).exists():
                fallisce("riferimenti", f"{doc.name} cita {percorso}, che non esiste")


# --- esecuzione ---------------------------------------------------------------

CONTROLLI = [
    ("numeri dichiarati", controlla_numeri),
    ("mutanti applicabili", controlla_mutanti),
    ("catalogo rigenerabile", controlla_catalogo),
    ("invarianti", controlla_invarianti),
    ("glifi nel font", controlla_font),
    ("riferimenti nei documenti", controlla_riferimenti),
]

if __name__ == "__main__":
    for nome, fn in CONTROLLI:
        prima = len(problemi)
        fn()
        esito = "ok" if len(problemi) == prima else f"{len(problemi) - prima} problemi"
        print(f"  {esito:14s} {nome}")

    if problemi:
        print()
        for controllo, dettaglio in problemi:
            print(f"[{controllo}] {dettaglio}")
        print(f"\n{len(problemi)} problemi")
        sys.exit(1)
    print("\ntutto coerente")
