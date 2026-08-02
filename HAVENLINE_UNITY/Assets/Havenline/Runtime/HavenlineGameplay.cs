using System;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;

namespace Havenline
{
    public sealed class HavenlineResourceNode : HavenlineInteractable
    {
        [SerializeField] private ResourceKind kind;
        [SerializeField] private int remaining = 18;
        [SerializeField] private float secondsPerUnit = 0.62f;
        private HavenlinePlayerController activeActor;
        private float progress;
        public ResourceKind Kind => kind;
        public int Remaining => remaining;
        public void Configure(ResourceKind resourceKind, int units) { kind = resourceKind; remaining = units; }
        public override bool CanInteract(HavenlinePlayerController actor) => remaining > 0 && !actor.Inventory.IsFull;
        public override void TickInteraction(HavenlinePlayerController actor, float deltaTime)
        {
            if (!CanInteract(actor)) return;
            if (activeActor != actor) { activeActor = actor; progress = 0f; }
            progress += deltaTime;
            actor.GetComponentInChildren<HavenlineActorAnimator>()?.PulseAction();
            if (progress < secondsPerUnit) return;
            progress = 0f;
            if (actor.Inventory.Add(kind, 1) > 0 && --remaining <= 0) gameObject.SetActive(false);
        }
        public bool GatherForHelper(HavenlineInventory inventory, float deltaTime)
        {
            if (remaining <= 0 || inventory.IsFull) return false;
            progress += deltaTime;
            if (progress < secondsPerUnit) return true;
            progress = 0f;
            if (inventory.Add(kind, 1) > 0 && --remaining <= 0) gameObject.SetActive(false);
            return true;
        }
    }

    public sealed class HavenlineFurnace : MonoBehaviour
    {
        public static HavenlineFurnace Instance { get; private set; }
        [SerializeField] private Transform warmthRing;
        [SerializeField] private Light fireLight;
        [SerializeField] private ParticleSystem fireParticles;
        private readonly Dictionary<ResourceKind, int> stored = new();
        public int Level { get; private set; } = 1;
        public float WarmthRadius { get; private set; } = 4f;
        public int Stored(ResourceKind kind) => stored.TryGetValue(kind, out var value) ? value : 0;
        public event Action Changed;
        public void Configure(Transform ring, Light light, ParticleSystem particles) { warmthRing = ring; fireLight = light; fireParticles = particles; ApplyVisuals(); }
        private void Awake() => Instance = this;
        public void Deposit(HavenlineInventory inventory)
        {
            var changed = false;
            foreach (ResourceKind kind in Enum.GetValues(typeof(ResourceKind)))
            {
                var amount = inventory.RemoveAll(kind);
                if (amount <= 0) continue;
                stored[kind] = Stored(kind) + amount; changed = true;
            }
            if (!changed) return;
            var previous = Level;
            if (Stored(ResourceKind.Wood) >= 18 && Stored(ResourceKind.Stone) >= 6) Level = Mathf.Max(Level, 2);
            if (Stored(ResourceKind.Wood) >= 38 && Stored(ResourceKind.Stone) >= 16) Level = Mathf.Max(Level, 3);
            if (Stored(ResourceKind.Wood) >= 64 && Stored(ResourceKind.Stone) >= 28 && Stored(ResourceKind.Metal) >= 6) Level = 4;
            if (Level != previous) { WarmthRadius = 4f + (Level - 1) * 2.5f; ApplyVisuals(); }
            Changed?.Invoke();
        }
        private void ApplyVisuals()
        {
            if (warmthRing != null) warmthRing.localScale = Vector3.one * WarmthRadius;
            if (fireLight != null) { fireLight.range = 7f + Level * 2.2f; fireLight.intensity = 2.8f + Level * 1.3f; }
            if (fireParticles != null)
            {
                var main = fireParticles.main; main.startSizeMultiplier = 0.7f + Level * 0.18f;
                var emission = fireParticles.emission; emission.rateOverTimeMultiplier = 18f + Level * 7f;
            }
        }
    }

    public sealed class HavenlineBarricade : MonoBehaviour
    {
        [SerializeField] private float maxHealth = 160f;
        [SerializeField] private Renderer[] renderers;
        public float Health { get; private set; }
        public bool IsBuilt => Health > 0f;
        public float HealthFraction => maxHealth <= 0f ? 0f : Health / maxHealth;
        private void Awake() { Health = maxHealth * 0.55f; if (renderers == null || renderers.Length == 0) renderers = GetComponentsInChildren<Renderer>(); Apply(); }
        public void Damage(float amount) { Health = Mathf.Max(0f, Health - amount); Apply(); }
        public bool Repair(HavenlineInventory inventory)
        {
            if (Health >= maxHealth || inventory[ResourceKind.Wood] <= 0) return false;
            inventory.Remove(ResourceKind.Wood, 1); Health = Mathf.Min(maxHealth, Health + 28f); Apply(); return true;
        }
        private void Apply()
        {
            var color = Color.Lerp(new Color(0.22f,0.15f,0.11f), new Color(0.62f,0.40f,0.22f), HealthFraction);
            foreach (var r in renderers ?? Array.Empty<Renderer>()) if (r != null && r.material != null) r.material.color = color;
        }
    }

