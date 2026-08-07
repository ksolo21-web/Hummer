using UnityEngine;
using UnityEngine.UI;

namespace Havenline
{
    /// <summary>
    /// Compact HUD: carried resources, one short objective and temporary contextual action
    /// feedback. The game world communicates progression instead of permanent dashboards.
    /// </summary>
    [ExecuteAlways]
    public sealed class HavenlineHud : MonoBehaviour
    {
        private const float FoldableAspectThreshold = 1.45f;
        private const float TopPanelOpacity = 1f;

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
        private float lastLayoutAspect = -1f;
        private bool foldableTopLayout;

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
            lastLayoutAspect = -1f;
            ApplyAdaptiveTopLayout();
        }

        private void OnEnable()
        {
            Canvas.preWillRenderCanvases -= ApplyAdaptiveTopLayout;
            Canvas.preWillRenderCanvases += ApplyAdaptiveTopLayout;
            ApplyAdaptiveTopLayout();

            if (!Application.isPlaying)
                return;
            if (player != null && player.AutomaticActions != null)
                player.AutomaticActions.ContextChanged += HandleContext;
            if (director != null && director.Furnace != null)
                director.Furnace.LevelChanged += HandleFurnaceLevel;
        }

        private void Start()
        {
            if (!Application.isPlaying)
                return;

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
            Canvas.preWillRenderCanvases -= ApplyAdaptiveTopLayout;
            if (!Application.isPlaying)
                return;
            if (player != null && player.AutomaticActions != null)
                player.AutomaticActions.ContextChanged -= HandleContext;
            if (director != null && director.Furnace != null)
                director.Furnace.LevelChanged -= HandleFurnaceLevel;
        }

        private void OnRectTransformDimensionsChange()
        {
            lastLayoutAspect = -1f;
            ApplyAdaptiveTopLayout();
        }

        private void OnValidate()
        {
            lastLayoutAspect = -1f;
            ApplyAdaptiveTopLayout();
        }

        private void Update()
        {
            ApplyAdaptiveTopLayout();
            if (!Application.isPlaying || player == null || director == null || director.Furnace == null)
                return;

            var inventory = player.Inventory;
            if (resourceText != null)
            {
                resourceText.text = inventory.Total == 0
                    ? string.Empty
                    : foldableTopLayout
                        ? $"WOOD {inventory[ResourceKind.Wood]}   STONE {inventory[ResourceKind.Stone]}\n" +
                          $"METAL {inventory[ResourceKind.Metal]}   {inventory.Total}/{inventory.Capacity}"
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

        private void ApplyAdaptiveTopLayout()
        {
            var canvas = GetComponentInParent<Canvas>();
            if (canvas == null)
                return;

            var resourcesPanel = PanelRectFor(resourceText);
            var objectivePanel = PanelRectFor(objectiveText);
            var furnacePanel = PanelRectFor(transientStatusText);
            SetPanelOpacity(resourcesPanel, TopPanelOpacity);
            SetPanelOpacity(objectivePanel, TopPanelOpacity);
            SetPanelOpacity(furnacePanel, TopPanelOpacity);

            var pixelRect = canvas.pixelRect;
            var width = pixelRect.width;
            var height = pixelRect.height;
            if (width <= 1f || height <= 1f)
            {
                var rootRect = canvas.transform as RectTransform;
                if (rootRect == null || rootRect.rect.width <= 1f || rootRect.rect.height <= 1f)
                    return;
                width = rootRect.rect.width;
                height = rootRect.rect.height;
            }

            var aspect = width / height;
            if (Mathf.Abs(lastLayoutAspect - aspect) < 0.0025f)
                return;
            lastLayoutAspect = aspect;
            foldableTopLayout = aspect < FoldableAspectThreshold;

            if (foldableTopLayout)
            {
                // Fold mode still uses the same compact 72 px card height. Resource text
                // wraps to two smaller lines instead of expanding back into a dashboard.
                SetTopRect(resourcesPanel, new Vector2(0f, 1f), new Vector2(24f, -24f), new Vector2(430f, 72f));
                SetTopRect(furnacePanel, new Vector2(1f, 1f), new Vector2(-24f, -24f), new Vector2(255f, 72f));
                SetTopRect(objectivePanel, new Vector2(0.5f, 1f), new Vector2(0f, -108f), new Vector2(540f, 72f));
                ConfigureTopText(resourceText, 18, HorizontalWrapMode.Wrap);
                ConfigureTopText(objectiveText, 19, HorizontalWrapMode.Wrap);
                ConfigureTopText(transientStatusText, 18, HorizontalWrapMode.Wrap);
            }
            else
            {
                // Match the authored reference-grade HUD dimensions. ExecuteAlways used to
                // overwrite these with 500x92 cards after the final visual pass, which made
                // the generated shipping scene fail its own compact-HUD contract.
                SetTopRect(resourcesPanel, new Vector2(0f, 1f), new Vector2(26f, -24f), new Vector2(430f, 72f));
                SetTopRect(objectivePanel, new Vector2(0.5f, 1f), new Vector2(0f, -24f), new Vector2(500f, 68f));
                SetTopRect(furnacePanel, new Vector2(1f, 1f), new Vector2(-26f, -24f), new Vector2(255f, 72f));
                ConfigureTopText(resourceText, 22, HorizontalWrapMode.Overflow);
                ConfigureTopText(objectiveText, 22, HorizontalWrapMode.Overflow);
                ConfigureTopText(transientStatusText, 20, HorizontalWrapMode.Overflow);
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

        private static void SetTopRect(RectTransform rect, Vector2 anchor, Vector2 position, Vector2 size)
        {
            if (rect == null)
                return;
            rect.anchorMin = anchor;
            rect.anchorMax = anchor;
            rect.pivot = anchor;
            rect.anchoredPosition = position;
            rect.sizeDelta = size;
        }

        private static void ConfigureTopText(Text text, int fontSize, HorizontalWrapMode wrapMode)
        {
            if (text == null)
                return;
            text.fontSize = fontSize;
            text.horizontalOverflow = wrapMode;
            text.verticalOverflow = VerticalWrapMode.Truncate;
            text.resizeTextForBestFit = false;
        }

        private static void SetPanelOpacity(RectTransform rect, float alpha)
        {
            if (rect == null)
                return;
            var image = rect.GetComponent<Image>();
            if (image == null)
                return;
            var color = image.color;
            color.a = alpha;
            image.color = color;
        }

        private static RectTransform PanelRectFor(Component component)
        {
            if (component == null || component.transform.parent == null)
                return null;
            return component.transform.parent as RectTransform;
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
