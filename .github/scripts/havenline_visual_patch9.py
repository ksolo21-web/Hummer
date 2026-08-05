from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Missing {label} in {path}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


assets = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlinePremiumVisualAssets.cs")
replace_once(
    assets,
    '''        internal const string WarmPatchPath = Root + "/HAVENLINE_WarmSnowPatch.asset";
        internal const string SnowPathMaterialPath =
            "Assets/Havenline/Art/Production/Materials/HAVENLINE_SnowPath.mat";
''',
    '''        internal const string WarmPatchPath = Root + "/HAVENLINE_WarmSnowPatch.asset";
        internal const string FurnaceBodyPath = Root + "/HAVENLINE_FurnaceBody.asset";
        internal const string FurnaceHoodPath = Root + "/HAVENLINE_FurnaceHood.asset";
        internal const string FurnaceChimneyPath = Root + "/HAVENLINE_FurnaceChimney.asset";
        internal const string ShelterShellPath = Root + "/HAVENLINE_ShelterShell.asset";
        internal const string PaleSnowMaterialPath =
            "Assets/Havenline/Art/Production/Materials/HAVENLINE_PaleSnow.mat";
        internal const string SnowPathMaterialPath =
            "Assets/Havenline/Art/Production/Materials/HAVENLINE_SnowPath.mat";
''',
    "premium visual asset constants",
)
replace_once(
    assets,
    '''                CreateMeshIfMissing(WarmPatchPath, CreateEllipseMesh(
                    "HAVENLINE_WarmSnowPatch", 48, 1f, 1f, 0.055f, 16127));
                CreateMaterialIfMissing(
                    SnowPathMaterialPath,
''',
    '''                CreateMeshIfMissing(WarmPatchPath, CreateEllipseMesh(
                    "HAVENLINE_WarmSnowPatch", 48, 1f, 1f, 0.055f, 16127));
                CreateMeshIfMissing(FurnaceBodyPath, CreateChamferedColumnMesh(
                    "HAVENLINE_FurnaceBody", 3.2f, 1.9f, 1.8f, 0.24f));
                CreateMeshIfMissing(FurnaceHoodPath, CreateChamferedColumnMesh(
                    "HAVENLINE_FurnaceHood", 3.75f, 2.2f, 0.58f, 0.28f));
                CreateMeshIfMissing(FurnaceChimneyPath, CreateChamferedColumnMesh(
                    "HAVENLINE_FurnaceChimney", 0.82f, 0.82f, 1.55f, 0.16f));
                CreateMeshIfMissing(ShelterShellPath, CreateTentMesh(
                    "HAVENLINE_ShelterShell", 3.9f, 2.65f, 3.3f));
                CreateMaterialIfMissing(
                    PaleSnowMaterialPath,
                    new Color(0.965f, 0.985f, 1f, 1f),
                    0.17f,
                    string.Empty);
                CreateMaterialIfMissing(
                    SnowPathMaterialPath,
''',
    "premium mesh generation",
)
insert_marker = '''        private static Mesh BuildMesh(
            string name,
'''
mesh_helpers = '''        private static Mesh CreateChamferedColumnMesh(
            string name,
            float width,
            float depth,
            float height,
            float bevel)
        {
            var halfWidth = width * 0.5f;
            var halfDepth = depth * 0.5f;
            var points = new[]
            {
                new Vector2(-halfWidth + bevel, -halfDepth),
                new Vector2(halfWidth - bevel, -halfDepth),
                new Vector2(halfWidth, -halfDepth + bevel),
                new Vector2(halfWidth, halfDepth - bevel),
                new Vector2(halfWidth - bevel, halfDepth),
                new Vector2(-halfWidth + bevel, halfDepth),
                new Vector2(-halfWidth, halfDepth - bevel),
                new Vector2(-halfWidth, -halfDepth + bevel)
            };
            var vertices = new List<Vector3>();
            var uv = new List<Vector2>();
            foreach (var point in points)
            {
                vertices.Add(new Vector3(point.x, 0f, point.y));
                uv.Add(new Vector2(point.x / width + 0.5f, 0f));
            }
            foreach (var point in points)
            {
                vertices.Add(new Vector3(point.x, height, point.y));
                uv.Add(new Vector2(point.x / width + 0.5f, 1f));
            }
            var bottomCenter = vertices.Count;
            vertices.Add(Vector3.zero);
            uv.Add(new Vector2(0.5f, 0.5f));
            var topCenter = vertices.Count;
            vertices.Add(new Vector3(0f, height, 0f));
            uv.Add(new Vector2(0.5f, 0.5f));
            var triangles = new List<int>();
            for (var index = 0; index < points.Length; index++)
            {
                var next = (index + 1) % points.Length;
                triangles.Add(index);
                triangles.Add(next + points.Length);
                triangles.Add(index + points.Length);
                triangles.Add(index);
                triangles.Add(next);
                triangles.Add(next + points.Length);
                triangles.Add(topCenter);
                triangles.Add(index + points.Length);
                triangles.Add(next + points.Length);
                triangles.Add(bottomCenter);
                triangles.Add(next);
                triangles.Add(index);
            }
            return BuildMesh(name, vertices, triangles, uv);
        }

        private static Mesh CreateTentMesh(string name, float width, float height, float depth)
        {
            var halfWidth = width * 0.5f;
            var halfDepth = depth * 0.5f;
            var vertices = new List<Vector3>
            {
                new(-halfWidth, 0f, halfDepth), new(0f, height, halfDepth), new(halfWidth, 0f, halfDepth),
                new(-halfWidth, 0f, -halfDepth), new(0f, height, -halfDepth), new(halfWidth, 0f, -halfDepth)
            };
            var triangles = new List<int>
            {
                0,1,2, 5,4,3,
                0,3,4, 0,4,1,
                1,4,5, 1,5,2,
                0,2,5, 0,5,3
            };
            var uv = new List<Vector2>
            {
                new(0f,0f), new(0.5f,1f), new(1f,0f),
                new(0f,0f), new(0.5f,1f), new(1f,0f)
            };
            return BuildMesh(name, vertices, triangles, uv);
        }

'''
replace_once(assets, insert_marker, mesh_helpers + insert_marker, "premium mesh helpers")
replace_once(
    assets,
    '''        private static void CreateMeshIfMissing(string path, Mesh mesh)
        {
            if (AssetDatabase.LoadAssetAtPath<Mesh>(path) != null)
            {
                UnityEngine.Object.DestroyImmediate(mesh);
                return;
            }
            AssetDatabase.CreateAsset(mesh, path);
        }
''',
    '''        private static void CreateMeshIfMissing(string path, Mesh mesh)
        {
            if (AssetDatabase.LoadAssetAtPath<Mesh>(path) != null)
                AssetDatabase.DeleteAsset(path);
            AssetDatabase.CreateAsset(mesh, path);
        }
''',
    "replaceable premium mesh assets",
)
replace_once(
    assets,
    '''            if (AssetDatabase.LoadAssetAtPath<Material>(path) != null)
                return;
            var shader = Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard");
''',
    '''            if (AssetDatabase.LoadAssetAtPath<Material>(path) != null)
                AssetDatabase.DeleteAsset(path);
            var shader = Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard");
''',
    "replaceable premium materials",
)
replace_once(
    assets,
    '''            var texture = AssetDatabase.LoadAssetAtPath<Texture2D>(texturePath);
            if (texture != null)
''',
    '''            var texture = string.IsNullOrWhiteSpace(texturePath)
                ? null
                : AssetDatabase.LoadAssetAtPath<Texture2D>(texturePath);
            if (texture != null)
''',
    "optional premium material texture",
)

