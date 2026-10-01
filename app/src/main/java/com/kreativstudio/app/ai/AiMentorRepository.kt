package com.kreativstudio.app.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ai
import com.google.firebase.ai.ondevice.DownloadStatus
import com.google.firebase.ai.ondevice.FirebaseAIOnDevice
import com.google.firebase.ai.ondevice.OnDeviceModelStatus
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.kreativstudio.app.BuildConfig
import com.kreativstudio.app.model.AiAdvice
import com.kreativstudio.app.model.AiProcessingMode
import com.kreativstudio.app.model.KreativProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OnDeviceMentorPhase {
    CHECKING,
    AVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    UNSUPPORTED,
    LOCAL_FALLBACK,
    ERROR,
}

data class OnDeviceMentorState(
    val phase: OnDeviceMentorPhase,
    val detail: String,
    val bytesDownloaded: Long = 0L,
    val bytesToDownload: Long? = null,
)

class AiMentorRepository(private val firebaseReady: Boolean) {
    private val mutableOnDeviceState = MutableStateFlow(
        if (firebaseReady) {
            OnDeviceMentorState(
                phase = OnDeviceMentorPhase.CHECKING,
                detail = "Checking this device for Gemini Nano support.",
            )
        } else {
            localFallbackState()
        }
    )

    val onDeviceState: StateFlow<OnDeviceMentorState> = mutableOnDeviceState.asStateFlow()

    @Suppress("DEPRECATION")
    suspend fun refreshOnDeviceStatus() {
        if (!firebaseReady) {
            mutableOnDeviceState.value = localFallbackState()
            return
        }

        mutableOnDeviceState.value = OnDeviceMentorState(
            phase = OnDeviceMentorPhase.CHECKING,
            detail = "Checking this device for Gemini Nano support.",
        )
        runCatching { FirebaseAIOnDevice.checkStatus() }
            .onSuccess { status -> mutableOnDeviceState.value = status.toMentorState() }
            .onFailure { error ->
                mutableOnDeviceState.value = OnDeviceMentorState(
                    phase = OnDeviceMentorPhase.ERROR,
                    detail = error.message ?: "Gemini Nano status could not be checked. The offline studio coach is still ready.",
                )
            }
    }

    @Suppress("DEPRECATION")
    suspend fun downloadOnDeviceModel() {
        if (!firebaseReady) {
            mutableOnDeviceState.value = localFallbackState()
            return
        }

        when (val status = runCatching { FirebaseAIOnDevice.checkStatus() }.getOrElse { error ->
            mutableOnDeviceState.value = OnDeviceMentorState(
                phase = OnDeviceMentorPhase.ERROR,
                detail = error.message ?: "Gemini Nano could not be prepared. The offline studio coach is still ready.",
            )
            return
        }) {
            OnDeviceModelStatus.AVAILABLE -> {
                mutableOnDeviceState.value = status.toMentorState()
            }
            OnDeviceModelStatus.DOWNLOADABLE -> {
                FirebaseAIOnDevice.download().collect { download ->
                    mutableOnDeviceState.value = when (download) {
                        is DownloadStatus.DownloadStarted -> OnDeviceMentorState(
                            phase = OnDeviceMentorPhase.DOWNLOADING,
                            detail = "Downloading the private on-device teaching model.",
                            bytesToDownload = download.bytesToDownload,
                        )
                        is DownloadStatus.DownloadInProgress -> {
                            val previous = mutableOnDeviceState.value
                            previous.copy(
                                phase = OnDeviceMentorPhase.DOWNLOADING,
                                detail = "Downloading the private on-device teaching model.",
                                bytesDownloaded = download.totalBytesDownloaded,
                            )
                        }
                        is DownloadStatus.DownloadCompleted -> OnDeviceMentorState(
                            phase = OnDeviceMentorPhase.AVAILABLE,
                            detail = "Gemini Nano is ready for private, offline-capable coaching.",
                        )
                        is DownloadStatus.DownloadFailed -> OnDeviceMentorState(
                            phase = OnDeviceMentorPhase.ERROR,
                            detail = "Gemini Nano could not finish downloading. The offline studio coach is still ready.",
                        )
                        else -> OnDeviceMentorState(
                            phase = OnDeviceMentorPhase.ERROR,
                            detail = "Gemini Nano returned an unknown download state. The offline studio coach is still ready.",
                        )
                    }
                }
            }
            OnDeviceModelStatus.DOWNLOADING -> {
                mutableOnDeviceState.value = status.toMentorState()
            }
            OnDeviceModelStatus.UNAVAILABLE -> {
                mutableOnDeviceState.value = status.toMentorState()
            }
        }
    }

