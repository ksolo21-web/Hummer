#!/usr/bin/env bash
set -euo pipefail

archive="/tmp/msc-real-workbook-art-pages-0.14.1.py.xz"
script="/tmp/msc-real-workbook-art-pages-0.14.1.py"
cat .msc-build/real-workbook-art-pages-0.14.1.part*.b64 | base64 --decode > "$archive"
echo 'af82131a084af3dfbe7e9c5738293e0e6d604b49b1eec98c0ca0de53b6f6bf69  /tmp/msc-real-workbook-art-pages-0.14.1.py.xz' | sha256sum -c -
xz -t "$archive"
xz -dc "$archive" > "$script"
python3 "$script"
node --check MyStudyCompanionWeb/workbook.js

grep -Fq 'drawWorkbookArt' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt
grep -Fq 'drawPdfWorkbookArt' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt
grep -Fq 'renderColorByNumber' MyStudyCompanionWeb/workbook.js
grep -Fq 'artSvg' MyStudyCompanionWeb/workbook.js

echo 'PASS: real drawing pages and color-by-number pages installed in Android, printable PDF, and PWA.'
