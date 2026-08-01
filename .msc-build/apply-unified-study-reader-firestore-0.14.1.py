#!/usr/bin/env python3
from __future__ import annotations

import base64
import hashlib
import lzma
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAYLOAD = ROOT / ".msc-build/msc-0.14.1-firestore-only.patch.xz.b64"
EXPECTED_XZ_SHA256 = "9c28a85b5a8ee23334dcb4bd9f317e095b570e96dad50defe1a14a78812a849c"
EXPECTED_PATCH_SHA256 = "0057c9732968cefe929214fbbeaa14efec9c1de792a0877fe05891554b66c387"
RULES = ROOT / "MyStudyCompanion/firestore.rules"
TESTS = ROOT / ".msc-build/firebase-rules-tests/rules.test.cjs"


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def validate() -> None:
    rules = RULES.read_text(encoding="utf-8")
    tests = TESTS.read_text(encoding="utf-8")
    required_rules = (
        "memberStudyMaterials",
        "payloadJson.size() <= 700000",
        "request.auth.uid == accountUid",
    )
    for marker in required_rules:
        if marker not in rules:
            raise SystemExit(f"Missing 0.14.1 Firestore rule marker: {marker}")
    required_tests = (
        "member can synchronize only their own official study material",
        "study materials reject nonofficial sources, injected fields, and oversized payloads",
        "study material notes are private to the owning account",
    )
    for marker in required_tests:
        if marker not in tests:
            raise SystemExit(f"Missing 0.14.1 Firestore test marker: {marker}")


already_applied = (
    RULES.is_file()
    and TESTS.is_file()
    and "memberStudyMaterials" in RULES.read_text(encoding="utf-8")
    and "study material notes are private to the owning account"
        in TESTS.read_text(encoding="utf-8")
)

if not already_applied:
    compressed = base64.b64decode(PAYLOAD.read_text(encoding="ascii").strip(), validate=True)
    if digest(compressed) != EXPECTED_XZ_SHA256:
        raise SystemExit("Firestore-only compressed payload checksum mismatch.")
    patch = lzma.decompress(compressed)
    if digest(patch) != EXPECTED_PATCH_SHA256:
        raise SystemExit("Firestore-only patch checksum mismatch.")
    result = subprocess.run(
        ["patch", "-p1", "--forward", "--batch"],
        cwd=ROOT,
        input=patch,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    print(result.stdout.decode("utf-8", errors="replace"), end="")
    if result.returncode != 0:
        raise SystemExit(f"Firestore-only overlay failed with exit code {result.returncode}.")
else:
    print("Firestore-only 0.14.1 overlay is already present; validating it.")

validate()
print("Applied and validated My Study Companion 0.14.1 Firestore study-material security overlay.")
