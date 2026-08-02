using System;
using System.IO;
using System.IO.Compression;
using System.Net;
using UnityEditor;
using UnityEditor.Build;
using UnityEngine;

namespace Havenline.Editor
{
    [InitializeOnLoad]
    public static class HavenlineLocalAutoBuild
    {
        private const string RequestFileName = "AUTO_BUILD_REQUESTED.txt";
        private const string StateFileName = "HavenlineLocalAutoBuild.state.txt";
        private const string ApkRelativePath = "Builds/Android/HAVENLINE-Unity6-review-candidate-arm64.apk";

        private static readonly (string Name, string Url)[] AssetArchives =
        {
            (
                "QuaterniusCharacters",
                "https://opengameart.org/sites/default/files/ultimate_animated_character_pack_by_quaternius.zip"),
            (
                "QuaterniusAnimals",
                "https://opengameart.org/sites/default/files/Animal%20Pack%20Vol.2%20by%20%40Quaternius.zip"),
            (
                "KenneySurvival",
                "https://opengameart.org/sites/default/files/kenney_survival-kit.zip")
        };

        static HavenlineLocalAutoBuild()
        {
            EditorApplication.delayCall += TryStart;
        }

        [MenuItem("HAVENLINE/Reset Local Auto-Build Checkpoint", priority = 100)]
        public static void ResetCheckpoint()
        {
            var statePath = GetStatePath();
            if (File.Exists(statePath))
            {
                File.Delete(statePath);
            }

            Debug.Log("HAVENLINE local auto-build checkpoint reset. The next script reload can run it once.");
        }

        private static void TryStart()
        {
            if (Application.isBatchMode)
            {
                return;
            }

            if (EditorApplication.isCompiling || EditorApplication.isUpdating)
            {
                EditorApplication.delayCall += TryStart;
                return;
            }

            var requestPath = Path.Combine(GetProjectRoot(), RequestFileName);
            var statePath = GetStatePath();
            if (!File.Exists(requestPath) || File.Exists(statePath))
            {
                return;
            }

            Directory.CreateDirectory(Path.GetDirectoryName(statePath) ?? GetProjectRoot());
            File.WriteAllText(
                statePath,
                $"STARTED {DateTimeOffset.Now:O}\nThe checkpoint is written before work begins to prevent a retry loop.\n");

            try
            {
                EditorUtility.DisplayProgressBar("HAVENLINE", "Preparing the one-time local Unity build...", 0.02f);
                EnsureAndroidSupport();
                EnsureProductionAssets();

                EditorUtility.DisplayProgressBar("HAVENLINE", "Building the frozen outpost and ARM64 review APK...", 0.82f);
                HavenlineProductionPipeline.BuildReviewCandidate();

                var apkPath = Path.Combine(GetProjectRoot(), ApkRelativePath.Replace('/', Path.DirectorySeparatorChar));
                if (!File.Exists(apkPath) || new FileInfo(apkPath).Length == 0)
                {
                    throw new BuildFailedException($"Unity returned without creating the expected APK: {apkPath}");
                }

                File.WriteAllText(
                    statePath,
                    $"COMPLETED {DateTimeOffset.Now:O}\nAPK={apkPath}\n");
                Debug.Log($"HAVENLINE automatic local build completed: {apkPath}");
                EditorUtility.RevealInFinder(apkPath);
            }
            catch (Exception exception)
            {
                File.WriteAllText(
                    statePath,
                    $"FAILED {DateTimeOffset.Now:O}\n{exception}\n\nNo automatic retry will occur.\n");
                Debug.LogException(exception);
            }
            finally
            {
                EditorUtility.ClearProgressBar();
            }
        }

