using UnityEngine;

namespace Havenline
{
    /// <summary>
    /// Gives the authored furnace visual a restrained hand-animated pulse while preserving
    /// a stable mesh silhouette.
    /// </summary>
    public sealed class HavenlineFlamePulse : MonoBehaviour
    {
        [SerializeField] private float strength = 1f;
        [SerializeField] private float flutterSpeed = 4.8f;
        [SerializeField] private float swayDegrees = 2.4f;

        private Vector3 authoredScale;
        private Quaternion authoredRotation;
        private bool captured;
        private float phase;

        public void Configure(float newStrength)
        {
            Capture();
            strength = Mathf.Max(0f, newStrength);
            Apply(Time.unscaledTime);
        }

        private void Awake()
        {
            Capture();
            phase = Mathf.Abs(transform.GetInstanceID() * 0.0137f) % (Mathf.PI * 2f);
        }

        private void OnEnable()
        {
            Capture();
            Apply(Time.unscaledTime);
        }

        private void Update() => Apply(Time.time);

        private void Capture()
        {
            if (captured)
                return;
            captured = true;
            authoredScale = transform.localScale;
            authoredRotation = transform.localRotation;
        }

        private void Apply(float time)
        {
            if (!captured)
                Capture();
            var wave = Mathf.Sin(time * flutterSpeed + phase);
            var secondary = Mathf.Sin(time * flutterSpeed * 1.73f + phase * 0.41f);
            var width = Mathf.Max(0.01f, strength * (1f + wave * 0.045f));
            var height = Mathf.Max(0.01f, strength * (1f + secondary * 0.075f));
            transform.localScale = Vector3.Scale(authoredScale, new Vector3(width, height, width));
            transform.localRotation = authoredRotation * Quaternion.Euler(0f, 0f, wave * swayDegrees);
        }
    }
}
