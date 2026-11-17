import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.tristan.spotifyfast"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tristan.spotifyfast"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Room Offline-First
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore (remplace EncryptedSharedPreferences)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Browser Chrome Custom Tabs pour OAuth PKCE
    implementation("androidx.browser:browser:1.8.0")

    // Networking & Serialization
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Requis au runtime par spotify-app-remote-release-0.8.0.aar (dépendance transitive non incluse dans AAR local)
    implementation("com.google.code.gson:gson:2.11.0")
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))

    // Images & Extraction de couleur
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Media3 (activé uniquement pour Spotify Connect distant)
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // ProfileInstaller pour Baseline Profile AOT (< 300ms cold start)
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Tests unitaires & d'intégration JVM
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.room:room-testing:2.6.1")
}

val spotifyAarUrl =
    "https://github.com/spotify/android-sdk/releases/download/v0.8.0-appremote_v2.1.0-auth/spotify-app-remote-release-0.8.0.aar"
val spotifyAarExpectedSha256 =
    "b5a6dd880eaf01f63a871cba9ef7af77c341f8a94ffc8fdf2e9021f9a9d4c198"
val spotifyAarFile = layout.projectDirectory.file("libs/spotify-app-remote-release-0.8.0.aar").asFile

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var read = input.read(buffer)
        while (read > 0) {
            digest.update(buffer, 0, read)
            read = input.read(buffer)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val downloadSpotifyAppRemoteAar by tasks.registering {
    group = "spotify"
    description = "Downloads spotify-app-remote-release-0.8.0.aar and validates its SHA-256 checksum."
    outputs.file(spotifyAarFile)

    doLast {
        spotifyAarFile.parentFile.mkdirs()
        if (!spotifyAarFile.exists() || sha256Hex(spotifyAarFile) != spotifyAarExpectedSha256) {
            logger.lifecycle("Downloading Spotify App Remote AAR from $spotifyAarUrl...")
            URL(spotifyAarUrl).openStream().use { input ->
                spotifyAarFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        val actualHash = sha256Hex(spotifyAarFile)
        if (actualHash != spotifyAarExpectedSha256) {
            spotifyAarFile.delete()
            throw GradleException(
                "SHA-256 mismatch for ${spotifyAarFile.name}: expected $spotifyAarExpectedSha256, got $actualHash",
            )
        }
        logger.lifecycle("Verified ${spotifyAarFile.name} (SHA-256: $actualHash)")
    }
}

tasks.named("preBuild") {
    dependsOn(downloadSpotifyAppRemoteAar)
}

val printSpotifyDashboardSetup by tasks.registering {
    group = "spotify"
    description = "Prints package name, Redirect URI, and Android debug keystore SHA-1 for Spotify Developer Dashboard."

    doLast {
        val debugKeystore = File(System.getProperty("user.home"), ".android/debug.keystore")
        val sha1Output =
            if (debugKeystore.exists()) {
                val ks = KeyStore.getInstance(debugKeystore, "android".toCharArray())
                val cert = ks.getCertificate("androiddebugkey")
                if (cert != null) {
                    MessageDigest.getInstance("SHA-1")
                        .digest(cert.encoded)
                        .joinToString(":") { "%02X".format(it) }
                } else {
                    "Unavailable"
                }
            } else {
                "Debug keystore not generated yet (will be created on first assembleDebug)"
            }
        println("==============================================================")
        println("Spotify Developer Dashboard Setup (spotify-fast-android)")
        println("Package Name : com.tristan.spotifyfast")
        println("Redirect URI : http://127.0.0.1:8888/callback")
        println("Debug SHA-1  : $sha1Output")
        println("==============================================================")
    }
}
