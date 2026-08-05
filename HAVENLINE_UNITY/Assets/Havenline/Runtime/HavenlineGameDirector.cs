using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using UnityEngine;

namespace Havenline
{
    public sealed class HavenlineGameDirector : MonoBehaviour
    {
        [SerializeField] private HavenlineEnemy enemyPrefab;
        [SerializeField] private HavenlineHelper helper;
        [SerializeField] private HavenlineFurnace furnace;
        [SerializeField] private HavenlineConstructionSite requiredDefense;
        [SerializeField] private GameObject nextAreaGate;

        private float waveClock = 48f;
        private int completedWaves;
        private bool waveActive;

        public string Objective { get; private set; } = "Gather wood";
        public int Wave => completedWaves;
        public float WaveClock => waveClock;
        public bool WaveActive => waveActive;
        public int ActiveEnemyCount => HavenlineWorldRegistry.AliveEnemyCount;
        public HavenlineHelper Helper => helper;
        public HavenlineFurnace Furnace => furnace;
        public bool OpeningComplete => furnace != null && furnace.IsOperational && furnace.Level >= 2 &&
                                       helper != null && helper.State != HelperState.Trapped &&
                                       helper.State != HelperState.Rescuing &&
                                       (requiredDefense == null || requiredDefense.IsBuilt) && completedWaves >= 1;

        public void Configure(HavenlineEnemy prefab, HavenlineHelper survivor, HavenlineFurnace centralFurnace)
        {
            enemyPrefab = prefab;
            helper = survivor;
            furnace = centralFurnace;
        }

        public void Configure(
            HavenlineEnemy prefab,
            HavenlineHelper survivor,
            HavenlineFurnace centralFurnace,
            HavenlineConstructionSite defense,
            GameObject connectedAreaGate)
        {
            enemyPrefab = prefab;
            helper = survivor;
            furnace = centralFurnace;
            requiredDefense = defense;
            nextAreaGate = connectedAreaGate;
        }

        private void Start()
        {
            HavenlineSave.LoadDirector(out completedWaves, out waveClock);
            waveActive = false;
        }

        private void Update()
        {
            if (furnace == null)
                return;

            if (waveActive && HavenlineWorldRegistry.AliveEnemyCount == 0)
            {
                waveActive = false;
                completedWaves++;
                waveClock = Mathf.Max(24f, 48f - completedWaves * 3f);
                HavenlineSave.MarkDirty();
            }

            Objective = DetermineObjective();
            if (nextAreaGate != null)
                nextAreaGate.SetActive(OpeningComplete);

            if (waveActive || !ReadyForWave())
                return;

            waveClock -= Time.deltaTime;
            if (waveClock > 0f)
                return;

            var waveNumber = completedWaves + 1;
            SpawnWave(2 + waveNumber);
            waveActive = HavenlineWorldRegistry.AliveEnemyCount > 0;
            if (!waveActive)
                waveClock = 5f;
            HavenlineSave.MarkDirty();
        }

        private bool ReadyForWave() => furnace.IsOperational && furnace.Level >= 2 &&
                                       helper != null && helper.State != HelperState.Trapped &&
                                       helper.State != HelperState.Rescuing &&
                                       (requiredDefense == null || requiredDefense.IsBuilt);

        private string DetermineObjective()
        {
            if (!furnace.IsOperational)
                return "Carry wood close to repair the furnace";
            if (furnace.Level < 2)
                return "Gather supplies and feed the furnace";
            if (helper != null && helper.State == HelperState.Trapped)
                return "Move close to rescue the frozen survivor";
            if (requiredDefense != null && !requiredDefense.IsBuilt)
                return "Carry wood and stone to the barricade";
            if (waveActive)
                return $"Defend automatically • {HavenlineWorldRegistry.AliveEnemyCount} wolves";
            if (completedWaves < 1)
                return $"Prepare for wolves • {Mathf.CeilToInt(waveClock)}s";
            return OpeningComplete ? "The forest route is open" : "Repair the outpost";
        }

        private void SpawnWave(int count)
        {
            if (enemyPrefab == null)
                return;

            for (var index = 0; index < count; index++)
            {
                var north = index % 2 == 0;
                var position = new Vector3(
                    Mathf.Lerp(-9f, 9f, (index + 1f) / (count + 1f)),
                    0.15f,
                    north ? -15.2f : 15.2f);
                HavenlineEnemyPool.Spawn(enemyPrefab, position);
            }
        }
    }
}
