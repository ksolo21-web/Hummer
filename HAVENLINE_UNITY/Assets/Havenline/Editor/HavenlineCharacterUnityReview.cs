using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Havenline.Editor
{
    /// <summary>
    /// Imports the exact machine-validated production FBX files from a pinned character run,
    /// places them in the authored shipping outpost, and captures real Unity URP evidence.
    /// This deliberately leaves human approval pending; pictures must be compared against the
    /// checksum-pinned approved turnaround sheets before any character can be promoted.
    /// </summary>
    public static class HavenlineCharacterUnityReview
    {
        public const string ReviewDirectory = "Builds/CharacterUnityReview";
        private const string SourceRunPath = "Assets/Havenline/Generated/CharacterReviewSource/source-run.json";
        private const float ReviewCharacterHeight = 1.78f;

        private static readonly string[] Characters =
        {
            "Character1",
            "Character2",
            "Character3",
            "Character4"
        };

        private static readonly string[] Views =
        {
            "front",
            "three-quarter",
            "side",
            "back"
        };

        [Serializable]
        private sealed class SourceRun
        {
            public int schemaVersion;
            public string characterProductionRunId;
            public string characterProductionCommit;
            public bool humanVisualApprovalRequired;
        }

        [Serializable]
        private sealed class CharacterEvidence
        {
            public string character;
            public string modelAssetPath;
            public string modelAssetSha256;
            public long modelAssetBytes;
            public int rendererCount;
            public int skinnedRendererCount;
            public int materialSlotCount;
            public int nonNullMaterialCount;
            public int animatorCount;
            public int humanoidAnimatorCount;
            public float importedHeight;
            public float normalizedHeight;
            public string[] renderFiles = Array.Empty<string>();
            public string[] renderSha256 = Array.Empty<string>();
            public bool machineEvidenceComplete;
            public string humanVisualReviewStatus;
            public bool approved;
        }

        [Serializable]
        private sealed class ReviewReport
        {
            public int schemaVersion;
            public string generatedUtc;
            public string unityVersion;
            public string sourceCommit;
            public string characterProductionRunId;
            public string characterProductionCommit;
            public string shippingScene;
            public string renderPipeline;
            public string[] gameplayRenderFiles = Array.Empty<string>();
            public string[] gameplayRenderSha256 = Array.Empty<string>();
            public CharacterEvidence[] characters = Array.Empty<CharacterEvidence>();
            public bool allMachineEvidenceComplete;
            public bool humanVisualApprovalRequired;
            public string humanVisualReviewStatus;
            public bool approved;
        }

        private sealed class ReviewCharacter
        {
            public string Id;
            public string AssetPath;
            public GameObject Instance;
            public float ImportedHeight;
            public Bounds Bounds;
            public CharacterEvidence Evidence;
        }

        private sealed class CameraState
        {
            public Vector3 Position;
            public Quaternion Rotation;
            public bool Orthographic;
            public float OrthographicSize;
            public float FieldOfView;
            public float NearClip;
            public float FarClip;
            public float Aspect;
            public RenderTexture Target;
        }

        private sealed class CanvasState
        {
            public Canvas Canvas;
            public bool Enabled;
            public RenderMode RenderMode;
            public Camera WorldCamera;
            public float PlaneDistance;
        }

        [MenuItem("HAVENLINE Premium/Capture Production Characters In Unity")]
        public static void CaptureFromMenu() => CaptureFromCi();

        public static void CaptureFromCi()
        {
            Directory.CreateDirectory(ReviewDirectory);
            var source = ReadSourceRun();
            VerifySourcePackages();

            HavenlineCiBuildEntryPoints.PrepareGeneratedProductionContent();
            var manifest = HavenlinePremiumBuildGate.RequireProductionContent();
            HavenlinePremiumSceneAuthoring.Author(manifest);
            HavenlinePremiumSceneGate.RequirePremiumScene(manifest);

            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            var camera = FindMainCamera(scene);
            var player = FindTransform(scene, "Player");
            if (player == null)
                throw new BuildFailedException("Unity character review could not find Player in the authored shipping scene.");

            DisableAuthoredCharacterRenderers(scene);
            var reviewRoot = new GameObject("UNITY_CHARACTER_VISUAL_REVIEW");
            SceneManager.MoveGameObjectToScene(reviewRoot, scene);

            var offsets = new[]
            {
                new Vector3(-1.45f, 0f, -0.50f),
                new Vector3(-0.45f, 0f, 0.45f),
                new Vector3(0.55f, 0f, -0.15f),
                new Vector3(1.50f, 0f, 0.65f)
            };

            var reviewCharacters = new List<ReviewCharacter>();
            for (var index = 0; index < Characters.Length; index++)
            {
                var character = InstantiateProductionCharacter(
                    Characters[index],
                    reviewRoot.transform,
                    player.position + offsets[index],
                    player.rotation);
                reviewCharacters.Add(character);
            }

            AssetDatabase.SaveAssets();
            var canvasStates = CaptureCanvasStates(scene);
            var cameraState = CaptureCameraState(camera);
            var gameplayFiles = new List<string>();
            var gameplayHashes = new List<string>();

            try
            {
                ConfigureCanvasesForCamera(canvasStates, camera, true);
                var withUi = Path.Combine(ReviewDirectory, "HAVENLINE-characters-in-game-with-ui.png");
                Capture(camera, withUi, 1920, 1080);
                gameplayFiles.Add(Path.GetFileName(withUi));
                gameplayHashes.Add(Sha256(withUi));

                ConfigureCanvasesForCamera(canvasStates, camera, false);
                var clean = Path.Combine(ReviewDirectory, "HAVENLINE-characters-in-game-clean.png");
                Capture(camera, clean, 1920, 1080);
                gameplayFiles.Add(Path.GetFileName(clean));
                gameplayHashes.Add(Sha256(clean));

                foreach (var character in reviewCharacters)
                {
                    foreach (var candidate in reviewCharacters)
                        candidate.Instance.SetActive(candidate == character);

                    var files = new List<string>();
                    var hashes = new List<string>();
                    foreach (var view in Views)
                    {
                        ConfigureCloseReviewCamera(camera, character, view, 1f);
                        var path = Path.Combine(
                            ReviewDirectory,
                            $"HAVENLINE-{character.Id}-unity-{view}.png");
                        Capture(camera, path, 1200, 1200);
                        files.Add(Path.GetFileName(path));
                        hashes.Add(Sha256(path));
                    }

                    character.Evidence.renderFiles = files.ToArray();
                    character.Evidence.renderSha256 = hashes.ToArray();
                    character.Evidence.machineEvidenceComplete =
                        files.Count == Views.Length &&
                        files.All(file => File.Exists(Path.Combine(ReviewDirectory, file))) &&
                        character.Evidence.rendererCount > 0 &&
                        character.Evidence.skinnedRendererCount > 0 &&
                        character.Evidence.nonNullMaterialCount > 0 &&
                        character.Evidence.animatorCount > 0;
                }
            }
            finally
            {
                foreach (var character in reviewCharacters)
                    character.Instance.SetActive(true);
                RestoreCamera(camera, cameraState);
                RestoreCanvases(canvasStates);
            }

            var report = new ReviewReport
            {
                schemaVersion = 1,
                generatedUtc = DateTime.UtcNow.ToString("O"),
                unityVersion = Application.unityVersion,
                sourceCommit = Environment.GetEnvironmentVariable("GITHUB_SHA") ?? "local",
                characterProductionRunId = source.characterProductionRunId,
                characterProductionCommit = source.characterProductionCommit,
                shippingScene = Reference.ScenePath,
                renderPipeline = UnityEngine.Rendering.GraphicsSettings.currentRenderPipeline == null
                    ? "Built-in"
                    : UnityEngine.Rendering.GraphicsSettings.currentRenderPipeline.GetType().FullName,
                gameplayRenderFiles = gameplayFiles.ToArray(),
                gameplayRenderSha256 = gameplayHashes.ToArray(),
                characters = reviewCharacters.Select(character => character.Evidence).ToArray(),
                allMachineEvidenceComplete = reviewCharacters.All(character => character.Evidence.machineEvidenceComplete),
                humanVisualApprovalRequired = true,
                humanVisualReviewStatus = "pending",
                approved = false
            };

            var reportPath = Path.Combine(ReviewDirectory, "unity-character-review-report.json");
            File.WriteAllText(reportPath, JsonUtility.ToJson(report, true) + "\n");
            if (!report.allMachineEvidenceComplete)
                throw new BuildFailedException($"Unity character review evidence is incomplete. See {reportPath}");

            Debug.Log(
                $"HAVENLINE Unity character review rendered from exact character run " +
                $"{source.characterProductionRunId}. Human side-by-side approval remains pending: {ReviewDirectory}");
        }

        private static SourceRun ReadSourceRun()
        {
            if (!File.Exists(SourceRunPath))
                throw new BuildFailedException($"Pinned Unity character-review source is missing: {SourceRunPath}");
            var source = JsonUtility.FromJson<SourceRun>(File.ReadAllText(SourceRunPath));
            if (source == null || source.schemaVersion != 1 ||
                string.IsNullOrWhiteSpace(source.characterProductionRunId))
                throw new BuildFailedException($"Pinned Unity character-review source is invalid: {SourceRunPath}");
            if (!source.humanVisualApprovalRequired)
                throw new BuildFailedException("Character-review request attempted to bypass human visual approval.");
            return source;
        }

        private static void VerifySourcePackages()
        {
            foreach (var character in Characters)
            {
                var path = ModelAssetPath(character);
                if (!File.Exists(path))
                    throw new BuildFailedException($"Exact production FBX is missing for {character}: {path}");
                if (new FileInfo(path).Length < 10_000)
                    throw new BuildFailedException($"Production FBX is implausibly small for {character}: {path}");
                AssetDatabase.ImportAsset(path, ImportAssetOptions.ForceSynchronousImport | ImportAssetOptions.ForceUpdate);
            }
        }

        private static ReviewCharacter InstantiateProductionCharacter(
            string character,
            Transform parent,
            Vector3 desiredGroundPosition,
            Quaternion desiredRotation)
        {
            var path = ModelAssetPath(character);
            var model = AssetDatabase.LoadAssetAtPath<GameObject>(path);
            if (model == null)
                throw new BuildFailedException($"Unity could not import production model for {character}: {path}");

            var instanceObject = PrefabUtility.InstantiatePrefab(model, parent) as GameObject;
            if (instanceObject == null)
                throw new BuildFailedException($"Unity could not instantiate production model for {character}: {path}");
            instanceObject.name = $"{character}_UNITY_PRODUCTION_REVIEW";
            instanceObject.transform.localPosition = Vector3.zero;
            instanceObject.transform.localRotation = Quaternion.identity;
            instanceObject.transform.localScale = Vector3.one;

            var importedBounds = CalculateBounds(instanceObject);
            if (importedBounds.size.y <= 0.01f)
                throw new BuildFailedException($"Imported model has no meaningful height for {character}: {path}");
            var importedHeight = importedBounds.size.y;
            var scale = ReviewCharacterHeight / importedHeight;
            instanceObject.transform.localScale = Vector3.one * scale;
            instanceObject.transform.rotation = desiredRotation;

            var normalizedBounds = CalculateBounds(instanceObject);
            instanceObject.transform.position += new Vector3(
                desiredGroundPosition.x - normalizedBounds.center.x,
                desiredGroundPosition.y - normalizedBounds.min.y,
                desiredGroundPosition.z - normalizedBounds.center.z);
            normalizedBounds = CalculateBounds(instanceObject);

            var renderers = instanceObject.GetComponentsInChildren<Renderer>(true);
            var skinned = instanceObject.GetComponentsInChildren<SkinnedMeshRenderer>(true);
            var animators = instanceObject.GetComponentsInChildren<Animator>(true);
            var materialSlots = renderers.Sum(renderer => renderer.sharedMaterials.Length);
            var nonNullMaterials = renderers.Sum(renderer => renderer.sharedMaterials.Count(material => material != null));
            if (renderers.Length == 0 || skinned.Length == 0)
                throw new BuildFailedException($"{character} did not import as a rendered skinned Unity character.");
            if (nonNullMaterials == 0)
                throw new BuildFailedException($"{character} imported without usable Unity materials.");
            if (animators.Length == 0)
                throw new BuildFailedException($"{character} imported without an Animator.");

            var evidence = new CharacterEvidence
            {
                character = character,
                modelAssetPath = path,
                modelAssetSha256 = Sha256(path),
                modelAssetBytes = new FileInfo(path).Length,
                rendererCount = renderers.Length,
                skinnedRendererCount = skinned.Length,
                materialSlotCount = materialSlots,
                nonNullMaterialCount = nonNullMaterials,
                animatorCount = animators.Length,
                humanoidAnimatorCount = animators.Count(animator => animator.avatar != null && animator.avatar.isHuman),
                importedHeight = importedHeight,
                normalizedHeight = normalizedBounds.size.y,
                humanVisualReviewStatus = "pending",
                approved = false
            };

            return new ReviewCharacter
            {
                Id = character,
                AssetPath = path,
                Instance = instanceObject,
                ImportedHeight = importedHeight,
                Bounds = normalizedBounds,
                Evidence = evidence
            };
        }

        private static string ModelAssetPath(string character) =>
            $"Assets/Havenline/Art/Characters/Production/{character}/{character}_production.fbx";

        private static Camera FindMainCamera(Scene scene)
        {
            var camera = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Camera>(true))
                .FirstOrDefault(candidate => candidate.CompareTag("MainCamera"));
            if (camera == null)
                throw new BuildFailedException("Unity character review could not find the shipping MainCamera.");
            return camera;
        }

        private static Transform FindTransform(Scene scene, string name) =>
            scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
                .FirstOrDefault(candidate => string.Equals(candidate.name, name, StringComparison.Ordinal));

        private static void DisableAuthoredCharacterRenderers(Scene scene)
        {
            var transforms = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
                .Where(transform =>
                    string.Equals(transform.name, "Player", StringComparison.Ordinal) ||
                    transform.name.Contains("Helper", StringComparison.OrdinalIgnoreCase))
                .ToArray();
            foreach (var transform in transforms)
            {
                foreach (var renderer in transform.GetComponentsInChildren<Renderer>(true))
                    renderer.enabled = false;
            }
        }

        private static Bounds CalculateBounds(GameObject root)
        {
            var renderers = root.GetComponentsInChildren<Renderer>(true)
                .Where(renderer => renderer.enabled && renderer.gameObject.activeInHierarchy)
                .ToArray();
            if (renderers.Length == 0)
                return new Bounds(root.transform.position, Vector3.zero);
            var bounds = renderers[0].bounds;
            for (var index = 1; index < renderers.Length; index++)
                bounds.Encapsulate(renderers[index].bounds);
            return bounds;
        }

        private static CameraState CaptureCameraState(Camera camera) => new()
        {
            Position = camera.transform.position,
            Rotation = camera.transform.rotation,
            Orthographic = camera.orthographic,
            OrthographicSize = camera.orthographicSize,
            FieldOfView = camera.fieldOfView,
            NearClip = camera.nearClipPlane,
            FarClip = camera.farClipPlane,
            Aspect = camera.aspect,
            Target = camera.targetTexture
        };

        private static void RestoreCamera(Camera camera, CameraState state)
        {
            camera.transform.SetPositionAndRotation(state.Position, state.Rotation);
            camera.orthographic = state.Orthographic;
            camera.orthographicSize = state.OrthographicSize;
            camera.fieldOfView = state.FieldOfView;
            camera.nearClipPlane = state.NearClip;
            camera.farClipPlane = state.FarClip;
            camera.aspect = state.Aspect;
            camera.targetTexture = state.Target;
        }

        private static CanvasState[] CaptureCanvasStates(Scene scene) =>
            scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Canvas>(true))
                .Select(canvas => new CanvasState
                {
                    Canvas = canvas,
                    Enabled = canvas.enabled,
                    RenderMode = canvas.renderMode,
                    WorldCamera = canvas.worldCamera,
                    PlaneDistance = canvas.planeDistance
                })
                .ToArray();

        private static void ConfigureCanvasesForCamera(
            IEnumerable<CanvasState> states,
            Camera camera,
            bool visible)
        {
            foreach (var state in states)
            {
                if (state.Canvas == null)
                    continue;
                state.Canvas.enabled = visible && state.Enabled;
                if (!state.Canvas.enabled)
                    continue;
                state.Canvas.renderMode = RenderMode.ScreenSpaceCamera;
                state.Canvas.worldCamera = camera;
                state.Canvas.planeDistance = Mathf.Max(camera.nearClipPlane + 0.15f, 0.25f);
            }
            Canvas.ForceUpdateCanvases();
        }

        private static void RestoreCanvases(IEnumerable<CanvasState> states)
        {
            foreach (var state in states)
            {
                if (state.Canvas == null)
                    continue;
                state.Canvas.enabled = state.Enabled;
                state.Canvas.renderMode = state.RenderMode;
                state.Canvas.worldCamera = state.WorldCamera;
                state.Canvas.planeDistance = state.PlaneDistance;
            }
        }

        private static void ConfigureCloseReviewCamera(
            Camera camera,
            ReviewCharacter character,
            string view,
            float aspect)
        {
            var bounds = CalculateBounds(character.Instance);
            character.Bounds = bounds;
            var forward = character.Instance.transform.forward.normalized;
            var right = character.Instance.transform.right.normalized;
            Vector3 fromSubject;
            switch (view)
            {
                case "front":
                    fromSubject = -forward;
                    break;
                case "three-quarter":
                    fromSubject = (-forward + right * 0.72f).normalized;
                    break;
                case "side":
                    fromSubject = right;
                    break;
                case "back":
                    fromSubject = forward;
                    break;
                default:
                    throw new ArgumentOutOfRangeException(nameof(view), view, "Unsupported Unity review view.");
            }

            var height = Mathf.Max(bounds.size.y, 0.1f);
            var target = bounds.center + Vector3.up * height * 0.025f;
            var distance = Mathf.Max(5.2f, height * 3.8f);
            var elevatedDirection = (fromSubject + Vector3.up * 0.16f).normalized;
            camera.transform.position = target + elevatedDirection * distance;
            camera.transform.LookAt(target, Vector3.up);
            camera.orthographic = true;
            camera.aspect = aspect;
            camera.orthographicSize = Mathf.Max(height * 0.64f, bounds.size.x / Mathf.Max(aspect, 0.1f) * 0.64f);
            camera.nearClipPlane = 0.03f;
            camera.farClipPlane = 250f;
        }

        private static void Capture(Camera camera, string path, int width, int height)
        {
            Directory.CreateDirectory(Path.GetDirectoryName(path) ?? ReviewDirectory);
            var previousTarget = camera.targetTexture;
            var previousActive = RenderTexture.active;
            var previousAspect = camera.aspect;
            var texture = new RenderTexture(width, height, 24, RenderTextureFormat.ARGB32)
            {
                antiAliasing = 4,
                useMipMap = false,
                autoGenerateMips = false
            };
            texture.Create();
            try
            {
                camera.aspect = (float)width / height;
                camera.targetTexture = texture;
                RenderTexture.active = texture;
                Canvas.ForceUpdateCanvases();
                camera.Render();
                var image = new Texture2D(width, height, TextureFormat.RGB24, false, false);
                try
                {
                    image.ReadPixels(new Rect(0, 0, width, height), 0, 0);
                    image.Apply(false, false);
                    var png = image.EncodeToPNG();
                    if (png == null || png.Length < 25_000)
                        throw new BuildFailedException($"Unity produced an empty or implausibly small character render: {path}");
                    File.WriteAllBytes(path, png);
                }
                finally
                {
                    UnityEngine.Object.DestroyImmediate(image);
                }
            }
            finally
            {
                camera.targetTexture = previousTarget;
                camera.aspect = previousAspect;
                RenderTexture.active = previousActive;
                texture.Release();
                UnityEngine.Object.DestroyImmediate(texture);
            }
        }

        private static string Sha256(string path)
        {
            using var algorithm = SHA256.Create();
            using var stream = File.OpenRead(path);
            return BitConverter.ToString(algorithm.ComputeHash(stream))
                .Replace("-", string.Empty)
                .ToLowerInvariant();
        }
    }
}
