using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography;
using UnityEditor;
using UnityEngine;

namespace Havenline.Editor
{
    public static class HavenlineAssetBootstrap
    {
        private const string Destination = "Assets/Havenline/Art/Reference";
        private const string Cache = "Library/HavenlineReferenceDownloads";
        private sealed class Required
        {
            public readonly string Label;
            public readonly string FileName;
            public readonly string Sha256;
            public readonly string DestinationName;
            public Required(string label, string fileName, string sha256, string destinationName)
            { Label = label; FileName = fileName; Sha256 = sha256; DestinationName = destinationName; }
        }

        private static readonly Required[] RequiredFiles =
        {
            new("player", "Superhero_Male_FullBody.gltf", "", "Player/Superhero_Male_FullBody.gltf"),
            new("helper", "Superhero_Female_FullBody.gltf", "", "Helper/Superhero_Female_FullBody.gltf"),
            new("animation library 1", "UAL1.glb", "5e7f0efca238924037fd9659e56ef1c4aebde269d8b1f1800b330887907c198c", "Animation/UAL1.glb"),
            new("animation library 2", "UAL2.glb", "9a0ffda4931f934f13fb584002c51673723b03f9655a581167e7e5dae744f086", "Animation/UAL2.glb"),
            new("axe", "Axe.fbx", "41537fbef651a0931b42b1786658ea1ef116dbe4a2315a38d12644251e4c52f4", "Props/Axe.fbx"),
            new("backpack", "Backpack.fbx", "1e0f2d1d7d3063daf78abe727da0e4486717e9c3d3fe5a4a660d2cade829b70d", "Props/Backpack.fbx"),
            new("campfire", "Bonfire.fbx", "cf17677ab969bf5aa84faa1743e8ae974add5bdc9da4f97fbc2223a616de69fd", "Props/Bonfire.fbx"),
            new("wood", "WoodLog.fbx", "a758ac8c0f236a82c8af8a1db1d235d254325a9b3b0c065d8c1fbe8c5b37814d", "Props/WoodLog.fbx"),
            new("tent", "Tent.fbx", "621e0dcedb0bc4cffa9999c7e7b2b2ed83778bad28dccedd0606a1aa6a9b9f4d", "Props/Tent.fbx"),
            new("pine A", "Pine_2.fbx", "1ec8ee0339965d4249aa30ccba497356c10bcabd73d3e92163e32c3110f8025a", "Nature/Pine_2.fbx"),
            new("pine B", "Pine_3.fbx", "434578608ba1f2ee8ab35eda27c941e5f4b133bbe83633bc83657f6afba7896d", "Nature/Pine_3.fbx"),
            new("rock A", "Rock_Medium_2.fbx", "178576f884fdcc3a5c3cd824dbdceb54c8b96c791d06a69045d6553bf65cc526", "Nature/Rock_Medium_2.fbx"),
            new("rock B", "Rock_Medium_3.fbx", "f904be29181f09fb783be55b3ce412e4afb92919c02e291db570641ef0c3e986", "Nature/Rock_Medium_3.fbx")
        };

        [MenuItem("HAVENLINE Reference/Bootstrap Exact Reference Art")]
        public static void Bootstrap()
        {
            Directory.CreateDirectory(Cache); Directory.CreateDirectory(Destination);
            var sources = new[]
            {
                Download("characters.zip", "https://github.com/kirbycope/godot-3d-player-controller-v2/archive/a928cfa67684352b75a65c510d8751d1f3f2489c.zip"),
                Download("survival.zip", "https://opengameart.org/sites/default/files/survival_pack_-_sept_2020.zip"),
                Download("nature.zip", "https://opengameart.org/sites/default/files/stylized_nature_megakitstandard.zip"),
                Download("wolf.whl", "https://files.pythonhosted.org/packages/16/a0/3a0a2b8ee12d27e64a898a6a5f08820029d12afa36b794e663cc53537c32/animasim-0.2.1-py3-none-any.whl")
            };
            var extracted = sources.Select(Extract).ToArray();
            foreach (var required in RequiredFiles)
            {
                var candidate = extracted.SelectMany(root => Directory.EnumerateFiles(root, required.FileName, SearchOption.AllDirectories)).FirstOrDefault();
                if (candidate == null) throw new InvalidOperationException($"Missing exact HAVENLINE reference asset: {required.Label} ({required.FileName}).");
                if (!string.IsNullOrEmpty(required.Sha256)) Verify(candidate, required.Sha256, required.Label);
                CopyWithDependencies(candidate, Path.Combine(Destination, required.DestinationName));
            }

            var fireplace = Download("Fireplace.glb", "https://raw.githubusercontent.com/ToxSam/cc0-models-Polygonal-Mind/main/projects/christmas/Fireplace.glb");
            Verify(fireplace, "df5a90e160769ee4f5f8fb39e828f4b84378a97e9a126a457184318f51685a31", "furnace");
            CopyFile(fireplace, Path.Combine(Destination, "Props/Fireplace.glb"));

            var wolf = extracted.SelectMany(root => Directory.EnumerateFiles(root, "*.glb", SearchOption.AllDirectories))
                .FirstOrDefault(path => Sha256(path) == "aa06297d0e66568711885178d1d35e2ca1e392dceb05f988df0497de0274a705");
            if (wolf == null) throw new InvalidOperationException("The pinned AnimaSim package did not contain the checksum-locked HAVENLINE wolf.");
            CopyFile(wolf, Path.Combine(Destination, "Animals/Wolf.glb"));

            File.WriteAllText(Path.Combine(Destination, "REFERENCE_ASSET_PROVENANCE.txt"),
                "HAVENLINE reference art bootstrap\n" +
                "Quaternius characters commit a928cfa67684352b75a65c510d8751d1f3f2489c — CC0 1.0\n" +
                "Quaternius Survival Pack Sept 2020 — CC0 1.0\n" +
                "Quaternius Stylized Nature MegaKit — CC0 1.0\n" +
                "Quaternius wolf via animasim 0.2.1 — CC0 1.0\n" +
                "Polygonal Mind Fireplace — CC0 1.0\n");
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);
            ValidateImportedAssets();
        }

