using System;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Security.Cryptography;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.SceneManagement;

namespace Havenline.Editor
{
    public static class HavenlineBuildPipeline
    {
        private const string DeviceTestApkPath = "Builds/Android/HAVENLINE-premium-device-test-arm64.apk";
        private const string ReleaseApkPath = "Builds/Android/HAVENLINE-premium-release-candidate-arm64.apk";
        private const string ReviewDirectory = "Builds/Review";
        private const string AcceptancePath = "Builds/Acceptance/HAVENLINE-device-acceptance.json";
        private const string WideProof = "HAVENLINE-premium-frozen-outpost.png";
        private const string CloseProof = "HAVENLINE-premium-close-camera.png";
        private const string EvidenceFile = "HAVENLINE-premium-evidence.json";

        [Serializable]
        private sealed class DeviceAcceptance
        {
            public string sourceCommit;
            public string artVersion;
            public string deviceModel;
            public int targetFrameRate;
            public float averageFps;
            public float p95FrameTimeMs;
            public float p99FrameTimeMs;
            public long peakMemoryBytes;
            public float sessionSeconds;
            public bool suspendResumePassed;
            public bool foldUnfoldPassed;
            public bool saveResumePassed;
            public bool completeOpeningLoopPassed;
            public bool noCrashPassed;
        }

        [MenuItem("HAVENLINE Premium/Build Premium Android Device Test")]
        public static void BuildAndroidReviewCandidate()
        {
            ConfigurePlayer();
            var manifest = HavenlinePremiumBuildGate.RequireProductionContent();
            AuthorValidateAndCapture(manifest);
            BuildAndVerify(DeviceTestApkPath);
            var sha = Sha256(DeviceTestApkPath);
            File.WriteAllText(DeviceTestApkPath + ".sha256", $"{sha}  {Path.GetFileName(DeviceTestApkPath)}\n");
            WriteEvidence(sha, manifest, false, false, null);
            Debug.Log($"HAVENLINE premium device-test APK built: {DeviceTestApkPath} ({sha})");
        }

        [MenuItem("HAVENLINE Premium/Build Verified Android Release Candidate")]
        public static void BuildVerifiedReleaseCandidate()
        {
            ConfigurePlayer();
            var manifest = HavenlinePremiumBuildGate.RequireProductionContent();
            var acceptance = RequireDeviceAcceptance(manifest);
            AuthorValidateAndCapture(manifest);
            BuildAndVerify(ReleaseApkPath);
            var sha = Sha256(ReleaseApkPath);
            File.WriteAllText(ReleaseApkPath + ".sha256", $"{sha}  {Path.GetFileName(ReleaseApkPath)}\n");
            WriteEvidence(sha, manifest, true, true, acceptance);
            Debug.Log($"HAVENLINE verified premium release candidate built: {ReleaseApkPath} ({sha})");
        }

        [MenuItem("HAVENLINE Premium/Author and Validate Shipping Scene")]
        public static void PrepareAndCaptureOnly()
        {
            ConfigurePlayer();
            var manifest = HavenlinePremiumBuildGate.RequireProductionContent();
            AuthorValidateAndCapture(manifest);
            WriteEvidence(string.Empty, manifest, false, false, null);
        }

        private static void AuthorValidateAndCapture(HavenlinePremiumBuildGate.ProductionArtManifest manifest)
        {
            HavenlinePremiumSceneAuthoring.Author(manifest);
            HavenlinePremiumSceneGate.RequirePremiumScene(manifest);
            ValidateFunctionalScene();
            CapturePremiumFrames();
        }

        private static void ValidateFunctionalScene()
        {
            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            Require<HavenlinePlayerController>(scene, 1, "controlled survivor");
            Require<HavenlineAutomaticActionController>(scene, 1, "automatic action controller");
            Require<HavenlineFurnace>(scene, 1, "furnace");
            Require<HavenlineHelper>(scene, 1, "rescuable helper");
            RequireAtLeast<HavenlineResourceNode>(scene, 10, "resource nodes");
            RequireAtLeast<HavenlineConstructionSite>(scene, 2, "construction sites");
            RequireAtLeast<HavenlineBarricade>(scene, 2, "finished defense states");
            RequireAtLeast<HavenlineEnemy>(scene, 1, "enemy template");
            Require<HavenlineGameDirector>(scene, 1, "opening-loop director");
            Require<HavenlinePerformance>(scene, 1, "adaptive performance controller");
        }

