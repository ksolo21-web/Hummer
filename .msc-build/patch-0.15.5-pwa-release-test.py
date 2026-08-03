from pathlib import Path
import sys

root = Path(sys.argv[1])
test = root / "MyStudyCompanionWeb/appearance.test.mjs"
text = test.read_text()
old = "msc-web-v0154-professional-workbook-household-v1"
new = "msc-web-v0155-household-cancellation-v1"
assert old in text or new in text
text = text.replace(old, new)
test.write_text(text)
print("Updated PWA cache assertion for 0.15.5.")
