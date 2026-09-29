#!/usr/bin/env python3
"""Apply deterministic Phase 8/9 release hardening to the exact Phase 7 source.

This script never contains signing material. The resulting Gradle release variant
requires the Territory-specific signing environment variables at configuration time.
"""
from pathlib import Path
import hashlib
import json
import sys

BASE_BUILD_SHA = "f15da26c1aaf7b75acf56ebf82379c595d4b7114aca35d88d8250cd3659cf1aa"
BASE_MANIFEST_SHA = "4b0daaed3ce7f58b996ad1f6a9902ea182694467c7d10f42dc9cd0f657abb788"
EXPECTED = {
    "app/build.gradle.kts": "f1ef9d3f6db077a5e03616cc41df73b3e6180f2c143ec1cb54342a5ccdf5d3df",
    "app/src/main/AndroidManifest.xml": "aa1458b9cda320c69c4960f25236efd3ce4f5e2531dac79bbb2a37170371d00d",
    "app/proguard-rules.pro": "bc5ec6fa14af2a6848531e1f180f1238ab2790253bb6a900d02939852b14a97c",
    "app/src/main/res/xml/network_security_config.xml": "dbc726438a7e99c69fdfc39146cd51ef8300dacb236843bbce3ecd2a1712a80f",
    "app/src/main/res/xml/data_extraction_rules.xml": "eda9dddce1e895de1efd716fe75e244187bf8f91a3bb7444c6ea1731e51c74a8",
}

def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: release-hardening.py <phase7-source-directory>")
    root = Path(sys.argv[1]).resolve(strict=True)
    build = root / "app/build.gradle.kts"
    manifest = root / "app/src/main/AndroidManifest.xml"
    assert sha(build) == BASE_BUILD_SHA, ("Unexpected Phase 7 app Gradle baseline", sha(build))
    assert sha(manifest) == BASE_MANIFEST_SHA, ("Unexpected Phase 7 manifest baseline", sha(manifest))

    s = build.read_text()
    old = '        versionCode = 44\n        versionName = "0.6.1"'
    new = '        versionCode = 45\n        versionName = "1.0.0"'
    assert s.count(old) == 1
    s = s.replace(old, new)
    anchor = """    buildFeatures {
        compose = true
    }
"""
    hardened = """    val releaseKeystorePath = System.getenv("TCS_RELEASE_KEYSTORE_PATH")
        ?: error("TCS release keystore path is required")
    val releaseStorePassword = System.getenv("TCS_RELEASE_STORE_PASSWORD")
        ?: error("TCS release store password is required")
    val releaseKeyAlias = System.getenv("TCS_RELEASE_KEY_ALIAS")
        ?: error("TCS release key alias is required")
    val releaseKeyPassword = System.getenv("TCS_RELEASE_KEY_PASSWORD")
        ?: error("TCS release key password is required")

    signingConfigs {
        create("release") {
            storeFile = file(releaseKeystorePath)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        getByName("release") {
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        checkDependencies = true
    }

""" + anchor
    assert s.count(anchor) == 1
    build.write_text(s.replace(anchor, hardened))

    m = manifest.read_text()
    old = '        android:allowBackup="false"\n        android:label="Territory Card Studio"'
    new = (
        '        android:allowBackup="false"\n'
        '        android:fullBackupContent="false"\n'
        '        android:dataExtractionRules="@xml/data_extraction_rules"\n'
        '        android:networkSecurityConfig="@xml/network_security_config"\n'
        '        android:label="Territory Card Studio"'
    )
    assert m.count(old) == 1
    manifest.write_text(m.replace(old, new))

    xml = root / "app/src/main/res/xml"
    xml.mkdir(parents=True, exist_ok=True)
    (xml / "network_security_config.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
""")
    (xml / "data_extraction_rules.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
    </device-transfer>
</data-extraction-rules>
""")
    (root / "app/proguard-rules.pro").write_text("""# Territory Card Studio 1.0 release hardening.
# Preserve runtime metadata used by Android/ML Kit while allowing R8 optimization.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
""")

    actual = {name: sha(root / name) for name in EXPECTED}
    assert actual == EXPECTED, (actual, EXPECTED)
    print(json.dumps({
        "schema": "tcs-phase89-release-overlay-v1",
        "releaseVersionCode": 45,
        "releaseVersionName": "1.0.0",
        "releaseMinified": True,
        "releaseResourcesShrunk": True,
        "releaseSigningFromEnvironmentOnly": True,
        "cleartextDisabled": True,
        "privateBackupDisabled": True,
        "files": actual,
        "phase8Complete": False,
        "phase9Complete": False
    }, indent=2))

if __name__ == "__main__":
    main()
