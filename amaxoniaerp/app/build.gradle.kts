plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.amaxonia.erp"

    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.amaxonia.erp"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "BASE_URL",
                // "\"https://api.listoerp.app/\"",
                // "\"http://192.168.2.12:8080/\"",
                "\"http://10.0.2.2:8080/\"",
                // "\"http://127.0.0.1:8080/\""
            )
            buildConfigField("String", "DEFAULT_COUNTRY_CODE", "\"PA\"")
        }

        release {
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
            buildConfigField("String", "DEFAULT_COUNTRY_CODE", "\"PA\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    applicationVariants.all {
        outputs.all {
            val apkName = "amaxoniaerp-v$versionName-${buildType.name}.apk"
            @Suppress("UNCHECKED_CAST")
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = apkName
        }
    }
}

ksp {
    arg("room.schemaLocation", file("schemas").absolutePath)
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

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
    implementation(libs.coil.compose)
    implementation(libs.sunmi.printer)
    implementation(libs.imin.printer)
    implementation(files("libs/HKACryptoLib03022026.aar"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Tarea requerida por el indexador y sincronizador de Android Studio para scripts Kotlin DSL
tasks.register("prepareKotlinBuildScriptModel") {}

