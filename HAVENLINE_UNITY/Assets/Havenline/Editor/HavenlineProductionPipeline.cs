using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Havenline;
using Unity.AI.Navigation;
using UnityEditor;
using UnityEditor.Animations;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.AI;
using UnityEngine.EventSystems;
using UnityEngine.InputSystem;
using UnityEngine.InputSystem.UI;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;
using UnityEngine.SceneManagement;
using UnityEngine.UI;

namespace Havenline.Editor
{
    public static class HavenlineProductionPipeline
    {
        private const string Root = "Assets/Havenline";
        private const string ThirdParty = Root + "/ThirdParty";
        private const string Generated = Root + "/Generated";
        private const string Prefabs = Generated + "/Prefabs";
        private const string Materials = Generated + "/Materials";
        private const string Controllers = Generated + "/Controllers";
        private const string Settings = Root + "/Settings";
        private const string ScenePath = Root + "/Scenes/FrozenOutpost.unity";
        private const string BuildDirectory = "Builds/Android";

        private static readonly string[] CharacterTokens =
        {
            "survivor", "adventurer", "worker", "viking", "soldier", "human", "character"
        };

        [MenuItem("HAVENLINE/Build Fresh Unity Frozen Outpost")]
        public static void PrepareFromMenu() => Prepare();

        public static void Prepare()
        {
            EnsureFolders();
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);
            EnsureUrp();
            var input = EnsureInput();
            var assets = DiscoverAssets();
            var prefabs = BuildPrefabs(assets);
            var config = BuildConfig(prefabs);
            BuildScene(prefabs, config, input);
            ConfigurePlayer();
            CaptureReviewFrames();
            AssetDatabase.SaveAssets();
            Debug.Log("HAVENLINE Unity frozen outpost prepared from the fresh Unity 6 URP project.");
        }

        public static void BuildReviewCandidate()
        {
            Prepare();
            Directory.CreateDirectory(BuildDirectory);
            var path = Path.Combine(BuildDirectory, "HAVENLINE-Unity6-review-candidate-arm64.apk");
            var report = BuildPipeline.BuildPlayer(new BuildPlayerOptions
            {
                scenes = new[] { ScenePath },
                locationPathName = path,
                target = BuildTarget.Android,
                options = BuildOptions.Development | BuildOptions.CompressWithLz4HC
            });

            if (report.summary.result != UnityEditor.Build.Reporting.BuildResult.Succeeded)
            {
                throw new BuildFailedException($"HAVENLINE review APK failed: {report.summary.result}");
            }

            File.WriteAllText(path + ".sha256.pending", "Generate SHA-256 in CI after Unity exits.\n");
            Debug.Log($"HAVENLINE review APK built: {path}");
        }

        private static void EnsureFolders()
        {
            foreach (var folder in new[]
                     {
                         Root + "/Scenes", Root + "/Production", Settings, Generated,
                         Prefabs, Materials, Controllers, Generated + "/Meshes", Generated + "/UI"
                     })
            {
                Directory.CreateDirectory(folder);
            }
        }

        private static void EnsureUrp()
        {
            var rendererPath = Settings + "/HavenlineRenderer.asset";
            var pipelinePath = Settings + "/HavenlineURP.asset";
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
            QualitySettings.shadowDistance = 45f;
            EditorUtility.SetDirty(pipeline);
        }

        private static InputActionReference EnsureInput()
        {
            var path = Settings + "/HavenlineInput.inputactions";
            var asset = AssetDatabase.LoadAssetAtPath<InputActionAsset>(path);
            if (asset == null)
            {
                asset = ScriptableObject.CreateInstance<InputActionAsset>();
                var map = asset.AddActionMap("Gameplay");
                var move = map.AddAction("Move", InputActionType.Value, expectedControlLayout: "Vector2");
                move.AddCompositeBinding("2DVector")
                    .With("Up", "<Keyboard>/w")
                    .With("Down", "<Keyboard>/s")
                    .With("Left", "<Keyboard>/a")
                    .With("Right", "<Keyboard>/d");
                move.AddCompositeBinding("2DVector")
                    .With("Up", "<Keyboard>/upArrow")
                    .With("Down", "<Keyboard>/downArrow")
                    .With("Left", "<Keyboard>/leftArrow")
                    .With("Right", "<Keyboard>/rightArrow");
                move.AddBinding("<Gamepad>/leftStick");
                AssetDatabase.CreateAsset(asset, path);
                var reference = InputActionReference.Create(move);
                reference.name = "MoveReference";
                AssetDatabase.AddObjectToAsset(reference, asset);
                AssetDatabase.SaveAssets();
            }

            return AssetDatabase.LoadAllAssetsAtPath(path).OfType<InputActionReference>().First();
        }

        private sealed class AssetSet
        {
            public string Player;
            public string Survivor;
            public string Wolf;
            public string Campfire;
            public string Tent;
            public string Barricade;
            public string Wood;
            public string Scrap;
            public string Fuel;
            public string Backpack;
            public string Tree;
            public string Rock;
        }

        private sealed class PrefabSet
        {
            public GameObject Player;
            public GameObject Survivor;
            public GameObject Wolf;
            public GameObject Furnace;
            public GameObject Tent;
            public GameObject Barricade;
            public GameObject Wood;
            public GameObject Scrap;
            public GameObject Fuel;
            public GameObject Tree;
            public GameObject Rock;
        }

