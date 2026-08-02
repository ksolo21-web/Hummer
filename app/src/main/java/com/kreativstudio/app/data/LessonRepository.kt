package com.kreativstudio.app.data

import com.kreativstudio.app.model.Lesson
import com.kreativstudio.app.model.LessonCategory
import com.kreativstudio.app.model.LessonStep

class LessonRepository {
    val lessons: List<Lesson> = listOf(
        Lesson(
            id = "portrait-realism-01",
            title = "Real Human Portraits",
            subtitle = "Build likeness from gesture, structure, value, and edges.",
            category = LessonCategory.PORTRAIT,
            difficulty = 3,
            minutes = 45,
            steps = listOf(
                LessonStep("Gesture and tilt", "Place the head angle with one center line and one brow line before adding features.", "The head direction reads clearly at thumbnail size."),
                LessonStep("Skull structure", "Construct the cranium, jaw wedge, cheekbones, and eye sockets as simple forms.", "Both sides of the face follow the same perspective."),
                LessonStep("Feature landmarks", "Place eyes, nose, mouth, ears, and hairline using measured relationships, not symbols.", "Negative spaces and angles match the reference."),
                LessonStep("Value families", "Separate light and shadow into two clear families before blending.", "The portrait reads without outlines."),
                LessonStep("Edges and finish", "Sharpen focal edges and soften turning forms, preserving the artist's hand.", "The focal area is clear and skin still feels dimensional."),
            ),
        ),
        Lesson(
            id = "watercolor-realism-01",
            title = "Realistic Watercolor",
            subtitle = "Control water, pigment, glazing, blooms, lifting, and luminous edges.",
            category = LessonCategory.WATERCOLOR,
            difficulty = 2,
            minutes = 50,
            steps = listOf(
                LessonStep("Water map", "Decide which areas are dry, damp, or glossy wet before touching pigment.", "Edges behave intentionally rather than randomly."),
                LessonStep("First wash", "Lay the lightest large color family with one connected wash.", "The paper light remains visible."),
                LessonStep("Glaze", "Add transparent darker layers only after the first wash is dry.", "Color deepens without becoming muddy."),
                LessonStep("Blooms and textures", "Introduce controlled clean water where organic texture helps the subject.", "Blooms support form instead of fighting it."),
                LessonStep("Lift and accents", "Lift highlights and add a few concentrated dark accents at the end.", "The painting has a clear value range and breathing room."),
            ),
        ),
        Lesson(
            id = "figure-gesture-01",
            title = "Human Figure and Gesture",
            subtitle = "Draw believable movement before anatomy detail.",
            category = LessonCategory.HUMAN_FIGURE,
            difficulty = 2,
            minutes = 35,
            steps = listOf(
                LessonStep("Line of action", "Capture the pose with one directional rhythm.", "The gesture communicates movement without detail."),
                LessonStep("Rib cage and pelvis", "Place two masses with opposing tilts and a flexible connection.", "Weight and balance feel plausible."),
                LessonStep("Limbs", "Use tapered cylinders and joint landmarks to connect the pose.", "Limbs follow perspective and carry weight."),
                LessonStep("Contour economy", "Choose only contours that explain overlap and compression.", "The figure feels dimensional without scratchy searching."),
            ),
        ),
        Lesson(
            id = "perspective-01",
            title = "Perspective Without Fear",
            subtitle = "One-, two-, and three-point space using practical visual checks.",
            category = LessonCategory.PERSPECTIVE,
            difficulty = 2,
            minutes = 40,
            steps = listOf(
                LessonStep("Horizon", "Set eye level and identify which planes you can see.", "All objects share a believable eye level."),
                LessonStep("Vanishing systems", "Assign edges to the correct vanishing direction.", "Parallel world edges converge consistently."),
                LessonStep("Scale and spacing", "Use diagonals and repeated divisions to place objects in depth.", "Spacing compresses naturally with distance."),
                LessonStep("Organic forms", "Wrap perspective grids around trees, figures, and curved forms.", "The scene feels unified rather than mechanically boxed."),
            ),
        ),
        Lesson(
            id = "color-light-01",
            title = "Color and Light Mastery",
            subtitle = "Mix believable light, shadow, atmosphere, and skin tones.",
            category = LessonCategory.COLOR,
            difficulty = 3,
            minutes = 55,
            steps = listOf(
                LessonStep("Light identity", "Name the light's direction, softness, temperature, and strength.", "Every major shadow agrees with one light story."),
                LessonStep("Local color", "Estimate the object's color before light and atmosphere modify it.", "Materials remain distinguishable."),
                LessonStep("Temperature shifts", "Use relative warm and cool changes instead of adding white or black only.", "Form turns with richer color."),
                LessonStep("Atmosphere", "Reduce contrast, saturation, and detail with distance.", "Depth reads immediately."),
            ),
        ),
        Lesson(
            id = "line-form-01",
            title = "Confident Lines and Forms",
            subtitle = "Train control, rhythm, ellipses, boxes, and clean construction.",
            category = LessonCategory.FOUNDATIONS,
            difficulty = 1,
            minutes = 30,
            steps = listOf(
                LessonStep("Ghost the stroke", "Rehearse the motion above the canvas before committing one clean line.", "The line has a clear start, direction, and finish."),
                LessonStep("Ellipses in planes", "Draw through each ellipse and align its minor axis to the surface.", "Ellipses feel attached to the same solid form."),
                LessonStep("Boxes in space", "Use converging edge families and compare opposite planes.", "The box reads consistently without relying on outlines alone."),
                LessonStep("Organic construction", "Combine spheres, cylinders, and boxes into one believable object.", "Simple forms explain the object before details are added."),
            ),
        ),
        Lesson(
            id = "landscape-depth-01",
            title = "Atmospheric Landscapes",
            subtitle = "Compose depth, weather, foliage, water, and focal light.",
            category = LessonCategory.LANDSCAPE,
            difficulty = 2,
            minutes = 45,
            steps = listOf(
                LessonStep("Big value design", "Reduce the scene to three connected value groups.", "The composition reads at thumbnail size."),
                LessonStep("Depth layers", "Separate foreground, middle distance, and far distance with overlap and contrast.", "The eye moves through the scene naturally."),
                LessonStep("Organic variation", "Group foliage and texture into large families before adding accents.", "Nature feels varied without becoming noisy."),
                LessonStep("Water and sky", "Relate reflections to the sky and surrounding forms while keeping the water plane clear.", "Reflections support perspective and atmosphere."),
            ),
        ),
        Lesson(
            id = "animals-structure-01",
            title = "Animals From Gesture to Fur",
            subtitle = "Build living animal forms before surface detail.",
            category = LessonCategory.ANIMALS,
            difficulty = 2,
            minutes = 40,
            steps = listOf(
                LessonStep("Action and balance", "Capture spine rhythm, weight distribution, and direction first.", "The animal feels alive before anatomy detail."),
                LessonStep("Masses and joints", "Place rib cage, pelvis, skull, and limb joints as simple volumes.", "Limbs connect believably and carry weight."),
                LessonStep("Species landmarks", "Compare muzzle, ear, paw, shoulder, and hip relationships.", "The species reads without relying on fur pattern."),
                LessonStep("Fur economy", "Use directional groups and edge changes instead of drawing every hair.", "Fur describes form rather than flattening it."),
            ),
        ),
        Lesson(
            id = "mixed-media-01",
            title = "Mixed Media Storytelling",
            subtitle = "Combine ink, wash, texture, collage, and typography without visual clutter.",
            category = LessonCategory.MIXED_MEDIA,
            difficulty = 3,
            minutes = 50,
            steps = listOf(
                LessonStep("Visual hierarchy", "Choose one dominant medium and let the others support it.", "The focal idea remains obvious."),
                LessonStep("Texture library", "Import or capture real surfaces and assign each texture a purpose.", "Texture reinforces material, mood, or depth."),
                LessonStep("Unify the palette", "Repeat a limited color family across separate media.", "The piece feels intentional rather than assembled."),
                LessonStep("Type as shape", "Place words by rhythm, mass, and negative space before reading detail.", "Typography belongs to the composition."),
            ),
        ),
    )
}
