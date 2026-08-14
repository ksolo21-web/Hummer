using System;
using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
    /// <summary>
    /// Physical carry-stack presentation for the uncapped inventory. Logical carrying is never
    /// limited by the number of rendered props. Up to the authored visual-slot budget is shown;
    /// larger loads are represented proportionally and the stack grows subtly instead of spawning
    /// hundreds of renderers on mobile hardware.
    /// </summary>
    public sealed class HavenlineCarryVisual : MonoBehaviour
    {
        [SerializeField] private GameObject[] woodSlots = Array.Empty<GameObject>();
        [SerializeField] private GameObject[] stoneSlots = Array.Empty<GameObject>();
        [SerializeField] private GameObject[] metalSlots = Array.Empty<GameObject>();
        [SerializeField] private GameObject[] fuelSlots = Array.Empty<GameObject>();
        [SerializeField] private float maximumCompressedHeightScale = 1.35f;
        [SerializeField] private float maximumCompressedWidthScale = 1.10f;
        [SerializeField] private float pickupPulse = 1.075f;
        [SerializeField] private float unloadPulse = 0.94f;
        [SerializeField] private float scaleSharpness = 18f;

        private Vector3 baseScale = Vector3.one;
        private Vector3 targetScale = Vector3.one;
        private float transientPulse = 1f;
        private int lastTotal = -1;
        private bool capturedBaseScale;

        public void Configure(GameObject[] wood, GameObject[] stone, GameObject[] metal, GameObject[] fuel)
        {
            woodSlots = wood ?? Array.Empty<GameObject>();
            stoneSlots = stone ?? Array.Empty<GameObject>();
            metalSlots = metal ?? Array.Empty<GameObject>();
            fuelSlots = fuel ?? Array.Empty<GameObject>();
            CaptureBaseScale();
            targetScale = baseScale;
        }

        public void Apply(HavenlineInventorySnapshot snapshot, int total, int capacity)
        {
            CaptureBaseScale();
            var maximumSlots = Mathf.Max(
                Mathf.Max(woodSlots.Length, stoneSlots.Length),
                Mathf.Max(metalSlots.Length, fuelSlots.Length));
            var slotCount = Mathf.Min(Mathf.Max(0, total), maximumSlots);

            var display = AllocateVisibleCounts(snapshot, total, slotCount);
            var woodEnd = display.wood;
            var stoneEnd = woodEnd + display.stone;
            var metalEnd = stoneEnd + display.metal;
            var fuelEnd = metalEnd + display.fuel;

            for (var index = 0; index < maximumSlots; index++)
            {
                SetSlot(woodSlots, index, index < woodEnd);
                SetSlot(stoneSlots, index, index >= woodEnd && index < stoneEnd);
                SetSlot(metalSlots, index, index >= stoneEnd && index < metalEnd);
                SetSlot(fuelSlots, index, index >= metalEnd && index < fuelEnd);
            }

            targetScale = CompressedLoadScale(total, maximumSlots);
            if (Application.isPlaying && lastTotal >= 0 && total != lastTotal)
                transientPulse = total > lastTotal ? pickupPulse : unloadPulse;
            else if (!Application.isPlaying)
                transform.localScale = targetScale;
            lastTotal = total;
        }

        private void LateUpdate()
        {
            if (!capturedBaseScale)
                return;

            transientPulse = Mathf.Lerp(
                transientPulse,
                1f,
                1f - Mathf.Exp(-scaleSharpness * 0.72f * Time.deltaTime));
            var desired = targetScale * transientPulse;
            transform.localScale = Vector3.Lerp(
                transform.localScale,
                desired,
                1f - Mathf.Exp(-scaleSharpness * Time.deltaTime));
        }

        private HavenlineInventorySnapshot AllocateVisibleCounts(
            HavenlineInventorySnapshot snapshot,
            int total,
            int slotCount)
        {
            if (slotCount <= 0 || total <= 0)
                return new HavenlineInventorySnapshot();
            if (total <= slotCount)
                return snapshot;

            var source = new[] { snapshot.wood, snapshot.stone, snapshot.metal, snapshot.fuel };
            var assigned = new int[4];
            var fractions = new float[4];
            var used = 0;

            for (var index = 0; index < source.Length; index++)
            {
                if (source[index] <= 0)
                    continue;
                var exact = source[index] * slotCount / (float)total;
                assigned[index] = Mathf.Max(1, Mathf.FloorToInt(exact));
                fractions[index] = exact - Mathf.Floor(exact);
                used += assigned[index];
            }

            while (used > slotCount)
            {
                var reducible = -1;
                var smallestFraction = float.MaxValue;
                for (var index = 0; index < assigned.Length; index++)
                {
                    if (assigned[index] <= 1 || fractions[index] >= smallestFraction)
                        continue;
                    reducible = index;
                    smallestFraction = fractions[index];
                }
                if (reducible < 0)
                    break;
                assigned[reducible]--;
                used--;
            }

            while (used < slotCount)
            {
                var best = -1;
                var bestFraction = float.MinValue;
                for (var index = 0; index < source.Length; index++)
                {
                    if (source[index] <= 0 || fractions[index] <= bestFraction)
                        continue;
                    best = index;
                    bestFraction = fractions[index];
                }
                if (best < 0)
                    break;
                assigned[best]++;
                fractions[best] = -1f;
                used++;
            }

            return new HavenlineInventorySnapshot
            {
                wood = assigned[0],
                stone = assigned[1],
                metal = assigned[2],
                fuel = assigned[3]
            };
        }

        private Vector3 CompressedLoadScale(int total, int maximumSlots)
        {
            if (maximumSlots <= 0 || total <= maximumSlots)
                return baseScale;

            var overload = Mathf.Log(Mathf.Max(1f, total / (float)maximumSlots), 2f);
            var height = Mathf.Min(maximumCompressedHeightScale, 1f + overload * 0.09f);
            var width = Mathf.Min(maximumCompressedWidthScale, 1f + overload * 0.025f);
            return Vector3.Scale(baseScale, new Vector3(width, height, width));
        }

        private void CaptureBaseScale()
        {
            if (capturedBaseScale)
                return;
            baseScale = transform.localScale;
            targetScale = baseScale;
            capturedBaseScale = true;
        }

        private static void SetSlot(IReadOnlyList<GameObject> slots, int index, bool active)
        {
            if (index < slots.Count && slots[index] != null)
                slots[index].SetActive(active);
        }
    }
}
