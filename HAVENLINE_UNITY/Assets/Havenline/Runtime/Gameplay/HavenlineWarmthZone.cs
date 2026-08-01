using System;
using UnityEngine;

namespace Havenline
{
    [RequireComponent(typeof(SphereCollider))]
    public sealed class HavenlineWarmthZone : MonoBehaviour
    {
        [SerializeField, Min(0.5f)] private float initialRadius = 4f;
        [SerializeField, Min(1f)] private float maximumRadius = 11f;
        [SerializeField] private Transform visualRing;
        [SerializeField, Min(0.1f)] private float visualSharpness = 8f;

        private SphereCollider _trigger;
        private float _targetRadius;
        private float _currentRadius;

        public event Action<float> RadiusChanged;

        public float Radius => _currentRadius;

        private void Awake()
        {
            _trigger = GetComponent<SphereCollider>();
            _trigger.isTrigger = true;
            _targetRadius = initialRadius;
            _currentRadius = initialRadius;
            ApplyRadius(_currentRadius);
        }

        private void Update()
        {
            var next = Mathf.Lerp(
                _currentRadius,
                _targetRadius,
                1f - Mathf.Exp(-visualSharpness * Time.deltaTime));

            if (Mathf.Abs(next - _currentRadius) < 0.001f)
            {
                return;
            }

            _currentRadius = next;
            ApplyRadius(_currentRadius);
        }

        public void Configure(float initial, float maximum)
        {
            initialRadius = Mathf.Max(0.5f, initial);
            maximumRadius = Mathf.Max(initialRadius + 0.5f, maximum);
            _targetRadius = Mathf.Clamp(_targetRadius, initialRadius, maximumRadius);
        }

        public void SetLevel(int level, int maximumLevel)
        {
            var normalized = maximumLevel <= 1
                ? 1f
                : Mathf.InverseLerp(1f, maximumLevel, Mathf.Clamp(level, 1, maximumLevel));
            _targetRadius = Mathf.Lerp(initialRadius, maximumRadius, normalized);
        }

        public bool Contains(Vector3 worldPosition)
        {
            var offset = worldPosition - transform.position;
            offset.y = 0f;
            return offset.sqrMagnitude <= _currentRadius * _currentRadius;
        }

        private void ApplyRadius(float radius)
        {
            _trigger.radius = radius;
            if (visualRing != null)
            {
                visualRing.localScale = new Vector3(radius * 2f, 1f, radius * 2f);
            }

            RadiusChanged?.Invoke(radius);
        }
    }
}
