using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text;
using UnityEditor;
using UnityEngine;

namespace Havenline.Editor
{
    internal static class HavenlineStudioGeometry
    {
        internal sealed class ObjBuilder
        {
            private readonly StringBuilder text = new();
            private int nextVertex = 1;

            public ObjBuilder(string materialLibrary)
            {
                text.AppendLine("# HAVENLINE deterministic production geometry");
                text.Append("mtllib ").AppendLine(materialLibrary);
                text.AppendLine("s 1");
            }

            public void Begin(string name, string material)
            {
                text.Append("o ").AppendLine(Safe(name));
                text.Append("g ").AppendLine(Safe(name));
                text.Append("usemtl ").AppendLine(Safe(material));
            }

            public void AddBox(Vector3 center, Vector3 size, Quaternion rotation)
            {
                var half = size * 0.5f;
                var corners = new[]
                {
                    new Vector3(-half.x,-half.y,-half.z), new Vector3(half.x,-half.y,-half.z),
                    new Vector3(half.x,half.y,-half.z), new Vector3(-half.x,half.y,-half.z),
                    new Vector3(-half.x,-half.y,half.z), new Vector3(half.x,-half.y,half.z),
                    new Vector3(half.x,half.y,half.z), new Vector3(-half.x,half.y,half.z)
                };
                for (var i = 0; i < corners.Length; i++)
                    corners[i] = center + rotation * corners[i];
                AddQuad(corners[0], corners[1], corners[2], corners[3]);
                AddQuad(corners[5], corners[4], corners[7], corners[6]);
                AddQuad(corners[4], corners[0], corners[3], corners[7]);
                AddQuad(corners[1], corners[5], corners[6], corners[2]);
                AddQuad(corners[3], corners[2], corners[6], corners[7]);
                AddQuad(corners[4], corners[5], corners[1], corners[0]);
            }

            public void AddCylinder(Vector3 center, float radius, float height, int sides, Quaternion rotation)
            {
                sides = Mathf.Clamp(sides, 5, 32);
                var bottom = new Vector3[sides];
                var top = new Vector3[sides];
                for (var i = 0; i < sides; i++)
                {
                    var angle = i * Mathf.PI * 2f / sides;
                    var radial = new Vector3(Mathf.Cos(angle) * radius, -height * 0.5f, Mathf.Sin(angle) * radius);
                    bottom[i] = center + rotation * radial;
                    radial.y = height * 0.5f;
                    top[i] = center + rotation * radial;
                }
                var bottomCenter = center + rotation * new Vector3(0f, -height * 0.5f, 0f);
                var topCenter = center + rotation * new Vector3(0f, height * 0.5f, 0f);
                for (var i = 0; i < sides; i++)
                {
                    var next = (i + 1) % sides;
                    AddQuad(bottom[i], bottom[next], top[next], top[i]);
                    AddTriangle(bottomCenter, bottom[next], bottom[i]);
                    AddTriangle(topCenter, top[i], top[next]);
                }
            }

            public void AddCone(Vector3 center, float bottomRadius, float topRadius, float height, int sides, Quaternion rotation)
            {
                sides = Mathf.Clamp(sides, 5, 32);
                var bottom = new Vector3[sides];
                var top = new Vector3[sides];
                for (var i = 0; i < sides; i++)
                {
                    var angle = i * Mathf.PI * 2f / sides;
                    bottom[i] = center + rotation * new Vector3(
                        Mathf.Cos(angle) * bottomRadius, -height * 0.5f, Mathf.Sin(angle) * bottomRadius);
                    top[i] = center + rotation * new Vector3(
                        Mathf.Cos(angle) * topRadius, height * 0.5f, Mathf.Sin(angle) * topRadius);
                }
                var bottomCenter = center + rotation * new Vector3(0f, -height * 0.5f, 0f);
                var topCenter = center + rotation * new Vector3(0f, height * 0.5f, 0f);
                for (var i = 0; i < sides; i++)
                {
                    var next = (i + 1) % sides;
                    AddQuad(bottom[i], bottom[next], top[next], top[i]);
                    AddTriangle(bottomCenter, bottom[next], bottom[i]);
                    if (topRadius > 0.001f)
                        AddTriangle(topCenter, top[i], top[next]);
                }
            }

