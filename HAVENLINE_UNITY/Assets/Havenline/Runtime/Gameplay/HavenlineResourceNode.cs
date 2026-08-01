using UnityEngine;

namespace Havenline
{
    [RequireComponent(typeof(SphereCollider))]
    public sealed class HavenlineResourceNode : MonoBehaviour
    {
        [SerializeField] private HavenlineResourceKind kind = HavenlineResourceKind.Wood;
        [SerializeField, Min(1)] private int remaining = 12;
        [SerializeField, Min(0.1f)] private float gatherInterval = 0.45f;
        [SerializeField, Min(0.5f)] private float gatherRadius = 1.55f;
        [SerializeField] private Animator animator;
        [SerializeField] private string gatherTrigger = "Gathered";
        [SerializeField] private GameObject depletedVisual;

        private float _nextGatherTime;
        private int _gatherTriggerHash;
        private SphereCollider _trigger;

        public HavenlineResourceKind Kind => kind;
        public int Remaining => remaining;
        public bool IsDepleted => remaining <= 0;

        private void Awake()
        {
            _trigger = GetComponent<SphereCollider>();
            _trigger.isTrigger = true;
            _trigger.radius = gatherRadius;
            _gatherTriggerHash = Animator.StringToHash(gatherTrigger);
        }

        private void OnValidate()
        {
            var trigger = GetComponent<SphereCollider>();
            trigger.isTrigger = true;
            trigger.radius = gatherRadius;
        }

        private void OnTriggerStay(Collider other)
        {
            if (IsDepleted || Time.time < _nextGatherTime)
            {
                return;
            }

            var inventory = other.GetComponentInParent<HavenlineInventory>();
            if (inventory == null || inventory.IsFull)
            {
                return;
            }

            var accepted = inventory.Add(kind, 1);
            if (accepted <= 0)
            {
                return;
            }

            remaining -= accepted;
            _nextGatherTime = Time.time + gatherInterval;

            if (animator != null && !string.IsNullOrWhiteSpace(gatherTrigger))
            {
                animator.SetTrigger(_gatherTriggerHash);
            }

            if (remaining <= 0)
            {
                SetDepleted();
            }
        }

        private void SetDepleted()
        {
            remaining = 0;
            _trigger.enabled = false;

            if (depletedVisual != null)
            {
                depletedVisual.SetActive(true);
            }

            foreach (var renderer in GetComponentsInChildren<Renderer>())
            {
                renderer.enabled = depletedVisual != null && renderer.gameObject == depletedVisual;
            }
        }
    }
}
