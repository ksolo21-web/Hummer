using UnityEngine;
using UnityEngine.UI;

namespace Havenline
{
    public sealed class HavenlineHudController : MonoBehaviour
    {
        [SerializeField] private HavenlineInventory playerInventory;
        [SerializeField] private HavenlineFurnace furnace;
        [SerializeField] private HavenlineSurvivorHelper helper;
        [SerializeField] private Text resourceText;
        [SerializeField] private Text furnaceText;
        [SerializeField] private Text helperText;
        [SerializeField] private Text objectiveText;
        [SerializeField] private Image warmthProgress;
        [SerializeField] private RectTransform safeAreaRoot;

        private Rect _lastSafeArea;
        private float _nextRefresh;

        public void Configure(
            HavenlineInventory inventory,
            HavenlineFurnace targetFurnace,
            HavenlineSurvivorHelper targetHelper)
        {
            playerInventory = inventory;
            furnace = targetFurnace;
            helper = targetHelper;
            Refresh();
        }

        private void OnEnable()
        {
            if (playerInventory != null)
            {
                playerInventory.Changed += Refresh;
            }

            if (furnace != null)
            {
                furnace.Changed += Refresh;
            }
        }

        private void OnDisable()
        {
            if (playerInventory != null)
            {
                playerInventory.Changed -= Refresh;
            }

            if (furnace != null)
            {
                furnace.Changed -= Refresh;
            }
        }

        private void Update()
        {
            ApplySafeArea();
            if (Time.unscaledTime >= _nextRefresh)
            {
                _nextRefresh = Time.unscaledTime + 0.2f;
                Refresh();
            }
        }

        private void ApplySafeArea()
        {
            if (safeAreaRoot == null || Screen.safeArea == _lastSafeArea)
            {
                return;
            }

            _lastSafeArea = Screen.safeArea;
            var minimum = _lastSafeArea.position;
            var maximum = _lastSafeArea.position + _lastSafeArea.size;
            minimum.x /= Screen.width;
            minimum.y /= Screen.height;
            maximum.x /= Screen.width;
            maximum.y /= Screen.height;
            safeAreaRoot.anchorMin = minimum;
            safeAreaRoot.anchorMax = maximum;
            safeAreaRoot.offsetMin = Vector2.zero;
            safeAreaRoot.offsetMax = Vector2.zero;
        }

        private void Refresh()
        {
            if (playerInventory != null && resourceText != null)
            {
                resourceText.text =
                    $"WOOD {playerInventory.Get(HavenlineResourceKind.Wood)}   " +
                    $"SCRAP {playerInventory.Get(HavenlineResourceKind.Scrap)}   " +
                    $"FUEL {playerInventory.Get(HavenlineResourceKind.Fuel)}   " +
                    $"CARRY {playerInventory.Total}/{playerInventory.Capacity}";
            }

            if (furnace != null)
            {
                if (furnaceText != null)
                {
                    furnaceText.text =
                        $"FURNACE  LV {furnace.Level}   " +
                        $"WOOD {furnace.StoredWood}/{furnace.WoodRequiredForNextLevel}   " +
                        $"SCRAP {furnace.StoredScrap}/{furnace.ScrapRequiredForNextLevel}";
                }

                if (warmthProgress != null)
                {
                    warmthProgress.fillAmount = furnace.UpgradeProgress01;
                }
            }

            if (helperText != null)
            {
                helperText.text = helper != null && helper.IsRescued ? "HELPER  ACTIVE" : "HELPER  FROZEN";
            }

            if (objectiveText != null)
            {
                objectiveText.text = ResolveObjective();
            }
        }

        private string ResolveObjective()
        {
            if (playerInventory == null || furnace == null)
            {
                return "RESTORE THE FROZEN OUTPOST";
            }

            if (furnace.Level <= 1)
            {
                return playerInventory.Total == 0
                    ? "MOVE NEAR WOOD AND SCRAP — GATHERING IS AUTOMATIC"
                    : "RETURN TO THE FURNACE — DELIVERY IS AUTOMATIC";
            }

            if (helper != null && !helper.IsRescued)
            {
                return "EXPAND THE WARMTH ZONE TO RESCUE THE SURVIVOR";
            }

            var wolves = FindObjectsByType<HavenlineWolf>(FindObjectsSortMode.None);
            if (wolves.Length > 0)
            {
                return "HOLD THE BARRICADES — WOLVES ARE APPROACHING";
            }

            return "KEEP THE FURNACE FED AND EXPAND THE OUTPOST";
        }
    }
}