polish = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlinePremiumVisualPolish.cs")
replace_once(
    polish,
    '''            BuildLayeredGround(dressing.transform);
            BuildCampDetails(dressing.transform);
            TuneWorldLayout(objects);
''',
    '''            BuildLayeredGround(dressing.transform);
            BuildCampDetails(dressing.transform);
            BuildFurnaceSilhouette(dressing.transform);
            BuildShelterSilhouettes(dressing.transform);
            TuneWorldLayout(objects);
''',
    "premium silhouette calls",
)
replace_once(
    polish,
    '''                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Snow.mat",
                new Vector3(0f, -0.12f, 0f),
''',
    '''                HavenlinePremiumVisualAssets.PaleSnowMaterialPath,
                new Vector3(0f, -0.085f, 0f),
''',
    "pale layered snow field",
)
replace_once(
    polish,
    '''                new Vector3(0f, 0.086f, 0.25f),
                new Vector3(4.4f, 1f, 3.75f),
''',
    '''                new Vector3(0f, 0.092f, 0.25f),
                new Vector3(3.25f, 1f, 2.55f),
''',
    "controlled furnace warmth footprint",
)
layout_marker = '''        private static void TuneWorldLayout(IReadOnlyCollection<GameObject> objects)
'''
silhouette_methods = '''        private static void BuildFurnaceSilhouette(Transform parent)
        {
            CreateMeshObject(parent, "FurnacePremiumBody",
                HavenlinePremiumVisualAssets.FurnaceBodyPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Metal.mat",
                new Vector3(0f, 0.12f, 0.18f), Vector3.one, Quaternion.identity);
            CreateMeshObject(parent, "FurnacePremiumHood",
                HavenlinePremiumVisualAssets.FurnaceHoodPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_MetalLight.mat",
                new Vector3(0f, 1.86f, 0.18f), Vector3.one, Quaternion.identity);
            CreateMeshObject(parent, "FurnacePremiumChimney",
                HavenlinePremiumVisualAssets.FurnaceChimneyPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Navy.mat",
                new Vector3(0f, 2.36f, 0.05f), Vector3.one, Quaternion.identity);
            CreateMeshObject(parent, "FurnaceDoorFrame",
                HavenlinePremiumVisualAssets.FurnaceBodyPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Amber.mat",
                new Vector3(0f, 0.48f, 1.15f), new Vector3(0.50f, 0.54f, 0.12f), Quaternion.identity);
            CreateMeshObject(parent, "FurnaceGlowCore",
                HavenlinePremiumVisualAssets.FurnaceBodyPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Orange.mat",
                new Vector3(0f, 0.58f, 1.30f), new Vector3(0.38f, 0.39f, 0.055f), Quaternion.identity);
        }

        private static void BuildShelterSilhouettes(Transform parent)
        {
            BuildShelter(parent, "LeftPremiumShelter", new Vector3(-6.25f, 0.02f, -1.15f), 18f, true);
            BuildShelter(parent, "RightPremiumShelter", new Vector3(6.15f, 0.02f, -1.0f), -22f, false);
        }

        private static void BuildShelter(Transform parent, string name, Vector3 position, float yaw, bool left)
        {
            CreateMeshObject(parent, name + "Shell",
                HavenlinePremiumVisualAssets.ShelterShellPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Blue.mat",
                position, Vector3.one, Quaternion.Euler(0f, yaw, 0f));
            CreateMeshObject(parent, name + "SnowCap",
                HavenlinePremiumVisualAssets.ShelterShellPath,
                HavenlinePremiumVisualAssets.PaleSnowMaterialPath,
                position + new Vector3(0f, 0.42f, 0f),
                new Vector3(1.04f, 0.70f, 1.04f), Quaternion.Euler(0f, yaw, 0f));
            var lanternPosition = position + new Vector3(left ? 1.35f : -1.35f, 0.72f, 1.35f);
            CreateMeshObject(parent, name + "Lantern",
                HavenlinePremiumVisualAssets.FurnaceChimneyPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Amber.mat",
                lanternPosition, new Vector3(0.22f, 0.28f, 0.22f), Quaternion.identity);
            CreatePointLight(parent, name + "LanternLight", lanternPosition + Vector3.up * 0.15f,
                new Color(1f, 0.48f, 0.12f), 1.55f, 7.5f, false);
        }

'''
replace_once(polish, layout_marker, silhouette_methods + layout_marker, "furnace and shelter silhouette methods")
replace_once(
    polish,
    '''            SetPose(objects, "StartingTent", new Vector3(-5.45f, 0f, -3.0f), 24f);
            SetPose(objects, "RescueShelter", new Vector3(5.45f, 0f, -3.05f), -24f);
            SetPose(objects, "SupplyStorage", new Vector3(-3.15f, 0f, 1.55f), -12f);
            SetPose(objects, "Campfire", new Vector3(3.1f, 0f, 1.72f), 0f);
''',
    '''            SetPose(objects, "StartingTent", new Vector3(-6.25f, 0f, -1.15f), 18f);
            SetPose(objects, "RescueShelter", new Vector3(6.15f, 0f, -1.0f), -22f);
            SetPose(objects, "SupplyStorage", new Vector3(-3.35f, 0f, 1.65f), -16f);
            SetPose(objects, "Campfire", new Vector3(3.15f, 0f, 1.70f), 0f);
            TuneTreeComposition(objects);
''',
    "readable premium world layout",
)
insert_before_lighting = '''        private static void TuneLighting(IReadOnlyCollection<GameObject> objects, Transform parent)
'''
tree_method = '''        private static void TuneTreeComposition(IReadOnlyCollection<GameObject> objects)
        {
            var woodPositions = new[]
            {
                new Vector3(-10.6f,0f,7.4f), new Vector3(-11.7f,0f,1.9f),
                new Vector3(10.4f,0f,7.1f), new Vector3(11.8f,0f,1.4f),
                new Vector3(-10.2f,0f,-7.4f), new Vector3(10.8f,0f,-7.0f)
            };
            for (var index = 0; index < woodPositions.Length; index++)
                SetPose(objects, $"WoodNode_{index}", woodPositions[index], 19f + index * 47f);

            var boundaryPositions = new[]
            {
                new Vector3(-13.0f,0f,10.8f), new Vector3(12.7f,0f,9.9f),
                new Vector3(-13.1f,0f,-9.8f), new Vector3(12.8f,0f,-10.3f),
                new Vector3(-5.1f,0f,-13.6f), new Vector3(6.8f,0f,-13.1f)
            };
            for (var index = 0; index < boundaryPositions.Length; index++)
            {
                var pine = objects.FirstOrDefault(item => item.name == $"BoundaryPine_{index}");
                if (pine == null)
                    continue;
                pine.transform.position = boundaryPositions[index];
                pine.transform.rotation = Quaternion.Euler(0f, 31f + index * 61f, 0f);
                pine.transform.localScale *= 0.86f + index % 3 * 0.10f;
            }
        }

'''
replace_once(polish, insert_before_lighting, tree_method + insert_before_lighting, "asymmetric tree composition")
replace_once(
    polish,
    '''                new Color(0.76f, 0.88f, 0.95f, 1f),
                0.32f,
''',
    '''                new Color(0.91f, 0.965f, 1f, 1f),
                0.20f,
''',
    "brighter supporting snow material",
)
replace_once(
    polish,
    '''                if (image.name.Contains("Panel", StringComparison.OrdinalIgnoreCase))
                {
                    var color = image.color;
                    color.a = Mathf.Clamp(color.a, 0.78f, 0.94f);
                    image.color = color;
                }
            }
''',
    '''                if (image.name.Contains("Panel", StringComparison.OrdinalIgnoreCase))
                {
                    var color = image.color;
                    color.a = Mathf.Clamp(color.a, 0.68f, 0.84f);
                    image.color = color;
                }
            }

            foreach (var text in objects.SelectMany(item => item.GetComponents<Text>()))
            {
                text.fontStyle = FontStyle.Normal;
                text.resizeTextForBestFit = false;
                text.fontSize = 24;
                text.horizontalOverflow = HorizontalWrapMode.Overflow;
                text.verticalOverflow = VerticalWrapMode.Overflow;
                text.lineSpacing = 0.90f;
                text.raycastTarget = false;
                text.color = new Color(0.94f, 0.98f, 1f, 1f);
            }
''',
    "static-font-safe interface tuning",
)

