from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_exact(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"Expected source block not found in {path}")
    path.write_text(text.replace(old, new))


studio = ROOT / "app/src/main/java/com/kreativstudio/app/ui/KreativSketchbookStudio.kt"

replace_exact(
    studio,
    '''            Row(
                modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ColorDot(viewModel.activeColorArgb, onMore)
                Text(
                    viewModel.activeTool.displayName(),
                    style = MaterialTheme.typography.labelLarge,
                    color = HudOnSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SizeStepper(viewModel)
                ActionIcon(Icons.Default.MoreHoriz, "More controls", onMore)
                ActionIcon(Icons.Default.VisibilityOff, "Hide controls", onHide)
            }
''',
    '''            Row(
                modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (viewModel.activeTool.showsColorControl()) {
                    ColorDot(viewModel.activeColorArgb, onMore)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        viewModel.activeTool.displayName(),
                        style = MaterialTheme.typography.labelLarge,
                        color = HudOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        viewModel.activeTool.hudHint(
                            opacity = viewModel.brushOpacity,
                            inputStatus = viewModel.inputStatus,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = HudMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (viewModel.activeTool.usesSizeControl()) {
                    SizeStepper(viewModel)
                }
                ActionIcon(Icons.Default.MoreHoriz, "More controls", onMore)
                ActionIcon(Icons.Default.VisibilityOff, "Hide controls", onHide)
            }
''',
)

replace_exact(
    studio,
    '''        Row(
            modifier = Modifier.height(58.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorDot(viewModel.activeColorArgb, onMore)
            Column(Modifier.widthIn(min = 96.dp, max = 190.dp)) {
                Text(
                    viewModel.activeTool.displayName(),
                    style = MaterialTheme.typography.labelLarge,
                    color = HudOnSurface,
                )
                Text(
                    viewModel.activeTool.hudHint(viewModel.brushOpacity),
                    style = MaterialTheme.typography.labelSmall,
                    color = HudMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SizeStepper(viewModel)
            ActionIcon(Icons.Default.MoreHoriz, "More controls", onMore)
            ActionIcon(Icons.Default.VisibilityOff, "Hide controls", onHide)
        }
''',
    '''        Row(
            modifier = Modifier.height(62.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (viewModel.activeTool.showsColorControl()) {
                ColorDot(viewModel.activeColorArgb, onMore)
            }
            Column(Modifier.widthIn(min = 150.dp, max = 260.dp)) {
                Text(
                    viewModel.activeTool.displayName(),
                    style = MaterialTheme.typography.labelLarge,
                    color = HudOnSurface,
                )
                Text(
                    viewModel.activeTool.hudHint(
                        opacity = viewModel.brushOpacity,
                        inputStatus = viewModel.inputStatus,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = HudMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (viewModel.activeTool.usesSizeControl()) {
                SizeStepper(viewModel)
            }
            ActionIcon(Icons.Default.MoreHoriz, "More controls", onMore)
            ActionIcon(Icons.Default.VisibilityOff, "Hide controls", onHide)
        }
''',
)

replace_exact(
    studio,
    '''        IconButton(
            onClick = {
                viewModel.activeTool = tool
                if (tool == ToolType.SELECT) {
                    viewModel.showMessage("Select / Move: tap a visible stroke, shape, or text, then drag it.")
                }
            },
        ) {
''',
    '''        IconButton(
            onClick = { activateTool(viewModel, tool) },
        ) {
''',
)

replace_exact(
    studio,
    '''                    FilterChip(
                        selected = viewModel.activeTool == tool,
                        onClick = { viewModel.activeTool = tool },
                        label = { Text(tool.displayName()) },
                        leadingIcon = { Icon(tool.icon(), null, Modifier.size(18.dp)) },
                    )
''',
    '''                    FilterChip(
                        selected = viewModel.activeTool == tool,
                        onClick = { activateTool(viewModel, tool) },
                        label = { Text(tool.displayName()) },
                        leadingIcon = { Icon(tool.icon(), null, Modifier.size(18.dp)) },
                    )
''',
)

