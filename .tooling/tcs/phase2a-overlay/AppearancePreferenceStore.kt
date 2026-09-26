package com.koenterprises.territorycardstudio

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

enum class AppearanceMode(val storedValue: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromStoredValue(value: String?): AppearanceMode =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

class AppearancePreferenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableMode = mutableStateOf(AppearanceMode.fromStoredValue(preferences.getString(KEY_APPEARANCE_MODE, null)))

    val mode: State<AppearanceMode> get() = mutableMode

    fun setMode(mode: AppearanceMode) {
        if (mutableMode.value == mode) return
        check(preferences.edit().putString(KEY_APPEARANCE_MODE, mode.storedValue).commit()) {
            "Unable to persist appearance preference"
        }
        mutableMode.value = mode
    }

    companion object {
        internal const val PREFERENCES_NAME = "territory-card-studio-ui-v1"
        internal const val KEY_APPEARANCE_MODE = "appearance_mode"
    }
}
