from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    source = path.read_text(encoding="utf-8")
    if old not in source:
        raise SystemExit(f"Missing {label} in {path}")
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


assets = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlinePremiumVisualAssets.cs")
replace_once(
    assets,
    '''        internal const string ShelterSnowCapPath = Root + "/HAVENLINE_ShelterSnowCap.asset";
        internal const string FurnaceDarkMaterialPath =
''',
    '''        internal const string ShelterSnowCapPath = Root + "/HAVENLINE_ShelterSnowCap.asset";
        internal const string FlameMeshPath = Root + "/HAVENLINE_FurnaceFlame.asset";
        internal const string FlameOuterMaterialPath =
            "Assets/Havenline/Art/Production/Materials/HAVENLINE_FlameOuter.mat";
        internal const string FlameInnerMaterialPath =
            "Assets/Havenline/Art/Production/Materials/HAVENLINE_FlameInner.mat";
        internal const string ThawedSnowMaterialPath =
            "Assets/Havenline/Art/Production/Materials/HAVENLINE_ThawedSnow.mat";
        internal const string FurnaceDarkMaterialPath =
''',
    "furnace core asset paths",
)
replace_once(
    assets,
    '''                CreateMeshIfMissing(ShelterSnowCapPath, CreateTentRoofCapMesh(
                    "HAVENLINE_ShelterSnowCap", 3.9f, 2.65f, 3.3f, 0.52f));
                CreateMaterialIfMissing(
                    FurnaceDarkMaterialPath,
''',
    '''                CreateMeshIfMissing(ShelterSnowCapPath, CreateTentRoofCapMesh(
                    "HAVENLINE_ShelterSnowCap", 3.9f, 2.65f, 3.3f, 0.52f));
                CreateMeshIfMissing(FlameMeshPath, CreateFlameMesh("HAVENLINE_FurnaceFlame", 10));
                CreateEmissiveMaterial(
                    FlameOuterMaterialPath,
                    new Color(1f, 0.22f, 0.025f, 1f),
                    new Color(4.2f, 0.48f, 0.035f, 1f));
                CreateEmissiveMaterial(
                    FlameInnerMaterialPath,
                    new Color(1f, 0.72f, 0.08f, 1f),
                    new Color(4.6f, 2.2f, 0.14f, 1f));
                CreateMaterialIfMissing(
                    ThawedSnowMaterialPath,
                    new Color(0.72f, 0.79f, 0.76f, 1f),
                    0.22f,
                    string.Empty);
                CreateMaterialIfMissing(
                    FurnaceDarkMaterialPath,
''',
    "furnace core assets",
)
flame_mesh = '''        private static Mesh CreateFlameMesh(string name, int sides)
        {
            var ringHeights = new[] { 0f, 0.30f, 0.68f, 1.02f, 1.28f };
            var ringRadii = new[] { 0.34f, 0.31f, 0.22f, 0.13f, 0.025f };
            var offsets = new[]
            {
                Vector2.zero,
                new Vector2(0.035f, -0.015f),
                new Vector2(-0.045f, 0.025f),
                new Vector2(0.055f, 0.01f),
                new Vector2(-0.02f, 0f)
            };
            var vertices = new List<Vector3>();
            var uv = new List<Vector2>();
            for (var ring = 0; ring < ringHeights.Length; ring++)
            {
                for (var side = 0; side < sides; side++)
                {
                    var angle = side * Mathf.PI * 2f / sides + ring * 0.19f;
                    vertices.Add(new Vector3(
                        offsets[ring].x + Mathf.Cos(angle) * ringRadii[ring],
                        ringHeights[ring],
                        offsets[ring].y + Mathf.Sin(angle) * ringRadii[ring]));
                    uv.Add(new Vector2(side / (float)sides, ring / (float)(ringHeights.Length - 1)));
                }
            }
            var triangles = new List<int>();
            for (var ring = 0; ring < ringHeights.Length - 1; ring++)
            {
                var lower = ring * sides;
                var upper = (ring + 1) * sides;
                for (var side = 0; side < sides; side++)
                {
                    var next = (side + 1) % sides;
                    triangles.Add(lower + side);
                    triangles.Add(upper + next);
                    triangles.Add(upper + side);
                    triangles.Add(lower + side);
                    triangles.Add(lower + next);
                    triangles.Add(upper + next);
                }
            }
            var baseCenter = vertices.Count;
            vertices.Add(Vector3.zero);
            uv.Add(new Vector2(0.5f, 0.5f));
            for (var side = 0; side < sides; side++)
            {
                var next = (side + 1) % sides;
                triangles.Add(baseCenter);
                triangles.Add(next);
                triangles.Add(side);
            }
            return BuildMesh(name, vertices, triangles, uv);
        }

'''
replace_once(
    assets,
    '''        private static Mesh BuildMesh(
            string name,
''',
    flame_mesh + '''        private static Mesh BuildMesh(
            string name,
''',
    "furnace core mesh builder",
)
emissive_helper = '''        private static void CreateEmissiveMaterial(string path, Color color, Color emission)
        {
            if (AssetDatabase.LoadAssetAtPath<Material>(path) != null)
                AssetDatabase.DeleteAsset(path);
            var shader = Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard");
            if (shader == null)
                throw new InvalidOperationException("HAVENLINE furnace core could not find a lit shader.");
            var material = new Material(shader)
            {
                name = System.IO.Path.GetFileNameWithoutExtension(path),
                enableInstancing = true
            };
            if (material.HasProperty("_BaseColor")) material.SetColor("_BaseColor", color);
            if (material.HasProperty("_Color")) material.SetColor("_Color", color);
            if (material.HasProperty("_Smoothness")) material.SetFloat("_Smoothness", 0.16f);
            if (material.HasProperty("_EmissionColor")) material.SetColor("_EmissionColor", emission);
            material.EnableKeyword("_EMISSION");
            material.globalIlluminationFlags = MaterialGlobalIlluminationFlags.RealtimeEmissive;
            AssetDatabase.CreateAsset(material, path);
        }

'''
replace_once(
    assets,
    '''        private static void CreateMaterialIfMissing(
            string path,
''',
    emissive_helper + '''        private static void CreateMaterialIfMissing(
            string path,
''',
    "emissive material builder",
)

