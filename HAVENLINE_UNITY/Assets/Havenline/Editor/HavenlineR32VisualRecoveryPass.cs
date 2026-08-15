using System;
using UnityEditor;
using UnityEngine;

namespace Havenline.Editor
{
    /// <summary>
    /// Human-review recovery pass for the R32 environment. The earlier R32 pass fixed the gross
    /// silhouettes, but the shipping frames still read as block-built because the barricades and
    /// inhabited-camp props had too little structural information at gameplay distance.
    ///
    /// This pass replaces that repeated visual language with deterministic authored meshes. It
    /// deliberately writes production OBJ assets instead of adding Unity primitives or proof-only
    /// geometry, so the Android scene and render proof see the same improvement.
    /// </summary>
    internal static class HavenlineR32VisualRecoveryPass
    {
        internal const string ProductionRoot = "Assets/Havenline/Art/Production";
        internal const string StructureRoot = ProductionRoot + "/Structures";
        internal const string PropsRoot = ProductionRoot + "/Props";
        internal const string BarricadePath = StructureRoot + "/HAVENLINE_Barricade.obj";
        internal const string DuckboardPath = PropsRoot + "/HAVENLINE_R32Duckboard.obj";
        internal const string UtilityRackPath = PropsRoot + "/HAVENLINE_R32UtilityRack.obj";
        internal const string ShelterServicePath = PropsRoot + "/HAVENLINE_R32ShelterServiceModule.obj";

        internal static void ApplyToGeneratedProduction()
        {
            WriteDetailedBarricade();
            WriteDuckboard();
            WriteUtilityRack();
            WriteShelterServiceModule();

            HavenlineStudioGeometry.ConfigureModelImporter(BarricadePath);
            HavenlineStudioGeometry.ConfigureModelImporter(DuckboardPath);
            HavenlineStudioGeometry.ConfigureModelImporter(UtilityRackPath);
            HavenlineStudioGeometry.ConfigureModelImporter(ShelterServicePath);

            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);
        }

        private static void WriteDetailedBarricade()
        {
            HavenlineStudioGeometry.WriteObj(BarricadePath, obj =>
            {
                const int stakeCount = 7;
                for (var index = 0; index < stakeCount; index++)
                {
                    var x = Mathf.Lerp(-1.48f, 1.48f, index / (stakeCount - 1f));
                    var lean = (index % 2 == 0 ? -2.5f : 2.5f) + (index - 3) * 0.25f;

                    obj.Begin($"Stake_{index:00}", index % 3 == 0 ? "WoodLight" : "Wood");
                    obj.AddCylinder(
                        new Vector3(x, 0.74f, 0f),
                        0.075f + (index % 2) * 0.008f,
                        1.42f,
                        10,
                        Quaternion.Euler(0f, 0f, lean));
                    obj.Begin($"StakeTip_{index:00}", "WoodLight");
                    obj.AddCone(
                        new Vector3(x - Mathf.Sin(lean * Mathf.Deg2Rad) * 0.73f, 1.52f, 0f),
                        0.083f,
                        0.010f,
                        0.34f,
                        10,
                        Quaternion.Euler(0f, 0f, lean));
                    obj.Begin($"StakeSnow_{index:00}", "Snow");
                    obj.AddSphere(
                        new Vector3(x - Mathf.Sin(lean * Mathf.Deg2Rad) * 0.71f, 1.59f, -0.012f),
                        new Vector3(0.10f, 0.035f, 0.095f),
                        4,
                        10,
                        Quaternion.identity);
                }

                var railY = new[] { 0.45f, 0.83f, 1.16f };
                for (var index = 0; index < railY.Length; index++)
                {
                    obj.Begin($"Rail_{index:00}", index == 1 ? "WoodLight" : "Wood");
                    obj.AddBox(
                        new Vector3(0f, railY[index], -0.015f + index * 0.012f),
                        new Vector3(3.22f, 0.12f, 0.14f),
                        Quaternion.Euler(index == 1 ? 0f : (index == 0 ? -0.7f : 0.8f), 0f, 0f));

                    for (var clamp = -2; clamp <= 2; clamp++)
                    {
                        obj.Begin($"RailClamp_{index:00}_{clamp + 2:00}", "MetalLight");
                        obj.AddBox(
                            new Vector3(clamp * 0.64f, railY[index], -0.091f),
                            new Vector3(0.055f, 0.17f, 0.035f),
                            Quaternion.identity);
                    }
                }

                obj.Begin("LeftCrossBrace", "WoodLight");
                obj.AddBox(new Vector3(-0.70f, 0.78f, 0.095f), new Vector3(1.58f, 0.105f, 0.105f), Quaternion.Euler(0f, 0f, 31f));
                obj.Begin("RightCrossBrace", "WoodLight");
                obj.AddBox(new Vector3(0.70f, 0.78f, 0.095f), new Vector3(1.58f, 0.105f, 0.105f), Quaternion.Euler(0f, 0f, -31f));

                obj.Begin("LowerSnowLoad", "Snow");
                obj.AddBox(new Vector3(-0.54f, 0.535f, -0.09f), new Vector3(0.82f, 0.038f, 0.16f), Quaternion.Euler(0f, 0f, -1.2f));
                obj.Begin("UpperSnowLoad", "Snow");
                obj.AddBox(new Vector3(0.58f, 1.245f, -0.085f), new Vector3(0.88f, 0.040f, 0.16f), Quaternion.Euler(0f, 0f, 1.1f));
            });
        }

