import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

// Release signing comes from env vars or the untracked keystore.properties (same scheme as amaxoniaerp-pos).
val privateSigningPropertiesFile = rootProject.file("keystore.properties")
val privateSigningProperties =
    Properties().apply {
        if (privateSigningPropertiesFile.isFile) {
            privateSigningPropertiesFile.inputStream().use(::load)
        }
    }

fun privateSigningValue(name: String): String? =
    System.getenv(name)?.takeIf(String::isNotBlank)
        ?: privateSigningProperties.getProperty(name)?.takeIf(String::isNotBlank)

val releaseKeystorePath = privateSigningValue("KIOSK_KEYSTORE_FILE")
val releaseKeystorePassword = privateSigningValue("KIOSK_KEYSTORE_PASSWORD")
val releaseKeyAlias = privateSigningValue("KIOSK_KEY_ALIAS")
val releaseKeyPassword = privateSigningValue("KIOSK_KEY_PASSWORD")

android {
    namespace = "com.amaxonia.kiosk"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.amaxonia.kiosk"
        minSdk = 28
        targetSdk = 36
        // Bump both on every build handed to a kiosk and log it in CHANGELOG.md (versionCode must always increase).
        versionCode = 2
        versionName = "0.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (releaseKeystorePath != null) {
                storeFile = rootProject.file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    // brand: white-label identity (colors, logo, names). environment: backend + payment terminal wiring.
    flavorDimensions += listOf("brand", "environment")

    productFlavors {
        create("flowerp") {
            dimension = "brand"
        }

        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            buildConfigField("boolean", "IS_DEV", "true")
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"http://10.0.2.2:8080/\"")
        }

        create("prod") {
            dimension = "environment"
            buildConfigField("boolean", "IS_DEV", "false")
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"https://api.listoerp.app/\"")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Compose screenshot tests (Robolectric) need merged resources and the test manifest.
        unitTests.isIncludeAndroidResources = true
    }
}

// Screenshot tests only render/compare under the Roborazzi tasks (recordRoborazziFlowerpDevDebug,
// verifyRoborazziFlowerpDevDebug, compareRoborazziFlowerpDevDebug); a plain unit test run skips them.
roborazzi {
    outputDir.set(file("screenshots"))
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    autoCorrect = false
}

dependencies {
    // Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Networking (Ktor) & Serialization
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    // Persistence (Room & DataStore)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // Media (ExoPlayer & Coil)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.coil.compose)

    // Hardware (Sunmi Printer)
    implementation(libs.sunmi.printer)

    // QR rendering (Yappy payment codes)
    implementation(libs.zxing.core)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}
