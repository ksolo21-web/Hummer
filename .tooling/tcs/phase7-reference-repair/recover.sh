#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence tcs-src
printf '%s\n' '12bbae2c2abb7dd820367ba9837476c39e86ae2ca854e4dccea21a6528272965  .tooling/tcs/phase4-evidence/Phase4-Android-0.4.0-source.tar.xz' | sha256sum -c -
tar -xJf .tooling/tcs/phase4-evidence/Phase4-Android-0.4.0-source.tar.xz -C tcs-src
APP=tcs-src/app/src/main/java/com/koenterprises/territorycardstudio
TEST=tcs-src/app/src/androidTest/java/com/koenterprises/territorycardstudio
CORE=tcs-src/core/src/main/kotlin/com/koenterprises/territorycardstudio/core
CTEST=tcs-src/core/src/test/kotlin/com/koenterprises/territorycardstudio/core
for f in .tooling/tcs/phase56-overlay/*.kt; do
  case "$f" in *InstrumentationTest.kt) cp "$f" "$TEST/";; *) cp "$f" "$APP/";; esac
done
cp .tooling/tcs/phase56-overlay/*.java "$TEST/"
cp .tooling/tcs/phase56-overlay/AndroidTestManifest.xml tcs-src/app/src/androidTest/AndroidManifest.xml
cp .tooling/tcs/phase6-overlay/app/*.kt "$APP/"
cp .tooling/tcs/phase6-overlay/androidTest/* "$TEST/"
cp .tooling/tcs/phase6-overlay/app-build.gradle.kts tcs-src/app/build.gradle.kts
cp .tooling/tcs/phase6-overlay/core/*.kt "$CORE/"
cp .tooling/tcs/phase6-overlay/tests/*.kt "$CTEST/"
cp .tooling/tcs/boundary/overlay/core/*.kt "$CORE/"
cp .tooling/tcs/boundary/overlay/tests/*.kt "$CTEST/"
cp .tooling/tcs/boundary/overlay/app/*.kt "$APP/"
cp .tooling/tcs/boundary/overlay/androidTest/*.kt "$TEST/"
cp .tooling/tcs/phase7/Phase7RealImageIntakeInstrumentationTest.kt "$TEST/"
mkdir -p tcs-src/core/src/test/resources/boundary tcs-src/app/src/androidTest/assets
cp 'tcs-src/core/src/test/resources/pdf-boundary/Territory - 300Aa.pdf' tcs-src/app/src/androidTest/assets/reference300.pdf
base64 -d .tooling/tcs/boundary/source-26435.jpg.b64 > tcs-src/core/src/test/resources/boundary/source-26435.jpg
cp tcs-src/core/src/test/resources/boundary/source-26435.jpg tcs-src/app/src/androidTest/assets/source-26435.jpg
for source in .tooling/tcs/phase7/inputs/*.jpg; do cp "$source" "tcs-src/app/src/androidTest/assets/phase7-$(basename "$source")"; done
cp .tooling/tcs/phase7/inputs/reference-a265.pdf tcs-src/app/src/androidTest/assets/phase7-reference-a265.pdf
python3 .tooling/tcs/phase7/commissioning/apply.py tcs-src > evidence/commissioning-integration.json
python3 .tooling/tcs/phase7-reference-repair/apply.py tcs-src > evidence/reference-repair-integration.json
