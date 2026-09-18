import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Release signing credentials, read from an untracked `signing.properties` at the repo root
 * (already listed in `.gitignore`, alongside `*.jks`/`*.keystore`).
 *
 * Expected keys: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. When the file is absent
 * — any clone that is not the release machine, and CI — the release build type is simply left
 * unsigned rather than failing to configure, so `assembleRelease` still verifies that R8 and the
 * resource shrinker are happy.
 */
val signingProps = Properties().apply {
    val f = rootProject.file("signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasSigningConfig = signingProps.getProperty("storeFile")?.let { rootProject.file(it).exists() } == true

android {
    namespace = "com.mw.offlineupi"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mw.offlineupi"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2-beta"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Generates res/xml/_generated_res_locale_config.xml from the values-* folders that actually
    // exist and injects android:localeConfig for it, so the per-app language list in system
    // Settings can never drift from the shipped translations. Requires
    // src/main/res/resources.properties to name the locale that the unqualified values/ folder is
    // written in. Only en-IN ships today; adding values-hi/ is then the whole job.
    androidResources {
        generateLocaleConfig = true
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Set -PofflinePay.noMinify to build an otherwise-identical release APK with R8 off.
            // Bisects release-only failures: if the bug vanishes, it is a missing keep rule.
            val noMinify = providers.gradleProperty("offlinePay.noMinify").isPresent
            isMinifyEnabled = !noMinify
            isShrinkResources = !noMinify
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // `packaging { jniLibs { useLegacyPackaging = true } }` was removed. It forced SQLCipher's
    // .so files to be stored compressed and extracted into the app's data directory on install —
    // which doubles their on-device footprint and makes System.loadLibrary("sqlcipher") pay a
    // decompression cost. minSdk is 26, well past the API 23 cutoff where uncompressed native
    // libraries became the default, so nothing here needs the legacy layout.
}

ksp {
    // Writes the Room schema JSON for each version into app/schemas/. These files are the input
    // to Room's migration testing and the only record of what version N's tables actually looked
    // like; without them exportSchema = true just warns and the migrations in AppDatabase cannot
    // be verified against anything.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle & Activity
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // CameraX
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // ML Kit Barcode
    implementation(libs.mlkit.barcode)

    // Biometric
    implementation(libs.biometric)

    // Security
    implementation(libs.security.crypto)
    implementation(libs.sqlcipher)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
