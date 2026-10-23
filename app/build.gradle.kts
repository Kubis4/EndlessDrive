import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

android {
    namespace = "sk.kubis.endlessdrive"
    compileSdk = 36

    defaultConfig {
        applicationId = "sk.kubis.endlessdrive"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        // Play Games / Ads native .so — Studio deployuje ABI splity.
        // Bez filtra hlási 0 split APK a zlyhá na arm64-v8a zariadení.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    androidResources {
        // Ads / Play Games ťahajú desiatky prekladov. Hra má vlastný katalóg
        // (en, de, fr, it, sk, es), ostatné locale z balíčka vypadnú.
        localeFilters += listOf("en", "de", "fr", "it", "sk", "es")
    }

    splits {
        abi {
            // AAB nesmie ísť spolu s ABI split APK (AGP 9 / issuetracker 402800800).
            isEnable = gradle.startParameter.taskNames.none {
                it.contains("bundle", ignoreCase = true)
            }
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    bundle {
        // Play doručí iba ABI a hustotu konkrétneho zariadenia. Univerzálny
        // balík predtým niesol tri kópie natívnych knižníc a zbytočne rástol.
        abi { enableSplit = true }
        density { enableSplit = true }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("boolean", "SHOW_FPS", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "FULL"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "SHOW_FPS", "false")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/*.version",
            "META-INF/NOTICE.md",
            "META-INF/LICENSE.md",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json"
        )
        jniLibs {
            // Play stlačí download; mmap .so zmenší inštaláciu aj AAB.
            useLegacyPackaging = false
        }
    }
}

// Vstavaný Kotlin nepozná `kotlinOptions`. jvmTarget sa dopočíta
// z compileOptions.targetCompatibility, takže sa neuvádza dvakrát.
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("com.google.android.gms:play-services-games-v2:22.0.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Merger zahodí android:tag="". Po merge preto vložím literál "", aby
// PackageParser videl prázdny tag (AppOps), nie @string referenciu.
fun rewriteEmptyAttributionTag(file: File) {
    if (!file.isFile || file.name != "AndroidManifest.xml") return
    val original = file.readText()
    val updated = original.replace(
        """android:tag="@string/empty_attribution_tag"""",
        """android:tag="""""
    )
    if (updated != original) file.writeText(updated)
}

fun rewriteEmptyAttributionTagTree(root: File) {
    if (!root.exists()) return
    if (root.isFile) {
        rewriteEmptyAttributionTag(root)
        return
    }
    root.walkTopDown().forEach { rewriteEmptyAttributionTag(it) }
}

fun rewriteMergedAttributionManifests() {
    val intermediates = layout.buildDirectory.dir("intermediates").get().asFile
    listOf("merged_manifest", "merged_manifests", "packaged_manifests").forEach { dir ->
        rewriteEmptyAttributionTagTree(intermediates.resolve(dir))
    }
}

tasks.configureEach {
    val taskName = name
    if (taskName.startsWith("process") && taskName.contains("Manifest")) {
        doLast { rewriteMergedAttributionManifests() }
    }
}

tasks.configureEach {
    val taskName = name
    if (taskName.startsWith("process") && taskName.endsWith("Resources") && !taskName.contains("Test")) {
        doFirst { rewriteMergedAttributionManifests() }
    }
}
