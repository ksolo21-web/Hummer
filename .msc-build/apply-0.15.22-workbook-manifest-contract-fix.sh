#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Reconstruct every accepted change through 0.15.21 first. This layer changes
# only the workbook manifest/model contract that blocked the real image assets.
bash .msc-build/apply-0.15.21-event-navigation-hardening.sh

PATCH_FILE=".msc-build/0.15.22-workbook-manifest-contract-fix.patch"
echo 'fffa693030a188deef1856e9a8b0f9db1519b6d73abb1955d66d22ca002274ec  '"$PATCH_FILE" | sha256sum -c -
patch -p1 --batch --forward < "$PATCH_FILE"

python3 - <<'PY'
from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    target = Path(path)
    text = target.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise SystemExit(f"Expected one {old!r} in {target}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")

replace_once("MyStudyCompanion/app/build.gradle.kts", "versionCode = 54", "versionCode = 55")
replace_once(
    "MyStudyCompanion/app/build.gradle.kts",
    'versionName = "0.15.21-private-alpha-event-navigation-hardening"',
    'versionName = "0.15.22-private-alpha-workbook-manifest-contract-fix"',
)
replace_once("MyStudyCompanion/wear/build.gradle.kts", "versionCode = 360171001", "versionCode = 360172001")
replace_once(
    "MyStudyCompanion/wear/build.gradle.kts",
    'versionName = "0.15.21-wear-private-alpha-event-navigation-hardening"',
    'versionName = "0.15.22-wear-private-alpha-workbook-manifest-contract-fix"',
)
PY

mkdir -p MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/companion
cat > MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/companion/WorkbookIllustrationManifestContractTest.kt <<'KOTLIN'
package com.mystudycompanion.app.companion

import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkbookIllustrationManifestContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun packagedManifestDeserializesAndReferencesRealActivityImages() {
        val root = sequenceOf(
            File("src/main/assets/workbook"),
            File("app/src/main/assets/workbook"),
            File("MyStudyCompanion/app/src/main/assets/workbook"),
        ).firstOrNull(File::isDirectory) ?: error("Workbook assets not found")
        val manifestFile = File(root, "manifest.json")
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
            listOf(
                "master.webp",
                "difference-changed.webp",
                "color-master.webp",
                "color-line.png",
                "color-region-mask.png",
            ).forEach { name -> assertTrue(File(root, "${asset.id}/$name").isFile) }
        }
    }
}
KOTLIN

python3 - <<'PY'
from pathlib import Path
import json

catalog = Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/companion/WorkbookIllustrationCatalog.kt").read_text(encoding="utf-8")
required = (
    'import kotlinx.serialization.SerialName',
    '@SerialName("hex") val hex: String',
    'entry.hex.matches(Regex("^#[0-9A-Fa-f]{6}$"))',
    'asset.colorRegionCount == asset.colorRegions.size',
    'asset.colorRegions.size in 8..128',
)
for marker in required:
    if marker not in catalog:
        raise SystemExit(f"Missing workbook contract marker: {marker}")
for marker in ('val rgb: String', 'asset.colorRegions.size in 8..24'):
    if marker in catalog:
        raise SystemExit(f"Unsafe workbook contract remains: {marker}")

root = Path("MyStudyCompanion/app/src/main/assets/workbook")
data = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
assert data["version"] >= 4 and data["colorByNumberVersion"] >= 2
assert len(data["assets"]) == 16
assert all("hex" in p and "rgb" not in p for p in data["palette"])
counts = [len(a["colorRegions"]) for a in data["assets"]]
assert min(counts) >= 8 and max(counts) <= 128
assert any(count > 24 for count in counts)
for asset in data["assets"]:
    assert asset["colorRegionCount"] == len(asset["colorRegions"])
    assert min(r["pixelCount"] for r in asset["colorRegions"]) >= 900
    base = root / asset["id"]
    for name in ("master.webp", "difference-changed.webp", "color-master.webp", "color-line.png", "color-region-mask.png"):
        assert (base / name).is_file(), base / name

preserved = {
    "MyStudyCompanion/app/build.gradle.kts": (
        "versionCode = 55",
        'versionName = "0.15.22-private-alpha-workbook-manifest-contract-fix"',
    ),
    "MyStudyCompanion/wear/build.gradle.kts": (
        "versionCode = 360172001",
        'versionName = "0.15.22-wear-private-alpha-workbook-manifest-contract-fix"',
    ),
    "MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt": (
        "private data class LoadedDifferenceIllustration",
        "loadWorkbookAssetSafely",
        'Text("Start activity")',
        "repository.setActiveWorkbookSafely(book.id, page.key)",
    ),
    "MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ai/AiStudyRepository.kt": (
        "private val smartOnlineValidated = backendConfig.isConfigured",
        "smartOnlineConfigured = smartOnlineValidated || online",
    ),
}
for filename, markers in preserved.items():
    text = Path(filename).read_text(encoding="utf-8")
    for marker in markers:
        assert marker in text, f"{filename}: missing {marker}"

print(f"PASS: manifest model accepts the exact packaged palette and {min(counts)}-{max(counts)} curated regions; real images are present.")
PY

echo 'Applied My Study Companion 0.15.22 workbook manifest contract repair.'
