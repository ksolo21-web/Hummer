from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    source = path.read_text(encoding="utf-8")
    if old not in source:
        raise SystemExit(f"Missing {label} in {path}")
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


assets = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlinePremiumVisualAssets.cs")
replace_once(
    assets,
    '''        internal const string ShelterShellPath = Root + "/HAVENLINE_ShelterShell.asset";
        internal const string PaleSnowMaterialPath =
''',
    '''        internal const string ShelterShellPath = Root + "/HAVENLINE_ShelterShell.asset";
        internal const string ShelterSnowCapPath = Root + "/HAVENLINE_ShelterSnowCap.asset";
        internal const string PaleSnowMaterialPath =
''',
    "shelter snow cap asset constant",
)
replace_once(
    assets,
    '''        private static bool generating;

        internal static void Ensure()
        {
            if (generating)
                return;
''',
    '''        private static bool generating;
        private static bool ensured;

        internal static void Ensure()
        {
            if (ensured || generating)
                return;
''',
    "idempotent premium visual asset generation",
)
replace_once(
    assets,
    '''                CreateMeshIfMissing(ShelterShellPath, CreateTentMesh(
                    "HAVENLINE_ShelterShell", 3.9f, 2.65f, 3.3f));
                CreateMaterialIfMissing(
''',
    '''                CreateMeshIfMissing(ShelterShellPath, CreateTentMesh(
                    "HAVENLINE_ShelterShell", 3.9f, 2.65f, 3.3f));
                CreateMeshIfMissing(ShelterSnowCapPath, CreateTentRoofCapMesh(
                    "HAVENLINE_ShelterSnowCap", 3.9f, 2.65f, 3.3f, 0.72f));
                CreateMaterialIfMissing(
''',
    "authored shelter roof snow mesh",
)
replace_once(
    assets,
    '''                AssetDatabase.SaveAssets();
            }
            finally
''',
    '''                AssetDatabase.SaveAssets();
                ensured = true;
            }
            finally
''',
    "premium visual asset completion state",
)
roof_method = '''        private static Mesh CreateTentRoofCapMesh(
            string name,
            float width,
            float height,
            float depth,
            float coverage)
        {
            var halfWidth = width * 0.5f;
            var halfDepth = depth * 0.5f + 0.055f;
            var lowX = halfWidth * Mathf.Clamp01(coverage);
            var lowY = height * (1f - Mathf.Clamp01(coverage)) + 0.08f;
            var ridgeY = height + 0.13f;
            var vertices = new List<Vector3>
            {
                new(-lowX, lowY, halfDepth), new(0f, ridgeY, halfDepth),
                new(0f, ridgeY, -halfDepth), new(-lowX, lowY, -halfDepth),
                new(0f, ridgeY, halfDepth), new(lowX, lowY, halfDepth),
                new(lowX, lowY, -halfDepth), new(0f, ridgeY, -halfDepth)
            };
            var triangles = new List<int>
            {
                0,1,2, 0,2,3,
                4,5,6, 4,6,7
            };
            var uv = new List<Vector2>
            {
                new(0f,0f), new(1f,0f), new(1f,1f), new(0f,1f),
                new(0f,0f), new(1f,0f), new(1f,1f), new(0f,1f)
            };
            return BuildMesh(name, vertices, triangles, uv);
        }

'''
replace_once(
    assets,
    '''        private static Mesh BuildMesh(
            string name,
''',
    roof_method + '''        private static Mesh BuildMesh(
            string name,
''',
    "shelter roof cap mesh builder",
)

scene = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlinePremiumSceneAuthoring.cs")
replace_once(
    scene,
    '''            var levelVisuals = new[]
            {
                InstantiateScaled(manifest.furnaceModel, root.transform, "FurnaceLevel1", 2.15f).gameObject,
                InstantiateScaled(manifest.furnaceLevel2Model, root.transform, "FurnaceLevel2", 2.45f).gameObject,
                InstantiateScaled(manifest.furnaceLevel3Model, root.transform, "FurnaceLevel3", 2.8f).gameObject,
                InstantiateScaled(manifest.furnaceLevel4Model, root.transform, "FurnaceLevel4", 3.15f).gameObject
            };
''',
    '''            var levelVisuals = HavenlinePremiumFurnaceAuthoring.BuildStages(root.transform);
''',
    "gameplay-owned furnace stages",
)

