using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using UnityEngine;

namespace Havenline
{
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
}
