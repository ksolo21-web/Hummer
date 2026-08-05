using UnityEngine;
using UnityEngine.UI;

namespace Havenline
{
    [RequireComponent(typeof(Camera))]
    public sealed class HavenlineCameraRig : MonoBehaviour
    {
        [SerializeField] private Transform target;
        [SerializeField] private float maximumLookAhead = Reference.CameraLookAhead;
        private Camera cameraComponent;
        private Vector3 smoothedTarget;

        public void Configure(Transform followTarget)
        {
            target = followTarget;
            Snap();
        }

        private void Awake()
        {
            cameraComponent = GetComponent<Camera>();
            cameraComponent.orthographic = true;
            cameraComponent.orthographicSize = Reference.CameraSize;
        }

        private void Start() => Snap();

        private void LateUpdate()
        {
            if (target == null)
                return;

            var player = target.GetComponent<HavenlinePlayerController>();
            var velocity = player != null ? player.Velocity : Vector3.zero;
            var speedFraction = Mathf.Clamp01(velocity.magnitude / Reference.RunSpeed);
            var lookAhead = velocity.sqrMagnitude > 0.01f
                ? velocity.normalized * maximumLookAhead * speedFraction
                : Vector3.zero;
            var wanted = target.position + Vector3.up * Reference.CameraFocusHeight + lookAhead;
            smoothedTarget = Vector3.Lerp(
                smoothedTarget,
                wanted,
                1f - Mathf.Exp(-Reference.CameraFollowSharpness * Time.deltaTime));
            transform.position = smoothedTarget + Reference.CameraOffset;
            transform.rotation = Quaternion.LookRotation(smoothedTarget - transform.position, Vector3.up);
        }

        private void Snap()
        {
            if (target == null)
                return;
            smoothedTarget = target.position + Vector3.up * Reference.CameraFocusHeight;
            transform.position = smoothedTarget + Reference.CameraOffset;
            transform.rotation = Quaternion.LookRotation(smoothedTarget - transform.position, Vector3.up);
        }
    }

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
            SetVisible(contextualText, false);
            SetVisible(transientStatusText, false);
            SetVisible(threatText, false);
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

            if (contextualText != null)
            {
                contextualText.text = lastLabel;
                contextualText.gameObject.SetActive(lastAction != AutomaticActionKind.None);
            }
            if (contextualProgress != null)
            {
                var showProgress = lastAction != AutomaticActionKind.None && lastProgress >= 0f;
                contextualProgress.gameObject.SetActive(showProgress);
                if (showProgress)
                    contextualProgress.fillAmount = Mathf.Clamp01(lastProgress);
            }

            if (transientStatusText != null && transientStatusText.gameObject.activeSelf && Time.unscaledTime >= statusVisibleUntil)
                transientStatusText.gameObject.SetActive(false);

            if (threatText != null)
            {
                var threatSoon = director.Furnace.Level >= 2 && director.WaveClock <= 8f;
                threatText.gameObject.SetActive(threatSoon);
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
            transientStatusText.gameObject.SetActive(true);
            statusVisibleUntil = Time.unscaledTime + 2.4f;
        }

        private static void SetVisible(Component component, bool visible)
        {
            if (component != null)
                component.gameObject.SetActive(visible);
        }
    }

    public sealed class HavenlineSnowfall : MonoBehaviour
    {
        [SerializeField] private ParticleSystem particles;

        private void Awake()
        {
            if (particles == null)
                particles = GetComponent<ParticleSystem>();
        }

        private void LateUpdate()
        {
            if (particles == null || Camera.main == null)
                return;
            transform.position = Camera.main.transform.position + Camera.main.transform.forward * 6f + Vector3.up * 5f;
        }
    }
}