        private static void ConfigurePlayer()
        {
            if (!EditorUserBuildSettings.SwitchActiveBuildTarget(BuildTargetGroup.Android, BuildTarget.Android))
                throw new BuildFailedException("Unity could not switch HAVENLINE to Android. Android Build Support is required.");

            PlayerSettings.productName = Reference.ProductName;
            PlayerSettings.companyName = "HAVENLINE";
            PlayerSettings.SetApplicationIdentifier(NamedBuildTarget.Android, Reference.PackageId);
            PlayerSettings.SetScriptingBackend(NamedBuildTarget.Android, ScriptingImplementation.IL2CPP);
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64;
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel26;
            PlayerSettings.Android.targetSdkVersion = AndroidSdkVersions.AndroidApiLevelAuto;
            PlayerSettings.SetGraphicsAPIs(BuildTarget.Android, new[]
            {
                GraphicsDeviceType.Vulkan,
                GraphicsDeviceType.OpenGLES3
            });
            PlayerSettings.defaultInterfaceOrientation = UIOrientation.AutoRotation;
            PlayerSettings.allowedAutorotateToPortrait = false;
            PlayerSettings.allowedAutorotateToPortraitUpsideDown = false;
            PlayerSettings.allowedAutorotateToLandscapeLeft = true;
            PlayerSettings.allowedAutorotateToLandscapeRight = true;
            PlayerSettings.colorSpace = ColorSpace.Linear;
            PlayerSettings.stripEngineCode = true;
            PlayerSettings.SetManagedStrippingLevel(NamedBuildTarget.Android, ManagedStrippingLevel.Medium);
            EditorUserBuildSettings.development = false;
            EditorUserBuildSettings.allowDebugging = false;
            EditorUserBuildSettings.connectProfiler = false;
            QualitySettings.vSyncCount = 0;
        }

        private static void BuildAndVerify(string path)
        {
            Directory.CreateDirectory(Path.GetDirectoryName(path) ?? "Builds/Android");
            var report = UnityEditor.BuildPipeline.BuildPlayer(new BuildPlayerOptions
            {
                scenes = new[] { Reference.ScenePath },
                locationPathName = path,
                target = BuildTarget.Android,
                options = BuildOptions.CompressWithLz4HC
            });
            if (report.summary.result != BuildResult.Succeeded)
                throw new BuildFailedException($"HAVENLINE premium Android build failed: {report.summary.result}");
            if (!File.Exists(path) || new FileInfo(path).Length < 25_000_000)
                throw new InvalidDataException("Premium APK output is absent or implausibly small.");
            using var zip = ZipFile.OpenRead(path);
            if (!zip.Entries.Any(entry => entry.FullName == "AndroidManifest.xml"))
                throw new InvalidDataException("APK archive lacks AndroidManifest.xml.");
        }

        private static DeviceAcceptance RequireDeviceAcceptance(
            HavenlinePremiumBuildGate.ProductionArtManifest manifest)
        {
            if (!File.Exists(AcceptancePath))
                throw new BuildFailedException(
                    $"Release-candidate promotion requires physical-device evidence at {AcceptancePath}. " +
                    "Build the premium device-test APK and complete the sustained device pass first.");

            DeviceAcceptance acceptance;
            try
            {
                acceptance = JsonUtility.FromJson<DeviceAcceptance>(File.ReadAllText(AcceptancePath));
            }
            catch (Exception exception)
            {
                throw new BuildFailedException($"Device acceptance JSON is invalid: {exception.Message}");
            }
            if (acceptance == null)
                throw new BuildFailedException("Device acceptance JSON is empty.");

            var expectedCommit = Environment.GetEnvironmentVariable("GITHUB_SHA") ?? acceptance.sourceCommit;
            if (!string.Equals(acceptance.sourceCommit, expectedCommit, StringComparison.OrdinalIgnoreCase))
                throw new BuildFailedException("Device acceptance was captured from a different source commit.");
            if (!string.Equals(acceptance.artVersion, manifest.artVersion, StringComparison.Ordinal))
                throw new BuildFailedException("Device acceptance was captured with a different production-art version.");
            if (acceptance.targetFrameRate is not (60 or 90 or 120))
                throw new BuildFailedException("Device acceptance target must be 60, 90 or 120 FPS.");

            var minimumAverage = acceptance.targetFrameRate * 0.90f;
            var maximumP95 = 1000f / acceptance.targetFrameRate * 1.30f;
            if (acceptance.averageFps < minimumAverage || acceptance.p95FrameTimeMs > maximumP95)
                throw new BuildFailedException(
                    $"Sustained performance failed: {acceptance.averageFps:0.0} FPS, " +
                    $"P95 {acceptance.p95FrameTimeMs:0.0} ms at target {acceptance.targetFrameRate}.");
            if (acceptance.sessionSeconds < 900f)
                throw new BuildFailedException("Device acceptance requires at least a 15-minute sustained session.");
            if (acceptance.peakMemoryBytes <= 0 || string.IsNullOrWhiteSpace(acceptance.deviceModel))
                throw new BuildFailedException("Device acceptance is missing memory or device identity evidence.");
            if (!acceptance.suspendResumePassed || !acceptance.foldUnfoldPassed ||
                !acceptance.saveResumePassed || !acceptance.completeOpeningLoopPassed || !acceptance.noCrashPassed)
                throw new BuildFailedException("One or more physical functional acceptance checks failed.");
            return acceptance;
        }

