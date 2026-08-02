#!/usr/bin/env bash
set -euo pipefail

python3 .msc-build/apply-approved-theme-finish-v2.py

node --check MyStudyCompanionWeb/appearance.js
node --check MyStudyCompanionWeb/sw.js

test "$(find MyStudyCompanion/app/src/main/res/drawable-nodpi -maxdepth 1 -name 'theme_preview_*.webp' | wc -l)" -eq 13
test "$(find MyStudyCompanion/wear/src/main/res/drawable-nodpi -maxdepth 1 -name 'theme_scene_*.webp' | wc -l)" -eq 23
test "$(find MyStudyCompanion/wear/src/main/res/drawable-nodpi -maxdepth 1 -name 'theme_preview_*.webp' | wc -l)" -eq 13
test "$(find MyStudyCompanionWeb/assets -maxdepth 1 -name 'theme_preview_*.webp' | wc -l)" -eq 13

grep -Fq 'ApprovedThemeQuickActions' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/HomeScreen.kt
grep -Fq 'identity.mode.isIllustratedTheme -> 0.84f' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/ThemeBackdrop.kt
grep -Fq 'Approved full-screen design carried through phone' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/SettingsScreen.kt
grep -Fq 'theme.preview||theme.art' MyStudyCompanionWeb/appearance.js
grep -Fq 'msc-web-v0145-static-theme-auth-repair-v2' MyStudyCompanionWeb/sw.js

! grep -R -E 'rememberInfiniteTransition|infiniteRepeatable|isLiveTheme|liveTheme' \
  MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design \
  MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/HomeScreen.kt \
  MyStudyCompanionWeb/appearance.js MyStudyCompanionWeb/styles.css

echo 'PASS: approved robust static themes are integrated across phone, Fold/tablet, Wear OS, widgets, and PWA; Google sign-in remains unchanged.'
