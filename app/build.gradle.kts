plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "sk.kubis.endlessdrive"
    compileSdk = 35

    defaultConfig {
        applicationId = "sk.kubis.endlessdrive"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("boolean", "SHOW_FPS", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "SHOW_FPS", "false")
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
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
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
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.android.gms:play-services-ads:25.4.0")

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
