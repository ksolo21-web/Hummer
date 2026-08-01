using System;
using UnityEngine;

namespace Havenline
{
    public enum HavenlineResourceKind
    {
        Wood,
        Scrap,
        Fuel,
        Food
    }

    public sealed class HavenlineInventory : MonoBehaviour
    {
        [SerializeField, Min(1)] private int capacity = 8;
        [SerializeField] private Transform carriedVisualRoot;

        private int _wood;
        private int _scrap;
        private int _fuel;
        private int _food;

        public event Action Changed;

        public int Capacity => capacity;
        public int Total => _wood + _scrap + _fuel + _food;
        public bool IsFull => Total >= capacity;
        public float Fill01 => capacity <= 0 ? 0f : Mathf.Clamp01((float)Total / capacity);

        public void SetCapacity(int value)
        {
            capacity = Mathf.Max(1, value);
            NotifyChanged();
        }

        public int Get(HavenlineResourceKind kind)
        {
            return kind switch
            {
                HavenlineResourceKind.Wood => _wood,
                HavenlineResourceKind.Scrap => _scrap,
                HavenlineResourceKind.Fuel => _fuel,
                HavenlineResourceKind.Food => _food,
                _ => 0
            };
        }

        public int Add(HavenlineResourceKind kind, int amount)
        {
            if (amount <= 0 || IsFull)
            {
                return 0;
            }

            var accepted = Mathf.Min(amount, capacity - Total);
            Set(kind, Get(kind) + accepted);
            NotifyChanged();
            return accepted;
        }

        public int Remove(HavenlineResourceKind kind, int amount)
        {
            if (amount <= 0)
            {
                return 0;
            }

            var removed = Mathf.Min(amount, Get(kind));
            Set(kind, Get(kind) - removed);
            NotifyChanged();
            return removed;
        }

        public int RemoveAll(HavenlineResourceKind kind)
        {
            return Remove(kind, Get(kind));
        }

        private void Set(HavenlineResourceKind kind, int value)
        {
            switch (kind)
            {
                case HavenlineResourceKind.Wood:
                    _wood = value;
                    break;
                case HavenlineResourceKind.Scrap:
                    _scrap = value;
                    break;
                case HavenlineResourceKind.Fuel:
                    _fuel = value;
                    break;
                case HavenlineResourceKind.Food:
                    _food = value;
                    break;
                default:
                    throw new ArgumentOutOfRangeException(nameof(kind), kind, null);
            }
        }

        private void NotifyChanged()
        {
            if (carriedVisualRoot != null)
            {
                carriedVisualRoot.gameObject.SetActive(Total > 0);
                carriedVisualRoot.localScale = Vector3.one * Mathf.Lerp(0.72f, 1f, Fill01);
            }

            Changed?.Invoke();
        }
    }
}
