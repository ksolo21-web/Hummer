from pathlib import Path

source_path = Path('.msc-build/patch-0.12.2-final-link-gate-v2.py')
source = source_path.read_text(encoding='utf-8')
old = '''next_function = '    fun splitBiblePassages(reference: String): List<String> {'
start = text.find(function_start)
next_pos = text.find(next_function, start)
if start < 0 or next_pos < 0:
    raise SystemExit("Could not locate resolver target and multi-passage function boundaries.")
comment_start = text.rfind("    /**", start, next_pos)
if comment_start < 0:
    raise SystemExit("Could not locate multi-passage documentation boundary.")
'''
new = '''start = text.find(function_start)
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
if source.count(old) != 1:
    raise SystemExit('Expected one v2 resolver-boundary block.')
source = source.replace(old, new, 1)
exec(compile(source, str(source_path), 'exec'), {'__name__': '__main__'})