studio = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlineProceduralArtStudio.cs")
replacements = [
    ("new Vector2(390f, 132f)", "new Vector2(500f, 92f)"),
    ('"WOOD 0   STONE 0   METAL 0   FUEL 0", 25', '"WOOD 0   STONE 0   METAL 0", 24'),
    ("new Vector2(720f, 112f)", "new Vector2(580f, 88f)"),
    ('"RESTORE THE FURNACE", 31', '"RESTORE THE FURNACE", 24'),
    ("new Vector2(330f, 132f)", "new Vector2(300f, 92f)"),
    ('"FURNACE  LV.1", 25', '"FURNACE LV.1", 24'),
    ("new Vector2(0f, 42f), new Vector2(570f, 104f)", "new Vector2(0f, 28f), new Vector2(540f, 84f)"),
    ('"MOVE NEAR AN OBJECT TO ACT", 23', '"MOVE CLOSE TO ACT", 24'),
    ("new Vector2(500f,16f)", "new Vector2(470f,12f)"),
    ("new Vector2(24f, 214f), new Vector2(330f, 94f)", "new Vector2(24f, 132f), new Vector2(280f, 72f)"),
    ('"HELPER: FROZEN", 22', '"HELPER: FROZEN", 24'),
    ("new Vector2(-24f, 214f), new Vector2(330f, 94f)", "new Vector2(-24f, 132f), new Vector2(280f, 72f)"),
    ('"THREAT: QUIET", 22', '"THREAT: QUIET", 24'),
    ("new Vector2(166f,154f), new Vector2(226f,226f)", "new Vector2(132f,126f), new Vector2(190f,190f)"),
    ("new Vector2(96f,96f)", "new Vector2(82f,82f)"),
    ("new Vector2(-168f,154f), new Vector2(176f,176f)", "new Vector2(-128f,126f), new Vector2(150f,150f)"),
    ('"WARMTH", 21', '"WARMTH", 24'),
]
text = studio.read_text(encoding="utf-8")
for old, new in replacements:
    if old not in text:
        raise SystemExit(f"Missing HUD layout target: {old}")
    text = text.replace(old, new, 1)
