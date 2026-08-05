using System;
using UnityEditor;
using UnityEngine;
using UnityEngine.Rendering;

namespace Havenline.Editor
{
    /// <summary>
    /// Builds a stable volumetric firebox from HAVENLINE-owned meshes and emissive materials.
    /// Three staggered solid flame tongues sit above a glowing ember bed; particles remain
    /// secondary detail and are not part of this authored core.
    /// </summary>
    internal static class HavenlinePremiumFlameAuthoring
    {
        internal static HavenlineFlamePulse Build(Transform parent)
        {
            if (parent == null)
                throw new ArgumentNullException(nameof(parent));

            HavenlinePremiumVisualAssets.Ensure();
            HavenlinePremiumFlameMeshFactory.Ensure();

            var root = new GameObject("FurnaceFlameVisual");
            root.transform.SetParent(parent, false);
            root.transform.localPosition = new Vector3(0f, 0.36f, 1.10f);
            root.transform.localRotation = Quaternion.Euler(8f, 0f, 0f);
            root.transform.localScale = new Vector3(0.92f, 0.82f, 0.72f);

            CreateEmberBed(root.transform);
            CreateTonguePair(
                root.transform,
                "Main",
                new Vector3(0f, 0.01f, 0.01f),
                new Vector3(0.82f, 0.80f, 0.72f),
                Quaternion.Euler(0f, -3f, -2f));
            CreateTonguePair(
                root.transform,
                "Left",
                new Vector3(-0.29f, -0.08f, 0.015f),
                new Vector3(0.56f, 0.58f, 0.58f),
                Quaternion.Euler(0f, 7f, -14f));
            CreateTonguePair(
                root.transform,
                "Right",
                new Vector3(0.28f, -0.11f, -0.015f),
                new Vector3(0.50f, 0.52f, 0.54f),
                Quaternion.Euler(0f, -8f, 13f));

            return root.AddComponent<HavenlineFlamePulse>();
        }

        private static void CreateTonguePair(
            Transform parent,
            string suffix,
            Vector3 localPosition,
            Vector3 outerScale,
            Quaternion localRotation)
        {
            var mesh = HavenlinePremiumFlameMeshFactory.RequireFlameTongue();
            CreateLayer(
                parent,
                "FlameTongue_" + suffix + "_Outer",
                mesh,
                HavenlinePremiumVisualAssets.FlameOuterMaterialPath,
                localPosition,
                outerScale,
                localRotation);
            CreateLayer(
                parent,
                "FlameTongue_" + suffix + "_Inner",
                mesh,
                HavenlinePremiumVisualAssets.FlameInnerMaterialPath,
                localPosition + new Vector3(0f, -0.015f, 0.095f),
                Vector3.Scale(outerScale, new Vector3(0.53f, 0.62f, 0.70f)),
                localRotation);
        }

        private static void CreateEmberBed(Transform parent)
        {
            var emberRoot = new GameObject("EmberBed").transform;
            emberRoot.SetParent(parent, false);
            emberRoot.localPosition = new Vector3(0f, 0.015f, 0.03f);

            var positions = new[]
            {
                new Vector3(-0.34f, 0f, 0.02f),
                new Vector3(-0.19f, 0.015f, 0.10f),
                new Vector3(-0.08f, -0.005f, -0.06f),
                new Vector3(0.08f, 0.01f, 0.08f),
                new Vector3(0.20f, -0.005f, -0.04f),
                new Vector3(0.34f, 0.005f, 0.06f),
                new Vector3(0f, 0.035f, -0.10f)
            };
            var scales = new[]
            {
                new Vector3(0.30f, 0.16f, 0.26f),
                new Vector3(0.26f, 0.14f, 0.24f),
                new Vector3(0.28f, 0.15f, 0.25f),
                new Vector3(0.31f, 0.17f, 0.27f),
                new Vector3(0.27f, 0.14f, 0.24f),
                new Vector3(0.29f, 0.15f, 0.26f),
                new Vector3(0.32f, 0.18f, 0.28f)
            };
            var mesh = HavenlinePremiumFlameMeshFactory.RequireEmber();
            for (var index = 0; index < positions.Length; index++)
            {
                CreateLayer(
                    emberRoot,
                    $"Ember_{index + 1:00}",
                    mesh,
                    index % 3 == 0
                        ? HavenlinePremiumVisualAssets.FlameInnerMaterialPath
                        : HavenlinePremiumVisualAssets.FlameOuterMaterialPath,
                    positions[index],
                    scales[index],
                    Quaternion.Euler(index * 9f, index * 31f, index * 7f));
            }
        }

        private static void CreateLayer(
            Transform parent,
            string name,
            Mesh mesh,
            string materialPath,
            Vector3 localPosition,
            Vector3 localScale,
            Quaternion localRotation)
        {
            var material = AssetDatabase.LoadAssetAtPath<Material>(materialPath);
            if (mesh == null)
                throw new InvalidOperationException("HAVENLINE furnace core mesh is missing.");
            if (material == null)
                throw new InvalidOperationException("HAVENLINE core material is missing: " + materialPath);

            var layer = new GameObject(name, typeof(MeshFilter), typeof(MeshRenderer));
            layer.transform.SetParent(parent, false);
            layer.transform.localPosition = localPosition;
            layer.transform.localRotation = localRotation;
            layer.transform.localScale = localScale;
            layer.GetComponent<MeshFilter>().sharedMesh = mesh;
            var renderer = layer.GetComponent<MeshRenderer>();
            renderer.sharedMaterial = material;
            renderer.shadowCastingMode = ShadowCastingMode.Off;
            renderer.receiveShadows = false;
            renderer.lightProbeUsage = LightProbeUsage.BlendProbes;
            renderer.reflectionProbeUsage = ReflectionProbeUsage.Off;
            renderer.rendererPriority = 2;
        }
    }
}
