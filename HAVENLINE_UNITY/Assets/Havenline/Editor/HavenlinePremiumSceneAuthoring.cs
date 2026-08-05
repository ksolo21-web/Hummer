using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.Animations;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;
using UnityEngine.SceneManagement;
using UnityEngine.UI;

namespace Havenline.Editor
{
    /// <summary>
    /// Authors the shipping frozen-outpost scene exclusively from the approved production
    /// manifest. There is no fallback to reference downloads, primitives or filename guessing.
    /// </summary>
    public static class HavenlinePremiumSceneAuthoring
    {
        private const string GeneratedRoot = "Assets/Havenline/Generated/Premium";

        [MenuItem("HAVENLINE Premium/Author Shipping Frozen Outpost")]
        public static void AuthorFromMenu()
        {
            var manifest = HavenlinePremiumBuildGate.RequireProductionContent();
            Author(manifest);
            HavenlinePremiumSceneGate.RequirePremiumScene(manifest);
        }

        public static void Author(HavenlinePremiumBuildGate.ProductionArtManifest manifest)
        {
            if (manifest == null)
                throw new ArgumentNullException(nameof(manifest));

            Directory.CreateDirectory("Assets/Havenline/Scenes");
            Directory.CreateDirectory(GeneratedRoot);
            EnsureUrp();

            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
            var root = new GameObject("HAVENLINE_FROZEN_OUTPOST_SHIPPING");

            var environment = InstantiateAsset<GameObject>(manifest.environmentPrefab, root.transform, "FrozenOutpostEnvironment");
            environment.transform.SetLocalPositionAndRotation(Vector3.zero, Quaternion.identity);
            EnsureLighting(root.transform);

            var input = new GameObject("MovementInput").AddComponent<HavenlineInputRouter>();
            input.transform.SetParent(root.transform);

            var player = BuildPlayer(root.transform, input, manifest);
            BuildCamera(root.transform, player.transform);
            var furnace = BuildFurnace(root.transform, manifest);
            BuildWorldProps(root.transform, manifest);
            BuildResourceNodes(root.transform, manifest);
            var helper = BuildHelper(root.transform, manifest);
            var wolfTemplate = BuildWolfTemplate(root.transform, manifest);
            var northSite = BuildDefenseSite(root.transform, "north_barricade", Reference.NorthBarricade, 0f, manifest);
            BuildDefenseSite(root.transform, "south_barricade", Reference.SouthBarricade, 180f, manifest);
            var gate = InstantiateAsset<GameObject>(manifest.forestGateModel, root.transform, "ConnectedForestGate");
            gate.transform.position = Reference.ForestGate;
            gate.SetActive(false);

            var directorObject = new GameObject("GameplayDirector");
            directorObject.transform.SetParent(root.transform);
            var director = directorObject.AddComponent<HavenlineGameDirector>();
            director.Configure(wolfTemplate, helper, furnace, northSite, gate);

            BuildInterface(root.transform, player, director, manifest);
            InstantiateAsset<GameObject>(manifest.audioRigPrefab, root.transform, "ProductionAudioRig");
            InstantiateAsset<GameObject>(manifest.snowfallVfxPrefab, root.transform, "ProductionSnowfall");
            root.AddComponent<HavenlinePerformance>();

            EditorSceneManager.SaveScene(scene, Reference.ScenePath);
            EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(Reference.ScenePath, true) };
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);
        }

        private static HavenlinePlayerController BuildPlayer(
            Transform parent,
            HavenlineInputRouter input,
            HavenlinePremiumBuildGate.ProductionArtManifest manifest)
        {
            var root = new GameObject("Player");
            root.transform.SetParent(parent);
            root.transform.position = Reference.PlayerSpawn;
            var character = root.AddComponent<CharacterController>();
            character.height = 1.78f;
            character.radius = 0.36f;
            character.center = new Vector3(0f, 0.89f, 0f);

            var inventory = root.AddComponent<HavenlineInventory>();
            var visual = InstantiateScaled(manifest.playerModel, root.transform, "PlayerVisual", 1.78f);
            var unityAnimator = EnsureAnimator(visual.gameObject, manifest.playerController);
            var actorAnimator = visual.gameObject.AddComponent<HavenlineActorAnimator>();
            actorAnimator.Configure(unityAnimator);

            var carryRoot = new GameObject("VisibleCarriedStack").transform;
            carryRoot.SetParent(visual, false);
            carryRoot.localPosition = new Vector3(0f, 1.03f, -0.34f);
            carryRoot.localRotation = Quaternion.Euler(0f, 180f, 0f);
            InstantiateScaled(manifest.backpackModel, carryRoot, "Backpack", 0.58f);
            var carryVisual = carryRoot.gameObject.AddComponent<HavenlineCarryVisual>();
            carryVisual.Configure(
                BuildCarrySlots(carryRoot, manifest.logModel, "Wood", 8, 0.26f),
                BuildCarrySlots(carryRoot, manifest.stoneResourceModel, "Stone", 8, 0.18f),
                BuildCarrySlots(carryRoot, manifest.metalResourceModel, "Metal", 8, 0.17f),
                BuildCarrySlots(carryRoot, manifest.fuelResourceModel, "Fuel", 8, 0.19f));
            inventory.Configure(carryRoot, carryVisual, Reference.CarryCapacity);

            var player = root.AddComponent<HavenlinePlayerController>();
            player.Configure(input, visual, actorAnimator);
            return player;
        }

        private static HavenlineHelper BuildHelper(
            Transform parent,
            HavenlinePremiumBuildGate.ProductionArtManifest manifest)
        {
            var root = new GameObject("FrozenSurvivor");
            root.transform.SetParent(parent);
            root.transform.position = Reference.Survivor;
            var character = root.AddComponent<CharacterController>();
            character.height = 1.72f;
            character.radius = 0.35f;
            character.center = new Vector3(0f, 0.86f, 0f);

            var inventory = root.AddComponent<HavenlineInventory>();
            var visual = InstantiateScaled(manifest.survivorModel, root.transform, "SurvivorVisual", 1.72f);
            var unityAnimator = EnsureAnimator(visual.gameObject, manifest.survivorController);
            var actorAnimator = visual.gameObject.AddComponent<HavenlineActorAnimator>();
            actorAnimator.Configure(unityAnimator);

            var carryRoot = new GameObject("HelperCarriedStack").transform;
            carryRoot.SetParent(visual, false);
            carryRoot.localPosition = new Vector3(0f, 0.98f, -0.31f);
            carryRoot.localRotation = Quaternion.Euler(0f, 180f, 0f);
            InstantiateScaled(manifest.backpackModel, carryRoot, "HelperBackpack", 0.52f);
            var carryVisual = carryRoot.gameObject.AddComponent<HavenlineCarryVisual>();
            carryVisual.Configure(
                BuildCarrySlots(carryRoot, manifest.logModel, "HelperWood", 8, 0.24f),
                BuildCarrySlots(carryRoot, manifest.stoneResourceModel, "HelperStone", 8, 0.17f),
                BuildCarrySlots(carryRoot, manifest.metalResourceModel, "HelperMetal", 8, 0.16f),
                BuildCarrySlots(carryRoot, manifest.fuelResourceModel, "HelperFuel", 8, 0.18f));
            inventory.Configure(carryRoot, carryVisual, Reference.CarryCapacity);

            var rescueEffect = InstantiateEffect(manifest.buildVfxPrefab, root.transform, "RescueThawVFX");
            var helper = root.AddComponent<HavenlineHelper>();
            helper.Configure(visual, actorAnimator);
            AssignSerialized(helper, "rescueEffect", rescueEffect);
            return helper;
        }

        private static HavenlineFurnace BuildFurnace(
            Transform parent,
            HavenlinePremiumBuildGate.ProductionArtManifest manifest)
        {
            var root = new GameObject("Furnace");
            root.transform.SetParent(parent);
            root.transform.position = Reference.Furnace;
            var collider = root.AddComponent<SphereCollider>();
            collider.radius = 1.85f;
            collider.isTrigger = true;

            var levelVisuals = HavenlinePremiumFurnaceAuthoring.BuildStages(root.transform);
            for (var index = 0; index < levelVisuals.Length; index++)
                levelVisuals[index].SetActive(index == 0);

            var warmth = new GameObject("WarmthBoundary");
            warmth.transform.SetParent(root.transform, false);
            warmth.transform.localPosition = new Vector3(0f, 0.04f, 0f);
            warmth.AddComponent<MeshFilter>().sharedMesh = CreateRingMesh();
            warmth.AddComponent<MeshRenderer>().sharedMaterial = Load<Material>(manifest.warmthMaterial);

            var lightObject = new GameObject("FurnaceLight");
            lightObject.transform.SetParent(root.transform, false);
            lightObject.transform.localPosition = new Vector3(0f, 1.25f, 0f);
            var light = lightObject.AddComponent<Light>();
            light.type = LightType.Point;
            light.color = new Color(1f, 0.36f, 0.08f);
            light.shadows = LightShadows.Soft;
            light.shadowStrength = 0.72f;

            var fire = InstantiateEffect(manifest.fireVfxPrefab, root.transform, "FurnaceFireVFX");
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
            var furnace = root.AddComponent<HavenlineFurnace>();
            furnace.Configure(warmth.transform, light, fire, delivery, levelVisuals, FindHeatedSnowRenderers());
            return furnace;
        }

        private static HavenlineEnemy BuildWolfTemplate(
            Transform parent,
            HavenlinePremiumBuildGate.ProductionArtManifest manifest)
        {
            var root = new GameObject("WolfEnemyTemplate");
            root.transform.SetParent(parent);
            root.transform.position = new Vector3(0f, -20f, 0f);
            var character = root.AddComponent<CharacterController>();
            character.height = 0.96f;
            character.radius = 0.38f;
            character.center = new Vector3(0f, 0.48f, 0f);
            var visual = InstantiateScaled(manifest.wolfModel, root.transform, "WolfVisual", 0.96f);
            var unityAnimator = EnsureAnimator(visual.gameObject, manifest.wolfController);
            var actorAnimator = visual.gameObject.AddComponent<HavenlineActorAnimator>();
            actorAnimator.Configure(unityAnimator);
            var hitEffect = InstantiateEffect(manifest.hitVfxPrefab, root.transform, "WolfHitVFX");
            var enemy = root.AddComponent<HavenlineEnemy>();
            enemy.Configure(visual, actorAnimator);
            AssignSerialized(enemy, "hitEffect", hitEffect);
            root.SetActive(false);
            return enemy;
        }

        private static HavenlineConstructionSite BuildDefenseSite(
            Transform parent,
            string id,
            Vector3 position,
            float yaw,
            HavenlinePremiumBuildGate.ProductionArtManifest manifest)
        {
            var root = new GameObject(id == "north_barricade" ? "NorthBarricadeSite" : "SouthBarricadeSite");
            root.transform.SetParent(parent);
            root.transform.position = position;
            root.transform.rotation = Quaternion.Euler(0f, yaw, 0f);

            var stageA = InstantiateScaled(manifest.barricadeModel, root.transform, "ConstructionStageA", 0.55f).gameObject;
            var stageB = InstantiateScaled(manifest.barricadeModel, root.transform, "ConstructionStageB", 0.95f).gameObject;
            var stageC = InstantiateScaled(manifest.barricadeModel, root.transform, "ConstructionStageC", 1.35f).gameObject;
            stageA.transform.localScale *= 0.42f;
            stageB.transform.localScale *= 0.72f;
            stageC.transform.localScale *= 0.9f;

            var completed = InstantiateScaled(manifest.barricadeModel, root.transform, "CompletedBarricade", 1.45f).gameObject;
            var collider = completed.GetComponent<Collider>() ?? completed.AddComponent<BoxCollider>();
            if (collider is BoxCollider box)
            {
                box.size = new Vector3(6.2f, 1.4f, 0.8f);
                box.center = new Vector3(0f, 0.7f, 0f);
            }
            completed.AddComponent<HavenlineBarricade>();
            completed.SetActive(false);

            var buildEffect = InstantiateEffect(manifest.buildVfxPrefab, root.transform, "ConstructionVFX");
            var site = root.AddComponent<HavenlineConstructionSite>();
            site.Configure(id, 8, 3, new[] { stageA, stageB, stageC }, completed, buildEffect);
            return site;
        }

        private static void BuildWorldProps(
            Transform parent,
            HavenlinePremiumBuildGate.ProductionArtManifest manifest)
        {
            Place(manifest.tentModel, parent, "StartingTent", Reference.TentLeft, 2.6f, 14f);
            Place(manifest.storageModel, parent, "SupplyStorage", Reference.Storage, 1.65f, -8f);
            Place(manifest.campfireModel, parent, "Campfire", Reference.Campfire, 1.05f, 0f);
            Place(manifest.tentModel, parent, "RescueShelter", Reference.TentRight, 2.5f, -14f);

            var scenery = new[]
            {
                new Vector3(-12f,0f,10f), new Vector3(12f,0f,10f),
                new Vector3(-12f,0f,-9f), new Vector3(12f,0f,-9f),
                new Vector3(-5f,0f,-13f), new Vector3(5f,0f,-13f)
            };
            for (var index = 0; index < scenery.Length; index++)
            {
                var asset = index % 2 == 0 ? manifest.pineModelA : manifest.pineModelB;
                Place(asset, parent, $"BoundaryPine_{index}", scenery[index], 4.8f + (index % 3) * 0.3f, index * 53f);
            }
        }

        private static void BuildResourceNodes(
            Transform parent,
            HavenlinePremiumBuildGate.ProductionArtManifest manifest)
        {
            for (var index = 0; index < Reference.WoodNodes.Length; index++)
            {
                var asset = index % 2 == 0 ? manifest.pineModelA : manifest.pineModelB;
                BuildResource(parent, $"WoodNode_{index}", asset, Reference.WoodNodes[index], 4.0f, ResourceKind.Wood, 18, manifest);
            }
            for (var index = 0; index < Reference.StoneNodes.Length; index++)
            {
                var asset = index % 2 == 0 ? manifest.rockModelA : manifest.rockModelB;
                BuildResource(parent, $"StoneNode_{index}", asset, Reference.StoneNodes[index], 1.4f, ResourceKind.Stone, 14, manifest);
            }
            BuildResource(parent, "MetalNode_0", manifest.metalResourceModel, new Vector3(-9.4f, 0f, -8.8f), 1.25f, ResourceKind.Metal, 10, manifest);
            BuildResource(parent, "FuelNode_0", manifest.fuelResourceModel, new Vector3(9.5f, 0f, -8.6f), 1.2f, ResourceKind.Fuel, 10, manifest);
        }

        private static void BuildResource(
            Transform parent,
            string name,
            string assetPath,
            Vector3 position,
            float height,
            ResourceKind kind,
            int units,
            HavenlinePremiumBuildGate.ProductionArtManifest manifest)
        {
            var root = new GameObject(name);
            root.transform.SetParent(parent);
            root.transform.position = position;
            root.transform.rotation = Quaternion.Euler(0f, Mathf.Abs(name.GetHashCode()) % 360, 0f);
            var visual = InstantiateScaled(assetPath, root.transform, name + "Visual", height);
            if (visual.GetComponentInChildren<Collider>(true) == null)
            {
                var collider = root.AddComponent<CapsuleCollider>();
                collider.height = Mathf.Max(1f, height);
                collider.radius = Mathf.Max(0.35f, height * 0.17f);
                collider.center = new Vector3(0f, height * 0.5f, 0f);
            }
            var effect = InstantiateEffect(manifest.gatherVfxPrefab, root.transform, name + "ImpactVFX");
            var node = root.AddComponent<HavenlineResourceNode>();
            node.Configure(kind, units, kind == ResourceKind.Wood ? 0.58f : 0.7f, effect, null);
        }

        private static void BuildCamera(Transform parent, Transform target)
        {
            var cameraObject = new GameObject("GameplayCamera");
            cameraObject.transform.SetParent(parent);
            cameraObject.tag = "MainCamera";
            var camera = cameraObject.AddComponent<Camera>();
            camera.orthographic = true;
            camera.orthographicSize = Reference.CameraSize;
            camera.nearClipPlane = 0.15f;
            camera.farClipPlane = 140f;
            camera.clearFlags = CameraClearFlags.Skybox;
            camera.allowHDR = true;
            camera.allowMSAA = true;
            camera.useOcclusionCulling = true;
            cameraObject.AddComponent<AudioListener>();
            var rig = cameraObject.AddComponent<HavenlineCameraRig>();
            rig.Configure(target);
        }

        private static void BuildInterface(
            Transform parent,
            HavenlinePlayerController player,
            HavenlineGameDirector director,
            HavenlinePremiumBuildGate.ProductionArtManifest manifest)
        {
            var hudObject = InstantiateAsset<GameObject>(manifest.hudPrefab, parent, "GameplayHUD");
            var hud = hudObject.GetComponentInChildren<HavenlineHud>(true) ?? hudObject.AddComponent<HavenlineHud>();
            hud.Configure(
                FindNamed<Text>(hudObject, "ResourcesText"),
                FindNamed<Text>(hudObject, "ObjectiveText"),
                FindNamed<Text>(hudObject, "StatusText"),
                FindNamed<Text>(hudObject, "ContextText"),
                FindNamed<Text>(hudObject, "ThreatText"),
                FindNamed<Image>(hudObject, "ContextProgress"),
                player,
                director);

            var pauseObject = InstantiateAsset<GameObject>(manifest.pauseMenuPrefab, parent, "PauseSettings");
            pauseObject.SetActive(false);
        }

        private static GameObject[] BuildCarrySlots(
            Transform parent,
            string assetPath,
            string prefix,
            int count,
            float height)
        {
            var slots = new GameObject[count];
            for (var index = 0; index < count; index++)
            {
                var slot = new GameObject($"{prefix}_{index + 1}");
                slot.transform.SetParent(parent, false);
                var row = index / 4;
                var column = index % 4;
                slot.transform.localPosition = new Vector3((column - 1.5f) * 0.16f, 0.15f + row * 0.18f, -0.08f - row * 0.08f);
                slot.transform.localRotation = Quaternion.Euler(0f, column * 12f, prefix.Contains("Wood", StringComparison.Ordinal) ? 90f : 0f);
                InstantiateScaled(assetPath, slot.transform, prefix + "Visual", height);
                slots[index] = slot;
            }
            return slots;
        }

        private static Animator EnsureAnimator(GameObject visual, string controllerPath)
        {
            var animator = visual.GetComponentInChildren<Animator>(true) ?? visual.AddComponent<Animator>();
            animator.runtimeAnimatorController = Load<AnimatorController>(controllerPath);
            animator.applyRootMotion = false;
            animator.updateMode = AnimatorUpdateMode.Normal;
            animator.cullingMode = AnimatorCullingMode.CullUpdateTransforms;
            EditorUtility.SetDirty(animator);
            if (PrefabUtility.IsPartOfPrefabInstance(animator))
                PrefabUtility.RecordPrefabInstancePropertyModifications(animator);
            return animator;
        }

        private static ParticleSystem InstantiateEffect(string path, Transform parent, string name)
        {
            var instance = InstantiateAsset<GameObject>(path, parent, name);
            return instance.GetComponentInChildren<ParticleSystem>(true);
        }

        private static Transform Place(string asset, Transform parent, string name, Vector3 position, float height, float yaw)
        {
            var visual = InstantiateScaled(asset, parent, name, height);
            visual.position = position;
            visual.rotation = Quaternion.Euler(0f, yaw, 0f);
            return visual;
        }

        private static Transform InstantiateScaled(string path, Transform parent, string name, float targetHeight)
        {
            var instance = InstantiateAsset<GameObject>(path, parent, name);
            var renderers = instance.GetComponentsInChildren<Renderer>(true);
            if (renderers.Length > 0)
            {
                var bounds = renderers[0].bounds;
                foreach (var renderer in renderers.Skip(1))
                    bounds.Encapsulate(renderer.bounds);
                if (bounds.size.y > 0.001f)
                    instance.transform.localScale *= targetHeight / bounds.size.y;
            }
            return instance.transform;
        }

        private static T InstantiateAsset<T>(string path, Transform parent, string name) where T : UnityEngine.Object
        {
            var asset = Load<T>(path);
            var instance = PrefabUtility.InstantiatePrefab(asset, parent) as T;
            if (instance == null)
                throw new InvalidOperationException($"Could not instantiate production asset: {path}");
            instance.name = name;
            return instance;
        }

        private static T Load<T>(string path) where T : UnityEngine.Object
        {
            var asset = AssetDatabase.LoadAssetAtPath<T>(path);
            if (asset == null)
                throw new FileNotFoundException($"Production asset failed to import as {typeof(T).Name}", path);
            return asset;
        }

        private static T FindNamed<T>(GameObject root, string name) where T : Component
        {
            var component = root.GetComponentsInChildren<T>(true)
                .FirstOrDefault(candidate => string.Equals(candidate.name, name, StringComparison.Ordinal));
            if (component == null)
                throw new InvalidOperationException($"Premium UI prefab is missing {typeof(T).Name} named '{name}'.");
            return component;
        }

        private static Renderer[] FindHeatedSnowRenderers() =>
            UnityEngine.Object.FindObjectsByType<Renderer>(FindObjectsInactive.Include, FindObjectsSortMode.None)
                .Where(renderer => renderer.name.Contains("HeatedSnow", StringComparison.OrdinalIgnoreCase) ||
                                   renderer.name.Contains("Thaw", StringComparison.OrdinalIgnoreCase))
                .ToArray();

        private static void AssignSerialized(UnityEngine.Object target, string property, UnityEngine.Object value)
        {
            var serialized = new SerializedObject(target);
            var field = serialized.FindProperty(property);
            if (field == null)
                throw new InvalidOperationException($"Serialized property '{property}' was not found on {target.GetType().Name}.");
            field.objectReferenceValue = value;
            serialized.ApplyModifiedPropertiesWithoutUndo();
        }

        private static Mesh CreateRingMesh()
        {
            const int segments = 128;
            var vertices = new Vector3[segments * 2];
            var triangles = new int[segments * 6];
            for (var index = 0; index < segments; index++)
            {
                var angle = index * Mathf.PI * 2f / segments;
                var direction = new Vector3(Mathf.Cos(angle), 0f, Mathf.Sin(angle));
                vertices[index * 2] = direction * 0.97f;
                vertices[index * 2 + 1] = direction;
                var next = (index + 1) % segments;
                var triangle = index * 6;
                triangles[triangle] = index * 2;
                triangles[triangle + 1] = next * 2;
                triangles[triangle + 2] = index * 2 + 1;
                triangles[triangle + 3] = index * 2 + 1;
                triangles[triangle + 4] = next * 2;
                triangles[triangle + 5] = next * 2 + 1;
            }
            var mesh = new Mesh { name = "HAVENLINE_WarmthBoundary" };
            mesh.vertices = vertices;
            mesh.triangles = triangles;
            mesh.RecalculateNormals();
            mesh.RecalculateBounds();
            return mesh;
        }

        private static void EnsureLighting(Transform parent)
        {
            if (UnityEngine.Object.FindFirstObjectByType<Light>() != null)
                return;
            var sun = new GameObject("WinterKeyLight");
            sun.transform.SetParent(parent);
            sun.transform.rotation = Quaternion.Euler(46f, -34f, 0f);
            var light = sun.AddComponent<Light>();
            light.type = LightType.Directional;
            light.color = new Color(0.78f, 0.88f, 1f);
            light.intensity = 1.18f;
            light.shadows = LightShadows.Soft;
        }

        private static void EnsureUrp()
        {
            Directory.CreateDirectory(GeneratedRoot + "/RenderPipeline");
            var rendererPath = GeneratedRoot + "/RenderPipeline/HavenlinePremiumRenderer.asset";
            var pipelinePath = GeneratedRoot + "/RenderPipeline/HavenlinePremiumURP.asset";
            var renderer = AssetDatabase.LoadAssetAtPath<UniversalRendererData>(rendererPath);
            if (renderer == null)
            {
                renderer = ScriptableObject.CreateInstance<UniversalRendererData>();
                AssetDatabase.CreateAsset(renderer, rendererPath);
            }
            var pipeline = AssetDatabase.LoadAssetAtPath<UniversalRenderPipelineAsset>(pipelinePath);
            if (pipeline == null)
            {
                pipeline = UniversalRenderPipelineAsset.Create(renderer);
                AssetDatabase.CreateAsset(pipeline, pipelinePath);
            }
            GraphicsSettings.defaultRenderPipeline = pipeline;
            QualitySettings.renderPipeline = pipeline;
            QualitySettings.vSyncCount = 0;
            QualitySettings.shadowDistance = 48f;
        }
    }
}
