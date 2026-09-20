#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Unica sorgente del catalogo. Genera DUE artefatti che devono restare allineati:
  app/src/main/assets/regole.json                      -> cio' che viene spedito
  core/src/test/.../CatalogoReale.kt                   -> cio' che i test validano
Eseguire dalla radice del progetto. In CI: rigenerare e fallire se il diff non e' vuoto."""
import json, collections, pathlib

RADICE = pathlib.Path(__file__).resolve().parent.parent
ASSET = RADICE / "app/src/main/assets/regole.json"
SPECCHIO = RADICE / "core/src/test/kotlin/it/regoladelgiorno/core/CatalogoReale.kt"

Q, FE, FS = "QUALSIASI", "FERIALE", "FESTIVO"
# (testo, contesto, intensita, metrica, soglia)   metrica None => DICHIARATA
P = "PASSI_SOPRA_BASELINE_PERCENTUALE"
A = "PASSI_SOPRA_BASELINE_ASSOLUTI"

SOCIALE = [
 ("Oggi ringrazia qualcuno per una cosa fatta più di un anno fa.", Q, 2, None, None),
 ("Oggi fai una domanda in più a chi vedi ogni giorno.", Q, 1, None, None),
 ("Oggi complimenta uno sconosciuto per qualcosa che ha scelto lui.", Q, 3, None, None),
 ("Oggi parla per primo con chi stavi per evitare.", Q, 3, None, None),
 ("Oggi chiedi a qualcuno che lavoro faceva suo padre.", Q, 2, None, None),
 ("Oggi telefona a una persona a cui pensi e non senti.", Q, 2, None, None),
 ("Oggi lascia parlare l'altro fino in fondo, senza completare le frasi.", Q, 2, None, None),
 ("Oggi presenta fra loro due persone che non si conoscono.", Q, 3, None, None),
 ("Oggi dì di no a una cosa che avresti accettato per inerzia.", Q, 3, None, None),
 ("Oggi chiedi scusa per una cosa piccola che nessuno ha notato.", Q, 2, None, None),
 ("Oggi mangia con qualcuno invece che da solo.", Q, 1, None, None),
 ("Oggi saluta per nome chi di solito saluti e basta.", Q, 1, None, None),
 ("Oggi racconta a qualcuno una cosa che di solito ti tieni.", Q, 3, None, None),
 ("Oggi ascolta una storia già sentita senza dire che la conosci.", Q, 2, None, None),
 ("Oggi chiedi un consiglio a chi non lo chiederesti mai.", Q, 3, None, None),
 ("Oggi fai una domanda che rischia di avere una risposta lunga.", Q, 2, None, None),
 ("Oggi offri aiuto prima che qualcuno lo chieda.", Q, 2, None, None),
 ("Oggi riprendi una conversazione lasciata a metà settimane fa.", Q, 2, None, None),
 ("Oggi scambia due parole con chi ti serve al banco.", Q, 1, None, None),
 ("Oggi scrivi a mano un biglietto e consegnalo di persona.", Q, 2, None, None),
 ("Oggi accetta il primo invito che ricevi.", Q, 2, None, None),
 ("Oggi chiedi a un collega su cosa sta lavorando davvero.", FE, 2, None, None),
 ("Oggi resta cinque minuti in più dopo i saluti.", Q, 1, None, None),
 ("Oggi dì a qualcuno una cosa che apprezzi e non hai detto.", Q, 3, None, None),
 ("Oggi chiedi come sta e aspetta la seconda risposta.", Q, 1, None, None),
 ("Oggi difendi un'opinione che di solito tieni per te.", Q, 3, None, None),
 ("Oggi vai a trovare qualcuno che non vedi da tempo.", FS, 3, None, None),
 ("Oggi cedi il turno a chi hai dietro.", Q, 1, None, None),
 ("Oggi chiedi a qualcuno di insegnarti una cosa che sa fare.", Q, 2, None, None),
 ("Oggi siediti accanto a qualcuno invece che di fronte.", Q, 1, None, None),
]

