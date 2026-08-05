using System;
using System.Collections.Generic;
using System.IO;
using UnityEditor;
using UnityEngine;

namespace Havenline.Editor
{
    public static partial class HavenlineProceduralArtStudio
    {
        private static readonly Quaternion ForwardTilt = Quaternion.Euler(10f, 0f, 0f);

        private static void GenerateModels()
        {
            WriteCharacter(PlayerModelPath, false);
            WriteCharacter(SurvivorModelPath, true);
            WriteWolf(WolfModelPath);
            for (var level = 1; level <= 4; level++)
                WriteFurnace(StructureRoot + $"/HAVENLINE_Furnace_L{level}.obj", level);
            WriteCampfire(StructureRoot + "/HAVENLINE_Campfire.obj");
            WriteTent(StructureRoot + "/HAVENLINE_Tent.obj");
            WriteStorage(StructureRoot + "/HAVENLINE_Storage.obj");
            WriteBarricade(StructureRoot + "/HAVENLINE_Barricade.obj");
            WriteGate(StructureRoot + "/HAVENLINE_ForestGate.obj");
            WriteBackpack(PropsRoot + "/HAVENLINE_Backpack.obj");
            WriteLog(ResourcesRoot + "/HAVENLINE_Log.obj");
            WriteStone(ResourcesRoot + "/HAVENLINE_Stone.obj", 0);
            WriteMetal(ResourcesRoot + "/HAVENLINE_Metal.obj");
            WriteFuel(ResourcesRoot + "/HAVENLINE_Fuel.obj");
            WritePine(EnvironmentRoot + "/HAVENLINE_Pine_A.obj", 0);
            WritePine(EnvironmentRoot + "/HAVENLINE_Pine_B.obj", 1);
            WriteStone(EnvironmentRoot + "/HAVENLINE_Rock_A.obj", 1);
            WriteStone(EnvironmentRoot + "/HAVENLINE_Rock_B.obj", 2);
            WriteSnowIsland(EnvironmentRoot + "/HAVENLINE_SnowIsland.obj");
            WriteIceShelf(EnvironmentRoot + "/HAVENLINE_IceShelf.obj");

            for (var index = 0; index < 6; index++)
                WriteSnowBank(EnvironmentRoot + $"/HAVENLINE_SnowBank_{index + 1:00}.obj", index);
            for (var index = 0; index < 4; index++)
                WriteCliff(EnvironmentRoot + $"/HAVENLINE_Cliff_{index + 1:00}.obj", index);
            for (var index = 0; index < 4; index++)
                WriteDebris(PropsRoot + $"/HAVENLINE_SupplyDebris_{index + 1:00}.obj", index);

            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);
            foreach (var model in Directory.EnumerateFiles(ProductionRoot, "*.obj", SearchOption.AllDirectories))
                HavenlineStudioGeometry.ConfigureModelImporter(model.Replace('\\', '/'));
        }

        private static void WriteCharacter(string path, bool survivor)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                var coat = survivor ? "Teal" : "Blue";
                var accent = survivor ? "Amber" : "Orange";
                var heightOffset = survivor ? -0.015f : 0f;

