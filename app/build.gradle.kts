import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Secrets live in local.properties (git-ignored). Everything is optional: the app runs without them.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(key: String): String = (localProps.getProperty(key) ?: "").trim()

android {
    namespace = "com.sofasports.tv"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.sofasports.tv"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "0.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SENTRY_DSN", "\"${localProp("sentry.dsn")}\"")
        buildConfigField("String", "CONFIG_URL", "\"${localProp("sofa.configUrl").ifEmpty { "https://raw.githubusercontent.com/gitzero-io/sofasports/main/release-assets/config.json" }}\"")
        buildConfigField("String", "VERSION_URL", "\"${localProp("sofa.versionUrl").ifEmpty { "https://raw.githubusercontent.com/gitzero-io/sofasports/main/release-assets/version.json" }}\"")
        buildConfigField("String", "FEEDBACK_URL", "\"${localProp("sofa.feedbackUrl").ifEmpty { "https://example.com/sofasports-feedback" }}\"")
    }

    // Release signing is opt-in: add these four keys to local.properties once you've created a keystore.
    // Without them, assembleRelease produces an unsigned APK (nothing is signed or created automatically).
    val releaseStore = localProp("release.storeFile")
    if (releaseStore.isNotEmpty()) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = localProp("release.storePassword")
                keyAlias = localProp("release.keyAlias")
                keyPassword = localProp("release.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            optimization {
                enable = true
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStore.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
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
    sourceSets {
        // Debug builds ship the unit-test fixtures as assets so the app can run fully offline
        // (Settings → Data source → Fixtures). Release builds don't include them.
        getByName("debug") {
            assets.directories.add("src/test/resources")
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)
    implementation(libs.sentry.android)
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
