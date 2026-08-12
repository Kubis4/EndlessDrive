# Endless Drive – release build (minify + resource shrinking).
#
# Hra nepoužíva reflexiu ani serializačné knižnice: uložená jazda ide cez
# vlastný textový kodek (RunCodec), takže názvy tried sa smú prepísať.
# Kotlin, Compose a DataStore si nosia vlastné pravidlá v knižniciach,
# tu držíme len to, čo by R8 inak zbytočne vyhodil alebo pokazil.

# Enumy sa v uloženej jazde zapisujú menom (ComponentSlot, RoadFeature…),
# takže ich valueOf/values musí prežiť.
-keepclassmembers enum sk.kubis.endlessdrive.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Čitateľné stopy zásobníka z produkčných pádov.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
