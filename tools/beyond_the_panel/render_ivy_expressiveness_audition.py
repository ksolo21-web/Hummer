#!/usr/bin/env python3
"""Render a three-level Ivy expressiveness audition.

The goal is to keep Ivy's approved clean vocal identity while replacing the
flat, read-aloud delivery with emotionally changing performances.  A fresh
Kokoro af_heart clip supplies Ivy's tone reference.  Chatterbox Nano/Turbo
then performs the same host copy with different levels of reactivity and
paralinguistic behavior.

No reverb, echo, room simulation, delay, widening, doubling, chorus, EQ,
compression, ambience, or post-synthesis pitch/time effects are used.  The
only post steps are trimming, sample-rate conversion, scalar gain, timeline
placement, and 5 ms anti-click fades.
"""
from __future__ import annotations

import json
import math
import random
from pathlib import Path
from typing import Any

import numpy as np
import soundfile as sf
import torch
from scipy.signal import resample_poly

from chatterbox.tts_turbo import ChatterboxTurboTTS
from kokoro import KPipeline

OUT = Path("out")
LINES = OUT / "individual_beats"
OUT.mkdir(parents=True, exist_ok=True)
LINES.mkdir(parents=True, exist_ok=True)

MASTER_SR = 48_000
KOKORO_SR = 24_000
VOICE = "af_heart"

# A single audition script that intentionally moves through several emotional
# states: excited -> warm -> playful/goofy -> sincere -> suspicious -> inviting.
# Each model version receives equivalent content so the user can judge delivery,
# not merely different writing.
VARIANTS: dict[str, dict[str, Any]] = {
    "A_natural_conversational": {
        "label": "Natural conversational",
        "model": "nano",
        "temperature": 0.64,
        "top_p": 0.90,
        "top_k": 650,
        "min_p": 0.02,
        "repetition_penalty": 1.22,
        "seed": 52010,
        "beats": [
            ("Hi! Okay... wait. This is actually happening.", 0.34, "excited but grounded"),
            ("I'm Ivy, and welcome to Beyond the Panel.", 0.30, "warm personal welcome"),
            ("I'll be your host, your narrator, and somehow every character we meet along the way.", 0.30, "playful confidence; aside on somehow"),
            ("So yes, I might be a fearless hero one minute, and somebody's very opinionated grandmother the next. [chuckle] We're going to have fun.", 0.42, "goofy and genuinely amused"),
            ("But when a story hurts, I won't rush past it. I'll sit in that moment with you.", 0.42, "soft sincere emotional shift"),
            ("And when somebody like Orin walks in acting innocent? Mm-mm. I have questions.", 0.40, "suspicious, dry playful disbelief"),
            ("So take a breath, turn up the sound, and let's step beyond the panel... together.", 0.00, "warm cinematic invitation"),
        ],
    },
    "B_animated_reactive": {
        "label": "More animated and reactive",
        "model": "nano",
        "temperature": 0.82,
        "top_p": 0.95,
        "top_k": 1000,
        "min_p": 0.00,
        "repetition_penalty": 1.17,
        "seed": 52020,
        "beats": [
            ("Hi! Okay—wait. This is actually happening!", 0.30, "bright spontaneous launch excitement"),
            ("I'm Ivy... and welcome to the very first step Beyond the Panel.", 0.28, "warm, proud, intimate"),
            ("I'll be your host, your narrator, and—somehow—every character we meet along the way.", 0.26, "quick playful build; conspiratorial aside"),
            ("So yes... I could be a fearless hero one minute, and somebody's very opinionated grandmother the next. [laugh] Oh, we're going to have fun.", 0.38, "goofy delight with a real laugh"),
            ("But when a story hurts? I won't rush past it. I'll stay in that moment with you.", 0.44, "clear drop into tender sincerity"),
            ("And when somebody like Orin walks in acting innocent? [chuckle] Mm-mm. No. I have questions.", 0.40, "amused suspicion, then firm side-eye"),
            ("So take a breath... turn up the sound... and let's step beyond the panel—together.", 0.00, "slow warm invitation with lift at the end"),
        ],
    },
    "C_full_believable_performance": {
        "label": "Maximum believable expression",
        "model": "turbo",
        "temperature": 0.92,
        "top_p": 0.97,
        "top_k": 1000,
        "min_p": 0.00,
        "repetition_penalty": 1.14,
        "seed": 52030,
        "beats": [
            ("[gasp] Hi! Okay—wait... this is actually happening.", 0.34, "tiny breath of disbelief, then real excitement"),
            ("I'm Ivy. And welcome to the very first step Beyond the Panel.", 0.32, "settle into warm direct connection"),
            ("I'll be your host, your narrator, and—somehow—every character we meet along the way.", 0.26, "charismatic build; playful private aside"),
            ("So yes... I might be a fearless hero one minute, and somebody's very opinionated grandmother the next. [chuckle] Yeah... we're going to have fun.", 0.44, "organic amusement, not a performed joke"),
            ("[sigh] But when a story hurts, I won't rush past it. I'll sit in that moment with you.", 0.48, "emotionally present, quiet and sincere"),
            ("And when somebody like Orin walks in acting innocent? [chuckle] Mm-mm. I have questions... a lot of questions.", 0.44, "reactive suspicion with restrained humor"),
            ("So take a breath. Turn up the sound. And let's step beyond the panel... together.", 0.00, "confident cinematic close, intimate final word"),
        ],
    },
}


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def trim(y: np.ndarray, threshold: float = 1e-5, pad_seconds: float = 0.025, sr: int = MASTER_SR) -> np.ndarray:
    y = np.asarray(y, dtype=np.float32).reshape(-1)
    nz = np.flatnonzero(np.abs(y) > threshold)
    if not nz.size:
        return y
    pad = int(round(pad_seconds * sr))
    return y[max(0, int(nz[0]) - pad): min(len(y), int(nz[-1]) + pad + 1)]