            public void AddSphere(Vector3 center, Vector3 radius, int rings, int segments, Quaternion rotation)
            {
                rings = Mathf.Clamp(rings, 3, 16);
                segments = Mathf.Clamp(segments, 6, 24);
                var grid = new Vector3[rings + 1, segments];
                for (var ring = 0; ring <= rings; ring++)
                {
                    var latitude = -Mathf.PI * 0.5f + ring * Mathf.PI / rings;
                    var y = Mathf.Sin(latitude);
                    var horizontal = Mathf.Cos(latitude);
                    for (var segment = 0; segment < segments; segment++)
                    {
                        var longitude = segment * Mathf.PI * 2f / segments;
                        var point = new Vector3(
                            Mathf.Cos(longitude) * horizontal * radius.x,
                            y * radius.y,
                            Mathf.Sin(longitude) * horizontal * radius.z);
                        grid[ring, segment] = center + rotation * point;
                    }
                }
                for (var ring = 0; ring < rings; ring++)
                {
                    for (var segment = 0; segment < segments; segment++)
                    {
                        var next = (segment + 1) % segments;
                        AddQuad(grid[ring, segment], grid[ring, next], grid[ring + 1, next], grid[ring + 1, segment]);
                    }
                }
            }

            public void AddWedge(Vector3 center, Vector3 size, Quaternion rotation)
            {
                var half = size * 0.5f;
                var p0 = center + rotation * new Vector3(-half.x, -half.y, -half.z);
                var p1 = center + rotation * new Vector3(half.x, -half.y, -half.z);
                var p2 = center + rotation * new Vector3(half.x, -half.y, half.z);
                var p3 = center + rotation * new Vector3(-half.x, -half.y, half.z);
                var p4 = center + rotation * new Vector3(-half.x, half.y, half.z);
                var p5 = center + rotation * new Vector3(half.x, half.y, half.z);
                AddQuad(p0, p1, p2, p3);
                AddQuad(p3, p2, p5, p4);
                AddQuad(p0, p3, p4, p0);
                AddQuad(p1, p5, p2, p1);
                AddQuad(p0, p4, p5, p1);
            }

            public override string ToString() => text.ToString();

            private void AddQuad(Vector3 a, Vector3 b, Vector3 c, Vector3 d)
            {
                WriteVertex(a); WriteVertex(b); WriteVertex(c); WriteVertex(d);
                text.Append("f ").Append(nextVertex).Append(' ').Append(nextVertex + 1).Append(' ')
                    .Append(nextVertex + 2).Append(' ').Append(nextVertex + 3).AppendLine();
                nextVertex += 4;
            }

            private void AddTriangle(Vector3 a, Vector3 b, Vector3 c)
            {
                WriteVertex(a); WriteVertex(b); WriteVertex(c);
                text.Append("f ").Append(nextVertex).Append(' ').Append(nextVertex + 1).Append(' ')
                    .Append(nextVertex + 2).AppendLine();
                nextVertex += 3;
            }

            private void WriteVertex(Vector3 value)
            {
                text.Append("v ")
                    .Append(F(value.x)).Append(' ')
                    .Append(F(value.y)).Append(' ')
                    .Append(F(value.z)).AppendLine();
            }

            private static string F(float value) => value.ToString("0.######", CultureInfo.InvariantCulture);
            private static string Safe(string value) => string.Concat(value.Select(character =>
                char.IsLetterOrDigit(character) || character == '_' || character == '-' ? character : '_'));
        }

