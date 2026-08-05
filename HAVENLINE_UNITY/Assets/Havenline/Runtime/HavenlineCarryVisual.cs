using System;
using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
    /// <summary>
    /// Each physical stack position contains one visual option for every resource type.
    /// Exactly one option is enabled per occupied slot, preventing mixed cargo from
    /// intersecting while preserving the visible stacked-resource reference behavior.
    /// </summary>
    public sealed class HavenlineCarryVisual : MonoBehaviour
    {
        [SerializeField] private GameObject[] woodSlots = Array.Empty<GameObject>();
        [SerializeField] private GameObject[] stoneSlots = Array.Empty<GameObject>();
        [SerializeField] private GameObject[] metalSlots = Array.Empty<GameObject>();
        [SerializeField] private GameObject[] fuelSlots = Array.Empty<GameObject>();

        public void Configure(GameObject[] wood, GameObject[] stone, GameObject[] metal, GameObject[] fuel)
        {
            woodSlots = wood ?? Array.Empty<GameObject>();
            stoneSlots = stone ?? Array.Empty<GameObject>();
            metalSlots = metal ?? Array.Empty<GameObject>();
            fuelSlots = fuel ?? Array.Empty<GameObject>();
        }

        public void Apply(HavenlineInventorySnapshot snapshot, int total, int capacity)
        {
            var slotCount = Mathf.Min(
                Mathf.Max(0, capacity),
                Mathf.Max(
                    Mathf.Max(woodSlots.Length, stoneSlots.Length),
                    Mathf.Max(metalSlots.Length, fuelSlots.Length)));

            var woodEnd = snapshot.wood;
            var stoneEnd = woodEnd + snapshot.stone;
            var metalEnd = stoneEnd + snapshot.metal;
            var fuelEnd = metalEnd + snapshot.fuel;

            for (var index = 0; index < slotCount; index++)
            {
                SetSlot(woodSlots, index, index < woodEnd);
                SetSlot(stoneSlots, index, index >= woodEnd && index < stoneEnd);
                SetSlot(metalSlots, index, index >= stoneEnd && index < metalEnd);
                SetSlot(fuelSlots, index, index >= metalEnd && index < fuelEnd);
            }

            DisableRemaining(woodSlots, slotCount);
            DisableRemaining(stoneSlots, slotCount);
            DisableRemaining(metalSlots, slotCount);
            DisableRemaining(fuelSlots, slotCount);
        }

        private static void SetSlot(IReadOnlyList<GameObject> slots, int index, bool active)
        {
            if (index < slots.Count && slots[index] != null)
                slots[index].SetActive(active);
        }

        private static void DisableRemaining(IReadOnlyList<GameObject> slots, int start)
        {
            for (var index = start; index < slots.Count; index++)
            {
                if (slots[index] != null)
                    slots[index].SetActive(false);
            }
        }
    }
}