FISICO = [
 ("Oggi cammina venti minuti senza destinazione. Non è un tragitto.", Q, 2, A, 2000),
 ("Oggi scendi una fermata prima e fai il resto a piedi.", FE, 1, A, 1200),
 ("Oggi fai le scale ogni volta che puoi scegliere.", Q, 1, None, None),
 ("Oggi allunga la strada di casa di dieci minuti.", Q, 1, A, 1000),
 ("Oggi cammina mentre parli al telefono.", Q, 1, P, 10),
 ("Oggi fai il giro dell'isolato prima di entrare.", Q, 1, A, 600),
 ("Oggi vai a piedi dove andresti in macchina.", Q, 2, A, 1500),
 ("Oggi esci di casa entro un'ora dal risveglio.", Q, 2, None, None),
 ("Oggi cammina dopo cena, anche solo dieci minuti.", Q, 1, A, 800),
 ("Oggi porta a piedi una cosa che avresti spedito.", Q, 2, A, 1000),
 ("Oggi fai la spesa a piedi.", Q, 2, A, 1200),
 ("Oggi cambia strada per andare dove vai sempre.", Q, 1, P, 5),
 ("Oggi resta in piedi durante una telefonata lunga.", Q, 1, None, None),
 ("Oggi stiracchiati ogni volta che ti accorgi di essere fermo.", Q, 1, None, None),
 ("Oggi cammina dieci minuti prima di decidere una cosa.", Q, 2, A, 800),
 ("Oggi arriva a piedi in un posto che non conosci.", FS, 3, A, 1500),
 ("Oggi percorri un chilometro in più del solito.", Q, 2, A, 1300),
 ("Oggi sali le scale invece di aspettare l'ascensore.", Q, 1, None, None),
 ("Oggi porta fuori la spazzatura per la strada più lunga.", Q, 1, A, 400),
 ("Oggi accompagna qualcuno a piedi invece di salutarlo sulla porta.", Q, 2, A, 700),
 ("Oggi cammina con le mani fuori dalle tasche.", Q, 1, None, None),
 ("Oggi fai il doppio dei passi che fai di solito.", Q, 3, P, 100),
 ("Oggi esci a camminare anche se piove.", Q, 3, A, 1000),
 ("Oggi fai una pausa in piedi ogni ora.", FE, 1, None, None),
 ("Oggi corri per cento metri, una volta sola.", Q, 2, None, None),
 ("Oggi cammina fino a vedere qualcosa che non avevi mai visto.", FS, 3, A, 1500),
 ("Oggi lascia l'auto e vai in bicicletta.", Q, 2, None, None),
 ("Oggi cammina piano, più piano del solito.", Q, 1, None, None),
 ("Oggi esci a camminare prima di colazione.", Q, 2, A, 600),
 ("Oggi torna a casa dalla strada più lunga.", Q, 1, A, 900),
]

DIGITALE = [
 ("Oggi telefona a chi avresti scritto.", Q, 2, None, None),
 ("Oggi tieni il telefono in un'altra stanza mentre mangi.", Q, 1, None, None),
 ("Oggi non aprire i social prima di mezzogiorno.", Q, 2, None, None),
 ("Oggi rispondi a un messaggio solo quando puoi farlo bene.", Q, 2, None, None),
 ("Oggi esci senza cuffie.", Q, 2, None, None),
 ("Oggi lascia il telefono in borsa mentre cammini.", Q, 1, None, None),
 ("Oggi guarda una cosa sola alla volta, senza secondo schermo.", Q, 2, None, None),
 ("Oggi cancella un'applicazione che apri per abitudine.", Q, 3, None, None),
 ("Oggi non fotografare niente.", Q, 2, None, None),
 ("Oggi metti il telefono in bianco e nero.", Q, 2, None, None),
 ("Oggi leggi su carta qualcosa che avresti letto sullo schermo.", Q, 1, None, None),
 ("Oggi rispondi alle email in un unico momento.", FE, 2, None, None),
 ("Oggi non controllare le notifiche finché non ti fermi.", Q, 2, None, None),
 ("Oggi spegni il telefono per un'ora e dì a qualcuno quando.", Q, 3, None, None),
 ("Oggi cerca una cosa senza chiedere a internet.", Q, 2, None, None),
 ("Oggi non usare la navigazione satellitare.", Q, 2, None, None),
 ("Oggi scrivi un messaggio lungo invece di cinque corti.", Q, 1, None, None),
 ("Oggi togli il telefono dal tavolo quando parli con qualcuno.", Q, 1, None, None),
 ("Oggi ascolta un disco intero, nell'ordine in cui è fatto.", Q, 1, None, None),
 ("Oggi non guardare video mentre fai altro.", Q, 2, None, None),
 ("Oggi lascia il telefono a caricare lontano dal letto.", Q, 2, None, None),
 ("Oggi rispondi a voce invece che a messaggio.", Q, 1, None, None),
 ("Oggi disattiva le notifiche di una sola applicazione.", Q, 1, None, None),
 ("Oggi non leggere i commenti.", Q, 1, None, None),
 ("Oggi guarda l'orologio solo quando serve davvero.", Q, 2, None, None),
 ("Oggi chiudi tutte le schede aperte e ricomincia.", FE, 1, None, None),
 ("Oggi non mettere nessun segno di approvazione a nessuno.", Q, 2, None, None),
 ("Oggi scrivi a qualcuno senza aspettarti risposta.", Q, 2, None, None),
 ("Oggi tieni lo schermo al minimo della luminosità.", Q, 1, None, None),
 ("Oggi finisci un articolo prima di aprirne un altro.", Q, 1, None, None),
]