        internal static void WriteObj(string path, Action<ObjBuilder> build)
        {
            Directory.CreateDirectory(Path.GetDirectoryName(path) ?? string.Empty);
            var baseName = Path.GetFileNameWithoutExtension(path);
            var mtlName = baseName + ".mtl";
            var builder = new ObjBuilder(mtlName);
            build(builder);
            File.WriteAllText(path, builder.ToString(), Encoding.UTF8);
            File.WriteAllText(Path.ChangeExtension(path, ".mtl"), PaletteMtl(), Encoding.UTF8);
        }

        internal static void ConfigureModelImporter(string path)
        {
            AssetDatabase.ImportAsset(path, ImportAssetOptions.ForceSynchronousImport | ImportAssetOptions.ForceUpdate);
            if (AssetImporter.GetAtPath(path) is not ModelImporter importer)
                return;
            importer.globalScale = 1f;
            importer.useFileScale = true;
            importer.importAnimation = false;
            importer.importBlendShapes = false;
            importer.importCameras = false;
            importer.importLights = false;
            importer.isReadable = false;
            importer.meshCompression = ModelImporterMeshCompression.Medium;
            importer.optimizeMeshPolygons = true;
            importer.optimizeMeshVertices = true;
            importer.importNormals = ModelImporterNormals.Calculate;
            importer.normalCalculationMode = ModelImporterNormalCalculationMode.AreaAndAngleWeighted;
            importer.normalSmoothingAngle = 42f;
            importer.SaveAndReimport();
        }

        internal static void WriteTexture(string path, int seed, Color baseColor, Color accentColor, bool radial = false)
        {
            const int size = 256;
            var texture = new Texture2D(size, size, TextureFormat.RGBA32, false, false);
            var random = new System.Random(seed);
            var pixels = new Color32[size * size];
            var offsetX = (float)random.NextDouble() * 80f;
            var offsetY = (float)random.NextDouble() * 80f;
            for (var y = 0; y < size; y++)
            {
                for (var x = 0; x < size; x++)
                {
                    var u = x / (size - 1f);
                    var v = y / (size - 1f);
                    var noiseA = Mathf.PerlinNoise(offsetX + u * 6f, offsetY + v * 6f);
                    var noiseB = Mathf.PerlinNoise(offsetX * 0.31f + u * 22f, offsetY * 0.31f + v * 22f);
                    var value = noiseA * 0.7f + noiseB * 0.3f;
                    if (radial)
                    {
                        var distance = Vector2.Distance(new Vector2(u, v), new Vector2(0.5f, 0.5f));
                        value = Mathf.Clamp01(value * 0.42f + (1f - distance * 1.72f));
                    }
                    var color = Color.Lerp(baseColor, accentColor, Mathf.SmoothStep(0f, 1f, value));
                    pixels[y * size + x] = color;
                }
            }
            texture.SetPixels32(pixels);
            texture.Apply(false, false);
            Directory.CreateDirectory(Path.GetDirectoryName(path) ?? string.Empty);
            File.WriteAllBytes(path, texture.EncodeToPNG());
            UnityEngine.Object.DestroyImmediate(texture);
            AssetDatabase.ImportAsset(path, ImportAssetOptions.ForceSynchronousImport | ImportAssetOptions.ForceUpdate);
            if (AssetImporter.GetAtPath(path) is TextureImporter importer)
            {
                importer.textureCompression = TextureImporterCompression.CompressedHQ;
                importer.maxTextureSize = 512;
                importer.mipmapEnabled = true;
                importer.wrapMode = TextureWrapMode.Repeat;
                importer.filterMode = FilterMode.Trilinear;
                importer.anisoLevel = 4;
                importer.SaveAndReimport();
            }
        }

