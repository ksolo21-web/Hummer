using System;
using System.Collections.Generic;
using System.IO;
using UnityEditor;
using UnityEngine;

namespace Havenline.Editor
{
    /// <summary>
    /// Creates HAVENLINE-owned, asset-backed furnace meshes with genuine radial depth.
    /// The flame tongue is an asymmetric low-poly volume built from drifting rings, never
    /// a front-facing cutout, billboard, or stack of flat cards.
    /// </summary>
    internal static class HavenlinePremiumFlameMeshFactory
    {
        internal const string FlameTongueMeshPath =
            "Assets/Havenline/Art/Production/Environment/Premium/HAVENLINE_FurnaceFlameTongue.asset";
        internal const string EmberMeshPath =
            "Assets/Havenline/Art/Production/Environment/Premium/HAVENLINE_FurnaceEmber.asset";

        internal static readonly Color FlameOuterBase = new(0.68f, 0.085f, 0.012f, 1f);
        internal static readonly Color FlameOuterEmission = new(1.05f, 0.105f, 0.014f, 1f);
        internal static readonly Color FlameInnerBase = new(1f, 0.32f, 0.030f, 1f);
        internal static readonly Color FlameInnerEmission = new(1.42f, 0.34f, 0.030f, 1f);

        private static bool flameMaterialsWarmed;

        internal static void Ensure()
        {
            HavenlinePremiumVisualAssets.Ensure();
            var folder = Path.GetDirectoryName(FlameTongueMeshPath)?.Replace('\\', '/');
            if (!string.IsNullOrWhiteSpace(folder) && !AssetDatabase.IsValidFolder(folder))
                Directory.CreateDirectory(folder);

            CreateOrUpdateMesh(FlameTongueMeshPath, CreateFlameTongueMesh());
            CreateOrUpdateMesh(EmberMeshPath, CreateEmberMesh());
            TuneEmissiveMaterial(
                HavenlinePremiumVisualAssets.FlameOuterMaterialPath,
                FlameOuterBase,
                FlameOuterEmission,
                0.07f);
            TuneEmissiveMaterial(
                HavenlinePremiumVisualAssets.FlameInnerMaterialPath,
                FlameInnerBase,
                FlameInnerEmission,
                0.08f);

            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);
            if (!flameMaterialsWarmed)
            {
                WarmAuthoredFlameMaterials();
                flameMaterialsWarmed = true;
            }
        }

        internal static Mesh RequireFlameTongue()
        {
            Ensure();
            return AssetDatabase.LoadAssetAtPath<Mesh>(FlameTongueMeshPath) ??
                   throw new InvalidOperationException("HAVENLINE volumetric flame tongue mesh was not created.");
        }

        internal static Mesh RequireEmber()
        {
            Ensure();
            return AssetDatabase.LoadAssetAtPath<Mesh>(EmberMeshPath) ??
                   throw new InvalidOperationException("HAVENLINE furnace ember mesh was not created.");
        }