CREATIVO = [
 ("Oggi scrivi a mano qualcosa che avresti digitato.", Q, 1, None, None),
 ("Oggi fotografa una cosa sola. Una. Poi basta.", Q, 2, None, None),
 ("Oggi disegna un oggetto che hai davanti, male.", Q, 1, None, None),
 ("Oggi inventa un nome per una cosa che non ce l'ha.", Q, 1, None, None),
 ("Oggi cucina senza ricetta.", Q, 2, None, None),
 ("Oggi scrivi tre righe su come è cominciata la giornata.", Q, 1, None, None),
 ("Oggi canta qualcosa ad alta voce, da solo.", Q, 2, None, None),
 ("Oggi sposta un mobile.", Q, 2, None, None),
 ("Oggi ascolta un genere di musica che non ascolti mai.", Q, 1, None, None),
 ("Oggi scrivi la fine di una storia che non hai iniziato.", Q, 2, None, None),
 ("Oggi fai una cosa con la mano sbagliata.", Q, 1, None, None),
 ("Oggi impara a dire una frase in una lingua che non parli.", Q, 2, None, None),
 ("Oggi costruisci qualcosa con quello che stavi per buttare.", Q, 2, None, None),
 ("Oggi scrivi una lista di cose che non farai mai.", Q, 2, None, None),
 ("Oggi cambia il modo di fare una cosa che fai in automatico.", Q, 2, None, None),
 ("Oggi leggi ad alta voce una pagina a qualcuno.", Q, 2, None, None),
 ("Oggi metti un fiore o una foglia da qualche parte in casa.", Q, 1, None, None),
 ("Oggi prova una ricetta di un posto dove non sei stato.", FS, 2, None, None),
 ("Oggi disegna la pianta della casa dove sei cresciuto.", Q, 2, None, None),
 ("Oggi scrivi un titolo per la giornata di ieri.", Q, 1, None, None),
 ("Oggi indossa una cosa che tieni per le occasioni.", Q, 2, None, None),
 ("Oggi rimetti in funzione un oggetto rotto.", Q, 3, None, None),
 ("Oggi leggi una poesia. Una sola, due volte.", Q, 1, None, None),
 ("Oggi cambia una regola di casa tua per un giorno.", Q, 2, None, None),
 ("Oggi apri un libro a caso e leggi una pagina.", Q, 1, None, None),
 ("Oggi fai una fotografia di una cosa brutta.", Q, 1, None, None),
 ("Oggi scrivi il primo ricordo che ti viene, senza sceglierlo.", Q, 2, None, None),
 ("Oggi prepara da mangiare per qualcun altro.", FS, 2, None, None),
 ("Oggi cambia posto alla sedia dove ti siedi sempre.", Q, 1, None, None),
 ("Oggi finisci una cosa che avevi lasciato a metà.", Q, 2, None, None),
]

