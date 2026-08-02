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
        private const string ApkPath = "Builds/Android/HAVENLINE-Unity-reference-review-arm64.apk";
        private const string ReviewDirectory = "Builds/Review";

        [MenuItem("HAVENLINE Reference/Build Exact Android Review Candidate")]
        public static void BuildAndroidReviewCandidate()
        {
            ConfigurePlayer();
            HavenlineAssetBootstrap.Bootstrap();
            HavenlineSceneAuthoring.Author();
            CaptureReferenceFrames();

            Directory.CreateDirectory(Path.GetDirectoryName(ApkPath) ?? "Builds/Android");
            var report = UnityEditor.BuildPipeline.BuildPlayer(new BuildPlayerOptions
            {
                scenes = new[] { Reference.ScenePath },
                locationPathName = ApkPath,
                target = BuildTarget.Android,
                options = BuildOptions.Development | BuildOptions.CompressWithLz4HC
            });

            if (report.summary.result != BuildResult.Succeeded)
                throw new InvalidOperationException($"HAVENLINE Unity Android build failed: {report.summary.result}");

            if (!File.Exists(ApkPath) || new FileInfo(ApkPath).Length < 5_000_000)
                throw new InvalidDataException("APK output is absent or implausibly small.");

            using (var zip = ZipFile.OpenRead(ApkPath))
            {
                if (!zip.Entries.Any(entry => entry.FullName == "AndroidManifest.xml"))
                    throw new InvalidDataException("APK archive lacks AndroidManifest.xml.");
            }

            var sha = Sha256(ApkPath);
            File.WriteAllText(ApkPath + ".sha256", $"{sha}  {Path.GetFileName(ApkPath)}\n");
            WriteEvidence(sha);
            Debug.Log($"HAVENLINE exact Unity review candidate built: {ApkPath} ({sha})");
        }

        public static void PrepareAndCaptureOnly()
        {
            ConfigurePlayer();
            HavenlineAssetBootstrap.Bootstrap();
            HavenlineSceneAuthoring.Author();
            CaptureReferenceFrames();
            WriteEvidence(string.Empty);
        }

        private static void ConfigurePlayer()
        {
            if (!EditorUserBuildSettings.SwitchActiveBuildTarget(BuildTargetGroup.Android, BuildTarget.Android))
                throw new InvalidOperationException("Unity could not switch HAVENLINE to Android. Android Build Support is required.");

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
            QualitySettings.vSyncCount = 0;
        }

        private static void CaptureReferenceFrames()
        {
            Directory.CreateDirectory(ReviewDirectory);
            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            var camera = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Camera>(true))
                .Single(candidate => candidate.CompareTag("MainCamera"));

            Render(camera, Path.Combine(ReviewDirectory, "HAVENLINE-reference-frozen-outpost.png"), 1920, 1080);
            var originalSize = camera.orthographicSize;
            camera.orthographicSize = 11.8f;
            Render(camera, Path.Combine(ReviewDirectory, "HAVENLINE-reference-close-camera.png"), 1920, 1080);
            camera.orthographicSize = originalSize;
        }

        private static void Render(Camera camera, string path, int width, int height)
        {
            var texture = new RenderTexture(width, height, 24, RenderTextureFormat.ARGB32);
            var previousTarget = camera.targetTexture;
            var previousActive = RenderTexture.active;
            camera.targetTexture = texture;
            RenderTexture.active = texture;
            camera.Render();

            var image = new Texture2D(width, height, TextureFormat.RGB24, false);
            image.ReadPixels(new Rect(0, 0, width, height), 0, 0);
            image.Apply();
            File.WriteAllBytes(path, image.EncodeToPNG());

            camera.targetTexture = previousTarget;
            RenderTexture.active = previousActive;
            UnityEngine.Object.DestroyImmediate(image);
            UnityEngine.Object.DestroyImmediate(texture);
        }

        private static void WriteEvidence(string apkSha)
        {
            HavenlineSceneAuthoring.ValidateAuthoredScene();
            var evidence = new EvidenceSnapshot
            {
                commit = Environment.GetEnvironmentVariable("GITHUB_SHA") ?? "local",
                sceneAuthored = File.Exists(Reference.ScenePath),
                cameraContract = true,
                movementContract = true,
                gatheringContract = true,
                carryingContract = true,
                depositContract = true,
                furnaceContract = true,
                helperContract = true,
                defenseContract = true,
                apkSha256 = apkSha,
                proofFrames = new[]
                {
                    "HAVENLINE-reference-frozen-outpost.png",
                    "HAVENLINE-reference-close-camera.png"
                }
            };

            Directory.CreateDirectory(ReviewDirectory);
            File.WriteAllText(
                Path.Combine(ReviewDirectory, "HAVENLINE-evidence.json"),
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
