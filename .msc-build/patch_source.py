from pathlib import Path

root = Path("MyStudyCompanion")

# Kotlin 2.4 compilerOptions DSL.
for rel in ["app/build.gradle.kts", "wear/build.gradle.kts", "benchmark/build.gradle.kts"]:
    path = root / rel
    text = path.read_text(encoding="utf-8")
    if "import org.jetbrains.kotlin.gradle.dsl.JvmTarget" not in text:
        text = "import org.jetbrains.kotlin.gradle.dsl.JvmTarget\n" + text
    text = text.replace('    kotlinOptions {\n        jvmTarget = "17"\n    }\n', "")
    text = text.replace('    kotlinOptions { jvmTarget = "17" }\n', "")
    if "\nkotlin {\n    compilerOptions" not in text:
        block = "\nkotlin {\n    compilerOptions {\n        jvmTarget.set(JvmTarget.JVM_17)\n    }\n}\n"
        if "\ndependencies {" in text:
            text = text.replace("\ndependencies {", block + "\ndependencies {", 1)
        elif "\nksp {" in text:
            text = text.replace("\nksp {", block + "\nksp {", 1)
        else:
            raise RuntimeError(f"No compiler-options insertion anchor in {path}")
    path.write_text(text, encoding="utf-8")

# Compose graphics Path moved under androidx.compose.ui.graphics.
for rel in [
    "app/src/main/java/com/mystudycompanion/app/design/ThemeEmblem.kt",
    "app/src/main/java/com/mystudycompanion/app/design/ThemePatternOverlay.kt",
    "wear/src/main/java/com/mystudycompanion/app/wear/WearThemeEmblem.kt",
]:
    path = root / rel
    text = path.read_text(encoding="utf-8").replace(
        "import androidx.compose.ui.geometry.Path\n",
        "import androidx.compose.ui.graphics.Path\n",
    )
    path.write_text(text, encoding="utf-8")

# Current DrawScope drawArc API takes topLeft and size rather than Rect positionally.
path = root / "app/src/main/java/com/mystudycompanion/app/design/ThemeEmblem.kt"
text = path.read_text(encoding="utf-8")
text = text.replace(
    "drawArc(tint, 20f, 140f, false, Rect(size.width * 0.18f, size.height * 0.38f, size.width * 0.82f, size.height * 0.92f), style = Stroke(fine))",
    "run {\n                    val bounds = Rect(size.width * 0.18f, size.height * 0.38f, size.width * 0.82f, size.height * 0.92f)\n                    drawArc(tint, 20f, 140f, false, topLeft = bounds.topLeft, size = bounds.size, style = Stroke(fine))\n                }",
)
text = text.replace(
    "drawArc(tint, 20f, 140f, false, Rect(size.width * 0.37f, size.height * 0.58f, size.width * 0.63f, size.height * 0.79f), style = Stroke(fine))",
    "run {\n                    val bounds = Rect(size.width * 0.37f, size.height * 0.58f, size.width * 0.63f, size.height * 0.79f)\n                    drawArc(tint, 20f, 140f, false, topLeft = bounds.topLeft, size = bounds.size, style = Stroke(fine))\n                }",
)
path.write_text(text, encoding="utf-8")

# weight and matchParentSize are scope-owned extensions in current Compose.
for rel in [
    "app/src/main/java/com/mystudycompanion/app/ui/AiStudyScreen.kt",
    "app/src/main/java/com/mystudycompanion/app/ui/FamilyWorshipScreen.kt",
    "app/src/main/java/com/mystudycompanion/app/ui/HomeScreen.kt",
    "app/src/main/java/com/mystudycompanion/app/ui/MyStudyCompanionApp.kt",
    "app/src/main/java/com/mystudycompanion/app/ui/NotesScreen.kt",
    "app/src/main/java/com/mystudycompanion/app/ui/SettingsScreen.kt",
    "app/src/main/java/com/mystudycompanion/app/ui/StudyScreen.kt",
]:
    path = root / rel
    text = path.read_text(encoding="utf-8")
    text = text.replace("import androidx.compose.foundation.layout.weight\n", "")
    text = text.replace("import androidx.compose.foundation.layout.matchParentSize\n", "")
    path.write_text(text, encoding="utf-8")

# TopAppBar requires the Material 3 experimental opt-in in this dependency line.
path = root / "app/src/main/java/com/mystudycompanion/app/ui/MyStudyCompanionApp.kt"
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

# Glance app-widget APIs live in Glance and appwidget.action packages.
path = root / "app/src/main/java/com/mystudycompanion/app/widget/DailyStudyWidget.kt"
text = path.read_text(encoding="utf-8")
text = text.replace(
    "import androidx.glance.action.actionStartActivity\n",
    "import androidx.glance.appwidget.action.actionStartActivity\n",
)
text = text.replace("import androidx.glance.layout.defaultWeight\n", "")
text = text.replace(
    "import androidx.glance.material3.GlanceTheme\n",
    "import androidx.glance.GlanceTheme\n",
)
path.write_text(text, encoding="utf-8")

print("Applied Kotlin, Compose, graphics, Material 3, and Glance compatibility fixes.")
