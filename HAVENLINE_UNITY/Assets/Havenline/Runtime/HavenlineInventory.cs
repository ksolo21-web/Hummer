using System;
using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
    public sealed class HavenlineInventory : MonoBehaviour
    {
        // Zero means uncapped. This matches the example-game carrying behavior and keeps any
        // future optional challenge-mode capacity separate from the default HAVENLINE loop.
        [SerializeField] private int capacity = Reference.CarryCapacity;
        [SerializeField] private Transform visibleCarryRoot;
        [SerializeField] private HavenlineCarryVisual carryVisual;

        private readonly Dictionary<ResourceKind, int> amounts = new();

        public int Capacity => capacity;
        public bool HasCarryLimit => capacity > 0;
        public int Total => this[ResourceKind.Wood] + this[ResourceKind.Stone] + this[ResourceKind.Metal] + this[ResourceKind.Fuel];
        public bool IsFull => HasCarryLimit && Total >= capacity;
        public int this[ResourceKind kind] => amounts.TryGetValue(kind, out var value) ? value : 0;
        public event Action Changed;

        public void Configure(Transform carryRoot)
        {
            visibleCarryRoot = carryRoot;
            if (carryVisual == null && carryRoot != null)
                carryVisual = carryRoot.GetComponent<HavenlineCarryVisual>();
            RefreshVisual();
        }

        public void Configure(Transform carryRoot, HavenlineCarryVisual visual, int carryCapacity)
        {
            visibleCarryRoot = carryRoot;
            carryVisual = visual;
            capacity = carryCapacity <= 0 ? 0 : carryCapacity;
            RefreshVisual();
        }

        public int Add(ResourceKind kind, int amount)
        {
            var requested = Mathf.Max(0, amount);
            if (requested <= 0)
                return 0;

            var accepted = HasCarryLimit
                ? Mathf.Min(requested, Mathf.Max(0, capacity - Total))
                : requested;
            if (accepted <= 0)
                return 0;

            amounts[kind] = this[kind] + accepted;
            NotifyChanged();
            return accepted;
        }

        public int Remove(ResourceKind kind, int amount)
        {
            var removed = Mathf.Min(Mathf.Max(0, amount), this[kind]);
            if (removed <= 0)
                return 0;

            amounts[kind] = this[kind] - removed;
            NotifyChanged();
            return removed;
        }

        public int RemoveAll(ResourceKind kind) => Remove(kind, this[kind]);

        public bool TryGetFirstCarried(out ResourceKind kind)
        {
            foreach (ResourceKind candidate in Enum.GetValues(typeof(ResourceKind)))
            {
                if (this[candidate] <= 0)
                    continue;

                kind = candidate;
                return true;
            }

            kind = ResourceKind.Wood;
            return false;
        }

        public HavenlineInventorySnapshot Capture() => new()
        {
            wood = this[ResourceKind.Wood],
            stone = this[ResourceKind.Stone],
            metal = this[ResourceKind.Metal],
            fuel = this[ResourceKind.Fuel]
        };

        public void Restore(HavenlineInventorySnapshot snapshot)
        {
            amounts.Clear();
            amounts[ResourceKind.Wood] = Mathf.Max(0, snapshot.wood);
            amounts[ResourceKind.Stone] = Mathf.Max(0, snapshot.stone);
            amounts[ResourceKind.Metal] = Mathf.Max(0, snapshot.metal);
            amounts[ResourceKind.Fuel] = Mathf.Max(0, snapshot.fuel);

            if (HasCarryLimit)
            {
                while (Total > capacity)
                {
                    if (amounts[ResourceKind.Fuel] > 0) amounts[ResourceKind.Fuel]--;
                    else if (amounts[ResourceKind.Metal] > 0) amounts[ResourceKind.Metal]--;
                    else if (amounts[ResourceKind.Stone] > 0) amounts[ResourceKind.Stone]--;
                    else if (amounts[ResourceKind.Wood] > 0) amounts[ResourceKind.Wood]--;
                }
            }

            NotifyChanged();
        }

        private void NotifyChanged()
        {
            RefreshVisual();
            Changed?.Invoke();
        }

        private void RefreshVisual()
        {
            if (visibleCarryRoot != null)
                visibleCarryRoot.gameObject.SetActive(Total > 0);

            carryVisual?.Apply(Capture(), Total, capacity);
        }
    }
}
