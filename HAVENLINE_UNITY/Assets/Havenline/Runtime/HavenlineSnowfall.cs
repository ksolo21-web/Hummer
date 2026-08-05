using UnityEngine;
using UnityEngine.UI;

namespace Havenline
{
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