old_text_block = '''            text.fontSize = size;
            text.fontStyle = FontStyle.Bold;
            text.alignment = alignment;
            text.color = new Color(0.94f,0.98f,1f,1f);
            text.resizeTextForBestFit = true;
            text.resizeTextMinSize = Mathf.Max(15, size - 8);
            text.resizeTextMaxSize = size;
            text.horizontalOverflow = HorizontalWrapMode.Wrap;
            text.verticalOverflow = VerticalWrapMode.Truncate;
'''
new_text_block = '''            text.fontSize = 24;
            text.fontStyle = FontStyle.Normal;
            text.alignment = alignment;
            text.color = new Color(0.94f,0.98f,1f,1f);
            text.resizeTextForBestFit = false;
            text.horizontalOverflow = HorizontalWrapMode.Overflow;
            text.verticalOverflow = VerticalWrapMode.Overflow;
            text.lineSpacing = 0.90f;
            text.raycastTarget = false;
'''
if old_text_block not in text:
    raise SystemExit("Missing static font Text configuration block")
studio.write_text(text.replace(old_text_block, new_text_block, 1), encoding="utf-8")

font = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlineStudioBitmapFont.cs")
text = font.read_text(encoding="utf-8")
font_replacements = {
    "private const int PixelScale = 3;": "private const int PixelScale = 4;",
    "private const int GlyphWidth = 15;": "private const int GlyphWidth = 20;",
    "private const int GlyphHeight = 21;": "private const int GlyphHeight = 28;",
    "filterMode = FilterMode.Point,": "filterMode = FilterMode.Bilinear,",
    "importer.filterMode = FilterMode.Point;": "importer.filterMode = FilterMode.Bilinear;",
    "advance = character == ' ' ? 10 : 18,": "advance = character == ' ' ? 12 : 22,",
    'name = "HAVENLINE_UI_Rounded_Static",': 'name = "HAVENLINE_UI_Geometric_Static",',
}
for old, new in font_replacements.items():
    if old not in text:
        raise SystemExit(f"Missing static font target: {old}")
    text = text.replace(old, new, 1)