polish = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlinePremiumVisualPolish.cs")
replace_once(
    polish,
    '''            BuildCampDetails(dressing.transform);
            BuildFurnaceSilhouette(dressing.transform);
            BuildShelterSilhouettes(dressing.transform);
''',
    '''            BuildCampDetails(dressing.transform);
            BuildShelterSilhouettes(dressing.transform);
''',
    "remove decorative furnace overlay",
)
old_shelter = '''        private static void BuildShelter(Transform parent, string name, Vector3 position, float yaw, bool left)
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
new_shelter = '''        private static void BuildShelter(Transform parent, string name, Vector3 position, float yaw, bool left)
        {
            var root = new GameObject(name);
            root.transform.SetParent(parent, false);
            root.transform.localPosition = position;
            root.transform.localRotation = Quaternion.Euler(0f, yaw, 0f);

            CreateMeshObject(root.transform, "FabricShell",
                HavenlinePremiumVisualAssets.ShelterShellPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Blue.mat",
                Vector3.zero, Vector3.one, Quaternion.identity);
            CreateMeshObject(root.transform, "SnowRoof",
                HavenlinePremiumVisualAssets.ShelterSnowCapPath,
                HavenlinePremiumVisualAssets.PaleSnowMaterialPath,
                Vector3.zero, Vector3.one, Quaternion.identity);
            CreateMeshObject(root.transform, "DoorFrame",
                HavenlinePremiumVisualAssets.FurnaceBodyPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Amber.mat",
                new Vector3(0f, 0.12f, 1.71f), new Vector3(0.38f, 0.66f, 0.07f), Quaternion.identity);
            CreateMeshObject(root.transform, "InsulatedDoor",
                HavenlinePremiumVisualAssets.FurnaceBodyPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Navy.mat",
                new Vector3(0f, 0.20f, 1.83f), new Vector3(0.29f, 0.56f, 0.045f), Quaternion.identity);
            CreateMeshObject(root.transform, "DoorAwning",
                HavenlinePremiumVisualAssets.FurnaceHoodPath,
                HavenlinePremiumVisualAssets.PaleSnowMaterialPath,
                new Vector3(0f, 1.58f, 1.62f), new Vector3(0.36f, 0.12f, 0.44f),
                Quaternion.Euler(-8f, 0f, 0f));
            CreateMeshObject(root.transform, "EntryMat",
                HavenlinePremiumVisualAssets.PathPatchPath,
                HavenlinePremiumVisualAssets.SnowPathMaterialPath,
                new Vector3(0f, 0.075f, 2.12f), new Vector3(0.86f, 1f, 0.82f), Quaternion.identity);

            var lanternPosition = new Vector3(left ? 1.28f : -1.28f, 0.82f, 1.48f);
            CreateMeshObject(root.transform, "Lantern",
                HavenlinePremiumVisualAssets.FurnaceChimneyPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Amber.mat",
                lanternPosition, new Vector3(0.20f, 0.25f, 0.20f), Quaternion.identity);
            CreatePointLight(root.transform, "LanternLight", lanternPosition + Vector3.up * 0.18f,
                new Color(1f, 0.48f, 0.12f), 1.55f, 7.5f, false);

            for (var side = -1; side <= 1; side += 2)
            {
                CreateMeshObject(root.transform, side < 0 ? "LeftGuyPost" : "RightGuyPost",
                    HavenlinePremiumVisualAssets.FurnaceChimneyPath,
                    "Assets/Havenline/Art/Production/Materials/HAVENLINE_Navy.mat",
                    new Vector3(side * 1.82f, 0.04f, 1.36f),
                    new Vector3(0.11f, 0.42f, 0.11f), Quaternion.Euler(0f, 0f, side * 7f));
            }
        }
