using System;
using System.IO;
using NUnit.Framework;
using UnityEngine;

namespace Havenline.Tests
{
    public sealed class HavenlineJsonRuntimeContractTests
    {
        private const string ContractPath = "Assets/Havenline/Reference/HAVENLINE_REFERENCE_CONTRACT.json";

        [Serializable]
        private sealed class Contract
        {
            public string contractVersion;
            public Baseline referenceBaseline;
            public CharacterSystem characterSystem;
            public CameraLock camera;
            public PlayerLock player;
            public WorldLock world;
            public OpeningLoopTuning openingLoopTuning;
            public PerformanceLock performance;
        }

        [Serializable]
        private sealed class Baseline
        {
            public CameraLock camera;
            public PlayerLock player;
        }

        [Serializable]
        private sealed class CharacterSystem
        {
            public int activeCrewSize;
            public string[] startingPlayableLeads;
            public string[] companionFormation;
            public float[][] companionFormationOffsets;
            public bool characters3And4LockedAtStart;
        }

        [Serializable]
        private sealed class CameraLock
        {
            public string projection;
            public float size;
            public float[] offset;
            public float focusHeight;
            public float lookAhead;
            public float followSharpness;
        }

        [Serializable]
        private sealed class PlayerLock
        {
            public float[] spawn;
            public float walkSpeed;
            public float runSpeed;
            public float acceleration;
            public float deceleration;
            public int carryCapacity;
            public float interactionRadius;
            public float combatRadius;
            public float depositRadius;
            public float rescueRadius;
            public float buildRadius;
            public float turnSharpness;
        }

        [Serializable]
        private sealed class WorldLock
        {
            public float boundX;
            public float boundZ;
            public float fallRecoveryY;
            public float[] furnace;
            public float[] campfire;
            public float[] storage;
            public float[] leftTent;
            public float[] rightTent;
            public float[] survivor;
            public float[] northBarricade;
            public float[] southBarricade;
            public float[] forestGate;
            public float[] metalNode;
            public float[] fuelNode;
            public float[][] woodNodes;
            public float[][] stoneNodes;
        }

        [Serializable]
        private sealed class GatherSecondsPerUnit
        {
            public float wood;
            public float stone;
            public float metal;
            public float fuel;
        }

        [Serializable]
        private sealed class ResourceRequirement
        {
            public int wood;
            public int stone;
            public int metal;
        }

        [Serializable]
        private sealed class OpeningLoopTuning
        {
            public GatherSecondsPerUnit gatherSecondsPerUnit;
            public int woodUnitsPerNode;
            public int stoneUnitsPerNode;
            public int metalUnitsPerNode;
            public int fuelUnitsPerNode;
            public float furnaceMaxDurability;
            public float furnaceRepairPerWood;
            public float furnaceDepositSecondsPerUnit;
            public float furnaceRepairSecondsPerUnit;
            public ResourceRequirement furnaceLevel2;
            public ResourceRequirement furnaceLevel3;
            public ResourceRequirement furnaceLevel4;
            public float warmthRadiusLevel1;
            public float warmthRadiusPerAdditionalLevel;
            public float survivorRescueSeconds;
            public ResourceRequirement northBarricadeBuild;
            public ResourceRequirement southBarricadeBuild;
            public float firstWaveDelaySeconds;
            public float minimumWaveDelaySeconds;
            public float waveDelayReductionPerCompletedWave;
            public int firstWaveEnemyCount;
            public float automaticActionRescanSeconds;
            public float automaticActionTargetHysteresis;
            public float automaticActionMovementCancelThreshold;
            public float automaticActionFacingWeight;
        }

        [Serializable]
        private sealed class PerformanceLock
        {
            public int minimumFrameRate;
            public int balancedFrameRate;
            public int maximumFrameRate;
        }

