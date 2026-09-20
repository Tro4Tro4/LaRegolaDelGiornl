package it.regoladelgiorno.core

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * I casi vengono REGISTRATI, non eseguiti subito: cosi' la stessa suite serve
 * al runner standalone (qui sotto) e alla @TestFactory di JUnit, che ha bisogno
 * dei corpi come lambda per riportare ogni caso separatamente.
 */
internal val casi = mutableListOf<Pair<String, () -> Unit>>()

internal fun test(nome: String, corpo: () -> Unit) { casi += nome to corpo }

fun raccogliCasi(): List<Pair<String, () -> Unit>> {
    casi.clear()
    testTempo(); testCatalogo(); testSelezione(); testValutazione()
    testMotore(); testPianificatore(); testTipometria(); testComposizione()
    testSegno(); testRecupero(); testCatalogoReale()
    return casi.toList()
}

internal fun <T> eq(atteso: T, ottenuto: T, nota: String = "") {
    if (atteso != ottenuto) throw AssertionError("atteso <$atteso> ottenuto <$ottenuto> $nota")
}

internal fun vero(c: Boolean, nota: String = "") { if (!c) throw AssertionError("falso: $nota") }

/**
 * runBlocking minimale: kotlinx-coroutines non e' raggiungibile dalla rete del
 * container. Le finte non sospendono mai davvero, quindi basta questo.
 * Nel progetto Gradle si usa runTest di kotlinx-coroutines-test.
 */
internal fun <T> bloccante(blocco: suspend () -> T): T {
    var risultato: Result<T>? = null
    blocco.startCoroutine(Continuation(EmptyCoroutineContext) { risultato = it })
    return checkNotNull(risultato) { "la finta ha sospeso davvero" }.getOrThrow()
}

/** Runner standalone, per ambienti senza JUnit. */
fun main() {
    val esiti = raccogliCasi().map { (nome, corpo) ->
        nome to try { corpo(); null } catch (e: Throwable) { e.message ?: e.toString() }
    }
    esiti.forEach { (n, err) -> println(if (err == null) "  ok   $n" else "  FAIL $n\n         $err") }
    val falliti = esiti.count { it.second != null }
    println("\n${esiti.size - falliti}/${esiti.size} passati")
    if (falliti > 0) kotlin.system.exitProcess(1)
}
