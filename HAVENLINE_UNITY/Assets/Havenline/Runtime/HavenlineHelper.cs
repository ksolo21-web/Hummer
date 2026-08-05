using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using UnityEngine;

namespace Havenline
{
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
}
