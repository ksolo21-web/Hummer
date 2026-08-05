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
}
