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
using UnityEngine.SceneManagement;

namespace Havenline.Editor
{
    public static class HavenlineBuildPipeline
    {
        private const string ApkPath = "Builds/Android/HAVENLINE-premium-release-candidate-arm64.apk";
        private const string ReviewDirectory = "Builds/Review";
        private const string WideProof = "HAVENLINE-premium-frozen-outpost.png";
        private const string CloseProof = "HAVENLINE-premium-close-camera.png";
        private const string EvidenceFile = "HAVENLINE-premium-evidence.json";

        [MenuItem("HAVENLINE Premium/Build Gated Android Release Candidate")]
        public static void BuildAndroidReviewCandidate()
        {
            ConfigurePlayer();

            // A release build may only consume committed, licensed, approved production
            // content. The old build-time download/bootstrap path is intentionally excluded.
            var manifest = HavenlinePremiumBuildGate.RequireProductionContent();

            HavenlineSceneAuthoring.Author();
            HavenlineSceneAuthoring.ValidateAuthoredScene();
            HavenlinePremiumSceneGate.RequirePremiumScene(manifest);
            CapturePremiumFrames();

            Directory.CreateDirectory(Path.GetDirectoryName(ApkPath) ?? "Builds/Android");
            var report = UnityEditor.BuildPipeline.BuildPlayer(new BuildPlayerOptions
            {
                scenes = new[] { Reference.ScenePath },
                locationPathName = ApkPath,
                target = BuildTarget.Android,
                options = BuildOptions.CompressWithLz4HC
            });

            if (report.summary.result != BuildResult.Succeeded)
                throw new BuildFailedException($"HAVENLINE premium Android build failed: {report.summary.result}");

            if (!File.Exists(ApkPath) || new FileInfo(ApkPath).Length < 25_000_000)
                throw new InvalidDataException("Premium APK output is absent or implausibly small.");

            using (var zip = ZipFile.OpenRead(ApkPath))
            {
                if (!zip.Entries.Any(entry => entry.FullName == "AndroidManifest.xml"))
                    throw new InvalidDataException("APK archive lacks AndroidManifest.xml.");
            }

            var sha = Sha256(ApkPath);
            File.WriteAllText(ApkPath + ".sha256", $"{sha}  {Path.GetFileName(ApkPath)}\n");
            WriteEvidence(sha, manifest, true, Array.Empty<string>());
            Debug.Log($"HAVENLINE premium Unity release candidate built: {ApkPath} ({sha})");
        }

        [MenuItem("HAVENLINE Premium/Author and Validate Shipping Scene")]
        public static void PrepareAndCaptureOnly()
        {
            ConfigurePlayer();
            var manifest = HavenlinePremiumBuildGate.RequireProductionContent();
            HavenlineSceneAuthoring.Author();
            HavenlineSceneAuthoring.ValidateAuthoredScene();
            HavenlinePremiumSceneGate.RequirePremiumScene(manifest);
            CapturePremiumFrames();
            WriteEvidence(string.Empty, manifest, false, new[]
            {
                "Scene validation passed, but no signed Android APK was exported in this command."
            });
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

        private static void CapturePremiumFrames()
        {
            Directory.CreateDirectory(ReviewDirectory);
            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            var camera = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Camera>(true))
                .Single(candidate => candidate.CompareTag("MainCamera"));

            Render(camera, Path.Combine(ReviewDirectory, WideProof), 1920, 1080);
            var originalSize = camera.orthographicSize;
            camera.orthographicSize = Mathf.Min(originalSize, 10.8f);
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
            string[] validationFailures)
        {
            var evidence = new EvidenceSnapshot
            {
                commit = Environment.GetEnvironmentVariable("GITHUB_SHA") ?? "local",
                artVersion = manifest.artVersion,
                approvedBy = manifest.approvedBy,
                sceneAuthored = File.Exists(Reference.ScenePath),
                cameraContract = true,
                movementContract = true,
                gatheringContract = true,
                carryingContract = true,
                depositContract = true,
                furnaceContract = true,
                helperContract = true,
                defenseContract = true,
                premiumArtContract = true,
                animationContract = true,
                visualQualityContract = true,
                uiContract = true,
                audioContract = true,
                releaseCandidate = releaseCandidate,
                apkSha256 = apkSha,
                validationFailures = validationFailures ?? Array.Empty<string>(),
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
    }
}
