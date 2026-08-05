using System;
using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
    [CreateAssetMenu(menuName = "HAVENLINE/Typography Profile", fileName = "HAVENLINE_Typography")]
    public sealed class HavenlineTypographyProfile : ScriptableObject
    {
        [SerializeField] private Font runtimeFont;
        [SerializeField] private string preferredSystemFamily = "sans-serif";
        [SerializeField] private int headlineSize = 44;
        [SerializeField] private int objectiveSize = 31;
        [SerializeField] private int bodySize = 25;
        [SerializeField] private int compactSize = 20;
        [SerializeField] private Color primaryText = new(0.94f, 0.98f, 1f, 1f);
        [SerializeField] private Color secondaryText = new(0.72f, 0.82f, 0.9f, 1f);
        [SerializeField] private Color warningText = new(1f, 0.54f, 0.18f, 1f);

        public Font RuntimeFont => runtimeFont;
        public string PreferredSystemFamily => preferredSystemFamily;
        public int HeadlineSize => headlineSize;
        public int ObjectiveSize => objectiveSize;
        public int BodySize => bodySize;
        public int CompactSize => compactSize;
        public Color PrimaryText => primaryText;
        public Color SecondaryText => secondaryText;
        public Color WarningText => warningText;

        public void Configure(Font font, string family, int headline, int objective, int body, int compact)
        {
            runtimeFont = font;
            preferredSystemFamily = string.IsNullOrWhiteSpace(family) ? "sans-serif" : family;
            headlineSize = Mathf.Max(24, headline);
            objectiveSize = Mathf.Max(20, objective);
            bodySize = Mathf.Max(18, body);
            compactSize = Mathf.Max(16, compact);
        }
    }
}
