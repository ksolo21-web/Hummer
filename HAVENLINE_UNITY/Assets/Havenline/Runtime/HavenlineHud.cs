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
        // Compact top cards are intentionally opaque. Their footprint is small enough to preserve
        // the play space, while opacity prevents bright snow/props from reading through status
        // text. Keep this aligned with the deterministic editor HUD contract.
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

        public HavenlinePlayerController ControlledPlayer => player;
        public HavenlineGameDirector Director => director;

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
            director = gameDirector;
            RebindControlledPlayer(controlledPlayer);
            lastLayoutAspect = -1f;
            ApplyAdaptiveTopLayout();
            ApplyIdleVisibility();
        }

        public void RebindControlledPlayer(HavenlinePlayerController controlledPlayer)
        {
            if (ReferenceEquals(player, controlledPlayer))
                return;

            if (Application.isPlaying && player != null && player.AutomaticActions != null)
                player.AutomaticActions.ContextChanged -= HandleContext;

            player = controlledPlayer;
            lastAction = AutomaticActionKind.None;
            lastLabel = string.Empty;
            lastProgress = -1f;

            SetPanelVisible(contextualText, false);
            if (contextualProgress != null)
                contextualProgress.gameObject.SetActive(false);

            if (Application.isPlaying && isActiveAndEnabled && player != null && player.AutomaticActions != null)
            {
                player.AutomaticActions.ContextChanged -= HandleContext;
                player.AutomaticActions.ContextChanged += HandleContext;
            }
        }

        private void OnEnable()
        {
            Canvas.preWillRenderCanvases -= ApplyAdaptiveTopLayout;
            Canvas.preWillRenderCanvases += ApplyAdaptiveTopLayout;
            ApplyAdaptiveTopLayout();

            if (!Application.isPlaying)
            {
                ApplyIdleVisibility();
                return;
            }

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

            ApplyIdleVisibility();
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
            if (!Application.isPlaying)
                ApplyIdleVisibility();
        }

        private void Update()
        {
            ApplyAdaptiveTopLayout();
            if (!Application.isPlaying || player == null || director == null || director.Furnace == null)
                return;

            var inventory = player.Inventory;
            if (resourceText != null)
            {
                var showResources = inventory.Total > 0;
                SetPanelVisible(resourceText, showResources);
                if (showResources)
                {
                    resourceText.text = foldableTopLayout
                        ? $"WOOD {inventory[ResourceKind.Wood]}   STONE {inventory[ResourceKind.Stone]}\n" +
                          $"METAL {inventory[ResourceKind.Metal]}   LOAD {inventory.Total}"
                        : $"WOOD {inventory[ResourceKind.Wood]}   STONE {inventory[ResourceKind.Stone]}   " +
                          $"METAL {inventory[ResourceKind.Metal]}   LOAD {inventory.Total}";
                }
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
                SetTopRect(resourcesPanel, new Vector2(0f, 1f), new Vector2(22f, -22f), new Vector2(390f, 64f));
                SetTopRect(furnacePanel, new Vector2(1f, 1f), new Vector2(-22f, -22f), new Vector2(235f, 62f));
                SetTopRect(objectivePanel, new Vector2(0.5f, 1f), new Vector2(0f, -92f), new Vector2(470f, 62f));
                ConfigureTopText(resourceText, 17, HorizontalWrapMode.Wrap);
                ConfigureTopText(objectiveText, 18, HorizontalWrapMode.Wrap);
                ConfigureTopText(transientStatusText, 17, HorizontalWrapMode.Wrap);
            }
            else
            {
                SetTopRect(resourcesPanel, new Vector2(0f, 1f), new Vector2(22f, -22f), new Vector2(408f, 60f));
                SetTopRect(objectivePanel, new Vector2(0.5f, 1f), new Vector2(0f, -22f), new Vector2(460f, 60f));
                SetTopRect(furnacePanel, new Vector2(1f, 1f), new Vector2(-22f, -22f), new Vector2(235f, 60f));
                ConfigureTopText(resourceText, 19, HorizontalWrapMode.Overflow);
                ConfigureTopText(objectiveText, 20, HorizontalWrapMode.Overflow);
                ConfigureTopText(transientStatusText, 18, HorizontalWrapMode.Overflow);
            }
        }

        private void ApplyIdleVisibility()
        {
            SetPanelVisible(resourceText, false);
            SetPanelVisible(contextualText, false);
            SetPanelVisible(transientStatusText, false);
            SetPanelVisible(threatText, false);
            if (contextualProgress != null)
                contextualProgress.gameObject.SetActive(false);
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
