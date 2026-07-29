from pathlib import Path

root = Path("MyStudyCompanion")

# Compose Path moved under ui.graphics; keep geometry types under ui.geometry.
for relative in [
    "app/src/main/java/com/mystudycompanion/app/design/ThemeEmblem.kt",
    "app/src/main/java/com/mystudycompanion/app/design/ThemePatternOverlay.kt",
    "wear/src/main/java/com/mystudycompanion/app/wear/WearThemeEmblem.kt",
]:
    path = root / relative
    text = path.read_text(encoding="utf-8")
    text = text.replace(
        "import androidx.compose.ui.geometry.Path",
        "import androidx.compose.ui.graphics.Path",
    )
    path.write_text(text, encoding="utf-8")

# Current DrawScope drawArc takes topLeft and size rather than a Rect positional value.
path = root / "app/src/main/java/com/mystudycompanion/app/design/ThemeEmblem.kt"
text = path.read_text(encoding="utf-8")
old = "drawArc(tint, 20f, 140f, false, Rect(size.width * 0.18f, size.height * 0.38f, size.width * 0.82f, size.height * 0.92f), style = Stroke(fine))"
new = """val bodyArc = Rect(size.width * 0.18f, size.height * 0.38f, size.width * 0.82f, size.height * 0.92f)
                drawArc(color = tint, startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = bodyArc.topLeft, size = bodyArc.size, style = Stroke(fine))"""
if old not in text:
    raise SystemExit("Owl drawArc source shape changed unexpectedly")
text = text.replace(old, new)
old = "drawArc(tint, 20f, 140f, false, Rect(size.width * 0.37f, size.height * 0.58f, size.width * 0.63f, size.height * 0.79f), style = Stroke(fine))"
new = """val muzzleArc = Rect(size.width * 0.37f, size.height * 0.58f, size.width * 0.63f, size.height * 0.79f)
                drawArc(color = tint, startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = muzzleArc.topLeft, size = muzzleArc.size, style = Stroke(fine))"""
if old not in text:
    raise SystemExit("Tiger drawArc source shape changed unexpectedly")
path.write_text(text.replace(old, new), encoding="utf-8")

# RowScope, ColumnScope and BoxScope modifiers are receiver members in current Compose.
ui_root = root / "app/src/main/java/com/mystudycompanion/app/ui"
for path in ui_root.glob("*.kt"):
    text = path.read_text(encoding="utf-8")
    text = text.replace("import androidx.compose.foundation.layout.weight\n", "")
    text = text.replace("import androidx.compose.foundation.layout.matchParentSize\n", "")
    path.write_text(text, encoding="utf-8")

# TopAppBar remains an experimental Material 3 API on the pinned dependency line.
path = ui_root / "MyStudyCompanionApp.kt"
text = path.read_text(encoding="utf-8")
if "import androidx.compose.material3.ExperimentalMaterial3Api\n" not in text:
    text = text.replace(
        "import androidx.compose.material3.CircularProgressIndicator\n",
        "import androidx.compose.material3.CircularProgressIndicator\nimport androidx.compose.material3.ExperimentalMaterial3Api\n",
    )
text = text.replace(
    "@Composable\nprivate fun CompactAppScaffold(",
    "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun CompactAppScaffold(",
)
path.write_text(text, encoding="utf-8")

# Public Wear services cannot expose an internal data type through protected methods.
path = root / "wear/src/main/java/com/mystudycompanion/app/wear/WearComplicationContent.kt"
text = path.read_text(encoding="utf-8")
text = text.replace(
    "internal data class WearComplicationText(",
    "data class WearComplicationText(",
)
path.write_text(text, encoding="utf-8")

# Material3TileService requires the ProtoLayout Material 3 artifact.
path = root / "gradle/libs.versions.toml"
text = path.read_text(encoding="utf-8")
needle = 'androidx-wear-protolayout-material = { module = "androidx.wear.protolayout:protolayout-material", version.ref = "wearProtoLayout" }\n'
if needle not in text:
    raise SystemExit("ProtoLayout material dependency declaration is missing")
if "androidx-wear-protolayout-material3" not in text:
    text = text.replace(
        needle,
        needle + 'androidx-wear-protolayout-material3 = { module = "androidx.wear.protolayout:protolayout-material3", version.ref = "wearProtoLayout" }\n',
    )
path.write_text(text, encoding="utf-8")

path = root / "wear/build.gradle.kts"
text = path.read_text(encoding="utf-8")
needle = "    implementation(libs.androidx.wear.protolayout.material)\n"
if needle not in text:
    raise SystemExit("Wear ProtoLayout material implementation is missing")
if "protolayout.material3" not in text:
    text = text.replace(
        needle,
        needle + "    implementation(libs.androidx.wear.protolayout.material3)\n",
    )
path.write_text(text, encoding="utf-8")

# Glance scope modifiers and theming/action imports for the stable 1.1.1 API.
path = root / "app/src/main/java/com/mystudycompanion/app/widget/DailyStudyWidget.kt"
text = path.read_text(encoding="utf-8")
text = text.replace(
    "import androidx.glance.action.actionStartActivity\n",
    "import androidx.glance.action.actionStartActivity\nimport androidx.glance.appwidget.action.actionStartActivity as actionStartActivityIntent\n",
)
text = text.replace("import androidx.glance.layout.defaultWeight\n", "")
text = text.replace(
    "import androidx.glance.material3.GlanceTheme\n",
    "import androidx.glance.GlanceTheme\n",
)
text = text.replace(
    "actionStartActivity(\n                            Intent(Intent.ACTION_VIEW, Uri.parse(snapshot.daily.officialUrl)),\n                        )",
    "actionStartActivityIntent(\n                            Intent(Intent.ACTION_VIEW, Uri.parse(snapshot.daily.officialUrl)),\n                        )",
)
path.write_text(text, encoding="utf-8")

print("Applied current Compose, Wear Tile, complication, and Glance compatibility fixes")
