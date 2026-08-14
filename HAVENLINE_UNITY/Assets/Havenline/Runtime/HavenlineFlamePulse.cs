using UnityEngine;

namespace Havenline
{
    /// <summary>
    /// Gives the authored furnace visual a restrained hand-animated pulse while preserving
    /// a stable mesh silhouette. The nearby furnace point light receives only a small correlated
    /// flutter so warmth feels alive without producing distracting mobile-screen flicker.
    /// </summary>
    public sealed class HavenlineFlamePulse : MonoBehaviour
    {
        [SerializeField] private float strength = 1f;
        [SerializeField] private float flutterSpeed = 4.8f;
        [SerializeField] private float swayDegrees = 2.4f;
        [SerializeField] private float lightFlutterAmount = 0.035f;

        private Vector3 authoredScale;
        private Quaternion authoredRotation;
        private bool captured;
        private float phase;
        private Light furnaceLight;
        private float authoredLightIntensity;
        private float lastAppliedLightIntensity;
        private bool capturedLight;

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
            ResolveFurnaceLight();
        }

        private void OnEnable()
        {
            Capture();
            ResolveFurnaceLight();
            Apply(Time.unscaledTime);
        }

        private void OnDisable()
        {
            if (furnaceLight != null && capturedLight)
                furnaceLight.intensity = authoredLightIntensity;
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

        private void ResolveFurnaceLight()
        {
            if (furnaceLight != null)
                return;

            var furnace = GetComponentInParent<HavenlineFurnace>();
            if (furnace == null)
                return;

            foreach (var candidate in furnace.GetComponentsInChildren<Light>(true))
            {
                if (candidate == null || candidate.type != LightType.Point)
                    continue;
                furnaceLight = candidate;
                authoredLightIntensity = candidate.intensity;
                lastAppliedLightIntensity = candidate.intensity;
                capturedLight = true;
                break;
            }
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

            ResolveFurnaceLight();
            if (furnaceLight == null)
                return;

            // HavenlineFurnace may legitimately change the authored intensity after an upgrade.
            // Detect that external write and treat it as the new baseline before adding flutter.
            if (!capturedLight || Mathf.Abs(furnaceLight.intensity - lastAppliedLightIntensity) > 0.025f)
            {
                authoredLightIntensity = furnaceLight.intensity;
                capturedLight = true;
            }

            var flutter = 1f + wave * lightFlutterAmount + secondary * lightFlutterAmount * 0.42f;
            lastAppliedLightIntensity = Mathf.Max(0f, authoredLightIntensity * flutter);
            furnaceLight.intensity = lastAppliedLightIntensity;
        }
    }
}
