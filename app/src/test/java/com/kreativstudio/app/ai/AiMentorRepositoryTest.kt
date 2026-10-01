package com.kreativstudio.app.ai

import com.kreativstudio.app.model.AiProcessingMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMentorRepositoryTest {
    @Test
    fun unconfiguredFirebaseKeepsOfflineMentorAvailable() = runTest {
        val repository = AiMentorRepository(firebaseReady = false)

        repository.refreshOnDeviceStatus()

        assertEquals(OnDeviceMentorPhase.LOCAL_FALLBACK, repository.onDeviceState.value.phase)
        assertTrue(repository.onDeviceState.value.detail.contains("offline studio coach"))
    }

    @Test
    fun unconfiguredFirebaseAnswersLocally() = runTest {
        val repository = AiMentorRepository(firebaseReady = false)

        val advice = repository.advise(
            prompt = "Help me check portrait proportions",
            project = null,
            preferLocal = true,
        ).getOrThrow()

        assertEquals(AiProcessingMode.ON_DEVICE, advice.processingMode)
        assertTrue(advice.actions.isNotEmpty())
    }
}
