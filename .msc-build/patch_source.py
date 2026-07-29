from pathlib import Path

root = Path("MyStudyCompanion")

# The custom privateAlpha build type does not compile src/release sources.
# Give it the signed-build App Check provider explicitly so the main
# application initializer resolves without turning the APK into a debug build.
release_app_check = (
    root
    / "app/src/release/java/com/mystudycompanion/app/AppCheckProviderInstaller.kt"
)
private_alpha_app_check = (
    root
    / "app/src/privateAlpha/java/com/mystudycompanion/app/AppCheckProviderInstaller.kt"
)
if not release_app_check.is_file():
    raise RuntimeError(f"Missing signed App Check provider: {release_app_check}")
private_alpha_app_check.parent.mkdir(parents=True, exist_ok=True)
private_alpha_app_check.write_text(
    release_app_check.read_text(encoding="utf-8"),
    encoding="utf-8",
)

# BIND_LISTENER is deprecated and rejected by release-grade Wear lint.
# Use event- and path-specific Data Layer filters so background sync keeps
# working without subscribing either service to unrelated wearable traffic.
manifest_filter_updates = {
    "wear/src/main/AndroidManifest.xml": (
        '                <action android:name="com.google.android.gms.wearable.BIND_LISTENER" />',
        '                <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />\n'
        '                <data\n'
        '                    android:scheme="wear"\n'
        '                    android:host="*"\n'
        '                    android:path="/msc/study-snapshot" />',
    ),
    "app/src/main/AndroidManifest.xml": (
        '                <action android:name="com.google.android.gms.wearable.BIND_LISTENER" />',
        '                <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />\n'
        '                <data\n'
        '                    android:scheme="wear"\n'
        '                    android:host="*"\n'
        '                    android:path="/msc/action/considered" />',
    ),
}
for relative, (legacy_filter, current_filter) in manifest_filter_updates.items():
    path = root / relative
    text = path.read_text(encoding="utf-8")
    if legacy_filter not in text:
        raise RuntimeError(f"Legacy Wear listener filter changed unexpectedly: {path}")
    path.write_text(
        text.replace(legacy_filter, current_filter, 1),
        encoding="utf-8",
    )

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

# Wear Material 3 Tile APIs require the direct ProtoLayout Material3 artifact.
path = root / "gradle/libs.versions.toml"
text = path.read_text(encoding="utf-8")
needle = 'androidx-wear-protolayout-material = { module = "androidx.wear.protolayout:protolayout-material", version.ref = "wearProtoLayout" }\n'
addition = needle + 'androidx-wear-protolayout-material3 = { module = "androidx.wear.protolayout:protolayout-material3", version.ref = "wearProtoLayout" }\n'
if "androidx-wear-protolayout-material3" not in text:
    text = text.replace(needle, addition)
path.write_text(text, encoding="utf-8")

path = root / "wear/build.gradle.kts"
text = path.read_text(encoding="utf-8")
needle = "    implementation(libs.androidx.wear.protolayout.material)\n"
if "implementation(libs.androidx.wear.protolayout.material3)" not in text:
    text = text.replace(needle, needle + "    implementation(libs.androidx.wear.protolayout.material3)\n")
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

# Glance has separate generic and Intent activity-action overload packages.
path = root / "app/src/main/java/com/mystudycompanion/app/widget/DailyStudyWidget.kt"
text = path.read_text(encoding="utf-8")
intent_alias = "import androidx.glance.appwidget.action.actionStartActivity as actionStartActivityIntent\n"
if intent_alias not in text:
    text = text.replace(
        "import androidx.glance.action.actionStartActivity\n",
        "import androidx.glance.action.actionStartActivity\n" + intent_alias,
    )
text = text.replace(
    "import androidx.glance.appwidget.action.actionStartActivity\n",
    "import androidx.glance.action.actionStartActivity\n" + intent_alias,
)
text = text.replace("import androidx.glance.layout.defaultWeight\n", "")
text = text.replace(
    "import androidx.glance.material3.GlanceTheme\n",
    "import androidx.glance.GlanceTheme\n",
)
text = text.replace(
    "actionStartActivity(\n                            Intent(",
    "actionStartActivityIntent(\n                            Intent(",
)
path.write_text(text, encoding="utf-8")

# Public complication services cannot expose an internal model type.
path = root / "wear/src/main/java/com/mystudycompanion/app/wear/WearComplicationContent.kt"
text = path.read_text(encoding="utf-8").replace(
    "internal data class WearComplicationText(",
    "data class WearComplicationText(",
)
path.write_text(text, encoding="utf-8")

print("Applied Kotlin, Compose, graphics, Material 3, Glance, Tile, and complication fixes.")


# Bind debug variants to the explicitly configured stable private-alpha key.
for rel in ["app/build.gradle.kts", "wear/build.gradle.kts"]:
    path = root / rel
    text = path.read_text(encoding="utf-8")
    needle = "        debug {\n            applicationIdSuffix"
    replacement = (
        "        debug {\n"
        "            if (privateSigningConfigured) {\n"
        "                signingConfig = signingConfigs.getByName(\"privateRelease\")\n"
        "            }\n"
        "            applicationIdSuffix"
    )
    if needle not in text:
        raise RuntimeError(f"No debug signing insertion anchor in {path}")
    text = text.replace(needle, replacement, 1)
    path.write_text(text, encoding="utf-8")
