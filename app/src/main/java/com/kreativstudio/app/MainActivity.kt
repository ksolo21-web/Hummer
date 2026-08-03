package com.kreativstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kreativstudio.app.ui.KreativAppStable
import com.kreativstudio.app.ui.KreativFramedStudioHost
import com.kreativstudio.app.ui.KreativViewModel
import com.kreativstudio.app.ui.KreativViewModelFactory
import com.kreativstudio.app.ui.StudioScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as KreativApplication
        setContent {
            val vm: KreativViewModel = viewModel(factory = KreativViewModelFactory(app.container))
            if (vm.screen == StudioScreen.STUDIO) {
                KreativFramedStudioHost(viewModel = vm)
            } else {
                KreativAppStable(viewModel = vm, activity = this)
            }
        }
    }
}