INTROSPETTIVO = [
 ("Oggi non guardare l'ora fino a mezzogiorno.", Q, 2, None, None),
 ("Oggi nota chi parla di più nelle conversazioni a cui partecipi.", Q, 1, None, None),
 ("Oggi accorgiti di quando stai per dire una bugia piccola.", Q, 2, None, None),
 ("Oggi conta quante volte ti scusi senza motivo.", Q, 1, None, None),
 ("Oggi resta dieci minuti fermo senza fare niente.", Q, 2, None, None),
 ("Oggi scrivi cosa ti ha dato fastidio, e perché.", Q, 2, None, None),
 ("Oggi mangia un pasto senza fare altro.", Q, 1, None, None),
 ("Oggi nota la prima cosa che pensi al risveglio.", Q, 1, None, None),
 ("Oggi chiediti, una volta, se lo stai facendo per te.", Q, 3, None, None),
 ("Oggi accorgiti di quale mano usi per prima.", Q, 1, None, None),
 ("Oggi osserva per un minuto una persona che non conosci.", Q, 1, None, None),
 ("Oggi nota il momento in cui ti stanchi.", Q, 1, None, None),
 ("Oggi non dare consigli a nessuno.", Q, 3, None, None),
 ("Oggi accorgiti di quando cambi argomento.", Q, 2, None, None),
 ("Oggi guarda fuori dalla finestra per cinque minuti.", Q, 1, None, None),
 ("Oggi nota cosa ti ha fatto sorridere per primo.", Q, 1, None, None),
 ("Oggi resta in silenzio per la prima ora della giornata.", FS, 3, None, None),
 ("Oggi chiediti cosa avresti fatto a vent'anni.", Q, 2, None, None),
 ("Oggi nota quante volte controlli se hai ragione.", Q, 2, None, None),
 ("Oggi ascolta i suoni della casa prima di accendere qualcosa.", Q, 1, None, None),
 ("Oggi scrivi una cosa su cui hai cambiato idea.", Q, 2, None, None),
 ("Oggi accorgiti di quando smetti di ascoltare.", Q, 2, None, None),
 ("Oggi nota di cosa hai paura senza motivo.", Q, 2, None, None),
 ("Oggi conta le cose che possiedi in una stanza sola.", Q, 2, None, None),
 ("Oggi chiediti chi ti manca, e basta.", Q, 3, None, None),
 ("Oggi nota quante decisioni prendi prima delle nove.", Q, 1, None, None),
 ("Oggi resta sveglio dieci minuti in più, al buio, senza schermi.", Q, 2, None, None),
 ("Oggi accorgiti di quale rumore ti dà più fastidio.", Q, 1, None, None),
 ("Oggi nota cosa fai quando nessuno ti guarda.", Q, 2, None, None),
 ("Oggi chiediti se questa giornata ti somiglia.", Q, 3, None, None),
]

GRUPPI = [("SOCIALE","soc",SOCIALE),("FISICO","fis",FISICO),("DIGITALE","dig",DIGITALE),
          ("CREATIVO","cre",CREATIVO),("INTROSPETTIVO","int",INTROSPETTIVO)]

regole = []
for categoria, sigla, elenco in GRUPPI:
    for i, (testo, contesto, intensita, metrica, soglia) in enumerate(elenco, 1):
        r = {"id": f"{sigla}-{i:02d}", "testo": testo, "categoria": categoria,
             "livello": "OSSERVABILE" if metrica else "DICHIARATA",
             "contesto": contesto, "intensita": intensita, "cooldownGiorni": 60}
        if metrica:
            r["metrica"] = metrica
            r["soglia"] = soglia
        regole.append(r)

with open(ASSET, "w", encoding="utf-8") as f:
    json.dump({"versione": 1, "regole": regole}, f, ensure_ascii=False, indent=2)

# --- file Kotlin per far girare il validatore VERO sul contenuto reale
def kt(r):
    extra = ""
    if "metrica" in r:
        extra = f", metrica = Metrica.{r['metrica']}, soglia = {r['soglia']}"
    testo = r["testo"].replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    return (f'    Regola("{r["id"]}", "{testo}", Categoria.{r["categoria"]}, '
            f'Livello.{r["livello"]}, Contesto.{r["contesto"]}, {r["intensita"]}{extra}),')

with open(SPECCHIO, "w", encoding="utf-8") as f:
    f.write("package it.regoladelgiorno.core\n\n")
    f.write("/** GENERATO da regole.py: specchio del contenuto di assets/regole.json. */\n")
    f.write("val CATALOGO_REALE: List<Regola> = listOf(\n")
    f.writelines(kt(r) + "\n" for r in regole)
    f.write(")\n")

c = collections.Counter(r["categoria"] for r in regole)
ctx = collections.Counter(r["contesto"] for r in regole)
ints = collections.Counter(r["intensita"] for r in regole)
oss = sum(1 for r in regole if r["livello"] == "OSSERVABILE")
print(f"{len(regole)} regole   osservabili {oss} ({oss*100//len(regole)}%)")
print("categorie   ", dict(c))
print("contesti    ", dict(ctx))
print("intensita   ", dict(sorted(ints.items())))
lunghe = [(len(r["testo"].split()), r["id"]) for r in regole if len(r["testo"].split()) > 15]
print("oltre 15 parole:", lunghe or "nessuna")
