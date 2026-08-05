using System;
using UnityEngine;

namespace Havenline
{
    public sealed class HavenlineConstructionSite : HavenlineInteractable
    {
        private static readonly float[] MinimumStageWorldHeights = { 0.72f, 1.02f, 1.22f };
        private static readonly float[] MinimumStageWorldWidths = { 1.80f, 2.55f, 3.10f };

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
        public override float NormalizedProgress => Mathf.Clamp01(
            (deliveredWood + deliveredStone) / (float)Mathf.Max(1, requiredWood + requiredStone));

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
            EnsureReadableConstructionStages();
            ApplyVisuals();
        }

        private void Start()
        {
            var snapshot = HavenlineSave.LoadConstruction(buildId);
            deliveredWood = Mathf.Clamp(snapshot.deliveredWood, 0, requiredWood);
            deliveredStone = Mathf.Clamp(snapshot.deliveredStone, 0, requiredStone);
            IsBuilt = snapshot.built || HavenlineSave.IsConstructionBuilt(buildId);
            if (IsBuilt)
            {
                deliveredWood = requiredWood;
                deliveredStone = requiredStone;
            }
            EnsureReadableConstructionStages();
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
            if (ConsumeImpact(actor.ActorAnimator, ref buildElapsed, 0.24f, deltaTime))
                Contribute(actor.Inventory);
        }

        public bool ContributeForHelper(HavenlineInventory inventory, HavenlineActorAnimator animator, float deltaTime)
        {
            if (IsBuilt || inventory == null)
                return false;
            if (ConsumeImpact(animator, ref buildElapsed, 0.32f, deltaTime))
                Contribute(inventory);
            return !IsBuilt;
        }

        public bool Needs(ResourceKind kind) => kind switch
        {
            ResourceKind.Wood => deliveredWood < requiredWood,
            ResourceKind.Stone => deliveredStone < requiredStone,
            _ => false
        };

        public HavenlineConstructionSnapshot Capture() => new()
        {
            id = buildId,
            deliveredWood = deliveredWood,
            deliveredStone = deliveredStone,
            built = IsBuilt
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
            HavenlineSave.MarkDirty();
            ApplyVisuals();
        }

        private void EnsureReadableConstructionStages()
        {
            for (var index = 0; index < constructionStages.Length; index++)
            {
                var stage = constructionStages[index];
                if (stage == null || !TryGetRendererBounds(stage, out var bounds))
                    continue;

                var targetIndex = Mathf.Min(index, MinimumStageWorldHeights.Length - 1);
                var heightScale = MinimumStageWorldHeights[targetIndex] /
                                  Mathf.Max(0.0001f, bounds.size.y);
                var widthScale = MinimumStageWorldWidths[targetIndex] /
                                 Mathf.Max(0.0001f, Mathf.Max(bounds.size.x, bounds.size.z));
                var requiredScale = Mathf.Max(1f, heightScale, widthScale);
                if (requiredScale > 1.001f)
                    stage.transform.localScale *= requiredScale;
            }
        }

        private static bool TryGetRendererBounds(GameObject root, out Bounds bounds)
        {
            var renderers = root.GetComponentsInChildren<Renderer>(true);
            bounds = default;
            var initialized = false;
            foreach (var renderer in renderers)
            {
                if (renderer == null)
                    continue;
                if (!initialized)
                {
                    bounds = renderer.bounds;
                    initialized = true;
                }
                else
                {
                    bounds.Encapsulate(renderer.bounds);
                }
            }
            return initialized && bounds.size.sqrMagnitude > 0.000001f;
        }

        private void ApplyVisuals()
        {
            var visibleStage = constructionStages.Length == 0
                ? -1
                : Mathf.Clamp(Mathf.FloorToInt(NormalizedProgress * constructionStages.Length), 0, constructionStages.Length - 1);
            for (var index = 0; index < constructionStages.Length; index++)
            {
                if (constructionStages[index] != null)
                    constructionStages[index].SetActive(!IsBuilt && index == visibleStage);
            }
            if (completedStructure != null)
                completedStructure.SetActive(IsBuilt);
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
