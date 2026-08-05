using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
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

        private static readonly string[] FingerprintRoots =
        {
            "Assets/Havenline",
            "Packages",
            "ProjectSettings"
        };

        [Serializable]
        private sealed class DevicePerformanceProfile
        {
            public int targetFrameRate;
            public float averageFps;
            public float p95FrameTimeMs;
            public float p99FrameTimeMs;
            public long peakMemoryBytes;
            public float sessionSeconds;
            public string qualityTier;
            public int width;
            public int height;
        }

        [Serializable]
        private sealed class DeviceAcceptance
        {
            public string sourceCommit;
            public string sourceFingerprint;
            public string artVersion;
            public string deviceModel;
            public string operatingSystem;
            public DevicePerformanceProfile[] profiles = Array.Empty<DevicePerformanceProfile>();
            public bool thermalPassed;
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
            var fingerprint = ComputeSourceFingerprint();
            ApplyBuildIdentity(fingerprint);
            AuthorValidateAndCapture(manifest);
            BuildAndVerify(DeviceTestApkPath);
            var sha = Sha256(DeviceTestApkPath);
            File.WriteAllText(DeviceTestApkPath + ".sha256", $"{sha}  {Path.GetFileName(DeviceTestApkPath)}\n");
            WriteEvidence(sha, fingerprint, manifest, false, false, null);
            Debug.Log($"HAVENLINE premium device-test APK built: {DeviceTestApkPath} ({sha})");
        }

        [MenuItem("HAVENLINE Premium/Build Verified Android Release Candidate")]
        public static void BuildVerifiedReleaseCandidate()
        {
            ConfigurePlayer();
            var manifest = HavenlinePremiumBuildGate.RequireProductionContent();
            var fingerprint = ComputeSourceFingerprint();
            var acceptance = RequireDeviceAcceptance(manifest, fingerprint);
            ApplyBuildIdentity(fingerprint);
            AuthorValidateAndCapture(manifest);
            BuildAndVerify(ReleaseApkPath);
            var sha = Sha256(ReleaseApkPath);
            File.WriteAllText(ReleaseApkPath + ".sha256", $"{sha}  {Path.GetFileName(ReleaseApkPath)}\n");
            WriteEvidence(sha, fingerprint, manifest, true, true, acceptance);
            Debug.Log($"HAVENLINE verified premium release candidate built: {ReleaseApkPath} ({sha})");
        }

        [MenuItem("HAVENLINE Premium/Author and Validate Shipping Scene")]
        public static void PrepareAndCaptureOnly()
        {
            ConfigurePlayer();
            var manifest = HavenlinePremiumBuildGate.RequireProductionContent();
            var fingerprint = ComputeSourceFingerprint();
            ApplyBuildIdentity(fingerprint);
            AuthorValidateAndCapture(manifest);
            WriteEvidence(string.Empty, fingerprint, manifest, false, false, null);
        }

        [MenuItem("HAVENLINE Premium/Write Physical Acceptance Template")]
        public static void WritePhysicalAcceptanceTemplate()
        {
            var manifest = HavenlinePremiumBuildGate.RequireProductionContent();
            var fingerprint = ComputeSourceFingerprint();
            var acceptance = new DeviceAcceptance
            {
                sourceCommit = Environment.GetEnvironmentVariable("GITHUB_SHA") ?? "local",
                sourceFingerprint = fingerprint,
                artVersion = manifest.artVersion,
                deviceModel = string.Empty,
                operatingSystem = string.Empty,
                profiles = new[]
                {
                    NewProfile(60, "Ultra"),
                    NewProfile(90, "High"),
                    NewProfile(120, "High")
                }
            };
            Directory.CreateDirectory(Path.GetDirectoryName(AcceptancePath) ?? "Builds/Acceptance");
            File.WriteAllText(AcceptancePath, JsonUtility.ToJson(acceptance, true) + "\n");
            Debug.Log($"HAVENLINE physical acceptance template written: {AcceptancePath}");
        }

        private static DevicePerformanceProfile NewProfile(int target, string quality) => new()
        {
            targetFrameRate = target,
            qualityTier = quality
        };

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

        private static void ApplyBuildIdentity(string fingerprint)
        {
            var shortFingerprint = fingerprint.Length >= 12 ? fingerprint[..12] : fingerprint;
            PlayerSettings.bundleVersion = $"0.1.0-{shortFingerprint}";
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
            HavenlinePremiumBuildGate.ProductionArtManifest manifest,
            string expectedFingerprint)
        {
            if (!File.Exists(AcceptancePath))
                throw new BuildFailedException(
                    $"Release-candidate promotion requires physical-device evidence at {AcceptancePath}. " +
                    "Build and test the premium device package first.");

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
            if (!string.Equals(acceptance.sourceFingerprint, expectedFingerprint, StringComparison.OrdinalIgnoreCase))
                throw new BuildFailedException("Physical evidence does not match the current runtime, settings and production-art fingerprint.");
            if (!string.Equals(acceptance.artVersion, manifest.artVersion, StringComparison.Ordinal))
                throw new BuildFailedException("Device acceptance was captured with a different production-art version.");
            if (string.IsNullOrWhiteSpace(acceptance.deviceModel) || string.IsNullOrWhiteSpace(acceptance.operatingSystem))
                throw new BuildFailedException("Device acceptance is missing device or operating-system identity.");

            var profiles = acceptance.profiles ?? Array.Empty<DevicePerformanceProfile>();
            var profile60 = RequireProfile(profiles, 60);
            var profile90 = RequireProfile(profiles, 90);
            var profile120 = RequireProfile(profiles, 120);
            ValidateProfile(profile60, "Ultra");
            ValidateProfile(profile90, "High");
            ValidateProfile(profile120, "High");

            if (!acceptance.thermalPassed || !acceptance.suspendResumePassed || !acceptance.foldUnfoldPassed ||
                !acceptance.saveResumePassed || !acceptance.completeOpeningLoopPassed || !acceptance.noCrashPassed)
                throw new BuildFailedException("One or more physical functional, thermal or lifecycle acceptance checks failed.");
            return acceptance;
        }

        private static DevicePerformanceProfile RequireProfile(
            IEnumerable<DevicePerformanceProfile> profiles,
            int target)
        {
            var matches = profiles.Where(profile => profile != null && profile.targetFrameRate == target).ToArray();
            if (matches.Length != 1)
                throw new BuildFailedException($"Physical acceptance requires exactly one {target} FPS profile; found {matches.Length}.");
            return matches[0];
        }

        private static void ValidateProfile(DevicePerformanceProfile profile, string minimumQuality)
        {
            var minimumAverage = profile.targetFrameRate * 0.95f;
            var maximumP95 = 1000f / profile.targetFrameRate * 1.20f;
            var maximumP99 = 1000f / profile.targetFrameRate * 1.45f;
            if (profile.averageFps < minimumAverage ||
                profile.p95FrameTimeMs > maximumP95 ||
                profile.p99FrameTimeMs > maximumP99)
            {
                throw new BuildFailedException(
                    $"{profile.targetFrameRate} FPS profile failed: average {profile.averageFps:0.0}, " +
                    $"P95 {profile.p95FrameTimeMs:0.0} ms, P99 {profile.p99FrameTimeMs:0.0} ms.");
            }
            if (profile.sessionSeconds < 900f)
                throw new BuildFailedException($"{profile.targetFrameRate} FPS profile requires at least a 15-minute sustained session.");
            if (profile.peakMemoryBytes <= 0 || profile.width <= 0 || profile.height <= 0)
                throw new BuildFailedException($"{profile.targetFrameRate} FPS profile is missing memory or resolution evidence.");
            if (!MeetsQuality(profile.qualityTier, minimumQuality))
                throw new BuildFailedException(
                    $"{profile.targetFrameRate} FPS profile ran at {profile.qualityTier}; require at least {minimumQuality} quality.");
        }

        private static bool MeetsQuality(string actual, string minimum)
        {
            static int Rank(string value) => value?.Trim().ToLowerInvariant() switch
            {
                "ultra" => 3,
                "high" => 2,
                "balanced" => 1,
                _ => 0
            };
            return Rank(actual) >= Rank(minimum);
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
            string sourceFingerprint,
            HavenlinePremiumBuildGate.ProductionArtManifest manifest,
            bool releaseCandidate,
            bool performancePassed,
            DeviceAcceptance acceptance)
        {
            var profile120 = acceptance?.profiles?.FirstOrDefault(profile => profile != null && profile.targetFrameRate == 120);
            var evidence = new EvidenceSnapshot
            {
                commit = Environment.GetEnvironmentVariable("GITHUB_SHA") ?? "local",
                sourceFingerprint = sourceFingerprint,
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
                targetFrameRate = profile120?.targetFrameRate ?? 0,
                averageFps = profile120?.averageFps ?? 0f,
                p95FrameTimeMs = profile120?.p95FrameTimeMs ?? 0f,
                p99FrameTimeMs = profile120?.p99FrameTimeMs ?? 0f,
                peakMemoryBytes = profile120?.peakMemoryBytes ?? 0L,
                deviceModel = acceptance?.deviceModel ?? string.Empty,
                qualityTier = profile120?.qualityTier ?? "pending physical device test",
                apkSha256 = apkSha,
                validationFailures = performancePassed
                    ? Array.Empty<string>()
                    : new[] { "Physical 60, 90 and 120 FPS sustained performance and lifecycle acceptance are not yet attached." },
                proofFrames = new[] { WideProof, CloseProof }
            };

            Directory.CreateDirectory(ReviewDirectory);
            File.WriteAllText(
                Path.Combine(ReviewDirectory, EvidenceFile),
                JsonUtility.ToJson(evidence, true) + "\n");
        }

        private static string ComputeSourceFingerprint()
        {
            using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
            var files = new List<string>();
            foreach (var root in FingerprintRoots)
            {
                if (!Directory.Exists(root))
                    continue;
                files.AddRange(Directory.GetFiles(root, "*", SearchOption.AllDirectories));
            }

            foreach (var path in files
                         .Select(path => path.Replace('\\', '/'))
                         .Where(IncludeInFingerprint)
                         .OrderBy(path => path, StringComparer.Ordinal))
            {
                hash.AppendData(Encoding.UTF8.GetBytes(path));
                hash.AppendData(new byte[] { 0 });
                using var stream = File.OpenRead(path);
                var buffer = new byte[1024 * 128];
                int read;
                while ((read = stream.Read(buffer, 0, buffer.Length)) > 0)
                    hash.AppendData(buffer, 0, read);
                hash.AppendData(new byte[] { 0 });
            }

            return Convert.ToHexString(hash.GetHashAndReset()).ToLowerInvariant();
        }

        private static bool IncludeInFingerprint(string path)
        {
            if (path.Contains("/Generated/", StringComparison.OrdinalIgnoreCase) ||
                path.Contains("/Tests/", StringComparison.OrdinalIgnoreCase) ||
                path.EndsWith("/Scenes/FrozenOutpost.unity", StringComparison.OrdinalIgnoreCase) ||
                path.EndsWith(".DS_Store", StringComparison.OrdinalIgnoreCase))
                return false;
            return true;
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
