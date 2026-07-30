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

# Compose 1.10 exposes weight through RowScope/ColumnScope. Explicitly importing the
# internal implementation symbol fails compilation, so remove those imports.
ui_dir = Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui")
for filename in (
    "AiStudyScreen.kt",
    "CompanionHubScreen.kt",
    "MyStudyCompanionApp.kt",
    "NotesScreen.kt",
    "SettingsScreen.kt",
):
    path = ui_dir / filename
    source = path.read_text(encoding="utf-8")
    source = source.replace("import androidx.compose.foundation.layout.weight\n", "")
    path.write_text(source, encoding="utf-8")

# TopAppBar remains an experimental Material 3 API in the resolved dependency set.
app_ui = ui_dir / "MyStudyCompanionApp.kt"
source = app_ui.read_text(encoding="utf-8")
opt_in = "@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n\n"
if not source.startswith(opt_in):
    source = opt_in + source
app_ui.write_text(source, encoding="utf-8")

print("Applied Kotlin compiler-options and Compose compatibility repairs.")
