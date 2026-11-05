import java.util.Properties

// Version: apps/android/VERSION holds the release version (x.y.z); CI adds the build number (GitHub run number).
val gbsatVersion = rootProject.file("VERSION").readText().trim()
val gbsatBuild = (providers.gradleProperty("gbsatVersionCode").orNull
    ?: System.getenv("GBSAT_VERSION_CODE") ?: System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt().also {
    require(it in 1..2100000000) { "gbsatVersionCode must be a positive Android version code" }
}

// Signing (GBFrame CONVENTIONS.md "Signing"): the permanent key comes from GBSAT_KEYSTORE_FILE/GBSAT_KEYSTORE_PASSWORD
// (CI) or from gbsat.keystore.file/gbsat.keystore.passwordFile in local.properties (owner's PC), and signs debug and
// release alike. Without it the APK is a side-by-side "GBSat Dev" (app.gbsat.emulator.dev) signed with the debug key,
// so a differently signed APK can never claim the official application ID.
val gbsatLocalProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val gbsatKeyPath = System.getenv("GBSAT_KEYSTORE_FILE") ?: gbsatLocalProperties.getProperty("gbsat.keystore.file")
val gbsatKey = gbsatKeyPath?.let { file(it) }?.takeIf { it.isFile }
val gbsatKeyPassword = System.getenv("GBSAT_KEYSTORE_PASSWORD")
    ?: gbsatLocalProperties.getProperty("gbsat.keystore.passwordFile")?.let { file(it) }?.takeIf { it.isFile }
        ?.readText()?.trim()
if (gbsatKeyPath != null && gbsatKey == null) throw GradleException("Configured GBSat keystore does not exist")
if (gbsatKey != null && gbsatKeyPassword.isNullOrBlank()) throw GradleException("GBSat keystore password is missing")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Family convention: namespace must match the stable installed application ID.
    // Kotlin/JNI implementation packages can remain app-specific and are not inferred from this namespace.
    namespace = "app.gbsat.emulator"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "app.gbsat.emulator"
        minSdk = 28 // iconv in libc, AAudio, ANativeWindow
        targetSdk = 36
        versionCode = gbsatBuild
        versionName = "$gbsatVersion (build $gbsatBuild)"
        if (gbsatKey == null) applicationIdSuffix = ".dev"
        manifestPlaceholders["appLabel"] = if (gbsatKey == null) "GBSat Dev" else "GBSat"
        // Update channel (GBFrame docs/APP_UPDATES_STANDARD.md): -PgbChannel=play for the Google Play bundle, otherwise github.
        val gbChannel = providers.gradleProperty("gbChannel").getOrElse("github")
        manifestPlaceholders["gbUpdateChannel"] = gbChannel
        manifestPlaceholders["gbInstallPermission"] =
            if (gbChannel == "play") "android.permission.INTERNET" else "android.permission.REQUEST_INSTALL_PACKAGES"

        // The emulator core's hot paths are NEON-optimized; 32-bit ARM and x86 are not supported.
        ndk { abiFilters += "arm64-v8a" }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++20"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_CXX_SCAN_FOR_MODULES=OFF",
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DYmir_EXTRA_INLINING=ON",
                    "-DYmir_ENABLE_IPO=ON",
                    // 16 KB memory pages (required by Google Play for native code on Android 15+)
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                )
                // PGO training build (-PgbsatPgoGen): instrumented code that records which paths run most
                if (project.hasProperty("gbsatPgoGen")) arguments += "-DGBSAT_PGO_GEN=ON"
                // Profiling build: line tables too, so simpleperf can split time by source line (--sort srcline)
                if (project.hasProperty("gbsatProfile")) arguments += "-DGBSAT_LINE_TABLES=ON"
                // CI: reuse compiled objects between builds (ccache), so a small change recompiles only what it touches
                if (project.hasProperty("gbsatCcache")) {
                    arguments += listOf("-DCMAKE_C_COMPILER_LAUNCHER=ccache", "-DCMAKE_CXX_COMPILER_LAUNCHER=ccache")
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    signingConfigs {
        create("sideload") {
            if (gbsatKey != null) {
                storeFile = gbsatKey
                storePassword = gbsatKeyPassword
                keyAlias = "gbsat"
                keyPassword = gbsatKeyPassword
            } else {
                storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        jniLibs {
            useLegacyPackaging = false
            // Profiling build (-PgbsatProfile): keep the native symbols so simpleperf reports function names
            if (project.hasProperty("gbsatProfile")) keepDebugSymbols += "**/*.so"
        }
    }
    // README screenshots (Robolectric + Roborazzi), see ReadmeScreenshots.kt
    testOptions.unitTests {
        isIncludeAndroidResources = true
        all {
            it.systemProperty("roborazzi.test.record", "true")
            it.systemProperty("screenshotDir", rootProject.file("../../docs/screenshots").absolutePath)
            it.maxHeapSize = "3g"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.documentfile:documentfile:1.0.1")
    // Installs the baseline profiles (Compose's own and src/main/baseline-prof.txt) on sideloaded APKs, so the UI runs
    // compiled instead of interpreted right after an install (the library scrolled at ~33 fps on the RG Rotate: Nterp)
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation(project(":gbframe"))

    testImplementation(composeBom)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.espresso:espresso-core:3.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
