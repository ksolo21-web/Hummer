#!/usr/bin/env python3
from pathlib import Path
import re

root = Path('.')

rejected_enum = (
    'WATERFALL_SERENITY',
    'RAINFOREST_HARMONY',
    'OCEAN_MAJESTY',
    'CELESTIAL_WONDER',
    'MOUNTAIN_SUNRISE',
    'CREATION_GARDEN',
    'BIBLE_SKETCH_STUDY',
    'PARABLE_LINE_PANELS',
    'NOAHS_ARK',
    'RED_SEA_DELIVERANCE',
    'CREATION_SKY',
    'BIBLE_TIMELINE',
    'BIBLE_MAP',
)

rejected_slugs = (
    'waterfall_serenity',
    'rainforest_harmony',
    'ocean_majesty',
    'celestial_wonder',
    'mountain_sunrise',
    'creation_garden',
    'bible_sketch_study',
    'parable_line_panels',
    'noahs_ark',
    'red_sea_deliverance',
    'creation_sky',
    'bible_timeline',
    'bible_map',
)

# The approved-theme repair removed the rejected enum constants, but the
# expanded-gallery overlay also added downstream widget branches. Remove those
# branches so the phone source remains exhaustive and compile-safe.
widget = root / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/widget/DailyStudyWidget.kt'
widget_source = widget.read_text(encoding='utf-8')
for enum_name in rejected_enum:
    pattern = re.compile(
        rf'^        AppThemeMode\.{re.escape(enum_name)} -> WidgetRawPalette\(\n'
        rf'.*?'
        rf'^        \)\n',
        flags=re.M | re.S,
    )
    widget_source, count = pattern.subn('', widget_source, count=1)
    if count not in (0, 1):
        raise SystemExit(f'Unexpected widget branch count for {enum_name}: {count}')
widget.write_text(widget_source, encoding='utf-8')

# Wear uses string theme identities, so stale branches still compile until they
# reference removed resources. Delete both palette and artwork mappings.
wear_theme = root / 'MyStudyCompanion/wear/src/main/java/com/mystudycompanion/app/wear/WearTheme.kt'
wear_theme_source = wear_theme.read_text(encoding='utf-8')
for enum_name in rejected_enum:
    wear_theme_source = re.sub(
        rf'^    "{re.escape(enum_name)}" -> WearPalette\(.*\)\n',
        '',
        wear_theme_source,
        flags=re.M,
    )
wear_theme.write_text(wear_theme_source, encoding='utf-8')

wear_art = root / 'MyStudyCompanion/wear/src/main/java/com/mystudycompanion/app/wear/WearThemeArtwork.kt'
wear_art_source = wear_art.read_text(encoding='utf-8')
for enum_name in rejected_enum:
    wear_art_source = re.sub(
        rf'^    "{re.escape(enum_name)}" -> R\.drawable\.theme_scene_[a-z0-9_]+\n',
        '',
        wear_art_source,
        flags=re.M,
    )
wear_art.write_text(wear_art_source, encoding='utf-8')

# Replace the obsolete 13-theme test with a permanent-scope test that protects
# the three original themes and seven approved animal themes.
wear_test = root / 'MyStudyCompanion/wear/src/test/java/com/mystudycompanion/app/wear/WearThemeTest.kt'
wear_test_source = wear_test.read_text(encoding='utf-8')
obsolete_test = re.compile(
    r'\n    @Test\n'
    r'    fun allIllustratedPhoneThemesHaveWearPalettes\(\) \{\n'
    r'.*?'
    r'\n    \}\n',
    flags=re.S,
)
approved_test = '''
    @Test
    fun allApprovedPhoneThemesHaveWearPalettes() {
        val modes = listOf(
            "CALM_LIGHT", "PREMIUM_DARK", "WARM_EDITORIAL",
            "OWL", "FOX", "LION", "TIGER",
            "MOONLIT_WOLF", "GOLDEN_OWL", "SAKURA_TIGER",
        )
        val palettes = modes.map(::wearPalette)
        assertEquals(10, palettes.size)
        assertEquals(10, palettes.map { it.motif }.toSet().size)
    }
'''
wear_test_source, count = obsolete_test.subn(approved_test, wear_test_source, count=1)
if count == 0 and 'allApprovedPhoneThemesHaveWearPalettes' not in wear_test_source:
    raise SystemExit('Could not replace the obsolete Wear theme-gallery test.')
wear_test.write_text(wear_test_source, encoding='utf-8')

# Remove every rejected scene from all packaged targets. This is idempotent and
# also catches assets restored by a future overlay ordering change.
asset_roots = (
    root / 'MyStudyCompanion/app/src/main/res/drawable-nodpi',
    root / 'MyStudyCompanion/wear/src/main/res/drawable-nodpi',
    root / 'MyStudyCompanionWeb/assets',
)
for asset_root in asset_roots:
    for slug in rejected_slugs:
        for path in asset_root.glob(f'theme_scene_{slug}.*'):
            path.unlink()

# Release gates: no rejected runtime identity or removed drawable may survive.
source_roots = (
    root / 'MyStudyCompanion/app/src/main',
    root / 'MyStudyCompanion/app/src/test',
    root / 'MyStudyCompanion/wear/src/main',
    root / 'MyStudyCompanion/wear/src/test',
)
for source_root in source_roots:
    for path in source_root.rglob('*'):
        if not path.is_file() or path.suffix not in {'.kt', '.kts', '.xml'}:
            continue
        text = path.read_text(encoding='utf-8', errors='ignore')
        for enum_name in rejected_enum:
            if enum_name in text:
                raise SystemExit(f'Rejected theme identity remains: {enum_name} in {path}')
        for slug in rejected_slugs:
            if f'theme_scene_{slug}' in text:
                raise SystemExit(f'Rejected theme drawable remains: {slug} in {path}')

for asset_root in asset_roots:
    for slug in rejected_slugs:
        if any(asset_root.glob(f'theme_scene_{slug}.*')):
            raise SystemExit(f'Rejected theme asset remains: {slug} in {asset_root}')

approved_slugs = (
    'calm_light', 'premium_dark', 'warm_editorial',
    'owl', 'fox', 'lion', 'tiger',
    'moonlit_wolf', 'golden_owl', 'sakura_tiger',
)
for asset_root in asset_roots:
    for slug in approved_slugs:
        matches = tuple(asset_root.glob(f'theme_scene_{slug}.*'))
        if not matches or not any(path.stat().st_size > 1000 for path in matches):
            raise SystemExit(f'Approved theme asset missing or empty: {slug} in {asset_root}')

print('Removed all rejected theme branches, tests, drawables, and Wear mappings while preserving the 10 approved themes.')
