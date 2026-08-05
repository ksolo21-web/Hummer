using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using UnityEngine;

namespace Havenline
{
    [Serializable]
    public struct HavenlineFurnaceSnapshot
    {
        public int level;
        public int wood;
        public int stone;
        public int metal;
        public int fuel;
        public bool hasDurability;
        public float durability;
    }

    [Serializable]
    public struct HavenlineConstructionSnapshot
    {
        public string id;
        public int deliveredWood;
        public int deliveredStone;
        public bool built;
    }

    [Serializable]
    public struct HavenlineBarricadeSnapshot
    {
        public string id;
        public float health;
    }

    internal static class HavenlineWorldRegistry
    {
        private static readonly HashSet<HavenlineResourceNode> Resources = new();
        private static readonly HashSet<HavenlineConstructionSite> ConstructionSites = new();
        private static readonly HashSet<HavenlineBarricade> Barricades = new();
        private static readonly HashSet<HavenlineEnemy> Enemies = new();

        internal static IEnumerable<HavenlineResourceNode> ActiveResources => Resources;
        internal static IEnumerable<HavenlineConstructionSite> ActiveConstructionSites => ConstructionSites;
        internal static IEnumerable<HavenlineBarricade> ActiveBarricades => Barricades;
        internal static IEnumerable<HavenlineEnemy> ActiveEnemies => Enemies;

        internal static void Register(HavenlineResourceNode value) => Resources.Add(value);
        internal static void Register(HavenlineConstructionSite value) => ConstructionSites.Add(value);
        internal static void Register(HavenlineBarricade value) => Barricades.Add(value);
        internal static void Register(HavenlineEnemy value) => Enemies.Add(value);
        internal static void Unregister(HavenlineResourceNode value) => Resources.Remove(value);
        internal static void Unregister(HavenlineConstructionSite value) => ConstructionSites.Remove(value);
        internal static void Unregister(HavenlineBarricade value) => Barricades.Remove(value);
        internal static void Unregister(HavenlineEnemy value) => Enemies.Remove(value);

        internal static int AliveEnemyCount
        {
            get
            {
                var count = 0;
                foreach (var enemy in Enemies)
                {
                    if (enemy != null && enemy.isActiveAndEnabled && enemy.IsAlive)
                        count++;
                }
                return count;
            }
        }

        internal static HavenlineEnemy ClosestEnemy(Vector3 origin, float maximumDistance = float.PositiveInfinity)
        {
            HavenlineEnemy best = null;
            var bestSqr = maximumDistance * maximumDistance;
            foreach (var enemy in Enemies)
            {
                if (enemy == null || !enemy.isActiveAndEnabled || !enemy.IsAlive)
                    continue;
                var sqr = HorizontalSqr(origin, enemy.transform.position);
                if (sqr >= bestSqr)
                    continue;
                best = enemy;
                bestSqr = sqr;
            }
            return best;
        }

        internal static HavenlineResourceNode ClosestResource(Vector3 origin)
        {
            HavenlineResourceNode best = null;
            var bestSqr = float.PositiveInfinity;
            foreach (var resource in Resources)
            {
                if (resource == null || !resource.isActiveAndEnabled || resource.Remaining <= 0)
                    continue;
                var sqr = HorizontalSqr(origin, resource.transform.position);
                if (sqr >= bestSqr)
                    continue;
                best = resource;
                bestSqr = sqr;
            }
            return best;
        }

        internal static HavenlineConstructionSite ClosestIncompleteConstruction(Vector3 origin)
        {
            HavenlineConstructionSite best = null;
            var bestSqr = float.PositiveInfinity;
            foreach (var site in ConstructionSites)
            {
                if (site == null || !site.isActiveAndEnabled || site.IsBuilt)
                    continue;
                var sqr = HorizontalSqr(origin, site.transform.position);
                if (sqr >= bestSqr)
                    continue;
                best = site;
                bestSqr = sqr;
            }
            return best;
        }

        internal static HavenlineBarricade MostDamagedBarricade(Vector3 origin)
        {
            HavenlineBarricade best = null;
            var bestScore = float.PositiveInfinity;
            foreach (var barricade in Barricades)
            {
                if (barricade == null || !barricade.isActiveAndEnabled || !barricade.IsBuilt)
                    continue;
                var score = barricade.HealthFraction * 100f + Mathf.Sqrt(HorizontalSqr(origin, barricade.transform.position));
                if (score >= bestScore)
                    continue;
                best = barricade;
                bestScore = score;
            }
            return best;
        }

        internal static HavenlineBarricade ClosestStandingBarricade(Vector3 origin)
        {
            HavenlineBarricade best = null;
            var bestSqr = float.PositiveInfinity;
            foreach (var barricade in Barricades)
            {
                if (barricade == null || !barricade.isActiveAndEnabled || !barricade.IsBuilt)
                    continue;
                var sqr = HorizontalSqr(origin, barricade.transform.position);
                if (sqr >= bestSqr)
                    continue;
                best = barricade;
                bestSqr = sqr;
            }
            return best;
        }