                obj.Begin("Boot_L", "Navy");
                obj.AddBox(new Vector3(-0.19f, 0.12f, 0.04f), new Vector3(0.25f, 0.24f, 0.42f), Quaternion.identity);
                obj.Begin("Boot_R", "Navy");
                obj.AddBox(new Vector3(0.19f, 0.12f, 0.04f), new Vector3(0.25f, 0.24f, 0.42f), Quaternion.identity);
                obj.Begin("Leg_L", coat);
                obj.AddCylinder(new Vector3(-0.18f, 0.47f, 0f), 0.13f, 0.62f, 10, Quaternion.identity);
                obj.Begin("Leg_R", coat);
                obj.AddCylinder(new Vector3(0.18f, 0.47f, 0f), 0.13f, 0.62f, 10, Quaternion.identity);
                obj.Begin("Torso", coat);
                obj.AddCone(new Vector3(0f, 1.08f + heightOffset, 0f), 0.44f, 0.34f, 0.92f, 12, Quaternion.identity);
                obj.Begin("CoatTrim", accent);
                obj.AddBox(new Vector3(0f, 0.76f, 0.01f), new Vector3(0.83f, 0.11f, 0.49f), Quaternion.identity);
                obj.AddBox(new Vector3(0f, 1.17f, -0.235f), new Vector3(0.12f, 0.72f, 0.05f), Quaternion.identity);
                obj.Begin("Arm_L", coat);
                obj.AddCylinder(new Vector3(-0.49f, 1.08f, 0f), 0.13f, 0.72f, 10, Quaternion.Euler(0f, 0f, -11f));
                obj.Begin("Arm_R", coat);
                obj.AddCylinder(new Vector3(0.49f, 1.08f, 0f), 0.13f, 0.72f, 10, Quaternion.Euler(0f, 0f, 11f));
                obj.Begin("Glove_L", "Navy");
                obj.AddSphere(new Vector3(-0.56f, 0.72f, 0f), new Vector3(0.16f, 0.15f, 0.17f), 5, 10, Quaternion.identity);
                obj.Begin("Glove_R", "Navy");
                obj.AddSphere(new Vector3(0.56f, 0.72f, 0f), new Vector3(0.16f, 0.15f, 0.17f), 5, 10, Quaternion.identity);
                obj.Begin("Head", "Skin");
                obj.AddSphere(new Vector3(0f, 1.76f, 0f), new Vector3(0.31f, 0.34f, 0.30f), 7, 12, Quaternion.identity);
                obj.Begin("Hood", coat);
                obj.AddSphere(new Vector3(0f, 1.79f, 0.03f), new Vector3(0.39f, 0.42f, 0.36f), 7, 12, Quaternion.identity);
                obj.Begin("Face", "Skin");
                obj.AddSphere(new Vector3(0f, 1.74f, -0.235f), new Vector3(0.24f, 0.26f, 0.10f), 6, 12, Quaternion.identity);
                obj.Begin("Eye_L", "Black");
                obj.AddSphere(new Vector3(-0.085f, 1.79f, -0.327f), new Vector3(0.026f, 0.038f, 0.018f), 4, 8, Quaternion.identity);
                obj.Begin("Eye_R", "Black");
                obj.AddSphere(new Vector3(0.085f, 1.79f, -0.327f), new Vector3(0.026f, 0.038f, 0.018f), 4, 8, Quaternion.identity);
                obj.Begin("Scarf", accent);
                obj.AddCylinder(new Vector3(0f, 1.51f, 0f), 0.32f, 0.16f, 12, Quaternion.identity);
                obj.Begin("BackDetail", "Navy");
                obj.AddBox(new Vector3(0f, 1.18f, 0.30f), new Vector3(0.5f, 0.58f, 0.13f), Quaternion.identity);
            });
        }

        private static void WriteWolf(string path)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                obj.Begin("Body", "Fur");
                obj.AddSphere(new Vector3(0f, 0.55f, 0f), new Vector3(0.42f, 0.43f, 0.78f), 6, 12, ForwardTilt);
                obj.Begin("Chest", "FurLight");
                obj.AddSphere(new Vector3(0f, 0.62f, -0.52f), new Vector3(0.36f, 0.42f, 0.38f), 6, 12, ForwardTilt);
                obj.Begin("Head", "Fur");
                obj.AddSphere(new Vector3(0f, 0.83f, -0.79f), new Vector3(0.33f, 0.34f, 0.38f), 6, 12, Quaternion.identity);
                obj.Begin("Snout", "FurLight");
                obj.AddCone(new Vector3(0f, 0.75f, -1.10f), 0.21f, 0.13f, 0.42f, 10, Quaternion.Euler(90f, 0f, 0f));
                obj.Begin("Nose", "Black");
                obj.AddSphere(new Vector3(0f, 0.75f, -1.32f), new Vector3(0.13f, 0.10f, 0.10f), 4, 8, Quaternion.identity);
                obj.Begin("Ear_L", "Fur");
                obj.AddCone(new Vector3(-0.20f, 1.14f, -0.78f), 0.15f, 0f, 0.38f, 8, Quaternion.Euler(0f, 0f, -10f));
                obj.Begin("Ear_R", "Fur");
                obj.AddCone(new Vector3(0.20f, 1.14f, -0.78f), 0.15f, 0f, 0.38f, 8, Quaternion.Euler(0f, 0f, 10f));
                for (var side = -1; side <= 1; side += 2)
                {
                    obj.Begin(side < 0 ? "Leg_FL" : "Leg_FR", "Fur");
                    obj.AddCylinder(new Vector3(side * 0.25f, 0.25f, -0.43f), 0.10f, 0.52f, 8, Quaternion.identity);
                    obj.Begin(side < 0 ? "Leg_BL" : "Leg_BR", "Fur");
                    obj.AddCylinder(new Vector3(side * 0.25f, 0.25f, 0.45f), 0.11f, 0.52f, 8, Quaternion.identity);
                }
                obj.Begin("Tail", "Fur");
                obj.AddCone(new Vector3(0f, 0.69f, 0.87f), 0.20f, 0.07f, 0.88f, 10, Quaternion.Euler(-68f, 0f, 0f));
                obj.Begin("Eye_L", "Orange");
                obj.AddSphere(new Vector3(-0.105f, 0.89f, -1.08f), new Vector3(0.035f, 0.035f, 0.018f), 4, 8, Quaternion.identity);
                obj.Begin("Eye_R", "Orange");
                obj.AddSphere(new Vector3(0.105f, 0.89f, -1.08f), new Vector3(0.035f, 0.035f, 0.018f), 4, 8, Quaternion.identity);
            });
        }

        private static void WriteFurnace(string path, int level)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                var scale = 0.82f + level * 0.12f;
                obj.Begin("StoneBase", "Stone");
                obj.AddCylinder(new Vector3(0f, 0.24f, 0f), 0.72f * scale, 0.48f, 12, Quaternion.identity);
                obj.Begin("Core", "Metal");
                obj.AddCylinder(new Vector3(0f, 0.92f, 0f), 0.52f * scale, 1.18f + level * 0.12f, 12, Quaternion.identity);
                obj.Begin("Door", "Navy");
                obj.AddBox(new Vector3(0f, 0.82f, -0.50f * scale), new Vector3(0.58f, 0.56f, 0.10f), Quaternion.identity);
                obj.Begin("FireWindow", "Orange");
                obj.AddBox(new Vector3(0f, 0.83f, -0.565f * scale), new Vector3(0.38f, 0.32f, 0.04f), Quaternion.identity);
                obj.Begin("MetalBands", "MetalLight");
                obj.AddCylinder(new Vector3(0f, 0.46f, 0f), 0.56f * scale, 0.12f, 12, Quaternion.identity);
                obj.AddCylinder(new Vector3(0f, 1.30f + level * 0.05f, 0f), 0.55f * scale, 0.12f, 12, Quaternion.identity);
                obj.Begin("Chimney", "Metal");
                obj.AddCylinder(new Vector3(0f, 1.72f + level * 0.14f, 0f), 0.19f + level * 0.025f, 0.72f + level * 0.16f, 10, Quaternion.identity);
                obj.Begin("ChimneyCap", "MetalLight");
                obj.AddCone(new Vector3(0f, 2.12f + level * 0.22f, 0f), 0.34f + level * 0.03f, 0.24f, 0.20f, 10, Quaternion.identity);
                if (level >= 2)
                {
                    obj.Begin("SideTank_L", "Metal");
                    obj.AddCylinder(new Vector3(-0.65f * scale, 0.95f, 0f), 0.22f, 0.78f, 10, Quaternion.identity);
                    obj.Begin("SideTank_R", "Metal");
                    obj.AddCylinder(new Vector3(0.65f * scale, 0.95f, 0f), 0.22f, 0.78f, 10, Quaternion.identity);
                }
                if (level >= 3)
                {
                    obj.Begin("HeatFins", "Amber");
                    for (var i = 0; i < 6; i++)
                    {
                        var angle = i * 60f;
                        var direction = Quaternion.Euler(0f, angle, 0f) * Vector3.forward;
                        obj.AddBox(new Vector3(direction.x * 0.72f, 1.18f, direction.z * 0.72f),
                            new Vector3(0.10f, 0.76f, 0.34f), Quaternion.Euler(0f, angle, 0f));
                    }
                }
                if (level >= 4)
                {
                    obj.Begin("Crown", "Orange");
                    obj.AddCone(new Vector3(0f, 2.55f, 0f), 0.42f, 0.14f, 0.34f, 12, Quaternion.identity);
                }
            });
        }

        private static void WriteCampfire(string path)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                obj.Begin("StoneRing", "StoneLight");
                for (var i = 0; i < 10; i++)
                {
                    var angle = i * Mathf.PI * 0.2f;
                    obj.AddSphere(new Vector3(Mathf.Cos(angle) * 0.52f, 0.13f, Mathf.Sin(angle) * 0.52f),
                        new Vector3(0.22f, 0.16f, 0.18f), 4, 8, Quaternion.Euler(0f, i * 36f, 0f));
                }
                obj.Begin("Log_A", "Wood");
                obj.AddCylinder(new Vector3(0f, 0.26f, 0f), 0.11f, 0.95f, 10, Quaternion.Euler(0f, 0f, 90f));
                obj.Begin("Log_B", "WoodLight");
                obj.AddCylinder(new Vector3(0f, 0.28f, 0f), 0.11f, 0.95f, 10, Quaternion.Euler(90f, 0f, 0f));
                obj.Begin("Flame", "Orange");
                obj.AddCone(new Vector3(0f, 0.65f, 0f), 0.30f, 0.04f, 0.84f, 10, Quaternion.identity);
            });
        }

        private static void WriteTent(string path)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                obj.Begin("TentBody", "Blue");
                obj.AddWedge(new Vector3(0f, 0.88f, 0f), new Vector3(2.5f, 1.75f, 2.3f), Quaternion.identity);
                obj.Begin("TentTrim", "Orange");
                obj.AddBox(new Vector3(0f, 0.08f, -1.12f), new Vector3(2.56f, 0.14f, 0.13f), Quaternion.identity);
                obj.AddBox(new Vector3(0f, 0.87f, -1.13f), new Vector3(0.10f, 1.62f, 0.10f), Quaternion.identity);
                obj.Begin("Door", "Navy");
                obj.AddWedge(new Vector3(0f, 0.65f, -1.17f), new Vector3(0.76f, 1.22f, 0.08f), Quaternion.identity);
            });
        }

        private static void WriteStorage(string path)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                obj.Begin("Crate", "Wood");
                obj.AddBox(new Vector3(0f, 0.58f, 0f), new Vector3(1.55f, 1.16f, 1.18f), Quaternion.identity);
                obj.Begin("Bands", "MetalLight");
                obj.AddBox(new Vector3(-0.54f, 0.58f, 0f), new Vector3(0.12f, 1.20f, 1.22f), Quaternion.identity);
                obj.AddBox(new Vector3(0.54f, 0.58f, 0f), new Vector3(0.12f, 1.20f, 1.22f), Quaternion.identity);
                obj.Begin("Lid", "WoodLight");
                obj.AddBox(new Vector3(0f, 1.20f, 0f), new Vector3(1.68f, 0.16f, 1.28f), Quaternion.identity);
            });
        }

        private static void WriteBarricade(string path)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                obj.Begin("Posts", "Wood");
                for (var i = -3; i <= 3; i++)
                    obj.AddCone(new Vector3(i * 0.63f, 0.86f, 0f), 0.18f, 0.08f, 1.85f, 8, Quaternion.identity);
                obj.Begin("Crossbeams", "WoodLight");
                obj.AddCylinder(new Vector3(0f, 0.62f, -0.08f), 0.14f, 4.5f, 10, Quaternion.Euler(0f, 0f, 90f));
                obj.AddCylinder(new Vector3(0f, 1.20f, 0.08f), 0.14f, 4.5f, 10, Quaternion.Euler(0f, 0f, 90f));
                obj.Begin("Bindings", "Metal");
                for (var i = -2; i <= 2; i++)
                    obj.AddBox(new Vector3(i * 0.84f, 0.92f, -0.20f), new Vector3(0.08f, 1.05f, 0.08f), Quaternion.Euler(0f, 0f, 22f));
            });
        }

        private static void WriteGate(string path)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                obj.Begin("GatePosts", "Wood");
                obj.AddCylinder(new Vector3(-2.25f, 1.65f, 0f), 0.28f, 3.30f, 10, Quaternion.identity);
                obj.AddCylinder(new Vector3(2.25f, 1.65f, 0f), 0.28f, 3.30f, 10, Quaternion.identity);
                obj.Begin("GateBeam", "WoodLight");
                obj.AddCylinder(new Vector3(0f, 2.86f, 0f), 0.25f, 4.85f, 10, Quaternion.Euler(0f, 0f, 90f));
                obj.Begin("GateDoors", "Blue");
                obj.AddBox(new Vector3(-1.04f, 1.25f, 0f), new Vector3(1.90f, 2.45f, 0.24f), Quaternion.identity);
                obj.AddBox(new Vector3(1.04f, 1.25f, 0f), new Vector3(1.90f, 2.45f, 0.24f), Quaternion.identity);
                obj.Begin("GateMark", "Orange");
                obj.AddBox(new Vector3(0f, 1.42f, -0.16f), new Vector3(0.20f, 1.42f, 0.08f), Quaternion.Euler(0f, 0f, 45f));
                obj.AddBox(new Vector3(0f, 1.42f, -0.16f), new Vector3(0.20f, 1.42f, 0.08f), Quaternion.Euler(0f, 0f, -45f));
            });
        }

        private static void WriteBackpack(string path)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                obj.Begin("Pack", "Navy");
                obj.AddBox(new Vector3(0f, 0.42f, 0f), new Vector3(0.66f, 0.82f, 0.30f), Quaternion.identity);
                obj.Begin("Flap", "Blue");
                obj.AddBox(new Vector3(0f, 0.70f, -0.17f), new Vector3(0.70f, 0.32f, 0.10f), Quaternion.identity);
                obj.Begin("Straps", "Orange");
                obj.AddBox(new Vector3(-0.20f, 0.42f, -0.19f), new Vector3(0.08f, 0.70f, 0.06f), Quaternion.identity);
                obj.AddBox(new Vector3(0.20f, 0.42f, -0.19f), new Vector3(0.08f, 0.70f, 0.06f), Quaternion.identity);
            });
        }

        private static void WriteLog(string path)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                obj.Begin("Bark", "Wood");
                obj.AddCylinder(Vector3.zero, 0.18f, 0.82f, 10, Quaternion.Euler(0f, 0f, 90f));
                obj.Begin("CutEnds", "WoodLight");
                obj.AddCylinder(new Vector3(-0.42f, 0f, 0f), 0.15f, 0.03f, 10, Quaternion.Euler(0f, 0f, 90f));
                obj.AddCylinder(new Vector3(0.42f, 0f, 0f), 0.15f, 0.03f, 10, Quaternion.Euler(0f, 0f, 90f));
            });
        }

        private static void WriteStone(string path, int variant)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                obj.Begin("Rock", variant % 2 == 0 ? "Stone" : "StoneLight");
                obj.AddSphere(new Vector3(0f, 0.28f, 0f),
                    new Vector3(0.48f + variant * 0.06f, 0.33f + variant * 0.04f, 0.42f),
                    4, 7 + variant, Quaternion.Euler(variant * 9f, variant * 23f, variant * 5f));
                obj.Begin("SnowCap", "Snow");
                obj.AddSphere(new Vector3(0f, 0.48f, -0.02f), new Vector3(0.38f, 0.10f, 0.32f), 3, 8, Quaternion.identity);
            });
        }

        private static void WriteMetal(string path)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                obj.Begin("Scrap", "Metal");
                obj.AddBox(Vector3.zero, new Vector3(0.65f, 0.16f, 0.38f), Quaternion.Euler(8f, 13f, -5f));
                obj.Begin("Edge", "MetalLight");
                obj.AddBox(new Vector3(0.12f, 0.13f, 0f), new Vector3(0.48f, 0.08f, 0.28f), Quaternion.Euler(-4f, -21f, 10f));
            });
        }

        private static void WriteFuel(string path)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                obj.Begin("Can", "Orange");
                obj.AddBox(new Vector3(0f, 0.34f, 0f), new Vector3(0.46f, 0.68f, 0.28f), Quaternion.identity);
                obj.Begin("Cap", "MetalLight");
                obj.AddCylinder(new Vector3(0.12f, 0.74f, 0f), 0.07f, 0.12f, 8, Quaternion.identity);
                obj.Begin("Handle", "Navy");
                obj.AddBox(new Vector3(-0.08f, 0.66f, 0f), new Vector3(0.24f, 0.08f, 0.09f), Quaternion.identity);
            });
        }

        private static void WritePine(string path, int variant)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                obj.Begin("Trunk", "Wood");
                obj.AddCone(new Vector3(0f, 1.0f, 0f), 0.24f, 0.15f, 2f, 10, Quaternion.identity);
                for (var layer = 0; layer < 4; layer++)
                {
                    obj.Begin($"Branches_{layer}", layer % 2 == 0 ? "Pine" : "PineLight");
                    var height = 1.55f + layer * 0.72f;
                    var radius = 1.42f - layer * 0.24f + variant * 0.05f;
                    obj.AddCone(new Vector3(0f, height, 0f), radius, 0.07f, 1.38f, 10, Quaternion.Euler(0f, variant * 18f + layer * 11f, 0f));
                }
                obj.Begin("Snow", "Snow");
                for (var layer = 0; layer < 3; layer++)
                    obj.AddCone(new Vector3(0f, 1.83f + layer * 0.72f, 0f), 1.18f - layer * 0.22f, 0.03f, 0.18f, 10, Quaternion.identity);
            });
        }

        private static void WriteSnowIsland(string path)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                obj.Begin("Island", "Snow");
                obj.AddCylinder(new Vector3(0f, -0.24f, 0f), 15.8f, 0.82f, 24, Quaternion.identity);
                obj.Begin("RockFoundation", "Stone");
                obj.AddCone(new Vector3(0f, -1.15f, 0f), 15.2f, 11.8f, 1.45f, 24, Quaternion.identity);
            });
        }

        private static void WriteIceShelf(string path)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                obj.Begin("IceShelf", "Ice");
                obj.AddCylinder(new Vector3(0f, -0.08f, 0f), 16.4f, 0.28f, 24, Quaternion.identity);
            });
        }

        private static void WriteSnowBank(string path, int variant)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                obj.Begin("SnowBank", "Snow");
                obj.AddSphere(new Vector3(0f, 0.20f, 0f),
                    new Vector3(1.2f + variant * 0.11f, 0.34f + (variant % 2) * 0.07f, 0.74f + variant * 0.04f),
                    4, 10, Quaternion.Euler(0f, variant * 27f, 0f));
            });
        }

        private static void WriteCliff(string path, int variant)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                obj.Begin("Cliff", variant % 2 == 0 ? "Stone" : "StoneLight");
                obj.AddSphere(new Vector3(0f, 0.65f, 0f),
                    new Vector3(1.3f + variant * 0.22f, 0.95f + variant * 0.12f, 0.88f),
                    4, 8, Quaternion.Euler(variant * 12f, variant * 31f, 8f));
                obj.Begin("SnowCap", "Snow");
                obj.AddSphere(new Vector3(0f, 1.20f + variant * 0.08f, 0f),
                    new Vector3(1.06f + variant * 0.16f, 0.15f, 0.66f), 3, 8, Quaternion.identity);
            });
        }

        private static void WriteDebris(string path, int variant)
        {
            HavenlineStudioGeometry.WriteObj(path, obj =>
            {
                obj.Begin("Crate", variant % 2 == 0 ? "Wood" : "Blue");
                obj.AddBox(new Vector3(-0.18f, 0.24f, 0f), new Vector3(0.72f, 0.48f, 0.62f), Quaternion.Euler(0f, variant * 17f, variant * 3f));
                obj.Begin("Scrap", "MetalLight");
                obj.AddBox(new Vector3(0.36f, 0.22f, 0.12f), new Vector3(0.58f, 0.10f, 0.30f), Quaternion.Euler(12f, -variant * 21f, 8f));
                obj.Begin("Rope", "Orange");
                obj.AddCylinder(new Vector3(-0.18f, 0.27f, -0.34f), 0.035f, 0.70f, 8, Quaternion.Euler(0f, 0f, 90f));
            });
        }
    }
}
