package com.kreativstudio.app

import android.app.Application
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class KreativApplication : Application() {
    lateinit var container: KreativContainer
        private set

    var firebaseReady: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        firebaseReady = initializeFirebaseIfConfigured()
        container = KreativContainer(this, firebaseReady)
    }

    private fun initializeFirebaseIfConfigured(): Boolean {
        if (BuildConfig.FIREBASE_API_KEY.isBlank() ||
            BuildConfig.FIREBASE_APP_ID.isBlank() ||
            BuildConfig.FIREBASE_PROJECT_ID.isBlank()
        ) return false

        return runCatching {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .apply {
                        if (BuildConfig.FIREBASE_STORAGE_BUCKET.isNotBlank()) {
                            setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
                        }
                    }
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
            val ready = FirebaseApp.getApps(this).isNotEmpty()
            if (ready) configureAppCheck()
            ready
        }.getOrDefault(false)
    }

    private fun configureAppCheck() {
        val appCheck = FirebaseAppCheck.getInstance()
        val provider = if (isProbablyEmulator()) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        appCheck.installAppCheckProviderFactory(provider)
        appCheck.setTokenAutoRefreshEnabled(true)
    }

    /**
     * Debug App Check is reserved for emulator test runs. A debug-signed APK installed on a
     * physical phone must still use Play Integrity; otherwise every reinstall creates a new,
     * unregistered debug token and Firebase rejects otherwise valid signed-in requests.
     */
    private fun isProbablyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val hardware = Build.HARDWARE.lowercase()

        return fingerprint.startsWith("generic") ||
            "emulator" in fingerprint ||
            "vbox" in fingerprint ||
            "test-keys" in fingerprint && "sdk" in product ||
            "google_sdk" in model ||
            "emulator" in model ||
            "android sdk built for" in model ||
            "genymotion" in manufacturer ||
            (brand.startsWith("generic") && device.startsWith("generic")) ||
            product.contains("sdk_gphone") ||
            product.startsWith("sdk") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu")
    }
}
