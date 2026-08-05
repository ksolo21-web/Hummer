using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using UnityEngine;

namespace Havenline
{
    public sealed class HavenlineFurnace : HavenlineInteractable
    {
        public static HavenlineFurnace Instance { get; private set; }

        [SerializeField] private Transform warmthRing;
        [SerializeField] private Light fireLight;
        [SerializeField] private ParticleSystem fireParticles;
        [SerializeField] private HavenlineFlamePulse flameVisual;
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
            Renderer[] snowRenderers,
            HavenlineFlamePulse authoredFlameVisual = null)
        {
            warmthRing = ring;
            fireLight = light;
            fireParticles = particles;
            depositEffect = deliveryEffect;
            levelVisuals = authoredLevelVisuals ?? Array.Empty<GameObject>();
            heatedSnowRenderers = snowRenderers ?? Array.Empty<Renderer>();
            flameVisual = authoredFlameVisual;
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
                fireLight.range = IsOperational ? 6.2f + Level * 1.65f : 2.5f;
                fireLight.intensity = IsOperational ? 1.65f + Level * 0.72f : 0.18f;
            }

            if (fireParticles != null)
            {
                var main = fireParticles.main;
                main.startSizeMultiplier = IsOperational ? 0.16f + Level * 0.035f : 0.04f;
                var emission = fireParticles.emission;
                emission.rateOverTimeMultiplier = IsOperational ? 4f + Level * 3f : 0.5f;
            }

            if (flameVisual != null)
            {
                flameVisual.gameObject.SetActive(IsOperational);
                if (IsOperational)
                    flameVisual.Configure(0.88f + Level * 0.08f);
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
}
