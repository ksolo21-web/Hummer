#!/usr/bin/env bash
set -euo pipefail

archive="/tmp/msc-real-workbook-art-pages-0.14.1.py.xz"
script="/tmp/msc-real-workbook-art-pages-0.14.1.py"
log="/tmp/msc-real-workbook-art-pages-0.14.1.log"

parts=(.msc-build/real-workbook-art-pages-0.14.1.part*.b64)
if [[ "${#parts[@]}" -ne 2 ]]; then
  echo "Expected two workbook-art payload parts; found ${#parts[@]}." >&2
  exit 1
fi
cat "${parts[@]}" | tr -d '\r\n\t ' | base64 --decode > "$archive"
actual_sha="$(sha256sum "$archive" | awk '{print $1}')"
echo "Workbook-art payload SHA256: ${actual_sha}"
xz -t "$archive"
xz -dc "$archive" > "$script"
python3 -u "$script" 2>&1 | tee "$log"
node --check MyStudyCompanionWeb/workbook.js

android="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt"
web="MyStudyCompanionWeb/workbook.js"
for marker in drawWorkbookArt drawPdfWorkbookArt detectTapGestures 'Guided drawing canvas'; do
  grep -Fq "$marker" "$android" || { echo "Missing Android workbook-art marker: $marker" >&2; exit 1; }
done
for marker in renderColorByNumber drawArtCanvas artSvg svgArtStrokes; do
  grep -Fq "$marker" "$web" || { echo "Missing PWA workbook-art marker: $marker" >&2; exit 1; }
done

grep -Fq 'PASS: workbook engine now creates real guided drawing pages' "$log" || {
  echo 'Workbook-art patch did not report successful installation.' >&2
  exit 1
}

echo 'PASS: real drawing pages and color-by-number pages installed in Android, printable PDF, and PWA.'
