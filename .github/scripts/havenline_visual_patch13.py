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
                    ? new ParticleSystem.MinMaxCurve(0.12f, 0.34f)
                    : isSparks
                        ? new ParticleSystem.MinMaxCurve(0.035f, 0.09f)
                        : isSmoke
                            ? new ParticleSystem.MinMaxCurve(0.18f, 0.42f)
                            : new ParticleSystem.MinMaxCurve(size * 0.72f, size * 1.18f);
''',
    '''                main.startSize = isFire
                    ? new ParticleSystem.MinMaxCurve(0.18f)
                    : isSparks
                        ? new ParticleSystem.MinMaxCurve(0.055f)
                        : isSmoke
                            ? new ParticleSystem.MinMaxCurve(0.28f)
                            : new ParticleSystem.MinMaxCurve(size * 0.72f, size * 1.18f);
''',
    "fixed furnace particle base sizes",
)

revision = Path("HAVENLINE_UNITY/Assets/Havenline/Editor/HavenlineStudioRevision.cs")
source = revision.read_text(encoding="utf-8")
if '0.1.0-review.12' not in source:
    raise SystemExit("Missing review 12 revision marker")
source = source.replace('0.1.0-review.12', '0.1.0-review.13', 1)
old = "Validate soft transparent fire, sparks and chimney smoke, dedicated furnace finishes, darker structured shelters, clean regeneration and unchanged premium image gates."
new = "Use deterministic fixed furnace particle base sizes while preserving soft materials, motion variation, authored placement and unchanged visual limits."
if old not in source:
    raise SystemExit("Missing review 12 purpose")
revision.write_text(source.replace(old, new, 1), encoding="utf-8")
