#!/usr/bin/env python3
from pathlib import Path

MODEL = Path(
    "MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/companion/"
    "InteractiveWorkbookModels.kt"
)
EDITOR = Path(
    "MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/"
    "InteractiveWorkbookEditor.kt"
)

model = MODEL.read_text(encoding="utf-8")
old_model_declaration = "        val fun = when ((index + audience.ordinal) % 4) {"
new_model_declaration = "        val familyActivity = when ((index + audience.ordinal) % 4) {"
old_model_return = "        return listOf(listen, fun, WorkbookActivityDefinition("
new_model_return = "        return listOf(listen, familyActivity, WorkbookActivityDefinition("

if old_model_declaration in model:
    model = model.replace(old_model_declaration, new_model_declaration, 1)
elif new_model_declaration not in model:
    raise SystemExit("Interactive workbook family activity declaration was not found.")

if old_model_return in model:
    model = model.replace(old_model_return, new_model_return, 1)
elif new_model_return not in model:
    raise SystemExit("Interactive workbook family activity return was not found.")

MODEL.write_text(model, encoding="utf-8")

editor = EDITOR.read_text(encoding="utf-8")
experimental_import = "import androidx.compose.material3.ExperimentalMaterial3Api\n"
if experimental_import not in editor:
    import_anchor = "import androidx.compose.material3.FilterChip\n"
    if import_anchor not in editor:
        raise SystemExit("Material 3 import anchor was not found.")
    editor = editor.replace(import_anchor, import_anchor + experimental_import, 1)

annotation_anchor = "@Composable\nfun InteractiveWorkbookDialog("
annotated_anchor = "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun InteractiveWorkbookDialog("
if annotated_anchor not in editor:
    if annotation_anchor not in editor:
        raise SystemExit("InteractiveWorkbookDialog declaration was not found.")
    editor = editor.replace(annotation_anchor, annotated_anchor, 1)

EDITOR.write_text(editor, encoding="utf-8")

fixed_model = MODEL.read_text(encoding="utf-8")
fixed_editor = EDITOR.read_text(encoding="utf-8")
assert "val familyActivity = when" in fixed_model
assert "listOf(listen, familyActivity," in fixed_model
assert "val fun = when" not in fixed_model
assert "@OptIn(ExperimentalMaterial3Api::class)" in fixed_editor
print("Applied compile-safe interactive workbook source fixes.")
