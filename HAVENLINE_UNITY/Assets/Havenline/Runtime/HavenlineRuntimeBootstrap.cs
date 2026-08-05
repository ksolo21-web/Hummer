using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
    /// <summary>
    /// Applies runtime-only safeguards that must survive phone, tablet and foldable
    /// configuration changes without adding permanent action UI or changing gameplay.
    /// </summary>
    public static class HavenlineRuntimeBootstrap
    {
        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void InitializeShippingScene()
        {
            foreach (var camera in Object.FindObjectsByType<Camera>(FindObjectsInactive.Include, FindObjectsSortMode.None))
            {
                camera.allowDynamicResolution = true;
                if (camera.CompareTag("MainCamera") && camera.GetComponent<HavenlineAdaptiveCameraFraming>() == null)
                    camera.gameObject.AddComponent<HavenlineAdaptiveCameraFraming>();
            }

            foreach (var barricade in Object.FindObjectsByType<HavenlineBarricade>(FindObjectsInactive.Include, FindObjectsSortMode.None))
                barricade.Configure(HierarchyPath(barricade.transform), 160f);
        }

        private static string HierarchyPath(Transform transform)
        {
            var names = new Stack<string>();
            for (var current = transform; current != null; current = current.parent)
                names.Push(current.name);
            return string.Join("/", names);
        }
    }

}
