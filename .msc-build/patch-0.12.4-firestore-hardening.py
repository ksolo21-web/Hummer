#!/usr/bin/env python3
from pathlib import Path

root = Path("MyStudyCompanion")
source_rules = Path(".msc-build/firestore-0.12.4.rules")
target_rules = root / "firestore.rules"
build_file = root / "app/build.gradle.kts"

if not source_rules.is_file():
    raise SystemExit("Missing hardened Firestore rules source.")
if not target_rules.is_file():
    raise SystemExit("The 0.12.3 Firestore rules were not reconstructed.")
if not build_file.is_file():
    raise SystemExit("Missing Android app build file.")

rules = source_rules.read_text(encoding="utf-8")
required_rules = [
    "request.resource.data.householdId == resource.data.householdId",
    "allow delete: if false;",
    "match /shared/familyBoard",
    "householdExistsAfter(householdId)",
    "request.resource.data.usedAt == request.time",
]
for marker in required_rules:
    if marker not in rules:
        raise SystemExit(f"Hardened rules are missing required marker: {marker}")
target_rules.write_text(rules, encoding="utf-8")

build = build_file.read_text(encoding="utf-8")
old_code = "versionCode = 27"
old_name = 'versionName = "0.12.3-private-alpha-firebase-family"'
if build.count(old_code) != 1 or build.count(old_name) != 1:
    raise SystemExit("Expected the exact 0.12.3 app identity before hardening.")
build = build.replace(old_code, "versionCode = 28", 1)
build = build.replace(
    old_name,
    'versionName = "0.12.4-private-alpha-firebase-rules-hardened"',
    1,
)
build_file.write_text(build, encoding="utf-8")

print("Applied hardened Firestore rules and My Study Companion 0.12.4 identity.")
