using System;

namespace Havenline.Editor
{
    /// <summary>
    /// A local build-stopping exception used by the deterministic HAVENLINE pipeline.
    /// It intentionally keeps editor validation independent from Unity package namespace changes.
    /// </summary>
    public sealed class BuildFailedException : Exception
    {
        public BuildFailedException(string message) : base(message)
        {
        }
    }
}
