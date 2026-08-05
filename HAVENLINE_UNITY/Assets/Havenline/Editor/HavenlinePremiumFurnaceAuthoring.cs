using System;
using UnityEditor;
using UnityEngine;
using UnityEngine.Rendering;

namespace Havenline.Editor
{
    /// <summary>
    /// Builds the four visible furnace progression stages from HAVENLINE-owned meshes and
    /// materials. Every stage is a complete machine silhouette rather than an imported prop
    /// hidden behind decorative scene dressing.
    /// </summary>
    internal static class HavenlinePremiumFurnaceAuthoring
    {
        private const string MaterialRoot = "Assets/Havenline/Art/Production/Materials";
        private const string Metal = MaterialRoot + "/HAVENLINE_Metal.mat";
        private const string MetalLight = MaterialRoot + "/HAVENLINE_MetalLight.mat";
        private const string Navy = MaterialRoot + "/HAVENLINE_Navy.mat";
        private const string Blue = MaterialRoot + "/HAVENLINE_Blue.mat";
        private const string Amber = MaterialRoot + "/HAVENLINE_Amber.mat";
        private const string Orange = MaterialRoot + "/HAVENLINE_Orange.mat";

        internal static GameObject[] BuildStages(Transform parent)
        {
            if (parent == null)
                throw new ArgumentNullException(nameof(parent));

            HavenlinePremiumVisualAssets.Ensure();
            return new[]
            {
                BuildStageOne(parent),
                BuildStageTwo(parent),
                BuildStageThree(parent),
                BuildStageFour(parent)
            };
        }

        private static GameObject BuildStageOne(Transform parent)
        {
            var stage = CreateStage(parent, 1);
            Part(stage, "CompactCore", HavenlinePremiumVisualAssets.FurnaceBodyPath, Metal,
                new Vector3(0f, 0f, 0f), new Vector3(0.70f, 0.74f, 0.72f));
            Part(stage, "CompactHood", HavenlinePremiumVisualAssets.FurnaceHoodPath, MetalLight,
                new Vector3(0f, 1.31f, 0f), new Vector3(0.72f, 0.72f, 0.74f));
            Part(stage, "StarterChimney", HavenlinePremiumVisualAssets.FurnaceChimneyPath, Navy,
                new Vector3(0f, 1.72f, -0.10f), new Vector3(0.58f, 0.72f, 0.58f));
            AddDoor(stage, 0.56f, 0.78f, 0.58f, 0.78f);
            AddBand(stage, "StarterBand", 0.32f, 0.76f, 0.75f);
            return stage.gameObject;
        }

        private static GameObject BuildStageTwo(Transform parent)
        {
            var stage = CreateStage(parent, 2);
            Part(stage, "ExpandedCore", HavenlinePremiumVisualAssets.FurnaceBodyPath, Metal,
                new Vector3(0f, 0f, 0f), new Vector3(0.86f, 0.82f, 0.80f));
            Part(stage, "ExpandedHood", HavenlinePremiumVisualAssets.FurnaceHoodPath, MetalLight,
                new Vector3(0f, 1.46f, 0f), new Vector3(0.88f, 0.80f, 0.82f));
            Part(stage, "PrimaryStack", HavenlinePremiumVisualAssets.FurnaceChimneyPath, Navy,
                new Vector3(0f, 1.92f, -0.12f), new Vector3(0.68f, 0.90f, 0.68f));
            AddSideTank(stage, "LeftFuelTank", -1.38f, 0.04f, 0.72f);
            AddSideTank(stage, "RightFuelTank", 1.38f, 0.04f, 0.72f);
            Part(stage, "FuelManifold", HavenlinePremiumVisualAssets.FurnaceChimneyPath, Blue,
                new Vector3(0f, 1.32f, -0.08f), new Vector3(0.28f, 1.65f, 0.28f),
                Quaternion.Euler(0f, 0f, 90f));
            AddDoor(stage, 0.64f, 0.91f, 0.68f, 0.90f);
            AddBand(stage, "ExpansionBand", 0.40f, 0.92f, 0.84f);
            return stage.gameObject;
        }

