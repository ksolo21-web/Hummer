#!/usr/bin/env python3
from pathlib import Path
import base64, hashlib, io, json, lzma, tarfile, sys

ARCHIVE_SHA = "f78455e210d5bd3c744f0231bc4858b7dcf2f293a5218bfd7c9de77028f01833"
CHUNK_SHA = [
    "3b478e6fbff2db658b544b0e9c071d1fcf45f86430d76b78cd8dfc53c6a2c4b1",
    "664a4aca8c3dac27ebc9ce31c1f84ccd02187e4f97d8b1c86259aeea7ae6e3f6",
    "e05ed344d8b3a2805d14552805f8c495676ae565a750d274ca08a3afbf92e697",
]
EXPECTED_MEMBERS = {
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/koenterprises/territorycardstudio/ProductionUi.kt",
    "app/src/main/java/com/koenterprises/territorycardstudio/WorkspaceModeUi.kt",
    "app/src/main/java/com/koenterprises/territorycardstudio/TerritoryCardStudioTheme.kt",
    "app/src/main/res/values/icon_colors.xml",
    "app/src/main/res/drawable/ic_territory_foreground.xml",
    "app/src/main/res/drawable/ic_territory_monochrome.xml",
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
    "app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml",
    "app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml",
    "app/src/androidTest/java/com/koenterprises/territorycardstudio/PhaseA110InstrumentationTest.kt",
}

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: apply.py <accepted-1.0.1-source>")
    root = Path(sys.argv[1]).resolve(strict=True)
    here = Path(__file__).resolve().parent
    encoded_parts = []
    for i, expected in enumerate(CHUNK_SHA):
        raw = (here / "chunks" / f"c{i:02}").read_text().strip()
        assert sha(raw.encode()) == expected, f"Phase A chunk c{i:02} changed"
        encoded_parts.append(raw)

    archive = base64.b64decode("".join(encoded_parts))
    assert sha(archive) == ARCHIVE_SHA, "Phase A overlay archive changed"

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
    manifest = (root / "app/src/main/AndroidManifest.xml").read_text()
    production = (root / "app/src/main/java/com/koenterprises/territorycardstudio/ProductionUi.kt").read_text()
    workspace = (root / "app/src/main/java/com/koenterprises/territorycardstudio/WorkspaceModeUi.kt").read_text()

    assert 'versionCode = 47' in build
    assert 'versionName = "1.1.0"' in build
    assert 'android:icon="@mipmap/ic_launcher"' in manifest
    assert 'android:roundIcon="@mipmap/ic_launcher_round"' in manifest
    assert 'Home' in production and 'Territories' in production and 'Create' in production and 'Queue' in production and 'More' in production
    assert 'Select one or more. Only one is required.' in production
    assert 'Choose at least one territory type.' in production
    assert 'Create Card From This Map' in workspace
    assert 'one clear, complete map is enough' in workspace.lower()
    assert 'requestedTypeLabels' in workspace

    print(json.dumps({
        "schema": "territory-phase-a-110-overlay-v1",
        "archiveSha256": ARCHIVE_SHA,
        "versionCode": 47,
        "versionName": "1.1.0",
        "phase7Reopened": False,
        "conceptAIcon": True,
        "simpleNavigation": ["Home","Territories","Create","Queue","More"],
        "territoryTypeMultiSelect": True,
        "minimumSelectedTypes": 1,
        "advancedToolsHiddenByDefault": True,
        "singleCompleteUserMapCanBeAuthoritative": True,
        "status": "APPLIED"
    }, indent=2))

if __name__ == "__main__":
    main()
