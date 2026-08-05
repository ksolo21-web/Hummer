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
        internal const string PaleSnowMaterialPath =
''',
    '''        internal const string ShelterSnowCapPath = Root + "/HAVENLINE_ShelterSnowCap.asset";
        internal const string FurnaceDarkMaterialPath =
            "Assets/Havenline/Art/Production/Materials/HAVENLINE_FurnaceDark.mat";
        internal const string FurnaceSteelMaterialPath =
            "Assets/Havenline/Art/Production/Materials/HAVENLINE_FurnaceSteel.mat";
        internal const string FurnaceEnamelMaterialPath =
            "Assets/Havenline/Art/Production/Materials/HAVENLINE_FurnaceEnamel.mat";
        internal const string ShelterFabricMaterialPath =
            "Assets/Havenline/Art/Production/Materials/HAVENLINE_ShelterFabric.mat";
        internal const string PaleSnowMaterialPath =
''',
    "dedicated furnace and shelter material paths",
)
replace_once(
    assets,
    '''                CreateMeshIfMissing(ShelterSnowCapPath, CreateTentRoofCapMesh(
                    "HAVENLINE_ShelterSnowCap", 3.9f, 2.65f, 3.3f, 0.72f));
                CreateMaterialIfMissing(
                    PaleSnowMaterialPath,
''',
    '''                CreateMeshIfMissing(ShelterSnowCapPath, CreateTentRoofCapMesh(
                    "HAVENLINE_ShelterSnowCap", 3.9f, 2.65f, 3.3f, 0.52f));
                CreateMaterialIfMissing(
                    FurnaceDarkMaterialPath,
                    new Color(0.042f, 0.062f, 0.078f, 1f),
                    0.50f,
                    string.Empty);
                CreateMaterialIfMissing(
                    FurnaceSteelMaterialPath,
                    new Color(0.24f, 0.32f, 0.37f, 1f),
                    0.58f,
                    string.Empty);
                CreateMaterialIfMissing(
                    FurnaceEnamelMaterialPath,
                    new Color(0.035f, 0.16f, 0.24f, 1f),
                    0.40f,
                    string.Empty);
                CreateMaterialIfMissing(
                    ShelterFabricMaterialPath,
                    new Color(0.038f, 0.105f, 0.17f, 1f),
                    0.10f,
                    string.Empty);
                CreateMaterialIfMissing(
                    PaleSnowMaterialPath,
''',
    "dedicated furnace and shelter materials",
)

furnace = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlinePremiumFurnaceAuthoring.cs")
source = furnace.read_text(encoding="utf-8")
replacements = {
    'private const string Metal = MaterialRoot + "/HAVENLINE_Metal.mat";':
        'private const string Metal = HavenlinePremiumVisualAssets.FurnaceDarkMaterialPath;',
    'private const string MetalLight = MaterialRoot + "/HAVENLINE_MetalLight.mat";':
        'private const string MetalLight = HavenlinePremiumVisualAssets.FurnaceSteelMaterialPath;',
    'private const string Navy = MaterialRoot + "/HAVENLINE_Navy.mat";':
        'private const string Navy = HavenlinePremiumVisualAssets.FurnaceDarkMaterialPath;',
    'private const string Blue = MaterialRoot + "/HAVENLINE_Blue.mat";':
        'private const string Blue = HavenlinePremiumVisualAssets.FurnaceEnamelMaterialPath;',
}
for old, new in replacements.items():
    if old not in source:
        raise SystemExit(f"Missing furnace material target: {old}")
    source = source.replace(old, new, 1)
furnace.write_text(source, encoding="utf-8")

studio = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlineProceduralArtStudio.cs")
replace_once(
    studio,
    '''        private static void GenerateVfx()
        {
            CreateParticlePrefab(VfxRoot + "/HAVENLINE_FurnaceFire.prefab", "FurnaceFire",
                Orange, Amber, 160, 1.1f, 1.0f, new Vector3(0.28f, 0.64f, 0.28f), false, true);
            CreateParticlePrefab(VfxRoot + "/HAVENLINE_Snowfall.prefab", "Snowfall",
''',
    '''        private static void GenerateVfx()
        {
            HavenlinePremiumParticleAssets.Ensure();
            CreateParticlePrefab(VfxRoot + "/HAVENLINE_FurnaceFire.prefab", "FurnaceFire",
                Orange, Amber, 96, 0.65f, 0.28f, new Vector3(0.18f, 0.10f, 0.18f), false, true);
            CreateParticlePrefab(VfxRoot + "/HAVENLINE_FurnaceSparks.prefab", "FurnaceSparks",
                Amber, Orange, 48, 0.55f, 0.075f, new Vector3(0.12f, 0.04f, 0.12f), false, true);
            CreateParticlePrefab(VfxRoot + "/HAVENLINE_FurnaceSmoke.prefab", "FurnaceSmoke",
                new Color(0.22f,0.28f,0.32f,0.34f), new Color(0.08f,0.12f,0.16f,0f),
                40, 2.25f, 0.34f, new Vector3(0.18f,0.08f,0.18f), true, true);
            CreateParticlePrefab(VfxRoot + "/HAVENLINE_Snowfall.prefab", "Snowfall",
''',
    "authored furnace fire spark and smoke effects",
)
old_particle_method = '''        private static void CreateParticlePrefab(
            string path, string name, Color start, Color end, int maxParticles,
            float lifetime, float size, Vector3 shapeScale, bool worldSimulation, bool looping)
        {
            AssetDatabase.DeleteAsset(path);
            var root = new GameObject("HAVENLINE_" + name);
            try
            {
                var particles = root.AddComponent<ParticleSystem>();
                var main = particles.main;
                main.loop = looping || name == "Snowfall";
                main.playOnAwake = true;
                main.maxParticles = maxParticles;
                main.startLifetime = lifetime;
                main.startSpeed = name == "Snowfall" ? 1.6f : 1.15f;
                main.startSize = size;
                main.simulationSpace = worldSimulation ? ParticleSystemSimulationSpace.World : ParticleSystemSimulationSpace.Local;
                main.startColor = new ParticleSystem.MinMaxGradient(start, end);
                main.gravityModifier = name == "Snowfall" ? 0.05f : 0.18f;

                var emission = particles.emission;
                emission.rateOverTime = name == "Snowfall" ? 230f : looping ? 85f : 0f;
                if (!looping && name != "Snowfall")
                    emission.SetBursts(new[] { new ParticleSystem.Burst(0f, (short)Mathf.Min(maxParticles, 36)) });
                var shape = particles.shape;
                shape.shapeType = name == "Snowfall" ? ParticleSystemShapeType.Box : ParticleSystemShapeType.Cone;
                shape.scale = shapeScale;
                shape.angle = name == "FurnaceFire" ? 16f : 36f;
                var color = particles.colorOverLifetime;
                color.enabled = true;
                var gradient = new Gradient();
                gradient.SetKeys(
                    new[] { new GradientColorKey(start,0f), new GradientColorKey(end,1f) },
                    new[] { new GradientAlphaKey(0f,0f), new GradientAlphaKey(1f,0.12f), new GradientAlphaKey(0f,1f) });
                color.color = gradient;
                var renderer = root.GetComponent<ParticleSystemRenderer>();
                renderer.sharedMaterial = LoadMaterial(name == "Snowfall" ? "Snow" : name == "FurnaceFire" ? "Orange" : "Amber");
                renderer.renderMode = ParticleSystemRenderMode.Billboard;
                PrefabUtility.SaveAsPrefabAsset(root, path);
            }
            finally
            {
                UnityEngine.Object.DestroyImmediate(root);
            }
        }
'''
new_particle_method = '''        private static void CreateParticlePrefab(
            string path, string name, Color start, Color end, int maxParticles,
            float lifetime, float size, Vector3 shapeScale, bool worldSimulation, bool looping)
        {
            AssetDatabase.DeleteAsset(path);
            var root = new GameObject("HAVENLINE_" + name);
            try
            {
                var isSnow = name == "Snowfall";
                var isFire = name == "FurnaceFire";
                var isSparks = name == "FurnaceSparks";
                var isSmoke = name == "FurnaceSmoke";

                var particles = root.AddComponent<ParticleSystem>();
                var main = particles.main;
                main.loop = looping || isSnow;
                main.playOnAwake = true;
                main.maxParticles = maxParticles;
                main.simulationSpace = worldSimulation
                    ? ParticleSystemSimulationSpace.World
                    : ParticleSystemSimulationSpace.Local;
                main.startColor = new ParticleSystem.MinMaxGradient(start, end);
                main.startRotation = new ParticleSystem.MinMaxCurve(-0.35f, 0.35f);
                main.startLifetime = isFire
                    ? new ParticleSystem.MinMaxCurve(0.34f, 0.68f)
                    : isSparks
                        ? new ParticleSystem.MinMaxCurve(0.28f, 0.58f)
                        : isSmoke
                            ? new ParticleSystem.MinMaxCurve(1.7f, 2.6f)
                            : new ParticleSystem.MinMaxCurve(lifetime * 0.82f, lifetime * 1.18f);
                main.startSpeed = isSnow
                    ? new ParticleSystem.MinMaxCurve(1.15f, 1.75f)
                    : isFire
                        ? new ParticleSystem.MinMaxCurve(0.30f, 0.78f)
                        : isSparks
                            ? new ParticleSystem.MinMaxCurve(1.10f, 2.10f)
                            : isSmoke
                                ? new ParticleSystem.MinMaxCurve(0.24f, 0.52f)
                                : new ParticleSystem.MinMaxCurve(0.72f, 1.22f);
                main.startSize = isFire
                    ? new ParticleSystem.MinMaxCurve(0.12f, 0.34f)
                    : isSparks
                        ? new ParticleSystem.MinMaxCurve(0.035f, 0.09f)
                        : isSmoke
                            ? new ParticleSystem.MinMaxCurve(0.18f, 0.42f)
                            : new ParticleSystem.MinMaxCurve(size * 0.72f, size * 1.18f);
                main.gravityModifier = isSnow ? 0.04f : isSparks ? 0.26f : isSmoke ? -0.018f : isFire ? -0.06f : 0.16f;

                var emission = particles.emission;
                emission.rateOverTime = isSnow ? 210f : isFire ? 42f : isSparks ? 12f : isSmoke ? 6.5f : looping ? 34f : 0f;
                if (!looping && !isSnow)
                    emission.SetBursts(new[] { new ParticleSystem.Burst(0f, (short)Mathf.Min(maxParticles, 30)) });

                var shape = particles.shape;
                shape.shapeType = isSnow ? ParticleSystemShapeType.Box : ParticleSystemShapeType.Cone;
                shape.scale = shapeScale;
                shape.radius = isFire ? 0.12f : isSparks ? 0.08f : isSmoke ? 0.14f : 0.22f;
                shape.angle = isFire ? 8f : isSparks ? 20f : isSmoke ? 10f : 32f;

                var color = particles.colorOverLifetime;
                color.enabled = true;
                var gradient = new Gradient();
                gradient.SetKeys(
                    new[]
                    {
                        new GradientColorKey(start, 0f),
                        new GradientColorKey(Color.Lerp(start, end, 0.48f), 0.48f),
                        new GradientColorKey(end, 1f)
                    },
                    isSmoke
                        ? new[]
                        {
                            new GradientAlphaKey(0f, 0f), new GradientAlphaKey(0.34f, 0.18f),
                            new GradientAlphaKey(0.20f, 0.66f), new GradientAlphaKey(0f, 1f)
                        }
                        : new[]
                        {
                            new GradientAlphaKey(0f, 0f), new GradientAlphaKey(0.96f, 0.12f),
                            new GradientAlphaKey(0.72f, 0.64f), new GradientAlphaKey(0f, 1f)
                        });
                color.color = gradient;

                var sizeLifetime = particles.sizeOverLifetime;
                sizeLifetime.enabled = isFire || isSmoke;
                if (sizeLifetime.enabled)
                {
                    sizeLifetime.size = new ParticleSystem.MinMaxCurve(1f, new AnimationCurve(
                        new Keyframe(0f, isSmoke ? 0.38f : 0.55f),
                        new Keyframe(0.55f, 1f),
                        new Keyframe(1f, isSmoke ? 1.42f : 0.18f)));
                }

                var noise = particles.noise;
                noise.enabled = isFire || isSmoke;
                if (noise.enabled)
                {
                    noise.strength = isSmoke ? 0.18f : 0.12f;
                    noise.frequency = isSmoke ? 0.42f : 0.72f;
                    noise.scrollSpeed = 0.24f;
                    noise.damping = true;
                }

                var renderer = root.GetComponent<ParticleSystemRenderer>();
                renderer.sharedMaterial = HavenlinePremiumParticleAssets.Resolve(name);
                renderer.renderMode = isFire || isSparks
                    ? ParticleSystemRenderMode.Stretch
                    : ParticleSystemRenderMode.Billboard;
                renderer.lengthScale = isSparks ? 2.4f : isFire ? 1.15f : 1f;
                renderer.velocityScale = isSparks ? 0.38f : isFire ? 0.18f : 0f;
                renderer.sortMode = ParticleSystemSortMode.Distance;
                PrefabUtility.SaveAsPrefabAsset(root, path);
            }
            finally
            {
                UnityEngine.Object.DestroyImmediate(root);
            }
        }
'''
replace_once(studio, old_particle_method, new_particle_method, "soft transparent particle prefab authoring")

scene = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlinePremiumSceneAuthoring.cs")
replace_once(
    scene,
    '''            var fire = InstantiateEffect(manifest.fireVfxPrefab, root.transform, "FurnaceFireVFX");
            var delivery = InstantiateEffect(manifest.buildVfxPrefab, root.transform, "FurnaceDeliveryVFX");
''',
    '''            var fire = InstantiateEffect(manifest.fireVfxPrefab, root.transform, "FurnaceFireVFX");
            fire.transform.localPosition = new Vector3(0f, 0.64f, 1.10f);
            fire.transform.localRotation = Quaternion.Euler(-8f, 0f, 0f);
            var sparks = InstantiateEffect(
                "Assets/Havenline/Art/Production/VFX/HAVENLINE_FurnaceSparks.prefab",
                root.transform,
                "FurnaceSparksVFX");
            sparks.transform.localPosition = new Vector3(0f, 0.78f, 1.05f);
            var smoke = InstantiateEffect(
                "Assets/Havenline/Art/Production/VFX/HAVENLINE_FurnaceSmoke.prefab",
                root.transform,
                "FurnaceSmokeVFX");
            smoke.transform.localPosition = new Vector3(0f, 2.82f, -0.14f);
            var delivery = InstantiateEffect(manifest.buildVfxPrefab, root.transform, "FurnaceDeliveryVFX");
''',
    "door fire, sparks and chimney smoke placement",
)

polish = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlinePremiumVisualPolish.cs")
replace_once(
    polish,
    '''            CreateMeshObject(root.transform, "FabricShell",
                HavenlinePremiumVisualAssets.ShelterShellPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Blue.mat",
                Vector3.zero, Vector3.one, Quaternion.identity);
            CreateMeshObject(root.transform, "SnowRoof",
''',
    '''            CreateMeshObject(root.transform, "FabricShell",
                HavenlinePremiumVisualAssets.ShelterShellPath,
                HavenlinePremiumVisualAssets.ShelterFabricMaterialPath,
                Vector3.zero, Vector3.one, Quaternion.identity);
            CreateMeshObject(root.transform, "BaseSkirt",
                HavenlinePremiumVisualAssets.FurnaceBodyPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Navy.mat",
                new Vector3(0f, 0.01f, 0f), new Vector3(1.02f, 0.10f, 0.94f), Quaternion.identity);
            CreateMeshObject(root.transform, "SnowRoof",
''',
    "dark fabric shelter and base skirt",
)
replace_once(
    polish,
    '''            CreateMeshObject(root.transform, "DoorFrame",
                HavenlinePremiumVisualAssets.FurnaceBodyPath,
''',
    '''            CreateMeshObject(root.transform, "RidgeBeam",
                HavenlinePremiumVisualAssets.FurnaceChimneyPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Navy.mat",
                new Vector3(0f, 2.72f, 0f), new Vector3(0.13f, 2.16f, 0.13f),
                Quaternion.Euler(90f, 0f, 0f));
            for (var side = -1; side <= 1; side += 2)
            {
                CreateMeshObject(root.transform, side < 0 ? "LeftFrontTrim" : "RightFrontTrim",
                    HavenlinePremiumVisualAssets.FurnaceChimneyPath,
                    "Assets/Havenline/Art/Production/Materials/HAVENLINE_Navy.mat",
                    new Vector3(side * 1.58f, 0.08f, 1.69f),
                    new Vector3(0.10f, 1.10f, 0.10f), Quaternion.identity);
            }
            CreateMeshObject(root.transform, "DoorFrame",
                HavenlinePremiumVisualAssets.FurnaceBodyPath,
''',
    "shelter ridge and structural trim",
)

scene_gate = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlinePremiumSceneGate.cs")
particle_validation = '''            foreach (var effectName in new[] { "FurnaceFireVFX", "FurnaceSparksVFX", "FurnaceSmokeVFX" })
            {
                var effectObject = objects.SingleOrDefault(item => item.name == effectName);
                var particles = effectObject?.GetComponentInChildren<ParticleSystem>(true);
                if (particles == null)
                {
                    failures.Add($"Shipping furnace is missing authored effect: {effectName}.");
                    continue;
                }
                var materialPath = AssetDatabase.GetAssetPath(
                    particles.GetComponent<ParticleSystemRenderer>().sharedMaterial);
                if (!materialPath.Contains("/VFX/Materials/", StringComparison.Ordinal))
                    failures.Add($"{effectName} is not using a soft transparent HAVENLINE particle material.");
            }
            var fireEffect = objects.SingleOrDefault(item => item.name == "FurnaceFireVFX")
                ?.GetComponentInChildren<ParticleSystem>(true);
            if (fireEffect != null && fireEffect.main.startSize.constantMax > 0.45f)
                failures.Add("Furnace fire particles are oversized and obscure the machine silhouette.");
            var smokeEffect = objects.SingleOrDefault(item => item.name == "FurnaceSmokeVFX");
            if (smokeEffect != null && smokeEffect.transform.localPosition.y < 2.4f)
                failures.Add("Furnace smoke must originate above the authored chimney.");

'''
replace_once(
    scene_gate,
    '''            foreach (var oldTentName in new[] { "StartingTent", "RescueShelter" })
            {
                var oldTent = objects.FirstOrDefault(item => item.name == oldTentName);
                if (oldTent != null && oldTent.activeInHierarchy)
                    failures.Add($"Superseded imported tent visual is still active: {oldTentName}.");
            }

            var renderers = scene.GetRootGameObjects()
''',
    '''            foreach (var oldTentName in new[] { "StartingTent", "RescueShelter" })
            {
                var oldTent = objects.FirstOrDefault(item => item.name == oldTentName);
                if (oldTent != null && oldTent.activeInHierarchy)
                    failures.Add($"Superseded imported tent visual is still active: {oldTentName}.");
            }

''' + particle_validation + '''            var renderers = scene.GetRootGameObjects()
''',
    "premium furnace effect gates",
)

revision = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlineStudioRevision.cs")
source = revision.read_text(encoding="utf-8")
if '0.1.0-review.11' not in source:
    raise SystemExit("Missing review 11 revision marker")
source = source.replace('0.1.0-review.11', '0.1.0-review.12', 1)
old = "Validate four gameplay-owned furnace machines, complete multi-part shelters, hidden superseded props, clean regeneration and unchanged premium image thresholds."
new = "Replace opaque furnace billboards with soft door fire, sparks and chimney smoke, apply dedicated machine finishes, and expose darker shelter fabric beneath a reduced snow cap."
if old not in source:
    raise SystemExit("Missing review 11 purpose")
revision.write_text(source.replace(old, new, 1), encoding="utf-8")
