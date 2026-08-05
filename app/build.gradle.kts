plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.kreativstudio.app"
    compileSdk = 37

    val privateAlphaStoreFile = project.findProperty("KREATIV_PRIVATE_ALPHA_STORE_FILE")?.toString().orEmpty()
    val privateAlphaStorePassword = project.findProperty("KREATIV_PRIVATE_ALPHA_STORE_PASSWORD")?.toString().orEmpty()
    val privateAlphaKeyAlias = project.findProperty("KREATIV_PRIVATE_ALPHA_KEY_ALIAS")?.toString().orEmpty()
    val privateAlphaKeyPassword = project.findProperty("KREATIV_PRIVATE_ALPHA_KEY_PASSWORD")?.toString().orEmpty()

    defaultConfig {
        applicationId = "com.kreativstudio.app"
        minSdk = 28
        targetSdk = 37
        versionCode = 7
        versionName = "0.1.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Set these in ~/.gradle/gradle.properties, project gradle.properties, or CI secrets.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${project.findProperty("KREATIV_GOOGLE_WEB_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "OLIVIA_FIREBASE_UID", "\"${project.findProperty("KREATIV_OLIVIA_FIREBASE_UID") ?: ""}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${project.findProperty("KREATIV_FIREBASE_API_KEY") ?: ""}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${project.findProperty("KREATIV_FIREBASE_APP_ID") ?: ""}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${project.findProperty("KREATIV_FIREBASE_PROJECT_ID") ?: ""}\"")
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", "\"${project.findProperty("KREATIV_FIREBASE_STORAGE_BUCKET") ?: ""}\"")
        buildConfigField("String", "FIREBASE_AI_MODEL", "\"${project.findProperty("KREATIV_FIREBASE_AI_MODEL") ?: "gemini-3.6-flash"}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("privateAlpha") {
            if (privateAlphaStoreFile.isNotBlank()) storeFile = file(privateAlphaStoreFile)
            storePassword = privateAlphaStorePassword
            keyAlias = privateAlphaKeyAlias
            keyPassword = privateAlphaKeyPassword
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-dev"
        }
        create("privateAlpha") {
            initWith(getByName("debug"))
            versionNameSuffix = "-private-alpha"
            signingConfig = signingConfigs.getByName("privateAlpha")
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*"
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3.adaptive:adaptive:1.2.0")
    implementation("androidx.window:window:1.4.0")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("androidx.documentfile:documentfile:1.1.0")

    implementation("androidx.ink:ink-authoring:1.0.0")
    implementation("androidx.ink:ink-brush:1.0.0")
    implementation("androidx.ink:ink-rendering:1.0.0")
    implementation("androidx.ink:ink-storage:1.0.0")
    implementation("androidx.ink:ink-strokes:1.0.0")

    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")

    implementation(platform("com.google.firebase:firebase-bom:34.17.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-ai-ondevice:16.0.0-beta04")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    "privateAlphaImplementation"("com.google.firebase:firebase-appcheck-debug")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    "privateAlphaImplementation"("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
