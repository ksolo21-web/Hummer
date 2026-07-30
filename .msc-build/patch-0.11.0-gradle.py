from pathlib import Path

# Kotlin 2.x requires the compilerOptions DSL for JVM target selection.
build_file = Path("MyStudyCompanion/app/build.gradle.kts")
text = build_file.read_text(encoding="utf-8")
old = '''    kotlinOptions {
        jvmTarget = "17"
    }
'''
new = '''    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }
'''
if old in text:
    text = text.replace(old, new, 1)
elif "JvmTarget.JVM_17" not in text:
    raise SystemExit("Neither the legacy nor migrated Kotlin JVM-target block was found.")
build_file.write_text(text, encoding="utf-8")

# Apply the same cleanup immediately for the 0.11.0 source.
ui_dir = Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui")
for filename in (
    "AiStudyScreen.kt",
    "CompanionHubScreen.kt",
    "HomeScreen.kt",
    "MyStudyCompanionApp.kt",
    "NotesScreen.kt",
    "SettingsScreen.kt",
    "StudyScreen.kt",
):
    path = ui_dir / filename
    if not path.is_file():
        continue
    source = path.read_text(encoding="utf-8")
    source = source.replace("import androidx.compose.foundation.layout.weight\n", "")
    source = source.replace("import androidx.compose.foundation.layout.matchParentSize\n", "")
    path.write_text(source, encoding="utf-8")

app_ui = ui_dir / "MyStudyCompanionApp.kt"
source = app_ui.read_text(encoding="utf-8")
opt_in = "@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n\n"
if not source.startswith(opt_in):
    source = opt_in + source
source = source.replace(
    "@OptIn(ExperimentalMaterial3Api::class)",
    "@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)",
)
app_ui.write_text(source, encoding="utf-8")

# Material3TileService in Tiles 1.6.1 requires the ProtoLayout Material 3 artifact.
# The root build also owns a pre-compile cleanup task. It runs after every source
# overlay, so later feature overlays cannot reintroduce internal Compose imports.
root_build = Path("MyStudyCompanion/build.gradle.kts")
root_text = root_build.read_text(encoding="utf-8")
compatibility_block = r'''

project(":wear") {
    pluginManager.withPlugin("com.android.application") {
        dependencies.add(
            "implementation",
            "androidx.wear.protolayout:protolayout-material3:1.4.1",
        )
    }
}

val applyMscPostOverlayCompatibility by tasks.registering {
    doLast {
        val uiDirectory = rootProject.file("app/src/main/java/com/mystudycompanion/app/ui")
        listOf(
            "AiStudyScreen.kt",
            "CompanionHubScreen.kt",
            "HomeScreen.kt",
            "MyStudyCompanionApp.kt",
            "NotesScreen.kt",
            "SettingsScreen.kt",
            "StudyScreen.kt",
        ).forEach { filename ->
            val sourceFile = uiDirectory.resolve(filename)
            if (sourceFile.isFile) {
                var source = sourceFile.readText()
                    .replace("import androidx.compose.foundation.layout.weight\n", "")
                    .replace("import androidx.compose.foundation.layout.matchParentSize\n", "")
                if (filename == "MyStudyCompanionApp.kt") {
                    val optIn = "@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n\n"
                    if (!source.startsWith(optIn)) source = optIn + source
                    source = source.replace(
                        "@OptIn(ExperimentalMaterial3Api::class)",
                        "@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)",
                    )
                }
                sourceFile.writeText(source)
            }
        }
    }
}

subprojects {
    tasks.matching {
        it.name.startsWith("compile") && it.name.endsWith("Kotlin")
    }.configureEach {
        dependsOn(rootProject.tasks.named("applyMscPostOverlayCompatibility"))
    }
}
'''
marker = 'val applyMscPostOverlayCompatibility by tasks.registering'
if marker not in root_text:
    root_text += compatibility_block
    root_build.write_text(root_text, encoding="utf-8")

print("Applied Kotlin, Compose post-overlay, and Wear ProtoLayout Material 3 compatibility repairs.")