scene = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlinePremiumSceneAuthoring.cs")
replace_once(
    scene,
    '''            var fire = InstantiateEffect(manifest.fireVfxPrefab, root.transform, "FurnaceFireVFX");
''',
    '''            var flameVisual = HavenlinePremiumFlameAuthoring.Build(root.transform);
            var fire = InstantiateEffect(manifest.fireVfxPrefab, root.transform, "FurnaceFireVFX");
''',
    "authored furnace core visual",
)
replace_once(
    scene,
    '''            fire.transform.localScale = Vector3.one * 0.18f;
''',
    '''            fire.transform.localScale = Vector3.one * 0.035f;
''',
    "subtle secondary fire particles",
)
replace_once(
    scene,
    '''            furnace.Configure(warmth.transform, light, fire, delivery, levelVisuals, FindHeatedSnowRenderers());
''',
    '''            furnace.Configure(
                warmth.transform,
                light,
                fire,
                delivery,
                levelVisuals,
                FindHeatedSnowRenderers(),
                flameVisual);
''',
    "furnace core runtime binding",
)

furnace = Path("HAVENLINE_UNITY/Assets/Havenline/Runtime/HavenlineFurnace.cs")
replace_once(
    furnace,
    '''        [SerializeField] private ParticleSystem fireParticles;
        [SerializeField] private ParticleSystem depositEffect;
''',
    '''        [SerializeField] private ParticleSystem fireParticles;
        [SerializeField] private HavenlineFlamePulse flameVisual;
        [SerializeField] private ParticleSystem depositEffect;
''',
    "furnace core runtime field",
)
replace_once(
    furnace,
    '''            GameObject[] authoredLevelVisuals,
            Renderer[] snowRenderers)
''',
    '''            GameObject[] authoredLevelVisuals,
            Renderer[] snowRenderers,
            HavenlineFlamePulse authoredFlameVisual = null)
''',
    "furnace configure signature",
)
replace_once(
    furnace,
    '''            levelVisuals = authoredLevelVisuals ?? Array.Empty<GameObject>();
            heatedSnowRenderers = snowRenderers ?? Array.Empty<Renderer>();
''',
    '''            levelVisuals = authoredLevelVisuals ?? Array.Empty<GameObject>();
            heatedSnowRenderers = snowRenderers ?? Array.Empty<Renderer>();
            flameVisual = authoredFlameVisual;
''',
    "furnace core assignment",
)
replace_once(
    furnace,
    '''                fireLight.range = IsOperational ? 7f + Level * 2.4f : 2.5f;
                fireLight.intensity = IsOperational ? 2.9f + Level * 1.35f : 0.18f;
''',
    '''                fireLight.range = IsOperational ? 6.2f + Level * 1.65f : 2.5f;
                fireLight.intensity = IsOperational ? 1.65f + Level * 0.72f : 0.18f;
''',
    "balanced furnace runtime light",
)
replace_once(
    furnace,
    '''                main.startSizeMultiplier = IsOperational ? 0.72f + Level * 0.18f : 0.12f;
                var emission = fireParticles.emission;
                emission.rateOverTimeMultiplier = IsOperational ? 18f + Level * 7f : 1f;
            }

            for (var index = 0; index < levelVisuals.Length; index++)
''',
    '''                main.startSizeMultiplier = IsOperational ? 0.16f + Level * 0.035f : 0.04f;
                var emission = fireParticles.emission;
                emission.rateOverTimeMultiplier = IsOperational ? 4f + Level * 3f : 0.5f;
            }

            if (flameVisual != null)
            {
                flameVisual.gameObject.SetActive(IsOperational);
                if (IsOperational)
                    flameVisual.Configure(0.88f + Level * 0.08f);
            }

            for (var index = 0; index < levelVisuals.Length; index++)
''',
    "furnace core operational visuals",
)

