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

    public sealed class HavenlineResourceNode : HavenlineInteractable
    {
        [SerializeField] private ResourceKind kind;
        [SerializeField] private int remaining = 18;
        [SerializeField] private float secondsPerUnit = 0.62f;
        [SerializeField] private GameObject depletedVisual;
        [SerializeField] private ParticleSystem impactEffect;

        private HavenlinePlayerController activeActor;
        private float playerActionElapsed;
        private float helperActionElapsed;

        public ResourceKind Kind => kind;
        public int Remaining => remaining;
        public override AutomaticActionKind ActionKind => kind switch
        {
            ResourceKind.Wood => AutomaticActionKind.GatherWood,
            ResourceKind.Stone => AutomaticActionKind.GatherStone,
            ResourceKind.Metal => AutomaticActionKind.GatherMetal,
            _ => AutomaticActionKind.GatherFuel
        };
        public override int Priority => 30;
        public override float NormalizedProgress => Mathf.Clamp01(playerActionElapsed / Mathf.Max(0.05f, secondsPerUnit));
        public override string ContextLabel => kind switch
        {
            ResourceKind.Wood => "Chopping",
            ResourceKind.Stone => "Mining",
            ResourceKind.Metal => "Salvaging",
            _ => "Collecting fuel"
        };

        protected override void OnEnable()
        {
            base.OnEnable();
            HavenlineWorldRegistry.Register(this);
        }

        protected override void OnDisable()
        {
            HavenlineWorldRegistry.Unregister(this);
            base.OnDisable();
        }

        public void Configure(ResourceKind resourceKind, int units)
        {
            kind = resourceKind;
            remaining = Mathf.Max(0, units);
            ApplyDepletedState();
        }

        public void Configure(ResourceKind resourceKind, int units, float actionSeconds, ParticleSystem effect, GameObject depleted)
        {
            kind = resourceKind;
            remaining = Mathf.Max(0, units);
            secondsPerUnit = Mathf.Max(0.12f, actionSeconds);
            impactEffect = effect;
            depletedVisual = depleted;
            ApplyDepletedState();
        }

        public override bool CanInteract(HavenlinePlayerController actor) =>
            remaining > 0 && actor != null && actor.Inventory != null && !actor.Inventory.IsFull;

        public override void OnSelected(HavenlinePlayerController actor)
        {
            activeActor = actor;
            playerActionElapsed = 0f;
        }

        public override void OnDeselected(HavenlinePlayerController actor)
        {
            activeActor = null;
            playerActionElapsed = 0f;
        }

        public override void TickInteraction(HavenlinePlayerController actor, float deltaTime)
        {
            if (!CanInteract(actor))
                return;
            if (activeActor != actor)
                OnSelected(actor);

            var impact = ConsumeImpact(actor.ActorAnimator, ref playerActionElapsed, secondsPerUnit, deltaTime);
            if (!impact || actor.Inventory.Add(kind, 1) <= 0)
                return;

            impactEffect?.Play(true);
            remaining--;
            HavenlineSave.MarkDirty();
            ApplyDepletedState();
        }

        public bool GatherForHelper(HavenlineInventory inventory, HavenlineActorAnimator animator, float deltaTime)
        {
            if (remaining <= 0 || inventory == null || inventory.IsFull)
                return false;
            if (!ConsumeImpact(animator, ref helperActionElapsed, secondsPerUnit, deltaTime))
                return true;

            if (inventory.Add(kind, 1) > 0)
            {
                impactEffect?.Play(true);
                remaining--;
                HavenlineSave.MarkDirty();
                ApplyDepletedState();
            }
            return remaining > 0;
        }

        private void ApplyDepletedState()
        {
            if (depletedVisual != null)
                depletedVisual.SetActive(remaining <= 0);
            if (remaining <= 0 && gameObject.activeSelf)
                gameObject.SetActive(false);
        }

        private static bool ConsumeImpact(HavenlineActorAnimator animator, ref float elapsed, float seconds, float deltaTime)
        {
            if (animator != null)
                return animator.ConsumeImpact(ref elapsed, seconds);
            elapsed += deltaTime;
            if (elapsed < seconds)
                return false;
            elapsed = 0f;
            return true;
        }
    }

    public sealed class HavenlineFurnace : HavenlineInteractable
    {
        public static HavenlineFurnace Instance { get; private set; }

        [SerializeField] private Transform warmthRing;
        [SerializeField] private Light fireLight;
        [SerializeField] private ParticleSystem fireParticles;
        [SerializeField] private ParticleSystem depositEffect;
        [SerializeField] private GameObject[] levelVisuals = Array.Empty<GameObject>();
        [SerializeField] private Renderer[] heatedSnowRenderers = Array.Empty<Renderer>();
        [SerializeField] private float maxDurability = 260f;

        private readonly Dictionary<ResourceKind, int> stored = new();
        private float interactionElapsed;

        public int Level { get; private set; } = 1;
        public float WarmthRadius { get; private set; } = 4.5f;
        public float Durability { get; private set; }
        public float DurabilityFraction => maxDurability <= 0f ? 0f : Durability / maxDurability;
        public bool IsOperational => Durability > 0f;
        public bool NeedsRepair => Durability < maxDurability - 0.01f;
        public override AutomaticActionKind ActionKind => NeedsRepair ? AutomaticActionKind.Repair : AutomaticActionKind.Deposit;
        public override int Priority => NeedsRepair ? 130 : 95;
        public override float InteractionRange => Reference.DepositRadius;
        public override string ContextLabel => NeedsRepair ? "Repairing furnace" : "Delivering supplies";
        public override float NormalizedProgress => Mathf.Repeat(interactionElapsed / (NeedsRepair ? 0.34f : 0.16f), 1f);
        public int Stored(ResourceKind kind) => stored.TryGetValue(kind, out var value) ? value : 0;

        public event Action Changed;
        public event Action<int> LevelChanged;
        public event Action<ResourceKind, int> Deposited;
        public event Action<float> DurabilityChanged;

        protected override void OnEnable()
        {
            base.OnEnable();
            Instance = this;
            if (Durability <= 0f)
                Durability = maxDurability;
        }

        protected override void OnDisable()
        {
            if (Instance == this)
                Instance = null;
            base.OnDisable();
        }

        private void Start() => Restore(HavenlineSave.LoadFurnace());

        public void Configure(Transform ring, Light light, ParticleSystem particles)
        {
            warmthRing = ring;
            fireLight = light;
            fireParticles = particles;
            if (Durability <= 0f)
                Durability = maxDurability;
            ApplyVisuals();
        }

        public void Configure(
            Transform ring,
            Light light,
            ParticleSystem particles,
            ParticleSystem deliveryEffect,
            GameObject[] authoredLevelVisuals,
            Renderer[] snowRenderers)
        {
            warmthRing = ring;
            fireLight = light;
            fireParticles = particles;
            depositEffect = deliveryEffect;
            levelVisuals = authoredLevelVisuals ?? Array.Empty<GameObject>();
            heatedSnowRenderers = snowRenderers ?? Array.Empty<Renderer>();
            if (Durability <= 0f)
                Durability = maxDurability;
            ApplyVisuals();
        }

        public override bool CanInteract(HavenlinePlayerController actor)
        {
            if (actor == null || actor.Inventory == null)
                return false;
            if (NeedsRepair)
                return actor.Inventory[ResourceKind.Wood] > 0;
            return actor.Inventory.Total > 0;
        }

        public override void TickInteraction(HavenlinePlayerController actor, float deltaTime)
        {
            if (!CanInteract(actor))
                return;

            var seconds = NeedsRepair ? 0.34f : 0.16f;
            if (!ConsumeImpact(actor.ActorAnimator, ref interactionElapsed, seconds, deltaTime))
                return;

            if (NeedsRepair)
                RepairOne(actor.Inventory);
            else
                DepositOne(actor.Inventory);
        }

        public bool DepositOne(HavenlineInventory inventory)
        {
            if (!IsOperational || inventory == null || !inventory.TryGetFirstCarried(out var kind))
                return false;
            if (inventory.Remove(kind, 1) <= 0)
                return false;

            stored[kind] = Stored(kind) + 1;
            depositEffect?.Play(true);
            Deposited?.Invoke(kind, 1);
            RecalculateLevel();
            HavenlineSave.MarkDirty();
            Changed?.Invoke();
            return true;
        }

        public void Deposit(HavenlineInventory inventory)
        {
            while (DepositOne(inventory)) { }
        }

        public bool RepairOne(HavenlineInventory inventory)
        {
            if (inventory == null || Durability >= maxDurability || inventory.Remove(ResourceKind.Wood, 1) <= 0)
                return false;

            Durability = Mathf.Min(maxDurability, Durability + 42f);
            depositEffect?.Play(true);
            DurabilityChanged?.Invoke(DurabilityFraction);
            HavenlineSave.MarkDirty();
            ApplyVisuals();
            return true;
        }

        public void Damage(float amount)
        {
            if (amount <= 0f || Durability <= 0f)
                return;

            Durability = Mathf.Max(0f, Durability - amount);
            DurabilityChanged?.Invoke(DurabilityFraction);
            HavenlineSave.MarkDirty();
            ApplyVisuals();
        }

        public HavenlineFurnaceSnapshot Capture() => new()
        {
            level = Level,
            wood = Stored(ResourceKind.Wood),
            stone = Stored(ResourceKind.Stone),
            metal = Stored(ResourceKind.Metal),
            fuel = Stored(ResourceKind.Fuel),
            hasDurability = true,
            durability = Durability
        };

        public void Restore(HavenlineFurnaceSnapshot snapshot)
        {
            stored.Clear();
            stored[ResourceKind.Wood] = Mathf.Max(0, snapshot.wood);
            stored[ResourceKind.Stone] = Mathf.Max(0, snapshot.stone);
            stored[ResourceKind.Metal] = Mathf.Max(0, snapshot.metal);
            stored[ResourceKind.Fuel] = Mathf.Max(0, snapshot.fuel);
            Level = Mathf.Clamp(snapshot.level <= 0 ? 1 : snapshot.level, 1, 4);
            Durability = snapshot.hasDurability ? Mathf.Clamp(snapshot.durability, 0f, maxDurability) : maxDurability;
            RecalculateLevel();
            ApplyVisuals();
            Changed?.Invoke();
        }

        private void RecalculateLevel()
        {
            var previous = Level;
            if (Stored(ResourceKind.Wood) >= 18 && Stored(ResourceKind.Stone) >= 6)
                Level = Mathf.Max(Level, 2);
            if (Stored(ResourceKind.Wood) >= 38 && Stored(ResourceKind.Stone) >= 16)
                Level = Mathf.Max(Level, 3);
            if (Stored(ResourceKind.Wood) >= 64 && Stored(ResourceKind.Stone) >= 28 && Stored(ResourceKind.Metal) >= 6)
                Level = 4;

            WarmthRadius = 4.5f + (Level - 1) * 3.5f;
            ApplyVisuals();
            if (Level != previous)
                LevelChanged?.Invoke(Level);
        }

        private void ApplyVisuals()
        {
            WarmthRadius = 4.5f + (Mathf.Max(1, Level) - 1) * 3.5f;
            if (warmthRing != null)
                warmthRing.localScale = Vector3.one * WarmthRadius;

            if (fireLight != null)
            {
                fireLight.range = IsOperational ? 7f + Level * 2.4f : 2.5f;
                fireLight.intensity = IsOperational ? 2.9f + Level * 1.35f : 0.18f;
            }

            if (fireParticles != null)
            {
                var main = fireParticles.main;
                main.startSizeMultiplier = IsOperational ? 0.72f + Level * 0.18f : 0.12f;
                var emission = fireParticles.emission;
                emission.rateOverTimeMultiplier = IsOperational ? 18f + Level * 7f : 1f;
            }

            for (var index = 0; index < levelVisuals.Length; index++)
            {
                if (levelVisuals[index] != null)
                    levelVisuals[index].SetActive(index == Mathf.Clamp(Level - 1, 0, levelVisuals.Length - 1));
            }

            var thaw = IsOperational ? Mathf.InverseLerp(1f, 4f, Level) : 0f;
            foreach (var renderer in heatedSnowRenderers)
            {
                if (renderer == null)
                    continue;
                var block = new MaterialPropertyBlock();
                renderer.GetPropertyBlock(block);
                block.SetFloat("_HavenlineThaw", thaw);
                renderer.SetPropertyBlock(block);
            }
        }

        private static bool ConsumeImpact(HavenlineActorAnimator animator, ref float elapsed, float seconds, float deltaTime)
        {
            if (animator != null)
                return animator.ConsumeImpact(ref elapsed, seconds);
            elapsed += deltaTime;
            if (elapsed < seconds)
                return false;
            elapsed = 0f;
            return true;
        }
    }

    public sealed class HavenlineBarricade : HavenlineInteractable
    {
        [SerializeField] private string barricadeId;
        [SerializeField] private float maxHealth = 160f;
        [SerializeField] private Renderer[] renderers = Array.Empty<Renderer>();
        [SerializeField] private GameObject[] damageStages = Array.Empty<GameObject>();
        [SerializeField] private ParticleSystem repairEffect;

        private float repairElapsed;

        public string BarricadeId => string.IsNullOrWhiteSpace(barricadeId) ? name : barricadeId;
        public float Health { get; private set; }
        public bool IsBuilt => Health > 0f;
        public float HealthFraction => maxHealth <= 0f ? 0f : Health / maxHealth;
        public override AutomaticActionKind ActionKind => AutomaticActionKind.Repair;
        public override int Priority => 70;
        public override float InteractionRange => Reference.BuildRadius;
        public override string ContextLabel => "Repairing barricade";
        public override float NormalizedProgress => Mathf.Repeat(repairElapsed / 0.34f, 1f);

        protected override void OnEnable()
        {
            base.OnEnable();
            HavenlineWorldRegistry.Register(this);
        }

        protected override void OnDisable()
        {
            HavenlineWorldRegistry.Unregister(this);
            base.OnDisable();
        }

        private void Awake()
        {
            if (string.IsNullOrWhiteSpace(barricadeId))
                barricadeId = name;
            if (renderers == null || renderers.Length == 0)
                renderers = GetComponentsInChildren<Renderer>(true);
            Health = maxHealth;
            Apply();
        }

        private void Start()
        {
            Health = HavenlineSave.LoadBarricadeHealth(BarricadeId, maxHealth);
            Apply();
        }

        public void Configure(string id, float maximumHealth)
        {
            barricadeId = string.IsNullOrWhiteSpace(id) ? name : id;
            maxHealth = Mathf.Max(1f, maximumHealth);
            Health = maxHealth;
            Apply();
        }

        public override bool CanInteract(HavenlinePlayerController actor) =>
            actor != null && Health > 0f && Health < maxHealth && actor.Inventory[ResourceKind.Wood] > 0;

        public override void TickInteraction(HavenlinePlayerController actor, float deltaTime)
        {
            if (ConsumeImpact(actor.ActorAnimator, ref repairElapsed, 0.34f, deltaTime))
                Repair(actor.Inventory);
        }

        public void Damage(float amount)
        {
            Health = Mathf.Max(0f, Health - Mathf.Max(0f, amount));
            HavenlineSave.MarkDirty();
            Apply();
        }

        public bool Repair(HavenlineInventory inventory)
        {
            if (Health >= maxHealth || inventory == null || inventory.Remove(ResourceKind.Wood, 1) <= 0)
                return false;
            Health = Mathf.Min(maxHealth, Health + 28f);
            repairEffect?.Play(true);
            HavenlineSave.MarkDirty();
            Apply();
            return true;
        }

        public HavenlineBarricadeSnapshot Capture() => new()
        {
            id = BarricadeId,
            health = Health
        };

        private void Apply()
        {
            var stage = HealthFraction <= 0.05f ? 0 : HealthFraction < 0.45f ? 1 : HealthFraction < 0.8f ? 2 : 3;
            for (var index = 0; index < damageStages.Length; index++)
            {
                if (damageStages[index] != null)
                    damageStages[index].SetActive(index == Mathf.Min(stage, damageStages.Length - 1));
            }
            foreach (var renderer in renderers)
            {
                if (renderer == null)
                    continue;
                var block = new MaterialPropertyBlock();
                renderer.GetPropertyBlock(block);
                block.SetFloat("_Damage", 1f - HealthFraction);
                renderer.SetPropertyBlock(block);
            }
        }

        private static bool ConsumeImpact(HavenlineActorAnimator animator, ref float elapsed, float seconds, float deltaTime)
        {
            if (animator != null)
                return animator.ConsumeImpact(ref elapsed, seconds);
            elapsed += deltaTime;
            if (elapsed < seconds)
                return false;
            elapsed = 0f;
            return true;
        }
    }

    public sealed class HavenlineConstructionSite : HavenlineInteractable
    {
        [SerializeField] private string buildId = "barricade_north";
        [SerializeField] private int requiredWood = 8;
        [SerializeField] private int requiredStone = 3;
        [SerializeField] private GameObject[] constructionStages = Array.Empty<GameObject>();
        [SerializeField] private GameObject completedStructure;
        [SerializeField] private ParticleSystem buildEffect;

        private int deliveredWood;
        private int deliveredStone;
        private float buildElapsed;

        public bool IsBuilt { get; private set; }
        public string BuildId => buildId;
        public int DeliveredWood => deliveredWood;
        public int DeliveredStone => deliveredStone;
        public override AutomaticActionKind ActionKind => AutomaticActionKind.Build;
        public override int Priority => 80;
        public override float InteractionRange => Reference.BuildRadius;
        public override string ContextLabel => $"Building {name}";
        public override float NormalizedProgress => Mathf.Clamp01(
            (deliveredWood + deliveredStone) / (float)Mathf.Max(1, requiredWood + requiredStone));

        protected override void OnEnable()
        {
            base.OnEnable();
            HavenlineWorldRegistry.Register(this);
        }

        protected override void OnDisable()
        {
            HavenlineWorldRegistry.Unregister(this);
            base.OnDisable();
        }

        public void Configure(
            string id,
            int wood,
            int stone,
            GameObject[] stages,
            GameObject completed,
            ParticleSystem effect)
        {
            buildId = string.IsNullOrWhiteSpace(id) ? name : id;
            requiredWood = Mathf.Max(0, wood);
            requiredStone = Mathf.Max(0, stone);
            constructionStages = stages ?? Array.Empty<GameObject>();
            completedStructure = completed;
            buildEffect = effect;
            ApplyVisuals();
        }

        private void Start()
        {
            var snapshot = HavenlineSave.LoadConstruction(buildId);
            deliveredWood = Mathf.Clamp(snapshot.deliveredWood, 0, requiredWood);
            deliveredStone = Mathf.Clamp(snapshot.deliveredStone, 0, requiredStone);
            IsBuilt = snapshot.built || HavenlineSave.IsConstructionBuilt(buildId);
            if (IsBuilt)
            {
                deliveredWood = requiredWood;
                deliveredStone = requiredStone;
            }
            ApplyVisuals();
        }

        public override bool CanInteract(HavenlinePlayerController actor)
        {
            if (IsBuilt || actor == null)
                return false;
            return (deliveredWood < requiredWood && actor.Inventory[ResourceKind.Wood] > 0) ||
                   (deliveredStone < requiredStone && actor.Inventory[ResourceKind.Stone] > 0);
        }

        public override void TickInteraction(HavenlinePlayerController actor, float deltaTime)
        {
            if (ConsumeImpact(actor.ActorAnimator, ref buildElapsed, 0.24f, deltaTime))
                Contribute(actor.Inventory);
        }

        public bool ContributeForHelper(HavenlineInventory inventory, HavenlineActorAnimator animator, float deltaTime)
        {
            if (IsBuilt || inventory == null)
                return false;
            if (ConsumeImpact(animator, ref buildElapsed, 0.32f, deltaTime))
                Contribute(inventory);
            return !IsBuilt;
        }

        public bool Needs(ResourceKind kind) => kind switch
        {
            ResourceKind.Wood => deliveredWood < requiredWood,
            ResourceKind.Stone => deliveredStone < requiredStone,
            _ => false
        };

        public HavenlineConstructionSnapshot Capture() => new()
        {
            id = buildId,
            deliveredWood = deliveredWood,
            deliveredStone = deliveredStone,
            built = IsBuilt
        };

        private void Contribute(HavenlineInventory inventory)
        {
            if (deliveredWood < requiredWood && inventory.Remove(ResourceKind.Wood, 1) > 0)
                deliveredWood++;
            else if (deliveredStone < requiredStone && inventory.Remove(ResourceKind.Stone, 1) > 0)
                deliveredStone++;
            else
                return;

            buildEffect?.Play(true);
            if (deliveredWood >= requiredWood && deliveredStone >= requiredStone)
            {
                IsBuilt = true;
                HavenlineSave.MarkConstructionBuilt(buildId);
            }
            HavenlineSave.MarkDirty();
            ApplyVisuals();
        }

        private void ApplyVisuals()
        {
            var visibleStage = constructionStages.Length == 0
                ? -1
                : Mathf.Clamp(Mathf.FloorToInt(NormalizedProgress * constructionStages.Length), 0, constructionStages.Length - 1);
            for (var index = 0; index < constructionStages.Length; index++)
            {
                if (constructionStages[index] != null)
                    constructionStages[index].SetActive(!IsBuilt && index == visibleStage);
            }
            if (completedStructure != null)
                completedStructure.SetActive(IsBuilt);
        }

        private static bool ConsumeImpact(HavenlineActorAnimator animator, ref float elapsed, float seconds, float deltaTime)
        {
            if (animator != null)
                return animator.ConsumeImpact(ref elapsed, seconds);
            elapsed += deltaTime;
            if (elapsed < seconds)
                return false;
            elapsed = 0f;
            return true;
        }
    }

    [RequireComponent(typeof(CharacterController), typeof(HavenlineInventory))]
    public sealed class HavenlineHelper : HavenlineInteractable
    {
        [SerializeField] private Transform visual;
        [SerializeField] private HavenlineActorAnimator animator;
        [SerializeField] private ParticleSystem rescueEffect;

        private CharacterController controller;
        private HavenlineInventory inventory;
        private HavenlinePlayerController player;
        private HavenlineResourceNode targetResource;
        private HavenlineEnemy targetEnemy;
        private HavenlineBarricade targetBarricade;
        private HavenlineConstructionSite targetConstruction;
        private float decisionClock;
        private float rescueElapsed;
        private float actionElapsed;

        public HelperState State { get; private set; } = HelperState.Trapped;
        public HavenlineInventory Inventory => inventory;
        public override AutomaticActionKind ActionKind => AutomaticActionKind.Rescue;
        public override int Priority => 110;
        public override float InteractionRange => Reference.RescueRadius;
        public override string ContextLabel => "Rescuing survivor";
        public override float NormalizedProgress => Mathf.Clamp01(rescueElapsed / 2.2f);

        public void Configure(Transform visualRoot, HavenlineActorAnimator actorAnimator)
        {
            visual = visualRoot;
            animator = actorAnimator;
        }

        private void Awake()
        {
            controller = GetComponent<CharacterController>();
            if (controller == null)
                controller = gameObject.AddComponent<CharacterController>();
            inventory = GetComponent<HavenlineInventory>();
            if (inventory == null)
                inventory = gameObject.AddComponent<HavenlineInventory>();
            inventory.Changed += HavenlineSave.MarkDirty;
        }

        private void OnDestroy()
        {
            if (inventory != null)
                inventory.Changed -= HavenlineSave.MarkDirty;
        }

        private void Start()
        {
            State = HavenlineSave.LoadHelperState();
            if (State == HelperState.Rescuing)
                State = HelperState.Trapped;
            inventory.Restore(HavenlineSave.LoadHelperInventory());
            var savedPosition = HavenlineSave.LoadHelperPosition(transform.position);
            if (Reference.IsValidSavedPosition(savedPosition))
                transform.position = savedPosition;
        }

        public override bool CanInteract(HavenlinePlayerController actor) =>
            State == HelperState.Trapped && HavenlineFurnace.Instance != null &&
            HavenlineFurnace.Instance.IsOperational && HavenlineFurnace.Instance.Level >= 2;

        public override void OnSelected(HavenlinePlayerController actor)
        {
            rescueElapsed = 0f;
            SetState(HelperState.Rescuing, AutomaticActionKind.Rescue);
        }

        public override void OnDeselected(HavenlinePlayerController actor)
        {
            if (State == HelperState.Rescuing)
                State = HelperState.Trapped;
            rescueElapsed = 0f;
            animator?.EndAction();
        }

        public override void TickInteraction(HavenlinePlayerController actor, float deltaTime)
        {
            if (State != HelperState.Rescuing)
                SetState(HelperState.Rescuing, AutomaticActionKind.Rescue);
            rescueElapsed += deltaTime;
            if (rescueElapsed < 2.2f)
                return;

            rescueElapsed = 2.2f;
            State = HelperState.Following;
            rescueEffect?.Play(true);
            animator?.EndAction();
            HavenlineSave.MarkDirty();
            HavenlineSave.SaveNow(actor);
        }

        private void Update()
        {
            if (State == HelperState.Trapped || State == HelperState.Rescuing)
            {
                animator?.SetMotion(0f);
                return;
            }

            if (player == null)
                player = FindFirstObjectByType<HavenlinePlayerController>();
            var furnace = HavenlineFurnace.Instance;
            if (player == null || furnace == null)
                return;

            decisionClock -= Time.deltaTime;
            if (decisionClock <= 0f)
            {
                decisionClock = 0.35f;
                RefreshTargets();
            }

            if (targetEnemy != null && targetEnemy.IsAlive &&
                Vector3.Distance(targetEnemy.transform.position, transform.position) < 7f)
            {
                SetState(HelperState.Defending, AutomaticActionKind.Combat);
                MoveToward(targetEnemy.transform.position, 3.75f);
                if (Vector3.Distance(targetEnemy.transform.position, transform.position) < 2.3f &&
                    ConsumeImpact(animator, ref actionElapsed, 0.9f, Time.deltaTime))
                    targetEnemy.Damage(18f);
                return;
            }

            if (furnace.NeedsRepair && inventory[ResourceKind.Wood] > 0)
            {
                SetState(HelperState.Repairing, AutomaticActionKind.Repair);
                MoveToward(furnace.transform.position, 3.3f);
                if (Vector3.Distance(transform.position, furnace.transform.position) < Reference.DepositRadius &&
                    ConsumeImpact(animator, ref actionElapsed, 0.42f, Time.deltaTime))
                    furnace.RepairOne(inventory);
                return;
            }

            if (targetConstruction != null && !targetConstruction.IsBuilt &&
                ((targetConstruction.Needs(ResourceKind.Wood) && inventory[ResourceKind.Wood] > 0) ||
                 (targetConstruction.Needs(ResourceKind.Stone) && inventory[ResourceKind.Stone] > 0)))
            {
                SetState(HelperState.Building, AutomaticActionKind.Build);
                MoveToward(targetConstruction.transform.position, 3.25f);
                if (Vector3.Distance(transform.position, targetConstruction.transform.position) < Reference.BuildRadius)
                    targetConstruction.ContributeForHelper(inventory, animator, Time.deltaTime);
                return;
            }

            if (targetBarricade != null && targetBarricade.HealthFraction < 0.75f && inventory[ResourceKind.Wood] > 0)
            {
                SetState(HelperState.Repairing, AutomaticActionKind.Repair);
                MoveToward(targetBarricade.transform.position, 3.2f);
                if (Vector3.Distance(transform.position, targetBarricade.transform.position) < Reference.BuildRadius &&
                    ConsumeImpact(animator, ref actionElapsed, 0.42f, Time.deltaTime))
                    targetBarricade.Repair(inventory);
                return;
            }

            if (inventory.Total > 0)
            {
                SetState(HelperState.Delivering, AutomaticActionKind.Deposit);
                MoveToward(furnace.transform.position, 3.3f);
                if (Vector3.Distance(transform.position, furnace.transform.position) < Reference.DepositRadius &&
                    ConsumeImpact(animator, ref actionElapsed, 0.2f, Time.deltaTime))
                    furnace.DepositOne(inventory);
                return;
            }

            if (targetResource != null && targetResource.isActiveAndEnabled && targetResource.Remaining > 0)
            {
                SetState(HelperState.Gathering, targetResource.ActionKind);
                MoveToward(targetResource.transform.position, 3.15f);
                if (Vector3.Distance(transform.position, targetResource.transform.position) < targetResource.InteractionRange)
                    targetResource.GatherForHelper(inventory, animator, Time.deltaTime);
                return;
            }

            SetState(HelperState.Following, AutomaticActionKind.None);
            MoveToward(player.transform.position + new Vector3(-1.35f, 0f, -1.05f), 3.2f);
        }

        private void RefreshTargets()
        {
            targetEnemy = HavenlineWorldRegistry.ClosestEnemy(transform.position, 9f);
            targetConstruction = HavenlineWorldRegistry.ClosestIncompleteConstruction(transform.position);
            targetBarricade = HavenlineWorldRegistry.MostDamagedBarricade(transform.position);
            if (targetResource == null || !targetResource.isActiveAndEnabled || targetResource.Remaining <= 0)
                targetResource = HavenlineWorldRegistry.ClosestResource(transform.position);
        }

        private void SetState(HelperState state, AutomaticActionKind action)
        {
            if (State == state)
            {
                if (action != AutomaticActionKind.None)
                    animator?.BeginAction(action);
                return;
            }

            State = state;
            actionElapsed = 0f;
            if (action == AutomaticActionKind.None)
                animator?.EndAction();
            else
                animator?.BeginAction(action);
            HavenlineSave.MarkDirty();
        }

        private void MoveToward(Vector3 target, float speed)
        {
            var direction = Vector3.ProjectOnPlane(target - transform.position, Vector3.up);
            if (direction.magnitude < 0.4f)
            {
                animator?.SetMotion(0f);
                return;
            }

            direction.Normalize();
            controller.Move((direction * speed + Physics.gravity) * Time.deltaTime);
            transform.position = Reference.ClampToWorld(transform.position);
            if (visual != null)
            {
                visual.rotation = Quaternion.Slerp(
                    visual.rotation,
                    Quaternion.LookRotation(direction),
                    1f - Mathf.Exp(-Reference.TurnSharpness * Time.deltaTime));
            }
            animator?.SetMotion(1f);
        }

        private static bool ConsumeImpact(HavenlineActorAnimator actorAnimator, ref float elapsed, float seconds, float deltaTime)
        {
            if (actorAnimator != null)
                return actorAnimator.ConsumeImpact(ref elapsed, seconds);
            elapsed += deltaTime;
            if (elapsed < seconds)
                return false;
            elapsed = 0f;
            return true;
        }
    }

    [RequireComponent(typeof(CharacterController))]
    public sealed class HavenlineEnemy : HavenlineInteractable
    {
        [SerializeField] private Transform visual;
        [SerializeField] private HavenlineActorAnimator animator;
        [SerializeField] private float maxHealth = 65f;
        [SerializeField] private ParticleSystem hitEffect;
        [SerializeField] private GameObject droppedResource;

        private CharacterController controller;
        private HavenlineBarricade targetBarricade;
        private float health;
        private float playerHitElapsed;
        private float enemyAttackElapsed;
        private float targetRefreshClock;
        private bool dying;

        public bool IsAlive => health > 0f && !dying;
        public override AutomaticActionKind ActionKind => AutomaticActionKind.Combat;
        public override int Priority => 200;
        public override float InteractionRange => Reference.CombatRadius;
        public override string ContextLabel => "Defending";
        public override float NormalizedProgress => -1f;

        protected override void OnEnable()
        {
            base.OnEnable();
            HavenlineWorldRegistry.Register(this);
        }

        protected override void OnDisable()
        {
            HavenlineWorldRegistry.Unregister(this);
            base.OnDisable();
        }

        private void Awake()
        {
            controller = GetComponent<CharacterController>();
            if (controller == null)
                controller = gameObject.AddComponent<CharacterController>();
            ResetForSpawn();
        }

        public void Configure(Transform visualRoot, HavenlineActorAnimator actorAnimator)
        {
            visual = visualRoot;
            animator = actorAnimator;
        }

        public void ResetForSpawn()
        {
            StopAllCoroutines();
            health = maxHealth;
            dying = false;
            playerHitElapsed = 0f;
            enemyAttackElapsed = 0f;
            targetRefreshClock = 0f;
            targetBarricade = null;
            if (controller != null)
                controller.enabled = true;
        }

        public override bool CanInteract(HavenlinePlayerController actor) => IsAlive;

        public override void TickInteraction(HavenlinePlayerController actor, float deltaTime)
        {
            if (!IsAlive)
                return;
            if (ConsumeImpact(actor.ActorAnimator, ref playerHitElapsed, 0.64f, deltaTime))
                Damage(22f);
        }

        private void Update()
        {
            if (!IsAlive)
                return;

            targetRefreshClock -= Time.deltaTime;
            if (targetRefreshClock <= 0f)
            {
                targetRefreshClock = 0.45f;
                targetBarricade = HavenlineWorldRegistry.ClosestStandingBarricade(transform.position);
            }

            var furnace = HavenlineFurnace.Instance;
            var hasBarricade = targetBarricade != null && targetBarricade.IsBuilt;
            var target = hasBarricade ? targetBarricade.transform.position : furnace != null ? furnace.transform.position : Reference.Furnace;
            var direction = Vector3.ProjectOnPlane(target - transform.position, Vector3.up);
            if (direction.magnitude > 1.55f)
            {
                direction.Normalize();
                controller.Move((direction * 3.9f + Physics.gravity) * Time.deltaTime);
                if (visual != null)
                {
                    visual.rotation = Quaternion.Slerp(
                        visual.rotation,
                        Quaternion.LookRotation(direction),
                        1f - Mathf.Exp(-Reference.TurnSharpness * Time.deltaTime));
                }
                animator?.SetMotion(1f);
                animator?.EndAction();
                return;
            }

            animator?.SetMotion(0f);
            animator?.BeginAction(AutomaticActionKind.Combat);
            if (!ConsumeImpact(animator, ref enemyAttackElapsed, 1.1f, Time.deltaTime))
                return;

            if (hasBarricade)
                targetBarricade.Damage(16f);
            else
                furnace?.Damage(14f);
        }

        public void Damage(float amount)
        {
            if (!IsAlive)
                return;

            health -= Mathf.Max(0f, amount);
            hitEffect?.Play(true);
            animator?.PlayHit();
            if (health > 0f)
                return;

            health = 0f;
            dying = true;
            if (controller != null)
                controller.enabled = false;
            animator?.PlayDeath();
            if (droppedResource != null)
                Instantiate(droppedResource, transform.position, Quaternion.identity);
            StartCoroutine(ReturnAfterDeath());
        }

        private IEnumerator ReturnAfterDeath()
        {
            yield return new WaitForSeconds(1.15f);
            HavenlineEnemyPool.Return(this);
        }

        private static bool ConsumeImpact(HavenlineActorAnimator actorAnimator, ref float elapsed, float seconds, float deltaTime)
        {
            if (actorAnimator != null)
                return actorAnimator.ConsumeImpact(ref elapsed, seconds);
            elapsed += deltaTime;
            if (elapsed < seconds)
                return false;
            elapsed = 0f;
            return true;
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