replace_exact(
    studio,
    '''        item {
            Text("Color", style = MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(palette) { value ->
                    val selected = viewModel.activeColorArgb == value
                    Surface(
                        modifier = Modifier
                            .size(if (selected) 48.dp else 42.dp)
                            .clickable { viewModel.activeColorArgb = value },
                        shape = CircleShape,
                        color = Color(value),
                        border = BorderStroke(
                            if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        ),
                    ) {}
                }
            }
        }
        item {
            Text("Brush response", style = MaterialTheme.typography.titleLarge)
            Text("Size ${viewModel.brushWidth.toInt()} px")
            Slider(
                value = viewModel.brushWidth,
                onValueChange = { viewModel.brushWidth = it },
                valueRange = 1f..180f,
            )
            Text("Opacity ${(viewModel.brushOpacity * 100).toInt()}%")
            Slider(
                value = viewModel.brushOpacity,
                onValueChange = { viewModel.brushOpacity = it },
                valueRange = .05f..1f,
            )
            Text("Stabilization ${(viewModel.stabilization * 100).toInt()}%")
            Slider(
                value = viewModel.stabilization,
                onValueChange = { viewModel.stabilization = it },
                valueRange = 0f..0.95f,
            )
        }
''',
    '''        if (viewModel.activeTool.showsColorControl()) {
            item {
                Text("Color", style = MaterialTheme.typography.titleLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(palette) { value ->
                        val selected = viewModel.activeColorArgb == value
                        Surface(
                            modifier = Modifier
                                .size(if (selected) 48.dp else 42.dp)
                                .clickable { viewModel.activeColorArgb = value },
                            shape = CircleShape,
                            color = Color(value),
                            border = BorderStroke(
                                if (selected) 3.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                            ),
                        ) {}
                    }
                }
            }
        }
        if (viewModel.activeTool.usesSizeControl()) {
            item {
                Text("Brush response", style = MaterialTheme.typography.titleLarge)
                Text("Size ${viewModel.brushWidth.toInt()} px")
                Slider(
                    value = viewModel.brushWidth,
                    onValueChange = { viewModel.brushWidth = it },
                    valueRange = 1f..180f,
                )
                Text("Opacity ${(viewModel.brushOpacity * 100).toInt()}%")
                Slider(
                    value = viewModel.brushOpacity,
                    onValueChange = { viewModel.brushOpacity = it },
                    valueRange = .05f..1f,
                )
                Text("Stabilization ${(viewModel.stabilization * 100).toInt()}%")
                Slider(
                    value = viewModel.stabilization,
                    onValueChange = { viewModel.stabilization = it },
                    valueRange = 0f..0.95f,
                )
            }
        } else if (viewModel.activeTool == ToolType.SELECT) {
            item {
                Text("Select / Move", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Tap a visible, unlocked stroke, shape, or text object. A gold dashed box confirms the selection; drag it to move the object.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
''',
)

replace_exact(
    studio,
    '''private fun ToolType.hudHint(opacity: Float): String = when (this) {
    ToolType.SELECT -> "Tap an object • drag to move"
    ToolType.ERASER -> "Drag across marks to erase"
    ToolType.TEXT -> "Tap canvas to place text"
    else -> "Opacity ${(opacity * 100).toInt()}%"
}
''',
    '''private fun activateTool(viewModel: KreativViewModel, tool: ToolType) {
    viewModel.activeTool = tool
    viewModel.inputStatus = tool.initialInputStatus()
    if (tool == ToolType.SELECT) {
        viewModel.showMessage("Select / Move: tap a visible, unlocked object, then drag it.")
    }
}

private fun ToolType.initialInputStatus(): String = when (this) {
    ToolType.SELECT -> "Select / Move • tap an object"
    ToolType.ERASER -> "Eraser • drag across marks"
    ToolType.TEXT -> "Text • tap the canvas"
    ToolType.FILL -> "Fill • tap the canvas"
    else -> "${displayName()} • ready"
}

private fun ToolType.hudHint(opacity: Float, inputStatus: String): String = when (this) {
    ToolType.SELECT -> inputStatus.takeIf { it.startsWith("Select") }
        ?: "Select / Move • tap an object"
    ToolType.ERASER -> "Drag across marks to erase"
    ToolType.TEXT -> "Tap canvas to place text"
    ToolType.FILL -> "Tap canvas to fill the background"
    else -> "Opacity ${(opacity * 100).toInt()}%"
}

private fun ToolType.showsColorControl(): Boolean = this !in setOf(
    ToolType.SELECT,
    ToolType.ERASER,
)

private fun ToolType.usesSizeControl(): Boolean = this !in setOf(
    ToolType.SELECT,
    ToolType.FILL,
)
''',
)