polish = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlinePremiumVisualPolish.cs")
replace_once(
    polish,
    '''                HavenlinePremiumVisualAssets.WarmSnowMaterialPath,
                new Vector3(0f, 0.092f, 0.25f),
                new Vector3(3.25f, 1f, 2.55f),
''',
    '''                HavenlinePremiumVisualAssets.ThawedSnowMaterialPath,
                new Vector3(0f, 0.092f, 0.25f),
                new Vector3(2.45f, 1f, 1.68f),
''',
    "pale thawed furnace ground",
)
replace_once(
    polish,
    '''                furnace.intensity = 5.4f;
                furnace.range = 14f;
                furnace.color = new Color(1f, 0.30f, 0.055f, 1f);
''',
    '''                furnace.intensity = 2.35f;
                furnace.range = 9f;
                furnace.color = new Color(1f, 0.42f, 0.12f, 1f);
''',
    "balanced authored furnace light",
)
replace_once(
    polish,
    '''            CreatePointLight(parent, "FurnaceBounceLight", new Vector3(0f, 1.2f, 0.4f),
                new Color(1f, 0.25f, 0.04f), 4.2f, 13f, true);
''',
    '''            CreatePointLight(parent, "FurnaceBounceLight", new Vector3(0f, 1.2f, 0.4f),
                new Color(1f, 0.36f, 0.10f), 1.65f, 8.5f, true);
''',
    "balanced furnace bounce light",
)
replace_once(
    polish,
    '''                renderer.shadowCastingMode = ShadowCastingMode.On;
                renderer.receiveShadows = true;
                renderer.lightProbeUsage = LightProbeUsage.BlendProbes;
                renderer.reflectionProbeUsage = ReflectionProbeUsage.BlendProbes;
''',
    '''                var isFlame = renderer.GetComponentInParent<HavenlineFlamePulse>() != null;
                renderer.shadowCastingMode = isFlame ? ShadowCastingMode.Off : ShadowCastingMode.On;
                renderer.receiveShadows = !isFlame;
                renderer.lightProbeUsage = LightProbeUsage.BlendProbes;
                renderer.reflectionProbeUsage = isFlame
                    ? ReflectionProbeUsage.Off
                    : ReflectionProbeUsage.BlendProbes;
''',
    "stable furnace core renderer settings",
)

gate = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlinePremiumSceneGate.cs")
flame_gate = '''            var flameVisuals = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<HavenlineFlamePulse>(true))
                .ToArray();
            if (flameVisuals.Length != 1)
            {
                failures.Add($"Shipping furnace requires exactly one authored mesh flame; found {flameVisuals.Length}.");
            }
            else
            {
                var flameRenderers = flameVisuals[0].GetComponentsInChildren<MeshRenderer>(true);
                if (flameRenderers.Length != 2)
                    failures.Add($"Authored furnace flame requires outer and inner mesh layers; found {flameRenderers.Length}.");
                var flameMaterialPaths = flameRenderers
                    .Select(renderer => AssetDatabase.GetAssetPath(renderer.sharedMaterial))
                    .ToArray();
                if (!flameMaterialPaths.Contains(HavenlinePremiumVisualAssets.FlameOuterMaterialPath) ||
                    !flameMaterialPaths.Contains(HavenlinePremiumVisualAssets.FlameInnerMaterialPath))
                {
                    failures.Add("Authored furnace flame is not using both approved emissive HAVENLINE materials.");
                }
            }

            var thawedSnow = objects.SingleOrDefault(item => item.name == "FurnaceWarmSnow")
                ?.GetComponent<MeshRenderer>();
            if (thawedSnow == null ||
                AssetDatabase.GetAssetPath(thawedSnow.sharedMaterial) != HavenlinePremiumVisualAssets.ThawedSnowMaterialPath)
            {
                failures.Add("Furnace thaw footprint must use the approved pale thawed-snow material.");
            }

'''
replace_once(
    gate,
    '''            foreach (var effectName in new[] { "FurnaceFireVFX", "FurnaceSparksVFX", "FurnaceSmokeVFX" })
''',
    flame_gate + '''            foreach (var effectName in new[] { "FurnaceFireVFX", "FurnaceSparksVFX", "FurnaceSmokeVFX" })
''',
    "authored furnace core gates",
)
replace_once(
    gate,
    '''                if (effectiveFireSize > 0.45f)
''',
    '''                if (effectiveFireSize > 0.08f)
''',
    "secondary particle world-size limit",
)

revision = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlineStudioRevision.cs")
source = revision.read_text(encoding="utf-8")
if '0.1.0-review.15' not in source:
    raise SystemExit("Missing review 15 revision marker")
source = source.replace('0.1.0-review.15', '0.1.0-review.16', 1)
old = "Validate normalized furnace particle curves, authored hierarchy scales, effective rendered world-size measurement, gameplay integrity and unchanged premium gates."
new = "Replace the billboard stack with a level-aware two-layer mesh flame, reduce particles to secondary feedback, balance furnace lighting and use pale thawed snow."
if old not in source:
    raise SystemExit("Missing review 15 purpose")
revision.write_text(source.replace(old, new, 1), encoding="utf-8")
