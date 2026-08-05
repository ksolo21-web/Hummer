using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using UnityEngine;

namespace Havenline
{
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
        private float playerHitElapsed;
        private float enemyAttackElapsed;
        private float targetRefreshClock;
        private bool dying;

        public bool IsAlive => health > 0f && !dying;
        public override AutomaticActionKind ActionKind => AutomaticActionKind.Combat;
        public override int Priority => 200;
        public override float InteractionRange => Reference.CombatRadius;
        public override string ContextLabel => "Defending";
        public override float NormalizedProgress => -1f;

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
            controller = GetComponent<CharacterController>();
            if (controller == null)
                controller = gameObject.AddComponent<CharacterController>();
            ResetForSpawn();
        }

        public void Configure(Transform visualRoot, HavenlineActorAnimator actorAnimator)
        {
            visual = visualRoot;
            animator = actorAnimator;
        }

        public void ResetForSpawn()
        {
            StopAllCoroutines();
            health = maxHealth;
            dying = false;
            playerHitElapsed = 0f;
            enemyAttackElapsed = 0f;
            targetRefreshClock = 0f;
            targetBarricade = null;
            if (controller != null)
                controller.enabled = true;
        }

        public override bool CanInteract(HavenlinePlayerController actor) => IsAlive;

        public override void TickInteraction(HavenlinePlayerController actor, float deltaTime)
        {
            if (!IsAlive)
                return;
            if (ConsumeImpact(actor.ActorAnimator, ref playerHitElapsed, 0.64f, deltaTime))
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
                targetBarricade = HavenlineWorldRegistry.ClosestStandingBarricade(transform.position);
            }

            var furnace = HavenlineFurnace.Instance;
            var hasBarricade = targetBarricade != null && targetBarricade.IsBuilt;
            var target = hasBarricade ? targetBarricade.transform.position : furnace != null ? furnace.transform.position : Reference.Furnace;
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
            if (!ConsumeImpact(animator, ref enemyAttackElapsed, 1.1f, Time.deltaTime))
                return;

            if (hasBarricade)
                targetBarricade.Damage(16f);
            else
                furnace?.Damage(14f);
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
            dying = true;
            if (controller != null)
                controller.enabled = false;
            animator?.PlayDeath();
            if (droppedResource != null)
                Instantiate(droppedResource, transform.position, Quaternion.identity);
            StartCoroutine(ReturnAfterDeath());
        }

        private IEnumerator ReturnAfterDeath()
        {
            yield return new WaitForSeconds(1.15f);
            HavenlineEnemyPool.Return(this);
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
