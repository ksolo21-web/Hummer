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
                    ? CreateConstantCurve(0.18f)
                    : isSparks
                        ? CreateConstantCurve(0.055f)
                        : isSmoke
                            ? CreateConstantCurve(0.28f)
                            : new ParticleSystem.MinMaxCurve(size * 0.72f, size * 1.18f);
''',
    '''                main.startSize = isFire || isSparks || isSmoke
                    ? CreateConstantCurve(1f)
                    : new ParticleSystem.MinMaxCurve(size * 0.72f, size * 1.18f);
                main.scalingMode = ParticleSystemScalingMode.Hierarchy;
''',
    "normalized furnace particle base size",
)

scene = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlinePremiumSceneAuthoring.cs")
replace_once(
    scene,
    '''            fire.transform.localPosition = new Vector3(0f, 0.64f, 1.10f);
            fire.transform.localRotation = Quaternion.Euler(-8f, 0f, 0f);
''',
    '''            fire.transform.localPosition = new Vector3(0f, 0.64f, 1.10f);
            fire.transform.localRotation = Quaternion.Euler(-8f, 0f, 0f);
            fire.transform.localScale = Vector3.one * 0.18f;
''',
    "authored fire world scale",
)
replace_once(
    scene,
    '''            sparks.transform.localPosition = new Vector3(0f, 0.78f, 1.05f);
''',
    '''            sparks.transform.localPosition = new Vector3(0f, 0.78f, 1.05f);
            sparks.transform.localScale = Vector3.one * 0.055f;
''',
    "authored spark world scale",
)
replace_once(
    scene,
    '''            smoke.transform.localPosition = new Vector3(0f, 2.82f, -0.14f);
''',
    '''            smoke.transform.localPosition = new Vector3(0f, 2.82f, -0.14f);
            smoke.transform.localScale = Vector3.one * 0.28f;
''',
    "authored smoke world scale",
)

gate = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlinePremiumSceneGate.cs")
replace_once(
    gate,
    '''            if (fireEffect != null && EffectiveCurveMaximum(fireEffect.main.startSize) > 0.45f)
                failures.Add("Furnace fire particles are oversized and obscure the machine silhouette.");
''',
    '''            if (fireEffect != null)
            {
                var effectiveFireSize = EffectiveCurveMaximum(fireEffect.main.startSize) *
                                        MaximumScale(fireEffect.transform.lossyScale);
                if (effectiveFireSize > 0.45f)
                {
                    failures.Add(
                        $"Furnace fire particles are oversized and obscure the machine silhouette " +
                        $"(effective world size {effectiveFireSize:0.###}, mode {fireEffect.main.startSize.mode}).");
                }
            }
''',
    "world-space furnace particle size validation",
)
replace_once(
    gate,
    '''        private static float EffectiveCurveMaximum(ParticleSystem.MinMaxCurve curve)
''',
    '''        private static float MaximumScale(Vector3 scale) =>
            Mathf.Max(Mathf.Abs(scale.x), Mathf.Abs(scale.y), Mathf.Abs(scale.z));

        private static float EffectiveCurveMaximum(ParticleSystem.MinMaxCurve curve)
''',
    "world scale helper",
)

revision = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlineStudioRevision.cs")
source = revision.read_text(encoding="utf-8")
if '0.1.0-review.14' not in source:
    raise SystemExit("Missing review 14 revision marker")
source = source.replace('0.1.0-review.14', '0.1.0-review.15', 1)
old = "Validate explicit constant-mode furnace particle curves, effective-size measurement, soft VFX materials, authored machine and shelter structures, and unchanged premium gates."
new = "Normalize furnace particle curves and author their real hierarchy scales, then validate effective world size exactly as rendered."
if old not in source:
    raise SystemExit("Missing review 14 purpose")
revision.write_text(source.replace(old, new, 1), encoding="utf-8")
