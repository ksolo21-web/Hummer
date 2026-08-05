using System;
using UnityEditor;
using UnityEngine;
using UnityEngine.Rendering;

namespace Havenline.Editor
{
    /// <summary>
    /// Instantiates the stable two-layer furnace core visual from HAVENLINE-owned mesh and
    /// emissive materials. Tiny particles remain secondary feedback only.
    /// </summary>
    internal static class HavenlinePremiumFlameAuthoring
    {
        internal static HavenlineFlamePulse Build(Transform parent)
        {
            if (parent == null)
                throw new ArgumentNullException(nameof(parent));
            HavenlinePremiumVisualAssets.Ensure();

            var root = new GameObject("FurnaceFlameVisual");
            root.transform.SetParent(parent, false);
            root.transform.localPosition = new Vector3(0f, 0.44f, 1.04f);
            root.transform.localScale = new Vector3(0.52f, 0.74f, 0.38f);

            CreateLayer(
                root.transform,
                "OuterFlame",
                HavenlinePremiumVisualAssets.FlameMeshPath,
                HavenlinePremiumVisualAssets.FlameOuterMaterialPath,
                Vector3.zero,
                Vector3.one);
            CreateLayer(
                root.transform,
                "InnerFlame",
                HavenlinePremiumVisualAssets.FlameMeshPath,
                HavenlinePremiumVisualAssets.FlameInnerMaterialPath,
                new Vector3(0f, -0.035f, 0.055f),
                new Vector3(0.56f, 0.62f, 0.58f));

            return root.AddComponent<HavenlineFlamePulse>();
        }

        private static void CreateLayer(
            Transform parent,
            string name,
            string meshPath,
            string materialPath,
            Vector3 localPosition,
            Vector3 localScale)
        {
            var mesh = AssetDatabase.LoadAssetAtPath<Mesh>(meshPath);
            var material = AssetDatabase.LoadAssetAtPath<Material>(materialPath);
            if (mesh == null)
                throw new InvalidOperationException("HAVENLINE core mesh is missing: " + meshPath);
            if (material == null)
                throw new InvalidOperationException("HAVENLINE core material is missing: " + materialPath);

            var layer = new GameObject(name, typeof(MeshFilter), typeof(MeshRenderer));
            layer.transform.SetParent(parent, false);
            layer.transform.localPosition = localPosition;
            layer.transform.localScale = localScale;
            layer.GetComponent<MeshFilter>().sharedMesh = mesh;
            var renderer = layer.GetComponent<MeshRenderer>();
            renderer.sharedMaterial = material;
            renderer.shadowCastingMode = ShadowCastingMode.Off;
            renderer.receiveShadows = false;
            renderer.lightProbeUsage = LightProbeUsage.BlendProbes;
            renderer.reflectionProbeUsage = ReflectionProbeUsage.Off;
        }
    }
}
