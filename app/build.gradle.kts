/**
 * AGP 9.0 porta il supporto Kotlin integrato: il plugin
 * org.jetbrains.kotlin.android NON va applicato, e non e' compatibile
 * con il nuovo DSL. Il plugin del compilatore Compose resta separato.
 */
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "it.regoladelgiorno"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.regoladelgiorno"
        minSdk = 23      // il minimo che le librerie AndroidX ancora supportano
        targetSdk = 36   // richiesto da Google Play; AGP 9.0 non va oltre
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true   // serve a CaricatoreCatalogo per distinguere debug e release
    }

    compileOptions {
        // java.time sotto API 26 esiste solo grazie a questo
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    // Lo schema va committato in git: alla versione 1 non c'e' migrazione da
    // testare, ma senza questo file il test da 1 a 2 sara' impossibile.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    val compose = platform(libs.compose.bom)
    implementation(compose)
    androidTestImplementation(compose)

    // Niente Material: solo foundation e ui.
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    debugImplementation(libs.compose.ui.tooling)
    // Niente ui-tooling-preview finche' non esiste una @Preview: oggi non ce n'e'
    // nessuna, e in implementation finirebbe nell'APK di release. Riaggiungerlo
    // insieme alla prima anteprima, non prima.

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