        internal static void WriteHudAtlas(string path)
        {
            const int size = 512;
            var texture = new Texture2D(size, size, TextureFormat.RGBA32, false, false);
            var pixels = Enumerable.Repeat(new Color32(0, 0, 0, 0), size * size).ToArray();
            DrawRounded(pixels, size, new RectInt(16, 272, 224, 224), 34,
                new Color32(11, 28, 44, 228), new Color32(89, 151, 194, 255));
            DrawRounded(pixels, size, new RectInt(272, 336, 224, 160), 28,
                new Color32(13, 31, 48, 235), new Color32(255, 133, 40, 255));
            DrawCircle(pixels, size, new Vector2Int(116, 116), 92,
                new Color32(16, 39, 61, 185), new Color32(117, 185, 224, 220));
            DrawCircle(pixels, size, new Vector2Int(384, 128), 62,
                new Color32(28, 63, 91, 220), new Color32(146, 210, 239, 255));
            texture.SetPixels32(pixels);
            texture.Apply(false, false);
            Directory.CreateDirectory(Path.GetDirectoryName(path) ?? string.Empty);
            File.WriteAllBytes(path, texture.EncodeToPNG());
            UnityEngine.Object.DestroyImmediate(texture);
            AssetDatabase.ImportAsset(path, ImportAssetOptions.ForceSynchronousImport | ImportAssetOptions.ForceUpdate);
            if (AssetImporter.GetAtPath(path) is TextureImporter importer)
            {
                importer.textureType = TextureImporterType.Sprite;
                importer.spriteImportMode = SpriteImportMode.Single;
                importer.alphaIsTransparency = true;
                importer.mipmapEnabled = false;
                importer.textureCompression = TextureImporterCompression.CompressedHQ;
                importer.maxTextureSize = 1024;
                importer.SaveAndReimport();
            }
        }

        internal static void WriteWav(string path, int seed, float duration, float frequency, float noiseAmount, float decay)
        {
            const int sampleRate = 44100;
            var sampleCount = Mathf.Max(128, Mathf.CeilToInt(duration * sampleRate));
            var random = new System.Random(seed);
            var samples = new short[sampleCount];
            var phase = 0f;
            for (var i = 0; i < sampleCount; i++)
            {
                var t = i / (float)sampleRate;
                phase += frequency * (1f + Mathf.Sin(t * 7f) * 0.025f) / sampleRate;
                var envelope = Mathf.Pow(Mathf.Clamp01(1f - t / Mathf.Max(0.01f, duration)), Mathf.Max(0.05f, decay));
                var tone = Mathf.Sin(phase * Mathf.PI * 2f) * (1f - noiseAmount);
                var noise = ((float)random.NextDouble() * 2f - 1f) * noiseAmount;
                var value = Mathf.Clamp((tone + noise) * envelope * 0.82f, -1f, 1f);
                samples[i] = (short)Mathf.RoundToInt(value * short.MaxValue);
            }
            Directory.CreateDirectory(Path.GetDirectoryName(path) ?? string.Empty);
            using var stream = File.Create(path);
            using var writer = new BinaryWriter(stream);
            writer.Write(Encoding.ASCII.GetBytes("RIFF"));
            writer.Write(36 + sampleCount * 2);
            writer.Write(Encoding.ASCII.GetBytes("WAVEfmt "));
            writer.Write(16);
            writer.Write((short)1);
            writer.Write((short)1);
            writer.Write(sampleRate);
            writer.Write(sampleRate * 2);
            writer.Write((short)2);
            writer.Write((short)16);
            writer.Write(Encoding.ASCII.GetBytes("data"));
            writer.Write(sampleCount * 2);
            foreach (var sample in samples)
                writer.Write(sample);
            AssetDatabase.ImportAsset(path, ImportAssetOptions.ForceSynchronousImport | ImportAssetOptions.ForceUpdate);
        }

