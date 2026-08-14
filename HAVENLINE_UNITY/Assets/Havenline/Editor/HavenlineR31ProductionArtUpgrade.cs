using System;
using System.Collections.Generic;
using System.IO;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Havenline.Editor
{
    /// <summary>
    /// R31 replaces the last visibly prototype-grade environment sources with deterministic,
    /// authored production meshes. It deliberately stays asset-based: no visible Unity primitive
    /// is introduced, the meshes are reusable across instances, and Android draw-call cost stays
    /// bounded while silhouette/detail quality increases materially.
    /// </summary>
    internal static class HavenlineR31ProductionArtUpgrade
    {
        private const string StructureRoot = "Assets/Havenline/Art/Production/Structures";
        private const string EnvironmentRoot = "Assets/Havenline/Art/Production/Environment";

        internal static void ApplyToGeneratedProduction()
        {
            WritePine(EnvironmentRoot + "/HAVENLINE_Pine_A.obj", 0);
            WritePine(EnvironmentRoot + "/HAVENLINE_Pine_B.obj", 1);
            for (var level = 1; level <= 4; level++)
                WriteFurnace(StructureRoot + $"/HAVENLINE_Furnace_L{level}.obj", level);

            HavenlineStudioGeometry.ConfigureModelImporter(EnvironmentRoot + "/HAVENLINE_Pine_A.obj");
            HavenlineStudioGeometry.ConfigureModelImporter(EnvironmentRoot + "/HAVENLINE_Pine_B.obj");
            for (var level = 1; level <= 4; level++)
                HavenlineStudioGeometry.ConfigureModelImporter(StructureRoot + $"/HAVENLINE_Furnace_L{level}.obj");

            ReplaceMeshAsset(
                HavenlinePremiumVisualAssets.SnowFieldPath,
                CreateSnowField("HAVENLINE_R31LayeredSnowField", 64, 7, 15.2f, 15.0f));
            ReplaceMeshAsset(
                HavenlinePremiumVisualAssets.ShelterShellPath,
                CreateShelterShell("HAVENLINE_R31ShelterShell", 3.95f, 3.36f));
            ReplaceMeshAsset(
                HavenlinePremiumVisualAssets.ShelterSnowCapPath,
                CreateShelterSnowCap("HAVENLINE_R31ShelterSnowCap", 3.95f, 3.42f));
            ReplaceMeshAsset(
                HavenlinePremiumVisualAssets.FurnaceBodyPath,
                CreateRoundedMachineBody("HAVENLINE_R31FurnaceBody", 2.05f, 1.78f, 2.06f, 16));
            ReplaceMeshAsset(
                HavenlinePremiumVisualAssets.FurnaceHoodPath,
                CreateRoundedMachineBody("HAVENLINE_R31FurnaceHood", 2.32f, 1.98f, 0.62f, 16));
            ReplaceMeshAsset(
                HavenlinePremiumVisualAssets.FurnaceChimneyPath,
                CreateRoundedMachineBody("HAVENLINE_R31FurnaceChimney", 0.72f, 0.72f, 1.58f, 14));

            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);

            // Re-save the authored scene so every existing presentation pass resolves the new
            // mesh assets by path and recreates its transient visual dressing deterministically.
            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            EditorSceneManager.SaveScene(scene, Reference.ScenePath);
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);
        }

        private static void WritePine(string path, int variant)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                var yawOffset = variant * 19f;
                obj.Begin("Trunk", "Wood");
                obj.AddCone(new Vector3(0f, 1.65f, 0f), 0.28f, 0.15f, 3.30f, 12,
                    Quaternion.Euler(0f, yawOffset, variant == 0 ? -2.5f : 3.5f));

                // Six irregular bough tiers replace the old four stacked cones. Each tier uses
                // discrete angled branches and soft needle masses, so the tree reads as a tree
                // from the close isometric camera while remaining one shared imported mesh.
                for (var tier = 0; tier < 6; tier++)
                {
                    var y = 0.95f + tier * 0.53f;
                    var reach = 1.46f - tier * 0.17f + (variant == 1 ? 0.06f : 0f);
                    var branchCount = tier < 2 ? 7 : 6;
                    for (var branch = 0; branch < branchCount; branch++)
                    {
                        var angle = yawOffset + tier * 23f + branch * (360f / branchCount);
                        var direction = Quaternion.Euler(0f, angle, 0f) * Vector3.forward;
                        var center = new Vector3(direction.x * reach * 0.48f, y, direction.z * reach * 0.48f);
                        var downTilt = 69f + tier * 1.8f + ((branch + tier + variant) % 3 - 1) * 4f;

                        obj.Begin($"Bough_{tier}_{branch}", tier % 2 == 0 ? "Pine" : "PineLight");
                        obj.AddCone(
                            center,
                            0.19f + (5 - tier) * 0.014f,
                            0.035f,
                            reach * 0.98f,
                            9,
                            Quaternion.Euler(downTilt, angle, 0f));

                        var crown = new Vector3(direction.x * reach * 0.72f,
                            y + 0.08f + ((branch + tier) % 2) * 0.06f,
                            direction.z * reach * 0.72f);
                        obj.Begin($"Needles_{tier}_{branch}", branch % 2 == 0 ? "Pine" : "PineLight");
                        obj.AddSphere(crown,
                            new Vector3(0.42f + (5 - tier) * 0.025f, 0.24f, 0.60f + (5 - tier) * 0.035f),
                            5, 10, Quaternion.Euler(0f, angle, 0f));

                        if ((branch + tier + variant) % 2 == 0)
                        {
                            obj.Begin($"SnowLoad_{tier}_{branch}", "Snow");
                            obj.AddSphere(crown + Vector3.up * 0.17f,
                                new Vector3(0.34f + (5 - tier) * 0.018f, 0.085f, 0.46f + (5 - tier) * 0.025f),
                                4, 10, Quaternion.Euler(0f, angle, 0f));
                        }
                    }
                }

                obj.Begin("CrownSpire", "PineLight");
                obj.AddCone(new Vector3(0.03f, 4.20f, -0.02f), 0.48f, 0.02f, 1.65f, 11,
                    Quaternion.Euler(variant == 0 ? 1.5f : -2f, yawOffset + 11f, variant == 0 ? -3f : 2f));
                obj.Begin("CrownSnow", "Snow");
                obj.AddSphere(new Vector3(-0.02f, 4.28f, 0.02f), new Vector3(0.34f, 0.10f, 0.31f),
                    4, 10, Quaternion.Euler(0f, yawOffset, 0f));
            });
        }

        private static void WriteFurnace(string path, int level)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                var growth = 1f + (level - 1) * 0.10f;
                obj.Begin("FoundationStone", "Stone");
                obj.AddCylinder(new Vector3(0f, 0.18f, 0f), 0.86f * growth, 0.36f, 16, Quaternion.identity);
                obj.Begin("LowerFlare", "Metal");
                obj.AddCone(new Vector3(0f, 0.56f, 0f), 0.70f * growth, 0.60f * growth, 0.56f, 16, Quaternion.identity);
                obj.Begin("BoilerBody", "Metal");
                obj.AddCylinder(new Vector3(0f, 1.20f, 0f), 0.59f * growth, 0.98f + level * 0.10f, 16, Quaternion.identity);
                obj.Begin("Shoulder", "MetalLight");
                obj.AddCone(new Vector3(0f, 1.78f + level * 0.05f, 0f), 0.62f * growth, 0.38f * growth, 0.34f, 16, Quaternion.identity);

                obj.Begin("DoorSurround", "MetalLight");
                obj.AddBox(new Vector3(0f, 0.98f, -0.61f * growth),
                    new Vector3(0.76f, 0.74f, 0.13f), Quaternion.identity);
                obj.Begin("FireboxDoor", "Navy");
                obj.AddBox(new Vector3(0f, 0.98f, -0.70f * growth),
                    new Vector3(0.58f, 0.57f, 0.10f), Quaternion.identity);
                obj.Begin("FireboxGlass", "Orange");
                obj.AddBox(new Vector3(0f, 1.01f, -0.765f * growth),
                    new Vector3(0.37f, 0.32f, 0.045f), Quaternion.identity);

                obj.Begin("DoorBolts", "Amber");
                foreach (var bolt in new[]
                         {
                             new Vector3(-0.32f,0.71f,-0.79f * growth), new Vector3(0.32f,0.71f,-0.79f * growth),
                             new Vector3(-0.32f,1.25f,-0.79f * growth), new Vector3(0.32f,1.25f,-0.79f * growth)
                         })
                    obj.AddSphere(bolt, new Vector3(0.055f, 0.055f, 0.035f), 4, 8, Quaternion.identity);

                obj.Begin("BandLower", "MetalLight");
                obj.AddCylinder(new Vector3(0f, 0.75f, 0f), 0.635f * growth, 0.10f, 16, Quaternion.identity);
                obj.Begin("BandUpper", "MetalLight");
                obj.AddCylinder(new Vector3(0f, 1.55f + level * 0.04f, 0f), 0.635f * growth, 0.10f, 16, Quaternion.identity);

                obj.Begin("Chimney", "Navy");
                obj.AddCylinder(new Vector3(0f, 2.30f + level * 0.12f, 0.04f),
                    0.22f + level * 0.018f, 1.12f + level * 0.15f, 14, Quaternion.identity);
                obj.Begin("ChimneyCollar", "MetalLight");
                obj.AddCylinder(new Vector3(0f, 1.86f + level * 0.06f, 0.04f),
                    0.31f + level * 0.018f, 0.12f, 14, Quaternion.identity);
                obj.Begin("RainCap", "MetalLight");
                obj.AddCone(new Vector3(0f, 2.91f + level * 0.19f, 0.04f),
                    0.38f + level * 0.02f, 0.24f, 0.18f, 14, Quaternion.identity);

                for (var side = -1; side <= 1; side += 2)
                {
                    obj.Begin(side < 0 ? "SidePipeL" : "SidePipeR", "MetalLight");
                    obj.AddCylinder(new Vector3(side * 0.67f * growth, 1.20f, 0.04f),
                        0.10f, 0.70f + level * 0.08f, 12, Quaternion.identity);
                    obj.Begin(side < 0 ? "ValveL" : "ValveR", "Amber");
                    obj.AddCylinder(new Vector3(side * 0.73f * growth, 1.40f, -0.10f),
                        0.16f, 0.08f, 10, Quaternion.Euler(90f, 0f, 0f));
                }

                if (level >= 2)
                {
                    obj.Begin("ServiceTankL", "Metal");
                    obj.AddCylinder(new Vector3(-0.78f * growth, 0.82f, 0.08f), 0.21f, 0.64f, 12, Quaternion.identity);
                    obj.Begin("ServiceTankR", "Metal");
                    obj.AddCylinder(new Vector3(0.78f * growth, 0.82f, 0.08f), 0.21f, 0.64f, 12, Quaternion.identity);
                }

                if (level >= 3)
                {
                    obj.Begin("HeatFins", "MetalLight");
                    for (var fin = 0; fin < 8; fin++)
                    {
                        var angle = fin * 45f;
                        var direction = Quaternion.Euler(0f, angle, 0f) * Vector3.forward;
                        obj.AddBox(new Vector3(direction.x * 0.70f * growth, 1.38f, direction.z * 0.70f * growth),
                            new Vector3(0.09f, 0.54f, 0.26f), Quaternion.Euler(0f, angle, 0f));
                    }
                }

                if (level >= 4)
                {
                    obj.Begin("TopBeacon", "Orange");
                    obj.AddSphere(new Vector3(0f, 3.13f, 0.04f), new Vector3(0.18f, 0.14f, 0.18f),
                        5, 10, Quaternion.identity);
                }
            });
        }

        private static Mesh CreateSnowField(string name, int segments, int rings, float radiusX, float radiusZ)
        {
            var vertices = new List<Vector3> { new(0f, 0.11f, 0f) };
            var uvs = new List<Vector2> { new(0.5f, 0.5f) };
            for (var ring = 1; ring <= rings; ring++)
            {
                var fraction = ring / (float)rings;
                for (var segment = 0; segment < segments; segment++)
                {
                    var angle = segment * Mathf.PI * 2f / segments;
                    var irregularity = 1f + Mathf.Sin(angle * 3f + ring * 1.19f) * 0.018f +
                                       Mathf.Sin(angle * 7f + ring * 0.71f) * 0.012f;
                    var radial = fraction * irregularity;
                    var broad = Mathf.Sin(angle * 2f + fraction * 5.7f) * 0.045f;
                    var fine = Mathf.Sin(angle * 9f + ring * 1.31f) * 0.018f;
                    var campFlatten = Mathf.SmoothStep(0f, 1f, Mathf.InverseLerp(0.22f, 0.58f, fraction));
                    var edgeDrift = Mathf.SmoothStep(0f, 1f, Mathf.InverseLerp(0.70f, 1f, fraction)) *
                                    (0.08f + Mathf.Sin(angle * 5f) * 0.04f);
                    var height = 0.11f + (broad + fine) * campFlatten + edgeDrift;
                    var x = Mathf.Cos(angle) * radiusX * radial;
                    var z = Mathf.Sin(angle) * radiusZ * radial;
                    vertices.Add(new Vector3(x, height, z));
                    uvs.Add(new Vector2(x / (radiusX * 2f) + 0.5f, z / (radiusZ * 2f) + 0.5f));
                }
            }

            var triangles = new List<int>();
            for (var segment = 0; segment < segments; segment++)
            {
                var next = (segment + 1) % segments;
                triangles.Add(0); triangles.Add(1 + next); triangles.Add(1 + segment);
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
                    triangles.Add(inner); triangles.Add(outerNext); triangles.Add(outer);
                    triangles.Add(inner); triangles.Add(innerNext); triangles.Add(outerNext);
                }
            }
            return BuildMesh(name, vertices, triangles, uvs);
        }

        private static Mesh CreateShelterShell(string name, float width, float depth)
        {
            var halfWidth = width * 0.5f;
            var profile = new[]
            {
                new Vector2(-halfWidth,0f), new Vector2(-halfWidth * 0.96f,0.48f),
                new Vector2(-halfWidth * 0.83f,1.18f), new Vector2(-halfWidth * 0.58f,1.92f),
                new Vector2(-halfWidth * 0.24f,2.48f), new Vector2(0f,2.68f),
                new Vector2(halfWidth * 0.24f,2.48f), new Vector2(halfWidth * 0.58f,1.92f),
                new Vector2(halfWidth * 0.83f,1.18f), new Vector2(halfWidth * 0.96f,0.48f),
                new Vector2(halfWidth,0f)
            };
            return ExtrudeClosedProfile(name, profile, depth);
        }

        private static Mesh CreateShelterSnowCap(string name, float width, float depth)
        {
            var halfWidth = width * 0.5f;
            var outer = new[]
            {
                new Vector2(-halfWidth * 0.82f,1.24f), new Vector2(-halfWidth * 0.59f,1.99f),
                new Vector2(-halfWidth * 0.25f,2.57f), new Vector2(0f,2.80f),
                new Vector2(halfWidth * 0.25f,2.57f), new Vector2(halfWidth * 0.59f,1.99f),
                new Vector2(halfWidth * 0.82f,1.24f)
            };
            var inner = new Vector2[outer.Length];
            for (var index = 0; index < outer.Length; index++)
                inner[index] = new Vector2(outer[index].x * 0.985f, outer[index].y - 0.16f);
            return ExtrudeRoofRibbon(name, outer, inner, depth);
        }

        private static Mesh CreateRoundedMachineBody(
            string name, float width, float depth, float height, int segments)
        {
            segments = Mathf.Max(12, segments);
            var vertices = new List<Vector3>();
            var uvs = new List<Vector2>();
            for (var level = 0; level < 4; level++)
            {
                var t = level / 3f;
                var y = t * height;
                var shoulder = level is 0 or 3 ? 0.92f : 1f;
                for (var segment = 0; segment < segments; segment++)
                {
                    var angle = segment * Mathf.PI * 2f / segments;
                    var x = Mathf.Cos(angle) * width * 0.5f * shoulder;
                    var z = Mathf.Sin(angle) * depth * 0.5f * shoulder;
                    vertices.Add(new Vector3(x, y, z));
                    uvs.Add(new Vector2(segment / (float)segments, t));
                }
            }

            var triangles = new List<int>();
            for (var level = 0; level < 3; level++)
            {
                for (var segment = 0; segment < segments; segment++)
                {
                    var next = (segment + 1) % segments;
                    var a = level * segments + segment;
                    var b = level * segments + next;
                    var c = (level + 1) * segments + next;
                    var d = (level + 1) * segments + segment;
                    triangles.Add(a); triangles.Add(c); triangles.Add(d);
                    triangles.Add(a); triangles.Add(b); triangles.Add(c);
                }
            }
            AddCap(vertices, uvs, triangles, 0, segments, false, 0f);
            AddCap(vertices, uvs, triangles, 3 * segments, segments, true, height);
            return BuildMesh(name, vertices, triangles, uvs);
        }

        private static Mesh ExtrudeClosedProfile(string name, Vector2[] profile, float depth)
        {
            var halfDepth = depth * 0.5f;
            var vertices = new List<Vector3>();
            var uvs = new List<Vector2>();
            for (var side = 0; side < 2; side++)
            {
                var z = side == 0 ? halfDepth : -halfDepth;
                for (var index = 0; index < profile.Length; index++)
                {
                    vertices.Add(new Vector3(profile[index].x, profile[index].y, z));
                    uvs.Add(new Vector2(index / (float)(profile.Length - 1), side));
                }
            }
            var triangles = new List<int>();
            for (var index = 0; index < profile.Length - 1; index++)
            {
                var a = index;
                var b = index + 1;
                var c = profile.Length + index + 1;
                var d = profile.Length + index;
                triangles.Add(a); triangles.Add(c); triangles.Add(d);
                triangles.Add(a); triangles.Add(b); triangles.Add(c);
            }
            // Floor closes the shell without creating a visible triangular front wall; front/back
            // stay open enough for the dark insulated doorway treatment to read with depth.
            var frontLeft = 0;
            var frontRight = profile.Length - 1;
            var backLeft = profile.Length;
            var backRight = profile.Length * 2 - 1;
            triangles.Add(frontLeft); triangles.Add(backRight); triangles.Add(backLeft);
            triangles.Add(frontLeft); triangles.Add(frontRight); triangles.Add(backRight);
            return BuildMesh(name, vertices, triangles, uvs);
        }

        private static Mesh ExtrudeRoofRibbon(string name, Vector2[] outer, Vector2[] inner, float depth)
        {
            var halfDepth = depth * 0.5f;
            var vertices = new List<Vector3>();
            var uvs = new List<Vector2>();
            foreach (var z in new[] { halfDepth, -halfDepth })
            {
                for (var index = 0; index < outer.Length; index++)
                {
                    vertices.Add(new Vector3(outer[index].x, outer[index].y, z));
                    uvs.Add(new Vector2(index / (float)(outer.Length - 1), 1f));
                }
                for (var index = 0; index < inner.Length; index++)
                {
                    vertices.Add(new Vector3(inner[index].x, inner[index].y, z));
                    uvs.Add(new Vector2(index / (float)(inner.Length - 1), 0f));
                }
            }

            var stride = outer.Length + inner.Length;
            var triangles = new List<int>();
            for (var side = 0; side < 2; side++)
            {
                var offset = side * stride;
                for (var index = 0; index < outer.Length - 1; index++)
                {
                    var o0 = offset + index;
                    var o1 = offset + index + 1;
                    var i0 = offset + outer.Length + index;
                    var i1 = offset + outer.Length + index + 1;
                    if (side == 0)
                    {
                        triangles.Add(o0); triangles.Add(o1); triangles.Add(i1);
                        triangles.Add(o0); triangles.Add(i1); triangles.Add(i0);
                    }
                    else
                    {
                        triangles.Add(o0); triangles.Add(i1); triangles.Add(o1);
                        triangles.Add(o0); triangles.Add(i0); triangles.Add(i1);
                    }
                }
            }

            // Connect outer and inner front/back edges so the snow reads as a padded layer.
            for (var index = 0; index < outer.Length - 1; index++)
            {
                var front0 = index;
                var front1 = index + 1;
                var back0 = stride + index;
                var back1 = stride + index + 1;
                triangles.Add(front0); triangles.Add(back1); triangles.Add(back0);
                triangles.Add(front0); triangles.Add(front1); triangles.Add(back1);

                var innerFront0 = outer.Length + index;
                var innerFront1 = outer.Length + index + 1;
                var innerBack0 = stride + outer.Length + index;
                var innerBack1 = stride + outer.Length + index + 1;
                triangles.Add(innerFront0); triangles.Add(innerBack0); triangles.Add(innerBack1);
                triangles.Add(innerFront0); triangles.Add(innerBack1); triangles.Add(innerFront1);
            }
            return BuildMesh(name, vertices, triangles, uvs);
        }

        private static void AddCap(
            List<Vector3> vertices, List<Vector2> uvs, List<int> triangles,
            int ringStart, int segments, bool top, float y)
        {
            var center = vertices.Count;
            vertices.Add(new Vector3(0f, y, 0f));
            uvs.Add(new Vector2(0.5f, 0.5f));
            for (var segment = 0; segment < segments; segment++)
            {
                var next = (segment + 1) % segments;
                if (top)
                {
                    triangles.Add(center); triangles.Add(ringStart + segment); triangles.Add(ringStart + next);
                }
                else
                {
                    triangles.Add(center); triangles.Add(ringStart + next); triangles.Add(ringStart + segment);
                }
            }
        }

        private static Mesh BuildMesh(string name, List<Vector3> vertices, List<int> triangles, List<Vector2> uvs)
        {
            var mesh = new Mesh { name = name };
            mesh.SetVertices(vertices);
            mesh.SetTriangles(triangles, 0, true);
            if (uvs != null && uvs.Count == vertices.Count)
                mesh.SetUVs(0, uvs);
            mesh.RecalculateNormals(60f);
            mesh.RecalculateTangents();
            mesh.RecalculateBounds();
            return mesh;
        }

        private static void ReplaceMeshAsset(string path, Mesh mesh)
        {
            var folder = Path.GetDirectoryName(path)?.Replace('\\', '/');
            if (!string.IsNullOrEmpty(folder))
                EnsureAssetFolder(folder);
            if (AssetDatabase.LoadMainAssetAtPath(path) != null)
                AssetDatabase.DeleteAsset(path);
            AssetDatabase.CreateAsset(mesh, path);
        }

        private static void EnsureAssetFolder(string path)
        {
            if (AssetDatabase.IsValidFolder(path))
                return;
            var parts = path.Split('/');
            var current = parts[0];
            for (var index = 1; index < parts.Length; index++)
            {
                var next = current + "/" + parts[index];
                if (!AssetDatabase.IsValidFolder(next))
                    AssetDatabase.CreateFolder(current, parts[index]);
                current = next;
            }
        }
    }
}