        private static AssetSet DiscoverAssets()
        {
            var all = AssetDatabase.FindAssets("t:Model", new[] { ThirdParty })
                .Select(AssetDatabase.GUIDToAssetPath)
                .Where(path => path.EndsWith(".fbx", StringComparison.OrdinalIgnoreCase) ||
                               path.EndsWith(".obj", StringComparison.OrdinalIgnoreCase))
                .Distinct()
                .ToArray();

            if (all.Length < 12)
            {
                throw new BuildFailedException(
                    $"HAVENLINE needs the downloaded CC0 production asset packs; only {all.Length} model files were found.");
            }

            string Pick(string label, params string[] tokens)
            {
                var selected = all
                    .Select(path => new
                    {
                        Path = path,
                        Score = tokens.Sum(token => path.Contains(token, StringComparison.OrdinalIgnoreCase) ? 10 : 0) +
                                (path.Contains("FBX", StringComparison.OrdinalIgnoreCase) ? 1 : 0)
                    })
                    .OrderByDescending(item => item.Score)
                    .ThenBy(item => item.Path.Length)
                    .FirstOrDefault(item => item.Score > 0)?.Path;
                if (selected == null)
                {
                    throw new BuildFailedException($"No imported model matched HAVENLINE role '{label}' ({string.Join(", ", tokens)}).");
                }
                return selected;
            }

            var characterPaths = all.Where(path =>
                path.Contains("character", StringComparison.OrdinalIgnoreCase) ||
                path.Contains("human", StringComparison.OrdinalIgnoreCase) ||
                path.Contains("survivor", StringComparison.OrdinalIgnoreCase)).ToArray();
            var player = characterPaths.FirstOrDefault(path => CharacterTokens.Any(token => path.Contains(token, StringComparison.OrdinalIgnoreCase)))
                         ?? Pick("player", CharacterTokens);
            var survivor = characterPaths.FirstOrDefault(path => path != player) ?? player;

            return new AssetSet
            {
                Player = player,
                Survivor = survivor,
                Wolf = Pick("wolf", "wolf"),
                Campfire = Pick("furnace", "campfire", "fireplace", "barrel"),
                Tent = Pick("tent", "tent", "shelter"),
                Barricade = Pick("barricade", "barricade", "fence", "wall"),
                Wood = Pick("wood", "wood", "log", "plank"),
                Scrap = Pick("scrap", "scrap", "metal", "rock", "stone"),
                Fuel = Pick("fuel", "fuel", "coal", "barrel", "crate"),
                Backpack = all.FirstOrDefault(path => path.Contains("backpack", StringComparison.OrdinalIgnoreCase) || path.Contains("bag", StringComparison.OrdinalIgnoreCase)),
                Tree = Pick("tree", "tree", "pine", "spruce"),
                Rock = Pick("rock", "rock", "stone", "boulder")
            };
        }

        private static PrefabSet BuildPrefabs(AssetSet assets)
        {
            var player = BuildCharacterPrefab("HAVENLINE_Player", assets.Player, assets.Backpack, true);
            var survivor = BuildCharacterPrefab("HAVENLINE_Survivor", assets.Survivor, assets.Backpack, false);
            var wolf = BuildWolfPrefab(assets.Wolf);
            var wood = BuildResourcePrefab("HAVENLINE_Wood", assets.Wood, HavenlineResourceKind.Wood, 18);
            var scrap = BuildResourcePrefab("HAVENLINE_Scrap", assets.Scrap, HavenlineResourceKind.Scrap, 14);
            var fuel = BuildResourcePrefab("HAVENLINE_Fuel", assets.Fuel, HavenlineResourceKind.Fuel, 10);
            var furnace = BuildFurnacePrefab(assets.Campfire);
            var tent = BuildStaticPrefab("HAVENLINE_Tent", assets.Tent, 2.7f, true);
            var barricade = BuildBarricadePrefab(assets.Barricade);
            var tree = BuildStaticPrefab("HAVENLINE_Tree", assets.Tree, 3.9f, true);
            var rock = BuildStaticPrefab("HAVENLINE_Rock", assets.Rock, 1.5f, true);
            return new PrefabSet
            {
                Player = player, Survivor = survivor, Wolf = wolf, Furnace = furnace,
                Tent = tent, Barricade = barricade, Wood = wood, Scrap = scrap, Fuel = fuel,
                Tree = tree, Rock = rock
            };
        }

