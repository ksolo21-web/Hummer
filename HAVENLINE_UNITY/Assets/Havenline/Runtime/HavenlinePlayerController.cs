using System;
using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
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
