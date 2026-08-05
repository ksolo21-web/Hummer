using UnityEngine;
using UnityEngine.UI;

namespace Havenline
{
    /// <summary>
    /// Compact HUD: carried resources, one short objective and temporary contextual action
    /// feedback. The game world communicates progression instead of permanent dashboards.
    /// </summary>
    public sealed class HavenlineHud : MonoBehaviour
    {
        [SerializeField] private Text resourceText;
        [SerializeField] private Text objectiveText;
        [SerializeField] private Text contextualText;
        [SerializeField] private Text transientStatusText;
        [SerializeField] private Text threatText;
        [SerializeField] private Image contextualProgress;
        [SerializeField] private HavenlinePlayerController player;
        [SerializeField] private HavenlineGameDirector director;

        private AutomaticActionKind lastAction;
        private string lastLabel = string.Empty;
        private float lastProgress = -1f;
        private float statusVisibleUntil;

        public void Configure(
            Text resources,
            Text objective,
            Text furnace,
            Text helper,
            Text wave,
            Image progress,
            HavenlinePlayerController controlledPlayer,
            HavenlineGameDirector gameDirector)
        {
            resourceText = resources;
            objectiveText = objective;
            transientStatusText = furnace;
            contextualText = helper;
            threatText = wave;
            contextualProgress = progress;
            player = controlledPlayer;
            director = gameDirector;
        }

        private void OnEnable()
        {
            if (player != null && player.AutomaticActions != null)
                player.AutomaticActions.ContextChanged += HandleContext;
            if (director != null && director.Furnace != null)
                director.Furnace.LevelChanged += HandleFurnaceLevel;
        }

        private void Start()
        {
            if (player != null && player.AutomaticActions != null)
            {
                player.AutomaticActions.ContextChanged -= HandleContext;
                player.AutomaticActions.ContextChanged += HandleContext;
            }
            if (director != null && director.Furnace != null)
            {
                director.Furnace.LevelChanged -= HandleFurnaceLevel;
                director.Furnace.LevelChanged += HandleFurnaceLevel;
            }

            SetPanelVisible(contextualText, false);
            SetPanelVisible(transientStatusText, false);
            SetPanelVisible(threatText, false);
            if (contextualProgress != null)
                contextualProgress.gameObject.SetActive(false);
        }

        private void OnDisable()
        {
            if (player != null && player.AutomaticActions != null)
                player.AutomaticActions.ContextChanged -= HandleContext;
            if (director != null && director.Furnace != null)
                director.Furnace.LevelChanged -= HandleFurnaceLevel;
        }

        private void Update()
        {
            if (player == null || director == null || director.Furnace == null)
                return;

            var inventory = player.Inventory;
            if (resourceText != null)
            {
                resourceText.text = inventory.Total == 0
                    ? string.Empty
                    : $"WOOD {inventory[ResourceKind.Wood]}   STONE {inventory[ResourceKind.Stone]}   " +
                      $"METAL {inventory[ResourceKind.Metal]}   {inventory.Total}/{inventory.Capacity}";
                resourceText.gameObject.SetActive(inventory.Total > 0);
            }

            if (objectiveText != null)
                objectiveText.text = director.Objective;

            var showContext = lastAction != AutomaticActionKind.None;
            if (contextualText != null)
            {
                contextualText.text = lastLabel;
                SetPanelVisible(contextualText, showContext);
            }
            if (contextualProgress != null)
            {
                var showProgress = showContext && lastProgress >= 0f;
                contextualProgress.gameObject.SetActive(showProgress);
                if (showProgress)
                    contextualProgress.fillAmount = Mathf.Clamp01(lastProgress);
            }

            if (transientStatusText != null &&
                IsPanelVisible(transientStatusText) &&
                Time.unscaledTime >= statusVisibleUntil)
            {
                SetPanelVisible(transientStatusText, false);
            }

            if (threatText != null)
            {
                var threatSoon = director.Furnace.Level >= 2 && director.WaveClock <= 8f;
                SetPanelVisible(threatText, threatSoon);
                if (threatSoon)
                    threatText.text = $"WOLVES • {Mathf.CeilToInt(director.WaveClock)}";
            }
        }

        private void HandleContext(AutomaticActionKind action, string label, float progress)
        {
            lastAction = action;
            lastLabel = label;
            lastProgress = progress;
        }

        private void HandleFurnaceLevel(int level)
        {
            if (transientStatusText == null)
                return;
            transientStatusText.text = $"FURNACE LEVEL {level} • WARMTH EXPANDED";
            SetPanelVisible(transientStatusText, true);
            statusVisibleUntil = Time.unscaledTime + 2.4f;
        }

        private static void SetPanelVisible(Component component, bool visible)
        {
            var panel = PanelFor(component);
            if (panel != null)
                panel.SetActive(visible);
        }

        private static bool IsPanelVisible(Component component)
        {
            var panel = PanelFor(component);
            return panel != null && panel.activeSelf;
        }

        private static GameObject PanelFor(Component component)
        {
            if (component == null)
                return null;
            return component.transform.parent != null
                ? component.transform.parent.gameObject
                : component.gameObject;
        }
    }
}