        private static GameObject BuildStageThree(Transform parent)
        {
            var stage = CreateStage(parent, 3);
            Part(stage, "IndustrialCore", HavenlinePremiumVisualAssets.FurnaceBodyPath, Metal,
                new Vector3(0f, 0f, 0f), new Vector3(1.00f, 0.91f, 0.88f));
            Part(stage, "IndustrialHood", HavenlinePremiumVisualAssets.FurnaceHoodPath, MetalLight,
                new Vector3(0f, 1.62f, 0f), new Vector3(1.04f, 0.88f, 0.92f));
            Part(stage, "IndustrialStack", HavenlinePremiumVisualAssets.FurnaceChimneyPath, Navy,
                new Vector3(0f, 2.12f, -0.18f), new Vector3(0.78f, 1.10f, 0.78f));
            AddSideModule(stage, "LeftHeatExchanger", -1.70f, 0.10f);
            AddSideModule(stage, "RightHeatExchanger", 1.70f, 0.10f);
            AddSideTank(stage, "LeftPressureTank", -1.62f, 1.02f, 0.52f);
            AddSideTank(stage, "RightPressureTank", 1.62f, 1.02f, 0.52f);
            AddDoor(stage, 0.72f, 1.05f, 0.76f, 0.99f);
            AddBand(stage, "IndustrialBandLower", 0.36f, 1.05f, 0.93f);
            AddBand(stage, "IndustrialBandUpper", 1.20f, 1.05f, 0.93f);
            AddGrate(stage, 1.04f, 0.78f, 0.98f);
            return stage.gameObject;
        }

        private static GameObject BuildStageFour(Transform parent)
        {
            var stage = CreateStage(parent, 4);
            Part(stage, "HavenlineBase", HavenlinePremiumVisualAssets.FurnaceBodyPath, Navy,
                new Vector3(0f, 0f, 0f), new Vector3(1.17f, 0.30f, 1.02f));
            Part(stage, "HavenlineCore", HavenlinePremiumVisualAssets.FurnaceBodyPath, Metal,
                new Vector3(0f, 0.44f, 0f), new Vector3(1.10f, 0.96f, 0.94f));
            Part(stage, "HavenlineCrown", HavenlinePremiumVisualAssets.FurnaceHoodPath, MetalLight,
                new Vector3(0f, 2.15f, 0f), new Vector3(1.14f, 0.96f, 0.98f));
            Part(stage, "LeftExhaustStack", HavenlinePremiumVisualAssets.FurnaceChimneyPath, Navy,
                new Vector3(-0.72f, 2.70f, -0.22f), new Vector3(0.70f, 1.22f, 0.70f));
            Part(stage, "RightExhaustStack", HavenlinePremiumVisualAssets.FurnaceChimneyPath, Navy,
                new Vector3(0.72f, 2.70f, -0.22f), new Vector3(0.70f, 1.22f, 0.70f));
            AddSideModule(stage, "LeftReinforcedExchanger", -1.96f, 0.22f);
            AddSideModule(stage, "RightReinforcedExchanger", 1.96f, 0.22f);
            AddSideTank(stage, "LeftReserveTank", -2.02f, 1.22f, 0.68f);
            AddSideTank(stage, "RightReserveTank", 2.02f, 1.22f, 0.68f);
            Part(stage, "UpperManifold", HavenlinePremiumVisualAssets.FurnaceChimneyPath, Blue,
                new Vector3(0f, 2.08f, -0.10f), new Vector3(0.32f, 2.35f, 0.32f),
                Quaternion.Euler(0f, 0f, 90f));
            AddDoor(stage, 0.84f, 1.18f, 0.88f, 1.08f);
            AddBand(stage, "HavenlineBandLower", 0.72f, 1.20f, 1.00f);
            AddBand(stage, "HavenlineBandUpper", 1.72f, 1.20f, 1.00f);
            AddGrate(stage, 1.42f, 0.90f, 1.08f);
            return stage.gameObject;
        }

        private static Transform CreateStage(Transform parent, int level)
        {
            var root = new GameObject($"FurnaceLevel{level}");
            root.transform.SetParent(parent, false);
            root.transform.localPosition = Vector3.zero;
            root.transform.localRotation = Quaternion.identity;
            return root.transform;
        }

