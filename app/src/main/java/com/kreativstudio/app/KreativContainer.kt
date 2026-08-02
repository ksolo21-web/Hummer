package com.kreativstudio.app

import android.content.Context
import com.kreativstudio.app.ai.AiMentorRepository
import com.kreativstudio.app.auth.AuthRepository
import com.kreativstudio.app.cloud.CloudSyncRepository
import com.kreativstudio.app.data.FileProjectRepository
import com.kreativstudio.app.data.LessonProgressRepository
import com.kreativstudio.app.data.LessonRepository
import com.kreativstudio.app.data.ProjectRepository
import com.kreativstudio.app.data.SettingsRepository
import com.kreativstudio.app.export.ProjectExporter

class KreativContainer(context: Context, firebaseReady: Boolean) {
    val settingsRepository = SettingsRepository(context)
    val projectRepository: ProjectRepository = FileProjectRepository(context)
    val lessonRepository = LessonRepository()
    val lessonProgressRepository = LessonProgressRepository(context)
    val authRepository = AuthRepository(context, firebaseReady)
    val cloudSyncRepository = CloudSyncRepository(context, firebaseReady)
    val aiMentorRepository = AiMentorRepository(firebaseReady)
    val projectExporter = ProjectExporter(context)
}