        private static GameObject BuildCharacterPrefab(string name, string modelPath, string backpackPath, bool player)
        {
            var root = new GameObject(name);
            var visual = AddModel(root.transform, modelPath, 1.78f);
            var animator = visual.GetComponentInChildren<Animator>() ?? visual.AddComponent<Animator>();
            animator.runtimeAnimatorController = BuildController(name, modelPath, false);
            var controller = root.AddComponent<CharacterController>();
            controller.height = 1.75f;
            controller.radius = 0.34f;
            controller.center = new Vector3(0, 0.88f, 0);
            var inventory = root.AddComponent<HavenlineInventory>();
            var carryRoot = new GameObject("VisibleCarriedSupplies").transform;
            carryRoot.SetParent(root.transform, false);
            carryRoot.localPosition = new Vector3(0, 1.05f, -0.28f);
            if (!string.IsNullOrWhiteSpace(backpackPath))
            {
                var pack = AddModel(carryRoot, backpackPath, 0.55f);
                pack.transform.localRotation = Quaternion.Euler(0, 180, 0);
            }
            SetObject(inventory, "carriedVisualRoot", carryRoot);
            carryRoot.gameObject.SetActive(false);

            if (player)
            {
                var motor = root.AddComponent<HavenlinePlayerMotor>();
                SetObject(motor, "animator", animator);
            }
            else
            {
                var agent = root.AddComponent<NavMeshAgent>();
                agent.speed = 3.25f;
                agent.acceleration = 18f;
                agent.angularSpeed = 720f;
                agent.stoppingDistance = 1.1f;
                var helper = root.AddComponent<HavenlineSurvivorHelper>();
                SetObject(helper, "animator", animator);
            }

            return SavePrefab(root, Prefabs + "/" + name + ".prefab");
        }

        private static GameObject BuildWolfPrefab(string modelPath)
        {
            var root = new GameObject("HAVENLINE_Wolf");
            var visual = AddModel(root.transform, modelPath, 0.95f);
            var animator = visual.GetComponentInChildren<Animator>() ?? visual.AddComponent<Animator>();
            animator.runtimeAnimatorController = BuildController("HAVENLINE_Wolf", modelPath, true);
            var agent = root.AddComponent<NavMeshAgent>();
            agent.speed = 4.3f;
            agent.acceleration = 16f;
            agent.angularSpeed = 900f;
            agent.stoppingDistance = 1.05f;
            agent.radius = 0.35f;
            var wolf = root.AddComponent<HavenlineWolf>();
            SetObject(wolf, "animator", animator);
            AddBoundsCollider(root, visual, false);
            return SavePrefab(root, Prefabs + "/HAVENLINE_Wolf.prefab");
        }

        private static GameObject BuildResourcePrefab(string name, string modelPath, HavenlineResourceKind kind, int amount)
        {
            var root = new GameObject(name);
            var visual = AddModel(root.transform, modelPath, kind == HavenlineResourceKind.Wood ? 1.25f : 1.0f);
            var trigger = root.AddComponent<SphereCollider>();
            trigger.isTrigger = true;
            trigger.radius = 1.55f;
            var node = root.AddComponent<HavenlineResourceNode>();
            SetEnum(node, "kind", (int)kind);
            SetInt(node, "remaining", amount);
            AddBoundsCollider(root, visual, false, true);
            return SavePrefab(root, Prefabs + "/" + name + ".prefab");
        }

        private static GameObject BuildFurnacePrefab(string modelPath)
        {
            var root = new GameObject("HAVENLINE_Furnace");
            AddModel(root.transform, modelPath, 2.1f);
            var trigger = root.AddComponent<SphereCollider>();
            trigger.isTrigger = true;
            trigger.radius = 1.8f;
            var warmth = root.AddComponent<HavenlineWarmthZone>();
            warmth.Configure(4f, 11f);
            var ring = new GameObject("WarmthRing");
            ring.transform.SetParent(root.transform, false);
            ring.transform.localPosition = new Vector3(0, 0.04f, 0);
            var filter = ring.AddComponent<MeshFilter>();
            filter.sharedMesh = EnsureRingMesh();
            var renderer = ring.AddComponent<MeshRenderer>();
            renderer.sharedMaterial = EnsureMaterial("Warmth", new Color(1f, 0.34f, 0.08f, 0.22f), true);
            SetObject(warmth, "visualRing", ring.transform);
            var furnace = root.AddComponent<HavenlineFurnace>();
            SetObject(furnace, "warmthZone", warmth);
            var lightObject = new GameObject("FurnaceLight");
            lightObject.transform.SetParent(root.transform, false);
            lightObject.transform.localPosition = new Vector3(0, 1.2f, 0);
            var light = lightObject.AddComponent<Light>();
            light.type = LightType.Point;
            light.color = new Color(1f, 0.35f, 0.08f);
            light.range = 9f;
            light.intensity = 4.5f;
            return SavePrefab(root, Prefabs + "/HAVENLINE_Furnace.prefab");
        }

        private static GameObject BuildBarricadePrefab(string modelPath)
        {
            var root = new GameObject("HAVENLINE_Barricade");
            var visual = AddModel(root.transform, modelPath, 1.65f);
            AddBoundsCollider(root, visual, true);
            root.AddComponent<HavenlineBarricade>();
            return SavePrefab(root, Prefabs + "/HAVENLINE_Barricade.prefab");
        }

        private static GameObject BuildStaticPrefab(string name, string modelPath, float height, bool collider)
        {
            var root = new GameObject(name);
            var visual = AddModel(root.transform, modelPath, height);
            if (collider)
            {
                AddBoundsCollider(root, visual, true);
            }
            return SavePrefab(root, Prefabs + "/" + name + ".prefab");
        }

