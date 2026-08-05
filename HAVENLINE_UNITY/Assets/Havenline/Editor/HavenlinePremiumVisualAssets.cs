using System;
using System.Collections.Generic;
using System.Linq;
using UnityEditor;
using UnityEngine;

namespace Havenline.Editor
{
    internal static class HavenlinePremiumVisualAssets
    {
        internal const string Root = "Assets/Havenline/Art/Production/Environment/Premium";
        internal const string SnowFieldPath = Root + "/HAVENLINE_LayeredSnowField.asset";
        internal const string PathPatchPath = Root + "/HAVENLINE_SnowPathPatch.asset";
        internal const string WarmPatchPath = Root + "/HAVENLINE_WarmSnowPatch.asset";
        internal const string FurnaceBodyPath = Root + "/HAVENLINE_FurnaceBody.asset";
        internal const string FurnaceHoodPath = Root + "/HAVENLINE_FurnaceHood.asset";
        internal const string FurnaceChimneyPath = Root + "/HAVENLINE_FurnaceChimney.asset";
        internal const string ShelterShellPath = Root + "/HAVENLINE_ShelterShell.asset";
        internal const string ShelterSnowCapPath = Root + "/HAVENLINE_ShelterSnowCap.asset";
        internal const string PaleSnowMaterialPath =
            "Assets/Havenline/Art/Production/Materials/HAVENLINE_PaleSnow.mat";
        internal const string SnowPathMaterialPath =
            "Assets/Havenline/Art/Production/Materials/HAVENLINE_SnowPath.mat";
        internal const string WarmSnowMaterialPath =
            "Assets/Havenline/Art/Production/Materials/HAVENLINE_WarmSnow.mat";

        private static bool generating;
        private static bool ensured;

        internal static void Ensure()
        {
            if (ensured || generating)
                return;
            generating = true;
            try
            {
                EnsureFolder("Assets/Havenline/Art/Production/Environment", "Premium");
                CreateMeshIfMissing(SnowFieldPath, CreateRadialMesh(
                    "HAVENLINE_LayeredSnowField", 64, 14.65f, 14.9f, 4, 0.22f, 0.16f, 4171));
                CreateMeshIfMissing(PathPatchPath, CreateEllipseMesh(
                    "HAVENLINE_SnowPathPatch", 40, 1f, 1f, 0.045f, 9137));
                CreateMeshIfMissing(WarmPatchPath, CreateEllipseMesh(
                    "HAVENLINE_WarmSnowPatch", 48, 1f, 1f, 0.055f, 16127));
                CreateMeshIfMissing(FurnaceBodyPath, CreateChamferedColumnMesh(
                    "HAVENLINE_FurnaceBody", 3.2f, 1.9f, 1.8f, 0.24f));
                CreateMeshIfMissing(FurnaceHoodPath, CreateChamferedColumnMesh(
                    "HAVENLINE_FurnaceHood", 3.75f, 2.2f, 0.58f, 0.28f));
                CreateMeshIfMissing(FurnaceChimneyPath, CreateChamferedColumnMesh(
                    "HAVENLINE_FurnaceChimney", 0.82f, 0.82f, 1.55f, 0.16f));
                CreateMeshIfMissing(ShelterShellPath, CreateTentMesh(
                    "HAVENLINE_ShelterShell", 3.9f, 2.65f, 3.3f));
                CreateMeshIfMissing(ShelterSnowCapPath, CreateTentRoofCapMesh(
                    "HAVENLINE_ShelterSnowCap", 3.9f, 2.65f, 3.3f, 0.72f));
                CreateMaterialIfMissing(
                    PaleSnowMaterialPath,
                    new Color(0.965f, 0.985f, 1f, 1f),
                    0.17f,
                    string.Empty);
                CreateMaterialIfMissing(
                    SnowPathMaterialPath,
                    new Color(0.56f, 0.73f, 0.82f, 1f),
                    0.28f,
                    "Assets/Havenline/Art/Production/Textures/HAVENLINE_Surface_02.png");
                CreateMaterialIfMissing(
                    WarmSnowMaterialPath,
                    new Color(0.96f, 0.82f, 0.62f, 1f),
                    0.34f,
                    "Assets/Havenline/Art/Production/Textures/HAVENLINE_Surface_05.png");
                AssetDatabase.SaveAssets();
                ensured = true;
            }
            finally
            {
                generating = false;
            }
        }

