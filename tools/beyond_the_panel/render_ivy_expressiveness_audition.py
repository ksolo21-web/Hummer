#!/usr/bin/env python3
"""Render three dry Ivy expressiveness auditions.

A fresh Kokoro af_heart clip supplies the approved Ivy vocal identity.
Chatterbox Turbo then performs the same emotional journey at three intensity
levels. The renderer works in short emotional beats so the delivery can change
from excited, to playful, to sincere, to suspicious, and finally warm.

No reverb, echo, room simulation, delay, widening, doubling, chorus, EQ,
compression, ambience, or post-synthesis pitch/time processing is used.
"""
from __future__ import annotations

import inspect
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

# Each version follows the same emotional arc. Text and punctuation vary only
# where needed to invite a different performance rather than a different idea.
VARIANTS: dict[str, dict[str, Any]] = {
    "A_natural_conversational": {
        "label": "Natural conversational",
        "temperature": 0.62,
        "top_p": 0.88,
        "top_k": 600,
        "repetition_penalty": 1.24,
        "seed": 52010,
        "beats": [
            ("Hi! Okay... wait. This is actually happening.", 0.34, "excited but grounded"),
            ("I'm Ivy, and welcome to Beyond the Panel.", 0.30, "warm personal welcome"),
            ("I'll be your host, your narrator, and somehow every character we meet along the way.", 0.30, "playful confidence; private aside on somehow"),
            ("So yes, I might be a fearless hero one minute, and somebody's very opinionated grandmother the next. [chuckle] We're going to have fun.", 0.42, "genuinely amused, not cartoonish"),
            ("But when a story hurts, I won't rush past it. I'll sit in that moment with you.", 0.44, "soft sincere emotional shift"),
            ("And when somebody like Orin walks in acting innocent? Mm-mm. I have questions.", 0.42, "dry playful suspicion"),
            ("So take a breath, turn up the sound, and let's step beyond the panel... together.", 0.00, "warm cinematic invitation"),
        ],
    },
    "B_animated_reactive": {
        "label": "More animated and reactive",
        "temperature": 0.80,
        "top_p": 0.95,
        "top_k": 1000,
        "repetition_penalty": 1.18,
        "seed": 52020,
        "beats": [
            ("Hi! Okay—wait. This is actually happening!", 0.30, "bright spontaneous launch excitement"),
            ("I'm Ivy... and welcome to the very first step Beyond the Panel.", 0.28, "warm, proud, intimate"),
            ("I'll be your host, your narrator, and—somehow—every character we meet along the way.", 0.26, "quick playful build; conspiratorial aside"),
            ("So yes... I could be a fearless hero one minute, and somebody's very opinionated grandmother the next. [laugh] Oh, we're going to have fun.", 0.40, "goofy delight with a small real laugh"),
            ("But when a story hurts? I won't rush past it. I'll stay in that moment with you.", 0.46, "clear drop into tender sincerity"),
            ("And when somebody like Orin walks in acting innocent? [chuckle] Mm-mm. No. I have questions.", 0.42, "amused suspicion, then firm side-eye"),
            ("So take a breath... turn up the sound... and let's step beyond the panel—together.", 0.00, "slow warm invitation with a lift at the end"),
        ],
    },
    "C_full_believable_performance": {
        "label": "Maximum believable expression",
        "temperature": 0.92,
        "top_p": 0.98,
        "top_k": 1000,
        "repetition_penalty": 1.14,
        "seed": 52030,
        "beats": [
            ("[gasp] Hi! Okay—wait... this is actually happening.", 0.34, "tiny breath of disbelief, then real excitement"),
            ("I'm Ivy. And welcome to the very first step Beyond the Panel.", 0.32, "settle into a warm direct connection"),
            ("I'll be your host, your narrator, and—somehow—every character we meet along the way.", 0.26, "charismatic build; playful private aside"),
            ("So yes... I might be a fearless hero one minute, and somebody's very opinionated grandmother the next. [chuckle] Yeah... we're going to have fun.", 0.44, "organic amusement, never a performed gag"),
            ("[sigh] But when a story hurts, I won't rush past it. I'll sit in that moment with you.", 0.50, "emotionally present, quiet and sincere"),
            ("And when somebody like Orin walks in acting innocent? [chuckle] Mm-mm. I have questions... a lot of questions.", 0.46, "reactive suspicion with restrained humor"),
            ("So take a breath. Turn up the sound. And let's step beyond the panel... together.", 0.00, "confident cinematic close; intimate final word"),
        ],
    },
}


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def trim(
    y: np.ndarray,
    threshold: float = 1e-5,
    pad_seconds: float = 0.025,
    sr: int = MASTER_SR,
) -> np.ndarray:
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
    """Create a clean, longer-than-five-seconds af_heart identity reference."""
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
        y = trim(np.concatenate(generated), sr=KOKORO_SR)
        y = resample_poly(y, MASTER_SR, KOKORO_SR).astype(np.float32)
        y = anti_click(y)
        chunks.append(y)
        if idx < len(phrases) - 1:
            chunks.append(np.zeros(int(round(0.18 * MASTER_SR)), dtype=np.float32))

    reference = np.concatenate(chunks)
    reference, _ = scalar_peak(reference, -3.0)
    path = OUT / "Ivy_af_heart_reference_48k_PCM16.wav"
    sf.write(path, reference, MASTER_SR, subtype="PCM_16")
    if sf.info(path).duration <= 5.0:
        raise RuntimeError("Ivy identity reference must be longer than five seconds")
    return path