        private static void EnsureAndroidSupport()
        {
            if (!BuildPipeline.IsBuildTargetSupported(BuildTargetGroup.Android, BuildTarget.Android))
            {
                throw new BuildFailedException(
                    "Android Build Support is not installed for this Unity editor. Add Android Build Support, Android SDK & NDK Tools, and OpenJDK in Unity Hub.");
            }

            if (EditorUserBuildSettings.activeBuildTarget == BuildTarget.Android)
            {
                return;
            }

            if (!EditorUserBuildSettings.SwitchActiveBuildTarget(BuildTargetGroup.Android, BuildTarget.Android))
            {
                throw new BuildFailedException("Unity could not switch the HAVENLINE project to Android.");
            }
        }

        private static void EnsureProductionAssets()
        {
            const string assetRelativeRoot = "Assets/Havenline/ThirdParty";
            var absoluteRoot = Path.Combine(GetProjectRoot(), assetRelativeRoot.Replace('/', Path.DirectorySeparatorChar));
            Directory.CreateDirectory(absoluteRoot);

            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);
            if (AssetDatabase.FindAssets("t:Model", new[] { assetRelativeRoot }).Length >= 12)
            {
                return;
            }

            var downloadRoot = Path.Combine(GetProjectRoot(), "Library", "HavenlineDownloads");
            Directory.CreateDirectory(downloadRoot);

            using var client = new WebClient();
            client.Headers[HttpRequestHeader.UserAgent] = "HAVENLINE-Unity-Local-Build/1.0";

            for (var index = 0; index < AssetArchives.Length; index++)
            {
                var archive = AssetArchives[index];
                var progress = 0.08f + index * 0.20f;
                EditorUtility.DisplayProgressBar("HAVENLINE", $"Downloading {archive.Name} production assets...", progress);

                var zipPath = Path.Combine(downloadRoot, archive.Name + ".zip");
                if (!File.Exists(zipPath) || new FileInfo(zipPath).Length == 0)
                {
                    client.DownloadFile(archive.Url, zipPath);
                }

                var destination = Path.Combine(absoluteRoot, archive.Name);
                Directory.CreateDirectory(destination);
                ExtractArchiveSafely(zipPath, destination);
            }

            File.WriteAllText(
                Path.Combine(absoluteRoot, "LOCAL-ASSET-SOURCES.txt"),
                "Downloaded automatically for the HAVENLINE local Unity review build.\n" +
                string.Join("\n", Array.ConvertAll(AssetArchives, item => $"{item.Name}: {item.Url}")) + "\n");

            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);
            var modelCount = AssetDatabase.FindAssets("t:Model", new[] { assetRelativeRoot }).Length;
            if (modelCount < 12)
            {
                throw new BuildFailedException(
                    $"The local asset bootstrap completed but Unity imported only {modelCount} models; at least 12 are required.");
            }

            Debug.Log($"HAVENLINE local production assets imported: {modelCount} models.");
        }

        private static void ExtractArchiveSafely(string archivePath, string destinationRoot)
        {
            var destinationFullPath = Path.GetFullPath(destinationRoot) + Path.DirectorySeparatorChar;
            using var archive = ZipFile.OpenRead(archivePath);
            foreach (var entry in archive.Entries)
            {
                var targetPath = Path.GetFullPath(Path.Combine(destinationRoot, entry.FullName));
                if (!targetPath.StartsWith(destinationFullPath, StringComparison.OrdinalIgnoreCase))
                {
                    throw new InvalidDataException($"Archive entry escapes its destination: {entry.FullName}");
                }

                if (string.IsNullOrEmpty(entry.Name))
                {
                    Directory.CreateDirectory(targetPath);
                    continue;
                }

                Directory.CreateDirectory(Path.GetDirectoryName(targetPath) ?? destinationRoot);
                entry.ExtractToFile(targetPath, overwrite: true);
            }
        }

        private static string GetProjectRoot() =>
            Path.GetFullPath(Path.Combine(Application.dataPath, ".."));

        private static string GetStatePath() =>
            Path.Combine(GetProjectRoot(), "Library", StateFileName);
    }
}