        public static void ValidateImportedAssets()
        {
            foreach (var required in RequiredFiles.Where(r => !string.IsNullOrEmpty(r.Sha256)))
            {
                var path = Path.Combine(Destination, required.DestinationName);
                if (!File.Exists(path)) throw new FileNotFoundException($"Required reference art is absent: {path}");
                Verify(path, required.Sha256, required.Label);
            }
            Verify(Path.Combine(Destination,"Props/Fireplace.glb"), "df5a90e160769ee4f5f8fb39e828f4b84378a97e9a126a457184318f51685a31", "furnace");
            Verify(Path.Combine(Destination,"Animals/Wolf.glb"), "aa06297d0e66568711885178d1d35e2ca1e392dceb05f988df0497de0274a705", "wolf");
        }

        private static string Download(string name, string url)
        {
            var path = Path.Combine(Cache, name);
            if (File.Exists(path) && new FileInfo(path).Length > 0) return path;
            using var client = new HttpClient { Timeout = TimeSpan.FromMinutes(12) };
            client.DefaultRequestHeaders.UserAgent.ParseAdd("HAVENLINE-Unity-Reference-Rebuild/1.0");
            var bytes = client.GetByteArrayAsync(url).GetAwaiter().GetResult();
            File.WriteAllBytes(path, bytes); return path;
        }

        private static string Extract(string archive)
        {
            var root = Path.Combine(Cache, Path.GetFileNameWithoutExtension(archive));
            if (Directory.Exists(root) && Directory.EnumerateFileSystemEntries(root).Any()) return root;
            Directory.CreateDirectory(root);
            using var zip = ZipFile.OpenRead(archive);
            var safeRoot = Path.GetFullPath(root) + Path.DirectorySeparatorChar;
            foreach (var entry in zip.Entries)
            {
                var destination = Path.GetFullPath(Path.Combine(root, entry.FullName));
                if (!destination.StartsWith(safeRoot, StringComparison.OrdinalIgnoreCase)) throw new InvalidDataException("Unsafe archive entry.");
                if (string.IsNullOrEmpty(entry.Name)) { Directory.CreateDirectory(destination); continue; }
                Directory.CreateDirectory(Path.GetDirectoryName(destination)!); entry.ExtractToFile(destination, true);
            }
            return root;
        }

        private static void CopyWithDependencies(string source, string destination)
        {
            CopyFile(source, destination);
            var extension = Path.GetExtension(source).ToLowerInvariant();
            if (extension is not ".gltf") return;
            var sourceDir = Path.GetDirectoryName(source)!; var destinationDir = Path.GetDirectoryName(destination)!;
            foreach (var file in Directory.EnumerateFiles(sourceDir))
            {
                var ext = Path.GetExtension(file).ToLowerInvariant();
                if (ext is ".bin" or ".png" or ".jpg" or ".jpeg") CopyFile(file, Path.Combine(destinationDir, Path.GetFileName(file)));
            }
        }
        private static void CopyFile(string source, string destination)
        { Directory.CreateDirectory(Path.GetDirectoryName(destination)!); File.Copy(source, destination, true); }
        private static void Verify(string path, string expected, string label)
        { var actual=Sha256(path); if (!actual.Equals(expected,StringComparison.OrdinalIgnoreCase)) throw new InvalidDataException($"{label} SHA-256 mismatch. Expected {expected}, got {actual}."); }
        private static string Sha256(string path)
        { using var algorithm=SHA256.Create(); using var stream=File.OpenRead(path); return BitConverter.ToString(algorithm.ComputeHash(stream)).Replace("-", string.Empty).ToLowerInvariant(); }
    }
}
