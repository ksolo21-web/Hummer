using UnityEngine;

namespace Havenline
{
    [RequireComponent(typeof(Camera))]
    public sealed class HavenlineIsometricCamera : MonoBehaviour
    {
        [SerializeField] private Transform target;
        [SerializeField] private Vector3 worldOffset = new(7.2f, 9.4f, 8.4f);
        [SerializeField] private Vector3 lookOffset = new(0f, 0.9f, -0.7f);
        [SerializeField, Min(0.1f)] private float positionSharpness = 8f;
        [SerializeField, Min(0.1f)] private float rotationSharpness = 10f;
        [SerializeField, Min(2f)] private float orthographicSize = 8.8f;

        private Camera _camera;

        public Transform Target => target;

        private void Awake()
        {
            _camera = GetComponent<Camera>();
            _camera.orthographic = true;
            _camera.orthographicSize = orthographicSize;
        }

        private void LateUpdate()
        {
            if (target == null)
            {
                return;
            }

            var desiredPosition = target.position + worldOffset;
            transform.position = Vector3.Lerp(
                transform.position,
                desiredPosition,
                1f - Mathf.Exp(-positionSharpness * Time.deltaTime));

            var lookDirection = target.position + lookOffset - transform.position;
            if (lookDirection.sqrMagnitude > 0.001f)
            {
                var desiredRotation = Quaternion.LookRotation(lookDirection.normalized, Vector3.up);
                transform.rotation = Quaternion.Slerp(
                    transform.rotation,
                    desiredRotation,
                    1f - Mathf.Exp(-rotationSharpness * Time.deltaTime));
            }
        }

        public void SetTarget(Transform value, bool snap)
        {
            target = value;
            if (!snap || target == null)
            {
                return;
            }

            transform.position = target.position + worldOffset;
            transform.LookAt(target.position + lookOffset, Vector3.up);
        }
    }
}