        internal static HavenlineConstructionSnapshot[] CaptureConstruction()
        {
            var values = new List<HavenlineConstructionSnapshot>(ConstructionSites.Count);
            foreach (var site in ConstructionSites)
            {
                if (site != null)
                    values.Add(site.Capture());
            }
            return values.ToArray();
        }

        internal static HavenlineBarricadeSnapshot[] CaptureBarricades()
        {
            var values = new List<HavenlineBarricadeSnapshot>(Barricades.Count);
            foreach (var barricade in Barricades)
            {
                if (barricade != null && barricade.IsBuilt)
                    values.Add(barricade.Capture());
            }
            return values.ToArray();
        }

        private static float HorizontalSqr(Vector3 a, Vector3 b)
        {
            var x = a.x - b.x;
            var z = a.z - b.z;
            return x * x + z * z;
        }
    }

    internal static class HavenlineEnemyPool
    {
        private static readonly Stack<HavenlineEnemy> Pool = new();

        internal static HavenlineEnemy Spawn(HavenlineEnemy template, Vector3 position)
        {
            if (template == null)
                return null;

            HavenlineEnemy enemy = null;
            while (Pool.Count > 0 && enemy == null)
                enemy = Pool.Pop();

            if (enemy == null)
                enemy = UnityEngine.Object.Instantiate(template, position, Quaternion.identity);
            else
                enemy.transform.SetPositionAndRotation(position, Quaternion.identity);

            enemy.ResetForSpawn();
            enemy.gameObject.SetActive(true);
            return enemy;
        }

        internal static void Return(HavenlineEnemy enemy)
        {
            if (enemy == null)
                return;
            enemy.gameObject.SetActive(false);
            Pool.Push(enemy);
        }
    }

    [Serializable]
    public sealed class HavenlineSaveData
    {
        public int version = 3;
        public Vector3 playerPosition = Reference.PlayerSpawn;
        public HavenlineInventorySnapshot inventory;
        public HavenlineFurnaceSnapshot furnace;
        public HelperState helperState = HelperState.Trapped;
        public bool helperPositionValid;
        public Vector3 helperPosition;
        public HavenlineInventorySnapshot helperInventory;
        public int wave;
        public float waveClock = 48f;
        public string[] builtConstructionIds = Array.Empty<string>();
        public HavenlineConstructionSnapshot[] construction = Array.Empty<HavenlineConstructionSnapshot>();
        public HavenlineBarricadeSnapshot[] barricades = Array.Empty<HavenlineBarricadeSnapshot>();
        public long savedUtcTicks;
    }

    public static class HavenlineSave
    {
        private const string LegacyX = "havenline.player.x";
        private const string LegacyY = "havenline.player.y";
        private const string LegacyZ = "havenline.player.z";
        private static readonly HashSet<string> Built = new(StringComparer.Ordinal);
        private static HavenlineSaveData cached;
        private static float nextSave;
        private static bool dirty = true;
        private static Vector3 lastSavedPosition = Reference.PlayerSpawn;

        private static string SavePath => Path.Combine(Application.persistentDataPath, "havenline-save-v3.json");
        private static string TempPath => SavePath + ".tmp";

        public static void MarkDirty() => dirty = true;

        public static Vector3 LoadPlayerPosition()
        {
            EnsureLoaded();
            return Reference.IsValidSavedPosition(cached.playerPosition) ? cached.playerPosition : Reference.PlayerSpawn;
        }

        public static HavenlineInventorySnapshot LoadInventory()
        {
            EnsureLoaded();
            return cached.inventory;
        }

        public static HavenlineFurnaceSnapshot LoadFurnace()
        {
            EnsureLoaded();
            if (cached.furnace.level <= 0)
                cached.furnace.level = 1;
            return cached.furnace;
        }

        public static HelperState LoadHelperState()
        {
            EnsureLoaded();
            return cached.helperState;
        }

        public static Vector3 LoadHelperPosition(Vector3 fallback)
        {
            EnsureLoaded();
            return cached.helperPositionValid && Reference.IsValidSavedPosition(cached.helperPosition)
                ? cached.helperPosition
                : fallback;
        }

        public static HavenlineInventorySnapshot LoadHelperInventory()
        {
            EnsureLoaded();
            return cached.helperInventory;
        }

        public static void LoadDirector(out int wave, out float waveClock)
        {
            EnsureLoaded();
            wave = Mathf.Max(0, cached.wave);
            waveClock = cached.waveClock > 0f ? cached.waveClock : 48f;
        }

        public static HavenlineConstructionSnapshot LoadConstruction(string id)
        {
            EnsureLoaded();
            if (cached.construction != null)
            {
                foreach (var snapshot in cached.construction)
                {
                    if (string.Equals(snapshot.id, id, StringComparison.Ordinal))
                        return snapshot;
                }
            }
            return new HavenlineConstructionSnapshot { id = id, built = Built.Contains(id) };
        }

        public static float LoadBarricadeHealth(string id, float fallback)
        {
            EnsureLoaded();
            if (cached.barricades != null)
            {
                foreach (var snapshot in cached.barricades)
                {
                    if (string.Equals(snapshot.id, id, StringComparison.Ordinal))
                        return Mathf.Clamp(snapshot.health, 0f, fallback);
                }
            }
            return fallback;
        }