    [RequireComponent(typeof(CharacterController), typeof(HavenlineInventory))]
    public sealed class HavenlineHelper : MonoBehaviour
    {
        [SerializeField] private Transform visual;
        [SerializeField] private HavenlineActorAnimator animator;
        private CharacterController controller;
        private HavenlineInventory inventory;
        private HavenlineResourceNode targetResource;
        private Vector3 velocity;
        private float attackTick;
        public HelperState State { get; private set; } = HelperState.Trapped;
        public HavenlineInventory Inventory => inventory;
        public void Configure(Transform visualRoot, HavenlineActorAnimator actorAnimator) { visual = visualRoot; animator = actorAnimator; }
        private void Awake() { controller = GetComponent<CharacterController>(); inventory = GetComponent<HavenlineInventory>(); }
        private void Update()
        {
            var player = FindFirstObjectByType<HavenlinePlayerController>();
            var furnace = HavenlineFurnace.Instance;
            if (player == null || furnace == null) return;
            if (State == HelperState.Trapped)
            {
                if (furnace.Level >= 2 && Vector3.Distance(player.transform.position, transform.position) < 2.2f) State = HelperState.Following;
                animator?.SetMotion(0f); return;
            }
            var enemy = FindObjectsByType<HavenlineEnemy>(FindObjectsSortMode.None).Where(e => e.IsAlive)
                .OrderBy(e => Vector3.Distance(e.transform.position, transform.position)).FirstOrDefault();
            if (enemy != null && Vector3.Distance(enemy.transform.position, transform.position) < 7f)
            {
                State = HelperState.Defending; MoveToward(enemy.transform.position, 3.7f);
                attackTick -= Time.deltaTime;
                if (Vector3.Distance(enemy.transform.position, transform.position) < 2.3f && attackTick <= 0f)
                { attackTick = 0.9f; enemy.Damage(18f); animator?.PulseAction(); }
                return;
            }
            var damaged = FindObjectsByType<HavenlineBarricade>(FindObjectsSortMode.None).OrderBy(b => b.HealthFraction).FirstOrDefault();
            if (damaged != null && damaged.HealthFraction < 0.75f && inventory[ResourceKind.Wood] > 0)
            {
                State = HelperState.Repairing; MoveToward(damaged.transform.position, 3.2f);
                if (Vector3.Distance(transform.position, damaged.transform.position) < 2.1f) damaged.Repair(inventory);
                return;
            }
            if (inventory.Total > 0)
            {
                State = HelperState.Delivering; MoveToward(furnace.transform.position, 3.25f);
                if (Vector3.Distance(transform.position, furnace.transform.position) < 2.1f) furnace.Deposit(inventory);
                return;
            }
            if (targetResource == null || !targetResource.isActiveAndEnabled || targetResource.Remaining <= 0)
                targetResource = FindObjectsByType<HavenlineResourceNode>(FindObjectsSortMode.None)
                    .Where(r => r.isActiveAndEnabled && r.Remaining > 0).OrderBy(r => Vector3.Distance(r.transform.position, transform.position)).FirstOrDefault();
            if (targetResource != null)
            {
                State = HelperState.Gathering; MoveToward(targetResource.transform.position, 3.15f);
                if (Vector3.Distance(transform.position, targetResource.transform.position) < 1.7f)
                { targetResource.GatherForHelper(inventory, Time.deltaTime); animator?.PulseAction(); }
                return;
            }
            State = HelperState.Following; MoveToward(player.transform.position + new Vector3(-1.4f,0f,-1.1f), 3.2f);
        }
        private void MoveToward(Vector3 target, float speed)
        {
            var direction = Vector3.ProjectOnPlane(target - transform.position, Vector3.up);
            if (direction.magnitude < 0.4f) { animator?.SetMotion(0f); return; }
            direction.Normalize(); var velocity = direction * speed;
            controller.Move((velocity + Physics.gravity) * Time.deltaTime); transform.position = Reference.ClampToWorld(transform.position);
            if (visual != null) visual.rotation = Quaternion.Slerp(visual.rotation, Quaternion.LookRotation(direction), 1f - Mathf.Exp(-12f * Time.deltaTime));
            animator?.SetMotion(1f);
        }
    }