    suspend fun advise(
        prompt: String,
        project: KreativProject?,
        preferLocal: Boolean,
    ): Result<AiAdvice> = runCatching {
        val trimmed = prompt.trim()
        require(trimmed.isNotEmpty()) { "Ask KREATIV Mentor a question first." }

        when {
            !firebaseReady -> localAdvice(trimmed, project)
            preferLocal -> runCatching { hybridAdvice(trimmed, project) }
                .getOrElse { localAdvice(trimmed, project) }
            else -> runCatching { cloudAdvice(trimmed, project) }
                .getOrElse { localAdvice(trimmed, project) }
        }
    }

    @Suppress("DEPRECATION")
    private fun OnDeviceModelStatus.toMentorState(): OnDeviceMentorState = when (this) {
        OnDeviceModelStatus.AVAILABLE -> OnDeviceMentorState(
            phase = OnDeviceMentorPhase.AVAILABLE,
            detail = "Gemini Nano is ready for private, offline-capable coaching.",
        )
        OnDeviceModelStatus.DOWNLOADABLE -> OnDeviceMentorState(
            phase = OnDeviceMentorPhase.DOWNLOADABLE,
            detail = "Gemini Nano is supported and can be downloaded to this device.",
        )
        OnDeviceModelStatus.DOWNLOADING -> OnDeviceMentorState(
            phase = OnDeviceMentorPhase.DOWNLOADING,
            detail = "The on-device teaching model is being downloaded by Android.",
        )
        OnDeviceModelStatus.UNAVAILABLE -> OnDeviceMentorState(
            phase = OnDeviceMentorPhase.UNSUPPORTED,
            detail = "Gemini Nano is not supported on this device. The built-in offline studio coach remains available.",
        )
        else -> OnDeviceMentorState(
            phase = OnDeviceMentorPhase.ERROR,
            detail = "Gemini Nano returned an unknown status. The built-in offline studio coach remains available.",
        )
    }

    private fun localFallbackState() = OnDeviceMentorState(
        phase = OnDeviceMentorPhase.LOCAL_FALLBACK,
        detail = "The built-in offline studio coach is ready. Private Firebase setup enables Gemini Nano and cloud coaching.",
    )

