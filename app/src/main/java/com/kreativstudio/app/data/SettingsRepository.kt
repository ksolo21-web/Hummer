package com.kreativstudio.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kreativstudio.app.model.AppSettings
import com.kreativstudio.app.model.StudioThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kreativ_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val highContrast = booleanPreferencesKey("high_contrast")
        val textScale = floatPreferencesKey("text_scale")
        val leftHanded = booleanPreferencesKey("left_handed")
        val autosave = intPreferencesKey("autosave_seconds")
        val aiLocal = booleanPreferencesKey("ai_local_first")
        val handHealth = booleanPreferencesKey("hand_health")
        val focusMode = booleanPreferencesKey("focus_mode")
        val symmetry = booleanPreferencesKey("symmetry")
        val perspective = booleanPreferencesKey("perspective")
        val palmRejection = booleanPreferencesKey("palm_rejection")
        val shapeSnap = booleanPreferencesKey("shape_snap")
        val kalebMessage = stringPreferencesKey("kaleb_message")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map(::fromPreferences)

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val updated = transform(fromPreferences(prefs))
            prefs[Keys.theme] = updated.themeId.name
            prefs[Keys.highContrast] = updated.highContrastText
            prefs[Keys.textScale] = updated.textScale
            prefs[Keys.leftHanded] = updated.leftHanded
            prefs[Keys.autosave] = updated.autosaveSeconds
            prefs[Keys.aiLocal] = updated.aiLocalFirst
            prefs[Keys.handHealth] = updated.handHealthReminders
            prefs[Keys.focusMode] = updated.focusMode
            prefs[Keys.symmetry] = updated.symmetryEnabled
            prefs[Keys.perspective] = updated.perspectiveGridEnabled
            prefs[Keys.palmRejection] = updated.palmRejectionEnabled
            prefs[Keys.shapeSnap] = updated.shapeSnapEnabled
            prefs[Keys.kalebMessage] = updated.fromKalebMessage
        }
    }

    private fun fromPreferences(prefs: androidx.datastore.preferences.core.Preferences): AppSettings = AppSettings(
        themeId = runCatching { StudioThemeId.valueOf(prefs[Keys.theme] ?: StudioThemeId.ROYAL_OWL.name) }
            .getOrDefault(StudioThemeId.ROYAL_OWL),
        highContrastText = prefs[Keys.highContrast] ?: true,
        textScale = prefs[Keys.textScale] ?: 1f,
        leftHanded = prefs[Keys.leftHanded] ?: false,
        autosaveSeconds = prefs[Keys.autosave] ?: 5,
        aiLocalFirst = prefs[Keys.aiLocal] ?: true,
        handHealthReminders = prefs[Keys.handHealth] ?: true,
        focusMode = prefs[Keys.focusMode] ?: false,
        symmetryEnabled = prefs[Keys.symmetry] ?: false,
        perspectiveGridEnabled = prefs[Keys.perspective] ?: false,
        palmRejectionEnabled = prefs[Keys.palmRejection] ?: true,
        shapeSnapEnabled = prefs[Keys.shapeSnap] ?: true,
        fromKalebMessage = prefs[Keys.kalebMessage]
            ?: "Your studio is ready. I believe in you and everything you create.",
    )
}
