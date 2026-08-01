using System;
using UnityEngine;

namespace Havenline
{
    [CreateAssetMenu(fileName = "HavenlineProductionConfig", menuName = "HAVENLINE/Production Config")]
    public sealed class HavenlineProductionConfig : ScriptableObject
    {
        [Header("Original HAVENLINE production prefabs")]
        [SerializeField] private GameObject playerPrefab;
        [SerializeField] private GameObject survivorPrefab;
        [SerializeField] private GameObject wolfPrefab;
        [SerializeField] private GameObject furnacePrefab;
        [SerializeField] private GameObject barricadePrefab;
        [SerializeField] private GameObject tentPrefab;
        [SerializeField] private GameObject[] resourcePrefabs = Array.Empty<GameObject>();

        [Header("Gameplay")]
        [SerializeField, Min(1f)] private float playableRadius = 16f;
        [SerializeField, Min(1f)] private float initialWarmthRadius = 4f;
        [SerializeField, Min(1f)] private float maximumWarmthRadius = 11f;
        [SerializeField, Min(1)] private int carryCapacity = 8;

        public GameObject PlayerPrefab => playerPrefab;
        public GameObject SurvivorPrefab => survivorPrefab;
        public GameObject WolfPrefab => wolfPrefab;
        public GameObject FurnacePrefab => furnacePrefab;
        public GameObject BarricadePrefab => barricadePrefab;
        public GameObject TentPrefab => tentPrefab;
        public GameObject[] ResourcePrefabs => resourcePrefabs;
        public float PlayableRadius => playableRadius;
        public float InitialWarmthRadius => initialWarmthRadius;
        public float MaximumWarmthRadius => maximumWarmthRadius;
        public int CarryCapacity => carryCapacity;

        public void ValidateOrThrow()
        {
            Require(playerPrefab, nameof(playerPrefab));
            Require(survivorPrefab, nameof(survivorPrefab));
            Require(wolfPrefab, nameof(wolfPrefab));
            Require(furnacePrefab, nameof(furnacePrefab));
            Require(barricadePrefab, nameof(barricadePrefab));
            Require(tentPrefab, nameof(tentPrefab));

            if (resourcePrefabs == null || resourcePrefabs.Length < 2)
            {
                throw new InvalidOperationException(
                    "HAVENLINE production requires at least two approved resource prefabs. " +
                    "Development cubes and generated block art are not accepted as production assets.");
            }

            for (var index = 0; index < resourcePrefabs.Length; index++)
            {
                Require(resourcePrefabs[index], $"resourcePrefabs[{index}]");
            }

            if (maximumWarmthRadius <= initialWarmthRadius)
            {
                throw new InvalidOperationException("Maximum warmth radius must exceed the initial warmth radius.");
            }
        }

        private static void Require(UnityEngine.Object value, string field)
        {
            if (value == null)
            {
                throw new InvalidOperationException(
                    $"HAVENLINE production asset '{field}' is missing. " +
                    "The build is intentionally blocked until the original approved asset is assigned.");
            }
        }
    }
}