        private static void WriteDuckboard()
        {
            HavenlineStudioGeometry.WriteObj(DuckboardPath, obj =>
            {
                obj.Begin("UnderRailLeft", "Wood");
                obj.AddBox(new Vector3(-0.72f, 0.055f, 0f), new Vector3(0.12f, 0.11f, 2.65f), Quaternion.identity);
                obj.Begin("UnderRailRight", "Wood");
                obj.AddBox(new Vector3(0.72f, 0.055f, 0f), new Vector3(0.12f, 0.11f, 2.65f), Quaternion.identity);

                const int slats = 14;
                for (var index = 0; index < slats; index++)
                {
                    var z = Mathf.Lerp(-1.23f, 1.23f, index / (slats - 1f));
                    var yaw = ((index * 17) % 7 - 3) * 0.33f;
                    var width = 1.73f + ((index * 5) % 4) * 0.025f;
                    obj.Begin($"Slat_{index:00}", index % 4 == 0 ? "WoodLight" : "Wood");
                    obj.AddBox(
                        new Vector3(((index * 11) % 5 - 2) * 0.006f, 0.135f + (index % 3) * 0.004f, z),
                        new Vector3(width, 0.095f, 0.145f),
                        Quaternion.Euler(0f, yaw, 0f));

                    if (index % 3 == 0)
                    {
                        obj.Begin($"SlatSnow_{index:00}", "Snow");
                        obj.AddBox(new Vector3(-0.28f + (index % 2) * 0.55f, 0.194f, z - 0.018f), new Vector3(0.48f, 0.025f, 0.11f), Quaternion.Euler(0f, yaw, 0f));
                    }
                }

                for (var index = 0; index < 6; index++)
                {
                    var z = -1.02f + index * 0.41f;
                    obj.Begin($"FastenerL_{index:00}", "MetalLight");
                    obj.AddCylinder(new Vector3(-0.64f, 0.198f, z), 0.025f, 0.025f, 8, Quaternion.Euler(90f, 0f, 0f));
                    obj.Begin($"FastenerR_{index:00}", "MetalLight");
                    obj.AddCylinder(new Vector3(0.64f, 0.198f, z), 0.025f, 0.025f, 8, Quaternion.Euler(90f, 0f, 0f));
                }
            });
        }

