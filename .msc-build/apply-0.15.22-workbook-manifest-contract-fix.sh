#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Preserve the complete accepted source through 0.15.21, then repair only the
# workbook illustration manifest/model contract that prevented real assets from loading.
bash .msc-build/apply-0.15.21-event-navigation-hardening.sh

PATCH_FILE=".msc-build/0.15.22-workbook-manifest-contract-fix.patch"
echo '01bb9a586ccc5e68e1fa300c2cdb3d2256db27f5a0d702463b99dc63155f3408  '"$PATCH_FILE" | sha256sum -c -
patch -p1 --batch --forward < "$PATCH_FILE"

python3 - <<'PY'
from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one occurrence, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

replace_once(
    Path("MyStudyCompanion/app/build.gradle.kts"),
    "versionCode = 54",
    "versionCode = 55",
    "phone version code",
)
replace_once(
    Path("MyStudyCompanion/app/build.gradle.kts"),
    'versionName = "0.15.21-private-alpha-event-navigation-hardening"',
    'versionName = "0.15.22-private-alpha-workbook-manifest-contract-fix"',
    "phone version name",
)
replace_once(
    Path("MyStudyCompanion/wear/build.gradle.kts"),
    "versionCode = 360171001",
    "versionCode = 360172001",
    "Wear version code",
)
replace_once(
    Path("MyStudyCompanion/wear/build.gradle.kts"),
    'versionName = "0.15.21-wear-private-alpha-event-navigation-hardening"',
    'versionName = "0.15.22-wear-private-alpha-workbook-manifest-contract-fix"',
    "Wear version name",
)
PY

mkdir -p MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/companion
cat > MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/companion/WorkbookIllustrationManifestContractTest.kt <<'KOTLIN'
package com.mystudycompanion.app.companion

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkbookIllustrationManifestContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun exactPackagedManifestDeserializesAndReferencesRealDifferenceImages() {
        val assetRoot = sequenceOf(
            File("src/main/assets/workbook"),
            File("app/src/main/assets/workbook"),
            File("MyStudyCompanion/app/src/main/assets/workbook"),
        ).firstOrNull { it.isDirectory }
            ?: error("Could not locate the workbook asset directory")

        val manifestFile = File(assetRoot, "manifest.json")
        assertTrue("Workbook manifest must exist", manifestFile.isFile)
        val manifest = json.decodeFromString<WorkbookIllustrationManifest>(manifestFile.readText())

        assertTrue(manifest.version >= 4)
        assertTrue(manifest.colorByNumberVersion >= 2)
        assertEquals(16, manifest.assets.size)
        assertEquals(manifest.palette.size, manifest.palette.map { it.number }.toSet().size)
        assertTrue(manifest.palette.all { it.hex.matches(Regex("^#[0-9A-Fa-f]{6}$")) })

        manifest.assets.forEach { asset ->
            assertEquals(asset.colorRegionCount, asset.colorRegions.size)
            assertTrue(asset.colorRegions.size in 8..128)
            assertTrue(asset.colorRegions.all { it.pixelCount >= 900 })
            assertTrue(asset.differences.isNotEmpty())
            assertTrue(File(assetRoot, "${asset.id}/master.webp").isFile)
            assertTrue(File(assetRoot, "${asset.id}/difference-changed.webp").isFile)
            assertTrue(File(assetRoot, "${asset.id}/color-master.webp").isFile)
            assertTrue(File(assetRoot, "${asset.id}/color-line.png").isFile)
            assertTrue(File(assetRoot, "${asset.id}/color-region-mask.png").isFile)
        }
    }
}
KOTLIN

python3 - <<'PY'
from pathlib import Path
import json

catalog_path = Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/companion/WorkbookIllustrationCatalog.kt")
catalog = catalog_path.read_text(encoding="utf-8")
for marker in (
    'import kotlinx.serialization.SerialName',
    '@SerialName("hex") val hex: String',
    'entry.hex.matches(Regex("^#[0-9A-Fa-f]{6}$"))',
    'asset.colorRegionCount == asset.colorRegions.size',
    'asset.colorRegions.size in 8..128',
):
    if marker not in catalog:
        raise SystemExit(f"Workbook catalog is missing manifest-contract marker: {marker}")
for forbidden in (
    'val rgb: String',
    'asset.colorRegions.size in 8..24',
):
    if forbidden in catalog:
        raise SystemExit(f"Workbook catalog still contains rejected contract marker: {forbidden}")

manifest_path = Path("MyStudyCompanion/app/src/main/assets/workbook/manifest.json")
data = json.loads(manifest_path.read_text(encoding="utf-8"))
assert data["version"] >= 4
assert data["colorByNumberVersion"] >= 2
assert len(data["assets"]) == 16
assert all("hex" in item and "rgb" not in item for item in data["palette"])
assert all(8 <= len(asset["colorRegions"]) <= 128 for asset in data["assets"])
assert all(asset["colorRegionCount"] == len(asset["colorRegions"]) for asset in data["assets"])
assert all(min(region["pixelCount"] for region in asset["colorRegions"]) >= 900 for asset in data["assets"])
for asset in data["assets"]:
    base = manifest_path.parent / asset["id"]
    for name in (
        "master.webp", "difference-changed.webp", "color-master.webp",
        "color-line.png", "color-region-mask.png",
    ):
        if not (base / name).is_file():
            raise SystemExit(f"Missing real workbook asset: {base / name}")

checks = {
    Path("MyStudyCompanion/app/build.gradle.kts"): [
        "versionCode = 55",
        'versionName = "0.15.22-private-alpha-workbook-manifest-contract-fix"',
    ],
    Path("MyStudyCompanion/wear/build.gradle.kts"): [
        "versionCode = 360172001",
        'versionName = "0.15.22-wear-private-alpha-workbook-manifest-contract-fix"',
    ],
    Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt"): [
        "private data class LoadedDifferenceIllustration",
        "loadWorkbookAssetSafely",
        'Text("Start activity")',
        "repository.setActiveWorkbookSafely(book.id, page.key)",
    ],
    Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ai/AiStudyRepository.kt"): [
        "private val smartOnlineValidated = backendConfig.isConfigured",
        "smartOnlineConfigured = smartOnlineValidated || online",
    ],
}
missing = []
for path, markers in checks.items():
    text = path.read_text(encoding="utf-8") if path.is_file() else ""
    for marker in markers:
        if marker not in text:
            missing.append(f"{path}: missing {marker!r}")
if missing:
    raise SystemExit("FAIL: 0.15.22 cumulative preservation gate:\n- " + "\n- ".join(missing))

print("PASS: exact workbook manifest now matches the Kotlin model and every real illustration asset is present.")
PY

echo 'Applied My Study Companion 0.15.22 workbook manifest contract repair.'