        internal static Mesh CreateFlameTongueMesh()
        {
            const int sides = 8;
            var heights = new[] { 0f, 0.18f, 0.43f, 0.72f, 1.01f, 1.24f };
            var radiusX = new[] { 0.34f, 0.38f, 0.31f, 0.24f, 0.15f, 0.072f };
            var radiusZ = new[] { 0.27f, 0.30f, 0.25f, 0.19f, 0.12f, 0.058f };
            var centers = new[]
            {
                new Vector2(0f, 0f),
                new Vector2(0.025f, -0.012f),
                new Vector2(-0.045f, 0.025f),
                new Vector2(0.052f, 0.012f),
                new Vector2(-0.018f, -0.018f),
                new Vector2(0.048f, 0.014f)
            };
            var phase = new[] { 0.02f, 0.31f, -0.18f, 0.42f, -0.27f, 0.16f };

            var vertices = new List<Vector3>(heights.Length * sides + 2);
            var uv = new List<Vector2>(heights.Length * sides + 2);
            for (var ring = 0; ring < heights.Length; ring++)
            {
                for (var side = 0; side < sides; side++)
                {
                    var angle = side * Mathf.PI * 2f / sides + phase[ring];
                    var irregularity = 1f +
                                       Mathf.Sin(angle * 3f + ring * 0.71f) * 0.09f +
                                       Mathf.Cos(angle * 2f - ring * 0.43f) * 0.045f;
                    vertices.Add(new Vector3(
                        centers[ring].x + Mathf.Cos(angle) * radiusX[ring] * irregularity,
                        heights[ring],
                        centers[ring].y + Mathf.Sin(angle) * radiusZ[ring] * irregularity));
                    uv.Add(new Vector2(side / (float)sides, ring / (float)(heights.Length - 1)));
                }
            }

            var bottomCenter = vertices.Count;
            vertices.Add(new Vector3(centers[0].x, heights[0], centers[0].y));
            uv.Add(new Vector2(0.5f, 0f));
            var apex = vertices.Count;
            vertices.Add(new Vector3(0.015f, 1.43f, 0.028f));
            uv.Add(new Vector2(0.5f, 1f));

            var triangles = new List<int>(heights.Length * sides * 6);
            for (var ring = 0; ring < heights.Length - 1; ring++)
            {
                var lower = ring * sides;
                var upper = (ring + 1) * sides;
                for (var side = 0; side < sides; side++)
                {
                    var next = (side + 1) % sides;
                    triangles.Add(lower + side);
                    triangles.Add(upper + next);
                    triangles.Add(upper + side);
                    triangles.Add(lower + side);
                    triangles.Add(lower + next);
                    triangles.Add(upper + next);
                }
            }

            for (var side = 0; side < sides; side++)
            {
                var next = (side + 1) % sides;
                triangles.Add(bottomCenter);
                triangles.Add(next);
                triangles.Add(side);

                var top = (heights.Length - 1) * sides;
                triangles.Add(top + side);
                triangles.Add(top + next);
                triangles.Add(apex);
            }

            var mesh = new Mesh { name = "HAVENLINE_FurnaceFlameTongue" };
            mesh.SetVertices(vertices);
            mesh.SetTriangles(triangles, 0, true);
            mesh.SetUVs(0, uv);
            mesh.RecalculateNormals();
            mesh.RecalculateTangents();
            mesh.RecalculateBounds();
            return mesh;
        }

        internal static Mesh CreateEmberMesh()
        {
            const int segments = 10;
            const int rings = 4;
            var vertices = new List<Vector3>();
            var uv = new List<Vector2>();
            for (var ring = 0; ring <= rings; ring++)
            {
                var v = ring / (float)rings;
                var latitude = Mathf.Lerp(-Mathf.PI * 0.5f, Mathf.PI * 0.5f, v);
                var radius = Mathf.Cos(latitude) * 0.5f;
                var y = Mathf.Sin(latitude) * 0.5f;
                for (var segment = 0; segment < segments; segment++)
                {
                    var u = segment / (float)segments;
                    var angle = u * Mathf.PI * 2f + ring * 0.11f;
                    var variation = 1f + Mathf.Sin(angle * 3f + ring) * 0.08f;
                    vertices.Add(new Vector3(
                        Mathf.Cos(angle) * radius * variation,
                        y,
                        Mathf.Sin(angle) * radius * (1.05f - variation * 0.08f)));
                    uv.Add(new Vector2(u, v));
                }
            }

            var triangles = new List<int>();
            for (var ring = 0; ring < rings; ring++)
            {
                var lower = ring * segments;
                var upper = (ring + 1) * segments;
                for (var segment = 0; segment < segments; segment++)
                {
                    var next = (segment + 1) % segments;
                    triangles.Add(lower + segment);
                    triangles.Add(upper + next);
                    triangles.Add(upper + segment);
                    triangles.Add(lower + segment);
                    triangles.Add(lower + next);
                    triangles.Add(upper + next);
                }
            }

            var mesh = new Mesh { name = "HAVENLINE_FurnaceEmber" };
            mesh.SetVertices(vertices);
            mesh.SetTriangles(triangles, 0, true);
            mesh.SetUVs(0, uv);
            mesh.RecalculateNormals();
            mesh.RecalculateTangents();
            mesh.RecalculateBounds();
            return mesh;
        }

        private static void CreateOrUpdateMesh(string path, Mesh generated)
        {
            var existing = AssetDatabase.LoadAssetAtPath<Mesh>(path);
            if (existing == null)
            {
                AssetDatabase.CreateAsset(generated, path);
                return;
            }

            EditorUtility.CopySerialized(generated, existing);
            existing.name = generated.name;
            EditorUtility.SetDirty(existing);
            UnityEngine.Object.DestroyImmediate(generated);
        }

