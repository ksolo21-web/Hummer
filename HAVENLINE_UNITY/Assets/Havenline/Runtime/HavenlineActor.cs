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
    /// Displays individual carried resources in authored attachment slots. This replaces
    /// the old single backpack visibility toggle with the stacked-cargo look from the reference.
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
            ApplySlots(woodSlots, snapshot.wood);
            ApplySlots(stoneSlots, snapshot.stone);
            ApplySlots(metalSlots, snapshot.metal);
            ApplySlots(fuelSlots, snapshot.fuel);
        }

        private static void ApplySlots(IReadOnlyList<GameObject> slots, int visible)
        {
            for (var index = 0; index < slots.Count; index++)
            {
                if (slots[index] != null)
                    slots[index].SetActive(index < visible);
            }
        }
    }

    /// <summary>
    /// Production Mecanim bridge. Gameplay changes happen on animation impact events;
    /// a deterministic timing fallback keeps actions functional if a clip event is absent.
    /// </summary>
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

        // Called by animation events on chop/mine/attack/deposit/build/repair/rescue clips.
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
        private HavenlineAutomaticActionController automaticActions;
        private Vector3 planarVelocity;
        private Vector3 lastSafePosition;
        private Vector2 moveInput;

        public HavenlineInventory Inventory => inventory;
        public HavenlineActorAnimator ActorAnimator => actorAnimator;
        public HavenlineAutomaticActionController AutomaticActions => automaticActions;
        public Vector3 Velocity => planarVelocity;
        public float MoveInputMagnitude => moveInput.magnitude;
        public Vector3 VisualForward => visual != null ? visual.forward : transform.forward;

        public void Configure(HavenlineInputRouter router, Transform visualRoot, HavenlineActorAnimator animator)
        {
            input = router;
            visual = visualRoot;
            actorAnimator = animator;
            if (automaticActions != null)
                automaticActions.Configure(this);
        }

        private void Awake()
        {
            controller = GetComponent<CharacterController>();
            inventory = GetComponent<HavenlineInventory>();
            automaticActions = GetComponent<HavenlineAutomaticActionController>();
            automaticActions.Configure(this);
            lastSafePosition = Reference.PlayerSpawn;
            inventory.Changed += HandleInventoryChanged;
        }

        private void Start()
        {
            var saved = HavenlineSave.LoadPlayerPosition();
            transform.position = Reference.IsValidSavedPosition(saved) ? saved : Reference.PlayerSpawn;
            lastSafePosition = transform.position;
            inventory.Restore(HavenlineSave.LoadInventory());
        }

        private void OnDestroy()
        {
            if (inventory != null)
                inventory.Changed -= HandleInventoryChanged;
        }

        private void Update()
        {
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
