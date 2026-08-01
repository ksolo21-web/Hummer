using System;
using UnityEngine;

namespace Havenline
{
    public sealed class HavenlineBarricade : MonoBehaviour
    {
        [SerializeField, Min(1f)] private float maximumHealth = 100f;
        [SerializeField] private Animator animator;
        [SerializeField] private string damageTrigger = "Damage";
        [SerializeField] private string destroyedTrigger = "Destroyed";
        [SerializeField] private Collider blockingCollider;

        private float _health;
        private int _damageHash;
        private int _destroyedHash;

        public event Action<float> HealthChanged;
        public event Action Destroyed;

        public float Health => _health;
        public float Health01 => maximumHealth <= 0f ? 0f : Mathf.Clamp01(_health / maximumHealth);
        public bool IsDestroyed => _health <= 0f;

        private void Awake()
        {
            _health = maximumHealth;
            _damageHash = Animator.StringToHash(damageTrigger);
            _destroyedHash = Animator.StringToHash(destroyedTrigger);

            if (blockingCollider == null)
            {
                blockingCollider = GetComponentInChildren<Collider>();
            }
        }

        public void ApplyDamage(float amount)
        {
            if (amount <= 0f || IsDestroyed)
            {
                return;
            }

            _health = Mathf.Max(0f, _health - amount);
            HealthChanged?.Invoke(Health01);

            if (_health <= 0f)
            {
                if (blockingCollider != null)
                {
                    blockingCollider.enabled = false;
                }

                if (animator != null)
                {
                    animator.SetTrigger(_destroyedHash);
                }

                Destroyed?.Invoke();
            }
            else if (animator != null)
            {
                animator.SetTrigger(_damageHash);
            }
        }
    }
}
