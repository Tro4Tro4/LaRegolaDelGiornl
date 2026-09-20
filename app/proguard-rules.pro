# Room genera implementazioni per riflessione sul nome della classe.
-keep class it.regoladelgiorno.data.** { *; }
# I worker e i receiver sono istanziati dal sistema per nome.
-keep class it.regoladelgiorno.android.Lavoro* { *; }
-keep class it.regoladelgiorno.android.Ricevitore* { *; }

# I nomi degli enum del dominio non sono solo codice: vengono scritti nel
# database come TEXT e riletti con valueOf, e stanno anche in assets/regole.json.
# Se R8 li rinomina, un aggiornamento che cambia la mappatura rende illeggibile
# il database gia' sul telefono, e il catalogo non si carica piu' affatto.
-keepclassmembers enum it.regoladelgiorno.core.** { *; }
