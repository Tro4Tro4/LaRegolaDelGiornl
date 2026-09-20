package it.regoladelgiorno.core

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Le suite sono scritte come funzioni che REGISTRANO casi, non come metodi
 * annotati: cosi' girano identiche sia qui sotto JUnit sia dal runner
 * standalone in Harness.kt, utile in ambienti senza JUnit (per esempio lo
 * script di mutation testing in strumenti/mutazioni.py).
 *
 * DynamicTest riporta comunque ogni caso separatamente nel report di Gradle.
 */
class SuiteCore {

    @TestFactory
    fun dominio(): List<DynamicTest> =
        raccogliCasi().map { (nome, corpo) -> DynamicTest.dynamicTest(nome) { corpo() } }
}
