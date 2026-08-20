// Kotlin sa nezapája vlastným pluginom – od AGP 9 ho prekladá samo AGP.
// „kotlin-android“ bol jediný, kto volal zastaranú variant API, a KSP tu
// nemalo čo spracúvať: v projekte nie je ani jeden anotačný procesor.
plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