        private static void AddDoor(
            Transform stage,
            float bottom,
            float widthScale,
            float heightScale,
            float front)
        {
            Part(stage, "DoorFrame", HavenlinePremiumVisualAssets.FurnaceBodyPath, Amber,
                new Vector3(0f, bottom, front),
                new Vector3(widthScale * 0.50f, heightScale * 0.48f, 0.075f));
            Part(stage, "FireboxGlow", HavenlinePremiumVisualAssets.FurnaceBodyPath, Orange,
                new Vector3(0f, bottom + 0.08f, front + 0.11f),
                new Vector3(widthScale * 0.39f, heightScale * 0.37f, 0.045f));
        }

        private static void AddBand(
            Transform stage,
            string name,
            float bottom,
            float widthScale,
            float depthScale)
        {
            Part(stage, name, HavenlinePremiumVisualAssets.FurnaceBodyPath, Amber,
                new Vector3(0f, bottom, 0f),
                new Vector3(widthScale, 0.075f, depthScale));
        }

        private static void AddSideTank(
            Transform stage,
            string name,
            float x,
            float bottom,
            float heightScale)
        {
            Part(stage, name, HavenlinePremiumVisualAssets.FurnaceChimneyPath, Blue,
                new Vector3(x, bottom, -0.10f),
                new Vector3(0.78f, heightScale, 0.78f));
            Part(stage, name + "Cap", HavenlinePremiumVisualAssets.FurnaceHoodPath, MetalLight,
                new Vector3(x, bottom + heightScale * 1.48f, -0.10f),
                new Vector3(0.22f, 0.18f, 0.32f));
        }

        private static void AddSideModule(Transform stage, string name, float x, float bottom)
        {
            Part(stage, name, HavenlinePremiumVisualAssets.FurnaceBodyPath, Navy,
                new Vector3(x, bottom, -0.03f),
                new Vector3(0.34f, 0.70f, 0.63f));
            Part(stage, name + "Face", HavenlinePremiumVisualAssets.FurnaceBodyPath, MetalLight,
                new Vector3(x, bottom + 0.22f, 0.64f),
                new Vector3(0.25f, 0.42f, 0.055f));
        }

        private static void AddGrate(Transform stage, float bottom, float width, float front)
        {
            for (var index = -2; index <= 2; index++)
            {
                Part(stage, $"FrontGrate_{index + 3}", HavenlinePremiumVisualAssets.FurnaceChimneyPath, Navy,
                    new Vector3(index * width * 0.22f, bottom, front),
                    new Vector3(0.10f, 0.32f, 0.075f));
            }
        }

        private static GameObject Part(
            Transform parent,
            string name,
            string meshPath,
            string materialPath,
            Vector3 localPosition,
            Vector3 localScale,
            Quaternion? localRotation = null)
        {
            var mesh = AssetDatabase.LoadAssetAtPath<Mesh>(meshPath);
            var material = AssetDatabase.LoadAssetAtPath<Material>(materialPath);
            if (mesh == null)
                throw new InvalidOperationException("HAVENLINE furnace mesh is missing: " + meshPath);
            if (material == null)
                throw new InvalidOperationException("HAVENLINE furnace material is missing: " + materialPath);

            var part = new GameObject(name, typeof(MeshFilter), typeof(MeshRenderer));
            part.transform.SetParent(parent, false);
            part.transform.localPosition = localPosition;
            part.transform.localRotation = localRotation ?? Quaternion.identity;
            part.transform.localScale = localScale;
            part.GetComponent<MeshFilter>().sharedMesh = mesh;
            var renderer = part.GetComponent<MeshRenderer>();
            renderer.sharedMaterial = material;
            renderer.shadowCastingMode = ShadowCastingMode.On;
            renderer.receiveShadows = true;
            renderer.lightProbeUsage = LightProbeUsage.BlendProbes;
            renderer.reflectionProbeUsage = ReflectionProbeUsage.BlendProbes;
            return part;
        }
    }
}
