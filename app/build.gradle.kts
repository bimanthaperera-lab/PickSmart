plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// The account is optional (CLAUDE.md N4, section 14). With app/google-services.json present the
// Firebase plugin is applied and sign-in works; without it the app builds and runs with accounts off.
val firebaseConfig = file("google-services.json")
val firebaseEnabled = firebaseConfig.exists()
if (firebaseEnabled) apply(plugin = libs.plugins.google.services.get().pluginId)

/** The web OAuth client (type 3) from google-services.json, which Google sign-in asks a token for. */
fun googleWebClientId(): String {
    if (!firebaseEnabled) return ""
    @Suppress("UNCHECKED_CAST")
    val clients = (groovy.json.JsonSlurper().parse(firebaseConfig) as Map<String, Any?>)["client"] as List<Map<String, Any?>>
    @Suppress("UNCHECKED_CAST")
    return clients
        .flatMap { (it["oauth_client"] as? List<Map<String, Any?>>).orEmpty() }
        .firstOrNull { it["client_type"] == 3 }
        ?.get("client_id") as? String ?: ""
}

android {
    namespace = "lk.picksmart"
    compileSdk = 37

    defaultConfig {
        applicationId = "lk.picksmart"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The published fuel price file (CLAUDE.md section 8a). Empty until the owner publishes it;
        // set `picksmart.fuelPriceUrl` in gradle.properties (or with -P) to turn price checks on.
        val fuelPriceUrl = providers.gradleProperty("picksmart.fuelPriceUrl").orElse("").get()
        buildConfigField("String", "FUEL_PRICE_URL", "\"$fuelPriceUrl\"")

        buildConfigField("boolean", "FIREBASE_ENABLED", "$firebaseEnabled")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${googleWebClientId()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    lint {
        // Sinhala and Tamil are translated after the English text lands; a string without a
        // translation shows in English, so a gap is a warning, not a failed build.
        warning += "MissingTranslation"
    }

    bundle {
        // The rider picks the app language inside Picksmart, which may differ from the phone's,
        // so every install from Play must carry Sinhala and Tamil, not only the phone's language.
        language {
            enableSplit = false
        }
    }
}

// Exported schemas are committed and also served to device tests as assets (MigrationTestHelper).
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
