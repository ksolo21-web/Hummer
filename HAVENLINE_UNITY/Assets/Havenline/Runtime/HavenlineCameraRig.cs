using UnityEngine;
using UnityEngine.UI;

namespace Havenline
{
    [RequireComponent(typeof(Camera))]
    public sealed class HavenlineCameraRig : MonoBehaviour
    {
        [SerializeField] private Transform target;
        [SerializeField] private float maximumLookAhead = Reference.CameraLookAhead;
        [SerializeField] private float maximumImpulseOffset = 0.095f;
        [SerializeField] private float impulseFrequency = 28f;

        private Camera cameraComponent;
        private Vector3 smoothedTarget;
        private float impulseStrength;
        private float impulseTime;
        private float impulseDuration;
        private int impulseSequence;

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

        private void OnEnable()
        {
            HavenlineFeedbackBus.Pulse -= HandleFeedbackPulse;
            HavenlineFeedbackBus.Pulse += HandleFeedbackPulse;
        }

        private void OnDisable()
        {
            HavenlineFeedbackBus.Pulse -= HandleFeedbackPulse;
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

            var basePosition = smoothedTarget + Reference.CameraOffset;
            var impulse = EvaluateImpulse();
            transform.position = basePosition + impulse;
            transform.rotation = Quaternion.LookRotation(smoothedTarget - transform.position, Vector3.up);
        }

        private Vector3 EvaluateImpulse()
        {
            if (impulseStrength <= 0.001f || impulseDuration <= 0f)
                return Vector3.zero;

            impulseTime += Time.unscaledDeltaTime;
            var normalized = Mathf.Clamp01(impulseTime / impulseDuration);
            var envelope = (1f - normalized) * (1f - normalized);
            var phase = impulseTime * impulseFrequency + impulseSequence * 1.731f;
            var right = Mathf.Sin(phase * 1.13f);
            var up = Mathf.Sin(phase * 1.71f + 0.8f);
            var offset = new Vector3(right, up * 0.55f, -right * 0.18f) *
                         maximumImpulseOffset * impulseStrength * envelope;

            if (normalized >= 1f)
            {
                impulseStrength = 0f;
                impulseTime = 0f;
                impulseDuration = 0f;
            }
            return offset;
        }

        private void HandleFeedbackPulse(HavenlineFeedbackPulse pulse)
        {
            if (target == null)
                return;

            var horizontal = pulse.WorldPosition - target.position;
            horizontal.y = 0f;
            if (horizontal.sqrMagnitude > 9.5f * 9.5f)
                return;

            var duration = pulse.Kind switch
            {
                HavenlineFeedbackKind.Death => 0.28f,
                HavenlineFeedbackKind.Damage => 0.20f,
                HavenlineFeedbackKind.Upgrade => 0.24f,
                _ when pulse.Action == AutomaticActionKind.Combat => 0.15f,
                _ when pulse.Action == AutomaticActionKind.Build || pulse.Action == AutomaticActionKind.Repair => 0.13f,
                _ => 0.10f
            };

            impulseStrength = Mathf.Clamp01(Mathf.Max(impulseStrength * 0.62f, pulse.Strength));
            impulseDuration = Mathf.Max(impulseDuration, duration);
            impulseTime = 0f;
            impulseSequence++;
        }

        private void Snap()
        {
            if (target == null)
                return;
            smoothedTarget = target.position + Vector3.up * Reference.CameraFocusHeight;
            transform.position = smoothedTarget + Reference.CameraOffset;
            transform.rotation = Quaternion.LookRotation(smoothedTarget - transform.position, Vector3.up);
            impulseStrength = 0f;
            impulseTime = 0f;
            impulseDuration = 0f;
        }
    }
}
