#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path('.')
APP = ROOT / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app'
WEAR = ROOT / 'MyStudyCompanion/wear/src/main/java/com/mystudycompanion/app/wear'
WEB = ROOT / 'MyStudyCompanionWeb'

AVAILABLE_ENUMS = (
    'CALM_LIGHT',
    'PREMIUM_DARK',
    'WARM_EDITORIAL',
    'OWL',
    'FOX',
    'LION',
    'TIGER',
    'GOLDEN_OWL',
    'SAKURA_TIGER',
    'AUTOMATIC',
)
AVAILABLE_WEB_KEYS = (
    'automatic',
    'calm_light',
    'premium_dark',
    'warm_editorial',
    'owl',
    'fox',
    'lion',
    'tiger',
    'golden_owl',
    'sakura_tiger',
)
AVAILABLE_WEAR_KEYS = tuple(key.upper() for key in AVAILABLE_WEB_KEYS if key != 'automatic')
REMOVED_DISPLAY_NAMES = (
    'Moonlit Wolf',
    'Waterfall Serenity',
    'Rainforest Harmony',
    'Ocean Majesty',
    'Celestial Wonder',
    'Mountain Sunrise',
    'Creation Garden',
    'Bible Sketch Study',
    'Parable Line Panels',
    'Noah’s Ark',
    'Red Sea Deliverance',
    'Creation Sky',
    'Bible Timeline',
    'Bible Map',
)


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding='utf-8')
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one anchor in {path}, found {count}')
    path.write_text(text.replace(old, new, 1), encoding='utf-8')


# Keep the enum values for migration and source compatibility, but expose only
# the final 0.14.1 release set. Any previously selected removed theme migrates
# safely to Calm Light instead of crashing or leaving an invisible selection.
mode_file = APP / 'design/AppThemeMode.kt'
mode_text = mode_file.read_text(encoding='utf-8')
companion = '''

    companion object {
        val availableEntries: List<AppThemeMode> = listOf(
            CALM_LIGHT,
            PREMIUM_DARK,
            WARM_EDITORIAL,
            OWL,
            FOX,
            LION,
            TIGER,
            GOLDEN_OWL,
            SAKURA_TIGER,
            AUTOMATIC,
        )

        private val availableSet: Set<AppThemeMode> = availableEntries.toSet()

        fun normalizeForRelease(mode: AppThemeMode): AppThemeMode =
            if (mode in availableSet) mode else CALM_LIGHT
    }
'''
if 'val availableEntries: List<AppThemeMode>' not in mode_text:
    closing = mode_text.rfind('\n}')
    if closing < 0:
        raise SystemExit('Could not locate AppThemeMode enum closing brace.')
    mode_text = mode_text[:closing] + companion + mode_text[closing:]
    mode_file.write_text(mode_text, encoding='utf-8')

settings = APP / 'ui/SettingsScreen.kt'
replace_once(
    settings,
    'items(AppThemeMode.entries, key = { it.name }) { mode ->',
    'items(AppThemeMode.availableEntries, key = { it.name }) { mode ->',
    'theme picker release allowlist',
)

store = APP / 'design/ThemeStore.kt'
replace_once(
    store,
    '''    fun set(mode: AppThemeMode) {
        preferences.edit().putString(KEY_THEME, mode.name).apply()
        mutableTheme.value = mode
    }
''',
    '''    fun set(mode: AppThemeMode) {
        val normalized = AppThemeMode.normalizeForRelease(mode)
        preferences.edit().putString(KEY_THEME, normalized.name).apply()
        mutableTheme.value = normalized
    }
''',
    'theme write migration',
)
replace_once(
    store,
    '''    fun read(): AppThemeMode = runCatching {
        AppThemeMode.valueOf(preferences.getString(KEY_THEME, AppThemeMode.CALM_LIGHT.name)!!)
    }.getOrDefault(AppThemeMode.CALM_LIGHT)
''',
    '''    fun read(): AppThemeMode = AppThemeMode.normalizeForRelease(
        runCatching {
            AppThemeMode.valueOf(preferences.getString(KEY_THEME, AppThemeMode.CALM_LIGHT.name)!!)
        }.getOrDefault(AppThemeMode.CALM_LIGHT),
    )
''',
    'theme read migration',
)
replace_once(
    store,
    '''        AppThemeMode.entries
            .filterNot { it == AppThemeMode.AUTOMATIC }
''',
    '''        AppThemeMode.availableEntries
            .filterNot { it == AppThemeMode.AUTOMATIC }
''',
    'theme customization release allowlist',
)

# Normalize Wear snapshots immediately so an old synced Moonlit Wolf or one of
# the removed illustrated themes cannot remain visible on a watch after update.
wear_snapshot = WEAR / 'WearStudySnapshot.kt'
wear_text = wear_snapshot.read_text(encoding='utf-8')
wear_helper = '''

private val RELEASE_WEAR_THEME_MODES = setOf(
    "CALM_LIGHT",
    "PREMIUM_DARK",
    "WARM_EDITORIAL",
    "OWL",
    "FOX",
    "LION",
    "TIGER",
    "GOLDEN_OWL",
    "SAKURA_TIGER",
)

fun normalizeWearThemeMode(themeMode: String?): String =
    themeMode?.takeIf { it in RELEASE_WEAR_THEME_MODES } ?: "CALM_LIGHT"
'''
if 'fun normalizeWearThemeMode(themeMode: String?)' not in wear_text:
    wear_text += wear_helper
    wear_snapshot.write_text(wear_text, encoding='utf-8')