'''
replace_once(polish, old_shelter, new_shelter, "multi-part premium shelters")
replace_once(
    polish,
    '''            var leftTent = objects.FirstOrDefault(item => item.name == "StartingTent");
            var rightTent = objects.FirstOrDefault(item => item.name == "RescueShelter");
            if (leftTent != null) leftTent.transform.localScale *= 1.12f;
            if (rightTent != null) rightTent.transform.localScale *= 1.12f;
''',
    '''            var leftTent = objects.FirstOrDefault(item => item.name == "StartingTent");
            var rightTent = objects.FirstOrDefault(item => item.name == "RescueShelter");
            if (leftTent != null) leftTent.SetActive(false);
            if (rightTent != null) rightTent.SetActive(false);
''',
    "hide superseded imported tent visuals",
)

scene_gate = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlinePremiumSceneGate.cs")
validation = '''            var objects = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
                .Select(transform => transform.gameObject)
                .ToArray();

            var minimumStageRenderers = new[] { 6, 10, 16, 20 };
            for (var index = 0; index < minimumStageRenderers.Length; index++)
            {
                var stageName = $"FurnaceLevel{index + 1}";
                var stage = objects.SingleOrDefault(item => item.name == stageName);
                if (stage == null)
                {
                    failures.Add($"Shipping furnace is missing authored progression stage {index + 1}.");
                    continue;
                }
                var stageRenderers = stage.GetComponentsInChildren<Renderer>(true);
                if (stageRenderers.Length < minimumStageRenderers[index])
                {
                    failures.Add(
                        $"Furnace stage {index + 1} is not visually complete: found {stageRenderers.Length} renderers; " +
                        $"require at least {minimumStageRenderers[index]}.");
                }
                if (stageRenderers.Any(renderer =>
                        !string.IsNullOrWhiteSpace(
                            PrefabUtility.GetPrefabAssetPathOfNearestInstanceRoot(renderer.gameObject))))
                {
                    failures.Add($"Furnace stage {index + 1} still depends on an imported prop prefab instead of authored machine parts.");
                }
            }

            if (objects.Any(item => item.name.StartsWith("FurnacePremium", StringComparison.Ordinal)))
                failures.Add("Decorative furnace overlays are prohibited; progression stages must own the complete furnace silhouette.");

            foreach (var shelterName in new[] { "LeftPremiumShelter", "RightPremiumShelter" })
            {
                var shelter = objects.SingleOrDefault(item => item.name == shelterName);
                if (shelter == null)
                {
                    failures.Add($"Shipping outpost is missing authored shelter: {shelterName}.");
                    continue;
                }
                var shelterRenderers = shelter.GetComponentsInChildren<Renderer>(true).Length;
                if (shelterRenderers < 8)
                    failures.Add($"{shelterName} is not a complete multi-part shelter; found {shelterRenderers} renderers.");
            }

            foreach (var oldTentName in new[] { "StartingTent", "RescueShelter" })
            {
                var oldTent = objects.FirstOrDefault(item => item.name == oldTentName);
                if (oldTent != null && oldTent.activeInHierarchy)
                    failures.Add($"Superseded imported tent visual is still active: {oldTentName}.");
            }

'''
replace_once(
    scene_gate,
    '''            RequireAtLeast<HavenlineBarricade>(scene, 2, "barricades/defenses", failures);

            var renderers = scene.GetRootGameObjects()
''',
    '''            RequireAtLeast<HavenlineBarricade>(scene, 2, "barricades/defenses", failures);

''' + validation + '''            var renderers = scene.GetRootGameObjects()
''',
    "furnace and shelter premium scene gates",
)

revision = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlineStudioRevision.cs")
source = revision.read_text(encoding="utf-8")
if '0.1.0-review.10' not in source:
    raise SystemExit("Missing review 10 revision marker")
source = source.replace('0.1.0-review.10', '0.1.0-review.11', 1)
old = "Validate clean static-font generation, dedicated shaped HUD sprites, event-driven contextual panels and unchanged premium scene and image gates."
new = "Replace imported furnace props with four authored machine stages and rebuild both shelters as complete fabric, snow, door, awning and lantern assemblies."
if old not in source:
    raise SystemExit("Missing review 10 purpose")
revision.write_text(source.replace(old, new, 1), encoding="utf-8")
