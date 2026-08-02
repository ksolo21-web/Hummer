package com.kreativstudio.app.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AppUser(
    val uid: String,
    val displayName: String,
    val email: String? = null,
    val photoUrl: String? = null,
    val isOliviaOwner: Boolean = false,
    val isLocalPreview: Boolean = false,
)

@Serializable
enum class StudioThemeId {
    MIDNIGHT_OWL,
    EMBER_OWL,
    MOONFEATHER,
    FOREST_NOCTURNE,
    ROYAL_OWL,
}

@Serializable
enum class ToolType {
    PEN,
    PENCIL,
    WATERCOLOR,
    CHARCOAL,
    MARKER,
    ERASER,
    SMUDGE,
    LINE,
    RECTANGLE,
    ELLIPSE,
    TRIANGLE,
    POLYGON,
    STAR,
    ARC,
    ARROW,
    SELECT,
    TEXT,
    FILL,
}

@Serializable
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val tilt: Float = 0f,
    val orientation: Float = 0f,
    val timeMillis: Long = 0L,
)

@Serializable
data class CanvasElement(
    val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val tool: ToolType,
    val points: List<StrokePoint>,
    val colorArgb: Long,
    val width: Float,
    val opacity: Float = 1f,
    val stabilization: Float = 0.25f,
    val text: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class CanvasLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val opacity: Float = 1f,
)

@Serializable
data class ProjectAttachment(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val displayName: String,
    val mimeType: String? = null,
    val kind: AttachmentKind = AttachmentKind.REFERENCE,
    val addedAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class AttachmentKind { REFERENCE, TEXTURE, DOCUMENT, VIDEO, AUDIO, OTHER }

@Serializable
data class JournalEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val audioUri: String? = null,
)

@Serializable
data class BrushPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val tool: ToolType,
    val width: Float,
    val opacity: Float,
    val stabilization: Float,
    val textureUri: String? = null,
)

@Serializable
data class KreativProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled Artwork",
    val widthPx: Int = 2048,
    val heightPx: Int = 2048,
    val backgroundArgb: Long = 0xFFFFFFFF,
    val layers: List<CanvasLayer> = listOf(CanvasLayer(name = "Sketch")),
    val activeLayerId: String = layers.first().id,
    val elements: List<CanvasElement> = emptyList(),
    val attachments: List<ProjectAttachment> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    val brushPresets: List<BrushPreset> = defaultBrushPresets(),
    val lessonId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

@Serializable
enum class SyncState { LOCAL_ONLY, PENDING, SYNCED, ERROR }

@Serializable
data class Lesson(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: LessonCategory,
    val difficulty: Int,
    val minutes: Int,
    val steps: List<LessonStep>,
    val offlineAvailable: Boolean = true,
)

@Serializable
enum class LessonCategory {
    FOUNDATIONS,
    PORTRAIT,
    HUMAN_FIGURE,
    WATERCOLOR,
    COLOR,
    PERSPECTIVE,
    LANDSCAPE,
    ANIMALS,
    MIXED_MEDIA,
}

@Serializable
data class LessonStep(
    val title: String,
    val instruction: String,
    val checkpoint: String,
)

@Serializable
data class LessonProgress(
    val lessonId: String,
    val completedSteps: Int = 0,
    val attempts: Int = 0,
    val lastOpenedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class AiAdvice(
    val title: String,
    val explanation: String,
    val actions: List<String>,
    val processingMode: AiProcessingMode,
)

@Serializable
enum class AiProcessingMode { ON_DEVICE, CLOUD, HYBRID }

@Serializable
data class AppSettings(
    val themeId: StudioThemeId = StudioThemeId.ROYAL_OWL,
    val highContrastText: Boolean = true,
    val textScale: Float = 1f,
    val leftHanded: Boolean = false,
    val autosaveSeconds: Int = 5,
    val aiLocalFirst: Boolean = true,
    val handHealthReminders: Boolean = true,
    val focusMode: Boolean = false,
    val symmetryEnabled: Boolean = false,
    val perspectiveGridEnabled: Boolean = false,
    val palmRejectionEnabled: Boolean = true,
    val shapeSnapEnabled: Boolean = true,
    val fromKalebMessage: String = "Your studio is ready. I believe in you and everything you create.",
)

fun defaultBrushPresets(): List<BrushPreset> = listOf(
    BrushPreset(name = "Silk Ink", tool = ToolType.PEN, width = 8f, opacity = 1f, stabilization = .35f),
    BrushPreset(name = "Soft Graphite", tool = ToolType.PENCIL, width = 11f, opacity = .82f, stabilization = .18f),
    BrushPreset(name = "Royal Wash", tool = ToolType.WATERCOLOR, width = 42f, opacity = .28f, stabilization = .2f),
    BrushPreset(name = "Velvet Charcoal", tool = ToolType.CHARCOAL, width = 24f, opacity = .72f, stabilization = .12f),
    BrushPreset(name = "Studio Marker", tool = ToolType.MARKER, width = 20f, opacity = .68f, stabilization = .28f),
)
