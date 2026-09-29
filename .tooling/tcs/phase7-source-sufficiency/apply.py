#!/usr/bin/env python3
"""Apply the source-sufficiency implementation to the verified continuation baseline.

Never marks a card approved or Phase 7 complete. All original authority assets and
all untouched source files remain unchanged. Decoding also produces a readable
patch for review; the archived effective source contains the actual Kotlin files.
"""
from pathlib import Path
import base64
import gzip
import hashlib
import json
import subprocess
import sys
import tempfile

PATCH_SHA = "eb9531bfc8f8203c251172af0043fae0343dc8ed147edd5a5fc720d519f9c1da"
ENCODED_SHA = "ff47dd09cf483275b46c28b4f000d0fdf0ecb80234a4277c3950a17b1cf1f620"
FILES = [
 ["app/src/androidTest/java/com/koenterprises/territorycardstudio/Phase7SourceSufficiencyInstrumentationTest.kt", None, "fcaf5f43671e14ffa4e91e1c2aa9da79e584212b7e704e91f0bb6466c6a28fa9"],
 ["app/src/main/java/com/koenterprises/territorycardstudio/AndroidNativeAuthoringService.kt", "f4648e366ebb300bdb305e7a356bca09f083652dd225fa967fe4541c503a30af", "34d13f3723703ee6dfd07c19341110cfed71fb15137b0de0c56e3e48bb791c50"],
 ["app/src/main/java/com/koenterprises/territorycardstudio/AndroidNativeDraftStore.kt", "2c8e9ceb24bd42668be1f9f9a6807750ac0bbaaa1461e3ffffdaacc02b223bb1", "98080cf6ca366f36ad1441c42b9a264c73b49a31fe6b46692baeb157bba5453a"],
 ["app/src/main/java/com/koenterprises/territorycardstudio/NativeAuthoringUi.kt", "4aa6fe6c0086577636b2b45aefa5a1c7f243446287f30f9cfa0914238cc2d86a", "9f209b13db20a49f70baa90ab005d43d979bd1b44aafbfa038b862f35e018646"],
 ["app/src/main/java/com/koenterprises/territorycardstudio/NativeSourceAssessment.kt", None, "292075c6488df63a60b79707e79b1384bc4ed56fc98a6d0b6b24438cd0953895"],
 ["app/src/main/java/com/koenterprises/territorycardstudio/NativeSourceInspectionStore.kt", None, "e05b8fe5fbc86023449e6da5f698ac171466125f378ec69c8dc68ee584d044cc"],
 ["app/src/main/java/com/koenterprises/territorycardstudio/NativeSourceSufficiencyUi.kt", None, "2401a567cbc27766a62229d2c08c8075b06766621fd10d0dc20930a4a3425311"],
 ["core/src/main/kotlin/com/koenterprises/territorycardstudio/core/OutlinedMapBoundary.kt", "304285aa4d72f7978db6cffea1f97da67d06f662b0f76ba2de2051e13a3cd334", "02fd3240d6b3a4c2471d64bfbbe1087ff740db2fe13aa49905616eba2f56b6a4"],
 ["core/src/main/kotlin/com/koenterprises/territorycardstudio/core/SourceSufficiency.kt", None, "3b2ebd1db09ec269ce0555b5f42c6d32141179d276fee3d2c3a2bb23d53fa154"],
 ["core/src/test/kotlin/com/koenterprises/territorycardstudio/core/SourceSufficiencyJUnitTest.kt", None, "4c97a6d108f903ccc8947db221d3e887b43cd33e0f208d53fe1d1added07c5f5"],
 ["core/src/test/kotlin/com/koenterprises/territorycardstudio/core/SourceSufficiencyRegression.kt", None, "88c72a7d087a99b06ef88deb4a7e75b9a8e91ec3a1bb5b677f6520abdcfba5a1"]
]

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def check(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)

def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: apply.py <continuation-source-directory>")
    target = Path(sys.argv[1]).resolve(strict=True)
    here = Path(__file__).resolve().parent
    encoded = b"".join((here / "chunks" / f"c{i:02}").read_bytes() for i in range(5))
    check(sha(encoded) == ENCODED_SHA, "Source-sufficiency transport changed")
    patch = gzip.decompress(base64.b64decode(encoded, validate=True))
    check(sha(patch) == PATCH_SHA, "Source-sufficiency patch changed")
    for name, before, after in FILES:
        p = (target / name).resolve()
        check(target in p.parents, "Invalid overlay path")
        check(not p.exists() if before is None else p.is_file() and sha(p.read_bytes()) == before,
              f"Source-sufficiency baseline mismatch: {name}")
    with tempfile.NamedTemporaryFile(suffix=".patch") as handle:
        handle.write(patch)
        handle.flush()
        command = ["git", "apply", "--recount", "--whitespace=error", "--directory=" + target.name]
        subprocess.run(command + ["--check", handle.name], cwd=target.parent, check=True)
        subprocess.run(command + [handle.name], cwd=target.parent, check=True)
    for name, before, after in FILES:
        check(sha((target / name).read_bytes()) == after, f"Source-sufficiency result mismatch: {name}")
    evidence = target.parent / "evidence"
    evidence.mkdir(exist_ok=True)
    (evidence / "source-sufficiency-readable.patch").write_bytes(patch)
    print(json.dumps({"schema": 1, "filesVerified": len(FILES), "patchSha256": PATCH_SHA,
                      "phase7Complete": False, "approvedRealCards": 0,
                      "sourceReadyIsNotCardApproval": True}, indent=2))

if __name__ == "__main__":
    main()