    private fun localAdvice(prompt: String, project: KreativProject?): AiAdvice {
        val lower = prompt.lowercase()
        val context = project?.let {
            "Your current project has ${it.elements.size} marks across ${it.layers.size} layers."
        } ?: "Start with a small study so the feedback stays focused."

        return when {
            "water" in lower || "wash" in lower || "paint" in lower -> AiAdvice(
                title = "Water and pigment control",
                explanation = "$context Work from the lightest connected wash toward selective dark accents. Let each layer dry before glazing unless you intentionally want a soft bloom.",
                actions = listOf(
                    "Map dry, damp, and glossy-wet areas before adding pigment.",
                    "Keep one clean edge near the focal point and soften less important edges.",
                    "Test the next glaze on a scrap swatch before applying it.",
                ),
                processingMode = AiProcessingMode.ON_DEVICE,
            )
            "face" in lower || "portrait" in lower || "human" in lower || "anatom" in lower -> AiAdvice(
                title = "Build structure before detail",
                explanation = "$context Check the head tilt, center line, brow line, jaw width, and the relationship between the eye sockets before refining lashes, lips, or hair.",
                actions = listOf(
                    "Squint at the reference and separate the face into light and shadow families.",
                    "Compare negative spaces beside the nose, jaw, and neck.",
                    "Correct the largest angle error before touching small features.",
                ),
                processingMode = AiProcessingMode.ON_DEVICE,
            )
            "perspective" in lower || "building" in lower || "line" in lower -> AiAdvice(
                title = "Make the space agree",
                explanation = "$context Establish eye level first, then group world-parallel edges into consistent vanishing directions. Perfect lines only help when the perspective system is correct.",
                actions = listOf(
                    "Turn on the perspective guide and identify the horizon.",
                    "Check the longest structural edges before adding detail.",
                    "Use repeated spacing that compresses with distance.",
                ),
                processingMode = AiProcessingMode.ON_DEVICE,
            )
            "color" in lower || "light" in lower || "shadow" in lower -> AiAdvice(
                title = "Clarify the light story",
                explanation = "$context Name the light direction, softness, strength, and temperature. Keep every major cast shadow consistent with that single decision.",
                actions = listOf(
                    "Reduce the image to three value groups.",
                    "Compare warm and cool relationships instead of adding black or white alone.",
                    "Reserve the strongest contrast for the focal area.",
                ),
                processingMode = AiProcessingMode.ON_DEVICE,
            )
            else -> AiAdvice(
                title = "Three-step studio check",
                explanation = "$context Improve the artwork from large decisions to small ones: composition first, then value structure, then edges and detail.",
                actions = listOf(
                    "Flip or mirror the canvas to expose proportion errors.",
                    "View the work at thumbnail size and identify the focal point.",
                    "Make one deliberate correction, then reassess before adding more marks.",
                ),
                processingMode = AiProcessingMode.ON_DEVICE,
            )
        }
    }


    @OptIn(PublicPreviewAPI::class)
    private suspend fun hybridAdvice(prompt: String, project: KreativProject?): AiAdvice {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = BuildConfig.FIREBASE_AI_MODEL,
                onDeviceConfig = OnDeviceConfig(mode = InferenceMode.PREFER_ON_DEVICE),
            )
        val response = model.generateContent(mentorPrompt(prompt, project))
        val text = response.text?.trim().orEmpty().ifBlank {
            return localAdvice(prompt, project)
        }
        val usedDeviceModel = response.inferenceSource.toString().contains("ON_DEVICE", ignoreCase = true)
        return AiAdvice(
            title = if (usedDeviceModel) "KREATIV Mentor • On device" else "KREATIV Mentor • Cloud fallback",
            explanation = text,
            actions = studioActions(),
            processingMode = if (usedDeviceModel) AiProcessingMode.ON_DEVICE else AiProcessingMode.HYBRID,
        )
    }

    private suspend fun cloudAdvice(prompt: String, project: KreativProject?): AiAdvice {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(BuildConfig.FIREBASE_AI_MODEL)
        val response = model.generateContent(mentorPrompt(prompt, project))
        val text = response.text?.trim().orEmpty().ifBlank {
            "The cloud mentor returned no text. Use the offline mentor and try again later."
        }
        return AiAdvice(
            title = "KREATIV Mentor",
            explanation = text,
            actions = studioActions(),
            processingMode = AiProcessingMode.CLOUD,
        )
    }

    private fun mentorPrompt(prompt: String, project: KreativProject?): String {
        val projectContext = project?.let {
            "Project title: ${it.title}; canvas ${it.widthPx}x${it.heightPx}; ${it.layers.size} layers; ${it.elements.size} marks; lesson: ${it.lessonId ?: "none"}."
        } ?: "No project is open."
        return """
            You are KREATIV Mentor, a warm but honest professional art teacher. Preserve the artist's style. Never claim to see details that were not supplied. Give one concise explanation and three practical actions. Do not offer to replace the artist's work.
            $projectContext
            Artist question: $prompt
        """.trimIndent()
    }

    private fun studioActions(): List<String> = listOf(
        "Apply one suggestion on a duplicate layer.",
        "Compare before and after at thumbnail size.",
        "Keep the version that best supports your intent.",
    )

}
