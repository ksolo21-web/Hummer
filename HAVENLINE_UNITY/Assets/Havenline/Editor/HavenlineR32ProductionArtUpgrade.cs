using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;
using UnityEngine.SceneManagement;

namespace Havenline.Editor
{
    /// <summary>
    /// R32 is the first post-r31 human-review correction pass. It targets the shapes that still
    /// read as a prototype at gameplay distance: ring-like pines, wedge shelters, clipped-white
    /// snow and an under-dressed camp perimeter. All visible geometry comes from deterministic
    /// production meshes/assets; no Unity primitive or placeholder is introduced.
    /// </summary>
    internal static class HavenlineR32ProductionArtUpgrade
    {
        private const string ProductionRoot = "Assets/Havenline/Art/Production";
        private const string StructureRoot = ProductionRoot + "/Structures";
        private const string EnvironmentRoot = ProductionRoot + "/Environment";
        private const string PropsRoot = ProductionRoot + "/Props";
        private const string ResourceRoot = ProductionRoot + "/Resources";
        private const string MaterialRoot = ProductionRoot + "/Materials";
        private const string PremiumRoot = EnvironmentRoot + "/Premium";
        private const string DressingRootName = "HAVENLINE_R32CampDressing";
        private const string ShelterRibPath = PremiumRoot + "/HAVENLINE_R32ShelterRib.asset";

