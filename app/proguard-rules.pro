# Room genera implementazioni per riflessione sul nome della classe.
-keep class it.regoladelgiorno.data.** { *; }
# I worker e i receiver sono istanziati dal sistema per nome.
-keep class it.regoladelgiorno.android.Lavoro* { *; }
-keep class it.regoladelgiorno.android.Ricevitore* { *; }