        [Test]
        public void JsonRuntimeLockMatchesTheCompiledReferenceConstants()
        {
            Assert.That(File.Exists(ContractPath), Is.True, $"Missing runtime contract: {ContractPath}");
            var contract = LoadContract();
            Assert.That(contract.contractVersion, Is.EqualTo("1.3.0"));

            Assert.That(contract.camera.projection, Is.EqualTo("orthographic"));
            Assert.That(contract.camera.size, Is.EqualTo(Reference.CameraSize).Within(0.0001f));
            AssertVector(contract.camera.offset, Reference.CameraOffset, "camera.offset");
            Assert.That(contract.camera.focusHeight, Is.EqualTo(Reference.CameraFocusHeight).Within(0.0001f));
            Assert.That(contract.camera.lookAhead, Is.EqualTo(Reference.CameraLookAhead).Within(0.0001f));
            Assert.That(contract.camera.followSharpness, Is.EqualTo(Reference.CameraFollowSharpness).Within(0.0001f));

            AssertVector(contract.player.spawn, Reference.PlayerSpawn, "player.spawn");
            Assert.That(contract.player.walkSpeed, Is.EqualTo(Reference.WalkSpeed).Within(0.0001f));
            Assert.That(contract.player.runSpeed, Is.EqualTo(Reference.RunSpeed).Within(0.0001f));
            Assert.That(contract.player.acceleration, Is.EqualTo(Reference.Acceleration).Within(0.0001f));
            Assert.That(contract.player.deceleration, Is.EqualTo(Reference.Deceleration).Within(0.0001f));
            Assert.That(contract.player.carryCapacity, Is.EqualTo(Reference.CarryCapacity));
            Assert.That(contract.player.interactionRadius, Is.EqualTo(Reference.InteractionRadius).Within(0.0001f));
            Assert.That(contract.player.combatRadius, Is.EqualTo(Reference.CombatRadius).Within(0.0001f));
            Assert.That(contract.player.depositRadius, Is.EqualTo(Reference.DepositRadius).Within(0.0001f));
            Assert.That(contract.player.rescueRadius, Is.EqualTo(Reference.RescueRadius).Within(0.0001f));
            Assert.That(contract.player.buildRadius, Is.EqualTo(Reference.BuildRadius).Within(0.0001f));
            Assert.That(contract.player.turnSharpness, Is.EqualTo(Reference.TurnSharpness).Within(0.0001f));

            Assert.That(contract.world.boundX, Is.EqualTo(Reference.BoundX).Within(0.0001f));
            Assert.That(contract.world.boundZ, Is.EqualTo(Reference.BoundZ).Within(0.0001f));
            Assert.That(contract.world.fallRecoveryY, Is.EqualTo(Reference.FallRecoveryY).Within(0.0001f));
            AssertVector(contract.world.furnace, Reference.Furnace, "world.furnace");
            AssertVector(contract.world.campfire, Reference.Campfire, "world.campfire");
            AssertVector(contract.world.storage, Reference.Storage, "world.storage");
            AssertVector(contract.world.leftTent, Reference.TentLeft, "world.leftTent");
            AssertVector(contract.world.rightTent, Reference.TentRight, "world.rightTent");
            AssertVector(contract.world.survivor, Reference.Survivor, "world.survivor");
            AssertVector(contract.world.northBarricade, Reference.NorthBarricade, "world.northBarricade");
            AssertVector(contract.world.southBarricade, Reference.SouthBarricade, "world.southBarricade");
            AssertVector(contract.world.forestGate, Reference.ForestGate, "world.forestGate");

            Assert.That(contract.world.woodNodes, Has.Length.EqualTo(Reference.WoodNodes.Length));
            for (var i = 0; i < Reference.WoodNodes.Length; i++)
                AssertVector(contract.world.woodNodes[i], Reference.WoodNodes[i], $"world.woodNodes[{i}]");
            Assert.That(contract.world.stoneNodes, Has.Length.EqualTo(Reference.StoneNodes.Length));
            for (var i = 0; i < Reference.StoneNodes.Length; i++)
                AssertVector(contract.world.stoneNodes[i], Reference.StoneNodes[i], $"world.stoneNodes[{i}]");

            Assert.That(contract.performance.minimumFrameRate, Is.EqualTo(Reference.MinimumFrameRate));
            Assert.That(contract.performance.balancedFrameRate, Is.EqualTo(Reference.BalancedFrameRate));
            Assert.That(contract.performance.maximumFrameRate, Is.EqualTo(Reference.MaximumFrameRate));
        }

        [Test]
        public void OriginalReferenceApkMeasurementsRemainPreservedAsBaseline()
        {
            var contract = LoadContract();
            Assert.That(contract.referenceBaseline.camera.size, Is.EqualTo(14.8f).Within(0.0001f));
            AssertVector(contract.referenceBaseline.camera.offset, new Vector3(0f, 7f, 7f), "referenceBaseline.camera.offset");
            Assert.That(contract.referenceBaseline.player.walkSpeed, Is.EqualTo(3.85f).Within(0.0001f));
            Assert.That(contract.referenceBaseline.player.runSpeed, Is.EqualTo(5.75f).Within(0.0001f));
            Assert.That(contract.referenceBaseline.player.carryCapacity, Is.EqualTo(6));
        }

        [Test]
        public void CrewFormationMatchesTheOnboardingRuntimeContract()
        {
            var contract = LoadContract();
            Assert.That(contract.characterSystem.activeCrewSize, Is.EqualTo(4));
            Assert.That(contract.characterSystem.startingPlayableLeads, Is.EqualTo(new[] { "Character1", "Character2" }));
            Assert.That(contract.characterSystem.companionFormation,
                Is.EqualTo(new[] { "unselected playable lead", "Character3", "Character4" }));
            Assert.That(contract.characterSystem.characters3And4LockedAtStart, Is.False);
            Assert.That(contract.characterSystem.companionFormationOffsets, Has.Length.EqualTo(3));
            AssertVector(contract.characterSystem.companionFormationOffsets[0], new Vector3(-1.35f, 0f, -1.7f), "characterSystem.companionFormationOffsets[0]");
            AssertVector(contract.characterSystem.companionFormationOffsets[1], new Vector3(1.35f, 0f, -1.7f), "characterSystem.companionFormationOffsets[1]");
            AssertVector(contract.characterSystem.companionFormationOffsets[2], new Vector3(0f, 0f, -2.8f), "characterSystem.companionFormationOffsets[2]");
        }