        internal static void ApplyToGeneratedProduction()
        {
            WritePine(EnvironmentRoot + "/HAVENLINE_Pine_A.obj", 0);
            WritePine(EnvironmentRoot + "/HAVENLINE_Pine_B.obj", 1);
            HavenlineStudioGeometry.ConfigureModelImporter(EnvironmentRoot + "/HAVENLINE_Pine_A.obj");
            HavenlineStudioGeometry.ConfigureModelImporter(EnvironmentRoot + "/HAVENLINE_Pine_B.obj");

            ReplaceMeshAsset(
                HavenlinePremiumVisualAssets.SnowFieldPath,
                CreateSnowField("HAVENLINE_R32SculptedSnowField", 72, 9, 15.4f, 15.2f));
            ReplaceMeshAsset(
                HavenlinePremiumVisualAssets.ShelterShellPath,
                CreateArchedShelterShell("HAVENLINE_R32ArchedShelterShell", 4.10f, 3.55f));
            ReplaceMeshAsset(
                HavenlinePremiumVisualAssets.ShelterSnowCapPath,
                CreateArchedShelterSnowCap("HAVENLINE_R32ArchedShelterSnowCap", 4.10f, 3.62f));
            ReplaceMeshAsset(
                ShelterRibPath,
                CreateShelterRib("HAVENLINE_R32ShelterRib", 4.18f, 0.085f));

            TuneMaterial(HavenlinePremiumVisualAssets.PaleSnowMaterialPath,
                new Color(0.79f, 0.87f, 0.92f, 1f), 0.19f);
            TuneMaterial(HavenlinePremiumVisualAssets.SnowPathMaterialPath,
                new Color(0.49f, 0.66f, 0.76f, 1f), 0.25f);
            TuneMaterial(HavenlinePremiumVisualAssets.ShelterFabricMaterialPath,
                new Color(0.028f, 0.095f, 0.155f, 1f), 0.16f);

            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);

            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            ApplyScenePresentation(scene);
            EditorSceneManager.MarkSceneDirty(scene);
            EditorSceneManager.SaveScene(scene, Reference.ScenePath);
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);
        }

        private static void WritePine(string path, int variant)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                var baseYaw = variant == 0 ? 7f : 31f;
                obj.Begin("Trunk", "Wood");
                obj.AddCone(new Vector3(0f, 1.75f, 0f), 0.30f, 0.13f, 3.50f, 14,
                    Quaternion.Euler(variant == 0 ? 1.5f : -2.5f, baseYaw, variant == 0 ? -2f : 3f));

                for (var tier = 0; tier < 5; tier++)
                {
                    var branchCount = tier < 2 ? 7 : tier < 4 ? 6 : 5;
                    var y = 0.98f + tier * 0.62f;
                    var baseReach = 1.52f - tier * 0.22f;
                    for (var branch = 0; branch < branchCount; branch++)
                    {
                        var wobble = ((branch * 37 + tier * 17 + variant * 29) % 19 - 9) * 1.35f;
                        var angle = baseYaw + tier * 33f + branch * (360f / branchCount) + wobble;
                        var reachScale = 0.86f + ((branch * 11 + tier * 7 + variant * 5) % 9) * 0.035f;
                        var reach = baseReach * reachScale;
                        var direction = Quaternion.Euler(0f, angle, 0f) * Vector3.forward;
                        var branchCenter = new Vector3(direction.x * reach * 0.46f,
                            y + ((branch + tier) % 3 - 1) * 0.045f,
                            direction.z * reach * 0.46f);

                        obj.Begin($"Branch_{tier}_{branch}", "Wood");
                        obj.AddCone(branchCenter, 0.105f + (4 - tier) * 0.008f, 0.025f,
                            reach * 1.08f, 8,
                            Quaternion.Euler(67f + ((branch + tier) % 3) * 4f, angle, 0f));

                        var tip = new Vector3(direction.x * reach * 0.74f,
                            y + 0.10f + ((branch + tier) % 2) * 0.08f,
                            direction.z * reach * 0.74f);
                        var inner = new Vector3(direction.x * reach * 0.48f,
                            y + 0.18f - (branch % 2) * 0.06f,
                            direction.z * reach * 0.48f);
                        var scale = 1f - tier * 0.085f;

                        obj.Begin($"NeedleTip_{tier}_{branch}", branch % 3 == 0 ? "PineLight" : "Pine");
                        obj.AddSphere(tip,
                            new Vector3(0.36f * scale, 0.22f * scale, 0.64f * scale),
                            6, 12, Quaternion.Euler(0f, angle, (branch % 2 == 0 ? 8f : -7f)));
                        obj.Begin($"NeedleInner_{tier}_{branch}", branch % 2 == 0 ? "Pine" : "PineLight");
                        obj.AddSphere(inner,
                            new Vector3(0.31f * scale, 0.25f * scale, 0.50f * scale),
                            6, 12, Quaternion.Euler(0f, angle + 12f, (branch % 3 - 1) * 6f));

                        if ((branch + tier + variant) % 3 != 1)
                        {
                            obj.Begin($"SnowLoad_{tier}_{branch}", "Snow");
                            obj.AddSphere(tip + Vector3.up * (0.16f * scale),
                                new Vector3(0.27f * scale, 0.065f, 0.46f * scale),
                                5, 12, Quaternion.Euler(0f, angle, 0f));
                        }
                    }
                }

                obj.Begin("CrownNeedles", "PineLight");
                obj.AddSphere(new Vector3(0.02f, 3.95f, -0.03f), new Vector3(0.48f, 0.70f, 0.46f),
                    7, 14, Quaternion.Euler(0f, baseYaw + 17f, variant == 0 ? -4f : 4f));
                obj.Begin("CrownSpire", "Pine");
                obj.AddCone(new Vector3(-0.02f, 4.55f, 0.03f), 0.37f, 0.018f, 1.30f, 12,
                    Quaternion.Euler(variant == 0 ? 2f : -2f, baseYaw + 9f, variant == 0 ? -3f : 3f));
                obj.Begin("CrownSnow", "Snow");
                obj.AddSphere(new Vector3(0f, 4.17f, 0f), new Vector3(0.31f, 0.075f, 0.30f),
                    5, 12, Quaternion.identity);
            });
        }

        private static Mesh CreateSnowField(string name, int segments, int rings, float radiusX, float radiusZ)
        {
            var vertices = new List<Vector3> { new Vector3(0f, 0.09f, 0f) };
            var uvs = new List<Vector2> { new Vector2(0.5f, 0.5f) };
            for (var ring = 1; ring <= rings; ring++)
            {
                var fraction = ring / (float)rings;
                for (var segment = 0; segment < segments; segment++)
                {
                    var angle = segment * Mathf.PI * 2f / segments;
                    var radialNoise = 1f + Mathf.Sin(angle * 3f + ring * 0.79f) * 0.014f +
                                      Mathf.Sin(angle * 11f + ring * 1.37f) * 0.007f;
                    var radial = fraction * radialNoise;
                    var x = Mathf.Cos(angle) * radiusX * radial;
                    var z = Mathf.Sin(angle) * radiusZ * radial;
                    var campMask = Mathf.SmoothStep(0f, 1f, Mathf.InverseLerp(0.26f, 0.58f, fraction));
                    var dune = (Mathf.Sin(x * 0.44f + z * 0.17f) * 0.052f +
                                Mathf.Sin(z * 0.57f - x * 0.13f) * 0.038f) * campMask;
                    var windRipple = Mathf.Sin((x + z) * 1.22f) * 0.012f * campMask;
                    var edge = Mathf.SmoothStep(0f, 1f, Mathf.InverseLerp(0.68f, 1f, fraction));
                    var drift = edge * (0.10f + 0.075f * Mathf.Max(0f, Mathf.Sin(angle * 4f + 0.6f)));
                    var height = 0.09f + dune + windRipple + drift;
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
                    var a = innerStart + segment;
                    var b = innerStart + next;
                    var c = outerStart + next;
                    var d = outerStart + segment;
                    triangles.Add(a); triangles.Add(c); triangles.Add(d);
                    triangles.Add(a); triangles.Add(b); triangles.Add(c);
                }
            }
            return BuildMesh(name, vertices, triangles, uvs);
        }

        private static Vector2[] ShelterProfile(float width, float extraHeight)
        {
            var halfWidth = width * 0.5f;
            const int points = 17;
            var profile = new Vector2[points];
            for (var index = 0; index < points; index++)
            {
                var t = index / (float)(points - 1);
                var x = Mathf.Lerp(-halfWidth, halfWidth, t);
                var normalized = x / halfWidth;
                var arch = Mathf.Sqrt(Mathf.Max(0f, 1f - normalized * normalized));
                var sideLift = 0.28f + 0.20f * (1f - arch);
                profile[index] = new Vector2(x, sideLift + arch * (2.46f + extraHeight));
            }
            return profile;
        }

        private static Mesh CreateArchedShelterShell(string name, float width, float depth)
        {
            return ExtrudeOpenProfile(name, ShelterProfile(width, 0f), depth);
        }

        private static Mesh CreateArchedShelterSnowCap(string name, float width, float depth)
        {
            var full = ShelterProfile(width, 0.13f);
            var outer = full.Skip(3).Take(full.Length - 6).ToArray();
            var inner = outer.Select(point => new Vector2(point.x * 0.992f, point.y - 0.15f)).ToArray();
            return ExtrudeRibbon(name, outer, inner, depth);
        }

        private static Mesh CreateShelterRib(string name, float width, float thickness)
        {
            var outer = ShelterProfile(width, 0.08f);
            var inner = outer.Select(point => new Vector2(point.x * 0.986f, point.y - thickness)).ToArray();
            return ExtrudeRibbon(name, outer, inner, 0.075f);
        }

        private static Mesh ExtrudeOpenProfile(string name, Vector2[] profile, float depth)
        {
            var halfDepth = depth * 0.5f;
            var vertices = new List<Vector3>();
            var uvs = new List<Vector2>();
            foreach (var z in new[] { halfDepth, -halfDepth })
            {
                for (var index = 0; index < profile.Length; index++)
                {
                    vertices.Add(new Vector3(profile[index].x, profile[index].y, z));
                    uvs.Add(new Vector2(index / (float)(profile.Length - 1), z > 0f ? 1f : 0f));
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
            var leftFront = 0;
            var rightFront = profile.Length - 1;
            var leftBack = profile.Length;
            var rightBack = profile.Length * 2 - 1;
            triangles.Add(leftFront); triangles.Add(rightBack); triangles.Add(leftBack);
            triangles.Add(leftFront); triangles.Add(rightFront); triangles.Add(rightBack);
            return BuildMesh(name, vertices, triangles, uvs);
        }

        private static Mesh ExtrudeRibbon(string name, Vector2[] outer, Vector2[] inner, float depth)
        {
            var halfDepth = depth * 0.5f;
            var vertices = new List<Vector3>();
            var uvs = new List<Vector2>();
            foreach (var z in new[] { halfDepth, -halfDepth })
            {
                for (var index = 0; index < outer.Length; index++)
                {
                    vertices.Add(new Vector3(outer[index].x, outer[index].y, z));
                    uvs.Add(new Vector2(index / (float)Mathf.Max(1, outer.Length - 1), 1f));
                }
                for (var index = 0; index < inner.Length; index++)
                {
                    vertices.Add(new Vector3(inner[index].x, inner[index].y, z));
                    uvs.Add(new Vector2(index / (float)Mathf.Max(1, inner.Length - 1), 0f));
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
            for (var index = 0; index < outer.Length - 1; index++)
            {
                var front0 = index;
                var front1 = index + 1;
                var back0 = stride + index;
                var back1 = stride + index + 1;
                triangles.Add(front0); triangles.Add(back1); triangles.Add(back0);
                triangles.Add(front0); triangles.Add(front1); triangles.Add(back1);
            }
            return BuildMesh(name, vertices, triangles, uvs);
        }

        private static void ApplyScenePresentation(Scene scene)
        {
            var objects = AllObjects(scene);
            var oldRoot = objects.FirstOrDefault(item => item.name == DressingRootName);
            if (oldRoot != null)
                UnityEngine.Object.DestroyImmediate(oldRoot);

            var root = new GameObject(DressingRootName);
            SceneManager.MoveGameObjectToScene(root, scene);
            var shippingRoot = scene.GetRootGameObjects()
                .FirstOrDefault(item => item.name == HavenlineCoreCrewScenePostprocessor.ShippingRootName);
            if (shippingRoot != null)
                root.transform.SetParent(shippingRoot.transform, false);

            AddShelterRibs(scene);
            AddPerimeterDressing(root.transform);
            VaryPineSilhouettes(scene);
            TuneAtmosphere(scene);
        }

        private static void AddShelterRibs(Scene scene)
        {
            var ribMesh = AssetDatabase.LoadAssetAtPath<Mesh>(ShelterRibPath);
            var ribMaterial = AssetDatabase.LoadAssetAtPath<Material>(MaterialRoot + "/HAVENLINE_MetalLight.mat");
            if (ribMesh == null || ribMaterial == null)
                return;

            foreach (var shelterName in new[] { "LeftPremiumShelter", "RightPremiumShelter" })
            {
                var shelter = AllObjects(scene).FirstOrDefault(item => item.name == shelterName);
                if (shelter == null)
                    continue;
                var existing = shelter.transform.Find("R32StructuralRibs");
                if (existing != null)
                    UnityEngine.Object.DestroyImmediate(existing.gameObject);

                var ribRoot = new GameObject("R32StructuralRibs");
                ribRoot.transform.SetParent(shelter.transform, false);
                var depthPositions = new[] { -1.42f, -0.72f, 0f, 0.72f, 1.42f };
                for (var index = 0; index < depthPositions.Length; index++)
                {
                    var rib = new GameObject($"ShelterRib_{index + 1:00}");
                    rib.transform.SetParent(ribRoot.transform, false);
                    rib.transform.localPosition = new Vector3(0f, 0.015f, depthPositions[index]);
                    var filter = rib.AddComponent<MeshFilter>();
                    filter.sharedMesh = ribMesh;
                    var renderer = rib.AddComponent<MeshRenderer>();
                    renderer.sharedMaterial = ribMaterial;
                    renderer.shadowCastingMode = ShadowCastingMode.On;
                    renderer.receiveShadows = true;
                }
            }
        }

        private static void AddPerimeterDressing(Transform parent)
        {
            var barricades = new[]
            {
                (new Vector3(-7.65f,0.02f,3.10f), 72f, 0.78f),
                (new Vector3(-5.25f,0.02f,5.15f), 35f, 0.82f),
                (new Vector3(-2.15f,0.02f,6.15f), 10f, 0.86f),
                (new Vector3(2.15f,0.02f,6.10f), -10f, 0.86f),
                (new Vector3(5.25f,0.02f,5.10f), -35f, 0.82f),
                (new Vector3(7.60f,0.02f,3.05f), -72f, 0.78f)
            };
            for (var index = 0; index < barricades.Length; index++)
            {
                var item = InstantiateModel(StructureRoot + "/HAVENLINE_Barricade.obj", parent,
                    $"R32PerimeterBarricade_{index + 1:00}");
                item.transform.SetPositionAndRotation(barricades[index].Item1,
                    Quaternion.Euler(0f, barricades[index].Item2, 0f));
                item.transform.localScale *= barricades[index].Item3;
            }

            var debris = new[]
            {
                (new Vector3(-4.95f,0.04f,2.22f), -24f),
                (new Vector3(-3.45f,0.04f,2.42f), 18f),
                (new Vector3(3.55f,0.04f,2.35f), -12f),
                (new Vector3(4.90f,0.04f,2.18f), 29f)
            };
            for (var index = 0; index < debris.Length; index++)
            {
                var path = PropsRoot + $"/HAVENLINE_SupplyDebris_{index + 1:00}.obj";
                var item = InstantiateModel(path, parent, $"R32SupplyDebris_{index + 1:00}");
                item.transform.SetPositionAndRotation(debris[index].Item1,
                    Quaternion.Euler(0f, debris[index].Item2, 0f));
                item.transform.localScale *= 0.82f + index * 0.04f;
            }

            for (var index = 0; index < 6; index++)
            {
                var path = index % 2 == 0
                    ? ResourceRoot + "/HAVENLINE_Log.obj"
                    : EnvironmentRoot + "/HAVENLINE_Rock_B.obj";
                var angle = index * Mathf.PI * 2f / 6f + 0.38f;
                var radius = 4.4f + (index % 3) * 0.35f;
                var item = InstantiateModel(path, parent, $"R32CampEdgeDetail_{index + 1:00}");
                item.transform.position = new Vector3(Mathf.Cos(angle) * radius, 0.04f, 2.25f + Mathf.Sin(angle) * 1.55f);
                item.transform.rotation = Quaternion.Euler(index % 2 == 0 ? 82f : 0f, 21f + index * 47f, 0f);
                item.transform.localScale *= index % 2 == 0 ? 0.78f : 0.62f;
            }
        }

        private static GameObject InstantiateModel(string path, Transform parent, string name)
        {
            var asset = AssetDatabase.LoadAssetAtPath<GameObject>(path);
            if (asset == null)
                throw new FileNotFoundException("HAVENLINE R32 production model is missing.", path);
            var instance = PrefabUtility.InstantiatePrefab(asset, parent) as GameObject;
            if (instance == null)
                throw new InvalidOperationException("Could not instantiate HAVENLINE R32 production model: " + path);
            instance.name = name;
            foreach (var collider in instance.GetComponentsInChildren<Collider>(true))
                UnityEngine.Object.DestroyImmediate(collider);
            return instance;
        }

        private static void VaryPineSilhouettes(Scene scene)
        {
            var pines = AllObjects(scene)
                .Where(item => item.name.Contains("Pine", StringComparison.OrdinalIgnoreCase) &&
                               item.GetComponentsInChildren<Renderer>(true).Length > 0)
                .OrderBy(item => item.name, StringComparer.Ordinal)
                .ToArray();
            for (var index = 0; index < pines.Length; index++)
            {
                var pine = pines[index];
                var variation = ((index * 13 + 7) % 11) / 10f;
                var scale = pine.transform.localScale;
                scale.x *= Mathf.Lerp(0.91f, 1.09f, variation);
                scale.z *= Mathf.Lerp(1.07f, 0.93f, variation);
                scale.y *= Mathf.Lerp(0.94f, 1.10f, ((index * 7 + 3) % 9) / 8f);
                pine.transform.localScale = scale;
                pine.transform.rotation *= Quaternion.Euler((index % 3 - 1) * 1.6f, 17f + (index * 41) % 137, (index % 5 - 2) * 0.7f);
            }
        }

        private static void TuneAtmosphere(Scene scene)
        {
            RenderSettings.ambientIntensity = Mathf.Min(RenderSettings.ambientIntensity, 0.82f);
            RenderSettings.ambientLight = new Color(0.31f, 0.38f, 0.47f);
            if (RenderSettings.fog)
                RenderSettings.fogColor = new Color(0.34f, 0.43f, 0.51f);

            foreach (var volume in AllObjects(scene).SelectMany(item => item.GetComponents<Volume>()))
            {
                var profile = volume.sharedProfile;
                if (profile == null)
                    continue;
                if (profile.TryGet<ColorAdjustments>(out var color))
                {
                    color.postExposure.Override(-0.16f);
                    color.contrast.Override(16f);
                    color.saturation.Override(5f);
                }
                if (profile.TryGet<Bloom>(out var bloom))
                {
                    bloom.intensity.Override(0.28f);
                    bloom.threshold.Override(1.03f);
                    bloom.scatter.Override(0.52f);
                }
            }
        }

        private static void TuneMaterial(string path, Color color, float smoothness)
        {
            var material = AssetDatabase.LoadAssetAtPath<Material>(path);
            if (material == null)
                return;
            if (material.HasProperty("_BaseColor"))
                material.SetColor("_BaseColor", color);
            if (material.HasProperty("_Color"))
                material.SetColor("_Color", color);
            if (material.HasProperty("_Smoothness"))
                material.SetFloat("_Smoothness", smoothness);
            EditorUtility.SetDirty(material);
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

        private static GameObject[] AllObjects(Scene scene) => scene.GetRootGameObjects()
            .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
            .Select(item => item.gameObject)
            .Distinct()
            .ToArray();
    }
}