        private static GameObject AddModel(Transform parent, string path, float targetHeight)
        {
            var asset = AssetDatabase.LoadAssetAtPath<GameObject>(path);
            if (asset == null)
            {
                throw new BuildFailedException("Could not load model: " + path);
            }
            var instance = PrefabUtility.InstantiatePrefab(asset) as GameObject;
            instance.name = Path.GetFileNameWithoutExtension(path);
            instance.transform.SetParent(parent, false);
            var bounds = CalculateBounds(instance);
            if (bounds.size.y > 0.01f)
            {
                instance.transform.localScale = Vector3.one * (targetHeight / bounds.size.y);
                bounds = CalculateBounds(instance);
                instance.transform.localPosition = new Vector3(0, -bounds.min.y, 0);
            }
            return instance;
        }

        private static RuntimeAnimatorController BuildController(string name, string modelPath, bool animal)
        {
            var path = Controllers + "/" + name + ".controller";
            var existing = AssetDatabase.LoadAssetAtPath<AnimatorController>(path);
            if (existing != null)
            {
                return existing;
            }
            var controller = AnimatorController.CreateAnimatorControllerAtPath(path);
            controller.AddParameter("Speed", AnimatorControllerParameterType.Float);
            controller.AddParameter("Working", AnimatorControllerParameterType.Bool);
            foreach (var trigger in new[] { "Attack", "Rescued", "Gathered", "Deposit" })
            {
                controller.AddParameter(trigger, AnimatorControllerParameterType.Trigger);
            }
            var clips = FindClips(modelPath);
            if (clips.Length == 0)
            {
                throw new BuildFailedException("No animation clips were found near " + modelPath);
            }
            AnimationClip Pick(params string[] tokens) =>
                clips.FirstOrDefault(clip => tokens.Any(token => clip.name.Contains(token, StringComparison.OrdinalIgnoreCase))) ?? clips[0];
            var idleClip = Pick("idle", "stand");
            var walkClip = Pick("walk", "run", "move");
            var workClip = Pick("work", "chop", "mine", "pickup", "interact");
            var attackClip = Pick("attack", "bite", "hit");
            var machine = controller.layers[0].stateMachine;
            var idle = machine.AddState("Idle"); idle.motion = idleClip;
            var walk = machine.AddState("Move"); walk.motion = walkClip;
            var work = machine.AddState("Work"); work.motion = workClip;
            var attack = machine.AddState("Attack"); attack.motion = attackClip;
            machine.defaultState = idle;
            AddCondition(idle.AddTransition(walk), AnimatorConditionMode.Greater, 0.08f, "Speed", false);
            AddCondition(walk.AddTransition(idle), AnimatorConditionMode.Less, 0.08f, "Speed", false);
            AddCondition(machine.AddAnyStateTransition(work), AnimatorConditionMode.If, 0, "Working", false);
            var workExit = work.AddTransition(idle); workExit.hasExitTime = true; workExit.exitTime = 0.9f; workExit.duration = 0.12f;
            var attackTransition = machine.AddAnyStateTransition(attack);
            AddCondition(attackTransition, AnimatorConditionMode.If, 0, "Attack", false);
            var attackExit = attack.AddTransition(idle); attackExit.hasExitTime = true; attackExit.exitTime = 0.92f; attackExit.duration = 0.1f;
            return controller;
        }

        private static void AddCondition(AnimatorStateTransition transition, AnimatorConditionMode mode, float threshold, string parameter, bool exit)
        {
            transition.hasExitTime = exit;
            transition.duration = 0.12f;
            transition.AddCondition(mode, threshold, parameter);
        }

        private static AnimationClip[] FindClips(string modelPath)
        {
            var folder = Path.GetDirectoryName(modelPath)?.Replace('\\', '/');
            return AssetDatabase.FindAssets("t:AnimationClip", new[] { folder })
                .Select(AssetDatabase.GUIDToAssetPath)
                .SelectMany(AssetDatabase.LoadAllAssetsAtPath)
                .OfType<AnimationClip>()
                .Where(clip => !clip.name.StartsWith("__preview__", StringComparison.OrdinalIgnoreCase))
                .Distinct()
                .ToArray();
        }

        private static HavenlineProductionConfig BuildConfig(PrefabSet prefabs)
        {
            var path = Settings + "/HavenlineProductionConfig.asset";
            var config = AssetDatabase.LoadAssetAtPath<HavenlineProductionConfig>(path);
            if (config == null)
            {
                config = ScriptableObject.CreateInstance<HavenlineProductionConfig>();
                AssetDatabase.CreateAsset(config, path);
            }
            SetObject(config, "playerPrefab", prefabs.Player);
            SetObject(config, "survivorPrefab", prefabs.Survivor);
            SetObject(config, "wolfPrefab", prefabs.Wolf);
            SetObject(config, "furnacePrefab", prefabs.Furnace);
            SetObject(config, "barricadePrefab", prefabs.Barricade);
            SetObject(config, "tentPrefab", prefabs.Tent);
            var serialized = new SerializedObject(config);
            var resources = serialized.FindProperty("resourcePrefabs");
            resources.arraySize = 3;
            resources.GetArrayElementAtIndex(0).objectReferenceValue = prefabs.Wood;
            resources.GetArrayElementAtIndex(1).objectReferenceValue = prefabs.Scrap;
            resources.GetArrayElementAtIndex(2).objectReferenceValue = prefabs.Fuel;
            serialized.ApplyModifiedPropertiesWithoutUndo();
            return config;
        }

