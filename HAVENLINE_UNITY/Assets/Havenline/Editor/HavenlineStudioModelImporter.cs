using System;
using UnityEditor;

namespace Havenline.Editor
{
    /// <summary>
    /// Enforces deterministic import settings for the in-repository HAVENLINE studio output.
    /// Actor OBJ hierarchies import as Generic rigs so root-transform animation controllers
    /// remain valid; environment and prop meshes remain optimized static assets.
    /// </summary>
    public sealed class HavenlineStudioModelImporter : AssetPostprocessor
    {
        private const string Root = "Assets/Havenline/Art/Production/";

        private void OnPreprocessModel()
        {
            if (!assetPath.StartsWith(Root, StringComparison.Ordinal) ||
                assetImporter is not ModelImporter importer)
                return;

            importer.globalScale = 1f;
            importer.useFileScale = true;
            importer.importBlendShapes = false;
            importer.importCameras = false;
            importer.importLights = false;
            importer.isReadable = false;
            importer.meshCompression = ModelImporterMeshCompression.Medium;
            importer.optimizeMeshPolygons = true;
            importer.optimizeMeshVertices = true;
            importer.importNormals = ModelImporterNormals.Calculate;
            importer.normalSmoothingAngle = 42f;

            if (IsAnimatedActor(assetPath))
            {
                importer.importAnimation = true;
                importer.animationType = ModelImporterAnimationType.Generic;
                importer.avatarSetup = ModelImporterAvatarSetup.CreateFromThisModel;
                importer.optimizeGameObjects = false;
            }
            else
            {
                importer.importAnimation = false;
                importer.animationType = ModelImporterAnimationType.None;
                importer.optimizeGameObjects = true;
            }
        }

        private static bool IsAnimatedActor(string path) =>
            path.EndsWith("/Characters/HAVENLINE_Player.obj", StringComparison.Ordinal) ||
            path.EndsWith("/Characters/HAVENLINE_Survivor.obj", StringComparison.Ordinal) ||
            path.EndsWith("/Enemies/HAVENLINE_Wolf.obj", StringComparison.Ordinal);
    }
}
