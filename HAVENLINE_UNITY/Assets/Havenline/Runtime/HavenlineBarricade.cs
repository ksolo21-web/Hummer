using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using UnityEngine;

namespace Havenline
{
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
}
