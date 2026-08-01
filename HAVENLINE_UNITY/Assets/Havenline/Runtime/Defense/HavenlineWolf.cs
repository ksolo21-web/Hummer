using UnityEngine;
using UnityEngine.AI;

namespace Havenline
{
    [RequireComponent(typeof(NavMeshAgent))]
    public sealed class HavenlineWolf : MonoBehaviour
    {
        [SerializeField, Min(0.1f)] private float attackRange = 1.25f;
        [SerializeField, Min(0.1f)] private float attackInterval = 1.1f;
        [SerializeField, Min(0.1f)] private float barricadeDamage = 16f;
        [SerializeField, Min(1f)] private float targetRefreshInterval = 0.5f;
        [SerializeField] private Animator animator;
        [SerializeField] private string speedParameter = "Speed";
        [SerializeField] private string attackTrigger = "Attack";

        private NavMeshAgent _agent;
        private HavenlineBarricade _targetBarricade;
        private Transform _fallbackTarget;
        private float _nextTargetRefresh;
        private float _nextAttackTime;
        private int _speedHash;
        private int _attackHash;

        private void Awake()
        {
            _agent = GetComponent<NavMeshAgent>();
            _speedHash = Animator.StringToHash(speedParameter);
            _attackHash = Animator.StringToHash(attackTrigger);
        }

        private void Update()
        {
            if (Time.time >= _nextTargetRefresh)
            {
                RefreshTarget();
                _nextTargetRefresh = Time.time + targetRefreshInterval;
            }

            var target = _targetBarricade != null && !_targetBarricade.IsDestroyed
                ? _targetBarricade.transform
                : _fallbackTarget;

            if (target == null)
            {
                _agent.isStopped = true;
                UpdateAnimation();
                return;
            }

            var distance = Vector3.Distance(transform.position, target.position);
            if (distance > attackRange)
            {
                _agent.isStopped = false;
                _agent.SetDestination(target.position);
            }
            else
            {
                _agent.isStopped = true;
                FaceTarget(target.position);
                AttackIfReady();
            }

            UpdateAnimation();
        }

        public void SetFallbackTarget(Transform value)
        {
            _fallbackTarget = value;
        }

        private void RefreshTarget()
        {
            HavenlineBarricade nearest = null;
            var nearestDistance = float.PositiveInfinity;

            foreach (var barricade in FindObjectsByType<HavenlineBarricade>(FindObjectsSortMode.None))
            {
                if (barricade.IsDestroyed)
                {
                    continue;
                }

                var distance = (barricade.transform.position - transform.position).sqrMagnitude;
                if (distance >= nearestDistance)
                {
                    continue;
                }

                nearestDistance = distance;
                nearest = barricade;
            }

            _targetBarricade = nearest;
        }

        private void AttackIfReady()
        {
            if (Time.time < _nextAttackTime)
            {
                return;
            }

            _nextAttackTime = Time.time + attackInterval;
            if (animator != null)
            {
                animator.SetTrigger(_attackHash);
            }

            if (_targetBarricade != null && !_targetBarricade.IsDestroyed)
            {
                _targetBarricade.ApplyDamage(barricadeDamage);
            }
        }

        private void FaceTarget(Vector3 worldPosition)
        {
            var direction = worldPosition - transform.position;
            direction.y = 0f;
            if (direction.sqrMagnitude > 0.001f)
            {
                transform.rotation = Quaternion.LookRotation(direction.normalized, Vector3.up);
            }
        }

        private void UpdateAnimation()
        {
            if (animator != null)
            {
                animator.SetFloat(_speedHash, _agent.velocity.magnitude, 0.08f, Time.deltaTime);
            }
        }
    }
}
