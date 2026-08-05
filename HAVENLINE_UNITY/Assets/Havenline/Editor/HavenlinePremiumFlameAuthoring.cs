using System;
using UnityEditor;
using UnityEngine;
using UnityEngine.Rendering;

namespace Havenline.Editor
{
    /// <summary>
    /// Builds a stable three-dimensional firebox from HAVENLINE-owned radial flame volumes.
    /// The tongues are independently rotated and depth-staggered above a broad ember bed so
    /// the result reads as fire inside the furnace, not as a bright icon pasted on its face.
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
            root.transform.localPosition = new Vector3(0f, 0.32f, 1.11f);
            root.transform.localRotation = Quaternion.Euler(5f, 0f, 0f);
            root.transform.localScale = new Vector3(0.86f, 0.78f, 0.70f);

            CreateEmberBed(root.transform);
            CreateTonguePair(
                root.transform,
                "Main",
                new Vector3(-0.025f, 0.015f, -0.035f),
                new Vector3(0.68f, 0.76f, 0.72f),
                Quaternion.Euler(1f, 21f, -4f),
                new Vector3(0.42f, 0.55f, 0.48f));
            CreateTonguePair(
                root.transform,
                "Left",
                new Vector3(-0.30f, -0.105f, 0.045f),
                new Vector3(0.50f, 0.56f, 0.58f),
                Quaternion.Euler(-2f, -28f, -15f),
                new Vector3(0.38f, 0.49f, 0.44f));
            CreateTonguePair(
                root.transform,
                "Right",
                new Vector3(0.27f, -0.145f, 0.015f),
                new Vector3(0.43f, 0.47f, 0.52f),
                Quaternion.Euler(3f, 34f, 14f),
                new Vector3(0.36f, 0.46f, 0.42f));

            return root.AddComponent<HavenlineFlamePulse>();
        }

        private static void CreateTonguePair(
            Transform parent,
            string suffix,
            Vector3 localPosition,
            Vector3 outerScale,
            Quaternion localRotation,
            Vector3 innerScaleMultiplier)
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
                localPosition + new Vector3(0.015f, -0.035f, 0.075f),
                Vector3.Scale(outerScale, innerScaleMultiplier),
                localRotation * Quaternion.Euler(0f, 11f, 2f));
        }

        private static void CreateEmberBed(Transform parent)
        {
            var emberRoot = new GameObject("EmberBed").transform;
            emberRoot.SetParent(parent, false);
            emberRoot.localPosition = new Vector3(0f, 0.03f, 0.015f);
            emberRoot.localRotation = Quaternion.Euler(-4f, 0f, 0f);

            var positions = new[]
            {
                new Vector3(-0.40f, 0.010f, 0.03f),
                new Vector3(-0.29f, 0.035f, -0.08f),
                new Vector3(-0.19f, 0.020f, 0.12f),
                new Vector3(-0.08f, 0.045f, -0.03f),
                new Vector3(0.05f, 0.025f, 0.11f),
                new Vector3(0.16f, 0.045f, -0.08f),
                new Vector3(0.27f, 0.015f, 0.09f),
                new Vector3(0.39f, 0.030f, -0.02f),
                new Vector3(0f, 0.070f, -0.13f)
            };
            var scales = new[]
            {
                new Vector3(0.34f, 0.15f, 0.25f),
                new Vector3(0.30f, 0.13f, 0.24f),
                new Vector3(0.31f, 0.16f, 0.27f),
                new Vector3(0.35f, 0.17f, 0.28f),
                new Vector3(0.32f, 0.15f, 0.27f),
                new Vector3(0.29f, 0.14f, 0.24f),
                new Vector3(0.33f, 0.16f, 0.26f),
                new Vector3(0.31f, 0.14f, 0.25f),
                new Vector3(0.37f, 0.18f, 0.29f)
            };
            var mesh = HavenlinePremiumFlameMeshFactory.RequireEmber();
            for (var index = 0; index < positions.Length; index++)
            {
                CreateLayer(
                    emberRoot,
                    $"Ember_{index + 1:00}",
                    mesh,
                    index % 4 == 0
                        ? HavenlinePremiumVisualAssets.FlameInnerMaterialPath
                        : HavenlinePremiumVisualAssets.FlameOuterMaterialPath,
                    positions[index],
                    scales[index],
                    Quaternion.Euler(index * 13f, index * 37f + 11f, index * 9f));
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
