package com.kreativstudio.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
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
            if (ready) configureAppCheckForDistributionChannel()
            ready
        }.getOrDefault(false)
    }

    /**
     * Play Integrity is valid for the future Google Play release. Debug and private-alpha APKs
     * are deliberately sideloaded, so they must not be hard-gated by PLAY_RECOGNIZED attestation.
     * Firebase Authentication plus owner-only Firestore and Storage rules protect those builds.
     */
    private fun configureAppCheckForDistributionChannel() {
        if (BuildConfig.BUILD_TYPE != "release") return
        FirebaseAppCheck.getInstance().apply {
            installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            setTokenAutoRefreshEnabled(true)
        }
    }
}