        public static bool IsConstructionBuilt(string id)
        {
            EnsureLoaded();
            return !string.IsNullOrWhiteSpace(id) && Built.Contains(id);
        }

        public static void MarkConstructionBuilt(string id)
        {
            if (string.IsNullOrWhiteSpace(id))
                return;
            EnsureLoaded();
            Built.Add(id);
            cached.builtConstructionIds = CopyBuiltIds();
            dirty = true;
        }

        public static void MaybeSave(HavenlinePlayerController player)
        {
            if (player == null || !Reference.IsValidSavedPosition(player.transform.position))
                return;

            var moved = (player.transform.position - lastSavedPosition).sqrMagnitude > 0.25f;
            if ((!dirty && !moved) || Time.unscaledTime < nextSave)
                return;

            SaveNow(player);
        }

        public static void SaveNow(HavenlinePlayerController player)
        {
            if (player == null)
                return;

            EnsureLoaded();
            cached.playerPosition = Reference.IsValidSavedPosition(player.transform.position)
                ? player.transform.position
                : Reference.PlayerSpawn;
            cached.inventory = player.Inventory.Capture();

            var furnace = HavenlineFurnace.Instance;
            if (furnace != null)
                cached.furnace = furnace.Capture();

            var helper = UnityEngine.Object.FindFirstObjectByType<HavenlineHelper>();
            if (helper != null)
            {
                cached.helperState = helper.State;
                cached.helperPosition = helper.transform.position;
                cached.helperPositionValid = Reference.IsValidSavedPosition(helper.transform.position);
                cached.helperInventory = helper.Inventory.Capture();
            }

            var director = UnityEngine.Object.FindFirstObjectByType<HavenlineGameDirector>();
            if (director != null)
            {
                cached.wave = director.Wave;
                cached.waveClock = director.WaveClock;
            }

            cached.builtConstructionIds = CopyBuiltIds();
            cached.construction = HavenlineWorldRegistry.CaptureConstruction();
            cached.barricades = HavenlineWorldRegistry.CaptureBarricades();
            cached.savedUtcTicks = DateTime.UtcNow.Ticks;
            WriteAtomic(cached);

            lastSavedPosition = cached.playerPosition;
            nextSave = Time.unscaledTime + 8f;
            dirty = false;
        }

        public static void ResetAll()
        {
            cached = new HavenlineSaveData();
            cached.furnace.level = 1;
            Built.Clear();
            dirty = true;
            nextSave = 0f;
            lastSavedPosition = Reference.PlayerSpawn;
            DeleteIfExists(SavePath);
            DeleteIfExists(TempPath);
        }

        private static void EnsureLoaded()
        {
            if (cached != null)
                return;

            cached = ReadSave() ?? MigrateLegacyPosition();
            cached.version = 3;
            cached.builtConstructionIds ??= Array.Empty<string>();
            cached.construction ??= Array.Empty<HavenlineConstructionSnapshot>();
            cached.barricades ??= Array.Empty<HavenlineBarricadeSnapshot>();
            Built.Clear();
            foreach (var id in cached.builtConstructionIds)
            {
                if (!string.IsNullOrWhiteSpace(id))
                    Built.Add(id);
            }
            lastSavedPosition = cached.playerPosition;
        }

        private static HavenlineSaveData ReadSave()
        {
            try
            {
                if (!File.Exists(SavePath))
                    return null;
                var parsed = JsonUtility.FromJson<HavenlineSaveData>(File.ReadAllText(SavePath));
                if (parsed == null || parsed.version > 3)
                    return null;
                return parsed;
            }
            catch (Exception exception)
            {
                Debug.LogWarning($"HAVENLINE save recovery: {exception.Message}");
                return null;
            }
        }

        private static HavenlineSaveData MigrateLegacyPosition()
        {
            var data = new HavenlineSaveData();
            data.furnace.level = 1;
            if (!PlayerPrefs.HasKey(LegacyX))
                return data;

            var legacy = new Vector3(
                PlayerPrefs.GetFloat(LegacyX),
                PlayerPrefs.GetFloat(LegacyY),
                PlayerPrefs.GetFloat(LegacyZ));
            data.playerPosition = Reference.IsValidSavedPosition(legacy) ? legacy : Reference.PlayerSpawn;
            return data;
        }

        private static string[] CopyBuiltIds()
        {
            var values = new string[Built.Count];
            Built.CopyTo(values);
            Array.Sort(values, StringComparer.Ordinal);
            return values;
        }

        private static void WriteAtomic(HavenlineSaveData data)
        {
            try
            {
                Directory.CreateDirectory(Application.persistentDataPath);
                File.WriteAllText(TempPath, JsonUtility.ToJson(data, true));
                DeleteIfExists(SavePath);
                File.Move(TempPath, SavePath);
            }
            catch (Exception exception)
            {
                dirty = true;
                Debug.LogError($"HAVENLINE save failed: {exception.Message}");
            }
        }

        private static void DeleteIfExists(string path)
        {
            if (File.Exists(path))
                File.Delete(path);
        }
    }
}
