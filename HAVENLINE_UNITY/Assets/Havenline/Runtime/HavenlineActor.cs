using System;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;

namespace Havenline
{
    public sealed class HavenlineInventory : MonoBehaviour
    {
        [SerializeField] private int capacity = Reference.CarryCapacity;
        [SerializeField] private Transform visibleCarryRoot;
        private readonly Dictionary<ResourceKind, int> amounts = new();
        public int Total => amounts.Values.Sum();
        public bool IsFull => Total >= capacity;
        public int this[ResourceKind kind] => amounts.TryGetValue(kind, out var value) ? value : 0;

        public void Configure(Transform carryRoot) { visibleCarryRoot = carryRoot; RefreshVisual(); }
        public int Add(ResourceKind kind, int amount)
        {
            var accepted = Mathf.Min(amount, capacity - Total);
            if (accepted <= 0) return 0;
            amounts[kind] = this[kind] + accepted;
            RefreshVisual();
            return accepted;
        }
        public int Remove(ResourceKind kind, int amount)
        {
            var removed = Mathf.Min(amount, this[kind]);
            if (removed <= 0) return 0;
            amounts[kind] -= removed;
            RefreshVisual();
            return removed;
        }
        public int RemoveAll(ResourceKind kind) => Remove(kind, this[kind]);
        private void RefreshVisual() { if (visibleCarryRoot != null) visibleCarryRoot.gameObject.SetActive(Total > 0); }
    }

    public sealed class HavenlineActorAnimator : MonoBehaviour
    {
        private readonly List<(Transform bone, Quaternion basis, float phase, bool arm)> bones = new();
        private Vector3 basisPosition;
        private float speed;
        private float actionPulse;

        private void Awake()
        {
            basisPosition = transform.localPosition;
            foreach (var bone in GetComponentsInChildren<Transform>(true))
            {
                var n = bone.name.ToLowerInvariant();
                var arm = n.Contains("arm") || n.Contains("hand");
                var leg = n.Contains("leg") || n.Contains("thigh") || n.Contains("calf") || n.Contains("foot");
                if (arm || leg) bones.Add((bone, bone.localRotation, bones.Count * 1.7f, arm));
            }
        }

        public void SetMotion(float normalizedSpeed) => speed = Mathf.Clamp01(normalizedSpeed);
        public void PulseAction() => actionPulse = 1f;

        private void LateUpdate()
        {
            actionPulse = Mathf.MoveTowards(actionPulse, 0f, Time.deltaTime * 2.8f);
            var cycle = Time.time * Mathf.Lerp(3f, 9f, speed);
            transform.localPosition = basisPosition + Vector3.up * (Mathf.Sin(cycle * 2f) * 0.035f * speed);
            foreach (var entry in bones)
            {
                var swing = Mathf.Sin(cycle + entry.phase) * 25f * speed;
                if (entry.arm) swing += actionPulse * Mathf.Sin(Time.time * 22f) * 38f;
                entry.bone.localRotation = entry.basis * Quaternion.Euler(swing, 0f, 0f);
            }
        }
    }

    public abstract class HavenlineInteractable : MonoBehaviour
    {
        public abstract bool CanInteract(HavenlinePlayerController actor);
        public abstract void TickInteraction(HavenlinePlayerController actor, float deltaTime);
    }

    [RequireComponent(typeof(CharacterController), typeof(HavenlineInventory))]
    public sealed class HavenlinePlayerController : MonoBehaviour
    {
        [SerializeField] private HavenlineInputRouter input;
        [SerializeField] private Transform visual;
        [SerializeField] private HavenlineActorAnimator actorAnimator;
        private CharacterController controller;
        private HavenlineInventory inventory;
        private Vector3 planarVelocity;
        private Vector3 lastSafePosition;
        private float interactionScan;
        private HavenlineInteractable currentInteractable;
        public HavenlineInventory Inventory => inventory;
        public Vector3 Velocity => planarVelocity;

        public void Configure(HavenlineInputRouter router, Transform visualRoot, HavenlineActorAnimator animator)
        {
            input = router; visual = visualRoot; actorAnimator = animator;
        }