        private static void CapturePremiumFrames()
        {
            Directory.CreateDirectory(ReviewDirectory);
            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            var camera = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Camera>(true))
                .Single(candidate => candidate.CompareTag("MainCamera"));

            Render(camera, Path.Combine(ReviewDirectory, WideProof), 1920, 1080);
            var originalSize = camera.orthographicSize;
            camera.orthographicSize = Mathf.Min(originalSize, 9.45f);
            Render(camera, Path.Combine(ReviewDirectory, CloseProof), 1920, 1080);
            camera.orthographicSize = originalSize;
        }

        private static void Render(Camera camera, string path, int width, int height)
        {
            var texture = new RenderTexture(width, height, 24, RenderTextureFormat.ARGB32)
            {
                antiAliasing = 4,
                useMipMap = false,
                autoGenerateMips = false
            };
            var previousTarget = camera.targetTexture;
            var previousActive = RenderTexture.active;
            camera.targetTexture = texture;
            RenderTexture.active = texture;
            camera.Render();

            var image = new Texture2D(width, height, TextureFormat.RGB24, false, false);
            image.ReadPixels(new Rect(0, 0, width, height), 0, 0);
            image.Apply();
            File.WriteAllBytes(path, image.EncodeToPNG());

            camera.targetTexture = previousTarget;
            RenderTexture.active = previousActive;
            UnityEngine.Object.DestroyImmediate(image);
            UnityEngine.Object.DestroyImmediate(texture);
        }

        private static void WriteEvidence(
            string apkSha,
            HavenlinePremiumBuildGate.ProductionArtManifest manifest,
            bool releaseCandidate,
            bool performancePassed,
            DeviceAcceptance acceptance)
        {
            var evidence = new EvidenceSnapshot
            {
                commit = Environment.GetEnvironmentVariable("GITHUB_SHA") ?? "local",
                artVersion = manifest.artVersion,
                approvedBy = manifest.approvedBy,
                sceneAuthored = File.Exists(Reference.ScenePath),
                cameraContract = true,
                movementContract = true,
                automaticActionContract = true,
                gatheringContract = true,
                carryingContract = true,
                depositContract = true,
                furnaceContract = true,
                helperContract = true,
                constructionContract = true,
                defenseContract = true,
                saveResumeContract = acceptance?.saveResumePassed ?? false,
                premiumArtContract = true,
                animationContract = true,
                visualQualityContract = true,
                uiContract = true,
                audioContract = true,
                performanceContract = performancePassed,
                releaseCandidate = releaseCandidate,
                targetFrameRate = acceptance?.targetFrameRate ?? 0,
                averageFps = acceptance?.averageFps ?? 0f,
                p95FrameTimeMs = acceptance?.p95FrameTimeMs ?? 0f,
                p99FrameTimeMs = acceptance?.p99FrameTimeMs ?? 0f,
                peakMemoryBytes = acceptance?.peakMemoryBytes ?? 0L,
                deviceModel = acceptance?.deviceModel ?? string.Empty,
                qualityTier = performancePassed ? "physically validated" : "pending physical device test",
                apkSha256 = apkSha,
                validationFailures = performancePassed
                    ? Array.Empty<string>()
                    : new[] { "Physical 60/90/120 Hz sustained performance and device acceptance are not yet attached." },
                proofFrames = new[] { WideProof, CloseProof }
            };

            Directory.CreateDirectory(ReviewDirectory);
            File.WriteAllText(
                Path.Combine(ReviewDirectory, EvidenceFile),
                JsonUtility.ToJson(evidence, true) + "\n");
        }

        private static string Sha256(string path)
        {
            using var algorithm = SHA256.Create();
            using var stream = File.OpenRead(path);
            return BitConverter.ToString(algorithm.ComputeHash(stream))
                .Replace("-", string.Empty)
                .ToLowerInvariant();
        }

        private static void Require<T>(Scene scene, int expected, string label) where T : Component
        {
            var count = scene.GetRootGameObjects().SelectMany(root => root.GetComponentsInChildren<T>(true)).Count();
            if (count != expected)
                throw new BuildFailedException($"Shipping scene requires exactly {expected} {label}; found {count}.");
        }

        private static void RequireAtLeast<T>(Scene scene, int minimum, string label) where T : Component
        {
            var count = scene.GetRootGameObjects().SelectMany(root => root.GetComponentsInChildren<T>(true)).Count();
            if (count < minimum)
                throw new BuildFailedException($"Shipping scene requires at least {minimum} {label}; found {count}.");
        }
    }
}