def load_model() -> ChatterboxTurboTTS:
    """Load the PyPI 0.1.7 Turbo model once and reuse it for all versions."""
    torch.set_num_threads(max(1, min(8, torch.get_num_threads() or 4)))
    return ChatterboxTurboTTS.from_pretrained(device="cpu")


def supported_generate_kwargs(model: ChatterboxTurboTTS, requested: dict[str, Any]) -> dict[str, Any]:
    """Filter controls against the installed package signature for resilience."""
    supported = set(inspect.signature(model.generate).parameters)
    return {key: value for key, value in requested.items() if key in supported}


def generate_beat(
    model: ChatterboxTurboTTS,
    text: str,
    ref_path: Path,
    settings: dict[str, Any],
    seed: int,
) -> tuple[np.ndarray, dict[str, Any]]:
    requested_kwargs = {
        "audio_prompt_path": str(ref_path),
        "temperature": float(settings["temperature"]),
        "min_p": 0.0,
        "top_p": float(settings["top_p"]),
        "top_k": int(settings["top_k"]),
        "repetition_penalty": float(settings["repetition_penalty"]),
        "norm_loudness": False,
    }
    kwargs = supported_generate_kwargs(model, requested_kwargs)
    last_error: Exception | None = None

    for attempt in range(1, 4):
        attempt_seed = seed + (attempt - 1) * 101
        set_seed(attempt_seed)
        try:
            with torch.inference_mode():
                wav = model.generate(text, **kwargs)
            if torch.is_tensor(wav):
                y = wav.detach().cpu().float().numpy().reshape(-1)
            else:
                y = np.asarray(wav, dtype=np.float32).reshape(-1)
            if not np.isfinite(y).all() or y.size < int(0.25 * model.sr):
                raise RuntimeError("invalid or implausibly short model output")
            y = trim(y, sr=int(model.sr))
            y = resample_poly(y, MASTER_SR, int(model.sr)).astype(np.float32)
            y = anti_click(y)
            y, gain_db = scalar_peak(y, -2.8)
            return y, {
                "attempt": attempt,
                "seed": attempt_seed,
                "source_sample_rate": int(model.sr),
                "duration": round(len(y) / MASTER_SR, 6),
                "gain_db": round(gain_db, 3),
                "generate_kwargs": kwargs,
                "post_effects": [],
            }
        except Exception as exc:
            last_error = exc

    raise RuntimeError(f"Failed to render beat after three attempts: {last_error}")


def f0_metrics(path: Path) -> dict[str, float | None]:
    """Measure variation for QA only; never alter the rendered audio."""
    try:
        import librosa

        y, sr = librosa.load(path, sr=None, mono=True)
        f0, _voiced, _prob = librosa.pyin(
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
        return {
            "f0_mean_hz": None,
            "f0_std_hz": None,
            "f0_range_hz": None,
            "rms_std": None,
        }


def main() -> None:
    ref_path = build_ivy_reference()
    model = load_model()

    manifest: dict[str, Any] = {
        "status": "ok",
        "voice_identity_source": "fresh Kokoro af_heart reference",
        "performance_engine": "Chatterbox Turbo",
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
        "variants": {},
    }

    comparison_parts: list[np.ndarray] = []
    comparison_markers: list[dict[str, Any]] = []
    comparison_cursor = 0.0

    for variant_idx, (name, settings) in enumerate(VARIANTS.items(), start=1):
        assembled: list[np.ndarray] = []
        beat_manifest: list[dict[str, Any]] = []
        cursor = 0.0

        for beat_idx, (text, pause, direction) in enumerate(settings["beats"], start=1):
            y, meta = generate_beat(
                model,
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

        manifest["variants"][name] = {
            "label": settings["label"],
            "model": "turbo",
            "duration": round(len(master) / MASTER_SR, 6),
            "master_gain_db": round(master_gain_db, 3),
            "temperature": settings["temperature"],
            "top_p": settings["top_p"],
            "top_k": settings["top_k"],
            "repetition_penalty": settings["repetition_penalty"],
            "wav": str(wav_path),
            "metrics": f0_metrics(wav_path),
            "beats": beat_manifest,
        }

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
        "The same emotional journey is rendered at three believable intensity levels.\n\n"
        + "\n\n".join(
            f"## {settings['label']}\n\n"
            + "\n\n".join(beat[0] for beat in settings["beats"])
            for settings in VARIANTS.values()
        )
        + "\n",
        encoding="utf-8",
    )

    print(json.dumps({
        "status": "ok",
        "reference_seconds": round(sf.info(ref_path).duration, 3),
        "variants": {
            name: {
                "duration": manifest["variants"][name]["duration"],
                "metrics": manifest["variants"][name]["metrics"],
            }
            for name in VARIANTS
        },
    }, indent=2))


if __name__ == "__main__":
    main()
