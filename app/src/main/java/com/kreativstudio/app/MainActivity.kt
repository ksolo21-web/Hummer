package com.kreativstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kreativstudio.app.ui.KreativAppStable
import com.kreativstudio.app.ui.KreativLessonWorkspaceHost
import com.kreativstudio.app.ui.KreativMentorV2Host
import com.kreativstudio.app.ui.KreativSketchbookStudioHost
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
            when {
                vm.screen == StudioScreen.STUDIO && vm.currentProject?.lessonId != null -> {
                    KreativLessonWorkspaceHost(viewModel = vm, activity = this)
                }
                vm.screen == StudioScreen.STUDIO && vm.currentProject != null -> {
                    KreativSketchbookStudioHost(viewModel = vm, activity = this)
                }
                vm.screen == StudioScreen.MENTOR -> {
                    KreativMentorV2Host(viewModel = vm, activity = this)
                }
                else -> {
                    // Keep the normal application chrome active when Studio is selected
                    // without an open project. KreativAppStable then presents its explicit
                    // no-canvas recovery screen instead of routing into an empty canvas host.
                    KreativAppStable(viewModel = vm, activity = this)
                }
            }
        }
    }
}