        private static Mesh CreateRadialMesh(
            string name,
            int segments,
            float radiusX,
            float radiusZ,
            int rings,
            float centerHeight,
            float edgeHeight,
            int seed)
        {
            var vertices = new List<Vector3> { new(0f, centerHeight, 0f) };
            var uv = new List<Vector2> { new(0.5f, 0.5f) };
            for (var ring = 1; ring <= rings; ring++)
            {
                var ringFraction = ring / (float)rings;
                for (var segment = 0; segment < segments; segment++)
                {
                    var angle = segment * Mathf.PI * 2f / segments;
                    var irregularity = 1f + Mathf.Sin(angle * 3f + seed * 0.001f) * 0.025f +
                                       Mathf.Sin(angle * 7f + seed * 0.003f) * 0.018f;
                    var radial = Mathf.SmoothStep(0f, 1f, ringFraction) * irregularity;
                    var height = Mathf.Lerp(centerHeight, edgeHeight, ringFraction) +
                                 Mathf.Sin(angle * 5f + ring * 1.7f) * 0.018f * ringFraction;
                    var x = Mathf.Cos(angle) * radiusX * radial;
                    var z = Mathf.Sin(angle) * radiusZ * radial;
                    vertices.Add(new Vector3(x, height, z));
                    uv.Add(new Vector2(x / (radiusX * 2f) + 0.5f, z / (radiusZ * 2f) + 0.5f));
                }
            }

            var triangles = new List<int>();
            for (var segment = 0; segment < segments; segment++)
            {
                var next = (segment + 1) % segments;
                triangles.Add(0);
                triangles.Add(1 + next);
                triangles.Add(1 + segment);
            }
            for (var ring = 1; ring < rings; ring++)
            {
                var innerStart = 1 + (ring - 1) * segments;
                var outerStart = 1 + ring * segments;
                for (var segment = 0; segment < segments; segment++)
                {
                    var next = (segment + 1) % segments;
                    var inner = innerStart + segment;
                    var innerNext = innerStart + next;
                    var outer = outerStart + segment;
                    var outerNext = outerStart + next;
                    triangles.Add(inner);
                    triangles.Add(outerNext);
                    triangles.Add(outer);
                    triangles.Add(inner);
                    triangles.Add(innerNext);
                    triangles.Add(outerNext);
                }
            }

            return BuildMesh(name, vertices, triangles, uv);
        }

        private static Mesh CreateEllipseMesh(
            string name,
            int segments,
            float radiusX,
            float radiusZ,
            float height,
            int seed)
        {
            var vertices = new List<Vector3> { new(0f, height, 0f) };
            var uv = new List<Vector2> { new(0.5f, 0.5f) };
            for (var segment = 0; segment < segments; segment++)
            {
                var angle = segment * Mathf.PI * 2f / segments;
                var variation = 1f + Mathf.Sin(angle * 4f + seed * 0.0007f) * 0.07f +
                                Mathf.Sin(angle * 9f + seed * 0.0011f) * 0.035f;
                var x = Mathf.Cos(angle) * radiusX * variation;
                var z = Mathf.Sin(angle) * radiusZ * variation;
                vertices.Add(new Vector3(x, height + Mathf.Sin(angle * 3f) * 0.006f, z));
                uv.Add(new Vector2(x / (radiusX * 2.4f) + 0.5f, z / (radiusZ * 2.4f) + 0.5f));
            }
            var triangles = new List<int>();
            for (var segment = 0; segment < segments; segment++)
            {
                var next = (segment + 1) % segments;
                triangles.Add(0);
                triangles.Add(1 + next);
                triangles.Add(1 + segment);
            }
            return BuildMesh(name, vertices, triangles, uv);
        }

