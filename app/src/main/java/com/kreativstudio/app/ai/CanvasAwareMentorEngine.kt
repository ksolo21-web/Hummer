package com.kreativstudio.app.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.content
import com.kreativstudio.app.BuildConfig
import com.kreativstudio.app.model.KreativProject
import com.kreativstudio.app.model.LessonMastery

data class CanvasMentorResult(
    val title: String,
    val explanation: String,
    val actions: List<String>,
    val sourceLabel: String,
    val sawCanvas: Boolean,
    val mastery: LessonMastery = LessonMastery.NOT_ASSESSED,
)

object CanvasAwareMentorEngine {
    private val masteryLine = Regex("(?im)^MASTERY:\\s*(READY|REVISE)\\s*$")

    @OptIn(PublicPreviewAPI::class)
    suspend fun analyze(
        project: KreativProject?,
        artistRequest: String,
        preferOnDevice: Boolean,
        lessonContext: String? = null,
    ): CanvasMentorResult {
        val request = artistRequest.trim().ifBlank {
            "Analyze the current artwork and identify the three most important improvements."
        }
        val bitmap = project?.let { ProjectBitmapRenderer.render(it) }
        val promptText = buildPrompt(project, request, lessonContext, bitmap != null)

        return try {
            val model = if (preferOnDevice) {
                Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                    modelName = BuildConfig.FIREBASE_AI_MODEL,
                    onDeviceConfig = OnDeviceConfig(mode = InferenceMode.PREFER_ON_DEVICE),
                )
            } else {
                Firebase.ai(backend = GenerativeBackend.googleAI())
                    .generativeModel(modelName = BuildConfig.FIREBASE_AI_MODEL)
            }
            val prompt = content {
                bitmap?.let { image(it) }
                text(promptText)
            }
            val response = model.generateContent(prompt)
            val rawText = response.text?.trim().orEmpty()
            if (rawText.isBlank()) {
                offlineResult(project, request, lessonContext, "The AI service returned no usable response.")
            } else {
                val mastery = when {
                    lessonContext == null -> LessonMastery.NOT_ASSESSED
                    masteryLine.find(rawText)?.groupValues?.getOrNull(1).equals("READY", ignoreCase = true) ->
                        LessonMastery.READY_TO_ADVANCE
                    else -> LessonMastery.NEEDS_PRACTICE
                }
                val cleanedText = rawText.replace(masteryLine, "").trim().ifBlank { rawText }
                CanvasMentorResult(
                    title = if (lessonContext == null) "Canvas-aware KREATIV Mentor" else "Lesson check",
                    explanation = cleanedText,
                    actions = listOf(
                        "Apply only the highest-priority correction first.",
                        "Compare the result at full size and thumbnail size.",
                        "Ask the Mentor to re-check the updated canvas.",
                    ),
                    sourceLabel = if (preferOnDevice) "Private local-first multimodal analysis" else "Cloud multimodal analysis",
                    sawCanvas = bitmap != null,
                    mastery = mastery,
                )
            }
        } catch (error: Throwable) {
            offlineResult(project, request, lessonContext, error.message)
        } finally {
            bitmap?.recycle()
        }
    }

    private fun buildPrompt(
        project: KreativProject?,
        request: String,
        lessonContext: String?,
        hasImage: Boolean,
    ): String = buildString {
        appendLine("You are KREATIV Mentor, a warm, exacting professional art teacher.")
        appendLine("Protect the artist's voice. Do not replace the work or flatter without evidence.")
        appendLine("Analyze from large structure to small detail: composition, proportion, perspective, values, color, edges, and technique.")
        appendLine("State what is working, identify the three highest-impact corrections in order, explain why each matters, and give one short practice drill.")
        appendLine("When an artwork image is supplied, refer to visible evidence and specific canvas regions. When it is not supplied, clearly say that visual inspection was unavailable.")
        lessonContext?.let {
            appendLine("Current lesson objective and rubric: $it")
            appendLine("Begin the response with exactly one separate line: MASTERY: READY or MASTERY: REVISE.")
            appendLine("Use READY only when visible evidence satisfies the stated checkpoint. A blank, minimal, unclear, or uninspectable canvas must be REVISE.")
        }
        project?.let {
            appendLine("Project: ${it.title}; ${it.widthPx} x ${it.heightPx}; ${it.layers.size} layers; ${it.elements.size} marks; ${it.attachments.size} references.")
        }
        appendLine("Artwork image supplied: $hasImage")
        appendLine("Artist request: $request")
        appendLine("Use readable headings and direct instructions. Keep the response focused enough to follow while drawing.")
    }

    private fun offlineResult(
        project: KreativProject?,
        request: String,
        lessonContext: String?,
        error: String?,
    ): CanvasMentorResult {
        val marks = project?.elements?.size ?: 0
        val context = when {
            project == null -> "No canvas is currently open."
            marks == 0 -> "The open canvas is still blank, so visual correction is not possible yet."
            marks < 8 -> "The study has only $marks marks, so concentrate on the largest construction decision before detail."
            else -> "The study contains $marks marks across ${project.layers.size} layers."
        }
        val lower = request.lowercase()
        val focus = when {
            "portrait" in lower || "face" in lower || "anatom" in lower -> "Check head tilt, center line, brow line, jaw width, eye-socket placement, and the separation of light and shadow before refining features."
            "water" in lower || "wash" in lower -> "Separate dry, damp, and wet zones; preserve the lightest paper; and delay dark glazing until the previous wash is dry."
            "perspective" in lower -> "Confirm eye level first, then verify that each family of parallel edges converges consistently."
            "color" in lower || "light" in lower || "value" in lower -> "Reduce the work to three value groups and reserve the strongest contrast for the focal area."
            else -> "Check composition at thumbnail size, then correct proportion and value structure before edges or detail."
        }
        return CanvasMentorResult(
            title = "Offline Basic Coach",
            explanation = "$context $focus The multimodal model was unavailable${error?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "."}",
            actions = listOf(
                "Make one large structural correction.",
                "View the canvas at thumbnail size.",
                "Retry canvas-aware analysis when the local or cloud model is available.",
            ),
            sourceLabel = "Offline rules-based fallback — not visual AI analysis",
            sawCanvas = false,
            mastery = if (lessonContext == null) LessonMastery.NOT_ASSESSED else LessonMastery.NEEDS_PRACTICE,
        )
    }
}