        private static void TuneEmissiveMaterial(
            string path,
            Color baseColor,
            Color emissionColor,
            float smoothness)
        {
            var material = AssetDatabase.LoadAssetAtPath<Material>(path);
            if (material == null)
                throw new InvalidOperationException("HAVENLINE flame material is missing: " + path);

            if (material.HasProperty("_BaseColor")) material.SetColor("_BaseColor", baseColor);
            if (material.HasProperty("_Color")) material.SetColor("_Color", baseColor);
            if (material.HasProperty("_EmissionColor")) material.SetColor("_EmissionColor", emissionColor);
            if (material.HasProperty("_Smoothness")) material.SetFloat("_Smoothness", smoothness);
            if (material.HasProperty("_Metallic")) material.SetFloat("_Metallic", 0f);
            if (material.HasProperty("_Surface")) material.SetFloat("_Surface", 0f);
            material.EnableKeyword("_EMISSION");
            material.globalIlluminationFlags = MaterialGlobalIlluminationFlags.RealtimeEmissive;
            EditorUtility.SetDirty(material);
        }

        private static void WarmAuthoredFlameMaterials()
        {
            var mesh = AssetDatabase.LoadAssetAtPath<Mesh>(FlameTongueMeshPath);
            var outer = AssetDatabase.LoadAssetAtPath<Material>(HavenlinePremiumVisualAssets.FlameOuterMaterialPath);
            var inner = AssetDatabase.LoadAssetAtPath<Material>(HavenlinePremiumVisualAssets.FlameInnerMaterialPath);
            if (mesh == null || outer == null || inner == null)
                throw new InvalidOperationException("HAVENLINE targeted flame warm-up assets are missing.");

            var root = new GameObject("HAVENLINE_FlameWarmupRoot")
            {
                hideFlags = HideFlags.HideAndDontSave
            };
            var texture = new RenderTexture(128, 128, 24, RenderTextureFormat.ARGB32)
            {
                antiAliasing = 1,
                useMipMap = false,
                autoGenerateMips = false,
                hideFlags = HideFlags.HideAndDontSave
            };
            var previousActive = RenderTexture.active;
            try
            {
                var cameraObject = new GameObject("HAVENLINE_FlameWarmupCamera")
                {
                    hideFlags = HideFlags.HideAndDontSave
                };
                cameraObject.transform.SetParent(root.transform, false);
                cameraObject.transform.localPosition = new Vector3(0f, 0.70f, -3f);
                var camera = cameraObject.AddComponent<Camera>();
                camera.enabled = false;
                camera.orthographic = true;
                camera.orthographicSize = 1.15f;
                camera.nearClipPlane = 0.01f;
                camera.farClipPlane = 8f;
                camera.clearFlags = CameraClearFlags.SolidColor;
                camera.backgroundColor = Color.black;
                camera.allowHDR = true;
                camera.targetTexture = texture;

                var lightObject = new GameObject("HAVENLINE_FlameWarmupLight")
                {
                    hideFlags = HideFlags.HideAndDontSave
                };
                lightObject.transform.SetParent(root.transform, false);
                lightObject.transform.localRotation = Quaternion.Euler(38f, -28f, 0f);
                var light = lightObject.AddComponent<Light>();
                light.type = LightType.Directional;
                light.color = new Color(1f, 0.72f, 0.52f, 1f);
                light.intensity = 1f;
                light.shadows = LightShadows.None;

                CreateWarmupRenderer(root.transform, "Outer", mesh, outer, new Vector3(-0.40f, 0f, 0f));
                CreateWarmupRenderer(root.transform, "Inner", mesh, inner, new Vector3(0.40f, 0f, 0f));

                texture.Create();
                RenderTexture.active = texture;
                camera.Render();
                camera.Render();
            }
            finally
            {
                RenderTexture.active = previousActive;
                texture.Release();
                UnityEngine.Object.DestroyImmediate(texture);
                UnityEngine.Object.DestroyImmediate(root);
            }
        }

        private static void CreateWarmupRenderer(
            Transform parent,
            string suffix,
            Mesh mesh,
            Material material,
            Vector3 localPosition)
        {
            var child = new GameObject(
                "HAVENLINE_FlameWarmup_" + suffix,
                typeof(MeshFilter),
                typeof(MeshRenderer))
            {
                hideFlags = HideFlags.HideAndDontSave
            };
            child.transform.SetParent(parent, false);
            child.transform.localPosition = localPosition;
            child.transform.localScale = Vector3.one * 0.72f;
            child.GetComponent<MeshFilter>().sharedMesh = mesh;
            child.GetComponent<MeshRenderer>().sharedMaterial = material;
        }
    }
}
