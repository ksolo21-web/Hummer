package com.kreativstudio.app.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectModelTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun textElementSurvivesProjectRoundTrip() {
        val layer = CanvasLayer(name = "Typography")
        val project = KreativProject(
            title = "Olivia's Study",
            layers = listOf(layer),
            activeLayerId = layer.id,
            elements = listOf(
                CanvasElement(
                    layerId = layer.id,
                    tool = ToolType.TEXT,
                    points = listOf(StrokePoint(120f, 240f)),
                    colorArgb = 0xFFE0B16F,
                    width = 72f,
                    text = "Create boldly",
                )
            ),
        )
        val restored = json.decodeFromString<KreativProject>(json.encodeToString(project))
        assertEquals("Create boldly", restored.elements.single().text)
        assertEquals(ToolType.TEXT, restored.elements.single().tool)
    }

    @Test
    fun everyStudioThemeIsDarkOwlTheme() {
        assertEquals(5, StudioThemeId.entries.size)
        assertTrue(StudioThemeId.entries.all { it.name.contains("OWL") || it == StudioThemeId.MOONFEATHER || it == StudioThemeId.FOREST_NOCTURNE })
    }
}