        private void Awake()
        {
            controller = GetComponent<CharacterController>();
            inventory = GetComponent<HavenlineInventory>();
            lastSafePosition = Reference.PlayerSpawn;
        }

        private void Start()
        {
            var saved = HavenlineSave.LoadPlayerPosition();
            transform.position = Reference.IsValidSavedPosition(saved) ? saved : Reference.PlayerSpawn;
            lastSafePosition = transform.position;
        }

        private void Update()
        {
            var move = input != null ? input.Move : Vector2.zero;
            var camera = Camera.main;
            var forward = camera != null ? Vector3.ProjectOnPlane(camera.transform.forward, Vector3.up).normalized : Vector3.forward;
            var right = camera != null ? Vector3.ProjectOnPlane(camera.transform.right, Vector3.up).normalized : Vector3.right;
            var desiredDirection = (right * move.x + forward * move.y);
            if (desiredDirection.sqrMagnitude > 1f) desiredDirection.Normalize();
            var targetSpeed = input != null && input.DashHeld ? Reference.RunSpeed : Reference.WalkSpeed;
            var targetVelocity = desiredDirection * targetSpeed;
            var rate = targetVelocity.sqrMagnitude > planarVelocity.sqrMagnitude ? Reference.Acceleration : Reference.Deceleration;
            planarVelocity = Vector3.MoveTowards(planarVelocity, targetVelocity, rate * Time.deltaTime);
            controller.Move((planarVelocity + Physics.gravity) * Time.deltaTime);
            transform.position = Reference.ClampToWorld(transform.position);

            if (desiredDirection.sqrMagnitude > 0.02f && visual != null)
                visual.rotation = Quaternion.Slerp(visual.rotation, Quaternion.LookRotation(desiredDirection), 1f - Mathf.Exp(-14f * Time.deltaTime));
            actorAnimator?.SetMotion(planarVelocity.magnitude / Reference.RunSpeed);

            if (controller.isGrounded && Reference.IsValidSavedPosition(transform.position)) lastSafePosition = transform.position;
            if (transform.position.y < Reference.FallRecoveryY)
            {
                controller.enabled = false; transform.position = lastSafePosition; controller.enabled = true; planarVelocity = Vector3.zero;
            }

            interactionScan -= Time.deltaTime;
            if (interactionScan <= 0f) { interactionScan = 0.16f; currentInteractable = FindClosestInteractable(); }
            currentInteractable?.TickInteraction(this, Time.deltaTime);

            var furnace = HavenlineFurnace.Instance;
            if (furnace != null && Vector3.Distance(transform.position, furnace.transform.position) <= 2.2f)
                furnace.Deposit(inventory);

            HavenlineCombat.ResolvePlayerAutoAttack(this, actorAnimator);
            HavenlineSave.MaybeSave(transform.position);
        }

        private HavenlineInteractable FindClosestInteractable()
        {
            HavenlineInteractable best = null;
            var bestDistance = Reference.InteractionRadius * Reference.InteractionRadius;
            foreach (var item in FindObjectsByType<HavenlineInteractable>(FindObjectsSortMode.None))
            {
                if (!item.isActiveAndEnabled || !item.CanInteract(this)) continue;
                var distance = (item.transform.position - transform.position).sqrMagnitude;
                if (distance < bestDistance) { best = item; bestDistance = distance; }
            }
            return best;
        }
    }

    public static class HavenlineCombat
    {
        private static float nextPlayerAttack;
        public static void ResolvePlayerAutoAttack(HavenlinePlayerController player, HavenlineActorAnimator animator)
        {
            if (Time.time < nextPlayerAttack) return;
            var enemy = UnityEngine.Object.FindObjectsByType<HavenlineEnemy>(FindObjectsSortMode.None)
                .Where(e => e.IsAlive).OrderBy(e => Vector3.Distance(e.transform.position, player.transform.position)).FirstOrDefault();
            if (enemy == null || Vector3.Distance(enemy.transform.position, player.transform.position) > 2.1f) return;
            nextPlayerAttack = Time.time + 0.72f;
            enemy.Damage(22f); animator?.PulseAction();
        }
    }
}
