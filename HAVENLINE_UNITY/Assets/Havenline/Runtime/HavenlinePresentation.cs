using UnityEngine;
using UnityEngine.UI;

namespace Havenline
{
    [RequireComponent(typeof(Camera))]
    public sealed class HavenlineCameraRig : MonoBehaviour
    {
        [SerializeField] private Transform target;
        private Camera cameraComponent;
        private Vector3 smoothedTarget;
        public void Configure(Transform followTarget) { target = followTarget; Snap(); }
        private void Awake()
        {
            cameraComponent = GetComponent<Camera>(); cameraComponent.orthographic = true; cameraComponent.orthographicSize = Reference.CameraSize;
        }
        private void Start() => Snap();
        private void LateUpdate()
        {
            if (target == null) return;
            var velocity = target.GetComponent<HavenlinePlayerController>()?.Velocity ?? Vector3.zero;
            var wanted = target.position + Vector3.up * Reference.CameraFocusHeight + velocity.normalized * Reference.CameraLookAhead * Mathf.Clamp01(velocity.magnitude);
            smoothedTarget = Vector3.Lerp(smoothedTarget, wanted, 1f - Mathf.Exp(-7.5f * Time.deltaTime));
            transform.position = smoothedTarget + Reference.CameraOffset;
            transform.rotation = Quaternion.LookRotation(smoothedTarget - transform.position, Vector3.up);
        }
        private void Snap()
        {
            if (target == null) return; smoothedTarget = target.position + Vector3.up * Reference.CameraFocusHeight;
            transform.position = smoothedTarget + Reference.CameraOffset; transform.rotation = Quaternion.LookRotation(smoothedTarget - transform.position, Vector3.up);
        }
    }

    public sealed class HavenlineHud : MonoBehaviour
    {
        [SerializeField] private Text resourceText;
        [SerializeField] private Text objectiveText;
        [SerializeField] private Text furnaceText;
        [SerializeField] private Text helperText;
        [SerializeField] private Text waveText;
        [SerializeField] private Image warmthBar;
        [SerializeField] private HavenlinePlayerController player;
        [SerializeField] private HavenlineGameDirector director;
        public void Configure(Text resources, Text objective, Text furnace, Text helper, Text wave, Image heatBar, HavenlinePlayerController controlledPlayer, HavenlineGameDirector gameDirector)
        { resourceText=resources; objectiveText=objective; furnaceText=furnace; helperText=helper; waveText=wave; warmthBar=heatBar; player=controlledPlayer; director=gameDirector; }
        private void Update()
        {
            if (player == null || director == null || director.Furnace == null) return;
            var inv = player.Inventory;
            resourceText.text = $"WOOD {inv[ResourceKind.Wood]}   STONE {inv[ResourceKind.Stone]}   METAL {inv[ResourceKind.Metal]}   PACK {inv.Total}/{Reference.CarryCapacity}";
            objectiveText.text = director.Objective;
            var furnace = director.Furnace;
            furnaceText.text = $"FURNACE • LEVEL {furnace.Level}   WARMTH {furnace.WarmthRadius:0.0}m";
            helperText.text = director.Helper == null ? "SURVIVOR • UNKNOWN" : $"SURVIVOR • {director.Helper.State.ToString().ToUpperInvariant()}";
            waveText.text = furnace.Level < 2 ? "THREAT • DORMANT" : $"WAVE {director.Wave + 1} • {Mathf.CeilToInt(director.WaveClock)}s";
            if (warmthBar != null) warmthBar.fillAmount = Mathf.InverseLerp(4f, 11.5f, furnace.WarmthRadius);
        }
    }

    public sealed class HavenlineSnowfall : MonoBehaviour
    {
        [SerializeField] private ParticleSystem particles;
        private void Awake() { if (particles == null) particles = GetComponent<ParticleSystem>(); }
        private void Update()
        {
            if (particles == null || Camera.main == null) return;
            transform.position = Camera.main.transform.position + Camera.main.transform.forward * 6f + Vector3.up * 5f;
        }
    }

    public sealed class HavenlinePerformance : MonoBehaviour
    {
        private float sampleTime;
        private int frames;
        private void Awake()
        {
            QualitySettings.vSyncCount = 0;
            Application.targetFrameRate = Screen.currentResolution.refreshRateRatio.value >= 90 ? 120 : 60;
            Screen.sleepTimeout = SleepTimeout.NeverSleep;
        }
        private void Update()
        {
            sampleTime += Time.unscaledDeltaTime; frames++;
            if (sampleTime < 4f) return;
            var fps = frames / sampleTime;
            if (fps < 48f) QualitySettings.shadowDistance = Mathf.Max(24f, QualitySettings.shadowDistance - 4f);
            sampleTime = 0f; frames = 0;
        }
    }
}