    [RequireComponent(typeof(CharacterController))]
    public sealed class HavenlineEnemy : MonoBehaviour
    {
        [SerializeField] private Transform visual;
        [SerializeField] private HavenlineActorAnimator animator;
        private CharacterController controller;
        private float health = 65f;
        private float attackCooldown;
        public bool IsAlive => health > 0f;
        public void Configure(Transform visualRoot, HavenlineActorAnimator actorAnimator) { visual = visualRoot; animator = actorAnimator; }
        private void Awake() => controller = GetComponent<CharacterController>();
        private void Update()
        {
            if (!IsAlive) return;
            var targetBarricade = FindObjectsByType<HavenlineBarricade>(FindObjectsSortMode.None).Where(b => b.IsBuilt)
                .OrderBy(b => Vector3.Distance(transform.position, b.transform.position)).FirstOrDefault();
            var target = targetBarricade != null ? targetBarricade.transform.position : Reference.Furnace;
            var direction = Vector3.ProjectOnPlane(target - transform.position, Vector3.up);
            if (direction.magnitude > 1.55f)
            {
                direction.Normalize(); controller.Move((direction * 3.9f + Physics.gravity) * Time.deltaTime);
                if (visual != null) visual.rotation = Quaternion.Slerp(visual.rotation, Quaternion.LookRotation(direction), 1f - Mathf.Exp(-12f * Time.deltaTime));
                animator?.SetMotion(1f);
            }
            else
            {
                animator?.SetMotion(0f); attackCooldown -= Time.deltaTime;
                if (attackCooldown <= 0f) { attackCooldown = 1.1f; targetBarricade?.Damage(16f); animator?.PulseAction(); }
            }
        }
        public void Damage(float amount)
        {
            if (!IsAlive) return; health -= amount;
            if (health <= 0f) { health = 0f; animator?.PulseAction(); Destroy(gameObject, 0.55f); }
        }
    }

    public sealed class HavenlineGameDirector : MonoBehaviour
    {
        [SerializeField] private HavenlineEnemy enemyPrefab;
        [SerializeField] private HavenlineHelper helper;
        [SerializeField] private HavenlineFurnace furnace;
        private float waveClock = 48f;
        private int wave;
        public string Objective { get; private set; } = "Restore the furnace\nGather wood and carry it to the heat zone";
        public int Wave => wave;
        public float WaveClock => waveClock;
        public HavenlineHelper Helper => helper;
        public HavenlineFurnace Furnace => furnace;
        public void Configure(HavenlineEnemy prefab, HavenlineHelper survivor, HavenlineFurnace centralFurnace)
        { enemyPrefab = prefab; helper = survivor; furnace = centralFurnace; }
        private void Update()
        {
            if (furnace == null) return;
            Objective = furnace.Level switch
            {
                1 => "Restore the furnace\nGather wood and stone automatically",
                2 when helper != null && helper.State == HelperState.Trapped => "Rescue the survivor\nReach the warm eastern shelter",
                2 => "Fortify the outpost\nSupply wood before the next attack",
                3 => "Hold the HAVENLINE\nDefend the furnace and repair barriers",
                _ => "Open the forest line\nThe frozen outpost is stabilized"
            };
            if (furnace.Level < 2) return;
            waveClock -= Time.deltaTime;
            if (waveClock > 0f) return;
            wave++; waveClock = Mathf.Max(24f, 48f - wave * 3f); SpawnWave(2 + wave);
        }
        private void SpawnWave(int count)
        {
            if (enemyPrefab == null) return;
            for (var i = 0; i < count; i++)
            {
                var north = i % 2 == 0;
                var position = new Vector3(Mathf.Lerp(-9f,9f,(i+1f)/(count+1f)),0.15f,north ? -15.2f : 15.2f);
                Instantiate(enemyPrefab, position, Quaternion.identity).gameObject.SetActive(true);
            }
        }
    }

    public static class HavenlineSave
    {
        private const string X = "havenline.player.x", Y = "havenline.player.y", Z = "havenline.player.z";
        private static float nextSave;
        public static Vector3 LoadPlayerPosition()
        {
            if (!PlayerPrefs.HasKey(X)) return Reference.PlayerSpawn;
            var value = new Vector3(PlayerPrefs.GetFloat(X), PlayerPrefs.GetFloat(Y), PlayerPrefs.GetFloat(Z));
            return Reference.IsValidSavedPosition(value) ? value : Reference.PlayerSpawn;
        }
        public static void MaybeSave(Vector3 position)
        {
            if (Time.unscaledTime < nextSave || !Reference.IsValidSavedPosition(position)) return;
            nextSave = Time.unscaledTime + 2f;
            PlayerPrefs.SetFloat(X, position.x); PlayerPrefs.SetFloat(Y, position.y); PlayerPrefs.SetFloat(Z, position.z); PlayerPrefs.Save();
        }
    }
}