        private static void WriteUtilityRack()
        {
            HavenlineStudioGeometry.WriteObj(UtilityRackPath, obj =>
            {
                foreach (var x in new[] { -0.70f, 0.70f })
                foreach (var z in new[] { -0.28f, 0.28f })
                {
                    obj.Begin($"Post_{x:0.00}_{z:0.00}", "Metal");
                    obj.AddCylinder(new Vector3(x, 0.78f, z), 0.045f, 1.56f, 10, Quaternion.identity);
                }

                var shelves = new[] { 0.24f, 0.77f, 1.29f };
                for (var index = 0; index < shelves.Length; index++)
                {
                    obj.Begin($"Shelf_{index:00}", "MetalLight");
                    obj.AddBox(new Vector3(0f, shelves[index], 0f), new Vector3(1.55f, 0.065f, 0.68f), Quaternion.identity);
                    obj.Begin($"ShelfLip_{index:00}", "Metal");
                    obj.AddBox(new Vector3(0f, shelves[index] + 0.075f, -0.33f), new Vector3(1.55f, 0.10f, 0.045f), Quaternion.identity);
                }

                obj.Begin("BackBraceA", "Metal");
                obj.AddBox(new Vector3(-0.34f, 0.78f, 0.31f), new Vector3(1.42f, 0.055f, 0.055f), Quaternion.Euler(0f, 0f, 51f));
                obj.Begin("BackBraceB", "Metal");
                obj.AddBox(new Vector3(0.34f, 0.78f, 0.31f), new Vector3(1.42f, 0.055f, 0.055f), Quaternion.Euler(0f, 0f, -51f));

                var cratePositions = new[]
                {
                    new Vector3(-0.38f, 0.38f, -0.02f),
                    new Vector3(0.36f, 0.38f, 0.04f),
                    new Vector3(-0.32f, 0.91f, 0.03f),
                    new Vector3(0.39f, 0.91f, -0.03f)
                };
                for (var index = 0; index < cratePositions.Length; index++)
                {
                    obj.Begin($"SupplyCase_{index:00}", index % 2 == 0 ? "Navy" : "Blue");
                    obj.AddBox(cratePositions[index], new Vector3(0.54f, 0.28f, 0.48f), Quaternion.Euler(0f, (index % 2 == 0 ? -4f : 5f), 0f));
                    obj.Begin($"CaseBand_{index:00}", "Orange");
                    obj.AddBox(cratePositions[index] + new Vector3(0f, 0f, -0.247f), new Vector3(0.12f, 0.30f, 0.025f), Quaternion.identity);
                }
            });
        }

        private static void WriteShelterServiceModule()
        {
            HavenlineStudioGeometry.WriteObj(ShelterServicePath, obj =>
            {
                obj.Begin("BackPlate", "Metal");
                obj.AddBox(new Vector3(0f, 0.78f, 0f), new Vector3(1.10f, 1.46f, 0.095f), Quaternion.identity);
                obj.Begin("InsetPanel", "Navy");
                obj.AddBox(new Vector3(0f, 0.82f, -0.068f), new Vector3(0.86f, 0.92f, 0.055f), Quaternion.identity);

                for (var row = 0; row < 3; row++)
                for (var column = 0; column < 4; column++)
                {
                    obj.Begin($"Vent_{row}_{column}", "MetalLight");
                    obj.AddBox(new Vector3(-0.30f + column * 0.20f, 0.58f + row * 0.17f, -0.105f), new Vector3(0.13f, 0.045f, 0.032f), Quaternion.identity);
                }

                obj.Begin("ServiceStripe", "Orange");
                obj.AddBox(new Vector3(-0.42f, 0.82f, -0.11f), new Vector3(0.055f, 0.94f, 0.035f), Quaternion.identity);
                obj.Begin("TopPipe", "MetalLight");
                obj.AddCylinder(new Vector3(0.36f, 1.48f, 0f), 0.065f, 0.45f, 12, Quaternion.identity);
                obj.Begin("TopPipeCap", "MetalLight");
                obj.AddCylinder(new Vector3(0.36f, 1.72f, 0f), 0.10f, 0.055f, 12, Quaternion.identity);
                obj.Begin("LowerConnector", "Amber");
                obj.AddCylinder(new Vector3(0.31f, 0.24f, -0.085f), 0.085f, 0.055f, 12, Quaternion.Euler(90f, 0f, 0f));
            });
        }
    }
}
