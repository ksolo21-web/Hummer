package com.koenterprises.territorycardstudio

import android.app.Application

class TerritoryCardStudioApplication : Application() {
    val appearancePreferences: AppearancePreferenceStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppearancePreferenceStore(this)
    }

    val services: TerritoryCardStudioServices by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TerritoryCardStudioServices(this)
    }
}
