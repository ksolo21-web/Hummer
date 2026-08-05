from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    source = path.read_text(encoding="utf-8")
    if old not in source:
        raise SystemExit(f"Missing {label} in {path}")
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


font = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlineStudioBitmapFont.cs")
replace_once(
    font,
    '''            var serializedFont = new SerializedObject(font);
            var fontSize = serializedFont.FindProperty("m_FontSize");
            if (fontSize != null)
                fontSize.intValue = 24;
            var lineSpacing = serializedFont.FindProperty("m_LineSpacing");
            if (lineSpacing != null)
                lineSpacing.floatValue = 1f;
            serializedFont.ApplyModifiedPropertiesWithoutUndo();
''',
    "",
    "unsupported native Font serialization",
)

studio = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlineProceduralArtStudio.cs")
replace_once(
    studio,
    '''                var helper = CreatePanel(safe, "HelperPanel", new Vector2(0f, 0f), new Vector2(24f, 132f), new Vector2(280f, 72f), Navy, 0.78f);
                CreateText(helper.transform, "HelperText", "HELPER: FROZEN", 24, TextAnchor.MiddleCenter);
                var threat = CreatePanel(safe, "ThreatPanel", new Vector2(1f, 0f), new Vector2(-24f, 132f), new Vector2(280f, 72f), Navy, 0.78f);
                CreateText(threat.transform, "ThreatText", "THREAT: QUIET", 24, TextAnchor.MiddleCenter);
''',
    '''                var helper = CreatePanel(safe, "HelperPanel", new Vector2(0f, 0f), new Vector2(24f, 132f), new Vector2(280f, 72f), Navy, 0.78f);
                CreateText(helper.transform, "HelperText", "HELPER: FROZEN", 24, TextAnchor.MiddleCenter);
                helper.SetActive(false);
                var threat = CreatePanel(safe, "ThreatPanel", new Vector2(1f, 0f), new Vector2(-24f, 132f), new Vector2(280f, 72f), Navy, 0.78f);
                CreateText(threat.transform, "ThreatText", "THREAT: QUIET", 24, TextAnchor.MiddleCenter);
                threat.SetActive(false);
                context.SetActive(false);
''',
    "transient HUD default state",
)
replace_once(
    studio,
    '''            var sprite = AssetDatabase.LoadAssetAtPath<Sprite>(UiRoot + "/HAVENLINE_HUD_Atlas.png");
            image.sprite = sprite;
            image.type = Image.Type.Sliced;
''',
    '''            image.sprite = HavenlineStudioUiAssets.Resolve(name);
            image.type = HavenlineStudioUiAssets.ShouldSlice(name)
                ? Image.Type.Sliced
                : Image.Type.Simple;
''',
    "dedicated UI sprite resolution",
)

revision = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlineStudioRevision.cs")
source = revision.read_text(encoding="utf-8")
if '0.1.0-review.9' not in source:
    raise SystemExit("Missing review 9 revision marker")
source = source.replace('0.1.0-review.9', '0.1.0-review.10', 1)
old = "Validate pale snow, premium furnace and shelter silhouettes, asymmetric winter composition, static HUD typography and unchanged proof thresholds."
new = "Remove unsupported font mutation, use dedicated HUD panel and control sprites, and keep transient interface panels event-driven."
if old not in source:
    raise SystemExit("Missing review 9 purpose")
revision.write_text(source.replace(old, new, 1), encoding="utf-8")