        private static void BuildScene(PrefabSet prefabs, HavenlineProductionConfig config, InputActionReference input)
        {
            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
            RenderSettings.fog = true;
            RenderSettings.fogMode = FogMode.Linear;
            RenderSettings.fogColor = new Color(0.48f, 0.60f, 0.68f);
            RenderSettings.fogStartDistance = 18f;
            RenderSettings.fogEndDistance = 44f;
            RenderSettings.ambientMode = AmbientMode.Trilight;
            RenderSettings.ambientSkyColor = new Color(0.42f, 0.54f, 0.62f);
            RenderSettings.ambientEquatorColor = new Color(0.20f, 0.27f, 0.32f);
            RenderSettings.ambientGroundColor = new Color(0.08f, 0.11f, 0.14f);

            var world = new GameObject("FrozenOutpost");
            var ground = new GameObject("SculptedSnowPlateau");
            ground.transform.SetParent(world.transform);
            var groundFilter = ground.AddComponent<MeshFilter>();
            groundFilter.sharedMesh = EnsureGroundMesh();
            var groundRenderer = ground.AddComponent<MeshRenderer>();
            groundRenderer.sharedMaterial = EnsureMaterial("Snow", new Color(0.78f, 0.88f, 0.93f), false);
            var groundCollider = ground.AddComponent<MeshCollider>();
            groundCollider.sharedMesh = groundFilter.sharedMesh;

            var sun = new GameObject("ColdSun").AddComponent<Light>();
            sun.type = LightType.Directional;
            sun.transform.rotation = Quaternion.Euler(48f, -32f, 0f);
            sun.color = new Color(0.72f, 0.83f, 1f);
            sun.intensity = 1.25f;
            sun.shadows = LightShadows.Soft;

            var furnaceObject = Spawn(prefabs.Furnace, Vector3.zero, 0f, world.transform);
            var furnace = furnaceObject.GetComponent<HavenlineFurnace>();
            var warmth = furnaceObject.GetComponent<HavenlineWarmthZone>();
            var playerObject = Spawn(prefabs.Player, new Vector3(0, 0.08f, 4.25f), 180f, world.transform);
            var player = playerObject.GetComponent<HavenlinePlayerMotor>();
            var inventory = playerObject.GetComponent<HavenlineInventory>();
            player.ConfigureBoundary(Vector3.zero, config.PlayableRadius);
            SetObject(player, "moveAction", input);

            var helperObject = Spawn(prefabs.Survivor, new Vector3(6.8f, 0.08f, -1.8f), 220f, world.transform);
            var helper = helperObject.GetComponent<HavenlineSurvivorHelper>();
            helper.Configure(furnace, warmth);

            Spawn(prefabs.Tent, new Vector3(-4.8f, 0, -2.8f), 28f, world.transform);
            Spawn(prefabs.Tent, new Vector3(4.5f, 0, -3.7f), -32f, world.transform);
            Spawn(prefabs.Barricade, new Vector3(-1.8f, 0, -7.4f), 0f, world.transform);
            Spawn(prefabs.Barricade, new Vector3(2.1f, 0, -7.4f), 0f, world.transform);
            Spawn(prefabs.Barricade, new Vector3(-7.5f, 0, 2.5f), 90f, world.transform);
            Spawn(prefabs.Barricade, new Vector3(7.5f, 0, 1.7f), 90f, world.transform);

            var resourcePlacements = new[]
            {
                (prefabs.Wood, new Vector3(-7.1f,0,5.0f), 18f),
                (prefabs.Wood, new Vector3(-8.7f,0,2.2f), -14f),
                (prefabs.Wood, new Vector3(-5.9f,0,7.5f), 42f),
                (prefabs.Scrap, new Vector3(7.0f,0,5.6f), -25f),
                (prefabs.Scrap, new Vector3(8.6f,0,2.7f), 18f),
                (prefabs.Scrap, new Vector3(6.0f,0,8.0f), 35f),
                (prefabs.Fuel, new Vector3(-1.6f,0,-9.3f), 5f),
                (prefabs.Fuel, new Vector3(2.7f,0,-9.0f), -8f)
            };
            foreach (var placement in resourcePlacements)
                Spawn(placement.Item1, placement.Item2, placement.Item3, world.transform);

            for (var i = 0; i < 20; i++)
            {
                var angle = i * Mathf.PI * 2f / 20f;
                var radius = 14.3f + (i % 3) * 0.7f;
                var prefab = i % 3 == 0 ? prefabs.Rock : prefabs.Tree;
                Spawn(prefab, new Vector3(Mathf.Cos(angle) * radius, 0, Mathf.Sin(angle) * radius), -angle * Mathf.Rad2Deg + 90f, world.transform);
            }

            var wolfA = Spawn(prefabs.Wolf, new Vector3(-5.2f, 0.08f, -12.0f), 0f, world.transform).GetComponent<HavenlineWolf>();
            var wolfB = Spawn(prefabs.Wolf, new Vector3(5.4f, 0.08f, -12.5f), 0f, world.transform).GetComponent<HavenlineWolf>();
            wolfA.SetFallbackTarget(furnace.transform);
            wolfB.SetFallbackTarget(furnace.transform);

            var cameraObject = new GameObject("HAVENLINE_CloseIsometricCamera", typeof(Camera), typeof(AudioListener));
            cameraObject.tag = "MainCamera";
            var camera = cameraObject.GetComponent<Camera>();
            camera.orthographic = true;
            camera.orthographicSize = 8.8f;
            camera.nearClipPlane = 0.1f;
            camera.farClipPlane = 80f;
            camera.backgroundColor = new Color(0.39f, 0.52f, 0.61f);
            camera.clearFlags = CameraClearFlags.SolidColor;
            var cameraRig = cameraObject.AddComponent<HavenlineIsometricCamera>();
            cameraRig.SetTarget(playerObject.transform, true);
            player.SetCamera(cameraObject.transform);

            var hud = BuildHud(inventory, furnace, helper, out var joystick);
            SetObject(player, "virtualJoystick", joystick);
            hud.transform.SetParent(null);

            var navSurface = world.AddComponent<NavMeshSurface>();
            navSurface.collectObjects = CollectObjects.Children;
            navSurface.useGeometry = NavMeshCollectGeometry.PhysicsColliders;
            navSurface.BuildNavMesh();

            var snow = new GameObject("Snowfall").AddComponent<ParticleSystem>();
            snow.transform.position = new Vector3(0, 10, 0);
            var main = snow.main;
            main.startLifetime = 7f;
            main.startSpeed = 1.6f;
            main.startSize = 0.06f;
            main.maxParticles = 900;
            var emission = snow.emission;
            emission.rateOverTime = 75f;
            var shape = snow.shape;
            shape.shapeType = ParticleSystemShapeType.Box;
            shape.scale = new Vector3(28, 1, 28);

            EditorSceneManager.SaveScene(scene, ScenePath);
            EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(ScenePath, true) };
        }

        private static HavenlineHudController BuildHud(HavenlineInventory inventory, HavenlineFurnace furnace, HavenlineSurvivorHelper helper, out HavenlineVirtualJoystick joystick)
        {
            var canvasObject = new GameObject("HAVENLINE_MobileHUD", typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster), typeof(HavenlineHudController));
            var canvas = canvasObject.GetComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            canvas.sortingOrder = 10;
            var scaler = canvasObject.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1920, 1080);
            scaler.matchWidthOrHeight = 0.5f;
            var safe = CreateRect("SafeArea", canvasObject.transform, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            var hud = canvasObject.GetComponent<HavenlineHudController>();
            SetObject(hud, "safeAreaRoot", safe);

            var top = CreatePanel("TopBar", safe, new Color(0.015f, 0.045f, 0.07f, 0.80f), new Vector2(0.015f, 0.925f), new Vector2(0.985f, 0.985f));
            var resources = CreateText("Resources", top, 25, TextAnchor.MiddleLeft, new Vector2(0.015f, 0), new Vector2(0.66f, 1));
            var furnaceText = CreateText("Furnace", top, 23, TextAnchor.MiddleRight, new Vector2(0.52f, 0), new Vector2(0.985f, 1));
            var objectivePanel = CreatePanel("ObjectivePanel", safe, new Color(0.02f, 0.07f, 0.105f, 0.78f), new Vector2(0.018f, 0.80f), new Vector2(0.39f, 0.90f));
            var objective = CreateText("Objective", objectivePanel, 23, TextAnchor.MiddleLeft, new Vector2(0.04f, 0.05f), new Vector2(0.96f, 0.95f));
            var helperPanel = CreatePanel("HelperPanel", safe, new Color(0.08f, 0.15f, 0.16f, 0.72f), new Vector2(0.78f, 0.82f), new Vector2(0.982f, 0.90f));
            var helperText = CreateText("Helper", helperPanel, 21, TextAnchor.MiddleCenter, new Vector2(0, 0), new Vector2(1, 1));
            var progress = helperPanel.gameObject.AddComponent<Image>();
            progress.color = new Color(1f, 0.36f, 0.08f, 0.4f);
            progress.type = Image.Type.Filled;
            progress.fillMethod = Image.FillMethod.Horizontal;
            progress.fillAmount = 0f;
            progress.raycastTarget = false;
            progress.transform.SetAsFirstSibling();

            var joystickRoot = CreatePanel("Joystick", safe, new Color(0.04f, 0.12f, 0.18f, 0.42f), new Vector2(0.025f, 0.035f), new Vector2(0.15f, 0.255f));
            var handle = CreatePanel("Handle", joystickRoot, new Color(0.62f, 0.82f, 0.91f, 0.62f), new Vector2(0.29f, 0.29f), new Vector2(0.71f, 0.71f));
            joystick = joystickRoot.gameObject.AddComponent<HavenlineVirtualJoystick>();
            SetObject(joystick, "handle", handle);
            SetFloat(joystick, "movementRadius", 72f);

            var eventSystem = new GameObject("EventSystem", typeof(EventSystem), typeof(InputSystemUIInputModule));
            eventSystem.transform.SetParent(canvasObject.transform, false);

            SetObject(hud, "playerInventory", inventory);
            SetObject(hud, "furnace", furnace);
            SetObject(hud, "helper", helper);
            SetObject(hud, "resourceText", resources);
            SetObject(hud, "furnaceText", furnaceText);
            SetObject(hud, "helperText", helperText);
            SetObject(hud, "objectiveText", objective);
            SetObject(hud, "warmthProgress", progress);
            hud.Configure(inventory, furnace, helper);
            return hud;
        }

        private static RectTransform CreateRect(string name, Transform parent, Vector2 anchorMin, Vector2 anchorMax, Vector2 offsetMin, Vector2 offsetMax)
        {
            var rect = new GameObject(name, typeof(RectTransform)).GetComponent<RectTransform>();
            rect.SetParent(parent, false);
            rect.anchorMin = anchorMin;
            rect.anchorMax = anchorMax;
            rect.offsetMin = offsetMin;
            rect.offsetMax = offsetMax;
            return rect;
        }

        private static RectTransform CreatePanel(string name, Transform parent, Color color, Vector2 min, Vector2 max)
        {
            var rect = CreateRect(name, parent, min, max, Vector2.zero, Vector2.zero);
            var image = rect.gameObject.AddComponent<Image>();
            image.color = color;
            return rect;
        }

        private static Text CreateText(string name, Transform parent, int size, TextAnchor alignment, Vector2 min, Vector2 max)
        {
            var rect = CreateRect(name, parent, min, max, Vector2.zero, Vector2.zero);
            var text = rect.gameObject.AddComponent<Text>();
            text.font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
            text.fontSize = size;
            text.alignment = alignment;
            text.color = Color.white;
            text.horizontalOverflow = HorizontalWrapMode.Wrap;
            text.verticalOverflow = VerticalWrapMode.Truncate;
            return text;
        }

        private static GameObject Spawn(GameObject prefab, Vector3 position, float yaw, Transform parent)
        {
            var instance = PrefabUtility.InstantiatePrefab(prefab) as GameObject;
            instance.transform.SetParent(parent, true);
            instance.transform.position = position;
            instance.transform.rotation = Quaternion.Euler(0, yaw, 0);
            return instance;
        }

        private static GameObject SavePrefab(GameObject root, string path)
        {
            var prefab = PrefabUtility.SaveAsPrefabAsset(root, path);
            UnityEngine.Object.DestroyImmediate(root);
            return prefab;
        }

        private static void AddBoundsCollider(GameObject root, GameObject visual, bool solid, bool preserveTrigger = false)
        {
            var bounds = CalculateBounds(visual);
            var collider = root.AddComponent<BoxCollider>();
            collider.isTrigger = !solid;
            collider.center = root.transform.InverseTransformPoint(bounds.center);
            collider.size = bounds.size;
            if (preserveTrigger)
            {
                collider.enabled = false;
            }
        }

        private static Bounds CalculateBounds(GameObject root)
        {
            var renderers = root.GetComponentsInChildren<Renderer>(true);
            if (renderers.Length == 0) return new Bounds(root.transform.position, Vector3.one);
            var bounds = renderers[0].bounds;
            foreach (var renderer in renderers.Skip(1)) bounds.Encapsulate(renderer.bounds);
            return bounds;
        }

        private static Material EnsureMaterial(string name, Color color, bool transparent)
        {
            var path = Materials + "/" + name + ".mat";
            var material = AssetDatabase.LoadAssetAtPath<Material>(path);
            if (material == null)
            {
                material = new Material(Shader.Find("Universal Render Pipeline/Lit"));
                AssetDatabase.CreateAsset(material, path);
            }
            material.SetColor("_BaseColor", color);
            material.SetFloat("_Smoothness", name == "Snow" ? 0.42f : 0.2f);
            if (transparent)
            {
                material.SetFloat("_Surface", 1f);
                material.SetFloat("_ZWrite", 0f);
                material.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
                material.renderQueue = 3000;
            }
            EditorUtility.SetDirty(material);
            return material;
        }

        private static Mesh EnsureGroundMesh()
        {
            var path = Generated + "/Meshes/SnowPlateau.asset";
            var mesh = AssetDatabase.LoadAssetAtPath<Mesh>(path);
            if (mesh != null) return mesh;
            const int segments = 96;
            var vertices = new List<Vector3> { Vector3.zero };
            var uv = new List<Vector2> { new(0.5f, 0.5f) };
            for (var i = 0; i <= segments; i++)
            {
                var angle = i * Mathf.PI * 2f / segments;
                var radius = 16.2f + Mathf.Sin(angle * 5f) * 0.45f + Mathf.Cos(angle * 9f) * 0.25f;
                vertices.Add(new Vector3(Mathf.Cos(angle) * radius, Mathf.Sin(angle * 3f) * 0.08f, Mathf.Sin(angle) * radius));
                uv.Add(new Vector2(Mathf.Cos(angle) * 0.5f + 0.5f, Mathf.Sin(angle) * 0.5f + 0.5f));
            }
            var triangles = new List<int>();
            for (var i = 1; i <= segments; i++) triangles.AddRange(new[] { 0, i, i + 1 });
            mesh = new Mesh { name = "HAVENLINE_SculptedSnowPlateau" };
            mesh.SetVertices(vertices);
            mesh.SetUVs(0, uv);
            mesh.SetTriangles(triangles, 0);
            mesh.RecalculateNormals();
            mesh.RecalculateBounds();
            AssetDatabase.CreateAsset(mesh, path);
            return mesh;
        }

        private static Mesh EnsureRingMesh()
        {
            var path = Generated + "/Meshes/WarmthRing.asset";
            var mesh = AssetDatabase.LoadAssetAtPath<Mesh>(path);
            if (mesh != null) return mesh;
            const int segments = 96;
            var vertices = new List<Vector3>();
            var triangles = new List<int>();
            for (var i = 0; i <= segments; i++)
            {
                var angle = i * Mathf.PI * 2f / segments;
                var direction = new Vector3(Mathf.Cos(angle), 0, Mathf.Sin(angle));
                vertices.Add(direction * 0.46f);
                vertices.Add(direction * 0.5f);
            }
            for (var i = 0; i < segments; i++)
            {
                var index = i * 2;
                triangles.AddRange(new[] { index, index + 2, index + 1, index + 1, index + 2, index + 3 });
            }
            mesh = new Mesh { name = "HAVENLINE_WarmthRing" };
            mesh.SetVertices(vertices);
            mesh.SetTriangles(triangles, 0);
            mesh.RecalculateNormals();
            AssetDatabase.CreateAsset(mesh, path);
            return mesh;
        }

        private static void ConfigurePlayer()
        {
            PlayerSettings.productName = "HAVENLINE";
            PlayerSettings.companyName = "Kaleb";
            PlayerSettings.bundleVersion = "0.1.0-unity6-review";
            PlayerSettings.SetApplicationIdentifier(NamedBuildTarget.Android, "com.kaleb.havenline");
            PlayerSettings.SetScriptingBackend(NamedBuildTarget.Android, ScriptingImplementation.IL2CPP);
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64;
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel29;
            PlayerSettings.defaultInterfaceOrientation = UIOrientation.LandscapeLeft;
            PlayerSettings.allowedAutorotateToLandscapeLeft = true;
            PlayerSettings.allowedAutorotateToLandscapeRight = true;
            PlayerSettings.allowedAutorotateToPortrait = false;
            PlayerSettings.allowedAutorotateToPortraitUpsideDown = false;
            PlayerSettings.runInBackground = false;
            PlayerSettings.use32BitDisplayBuffer = true;
            PlayerSettings.MTRendering = true;
            PlayerSettings.SetGraphicsAPIs(BuildTarget.Android, new[] { GraphicsDeviceType.Vulkan, GraphicsDeviceType.OpenGLES3 });
            EditorUserBuildSettings.androidBuildSystem = AndroidBuildSystem.Gradle;
            EditorUserBuildSettings.buildAppBundle = false;
        }

        private static void CaptureReviewFrames()
        {
            var scene = EditorSceneManager.OpenScene(ScenePath, OpenSceneMode.Single);
            var camera = scene.GetRootGameObjects().SelectMany(root => root.GetComponentsInChildren<Camera>(true)).First();
            Directory.CreateDirectory("Builds/Review");
            Capture(camera, "Builds/Review/HAVENLINE-unity-frozen-outpost.png", 1920, 1080);
            camera.orthographicSize = 7.8f;
            Capture(camera, "Builds/Review/HAVENLINE-unity-close-camera.png", 1920, 1080);
            camera.orthographicSize = 8.8f;
        }

        private static void Capture(Camera camera, string path, int width, int height)
        {
            var texture = new RenderTexture(width, height, 24, RenderTextureFormat.ARGB32);
            var previous = camera.targetTexture;
            camera.targetTexture = texture;
            camera.Render();
            RenderTexture.active = texture;
            var image = new Texture2D(width, height, TextureFormat.RGB24, false);
            image.ReadPixels(new Rect(0, 0, width, height), 0, 0);
            image.Apply();
            File.WriteAllBytes(path, image.EncodeToPNG());
            camera.targetTexture = previous;
            RenderTexture.active = null;
            UnityEngine.Object.DestroyImmediate(image);
            texture.Release();
            UnityEngine.Object.DestroyImmediate(texture);
        }

        private static void SetObject(UnityEngine.Object target, string field, UnityEngine.Object value)
        {
            var serialized = new SerializedObject(target);
            serialized.FindProperty(field).objectReferenceValue = value;
            serialized.ApplyModifiedPropertiesWithoutUndo();
        }

        private static void SetInt(UnityEngine.Object target, string field, int value)
        {
            var serialized = new SerializedObject(target);
            serialized.FindProperty(field).intValue = value;
            serialized.ApplyModifiedPropertiesWithoutUndo();
        }

        private static void SetFloat(UnityEngine.Object target, string field, float value)
        {
            var serialized = new SerializedObject(target);
            serialized.FindProperty(field).floatValue = value;
            serialized.ApplyModifiedPropertiesWithoutUndo();
        }

        private static void SetEnum(UnityEngine.Object target, string field, int value)
        {
            var serialized = new SerializedObject(target);
            serialized.FindProperty(field).enumValueIndex = value;
            serialized.ApplyModifiedPropertiesWithoutUndo();
        }
    }
}
