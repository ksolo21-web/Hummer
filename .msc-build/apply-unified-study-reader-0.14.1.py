#!/usr/bin/env python3
from __future__ import annotations

import base64
import hashlib
import lzma
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / ".msc-build"
CHUNKS = sorted(BUILD.glob("msc-0.14.1-unified-reader.patch.xz.b64.*"))
EXPECTED_CHUNK_COUNT = 5
EXPECTED_XZ_SHA256 = "ac61c59aca5ebe93df83bdf52ec8ad33d3316afd525dc729a6a8f7e7b0150bfa"
EXPECTED_PATCH_SHA256 = "3ac74b19c2160eb7770d12360a9c7fbd09f8e096a7486e362c52044437b1214a"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def require_text(path: str, needle: str) -> None:
    target = ROOT / path
    if not target.is_file():
        raise SystemExit(f"Required 0.14.1 file is missing: {path}")
    text = target.read_text(encoding="utf-8")
    if needle not in text:
        raise SystemExit(f"Required 0.14.1 marker is missing from {path}: {needle}")


if len(CHUNKS) != EXPECTED_CHUNK_COUNT:
    raise SystemExit(
        f"Expected {EXPECTED_CHUNK_COUNT} unified-reader payload chunks, found {len(CHUNKS)}."
    )

encoded = "".join(chunk.read_text(encoding="ascii").strip() for chunk in CHUNKS)
compressed = base64.b64decode(encoded, validate=True)
if sha256(compressed) != EXPECTED_XZ_SHA256:
    raise SystemExit("Unified-reader compressed payload checksum mismatch.")

patch_bytes = lzma.decompress(compressed)
if sha256(patch_bytes) != EXPECTED_PATCH_SHA256:
    raise SystemExit("Unified-reader patch checksum mismatch.")

already_applied = (
    (ROOT / "MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/UnifiedStudyReaderScreen.kt").is_file()
    and (ROOT / "MyStudyCompanionWeb/reader.js").is_file()
)

if not already_applied:
    result = subprocess.run(
        ["patch", "-p1", "--forward", "--batch"],
        cwd=ROOT,
        input=patch_bytes,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    output = result.stdout.decode("utf-8", errors="replace")
    print(output, end="")
    if result.returncode != 0:
        raise SystemExit(f"Unified-reader patch failed with exit code {result.returncode}.")
else:
    print("Unified-reader 0.14.1 overlay is already present; validating it.")

require_text(
    "MyStudyCompanion/app/build.gradle.kts",
    'versionName = "0.14.1-private-alpha-unified-study-reader"',
)
require_text(
    "MyStudyCompanion/wear/build.gradle.kts",
    'versionName = "0.14.1-wear-private-alpha-unified-study-reader"',
)
require_text(
    "MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/MyStudyCompanionApp.kt",
    "safeDrawingPadding()",
)
require_text(
    "MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/FamilyHubScreen.kt",
    "fun FamilyHubScreen(",
)
require_text(
    "MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/UnifiedStudyReaderScreen.kt",
    "UnifiedStudyReaderScreen",
)
require_text(
    "MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/studyreader/UnifiedStudyReaderRepository.kt",
    "memberStudyMaterials",
)
require_text("MyStudyCompanionWeb/index.html", 'id="studyLibraryList"')
require_text("MyStudyCompanionWeb/reader.js", "createStudyReader")
require_text("MyStudyCompanionWeb/firebase-sync.js", "memberStudyMaterials")
require_text("MyStudyCompanion/firestore.rules", "memberStudyMaterials")
require_text(
    ".msc-build/firebase-rules-tests/rules.test.cjs",
    "study material notes are private to the owning account",
)

print("Applied and validated My Study Companion 0.14.1 unified Study Reader overlay.")
