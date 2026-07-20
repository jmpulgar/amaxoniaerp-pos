import org.gradle.api.GradleException
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
}

val privateSigningPropertiesFile = rootProject.file("keystore.properties")
val privateSigningProperties =
    Properties().apply {
        if (privateSigningPropertiesFile.isFile) {
            privateSigningPropertiesFile.inputStream().use(::load)
        }
    }

fun privateSigningValue(name: String): String? =
    providers.environmentVariable(name).orNull?.takeIf(String::isNotBlank)
        ?: providers.gradleProperty(name).orNull?.takeIf(String::isNotBlank)
        ?: privateSigningProperties.getProperty(name)?.takeIf(String::isNotBlank)

val releaseKeystorePath = privateSigningValue("AMAXONIA_KEYSTORE_FILE")
val releaseKeystorePassword = privateSigningValue("AMAXONIA_KEYSTORE_PASSWORD")
val releaseKeyAlias = privateSigningValue("AMAXONIA_KEY_ALIAS")
val releaseKeyPassword = privateSigningValue("AMAXONIA_KEY_PASSWORD")

android {
    namespace = "com.amaxonia.pos"

    compileSdk {
        version = release(36)
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(releaseKeystorePath ?: "missing-release-keystore")
            storePassword = releaseKeystorePassword.orEmpty()
            keyAlias = releaseKeyAlias.orEmpty()
            keyPassword = releaseKeyPassword.orEmpty()
        }
    }

    defaultConfig {
        applicationId = "com.amaxonia.pos"
        minSdk = 29
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "brand"

    productFlavors {
        create("amaxonia") {
            dimension = "brand"
            applicationId = "com.amaxonia.pos"
        }

        create("banescoVenezuela") {
            dimension = "brand"
            // Provisional: reemplazar por el applicationId oficial de Banesco cuando esté disponible.
            applicationId = "com.amaxonia.pos.banesco"
        }
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "BASE_URL",
                // "\"https://api.listoerp.app/\""
                "\"http://192.168.2.10:8080/\"",
                // "\"http://10.0.2.2:8080/\""
            )
        }

        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            buildConfigField(
                "String",
                "BASE_URL",
                "\"https://api.listoerp.app/\"",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    applicationVariants.all {
        outputs.all {
            val flavorName = productFlavors.joinToString("-") { it.name }
            val apkName = "$flavorName-pos-v$versionName-${buildType.name}.apk"
            @Suppress("UNCHECKED_CAST")
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = apkName
        }
    }
}

ksp {
    arg("room.schemaLocation", file("schemas").absolutePath)
    arg("room.incremental", "true")
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/detekt-baseline.xml")
}

ktlint {
    android = true
    version.set("1.5.0")
    ignoreFailures = false
    outputToConsole = true
}

val verifyReleaseSigningConfig by tasks.registering {
    group = "verification"
    description = "Fails release signing with an explicit message when private configuration is missing."

    doLast {
        val missingValues =
            buildList {
                if (releaseKeystorePath.isNullOrBlank()) add("AMAXONIA_KEYSTORE_FILE")
                if (releaseKeystorePassword.isNullOrBlank()) add("AMAXONIA_KEYSTORE_PASSWORD")
                if (releaseKeyAlias.isNullOrBlank()) add("AMAXONIA_KEY_ALIAS")
                if (releaseKeyPassword.isNullOrBlank()) add("AMAXONIA_KEY_PASSWORD")
            }
        if (missingValues.isNotEmpty()) {
            throw GradleException(
                "Missing release signing configuration: ${missingValues.joinToString()}. " +
                    "Provide environment variables, private Gradle properties, or ignored keystore.properties.",
            )
        }

        val configuredKeystore = rootProject.file(checkNotNull(releaseKeystorePath))
        if (!configuredKeystore.isFile) {
            throw GradleException(
                "Configured release keystore does not exist. Check AMAXONIA_KEYSTORE_FILE.",
            )
        }
    }
}

tasks.configureEach {
    if (name.startsWith("validateSigning") && name.endsWith("Release")) {
        dependsOn(verifyReleaseSigningConfig)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.sunmi.printer)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(files("libs/HKACryptoLib03022026.aar"))
}
