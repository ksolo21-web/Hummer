from pathlib import Path

source_path = Path('.msc-build/patch-0.12.2-final-link-gate-v2.py')
source = source_path.read_text(encoding='utf-8')

old_boundary = '''next_function = '    fun splitBiblePassages(reference: String): List<String> {'
start = text.find(function_start)
next_pos = text.find(next_function, start)
if start < 0 or next_pos < 0:
    raise SystemExit("Could not locate resolver target and multi-passage function boundaries.")
comment_start = text.rfind("    /**", start, next_pos)
if comment_start < 0:
    raise SystemExit("Could not locate multi-passage documentation boundary.")
'''
new_boundary = '''start = text.find(function_start)
if start < 0:
    raise SystemExit("Could not locate targetFromOfficialUrl.")
search_from = start + len(function_start)
next_candidates = [
    position for position in (
        text.find("\\n    fun ", search_from),
        text.find("\\n    private fun ", search_from),
        text.find("\\n    internal fun ", search_from),
    )
    if position >= 0
]
if not next_candidates:
    raise SystemExit("Could not locate the Kotlin function following targetFromOfficialUrl.")
next_pos = min(next_candidates) + 1
comment_start = text.rfind("    /**", start, next_pos)
if comment_start < start:
    comment_start = next_pos
'''
if source.count(old_boundary) != 1:
    raise SystemExit('Expected one v2 resolver-boundary block.')
source = source.replace(old_boundary, new_boundary, 1)

old_overview_condition = '''if old_overview_button in text:
    text = text.replace(old_overview_button, new_overview_button, 1)
elif new_overview_button not in text:
    raise SystemExit("Family overview button anchor changed.")
'''
new_overview_condition = '''if old_overview_button in text:
    text = text.replace(old_overview_button, new_overview_button, 1)
elif new_overview_button not in text:
    overview_function = text.find("private fun FamilyOverviewCard(")
    overview_button_start = text.find("                OutlinedButton(", overview_function)
    overview_next_button = text.find("                Button(", overview_button_start)
    if overview_function < 0 or overview_button_start < 0 or overview_next_button < 0:
        raise SystemExit("Could not structurally locate the family overview official-material button.")
    text = text[:overview_button_start] + new_overview_button + text[overview_next_button:]
'''
if source.count(old_overview_condition) != 1:
    raise SystemExit('Expected one v2 family overview condition block.')
source = source.replace(old_overview_condition, new_overview_condition, 1)

old_section_condition = '''if old_section_button in text:
    text = text.replace(old_section_button, new_section_button, 1)
elif new_section_button not in text:
    raise SystemExit("Family section button anchor changed.")
'''
new_section_condition = '''if old_section_button in text:
    text = text.replace(old_section_button, new_section_button, 1)
elif new_section_button not in text:
    section_function = text.find("private fun FamilySectionCard(")
    section_button_start = text.find("            OutlinedButton(", section_function)
    section_button_end = text.find("\\n            }", section_button_start)
    if section_function < 0 or section_button_start < 0 or section_button_end < 0:
        raise SystemExit("Could not structurally locate the family section official-material button.")
    section_button_end += len("\\n            }")
    text = text[:section_button_start] + new_section_button.rstrip("\\n") + text[section_button_end:]
'''
if source.count(old_section_condition) != 1:
    raise SystemExit('Expected one v2 family section condition block.')
source = source.replace(old_section_condition, new_section_condition, 1)

exec(compile(source, str(source_path), 'exec'), {'__name__': '__main__'})
