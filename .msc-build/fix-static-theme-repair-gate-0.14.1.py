#!/usr/bin/env python3
from pathlib import Path

path = Path('.msc-build/apply-static-theme-auth-repair-0.14.1.py')
source = path.read_text(encoding='utf-8')
old = '''mapping_paths = (
    ROOT / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/ThemeArtwork.kt',
    ROOT / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/widget/DailyStudyWidget.kt',
    ROOT / 'MyStudyCompanion/wear/src/main/java/com/mystudycompanion/app/wear/WearTheme.kt',
    ROOT / 'MyStudyCompanion/wear/src/main/java/com/mystudycompanion/app/wear/WearThemeArtwork.kt',
)
for enum_name in STATIC_THEME_ENUMS:
    for path in mapping_paths:
        if enum_name not in path.read_text(encoding='utf-8', errors='ignore'):
            raise SystemExit(f'Static theme mapping missing: {enum_name} in {path}')
'''
new = '''explicit_mapping_paths = (
    ROOT / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/ThemeArtwork.kt',
    ROOT / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/widget/DailyStudyWidget.kt',
    ROOT / 'MyStudyCompanion/wear/src/main/java/com/mystudycompanion/app/wear/WearThemeArtwork.kt',
)
for enum_name in STATIC_THEME_ENUMS:
    for mapping_path in explicit_mapping_paths:
        if enum_name not in mapping_path.read_text(encoding='utf-8', errors='ignore'):
            raise SystemExit(f'Static theme mapping missing: {enum_name} in {mapping_path}')

# WearTheme.kt deliberately uses its final else branch as the Calm Light
# fallback. Require every non-default theme explicitly and verify that fallback
# rather than falsely demanding a literal CALM_LIGHT branch.
wear_theme_path = ROOT / 'MyStudyCompanion/wear/src/main/java/com/mystudycompanion/app/wear/WearTheme.kt'
wear_theme_source = wear_theme_path.read_text(encoding='utf-8', errors='ignore')
for enum_name in STATIC_THEME_ENUMS:
    if enum_name == 'CALM_LIGHT':
        continue
    if enum_name not in wear_theme_source:
        raise SystemExit(f'Static Wear palette missing: {enum_name} in {wear_theme_path}')
if 'else -> WearPalette(' not in wear_theme_source:
    raise SystemExit('Calm Light Wear fallback palette is missing.')
'''
if old not in source:
    if new in source:
        print('Static-theme mapping gate is already corrected.')
        raise SystemExit(0)
    raise SystemExit('Could not locate the obsolete Wear mapping gate.')
path.write_text(source.replace(old, new, 1), encoding='utf-8')
print('Corrected the Wear static-theme gate: Calm Light is validated through its intentional fallback palette.')
