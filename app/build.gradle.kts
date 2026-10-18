plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

fun signingValue(env: String, property: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: providers.gradleProperty(property).orNull?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("JISR_KEYSTORE_FILE", "jisr.keystore.file")
val releaseStorePassword = signingValue("JISR_KEYSTORE_PASSWORD", "jisr.keystore.password")
val releaseKeyAlias = signingValue("JISR_KEY_ALIAS", "jisr.key.alias")
val releaseKeyPassword = signingValue("JISR_KEY_PASSWORD", "jisr.key.password")
val hasReleaseSigning = releaseStoreFile != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null && file(releaseStoreFile).isFile

if (System.getenv("JISR_REQUIRE_RELEASE_SIGNING") == "true" && !hasReleaseSigning) {
    throw GradleException("JISR_REQUIRE_RELEASE_SIGNING is set but the release keystore is missing or incomplete.")
}

android {
    namespace = "org.jisr.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.jisr.app"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0.0"
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("jisr") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("jisr") else signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = false
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("dev.chrisbanes.haze:haze:1.7.2")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.datastore:datastore-preferences:1.2.0")

    val nuvioQuickJs = file("libs/quickjs-kt-android-1.0.5-nuvio.aar")
    if (!nuvioQuickJs.exists()) throw GradleException("Missing app/libs/${nuvioQuickJs.name}")
    implementation(files(nuvioQuickJs))
    implementation("com.fleeksoft.ksoup:ksoup:0.2.6")
    implementation("io.ktor:ktor-http:3.4.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
}
