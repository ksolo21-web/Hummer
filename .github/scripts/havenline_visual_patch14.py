from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    source = path.read_text(encoding="utf-8")
    if old not in source:
        raise SystemExit(f"Missing {label} in {path}")
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


studio = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlineProceduralArtStudio.cs")
replace_once(
    studio,
    '''                main.startSize = isFire
                    ? new ParticleSystem.MinMaxCurve(0.18f)
                    : isSparks
                        ? new ParticleSystem.MinMaxCurve(0.055f)
                        : isSmoke
                            ? new ParticleSystem.MinMaxCurve(0.28f)
                            : new ParticleSystem.MinMaxCurve(size * 0.72f, size * 1.18f);
''',
    '''                main.startSize = isFire
                    ? CreateConstantCurve(0.18f)
                    : isSparks
                        ? CreateConstantCurve(0.055f)
                        : isSmoke
                            ? CreateConstantCurve(0.28f)
                            : new ParticleSystem.MinMaxCurve(size * 0.72f, size * 1.18f);
''',
    "explicit fixed furnace particle curves",
)
replace_once(
    studio,
    '''        private static void CreateParticlePrefab(
            string path, string name, Color start, Color end, int maxParticles,
''',
    '''        private static ParticleSystem.MinMaxCurve CreateConstantCurve(float value)
        {
            var curve = new ParticleSystem.MinMaxCurve
            {
                mode = ParticleSystemCurveMode.Constant,
                constantMin = value,
                constantMax = value
            };
            return curve;
        }

        private static void CreateParticlePrefab(
            string path, string name, Color start, Color end, int maxParticles,
''',
    "explicit constant particle curve helper",
)

gate = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlinePremiumSceneGate.cs")
replace_once(
    gate,
    '''            if (fireEffect != null && fireEffect.main.startSize.constantMax > 0.45f)
                failures.Add("Furnace fire particles are oversized and obscure the machine silhouette.");
''',
    '''            if (fireEffect != null && EffectiveCurveMaximum(fireEffect.main.startSize) > 0.45f)
                failures.Add("Furnace fire particles are oversized and obscure the machine silhouette.");
''',
    "effective particle-size validation",
)
helper = '''        private static float EffectiveCurveMaximum(ParticleSystem.MinMaxCurve curve)
        {
            static float MaximumKey(AnimationCurve animationCurve)
            {
                if (animationCurve == null || animationCurve.length == 0)
                    return 0f;
                return animationCurve.keys.Max(key => Mathf.Abs(key.value));
            }

            return curve.mode switch
            {
                ParticleSystemCurveMode.Constant => Mathf.Abs(curve.constant),
                ParticleSystemCurveMode.TwoConstants =>
                    Mathf.Max(Mathf.Abs(curve.constantMin), Mathf.Abs(curve.constantMax)),
                ParticleSystemCurveMode.Curve =>
                    Mathf.Abs(curve.curveMultiplier) * MaximumKey(curve.curve),
                ParticleSystemCurveMode.TwoCurves =>
                    Mathf.Abs(curve.curveMultiplier) *
                    Mathf.Max(MaximumKey(curve.curveMin), MaximumKey(curve.curveMax)),
                _ => float.PositiveInfinity
            };
        }

'''
replace_once(
    gate,
    '''        private static T FindSingle<T>(Scene scene, string label, ICollection<string> failures) where T : Component
''',
    helper + '''        private static T FindSingle<T>(Scene scene, string label, ICollection<string> failures) where T : Component
''',
    "particle curve maximum helper",
)

revision = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlineStudioRevision.cs")
source = revision.read_text(encoding="utf-8")
if '0.1.0-review.13' not in source:
    raise SystemExit("Missing review 13 revision marker")
source = source.replace('0.1.0-review.13', '0.1.0-review.14', 1)
old = "Validate deterministic fixed furnace particle sizes, soft transparent materials, authored door and chimney placement, complete shelters and unchanged premium gates."
new = "Serialize furnace particle sizes with explicit constant curve mode and validate their effective maximum across every Unity MinMaxCurve mode."
if old not in source:
    raise SystemExit("Missing review 13 purpose")
revision.write_text(source.replace(old, new, 1), encoding="utf-8")
