from pathlib import Path

path = Path("MyStudyCompanion/app/build.gradle.kts")
text = path.read_text(encoding="utf-8")
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
if old not in text:
    raise SystemExit("Legacy kotlinOptions jvmTarget block was not found.")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Migrated Kotlin JVM target to compilerOptions DSL.")
