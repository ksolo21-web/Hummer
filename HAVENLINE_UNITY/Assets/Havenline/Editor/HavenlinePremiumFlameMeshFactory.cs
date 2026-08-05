using System;
using System.Collections.Generic;
using System.IO;
using UnityEditor;
using UnityEngine;

namespace Havenline.Editor
{
    /// <summary>
    /// Creates HAVENLINE-owned, asset-backed furnace meshes with a readable vertical silhouette.
    /// The flame tongue is an extruded solid rather than a billboard or a stack of circular rings.
    /// </summary>
    internal static class HavenlinePremiumFlameMeshFactory
    {
        internal const string FlameTongueMeshPath =
            "Assets/Havenline/Art/Production/Environment/Premium/HAVENLINE_FurnaceFlameTongue.asset";
        internal const string EmberMeshPath =
            "Assets/Havenline/Art/Production/Environment/Premium/HAVENLINE_FurnaceEmber.asset";

        internal static void Ensure()
        {
            HavenlinePremiumVisualAssets.Ensure();
            var folder = Path.GetDirectoryName(FlameTongueMeshPath)?.Replace('\\', '/');
            if (!string.IsNullOrWhiteSpace(folder) && !AssetDatabase.IsValidFolder(folder))
                Directory.CreateDirectory(folder);

            if (AssetDatabase.LoadAssetAtPath<Mesh>(FlameTongueMeshPath) == null)
                AssetDatabase.CreateAsset(CreateFlameTongueMesh(), FlameTongueMeshPath);
            if (AssetDatabase.LoadAssetAtPath<Mesh>(EmberMeshPath) == null)
                AssetDatabase.CreateAsset(CreateEmberMesh(), EmberMeshPath);

            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);
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
            var outline = new[]
            {
                new Vector2(-0.31f, 0f),
                new Vector2(-0.38f, 0.18f),
                new Vector2(-0.34f, 0.46f),
                new Vector2(-0.24f, 0.78f),
                new Vector2(-0.14f, 1.06f),
                new Vector2(-0.04f, 1.28f),
                new Vector2(0f, 1.46f),
                new Vector2(0.12f, 1.20f),
                new Vector2(0.24f, 0.91f),
                new Vector2(0.34f, 0.58f),
                new Vector2(0.38f, 0.26f),
                new Vector2(0.31f, 0f)
            };
            const float halfDepth = 0.14f;
            var vertices = new List<Vector3>(outline.Length * 2 + 2);
            var uv = new List<Vector2>(outline.Length * 2 + 2);

            foreach (var point in outline)
            {
                vertices.Add(new Vector3(point.x, point.y, halfDepth));
                uv.Add(new Vector2(point.x / 0.76f + 0.5f, point.y / 1.46f));
            }
            foreach (var point in outline)
            {
                vertices.Add(new Vector3(point.x, point.y, -halfDepth));
                uv.Add(new Vector2(1f - (point.x / 0.76f + 0.5f), point.y / 1.46f));
            }

            var frontCenter = vertices.Count;
            vertices.Add(new Vector3(0f, 0.61f, halfDepth));
            uv.Add(new Vector2(0.5f, 0.42f));
            var backCenter = vertices.Count;
            vertices.Add(new Vector3(0f, 0.61f, -halfDepth));
            uv.Add(new Vector2(0.5f, 0.42f));

            var triangles = new List<int>(outline.Length * 12);
            for (var index = 0; index < outline.Length; index++)
            {
                var next = (index + 1) % outline.Length;
                var front = index;
                var frontNext = next;
                var back = index + outline.Length;
                var backNext = next + outline.Length;

                triangles.Add(frontCenter);
                triangles.Add(frontNext);
                triangles.Add(front);

                triangles.Add(backCenter);
                triangles.Add(back);
                triangles.Add(backNext);

                triangles.Add(front);
                triangles.Add(frontNext);
                triangles.Add(backNext);
                triangles.Add(front);
                triangles.Add(backNext);
                triangles.Add(back);
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
            const int segments = 12;
            const int rings = 5;
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
                    var angle = u * Mathf.PI * 2f;
                    vertices.Add(new Vector3(Mathf.Cos(angle) * radius, y, Mathf.Sin(angle) * radius));
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
    }
}