        private static Mesh CreateChamferedColumnMesh(
            string name,
            float width,
            float depth,
            float height,
            float bevel)
        {
            var halfWidth = width * 0.5f;
            var halfDepth = depth * 0.5f;
            var points = new[]
            {
                new Vector2(-halfWidth + bevel, -halfDepth),
                new Vector2(halfWidth - bevel, -halfDepth),
                new Vector2(halfWidth, -halfDepth + bevel),
                new Vector2(halfWidth, halfDepth - bevel),
                new Vector2(halfWidth - bevel, halfDepth),
                new Vector2(-halfWidth + bevel, halfDepth),
                new Vector2(-halfWidth, halfDepth - bevel),
                new Vector2(-halfWidth, -halfDepth + bevel)
            };
            var vertices = new List<Vector3>();
            var uv = new List<Vector2>();
            foreach (var point in points)
            {
                vertices.Add(new Vector3(point.x, 0f, point.y));
                uv.Add(new Vector2(point.x / width + 0.5f, 0f));
            }
            foreach (var point in points)
            {
                vertices.Add(new Vector3(point.x, height, point.y));
                uv.Add(new Vector2(point.x / width + 0.5f, 1f));
            }
            var bottomCenter = vertices.Count;
            vertices.Add(Vector3.zero);
            uv.Add(new Vector2(0.5f, 0.5f));
            var topCenter = vertices.Count;
            vertices.Add(new Vector3(0f, height, 0f));
            uv.Add(new Vector2(0.5f, 0.5f));
            var triangles = new List<int>();
            for (var index = 0; index < points.Length; index++)
            {
                var next = (index + 1) % points.Length;
                triangles.Add(index);
                triangles.Add(next + points.Length);
                triangles.Add(index + points.Length);
                triangles.Add(index);
                triangles.Add(next);
                triangles.Add(next + points.Length);
                triangles.Add(topCenter);
                triangles.Add(index + points.Length);
                triangles.Add(next + points.Length);
                triangles.Add(bottomCenter);
                triangles.Add(next);
                triangles.Add(index);
            }
            return BuildMesh(name, vertices, triangles, uv);
        }

        private static Mesh CreateTentMesh(string name, float width, float height, float depth)
        {
            var halfWidth = width * 0.5f;
            var halfDepth = depth * 0.5f;
            var vertices = new List<Vector3>
            {
                new(-halfWidth, 0f, halfDepth), new(0f, height, halfDepth), new(halfWidth, 0f, halfDepth),
                new(-halfWidth, 0f, -halfDepth), new(0f, height, -halfDepth), new(halfWidth, 0f, -halfDepth)
            };
            var triangles = new List<int>
            {
                0,1,2, 5,4,3,
                0,3,4, 0,4,1,
                1,4,5, 1,5,2,
                0,2,5, 0,5,3
            };
            var uv = new List<Vector2>
            {
                new(0f,0f), new(0.5f,1f), new(1f,0f),
                new(0f,0f), new(0.5f,1f), new(1f,0f)
            };
            return BuildMesh(name, vertices, triangles, uv);
        }

