using System;
using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
    [Serializable]
    public struct HavenlineInventorySnapshot
    {
        public int wood;
        public int stone;
        public int metal;
        public int fuel;
    }

    public sealed class HavenlineInventory : MonoBehaviour
    {
        [SerializeField] private int capacity = Reference.CarryCapacity;
        [SerializeField] private Transform visibleCarryRoot;
        [SerializeField] private HavenlineCarryVisual carryVisual;

        private readonly Dictionary<ResourceKind, int> amounts = new();

        public int Capacity => capacity;
        public int Total => this[ResourceKind.Wood] + this[ResourceKind.Stone] + this[ResourceKind.Metal] + this[ResourceKind.Fuel];
        public bool IsFull => Total >= capacity;
        public int this[ResourceKind kind] => amounts.TryGetValue(kind, out var value) ? value : 0;
        public event Action Changed;

        public void Configure(Transform carryRoot)
        {
            visibleCarryRoot = carryRoot;
            if (carryVisual == null && carryRoot != null)
                carryVisual = carryRoot.GetComponent<HavenlineCarryVisual>();
            RefreshVisual();
        }

        public void Configure(Transform carryRoot, HavenlineCarryVisual visual, int carryCapacity)
        {
            visibleCarryRoot = carryRoot;
            carryVisual = visual;
            capacity = Mathf.Max(1, carryCapacity);
            RefreshVisual();
        }

        public int Add(ResourceKind kind, int amount)
        {
            var accepted = Mathf.Min(Mathf.Max(0, amount), capacity - Total);
            if (accepted <= 0)
                return 0;

            amounts[kind] = this[kind] + accepted;
            NotifyChanged();
            return accepted;
        }

        public int Remove(ResourceKind kind, int amount)
        {
            var removed = Mathf.Min(Mathf.Max(0, amount), this[kind]);
            if (removed <= 0)
                return 0;

            amounts[kind] = this[kind] - removed;
            NotifyChanged();
            return removed;
        }

        public int RemoveAll(ResourceKind kind) => Remove(kind, this[kind]);

        public bool TryGetFirstCarried(out ResourceKind kind)
        {
            foreach (ResourceKind candidate in Enum.GetValues(typeof(ResourceKind)))
            {
                if (this[candidate] <= 0)
                    continue;

                kind = candidate;
                return true;
            }

            kind = ResourceKind.Wood;
            return false;
        }

        public HavenlineInventorySnapshot Capture() => new()
        {
            wood = this[ResourceKind.Wood],
            stone = this[ResourceKind.Stone],
            metal = this[ResourceKind.Metal],
            fuel = this[ResourceKind.Fuel]
        };

        public void Restore(HavenlineInventorySnapshot snapshot)
        {
            amounts.Clear();
            amounts[ResourceKind.Wood] = Mathf.Max(0, snapshot.wood);
            amounts[ResourceKind.Stone] = Mathf.Max(0, snapshot.stone);
            amounts[ResourceKind.Metal] = Mathf.Max(0, snapshot.metal);
            amounts[ResourceKind.Fuel] = Mathf.Max(0, snapshot.fuel);

            while (Total > capacity)
            {
                if (amounts[ResourceKind.Fuel] > 0) amounts[ResourceKind.Fuel]--;
                else if (amounts[ResourceKind.Metal] > 0) amounts[ResourceKind.Metal]--;
                else if (amounts[ResourceKind.Stone] > 0) amounts[ResourceKind.Stone]--;
                else if (amounts[ResourceKind.Wood] > 0) amounts[ResourceKind.Wood]--;
            }

            NotifyChanged();
        }

        private void NotifyChanged()
        {
            RefreshVisual();
            Changed?.Invoke();
        }

        private void RefreshVisual()
        {
            if (visibleCarryRoot != null)
                visibleCarryRoot.gameObject.SetActive(Total > 0);

            carryVisual?.Apply(Capture(), Total, capacity);
        }
    }

    /// <summary>
    /// Each physical stack position contains one visual option for every resource type.
    /// Exactly one option is enabled per occupied slot, preventing mixed cargo from
    /// intersecting while preserving the visible stacked-resource reference behavior.
    /// </summary>
    public sealed class HavenlineCarryVisual : MonoBehaviour
    {
        [SerializeField] private GameObject[] woodSlots = Array.Empty<GameObject>();
        [SerializeField] private GameObject[] stoneSlots = Array.Empty<GameObject>();
        [SerializeField] private GameObject[] metalSlots = Array.Empty<GameObject>();
        [SerializeField] private GameObject[] fuelSlots = Array.Empty<GameObject>();

        public void Configure(GameObject[] wood, GameObject[] stone, GameObject[] metal, GameObject[] fuel)
        {
            woodSlots = wood ?? Array.Empty<GameObject>();
            stoneSlots = stone ?? Array.Empty<GameObject>();
            metalSlots = metal ?? Array.Empty<GameObject>();
            fuelSlots = fuel ?? Array.Empty<GameObject>();
        }

        public void Apply(HavenlineInventorySnapshot snapshot, int total, int capacity)
        {
            var slotCount = Mathf.Min(
                Mathf.Max(0, capacity),
                Mathf.Max(
                    Mathf.Max(woodSlots.Length, stoneSlots.Length),
                    Mathf.Max(metalSlots.Length, fuelSlots.Length)));

            var woodEnd = snapshot.wood;
            var stoneEnd = woodEnd + snapshot.stone;
            var metalEnd = stoneEnd + snapshot.metal;
            var fuelEnd = metalEnd + snapshot.fuel;

            for (var index = 0; index < slotCount; index++)
            {
                SetSlot(woodSlots, index, index < woodEnd);
                SetSlot(stoneSlots, index, index >= woodEnd && index < stoneEnd);
                SetSlot(metalSlots, index, index >= stoneEnd && index < metalEnd);
                SetSlot(fuelSlots, index, index >= metalEnd && index < fuelEnd);
            }

            DisableRemaining(woodSlots, slotCount);
            DisableRemaining(stoneSlots, slotCount);
            DisableRemaining(metalSlots, slotCount);
            DisableRemaining(fuelSlots, slotCount);
        }

        private static void SetSlot(IReadOnlyList<GameObject> slots, int index, bool active)
        {
            if (index < slots.Count && slots[index] != null)
                slots[index].SetActive(active);
        }

        private static void DisableRemaining(IReadOnlyList<GameObject> slots, int start)
        {
            for (var index = start; index < slots.Count; index++)
            {
                if (slots[index] != null)
                    slots[index].SetActive(false);
            }
        }
    }

    public sealed class HavenlineActorAnimator : MonoBehaviour
    {
        private static readonly int SpeedHash = Animator.StringToHash("Speed");
        private static readonly int CarryHash = Animator.StringToHash("CarryAmount");
        private static readonly int ActionTypeHash = Animator.StringToHash("ActionType");
        private static readonly int ActionHash = Animator.StringToHash("Action");
        private static readonly int ActionEndHash = Animator.StringToHash("ActionEnd");
        private static readonly int HitHash = Animator.StringToHash("Hit");
        private static readonly int DeadHash = Animator.StringToHash("Dead");

        [SerializeField] private Animator animator;
        private AutomaticActionKind currentAction;
        private bool impactQueued;

        public AutomaticActionKind CurrentAction => currentAction;

        private void Awake()
        {
            if (animator == null)
                animator = GetComponentInChildren<Animator>(true);
        }

        public void Configure(Animator productionAnimator) => animator = productionAnimator;

        public void SetMotion(float normalizedSpeed)
        {
            if (animator != null)
                animator.SetFloat(SpeedHash, Mathf.Clamp01(normalizedSpeed), 0.08f, Time.deltaTime);
        }

        public void SetCarryAmount(int amount)
        {
            if (animator != null)
                animator.SetInteger(CarryHash, Mathf.Max(0, amount));
        }

        public void BeginAction(AutomaticActionKind action)
        {
            if (action == AutomaticActionKind.None || currentAction == action)
                return;

            currentAction = action;
            impactQueued = false;
            if (animator == null)
                return;

            animator.SetInteger(ActionTypeHash, (int)action);
            animator.ResetTrigger(ActionEndHash);
            animator.SetTrigger(ActionHash);
        }

        public void EndAction()
        {
            if (currentAction == AutomaticActionKind.None)
                return;

            currentAction = AutomaticActionKind.None;
            impactQueued = false;
            if (animator == null)
                return;

            animator.SetInteger(ActionTypeHash, 0);
            animator.SetTrigger(ActionEndHash);
        }

        public bool ConsumeImpact(ref float elapsed, float fallbackSeconds)
        {
            elapsed += Time.deltaTime;
            if (!impactQueued && elapsed < Mathf.Max(0.05f, fallbackSeconds))
                return false;

            impactQueued = false;
            elapsed = 0f;
            return true;
        }

        public void ActionImpact() => impactQueued = true;
        public void PulseAction() => impactQueued = true;

        public void PlayHit()
        {
            if (animator != null)
                animator.SetTrigger(HitHash);
        }

        public void PlayDeath()
        {
            currentAction = AutomaticActionKind.None;
            if (animator != null)
                animator.SetTrigger(DeadHash);
        }
    }

    [RequireComponent(typeof(CharacterController), typeof(HavenlineInventory), typeof(HavenlineAutomaticActionController))]
    public sealed class HavenlinePlayerController : MonoBehaviour
    {
        [SerializeField] private HavenlineInputRouter input;
        [SerializeField] private Transform visual;
        [SerializeField] private HavenlineActorAnimator actorAnimator;

        private CharacterController controller;
        private HavenlineInventory inventory;
        private HavenlineInventory subscribedInventory;
        private HavenlineAutomaticActionController automaticActions;
        private Vector3 planarVelocity;
        private Vector3 lastSafePosition;
        private Vector2 moveInput;
        private bool initialized;

        public HavenlineInventory Inventory => inventory;
        public HavenlineActorAnimator ActorAnimator => actorAnimator;
        public HavenlineAutomaticActionController AutomaticActions => automaticActions;
        public Vector3 Velocity => planarVelocity;
        public float MoveInputMagnitude => moveInput.magnitude;
        public Vector3 VisualForward => visual != null ? visual.forward : transform.forward;

        public void Configure(HavenlineInputRouter router, Transform visualRoot, HavenlineActorAnimator animator)
        {
            EnsureDependencies();
            input = router;
            visual = visualRoot;
            actorAnimator = animator;
            automaticActions.Configure(this);
            actorAnimator?.SetCarryAmount(inventory.Total);
        }

        private void Awake() => EnsureDependencies();
        private void OnEnable() => EnsureDependencies();

        private void EnsureDependencies()
        {
            controller = GetComponent<CharacterController>();
            if (controller == null)
                controller = gameObject.AddComponent<CharacterController>();

            var resolvedInventory = GetComponent<HavenlineInventory>();
            if (resolvedInventory == null)
                resolvedInventory = gameObject.AddComponent<HavenlineInventory>();

            if (subscribedInventory != resolvedInventory)
            {
                if (subscribedInventory != null)
                    subscribedInventory.Changed -= HandleInventoryChanged;

                subscribedInventory = resolvedInventory;
                subscribedInventory.Changed += HandleInventoryChanged;
            }
            inventory = resolvedInventory;

            automaticActions = GetComponent<HavenlineAutomaticActionController>();
            if (automaticActions == null)
                automaticActions = gameObject.AddComponent<HavenlineAutomaticActionController>();
            automaticActions.Configure(this);

            if (initialized)
                return;

            initialized = true;
            lastSafePosition = Reference.PlayerSpawn;
        }

        private void Start()
        {
            EnsureDependencies();
            var saved = HavenlineSave.LoadPlayerPosition();
            transform.position = Reference.IsValidSavedPosition(saved) ? saved : Reference.PlayerSpawn;
            lastSafePosition = transform.position;
            inventory.Restore(HavenlineSave.LoadInventory());
        }

        private void OnDestroy()
        {
            if (subscribedInventory != null)
                subscribedInventory.Changed -= HandleInventoryChanged;
        }

        private void Update()
        {
            if (controller == null || inventory == null || automaticActions == null)
                EnsureDependencies();

            moveInput = input != null ? input.Move : Vector2.zero;
            var mainCamera = Camera.main;
            var forward = mainCamera != null
                ? Vector3.ProjectOnPlane(mainCamera.transform.forward, Vector3.up).normalized
                : Vector3.forward;
            var right = mainCamera != null
                ? Vector3.ProjectOnPlane(mainCamera.transform.right, Vector3.up).normalized
                : Vector3.right;

            var desiredDirection = right * moveInput.x + forward * moveInput.y;
            if (desiredDirection.sqrMagnitude > 1f)
                desiredDirection.Normalize();

            var targetSpeed = input != null && input.DashHeld ? Reference.RunSpeed : Reference.WalkSpeed;
            var targetVelocity = desiredDirection * targetSpeed;
            var rate = targetVelocity.sqrMagnitude > planarVelocity.sqrMagnitude
                ? Reference.Acceleration
                : Reference.Deceleration;

            planarVelocity = Vector3.MoveTowards(planarVelocity, targetVelocity, rate * Time.deltaTime);
            controller.Move((planarVelocity + Physics.gravity) * Time.deltaTime);
            transform.position = Reference.ClampToWorld(transform.position);

            if (desiredDirection.sqrMagnitude > 0.02f)
            {
                actorAnimator?.EndAction();
                Face(transform.position + desiredDirection);
            }

            actorAnimator?.SetMotion(planarVelocity.magnitude / Reference.RunSpeed);

            if (controller.isGrounded && Reference.IsValidSavedPosition(transform.position))
                lastSafePosition = transform.position;

            if (transform.position.y < Reference.FallRecoveryY)
                RecoverFromFall();

            HavenlineSave.MaybeSave(this);
        }

        public void Face(Vector3 worldPoint)
        {
            if (visual == null)
                return;

            var direction = Vector3.ProjectOnPlane(worldPoint - transform.position, Vector3.up);
            if (direction.sqrMagnitude < 0.001f)
                return;

            visual.rotation = Quaternion.Slerp(
                visual.rotation,
                Quaternion.LookRotation(direction.normalized),
                1f - Mathf.Exp(-Reference.TurnSharpness * Time.deltaTime));
        }

        private void RecoverFromFall()
        {
            controller.enabled = false;
            transform.position = lastSafePosition;
            controller.enabled = true;
            planarVelocity = Vector3.zero;
        }

        private void HandleInventoryChanged() => actorAnimator?.SetCarryAmount(inventory.Total);

        private void OnApplicationPause(bool paused)
        {
            if (paused)
                HavenlineSave.SaveNow(this);
        }

        private void OnApplicationFocus(bool focused)
        {
            if (!focused)
                HavenlineSave.SaveNow(this);
        }
    }
}
