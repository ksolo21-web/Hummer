package com.kreativstudio.app

import android.app.Application
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
        val provider = if (BuildConfig.DEBUG) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(provider)
    }
}
