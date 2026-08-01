using UnityEngine;
using UnityEngine.InputSystem;

namespace Havenline
{
    [RequireComponent(typeof(CharacterController))]
    public sealed class HavenlinePlayerMotor : MonoBehaviour
    {
        [Header("Input")]
        [SerializeField] private InputActionReference moveAction;
        [SerializeField] private HavenlineVirtualJoystick virtualJoystick;
        [SerializeField] private Transform cameraTransform;

        [Header("Movement")]
        [SerializeField, Min(0.1f)] private float moveSpeed = 4.2f;
        [SerializeField, Min(0.1f)] private float acceleration = 18f;
        [SerializeField, Min(1f)] private float rotationSharpness = 14f;
        [SerializeField, Min(0f)] private float gravity = 24f;

        [Header("Safety")]
        [SerializeField] private Vector3 playableCenter = Vector3.zero;
        [SerializeField, Min(1f)] private float playableRadius = 16f;
        [SerializeField] private float fallRecoveryHeight = -3f;

        [Header("Animation")]
        [SerializeField] private Animator animator;
        [SerializeField] private string speedParameter = "Speed";

        private CharacterController _controller;
        private Vector3 _planarVelocity;
        private float _verticalVelocity;
        private Vector3 _lastSafePosition;
        private int _speedHash;

        public Vector3 PlanarVelocity => _planarVelocity;
        public float NormalizedSpeed => Mathf.Clamp01(_planarVelocity.magnitude / moveSpeed);

        private void Awake()
        {
            _controller = GetComponent<CharacterController>();
            _lastSafePosition = transform.position;
            _speedHash = Animator.StringToHash(speedParameter);

            if (cameraTransform == null && Camera.main != null)
            {
                cameraTransform = Camera.main.transform;
            }
        }

        private void OnEnable()
        {
            moveAction?.action.Enable();
        }

        private void OnDisable()
        {
            moveAction?.action.Disable();
        }

        private void Update()
        {
            RecoverIfNeeded();

            var input = ReadInput();
            var desiredDirection = ToCameraRelativeDirection(input);
            var desiredVelocity = desiredDirection * moveSpeed;
            _planarVelocity = Vector3.MoveTowards(
                _planarVelocity,
                desiredVelocity,
                acceleration * Time.deltaTime);

            if (_controller.isGrounded && _verticalVelocity < 0f)
            {
                _verticalVelocity = -2f;
            }
            else
            {
                _verticalVelocity -= gravity * Time.deltaTime;
            }

            var motion = _planarVelocity + Vector3.up * _verticalVelocity;
            _controller.Move(motion * Time.deltaTime);

            if (desiredDirection.sqrMagnitude > 0.001f)
            {
                var targetRotation = Quaternion.LookRotation(desiredDirection, Vector3.up);
                transform.rotation = Quaternion.Slerp(
                    transform.rotation,
                    targetRotation,
                    1f - Mathf.Exp(-rotationSharpness * Time.deltaTime));
            }

            if (_controller.isGrounded && IsInsidePlayableArea(transform.position))
            {
                _lastSafePosition = transform.position;
            }

            if (animator != null && !string.IsNullOrWhiteSpace(speedParameter))
            {
                animator.SetFloat(_speedHash, NormalizedSpeed, 0.08f, Time.deltaTime);
            }
        }

        public void ConfigureBoundary(Vector3 center, float radius)
        {
            playableCenter = center;
            playableRadius = Mathf.Max(1f, radius);
        }

        public void SetCamera(Transform value)
        {
            cameraTransform = value;
        }

        private Vector2 ReadInput()
        {
            var actionValue = moveAction != null ? moveAction.action.ReadValue<Vector2>() : Vector2.zero;
            var joystickValue = virtualJoystick != null ? virtualJoystick.Value : Vector2.zero;
            return joystickValue.sqrMagnitude > actionValue.sqrMagnitude ? joystickValue : actionValue;
        }

        private Vector3 ToCameraRelativeDirection(Vector2 input)
        {
            if (input.sqrMagnitude <= 0.0001f)
            {
                return Vector3.zero;
            }

            var forward = cameraTransform != null ? cameraTransform.forward : Vector3.forward;
            var right = cameraTransform != null ? cameraTransform.right : Vector3.right;
            forward.y = 0f;
            right.y = 0f;
            forward.Normalize();
            right.Normalize();

            return Vector3.ClampMagnitude(forward * input.y + right * input.x, 1f);
        }

        private bool IsInsidePlayableArea(Vector3 position)
        {
            var flatOffset = position - playableCenter;
            flatOffset.y = 0f;
            return flatOffset.sqrMagnitude <= playableRadius * playableRadius;
        }

        private void RecoverIfNeeded()
        {
            if (transform.position.y >= fallRecoveryHeight && IsInsidePlayableArea(transform.position))
            {
                return;
            }

            _controller.enabled = false;
            transform.position = _lastSafePosition + Vector3.up * 0.05f;
            _controller.enabled = true;
            _planarVelocity = Vector3.zero;
            _verticalVelocity = 0f;
        }
    }
}
