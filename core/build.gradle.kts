/**
 * Modulo Kotlin puro, SENZA alcuna dipendenza Android.
 * Non e' una preferenza stilistica: e' il motivo per cui i test del dominio
 * girano in un secondo senza emulatore. Se qualcuno aggiunge qui una
 * dipendenza Android, il modulo smette di compilare, ed e' esattamente
 * l'effetto voluto.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit.jupiter)
    // Da Gradle 9 il launcher non e' piu' aggiunto d'ufficio al classpath di runtime.
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}
