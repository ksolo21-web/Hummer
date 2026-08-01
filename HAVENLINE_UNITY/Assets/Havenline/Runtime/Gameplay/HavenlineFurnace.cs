using System;
using UnityEngine;

namespace Havenline
{
    [RequireComponent(typeof(SphereCollider))]
    public sealed class HavenlineFurnace : MonoBehaviour
    {
        [SerializeField, Min(1)] private int maximumLevel = 5;
        [SerializeField, Min(1)] private int woodPerUpgrade = 8;
        [SerializeField, Min(1)] private int scrapPerUpgrade = 4;
        [SerializeField, Min(0.1f)] private float depositInterval = 0.2f;
        [SerializeField, Min(0.5f)] private float depositRadius = 1.8f;
        [SerializeField] private HavenlineWarmthZone warmthZone;
        [SerializeField] private Animator animator;
        [SerializeField] private string levelParameter = "Level";
        [SerializeField] private string depositTrigger = "Deposit";

        private int _level = 1;
        private int _storedWood;
        private int _storedScrap;
        private int _storedFuel;
        private float _nextDepositTime;
        private int _levelHash;
        private int _depositHash;
        private SphereCollider _trigger;

        public event Action Changed;

        public int Level => _level;
        public int StoredWood => _storedWood;
        public int StoredScrap => _storedScrap;
        public int StoredFuel => _storedFuel;
        public float UpgradeProgress01
        {
            get
            {
                if (_level >= maximumLevel)
                {
                    return 1f;
                }

                var woodProgress = Mathf.Clamp01((float)_storedWood / WoodRequiredForNextLevel);
                var scrapProgress = Mathf.Clamp01((float)_storedScrap / ScrapRequiredForNextLevel);
                return Mathf.Min(woodProgress, scrapProgress);
            }
        }

        public int WoodRequiredForNextLevel => woodPerUpgrade * _level;
        public int ScrapRequiredForNextLevel => scrapPerUpgrade * _level;

        private void Awake()
        {
            _trigger = GetComponent<SphereCollider>();
            _trigger.isTrigger = true;
            _trigger.radius = depositRadius;
            _levelHash = Animator.StringToHash(levelParameter);
            _depositHash = Animator.StringToHash(depositTrigger);
            ApplyLevel();
        }

        private void OnValidate()
        {
            var trigger = GetComponent<SphereCollider>();
            trigger.isTrigger = true;
            trigger.radius = depositRadius;
        }

        private void OnTriggerStay(Collider other)
        {
            if (Time.time < _nextDepositTime)
            {
                return;
            }

            var inventory = other.GetComponentInParent<HavenlineInventory>();
            if (inventory == null || inventory.Total <= 0)
            {
                return;
            }

            var deposited = 0;
            deposited += TransferAll(inventory, HavenlineResourceKind.Wood, ref _storedWood);
            deposited += TransferAll(inventory, HavenlineResourceKind.Scrap, ref _storedScrap);
            deposited += TransferAll(inventory, HavenlineResourceKind.Fuel, ref _storedFuel);

            if (deposited <= 0)
            {
                return;
            }

            _nextDepositTime = Time.time + depositInterval;
            TryUpgrade();

            if (animator != null && !string.IsNullOrWhiteSpace(depositTrigger))
            {
                animator.SetTrigger(_depositHash);
            }

            Changed?.Invoke();
        }

        private static int TransferAll(
            HavenlineInventory inventory,
            HavenlineResourceKind kind,
            ref int destination)
        {
            var amount = inventory.RemoveAll(kind);
            destination += amount;
            return amount;
        }

        private void TryUpgrade()
        {
            while (_level < maximumLevel &&
                   _storedWood >= WoodRequiredForNextLevel &&
                   _storedScrap >= ScrapRequiredForNextLevel)
            {
                _storedWood -= WoodRequiredForNextLevel;
                _storedScrap -= ScrapRequiredForNextLevel;
                _level++;
                ApplyLevel();
            }
        }

        private void ApplyLevel()
        {
            if (warmthZone != null)
            {
                warmthZone.SetLevel(_level, maximumLevel);
            }

            if (animator != null && !string.IsNullOrWhiteSpace(levelParameter))
            {
                animator.SetInteger(_levelHash, _level);
            }

            Changed?.Invoke();
        }
    }
}