wear_store = WEAR / 'WearSnapshotStore.kt'
replace_once(
    wear_store,
    'themeMode = dataMap.getString(WearDataContract.THEME_MODE) ?: "CALM_LIGHT",',
    'themeMode = normalizeWearThemeMode(dataMap.getString(WearDataContract.THEME_MODE)),',
    'Wear incoming theme migration',
)
replace_once(
    wear_store,
    'themeMode = preferences.getString(WearDataContract.THEME_MODE, "CALM_LIGHT")!!,',
    'themeMode = normalizeWearThemeMode(preferences.getString(WearDataContract.THEME_MODE, "CALM_LIGHT")),',
    'Wear stored theme migration',
)

# The PWA keeps the old definitions only as migration-compatible source data,
# while the picker, selection API, and stored-theme restoration are restricted
# to the nine approved visual themes plus Automatic.
appearance = WEB / 'appearance.js'
appearance_text = appearance.read_text(encoding='utf-8')
release_set = 'const RELEASE_THEME_KEYS = new Set(["automatic","calm_light","premium_dark","warm_editorial","owl","fox","lion","tiger","golden_owl","sakura_tiger"]);\n'
if 'const RELEASE_THEME_KEYS = new Set(' not in appearance_text:
    marker = 'const ROLE_LABELS = '
    index = appearance_text.find(marker)
    if index < 0:
        raise SystemExit('Could not locate PWA appearance role labels.')
    appearance_text = appearance_text[:index] + release_set + appearance_text[index:]
appearance_text = appearance_text.replace(
    'const themeKey=APPEARANCE_THEMES[saved.themeKey]?saved.themeKey:"automatic";',
    'const themeKey=RELEASE_THEME_KEYS.has(saved.themeKey)?saved.themeKey:"automatic";',
    1,
)
appearance_text = appearance_text.replace(
    'function chooseTheme(key){const theme=resolvedTheme(key);if(!theme)return;',
    'function chooseTheme(key){if(!RELEASE_THEME_KEYS.has(key))key="calm_light";const theme=resolvedTheme(key);if(!theme)return;',
    1,
)
appearance_text = appearance_text.replace(
    'Object.entries(APPEARANCE_THEMES).forEach(([key,raw])=>{',
    'Object.entries(APPEARANCE_THEMES).filter(([key])=>RELEASE_THEME_KEYS.has(key)).forEach(([key,raw])=>{',
    1,
)
appearance.write_text(appearance_text, encoding='utf-8')

# Add a focused regression test without removing the existing auth and feature
# tests. This proves the old themes may exist only as migration definitions and
# cannot be selected or rendered in the final release gallery.
test_file = WEB / 'appearance.test.mjs'
test_text = test_file.read_text(encoding='utf-8')
release_test = '''

test("0.14.1 release gallery is limited to the original seven plus Golden Owl and Sakura Tiger",()=>{
  assert.ok(appearance.includes('const RELEASE_THEME_KEYS = new Set(["automatic","calm_light","premium_dark","warm_editorial","owl","fox","lion","tiger","golden_owl","sakura_tiger"])'));
  assert.ok(appearance.includes('filter(([key])=>RELEASE_THEME_KEYS.has(key))'));
  assert.ok(appearance.includes('RELEASE_THEME_KEYS.has(saved.themeKey)?saved.themeKey:"automatic"'));
});
'''
if '0.14.1 release gallery is limited' not in test_text:
    test_file.write_text(test_text.rstrip() + release_test, encoding='utf-8')

# Final verification: exact release set, migration guards, Wear normalization,
# PWA picker restriction, and the already repaired Google sign-in path.
mode_text = mode_file.read_text(encoding='utf-8')
for enum_name in AVAILABLE_ENUMS:
    if enum_name not in mode_text:
        raise SystemExit(f'Missing approved release theme: {enum_name}')
if 'items(AppThemeMode.availableEntries' not in settings.read_text(encoding='utf-8'):
    raise SystemExit('Android theme picker is not using the release allowlist.')
store_text = store.read_text(encoding='utf-8')
if store_text.count('normalizeForRelease') < 2:
    raise SystemExit('Android removed-theme migration is incomplete.')
if wear_store.read_text(encoding='utf-8').count('normalizeWearThemeMode') < 2:
    raise SystemExit('Wear removed-theme migration is incomplete.')
appearance_text = appearance.read_text(encoding='utf-8')
for key in AVAILABLE_WEB_KEYS:
    if f'"{key}"' not in release_set:
        raise SystemExit(f'PWA release key missing: {key}')
if 'filter(([key])=>RELEASE_THEME_KEYS.has(key))' not in appearance_text:
    raise SystemExit('PWA picker is not restricted to the release set.')
auth = (WEB / 'firebase-sync.js').read_text(encoding='utf-8')
for required in ('browserLocalPersistence', 'getRedirectResult', 'signInWithPopup', 'auth/popup-blocked'):
    if required not in auth:
        raise SystemExit(f'Google sign-in repair was lost: {required}')

print('PASS: 0.14.1 exposes only Calm Light, Premium Dark, Warm Editorial, Owl, Fox, Lion, Tiger, Golden Owl, Sakura Tiger, and Automatic across Android, Wear OS, and PWA; removed selections migrate safely to Calm Light.')