canvas = ROOT / "app/src/main/java/com/kreativstudio/app/ui/canvas/KreativCanvasView.kt"
replace_exact(
    canvas,
    '''                if (activeTool == ToolType.SELECT) {
                    beginSelection(event)
                    onInputStatus(if (selectedElementId == null) "Select • tap an object" else "Select • drag to move")
                    invalidate()
                    return true
                }
''',
    '''                if (activeTool == ToolType.SELECT) {
                    beginSelection(event)
                    onInputStatus(
                        if (selectedElementId == null) "Select / Move • no object here"
                        else "Select / Move • selected • drag to move",
                    )
                    invalidate()
                    return true
                }
''',
)
replace_exact(
    canvas,
    '''    private fun finishSelection(event: MotionEvent) {
        updateSelection(event)
        val original = selectionOriginal
        val updated = selectionPreview
        if (original != null && updated != null && updated.points != original.points) {
            onElementTransformed(updated)
        }
        selectionOriginal = null
        selectionAnchor = null
        selectionPreview = updated
        invalidate()
    }
''',
    '''    private fun finishSelection(event: MotionEvent) {
        updateSelection(event)
        val original = selectionOriginal
        val updated = selectionPreview
        val moved = original != null && updated != null && updated.points != original.points
        if (moved && updated != null) {
            onElementTransformed(updated)
        }
        onInputStatus(
            when {
                updated == null -> "Select / Move • tap a visible, unlocked object"
                moved -> "Select / Move • object moved"
                else -> "Select / Move • object selected • drag to move"
            },
        )
        selectionOriginal = null
        selectionAnchor = null
        selectionPreview = updated
        invalidate()
    }
''',
)

test = ROOT / "app/src/androidTest/java/com/kreativstudio/app/ui/PrimaryRoutesTest.kt"
replace_exact(
    test,
    '''        composeRule.waitUntilAtLeastOneExists(
            hasContentDescription("Back to Atelier"),
            timeoutMillis = 20_000,
        )
    }
}
''',
    '''        composeRule.waitUntilAtLeastOneExists(
            hasContentDescription("Back to Atelier"),
            timeoutMillis = 20_000,
        )
        composeRule.waitUntilAtLeastOneExists(hasContentDescription("Pencil"), timeoutMillis = 10_000)
        composeRule.waitUntilAtLeastOneExists(hasContentDescription("Pen"), timeoutMillis = 10_000)
        composeRule.waitUntilAtLeastOneExists(hasContentDescription("Select / Move"), timeoutMillis = 10_000)
        composeRule.onNodeWithContentDescription("Select / Move").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Select / Move"), timeoutMillis = 10_000)
        composeRule.waitUntilAtLeastOneExists(
            hasText("Select / Move • tap an object"),
            timeoutMillis = 10_000,
        )
    }
}
''',
)

build = ROOT / "app/build.gradle.kts"
build_text = build.read_text().replace("versionCode = 4", "versionCode = 5").replace(
    'versionName = "0.1.3"', 'versionName = "0.1.4"'
)
build.write_text(build_text)
(ROOT / "VERSION").write_text("0.1.4\n")

# Fail early if any required result is missing.
checks = {
    studio: [
        "Select / Move • tap an object",
        "showsColorControl",
        "usesSizeControl",
        "ToolType.PEN -> Icons.Default.BorderColor",
        "ToolType.SELECT -> Icons.Default.SelectAll",
    ],
    canvas: ["Select / Move • object moved"],
    test: ['hasContentDescription("Pen")', 'hasText("Select / Move • tap an object")'],
    build: ['versionName = "0.1.4"', "versionCode = 5"],
}
for path, required in checks.items():
    text = path.read_text()
    for needle in required:
        if needle not in text:
            raise RuntimeError(f"Missing {needle!r} in {path}")

print("KREATIV Studio 0.1.4 tool UX repair applied")
