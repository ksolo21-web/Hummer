using System;
using System.Linq;
using UnityEngine;

namespace Havenline
{
    /// <summary>
    /// Very small deterministic sway for authored perimeter pines. This is intentionally subtle:
    /// it adds environmental life around the camp while keeping the playable silhouettes, resource
    /// nodes and automatic-action readability stable on a phone/foldable screen.
    /// </summary>
    public sealed class HavenlineWinterAmbientMotion : MonoBehaviour
    {
        [SerializeField] private float swayDegrees = 0.85f;
        [SerializeField] private float windSpeed = 0.46f;
        [SerializeField] private string targetPrefix = "QualitySceneryPine_";

        private Transform[] targets = Array.Empty<Transform>();
        private Quaternion[] authoredRotations = Array.Empty<Quaternion>();
        private float[] phases = Array.Empty<float>();

        public int TargetCount => targets.Length;

        private void Awake() => Capture();
        private void OnEnable() => Capture();

        private void LateUpdate()
        {
            if (targets.Length == 0)
                return;

            var time = Time.time * windSpeed;
            for (var index = 0; index < targets.Length; index++)
            {
                var target = targets[index];
                if (target == null)
                    continue;
                var slow = Mathf.Sin(time + phases[index]);
                var fine = Mathf.Sin(time * 1.63f + phases[index] * 0.37f);
                var pitch = slow * swayDegrees;
                var roll = fine * swayDegrees * 0.42f;
                target.localRotation = authoredRotations[index] * Quaternion.Euler(pitch, 0f, roll);
            }
        }

        private void OnDisable() => Restore();

        public void Recapture() => Capture();

        private void Capture()
        {
            Restore();
            targets = GetComponentsInChildren<Transform>(true)
                .Where(item => item != transform &&
                               item.name.StartsWith(targetPrefix, StringComparison.Ordinal))
                .OrderBy(item => item.name, StringComparer.Ordinal)
                .ToArray();
            authoredRotations = new Quaternion[targets.Length];
            phases = new float[targets.Length];
            for (var index = 0; index < targets.Length; index++)
            {
                authoredRotations[index] = targets[index].localRotation;
                phases[index] = index * 1.913f + 0.37f;
            }
        }

        private void Restore()
        {
            var count = Mathf.Min(targets.Length, authoredRotations.Length);
            for (var index = 0; index < count; index++)
            {
                if (targets[index] != null)
                    targets[index].localRotation = authoredRotations[index];
            }
        }
    }
}
