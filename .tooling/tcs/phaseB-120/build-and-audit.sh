#!/usr/bin/env bash
set -euo pipefail
(cd tcs-src && gradle --no-daemon --build-cache :core:test --rerun) > evidence/core.log 2>&1 || { cat evidence/core.log; exit 1; }
cp -r tcs-src/core/build/test-results/test evidence/core-results
(cd tcs-src && gradle --no-daemon --build-cache :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:bundleRelease) > evidence/build.log 2>&1 || { cat evidence/build.log; exit 1; }
cp tcs-src/app/build/reports/lint-results-release.xml evidence/lint-results-release.xml
cp tcs-src/app/build/outputs/apk/debug/app-debug.apk evidence/Territory-PhaseB-1.2.0-debug.apk
cp tcs-src/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk evidence/Territory-PhaseB-1.2.0-androidTest.apk
cp tcs-src/app/build/outputs/apk/release/app-release.apk evidence/Territory-Android-1.2.0-PhaseB-private.apk
cp tcs-src/app/build/outputs/bundle/release/app-release.aab evidence/Territory-Android-1.2.0-PhaseB-private.aab
sha256sum evidence/*.apk evidence/*.aab > evidence/builds.sha256
AAPT2="$ANDROID_HOME/build-tools/36.0.0/aapt2"
APKSIGNER="$ANDROID_HOME/build-tools/36.0.0/apksigner"
"$AAPT2" dump badging evidence/Territory-Android-1.2.0-PhaseB-private.apk > evidence/badging.txt
grep -q "versionCode='48'" evidence/badging.txt
grep -q "versionName='1.2.0'" evidence/badging.txt
grep -q "application-label:'Territory Card Studio'" evidence/badging.txt
"$APKSIGNER" verify --verbose --print-certs evidence/Territory-Android-1.2.0-PhaseB-private.apk > evidence/apk-signature.txt
unzip -t evidence/Territory-Android-1.2.0-PhaseB-private.apk > evidence/apk-ziptest.txt
unzip -t evidence/Territory-Android-1.2.0-PhaseB-private.aab > evidence/aab-ziptest.txt
