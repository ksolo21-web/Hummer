package com.koenterprises.territorycardstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TerritoryCardStudioApplication
        setContent {
            TerritoryCardStudioProductionApp(
                knowledgeBase = app.services.knowledgeBase,
                appearanceStore = app.appearancePreferences
            )
        }
    }
}