        private static Mesh CreateTentRoofCapMesh(
            string name,
            float width,
            float height,
            float depth,
            float coverage)
        {
            var halfWidth = width * 0.5f;
            var halfDepth = depth * 0.5f + 0.055f;
            var lowX = halfWidth * Mathf.Clamp01(coverage);
            var lowY = height * (1f - Mathf.Clamp01(coverage)) + 0.08f;
            var ridgeY = height + 0.13f;
            var vertices = new List<Vector3>
            {
                new(-lowX, lowY, halfDepth), new(0f, ridgeY, halfDepth),
                new(0f, ridgeY, -halfDepth), new(-lowX, lowY, -halfDepth),
                new(0f, ridgeY, halfDepth), new(lowX, lowY, halfDepth),
                new(lowX, lowY, -halfDepth), new(0f, ridgeY, -halfDepth)
            };
            var triangles = new List<int>
            {
                0,1,2, 0,2,3,
                4,5,6, 4,6,7
            };
            var uv = new List<Vector2>
            {
                new(0f,0f), new(1f,0f), new(1f,1f), new(0f,1f),
                new(0f,0f), new(1f,0f), new(1f,1f), new(0f,1f)
            };
            return BuildMesh(name, vertices, triangles, uv);
        }

        private static Mesh BuildMesh(
            string name,
            IReadOnlyList<Vector3> vertices,
            IReadOnlyList<int> triangles,
            IReadOnlyList<Vector2> uv)
        {
            var mesh = new Mesh { name = name };
            mesh.vertices = vertices.ToArray();
            mesh.triangles = triangles.ToArray();
            mesh.uv = uv.ToArray();
            mesh.RecalculateNormals();
            mesh.RecalculateTangents();
            mesh.RecalculateBounds();
            mesh.UploadMeshData(false);
            return mesh;
        }

        private static void CreateMeshIfMissing(string path, Mesh mesh)
        {
            if (AssetDatabase.LoadAssetAtPath<Mesh>(path) != null)
                AssetDatabase.DeleteAsset(path);
            AssetDatabase.CreateAsset(mesh, path);
        }

        private static void CreateMaterialIfMissing(
            string path,
            Color color,
            float smoothness,
            string texturePath)
        {
            if (AssetDatabase.LoadAssetAtPath<Material>(path) != null)
                AssetDatabase.DeleteAsset(path);
            var shader = Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard");
            if (shader == null)
                throw new InvalidOperationException("HAVENLINE visual polish could not find a lit shader.");
            var material = new Material(shader)
            {
                name = System.IO.Path.GetFileNameWithoutExtension(path),
                enableInstancing = true
            };
            if (material.HasProperty("_BaseColor")) material.SetColor("_BaseColor", color);
            if (material.HasProperty("_Color")) material.SetColor("_Color", color);
            if (material.HasProperty("_Smoothness")) material.SetFloat("_Smoothness", smoothness);
            var texture = string.IsNullOrWhiteSpace(texturePath)
                ? null
                : AssetDatabase.LoadAssetAtPath<Texture2D>(texturePath);
            if (texture != null)
            {
                if (material.HasProperty("_BaseMap")) material.SetTexture("_BaseMap", texture);
                if (material.HasProperty("_MainTex")) material.SetTexture("_MainTex", texture);
            }
            AssetDatabase.CreateAsset(material, path);
        }

        private static void EnsureFolder(string parent, string name)
        {
            var path = parent + "/" + name;
            if (!AssetDatabase.IsValidFolder(path))
                AssetDatabase.CreateFolder(parent, name);
        }
    }

    public sealed class HavenlinePremiumVisualAssetPostprocessor : AssetPostprocessor
    {
        private static void OnPostprocessAllAssets(
            string[] importedAssets,
            string[] deletedAssets,
            string[] movedAssets,
            string[] movedFromAssetPaths)
        {
            if (!importedAssets.Any(path =>
                    path.Equals(
                        "Assets/Havenline/Art/Production/Materials/HAVENLINE_Snow.mat",
                        StringComparison.Ordinal) ||
                    path.Equals(
                        "Assets/Havenline/Art/Production/Materials/HAVENLINE_Ice.mat",
                        StringComparison.Ordinal)))
                return;
            HavenlinePremiumVisualAssets.Ensure();
        }
    }
}
