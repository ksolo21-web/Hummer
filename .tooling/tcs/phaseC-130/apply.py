#!/usr/bin/env python3
from pathlib import Path
import base64, hashlib, io, json, tarfile, sys

ARCHIVE_SHA = "3264f07648facbaaa1a161006a482dde4aa7e37b8b4e64f8bc004ce0f9b381e0"
CHUNK_SHA = [
    "da638b7e2a9cc72e9fabe52010754dda0d14271c21a5a4b51dce84cf728432db",
    "1a37b3536942ea7dac491ead4c7dbb3ff9e10f9ccd8b6fe1ac3384481fb1cb48",
    "25b95a3659c513871fe0579c4abdaa23042c8e2b8414de256a93eb4976b4b235",
]
EXPECTED_MEMBERS = {
    "app/build.gradle.kts",
    "app/src/main/java/com/koenterprises/territorycardstudio/ProductionUi.kt",
    "app/src/androidTest/java/com/koenterprises/territorycardstudio/PhaseC130InstrumentationTest.kt",
}

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: apply.py <accepted-phase-b-source>")
    root = Path(sys.argv[1]).resolve(strict=True)
    here = Path(__file__).resolve().parent

    build = (root / "app/build.gradle.kts").read_text()
    assert 'versionCode = 48' in build
    assert 'versionName = "1.2.0"' in build

    encoded=[]
    for i, expected in enumerate(CHUNK_SHA):
        raw=(here/"chunks"/f"c{i:02}").read_text().strip()
        assert sha(raw.encode()) == expected, f"Phase C chunk c{i:02} changed"
        encoded.append(raw)

    archive=base64.b64decode("".join(encoded))
    assert sha(archive) == ARCHIVE_SHA, "Phase C overlay archive changed"

    with tarfile.open(fileobj=io.BytesIO(archive), mode="r:xz") as tf:
        members=[m for m in tf.getmembers() if m.isfile()]
        names={m.name for m in members}
        assert names == EXPECTED_MEMBERS, names ^ EXPECTED_MEMBERS
        for member in members:
            target=(root/member.name).resolve()
            assert root == target or root in target.parents
            target.parent.mkdir(parents=True, exist_ok=True)
            src=tf.extractfile(member)
            assert src is not None
            target.write_bytes(src.read())

    build=(root/"app/build.gradle.kts").read_text()
    ui=(root/"app/src/main/java/com/koenterprises/territorycardstudio/ProductionUi.kt").read_text()
    assert 'versionCode = 49' in build
    assert 'versionName = "1.3.0"' in build
    assert 'ProductionRoute.ADVANCED' in ui
    assert 'open-advanced-tools' in ui
    assert 'advanced-tools-screen' in ui
    assert 'detail-more' in ui
    assert 'Open Advanced Tools' in ui
    assert 'Open Knowledge Base' in ui
    assert 'Territory 1.3.0 • Private field app' in ui

    print(json.dumps({
        "schema":"territory-phase-c-130-overlay-v1",
        "archiveSha256":ARCHIVE_SHA,
        "versionCode":49,
        "versionName":"1.3.0",
        "phaseBReopened":False,
        "advancedToolsSecondary":True,
        "knowledgeBaseSecondary":True,
        "detailUsesMoreBeforeAdvanced":True,
        "simpleCreatePreserved":True,
        "finalStandardQaRequired":True,
        "status":"APPLIED"
    }, indent=2))

if __name__ == "__main__":
    main()
