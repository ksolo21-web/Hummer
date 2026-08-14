using UnityEngine;

namespace Havenline.Editor
{
    /// <summary>
    /// Small Unity-6 compatibility shim for authored mesh generators that specify a desired
    /// smoothing angle. Unity 6 removed the float-angle Mesh.RecalculateNormals overload; the
    /// deterministic R31 meshes share their intended smooth vertices already, so the supported
    /// parameterless recalculation produces the required normals without changing topology.
    /// </summary>
    internal static class HavenlineMeshCompatibility
    {
        internal static void RecalculateNormals(this Mesh mesh, float unusedSmoothingAngle)
        {
            mesh.RecalculateNormals();
        }
    }
}