        [Test]
        public void OpeningLoopTuningMatchesTheShippingAuthoringAndRuntimeRules()
        {
            var contract = LoadContract();
            var tuning = contract.openingLoopTuning;

            Assert.That(tuning.gatherSecondsPerUnit.wood, Is.EqualTo(0.58f).Within(0.0001f));
            Assert.That(tuning.gatherSecondsPerUnit.stone, Is.EqualTo(0.70f).Within(0.0001f));
            Assert.That(tuning.gatherSecondsPerUnit.metal, Is.EqualTo(0.70f).Within(0.0001f));
            Assert.That(tuning.gatherSecondsPerUnit.fuel, Is.EqualTo(0.70f).Within(0.0001f));
            Assert.That(tuning.woodUnitsPerNode, Is.EqualTo(18));
            Assert.That(tuning.stoneUnitsPerNode, Is.EqualTo(14));
            Assert.That(tuning.metalUnitsPerNode, Is.EqualTo(10));
            Assert.That(tuning.fuelUnitsPerNode, Is.EqualTo(10));

            AssertVector(contract.world.metalNode, new Vector3(-9.4f, 0f, -8.8f), "world.metalNode");
            AssertVector(contract.world.fuelNode, new Vector3(9.5f, 0f, -8.6f), "world.fuelNode");

            Assert.That(tuning.furnaceMaxDurability, Is.EqualTo(260f).Within(0.0001f));
            Assert.That(tuning.furnaceRepairPerWood, Is.EqualTo(42f).Within(0.0001f));
            Assert.That(tuning.furnaceDepositSecondsPerUnit, Is.EqualTo(0.16f).Within(0.0001f));
            Assert.That(tuning.furnaceRepairSecondsPerUnit, Is.EqualTo(0.34f).Within(0.0001f));
            Assert.That(tuning.furnaceLevel2.wood, Is.EqualTo(18));
            Assert.That(tuning.furnaceLevel2.stone, Is.EqualTo(6));
            Assert.That(tuning.furnaceLevel3.wood, Is.EqualTo(38));
            Assert.That(tuning.furnaceLevel3.stone, Is.EqualTo(16));
            Assert.That(tuning.furnaceLevel4.wood, Is.EqualTo(64));
            Assert.That(tuning.furnaceLevel4.stone, Is.EqualTo(28));
            Assert.That(tuning.furnaceLevel4.metal, Is.EqualTo(6));
            Assert.That(tuning.warmthRadiusLevel1, Is.EqualTo(4.5f).Within(0.0001f));
            Assert.That(tuning.warmthRadiusPerAdditionalLevel, Is.EqualTo(3.5f).Within(0.0001f));
            Assert.That(tuning.survivorRescueSeconds, Is.EqualTo(2.2f).Within(0.0001f));

            Assert.That(tuning.northBarricadeBuild.wood, Is.EqualTo(8));
            Assert.That(tuning.northBarricadeBuild.stone, Is.EqualTo(3));
            Assert.That(tuning.southBarricadeBuild.wood, Is.EqualTo(8));
            Assert.That(tuning.southBarricadeBuild.stone, Is.EqualTo(3));
            Assert.That(tuning.firstWaveDelaySeconds, Is.EqualTo(48f).Within(0.0001f));
            Assert.That(tuning.minimumWaveDelaySeconds, Is.EqualTo(24f).Within(0.0001f));
            Assert.That(tuning.waveDelayReductionPerCompletedWave, Is.EqualTo(3f).Within(0.0001f));
            Assert.That(tuning.firstWaveEnemyCount, Is.EqualTo(3));

            Assert.That(tuning.automaticActionRescanSeconds, Is.EqualTo(0.075f).Within(0.0001f));
            Assert.That(tuning.automaticActionTargetHysteresis, Is.EqualTo(0.32f).Within(0.0001f));
            Assert.That(tuning.automaticActionMovementCancelThreshold, Is.EqualTo(0.12f).Within(0.0001f));
            Assert.That(tuning.automaticActionFacingWeight, Is.EqualTo(0.35f).Within(0.0001f));
        }

        private static Contract LoadContract()
        {
            Assert.That(File.Exists(ContractPath), Is.True, $"Missing runtime contract: {ContractPath}");
            var contract = JsonUtility.FromJson<Contract>(File.ReadAllText(ContractPath));
            Assert.That(contract, Is.Not.Null);
            return contract;
        }

        private static void AssertVector(float[] actual, Vector3 expected, string label)
        {
            Assert.That(actual, Is.Not.Null.And.Length.EqualTo(3), $"{label} must contain exactly three values.");
            Assert.That(actual[0], Is.EqualTo(expected.x).Within(0.0001f), $"{label}.x");
            Assert.That(actual[1], Is.EqualTo(expected.y).Within(0.0001f), $"{label}.y");
            Assert.That(actual[2], Is.EqualTo(expected.z).Within(0.0001f), $"{label}.z");
        }
    }
}
