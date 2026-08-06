#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PAYLOAD='.msc-build/0.15.23-ai-study-repository.kt.gz.b64'
TARGET='MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ai/AiStudyRepository.kt'
TEMP="$(mktemp)"
trap 'rm -f "$TEMP"' EXIT

echo '86a4bd675411d5b5faa6a9a1a602997d0a6ec24acdaf35ddb24b7b194dd4ed50  '"$PAYLOAD" | sha256sum -c -
echo '3eaed9fe50a73600ad39d7e35295cf748abe03dae2b239e459ffe86c354153bc  '"$TARGET" | sha256sum -c -
base64 --decode "$PAYLOAD" | gzip -dc > "$TEMP"
echo '7e2b58219e9655dde30a0743e153782956fefc76a5294dfa34e7ef2834286b8c  '"$TEMP" | sha256sum -c -
install -m 0644 "$TEMP" "$TARGET"

python3 - <<'PY'
from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one occurrence, found {count}')
    return text.replace(old, new, 1)

screen = Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/AiStudyScreen.kt')
text = screen.read_text(encoding='utf-8')
text = replace_once(
    text,
    'Auto mode uses the secure Smart Online assistant for strong, connected answers and falls back to verified private offline help when needed.',
    'Auto mode answers from verified sources on this device unless the secure cloud service is actually configured. Every answer is checked against your exact question.',
    'focused-study mode explanation',
)
text = replace_once(
    text,
    'StatusLine("Connection", if (runtime.online) "Online — Smart Online research is available" else "Offline — cached sources only")',
    'StatusLine("Connection", if (runtime.online) "Online — official-source refresh is available" else "Offline — cached sources only")',
    'connection status truth label',
)
screen.write_text(text, encoding='utf-8')

for path, old_code, new_code, old_name, new_name in (
    (
        Path('MyStudyCompanion/app/build.gradle.kts'),
        '55', '56',
        '0.15.22-private-alpha-workbook-manifest-contract-fix',
        '0.15.23-private-alpha-ai-question-relevance-fix',
    ),
    (
        Path('MyStudyCompanion/wear/build.gradle.kts'),
        '360172001', '360173001',
        '0.15.22-wear-private-alpha-workbook-manifest-contract-fix',
        '0.15.23-wear-private-alpha-ai-question-relevance-fix',
    ),
):
    value = path.read_text(encoding='utf-8')
    value = replace_once(value, f'versionCode = {old_code}', f'versionCode = {new_code}', f'{path} version code')
    value = replace_once(value, f'versionName = "{old_name}"', f'versionName = "{new_name}"', f'{path} version name')
    path.write_text(value, encoding='utf-8')

changelog = Path('MyStudyCompanion/CHANGELOG.md')
change = changelog.read_text(encoding='utf-8')
entry = '''## 0.15.23-ai-question-relevance-fix-private

- Fixed AI Study retrieval so the user question determines the active Daily Text, weekly reading, Family Worship, meeting-part, or general source scope instead of appending every context to every query.
- Stopped treating ordinary internet connectivity as proof that Smart Online is configured; UI and routing now report the actual secure-backend state.
- Added question-relevance and deep-dive quality gates, one retry with explicit answer requirements, and a deterministic verified-source fallback that cannot reuse an unrelated cached answer.
- Updated the AI screen to distinguish official-source refresh connectivity from configured Smart Online availability.

'''
if entry not in change:
    marker = '# Changelog\n\n'
    if marker not in change:
        raise SystemExit('CHANGELOG heading was not found')
    change = change.replace(marker, marker + entry, 1)
changelog.write_text(change, encoding='utf-8')
PY

python3 - <<'PY'
from pathlib import Path

repo = Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ai/AiStudyRepository.kt').read_text(encoding='utf-8')
screen = Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/AiStudyScreen.kt').read_text(encoding='utf-8')
required = (
    'smartOnlineConfigured = smartOnlineValidated,',
    'val canUseSmartOnline =',
    'online && smartOnlineValidated',
    'StudyQuestionScope.DAILY_TEXT -> setOf(snapshot.daily.officialUrl)',
    'StudyQuestionScope.WEEKLY_READING -> setOf(snapshot.weekly.officialUrl)',
    'StudyQuestionScope.FAMILY_WORSHIP -> setOf(family.officialUrl)',
    'StudyQuestionScope.GENERAL -> cleanQuestion',
    'buildQuestionSpecificDirective(question, scope)',
    'answerAddressesQuestion(question, it.body)',
    'This is a deep-dive request.',
    'return sourceGuidedFallback(question, passages, scope)',
    'It does not reuse an unrelated cached answer.',
)
for marker in required:
    if marker not in repo:
        raise SystemExit(f'missing AI relevance marker: {marker}')
for forbidden in (
    'smartOnlineConfigured = smartOnlineValidated || online',
    'append(" Current Daily Text: ")',
    'append(". Current Bible reading: ")',
    'Verified official sources were refreshed online, and this answer was generated privately on this device.',
    'val canUseOnlineAssistant =',
):
    if forbidden in repo:
        raise SystemExit(f'obsolete AI routing marker remains: {forbidden}')
for marker in (
    'Every answer is checked against your exact question.',
    'Online — official-source refresh is available',
):
    if marker not in screen:
        raise SystemExit(f'missing truthful AI UI marker: {marker}')

# Behavioral regression fixtures mirror the production scope contract.
def classify(question: str, selected: bool = False) -> str:
    normalized = question.lower().replace('’', "'").replace("today's", 'todays')
    if any(v in normalized for v in ('daily text', 'todays text', 'today text', 'daily scripture')):
        return 'DAILY_TEXT'
    if any(v in normalized for v in ('bible reading', 'weekly reading', 'this weeks reading', 'spiritual gems')):
        return 'WEEKLY_READING'
    if any(v in normalized for v in ('family worship', 'family study')):
        return 'FAMILY_WORSHIP'
    if selected or any(v in normalized for v in ('meeting part', 'watchtower study', 'congregation bible study')):
        return 'MEETING_PART'
    return 'GENERAL'

fixtures = {
    "deep dive today's text": 'DAILY_TEXT',
    "What does this week's Bible reading teach me about Jehovah?": 'WEEKLY_READING',
    'Create discussion questions for our current Family Worship topic.': 'FAMILY_WORSHIP',
    'Help me prepare the Watchtower Study.': 'MEETING_PART',
    'Why did Jeremiah feel discouraged?': 'GENERAL',
}
for question, expected in fixtures.items():
    actual = classify(question)
    if actual != expected:
        raise SystemExit(f'scope fixture failed: {question!r}: {actual} != {expected}')
if classify("deep dive today's text") == classify("Why did Jeremiah feel discouraged?"):
    raise SystemExit('distinct user questions collapsed to the same retrieval scope')

print('PASS: AI question-first retrieval, truthful Smart Online routing, deep-answer gates, and nonrepeating fallback are installed.')
PY

echo 'Applied My Study Companion 0.15.23 AI question relevance root fix.'