old_font_create = '''            var font = new Font
            {
                name = "HAVENLINE_UI_Geometric_Static",
                material = material,
                characterInfo = characters.ToArray()
            };
            AssetDatabase.CreateAsset(font, fontPath);
'''
new_font_create = '''            var font = new Font
            {
                name = "HAVENLINE_UI_Geometric_Static",
                material = material,
                characterInfo = characters.ToArray()
            };
            var serializedFont = new SerializedObject(font);
            var fontSize = serializedFont.FindProperty("m_FontSize");
            if (fontSize != null)
                fontSize.intValue = 24;
            var lineSpacing = serializedFont.FindProperty("m_LineSpacing");
            if (lineSpacing != null)
                lineSpacing.floatValue = 1f;
            serializedFont.ApplyModifiedPropertiesWithoutUndo();
            AssetDatabase.CreateAsset(font, fontPath);
'''
if old_font_create not in text:
    raise SystemExit("Missing static font creation block")
font.write_text(text.replace(old_font_create, new_font_create, 1), encoding="utf-8")

revision = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlineStudioRevision.cs")
text = revision.read_text(encoding="utf-8")
if "0.1.0-review.8" not in text:
    raise SystemExit("Missing review 8 revision marker")
text = text.replace("0.1.0-review.8", "0.1.0-review.9", 1)
old_purpose = "Rerun the layered snow and HUD-inclusive visual review after correcting Unity mesh and shadow API compatibility."
new_purpose = "Raise snow readability, give the furnace and shelters premium silhouettes, break tree symmetry and make the static HUD font layout-safe."
if old_purpose not in text:
    raise SystemExit("Missing review 8 purpose")
revision.write_text(text.replace(old_purpose, new_purpose, 1), encoding="utf-8")