        private static string PaletteMtl() =>
            "# HAVENLINE winter-cartoon palette\n" +
            Material("Snow", new Color(0.72f, 0.86f, 0.94f), 0.05f) +
            Material("Ice", new Color(0.25f, 0.58f, 0.75f), 0.5f) +
            Material("Navy", new Color(0.06f, 0.13f, 0.20f), 0.1f) +
            Material("Blue", new Color(0.11f, 0.34f, 0.52f), 0.15f) +
            Material("Teal", new Color(0.08f, 0.47f, 0.50f), 0.18f) +
            Material("Orange", new Color(1f, 0.31f, 0.06f), 0.2f) +
            Material("Amber", new Color(1f, 0.62f, 0.12f), 0.15f) +
            Material("Wood", new Color(0.34f, 0.18f, 0.08f), 0.08f) +
            Material("WoodLight", new Color(0.58f, 0.34f, 0.14f), 0.08f) +
            Material("Stone", new Color(0.25f, 0.32f, 0.38f), 0.05f) +
            Material("StoneLight", new Color(0.46f, 0.55f, 0.60f), 0.08f) +
            Material("Metal", new Color(0.20f, 0.30f, 0.37f), 0.65f) +
            Material("MetalLight", new Color(0.46f, 0.62f, 0.70f), 0.75f) +
            Material("Pine", new Color(0.05f, 0.27f, 0.25f), 0.04f) +
            Material("PineLight", new Color(0.10f, 0.43f, 0.37f), 0.04f) +
            Material("Fur", new Color(0.20f, 0.25f, 0.29f), 0.02f) +
            Material("FurLight", new Color(0.42f, 0.48f, 0.52f), 0.02f) +
            Material("Skin", new Color(0.47f, 0.27f, 0.17f), 0.04f) +
            Material("White", new Color(0.92f, 0.96f, 0.98f), 0.04f) +
            Material("Black", new Color(0.015f, 0.025f, 0.035f), 0.1f);

        private static string Material(string name, Color color, float specular) =>
            $"newmtl {name}\nKd {color.r.ToString("0.###", CultureInfo.InvariantCulture)} {color.g.ToString("0.###", CultureInfo.InvariantCulture)} {color.b.ToString("0.###", CultureInfo.InvariantCulture)}\n" +
            $"Ks {specular.ToString("0.###", CultureInfo.InvariantCulture)} {specular.ToString("0.###", CultureInfo.InvariantCulture)} {specular.ToString("0.###", CultureInfo.InvariantCulture)}\nNs 24\nd 1\nillum 2\n\n";

        private static void DrawRounded(Color32[] pixels, int width, RectInt rectangle, int radius, Color32 fill, Color32 border)
        {
            for (var y = rectangle.yMin; y < rectangle.yMax; y++)
            {
                for (var x = rectangle.xMin; x < rectangle.xMax; x++)
                {
                    var localX = Mathf.Min(x - rectangle.xMin, rectangle.xMax - 1 - x);
                    var localY = Mathf.Min(y - rectangle.yMin, rectangle.yMax - 1 - y);
                    var inside = localX >= radius || localY >= radius ||
                        Vector2.Distance(new Vector2(localX, localY), new Vector2(radius, radius)) <= radius;
                    if (!inside)
                        continue;
                    var edge = localX < 4 || localY < 4;
                    pixels[y * width + x] = edge ? border : fill;
                }
            }
        }

        private static void DrawCircle(Color32[] pixels, int width, Vector2Int center, int radius, Color32 fill, Color32 border)
        {
            var radiusSquared = radius * radius;
            var innerSquared = (radius - 5) * (radius - 5);
            for (var y = center.y - radius; y <= center.y + radius; y++)
            {
                for (var x = center.x - radius; x <= center.x + radius; x++)
                {
                    if (x < 0 || y < 0 || x >= width || y >= width)
                        continue;
                    var delta = new Vector2Int(x - center.x, y - center.y);
                    var squared = delta.sqrMagnitude;
                    if (squared > radiusSquared)
                        continue;
                    pixels[y * width + x] = squared >= innerSquared ? border : fill;
                }
            }
        }
    }
}
