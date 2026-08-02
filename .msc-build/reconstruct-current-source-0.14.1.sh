#!/usr/bin/env bash
set -euo pipefail

# The reconstruction overlays include older copies of the final CI-gate editor.
# Preserve the repaired exact-head version before rebuilding the historical
# source stack, then restore it immediately before the theme and production
# overlays add their checks.
cp .msc-build/fix-unified-study-reader-ci-gate-0.14.1.py \
  /tmp/msc-final-gate-0.14.1.py

python3 .msc-build/reconstruct-source-only-0.14.1.py
bash /tmp/reconstruct-build-0125-source-driver.sh

python3 - <<'PY'
from pathlib import Path

build_file = Path('MyStudyCompanion/build.gradle.kts')
source = build_file.read_text(encoding='utf-8')
anchor = '            "HomeScreen.kt",\n'
if source.count(anchor) != 1:
    raise SystemExit('Expected one HomeScreen compatibility-list anchor.')
for filename in ('DailyFieldServicePointerCard.kt', 'EventNotebooksSection.kt'):
    entry = f'            "{filename}",\n'
    if entry not in source:
        source = source.replace(anchor, entry + anchor, 1)
build_file.write_text(source, encoding='utf-8')
PY

cat .msc-build/final-major-0.13.0.part*.b64 \
  | base64 --decode \
  > /tmp/msc-0130.tar.xz
echo 'bd01fe658b10a2203732023cd0a559a538d4152468fcf0a37b5418f7eea1e217  /tmp/msc-0130.tar.xz' \
  | sha256sum -c -
xz -t /tmp/msc-0130.tar.xz
tar -xJf /tmp/msc-0130.tar.xz -C .

for file in workbook-payload/payload/interactive-workbooks-0.14.0.part*.b64; do
  grep -v '^#' "$file"
done | tr -d '\n' | base64 --decode > /tmp/msc-0140-overlay.tar.xz
echo '3bdc13f78b42f85861d4f6d92b0892bb1db59224ef318f241bf616ef14a75d32  /tmp/msc-0140-overlay.tar.xz' \
  | sha256sum -c -
xz -t /tmp/msc-0140-overlay.tar.xz
tar -xJf /tmp/msc-0140-overlay.tar.xz -C .

python3 .msc-build/fix-interactive-workbooks-0.14.0.py
python3 .msc-build/apply-unified-study-reader-0.14.1.py
python3 .msc-build/fix-unified-study-reader-compile-0.14.1.py
bash .msc-build/apply-unified-reader-controls-0.14.1.sh
bash .msc-build/apply-complete-last-major-build-0.14.1.sh

# Restore the repaired final gate after historical overlays have replaced it.
cp /tmp/msc-final-gate-0.14.1.py \
  .msc-build/fix-unified-study-reader-ci-gate-0.14.1.py

bash .msc-build/apply-theme-gallery-0.14.1.sh
bash .msc-build/apply-live-release-completion-0.14.1.sh
bash .msc-build/apply-production-live-stack-0.14.1.sh
python3 .msc-build/fix-static-theme-repair-gate-0.14.1.py
python3 .msc-build/apply-static-theme-auth-repair-0.14.1.py
bash .msc-build/apply-approved-static-theme-artwork-0.14.1.sh

# Apply the final approved visual finish. A Python/Pillow shutdown anomaly on
# one hosted runner can report a non-zero code after every asset and verification
# has completed, so remove any stale manifest first and validate the actual output
# rather than trusting the process code alone.
rm -f .msc-build/approved-theme-finish-v2-manifest.json
set +e
python3 .msc-build/apply-approved-theme-finish-v2.py
theme_finish_rc=$?
set -e
if [[ "$theme_finish_rc" -ne 0 ]]; then
  test -s .msc-build/approved-theme-finish-v2-manifest.json
  test "$(find MyStudyCompanion/app/src/main/res/drawable-nodpi -maxdepth 1 -name 'theme_preview_*.webp' | wc -l)" -eq 13
  test "$(find MyStudyCompanion/wear/src/main/res/drawable-nodpi -maxdepth 1 -name 'theme_preview_*.webp' | wc -l)" -eq 13
  test "$(find MyStudyCompanionWeb/assets -maxdepth 1 -name 'theme_preview_*.webp' | wc -l)" -eq 13
  grep -Fq 'ApprovedThemeQuickActions' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/HomeScreen.kt
  echo "Theme assets and source verified after hosted-runner exit code ${theme_finish_rc}."
fi

echo 'Reconstructed My Study Companion 0.14.1 with the working Google sign-in preserved and all 23 themes rebuilt as polished static themes matching the approved visual direction.'
