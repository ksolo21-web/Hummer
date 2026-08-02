#!/usr/bin/env python3
from pathlib import Path

models_path = Path(
    'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/studyreader/'
    'UnifiedStudyReaderModels.kt'
)
study_path = Path(
    'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/StudyScreen.kt'
)

models = models_path.read_text(encoding='utf-8')
public_persisted = '    fun persisted(): PersistedStudyLibrary = PersistedStudyLibrary(\n'
internal_persisted = '    internal fun persisted(): PersistedStudyLibrary = PersistedStudyLibrary(\n'
if public_persisted in models:
    models = models.replace(public_persisted, internal_persisted, 1)
elif internal_persisted not in models:
    raise SystemExit('Unified Study Reader persisted() declaration was not found.')
models_path.write_text(models, encoding='utf-8')

study = study_path.read_text(encoding='utf-8')
auto_stories = 'Icons.Outlined.AutoStories'
menu_book = 'Icons.Outlined.MenuBook'
count = study.count(auto_stories)
if count:
    study = study.replace(auto_stories, menu_book)
elif study.count(menu_book) < 3:
    raise SystemExit('Study Reader icon anchors were not found.')
study_path.write_text(study, encoding='utf-8')

fixed_models = models_path.read_text(encoding='utf-8')
fixed_study = study_path.read_text(encoding='utf-8')
assert internal_persisted in fixed_models
assert public_persisted not in fixed_models
assert auto_stories not in fixed_study
assert fixed_study.count(menu_book) >= 3
assert 'import androidx.compose.material.icons.outlined.MenuBook' in fixed_study

print(
    'Applied compile-safe Unified Study Reader persistence visibility and '
    f'{count} supported study-book icon replacement(s).'
)
