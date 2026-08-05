using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
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
    }

    public sealed class HavenlineResourceNode : HavenlineInteractable
    {
        [SerializeField] private ResourceKind kind;
        [SerializeField] private int remaining = 18;
        [SerializeField] private float secondsPerUnit = 0.62f;
        [SerializeField] private GameObject depletedVisual;
        [SerializeField] private ParticleSystem impactEffect;

        private HavenlinePlayerController activeActor;
        private float actionElapsed;
        private float helperElapsed;

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
        public override float NormalizedProgress => Mathf.Clamp01(actionElapsed / Mathf.Max(0.05f, secondsPerUnit));
        public override string ContextLabel => kind switch
        {
            ResourceKind.Wood => "Chopping",
            ResourceKind.Stone => "Mining",
            ResourceKind.Metal => "Salvaging",
            _ => "Collecting fuel"
        };

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
            actionElapsed = 0f;
        }

        public override void OnDeselected(HavenlinePlayerController actor)
        {
            activeActor = null;
            actionElapsed = 0f;
        }

        public override void TickInteraction(HavenlinePlayerController actor, float deltaTime)
        {
            if (!CanInteract(actor))
                return;
            if (activeActor != actor)
                OnSelected(actor);

            var animator = actor.ActorAnimator;
            var impact = animator != null
                ? animator.ConsumeImpact(ref actionElapsed, secondsPerUnit)
                : AdvanceFallback(ref actionElapsed, secondsPerUnit, deltaTime);
            if (!impact)
                return;

            if (actor.Inventory.Add(kind, 1) <= 0)
                return;
            impactEffect?.Play(true);
            remaining--;
            ApplyDepletedState();
        }

        public bool GatherForHelper(HavenlineInventory inventory, HavenlineActorAnimator animator, float deltaTime)
        {
            if (remaining <= 0 || inventory == null || inventory.IsFull)
                return false;
            var impact = animator != null
                ? animator.ConsumeImpact(ref helperElapsed, secondsPerUnit)
                : AdvanceFallback(ref helperElapsed, secondsPerUnit, deltaTime);
            if (!impact)
                return true;
            if (inventory.Add(kind, 1) > 0)
            {
                impactEffect?.Play(true);
                remaining--;
                ApplyDepletedState();
            }
            return remaining > 0;
        }

        private void ApplyDepletedState()
        {
            if (depletedVisual != null)
                depletedVisual.SetActive(remaining <= 0);
            if (remaining <= 0)
                gameObject.SetActive(false);
        }

        private static bool AdvanceFallback(ref float elapsed, float seconds, float deltaTime)
        {
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

        private readonly Dictionary<ResourceKind, int> stored = new();
        private float depositElapsed;

        public int Level { get; private set; } = 1;
        public float WarmthRadius { get; private set; } = 4f;
        public override AutomaticActionKind ActionKind => AutomaticActionKind.Deposit;
        public override int Priority => 95;
        public override float InteractionRange => Reference.DepositRadius;
        public override string ContextLabel => "Delivering supplies";
        public override float NormalizedProgress => Mathf.Repeat(depositElapsed / 0.16f, 1f);
        public int Stored(ResourceKind kind) => stored.TryGetValue(kind, out var value) ? value : 0;

        public event Action Changed;
        public event Action<int> LevelChanged;
        public event Action<ResourceKind, int> Deposited;

        public void Configure(Transform ring, Light light, ParticleSystem particles)
        {
            warmthRing = ring;
            fireLight = light;
            fireParticles = particles;
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
            ApplyVisuals();
        }

        protected override void OnEnable()
        {
            base.OnEnable();
            Instance = this;
        }

        protected override void OnDisable()
        {
            if (Instance == this)
                Instance = null;
            base.OnDisable();
        }

        private void Start()
        {
            Restore(HavenlineSave.LoadFurnace());
        }

        public override bool CanInteract(HavenlinePlayerController actor) =>
            actor != null && actor.Inventory != null && actor.Inventory.Total > 0;

        public override void TickInteraction(HavenlinePlayerController actor, float deltaTime)
        {
            if (!CanInteract(actor))
                return;
            var animator = actor.ActorAnimator;
            var impact = animator != null
                ? animator.ConsumeImpact(ref depositElapsed, 0.16f)
                : Advance(ref depositElapsed, 0.16f, deltaTime);
            if (impact)
                DepositOne(actor.Inventory);
        }

        public bool DepositOne(HavenlineInventory inventory)
        {
            if (inventory == null || !inventory.TryGetFirstCarried(out var kind))
                return false;
            if (inventory.Remove(kind, 1) <= 0)
                return false;

            stored[kind] = Stored(kind) + 1;
            depositEffect?.Play(true);
            Deposited?.Invoke(kind, 1);
            RecalculateLevel();
            Changed?.Invoke();
            return true;
        }

        public void Deposit(HavenlineInventory inventory)
        {
            while (DepositOne(inventory)) { }
        }

        public HavenlineFurnaceSnapshot Capture() => new()
        {
            level = Level,
            wood = Stored(ResourceKind.Wood),
            stone = Stored(ResourceKind.Stone),
            metal = Stored(ResourceKind.Metal),
            fuel = Stored(ResourceKind.Fuel)
        };

        public void Restore(HavenlineFurnaceSnapshot snapshot)
        {
            stored.Clear();
            stored[ResourceKind.Wood] = Mathf.Max(0, snapshot.wood);
            stored[ResourceKind.Stone] = Mathf.Max(0, snapshot.stone);
            stored[ResourceKind.Metal] = Mathf.Max(0, snapshot.metal);
            stored[ResourceKind.Fuel] = Mathf.Max(0, snapshot.fuel);
            Level = Mathf.Clamp(snapshot.level <= 0 ? 1 : snapshot.level, 1, 4);
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

            WarmthRadius = 4f + (Level - 1) * 2.5f;
            ApplyVisuals();
            if (Level != previous)
                LevelChanged?.Invoke(Level);
        }

        private void ApplyVisuals()
        {
            WarmthRadius = 4f + (Mathf.Max(1, Level) - 1) * 2.5f;
            if (warmthRing != null)
                warmthRing.localScale = Vector3.one * WarmthRadius;
            if (fireLight != null)
            {
                fireLight.range = 7f + Level * 2.2f;
                fireLight.intensity = 2.8f + Level * 1.3f;
            }
            if (fireParticles != null)
            {
                var main = fireParticles.main;
                main.startSizeMultiplier = 0.7f + Level * 0.18f;
                var emission = fireParticles.emission;
                emission.rateOverTimeMultiplier = 18f + Level * 7f;
            }
            for (var index = 0; index < levelVisuals.Length; index++)
            {
                if (levelVisuals[index] != null)
                    levelVisuals[index].SetActive(index == Mathf.Clamp(Level - 1, 0, levelVisuals.Length - 1));
            }
            var thaw = Mathf.InverseLerp(1f, 4f, Level);
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

        private static bool Advance(ref float elapsed, float threshold, float deltaTime)
        {
            elapsed += deltaTime;
            if (elapsed < threshold)
                return false;
            elapsed = 0f;
            return true;
        }
    }

    public sealed class HavenlineBarricade : HavenlineInteractable
    {
        [SerializeField] private float maxHealth = 160f;
        [SerializeField] private Renderer[] renderers = Array.Empty<Renderer>();
        [SerializeField] private GameObject[] damageStages = Array.Empty<GameObject>();
        [SerializeField] private ParticleSystem repairEffect;
        private float repairElapsed;

        public float Health { get; private set; }
        public bool IsBuilt => Health > 0f;
        public float HealthFraction => maxHealth <= 0f ? 0f : Health / maxHealth;
        public override AutomaticActionKind ActionKind => AutomaticActionKind.Repair;
        public override int Priority => 70;
        public override float InteractionRange => Reference.BuildRadius;
        public override string ContextLabel => "Repairing barricade";
        public override float NormalizedProgress => Mathf.Repeat(repairElapsed / 0.34f, 1f);

        private void Awake()
        {
            Health = maxHealth * 0.55f;
            if (renderers == null || renderers.Length == 0)
                renderers = GetComponentsInChildren<Renderer>(true);
            Apply();
        }

        public override bool CanInteract(HavenlinePlayerController actor) =>
            actor != null && Health > 0f && Health < maxHealth && actor.Inventory[ResourceKind.Wood] > 0;

        public override void TickInteraction(HavenlinePlayerController actor, float deltaTime)
        {
            var animator = actor.ActorAnimator;
            var impact = animator != null
                ? animator.ConsumeImpact(ref repairElapsed, 0.34f)
                : Advance(ref repairElapsed, 0.34f, deltaTime);
            if (impact)
                Repair(actor.Inventory);
        }

        public void Damage(float amount)
        {
            Health = Mathf.Max(0f, Health - Mathf.Max(0f, amount));
            Apply();
        }

        public bool Repair(HavenlineInventory inventory)
        {
            if (Health >= maxHealth || inventory == null || inventory.Remove(ResourceKind.Wood, 1) <= 0)
                return false;
            Health = Mathf.Min(maxHealth, Health + 28f);
            repairEffect?.Play(true);
            Apply();
            return true;
        }

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

        private static bool Advance(ref float elapsed, float threshold, float deltaTime)
        {
            elapsed += deltaTime;
            if (elapsed < threshold)
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
        public override float NormalizedProgress
        {
            get
            {
                var total = Mathf.Max(1, requiredWood + requiredStone);
                return Mathf.Clamp01((deliveredWood + deliveredStone) / (float)total);
            }
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
            IsBuilt = HavenlineSave.IsConstructionBuilt(buildId);
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
            var animator = actor.ActorAnimator;
            var impact = animator != null
                ? animator.ConsumeImpact(ref buildElapsed, 0.24f)
                : Advance(ref buildElapsed, 0.24f, deltaTime);
            if (impact)
                Contribute(actor.Inventory);
        }

        public bool ContributeForHelper(HavenlineInventory inventory, HavenlineActorAnimator animator, float deltaTime)
        {
            if (IsBuilt || inventory == null)
                return false;
            var impact = animator != null
                ? animator.ConsumeImpact(ref buildElapsed, 0.32f)
                : Advance(ref buildElapsed, 0.32f, deltaTime);
            if (impact)
                Contribute(inventory);
            return !IsBuilt;
        }

        public bool Needs(ResourceKind kind) => kind switch
        {
            ResourceKind.Wood => deliveredWood < requiredWood,
            ResourceKind.Stone => deliveredStone < requiredStone,
            _ => false
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
            ApplyVisuals();
        }

        private void ApplyVisuals()
        {
            var progress = NormalizedProgress;
            var visibleStage = constructionStages.Length == 0
                ? -1
                : Mathf.Clamp(Mathf.FloorToInt(progress * constructionStages.Length), 0, constructionStages.Length - 1);
            for (var index = 0; index < constructionStages.Length; index++)
            {
                if (constructionStages[index] != null)
                    constructionStages[index].SetActive(!IsBuilt && index == visibleStage);
            }
            if (completedStructure != null)
                completedStructure.SetActive(IsBuilt);
        }

        private static bool Advance(ref float elapsed, float threshold, float deltaTime)
        {
            elapsed += deltaTime;
            if (elapsed < threshold)
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
        private HavenlineResourceNode targetResource;
        private HavenlineEnemy targetEnemy;
        private HavenlineBarricade targetBarricade;
        private HavenlineConstructionSite targetConstruction;
        private float decisionClock;
        private float rescueElapsed;
        private float helperActionElapsed;

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
            inventory = GetComponent<HavenlineInventory>();
        }

        private void Start()
        {
            State = HavenlineSave.LoadHelperState();
            if (State == HelperState.Rescuing)
                State = HelperState.Trapped;
        }

        public override bool CanInteract(HavenlinePlayerController actor) =>
            State == HelperState.Trapped && HavenlineFurnace.Instance != null && HavenlineFurnace.Instance.Level >= 2;

        public override void OnSelected(HavenlinePlayerController actor)
        {
            rescueElapsed = 0f;
            State = HelperState.Rescuing;
            animator?.BeginAction(AutomaticActionKind.Rescue);
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
                State = HelperState.Rescuing;
            rescueElapsed += deltaTime;
            if (rescueElapsed < 2.2f)
                return;
            rescueElapsed = 2.2f;
            State = HelperState.Following;
            rescueEffect?.Play(true);
            animator?.EndAction();
            HavenlineSave.SaveNow(actor);
        }

        private void Update()
        {
            if (State == HelperState.Trapped || State == HelperState.Rescuing)
            {
                animator?.SetMotion(0f);
                return;
            }

            var player = FindFirstObjectByType<HavenlinePlayerController>();
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
                State = HelperState.Defending;
                MoveToward(targetEnemy.transform.position, 3.75f);
                if (Vector3.Distance(targetEnemy.transform.position, transform.position) < 2.3f)
                {
                    animator?.BeginAction(AutomaticActionKind.Combat);
                    if (animator == null ? Advance(ref helperActionElapsed, 0.9f, Time.deltaTime) : animator.ConsumeImpact(ref helperActionElapsed, 0.9f))
                        targetEnemy.Damage(18f);
                }
                return;
            }

            if (targetConstruction != null && !targetConstruction.IsBuilt &&
                ((targetConstruction.Needs(ResourceKind.Wood) && inventory[ResourceKind.Wood] > 0) ||
                 (targetConstruction.Needs(ResourceKind.Stone) && inventory[ResourceKind.Stone] > 0)))
            {
                State = HelperState.Building;
                MoveToward(targetConstruction.transform.position, 3.25f);
                if (Vector3.Distance(transform.position, targetConstruction.transform.position) < Reference.BuildRadius)
                {
                    animator?.BeginAction(AutomaticActionKind.Build);
                    targetConstruction.ContributeForHelper(inventory, animator, Time.deltaTime);
                }
                return;
            }

            if (targetBarricade != null && targetBarricade.HealthFraction < 0.75f && inventory[ResourceKind.Wood] > 0)
            {
                State = HelperState.Repairing;
                MoveToward(targetBarricade.transform.position, 3.2f);
                if (Vector3.Distance(transform.position, targetBarricade.transform.position) < Reference.BuildRadius)
                {
                    animator?.BeginAction(AutomaticActionKind.Repair);
                    if (animator == null ? Advance(ref helperActionElapsed, 0.42f, Time.deltaTime) : animator.ConsumeImpact(ref helperActionElapsed, 0.42f))
                        targetBarricade.Repair(inventory);
                }
                return;
            }

            if (inventory.Total > 0)
            {
                State = HelperState.Delivering;
                MoveToward(furnace.transform.position, 3.3f);
                if (Vector3.Distance(transform.position, furnace.transform.position) < Reference.DepositRadius)
                {
                    animator?.BeginAction(AutomaticActionKind.Deposit);
                    if (animator == null ? Advance(ref helperActionElapsed, 0.2f, Time.deltaTime) : animator.ConsumeImpact(ref helperActionElapsed, 0.2f))
                        furnace.DepositOne(inventory);
                }
                return;
            }

            if (targetResource != null && targetResource.isActiveAndEnabled && targetResource.Remaining > 0)
            {
                State = HelperState.Gathering;
                MoveToward(targetResource.transform.position, 3.15f);
                if (Vector3.Distance(transform.position, targetResource.transform.position) < targetResource.InteractionRange)
                {
                    animator?.BeginAction(targetResource.ActionKind);
                    targetResource.GatherForHelper(inventory, animator, Time.deltaTime);
                }
                return;
            }

            State = HelperState.Following;
            animator?.EndAction();
            MoveToward(player.transform.position + new Vector3(-1.35f, 0f, -1.05f), 3.2f);
        }

        private void RefreshTargets()
        {
            targetEnemy = FindObjectsByType<HavenlineEnemy>(FindObjectsSortMode.None)
                .Where(enemy => enemy.IsAlive)
                .OrderBy(enemy => Vector3.Distance(enemy.transform.position, transform.position))
                .FirstOrDefault();
            targetConstruction = FindObjectsByType<HavenlineConstructionSite>(FindObjectsSortMode.None)
                .Where(site => !site.IsBuilt)
                .OrderBy(site => Vector3.Distance(site.transform.position, transform.position))
                .FirstOrDefault();
            targetBarricade = FindObjectsByType<HavenlineBarricade>(FindObjectsSortMode.None)
                .Where(barricade => barricade.IsBuilt)
                .OrderBy(barricade => barricade.HealthFraction)
                .FirstOrDefault();
            if (targetResource == null || !targetResource.isActiveAndEnabled || targetResource.Remaining <= 0)
            {
                targetResource = FindObjectsByType<HavenlineResourceNode>(FindObjectsSortMode.None)
                    .Where(resource => resource.isActiveAndEnabled && resource.Remaining > 0)
                    .OrderBy(resource => Vector3.Distance(resource.transform.position, transform.position))
                    .FirstOrDefault();
            }
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

        private static bool Advance(ref float elapsed, float threshold, float deltaTime)
        {
            elapsed += deltaTime;
            if (elapsed < threshold)
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
        private float attackElapsed;
        private float targetRefreshClock;

        public bool IsAlive => health > 0f;
        public override AutomaticActionKind ActionKind => AutomaticActionKind.Combat;
        public override int Priority => 200;
        public override float InteractionRange => Reference.CombatRadius;
        public override string ContextLabel => "Defending";
        public override float NormalizedProgress => -1f;

        public void Configure(Transform visualRoot, HavenlineActorAnimator actorAnimator)
        {
            visual = visualRoot;
            animator = actorAnimator;
        }

        private void Awake()
        {
            controller = GetComponent<CharacterController>();
            health = maxHealth;
        }

        public override bool CanInteract(HavenlinePlayerController actor) => IsAlive;

        public override void TickInteraction(HavenlinePlayerController actor, float deltaTime)
        {
            if (!IsAlive)
                return;
            var actorAnimator = actor.ActorAnimator;
            var impact = actorAnimator != null
                ? actorAnimator.ConsumeImpact(ref attackElapsed, 0.64f)
                : Advance(ref attackElapsed, 0.64f, deltaTime);
            if (impact)
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
                targetBarricade = FindObjectsByType<HavenlineBarricade>(FindObjectsSortMode.None)
                    .Where(barricade => barricade.IsBuilt)
                    .OrderBy(barricade => Vector3.Distance(transform.position, barricade.transform.position))
                    .FirstOrDefault();
            }

            var target = targetBarricade != null ? targetBarricade.transform.position : Reference.Furnace;
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
            if (animator == null ? Advance(ref attackElapsed, 1.1f, Time.deltaTime) : animator.ConsumeImpact(ref attackElapsed, 1.1f))
                targetBarricade?.Damage(16f);
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
            animator?.PlayDeath();
            if (droppedResource != null)
                Instantiate(droppedResource, transform.position, Quaternion.identity);
            Destroy(gameObject, 1.15f);
        }

        private static bool Advance(ref float elapsed, float threshold, float deltaTime)
        {
            elapsed += deltaTime;
            if (elapsed < threshold)
                return false;
            elapsed = 0f;
            return true;
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
        private int wave;

        public string Objective { get; private set; } = "Gather wood";
        public int Wave => wave;
        public float WaveClock => waveClock;
        public HavenlineHelper Helper => helper;
        public HavenlineFurnace Furnace => furnace;
        public bool OpeningComplete => furnace != null && furnace.Level >= 2 &&
                                       helper != null && helper.State != HelperState.Trapped &&
                                       (requiredDefense == null || requiredDefense.IsBuilt) && wave >= 1;

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
            HavenlineSave.LoadDirector(out wave, out waveClock);
        }

        private void Update()
        {
            if (furnace == null)
                return;

            Objective = DetermineObjective();
            if (nextAreaGate != null)
                nextAreaGate.SetActive(OpeningComplete);

            if (furnace.Level < 2 || helper == null || helper.State == HelperState.Trapped || helper.State == HelperState.Rescuing)
                return;
            if (requiredDefense != null && !requiredDefense.IsBuilt)
                return;

            waveClock -= Time.deltaTime;
            if (waveClock > 0f)
                return;
            wave++;
            waveClock = Mathf.Max(24f, 48f - wave * 3f);
            SpawnWave(2 + wave);
        }

        private string DetermineObjective()
        {
            if (furnace.Level < 2)
                return "Gather supplies and feed the furnace";
            if (helper != null && helper.State == HelperState.Trapped)
                return "Move close to rescue the frozen survivor";
            if (requiredDefense != null && !requiredDefense.IsBuilt)
                return "Carry wood and stone to the barricade";
            if (wave < 1)
                return $"Prepare for wolves • {Mathf.CeilToInt(waveClock)}s";
            return OpeningComplete ? "The forest route is open" : "Stay near threats to defend automatically";
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
                Instantiate(enemyPrefab, position, Quaternion.identity).gameObject.SetActive(true);
            }
        }
    }

    [Serializable]
    public sealed class HavenlineSaveData
    {
        public int version = 2;
        public Vector3 playerPosition = Reference.PlayerSpawn;
        public HavenlineInventorySnapshot inventory;
        public HavenlineFurnaceSnapshot furnace;
        public HelperState helperState = HelperState.Trapped;
        public int wave;
        public float waveClock = 48f;
        public string[] builtConstructionIds = Array.Empty<string>();
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

        private static string SavePath => Path.Combine(Application.persistentDataPath, "havenline-save-v2.json");
        private static string TempPath => SavePath + ".tmp";

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

        public static void LoadDirector(out int wave, out float waveClock)
        {
            EnsureLoaded();
            wave = Mathf.Max(0, cached.wave);
            waveClock = cached.waveClock > 0f ? cached.waveClock : 48f;
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
            cached.builtConstructionIds = Built.OrderBy(value => value, StringComparer.Ordinal).ToArray();
        }

        public static void MaybeSave(HavenlinePlayerController player)
        {
            if (player == null || Time.unscaledTime < nextSave || !Reference.IsValidSavedPosition(player.transform.position))
                return;
            nextSave = Time.unscaledTime + 2f;
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
                cached.helperState = helper.State;
            var director = UnityEngine.Object.FindFirstObjectByType<HavenlineGameDirector>();
            if (director != null)
            {
                cached.wave = director.Wave;
                cached.waveClock = director.WaveClock;
            }
            cached.builtConstructionIds = Built.OrderBy(value => value, StringComparer.Ordinal).ToArray();
            cached.savedUtcTicks = DateTime.UtcNow.Ticks;
            WriteAtomic(cached);
        }

        public static void ResetAll()
        {
            cached = new HavenlineSaveData();
            cached.furnace.level = 1;
            Built.Clear();
            if (File.Exists(SavePath))
                File.Delete(SavePath);
            if (File.Exists(TempPath))
                File.Delete(TempPath);
        }

        private static void EnsureLoaded()
        {
            if (cached != null)
                return;
            cached = ReadSave() ?? MigrateLegacyPosition();
            cached.version = 2;
            cached.builtConstructionIds ??= Array.Empty<string>();
            Built.Clear();
            foreach (var id in cached.builtConstructionIds)
            {
                if (!string.IsNullOrWhiteSpace(id))
                    Built.Add(id);
            }
        }

        private static HavenlineSaveData ReadSave()
        {
            try
            {
                if (!File.Exists(SavePath))
                    return null;
                var parsed = JsonUtility.FromJson<HavenlineSaveData>(File.ReadAllText(SavePath));
                if (parsed == null || parsed.version > 2)
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

        private static void WriteAtomic(HavenlineSaveData data)
        {
            try
            {
                Directory.CreateDirectory(Application.persistentDataPath);
                File.WriteAllText(TempPath, JsonUtility.ToJson(data, true));
                if (File.Exists(SavePath))
                    File.Delete(SavePath);
                File.Move(TempPath, SavePath);
            }
            catch (Exception exception)
            {
                Debug.LogError($"HAVENLINE save failed: {exception.Message}");
            }
        }
    }
}