def anti_click(y: np.ndarray, seconds: float = 0.005) -> np.ndarray:
    y = np.asarray(y, dtype=np.float32).copy()
    n = min(int(round(seconds * MASTER_SR)), len(y) // 2)
    if n > 0:
        ramp = np.linspace(0.0, 1.0, n, endpoint=False, dtype=np.float32)
        y[:n] *= ramp
        y[-n:] *= ramp[::-1]
    return y


def scalar_peak(y: np.ndarray, ceiling_dbfs: float = -2.0) -> tuple[np.ndarray, float]:
    peak = float(np.max(np.abs(y)) + 1e-12)
    max_peak = 10 ** (ceiling_dbfs / 20)
    gain = min(1.0, max_peak / peak)
    return (y * gain).astype(np.float32), 20 * math.log10(max(gain, 1e-12))


def build_ivy_reference() -> Path:
    """Create a clean 10–12 second af_heart reference with mild natural range."""
    pipeline = KPipeline(lang_code="a")
    phrases = [
        ("I'm Ivy, and welcome to Beyond the Panel.", 0.95),
        ("Every story has a voice, and I get to become all of them.", 1.02),
        ("When the story gets serious, I'll be right there with you.", 0.91),
    ]
    chunks: list[np.ndarray] = []
    for idx, (text, speed) in enumerate(phrases):
        generated: list[np.ndarray] = []
        for _g, _p, audio in pipeline(text, voice=VOICE, speed=speed):
            a = np.asarray(audio, dtype=np.float32).reshape(-1)
            if a.size:
                generated.append(a)
        if not generated:
            raise RuntimeError(f"Kokoro produced no reference audio for {text!r}")
        y = np.concatenate(generated)
        y = trim(y, sr=KOKORO_SR)
        y = resample_poly(y, MASTER_SR, KOKORO_SR).astype(np.float32)
        y = anti_click(y)
        chunks.append(y)
        if idx < len(phrases) - 1:
            chunks.append(np.zeros(int(round(0.18 * MASTER_SR)), dtype=np.float32))
    ref = np.concatenate(chunks)
    # Reference only: scalar gain; no dynamics or tonal processing.
    ref, _ = scalar_peak(ref, -3.0)
    path = OUT / "Ivy_af_heart_reference_48k_PCM16.wav"
    sf.write(path, ref, MASTER_SR, subtype="PCM_16")
    return path


def load_models() -> tuple[ChatterboxTurboTTS, ChatterboxTurboTTS | None, str | None]:
    torch.set_num_threads(max(1, min(8, (torch.get_num_threads() or 4))))
    nano = ChatterboxTurboTTS.from_pretrained(device="cpu", nano=True)
    turbo: ChatterboxTurboTTS | None = None
    error: str | None = None
    try:
        turbo = ChatterboxTurboTTS.from_pretrained(device="cpu")
    except Exception as exc:  # fallback is intentional so the audition still completes
        error = f"{type(exc).__name__}: {exc}"
    return nano, turbo, error


def generate_beat(
    model: ChatterboxTurboTTS,
    text: str,
    ref_path: Path,
    settings: dict[str, Any],
    seed: int,
) -> tuple[np.ndarray, dict[str, Any]]:
    set_seed(seed)
    kwargs = {
        "audio_prompt_path": str(ref_path),
        "temperature": float(settings["temperature"]),
        "min_p": float(settings["min_p"]),
        "top_p": float(settings["top_p"]),
        "top_k": int(settings["top_k"]),
        "repetition_penalty": float(settings["repetition_penalty"]),
        "norm_loudness": False,
    }
    last_error: Exception | None = None
    for attempt in range(1, 4):
        try:
            with torch.inference_mode():
                wav = model.generate(text, **kwargs)
            y = wav.detach().cpu().float().numpy().reshape(-1)
            if not np.isfinite(y).all() or y.size < int(0.25 * model.sr):
                raise RuntimeError("invalid or implausibly short model output")
            y = trim(y, sr=int(model.sr))
            y = resample_poly(y, MASTER_SR, int(model.sr)).astype(np.float32)
            y = anti_click(y)
            y, gain_db = scalar_peak(y, -2.8)
            return y, {
                "attempt": attempt,
                "seed": seed,
                "source_sample_rate": int(model.sr),
                "duration": round(len(y) / MASTER_SR, 6),
                "gain_db": round(gain_db, 3),
                "post_effects": [],
            }
        except Exception as exc:
            last_error = exc
            seed += 101
            set_seed(seed)
    assert last_error is not None
    raise RuntimeError(f"Failed to render beat after three attempts: {last_error}")


def f0_metrics(path: Path) -> dict[str, float | None]:
    """Measure variation for QA only; this does not alter audio."""
    try:
        import librosa

        y, sr = librosa.load(path, sr=None, mono=True)
        f0, voiced, _prob = librosa.pyin(
            y,
            fmin=librosa.note_to_hz("C2"),
            fmax=librosa.note_to_hz("C7"),
            sr=sr,
        )
        vals = f0[np.isfinite(f0)] if f0 is not None else np.array([])
        rms = librosa.feature.rms(y=y)[0]
        return {
            "f0_mean_hz": round(float(np.mean(vals)), 3) if vals.size else None,
            "f0_std_hz": round(float(np.std(vals)), 3) if vals.size else None,
            "f0_range_hz": round(float(np.ptp(vals)), 3) if vals.size else None,
            "rms_std": round(float(np.std(rms)), 8),
        }
    except Exception:
        return {"f0_mean_hz": None, "f0_std_hz": None, "f0_range_hz": None, "rms_std": None}


def main() -> None:
    ref_path = build_ivy_reference()
    nano, turbo, turbo_error = load_models()

    manifest: dict[str, Any] = {
        "status": "ok",
        "voice_identity_source": "fresh Kokoro af_heart reference",
        "reference_file": str(ref_path),
        "sample_rate": MASTER_SR,
        "channels": 1,
        "acoustic_processing": {
            "reverb": False,
            "echo": False,
            "delay": False,
            "room_simulation": False,
            "stereo_widening": False,
            "doubling": False,
            "chorus": False,
            "eq": False,
            "compression": False,
            "ambience": False,
            "post_pitch_shift": False,
            "post_time_stretch": False,
        },
        "allowed_post_processing": [
            "leading/trailing silence trim",
            "48 kHz polyphase resampling",
            "scalar peak gain",
            "timeline placement",
            "5 ms anti-click fades",
        ],
        "turbo_load_error": turbo_error,
        "variants": {},
    }

    comparison_parts: list[np.ndarray] = []
    comparison_markers: list[dict[str, Any]] = []
    comparison_cursor = 0.0

    for variant_idx, (name, settings) in enumerate(VARIANTS.items(), start=1):
        requested_model = settings["model"]
        selected_model = turbo if requested_model == "turbo" and turbo is not None else nano
        actual_model = "turbo" if selected_model is turbo and turbo is not None else "nano"

        assembled: list[np.ndarray] = []
        beat_manifest: list[dict[str, Any]] = []
        cursor = 0.0

        for beat_idx, (text, pause, direction) in enumerate(settings["beats"], start=1):
            y, meta = generate_beat(
                selected_model,
                text,
                ref_path,
                settings,
                int(settings["seed"]) + beat_idx,
            )
            beat_path = LINES / f"{name}_beat_{beat_idx:02d}.wav"
            sf.write(beat_path, y, MASTER_SR, subtype="PCM_24")
            start = cursor
            end = start + len(y) / MASTER_SR
            assembled.append(y)
            if pause > 0:
                assembled.append(np.zeros(int(round(pause * MASTER_SR)), dtype=np.float32))
            cursor = end + pause
            beat_manifest.append({
                "index": beat_idx,
                "text": text,
                "performance_direction": direction,
                "start": round(start, 6),
                "end": round(end, 6),
                "pause_after": pause,
                "line_file": str(beat_path),
                **meta,
            })

        master = np.concatenate(assembled) if assembled else np.zeros(1, dtype=np.float32)
        master, master_gain_db = scalar_peak(master, -1.8)
        wav_path = OUT / f"Ivy_Expressiveness_{name}_48k_PCM24.wav"
        sf.write(wav_path, master, MASTER_SR, subtype="PCM_24")

        metrics = f0_metrics(wav_path)
        manifest["variants"][name] = {
            "label": settings["label"],
            "requested_model": requested_model,
            "actual_model": actual_model,
            "duration": round(len(master) / MASTER_SR, 6),
            "master_gain_db": round(master_gain_db, 3),
            "temperature": settings["temperature"],
            "top_p": settings["top_p"],
            "top_k": settings["top_k"],
            "min_p": settings["min_p"],
            "repetition_penalty": settings["repetition_penalty"],
            "wav": str(wav_path),
            "metrics": metrics,
            "beats": beat_manifest,
        }

        # Comparison master: one second of silence between labeled variants.
        comparison_markers.append({
            "variant": name,
            "label": settings["label"],
            "start": round(comparison_cursor, 6),
            "end": round(comparison_cursor + len(master) / MASTER_SR, 6),
        })
        comparison_parts.append(master)
        comparison_cursor += len(master) / MASTER_SR
        if variant_idx < len(VARIANTS):
            comparison_parts.append(np.zeros(MASTER_SR, dtype=np.float32))
            comparison_cursor += 1.0

    comparison = np.concatenate(comparison_parts)
    comparison, comparison_gain_db = scalar_peak(comparison, -1.8)
    comparison_path = OUT / "Ivy_Expressiveness_Audition_All_Three_48k_PCM24.wav"
    sf.write(comparison_path, comparison, MASTER_SR, subtype="PCM_24")

    manifest["comparison"] = {
        "wav": str(comparison_path),
        "duration": round(len(comparison) / MASTER_SR, 6),
        "master_gain_db": round(comparison_gain_db, 3),
        "markers": comparison_markers,
    }

    (OUT / "Ivy_Expressiveness_Audition_Manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    (OUT / "Ivy_Expressiveness_Audition_Script.md").write_text(
        "# Ivy Expressiveness Audition\n\n"
        + "The same emotional journey is rendered at three performance levels.\n\n"
        + "## A — Natural conversational\n\n"
        + "\n\n".join(x[0] for x in VARIANTS["A_natural_conversational"]["beats"])
        + "\n\n## B — More animated and reactive\n\n"
        + "\n\n".join(x[0] for x in VARIANTS["B_animated_reactive"]["beats"])
        + "\n\n## C — Maximum believable expression\n\n"
        + "\n\n".join(x[0] for x in VARIANTS["C_full_believable_performance"]["beats"])
        + "\n",
        encoding="utf-8",
    )

    print(json.dumps({
        "status": "ok",
        "reference_seconds": round(sf.info(ref_path).duration, 3),
        "turbo_available": turbo is not None,
        "variants": {
            name: {
                "model": manifest["variants"][name]["actual_model"],
                "duration": manifest["variants"][name]["duration"],
                "metrics": manifest["variants"][name]["metrics"],
            }
            for name in VARIANTS
        },
    }, indent=2))


if __name__ == "__main__":
    main()
