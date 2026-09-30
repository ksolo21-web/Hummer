#!/usr/bin/env python3
from pathlib import Path
import base64, hashlib, io, json, tarfile, sys

ARCHIVE_SHA = "2b8ed716bcdedc2aead6a01b9f21b4a20b18f800805b42fe66bae13e35c1781e"
CHUNK_SHA = [
    "7aec0c3935ca1b5067b5c42a1977f8e0430bdf1303f7b7a4768fb0e17f6ccc41",
    "43f92518c947644636d43423a4560321c892818eb119284c5ec5787af1c067ba",
    "3228ae465de0ac5574c2d8941f582edbb6cbbfbdc54f029cefa54623dec8c547",
]
EXPECTED_MEMBERS = {
    "app/build.gradle.kts",
    "app/src/main/java/com/koenterprises/territorycardstudio/ProductionUi.kt",
    "app/src/main/java/com/koenterprises/territorycardstudio/TerritoryCardStudioTheme.kt",
    "app/src/androidTest/java/com/koenterprises/territorycardstudio/PhaseB120InstrumentationTest.kt",
}

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: apply.py <accepted-phase-a-source>")
    root = Path(sys.argv[1]).resolve(strict=True)
    here = Path(__file__).resolve().parent

    build = (root / "app/build.gradle.kts").read_text()
    assert 'versionCode = 47' in build
    assert 'versionName = "1.1.0"' in build

    encoded_parts = []
    for i, expected in enumerate(CHUNK_SHA):
        raw = (here / "chunks" / f"c{i:02}").read_text().strip()
        assert sha(raw.encode()) == expected, f"Phase B chunk c{i:02} changed"
        encoded_parts.append(raw)

    archive = base64.b64decode("".join(encoded_parts))
    assert sha(archive) == ARCHIVE_SHA, "Phase B overlay archive changed"

    with tarfile.open(fileobj=io.BytesIO(archive), mode="r:xz") as tf:
        members = [m for m in tf.getmembers() if m.isfile()]
        names = {m.name for m in members}
        assert names == EXPECTED_MEMBERS, (names ^ EXPECTED_MEMBERS)
        for member in members:
            target = (root / member.name).resolve()
            assert root == target or root in target.parents, member.name
            target.parent.mkdir(parents=True, exist_ok=True)
            src = tf.extractfile(member)
            assert src is not None
            target.write_bytes(src.read())

    build = (root / "app/build.gradle.kts").read_text()
    production = (root / "app/src/main/java/com/koenterprises/territorycardstudio/ProductionUi.kt").read_text()
    theme = (root / "app/src/main/java/com/koenterprises/territorycardstudio/TerritoryCardStudioTheme.kt").read_text()

    assert 'versionCode = 48' in build
    assert 'versionName = "1.2.0"' in build
    assert 'ProductionRoute.DETAIL' in production
    assert 'TerritoryDetailScreen' in production
    assert 'home-field-hero' in production
    assert 'territory-sort-label' in production
    assert 'detail-create-card' in production
    assert 'Territory 1.2.0 • Private field app' in production
    assert 'secondaryContainer = Color(0xFFE3EEFF)' in theme
    assert 'background = DarkBackground' in theme

    print(json.dumps({
        "schema":"territory-phase-b-120-overlay-v1",
        "archiveSha256":ARCHIVE_SHA,
        "versionCode":48,
        "versionName":"1.2.0",
        "phaseAReopened":False,
        "homeRedesign":True,
        "territoryListRedesign":True,
        "territoryDetailScreen":True,
        "darkThemePolish":True,
        "conceptAIconPreserved":True,
        "multiSelectCreatePreserved":True,
        "singleCompleteUserMapRulePreserved":True,
        "status":"APPLIED"
    },indent=2))

if __name__=="__main__":
    main()
