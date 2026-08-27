#!/usr/bin/env python3
"""Render the Ivy C2 expressiveness audition.

This audition tests the complete Ivy C2 direction in a short, non-canon host
reaction montage: cross-chapter recognition, a simulated audience theory,
adult romantic tension, humor, natural laughter, sincere love/trust, a major
cliffhanger, direct fourth-wall interaction, and a cinematic close.

Three review versions are produced:
  1. Direct Ivy C2 — expressive Turbo synthesis directly conditioned on Ivy.
  2. Performance Transfer — expressive guide takes converted into Ivy's voice.
  3. Hybrid Comp — scene-by-scene comp using the strongest production path.

For every scene and path, three takes are generated. The best valid take is
selected using scene-specific timing, pause, pitch, and energy-variation gates.
All raw takes remain in the package for human review.

No reverb, echo, room simulation, delay, widening, doubling, chorus, EQ,
compression, ambience, post pitch shift, or post time stretch is used. The only
post operations are silence trim, resampling, scalar gain, timeline placement,
and 5 ms anti-click fades.
"""
from __future__ import annotations

import json
import math
import random
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Literal

import librosa
import numpy as np
import soundfile as sf
import torch
from scipy.signal import resample_poly

from chatterbox.tts_turbo import ChatterboxTurboTTS
from chatterbox.vc import ChatterboxVC
from kokoro import KPipeline

OUT = Path("out")
TAKES = OUT / "raw_takes"
GUIDES = OUT / "performance_guides"
TAKES.mkdir(parents=True, exist_ok=True)
GUIDES.mkdir(parents=True, exist_ok=True)

MASTER_SR = 48_000
KOKORO_SR = 24_000
VOICE = "af_heart"
TAKES_PER_PATH = 3


@dataclass(frozen=True)
class Scene:
    key: str
    title: str
    text: str
    emotional_arc: str
    target_seconds: float
    pause_after: float
    preferred_hybrid_path: Literal["direct", "transfer"]
    desired_pitch_variation: Literal["low", "medium", "high"]
    desired_pause_fraction: float


# This is explicitly a fictional/non-canon performance test. No real viewer
# comment or handle is attributed.
SCENES = [
    Scene(
        key="callback_theory",
        title="Crazy reveal, continuity callback, and simulated fan theory",
        text=(
            "Okay—wait. No, because... that mark was on the key in the very first chapter. "
            "One of you said it was responding to Sori, not Nia. I thought that was a reach. "
            "I may owe you an apology."
        ),
        emotional_arc="shock → recognition → processing → amused humility",
        target_seconds=15.5,
        pause_after=0.42,
        preferred_hybrid_path="transfer",
        desired_pitch_variation="high",
        desired_pause_fraction=0.12,
    ),
    Scene(
        key="romantic_tension_joke",
        title="Adult romantic tension, playful recognition, and organic laugh",
        text=(
            "And that look between those two? Mm-hmm. That was not a strategy meeting. "
            "Before anybody says I'm imagining it—rewind it. He forgot what he was saying. "
            "[chuckle] Sir."
        ),
        emotional_arc="knowing observation → teasing certainty → genuine amusement",
        target_seconds=13.0,
        pause_after=0.48,
        preferred_hybrid_path="transfer",
        desired_pitch_variation="medium",
        desired_pause_fraction=0.10,
    ),
    Scene(
        key="love_trust",
        title="Laughter recovery into love, trust, and sincere emotional drop",
        text=(
            "But then she put the key in his hand... and didn't ask for it back. "
            "That wasn't flirting. That was trust. And after everything she's lost... yeah. "
            "That got me."
        ),
        emotional_arc="residual warmth → recognition of intimacy → quiet sincerity",
        target_seconds=13.0,
        pause_after=0.52,
        preferred_hybrid_path="direct",
        desired_pitch_variation="low",
        desired_pause_fraction=0.15,
    ),
    Scene(
        key="cliffhanger_close",
        title="Cliffhanger disbelief, fourth-wall community reaction, and close",
        text=(
            "Then the door opens, we hear her mother's voice, and the chapter just... stops. "
            "No. You cannot leave us there. Tell me what you think that voice means—and if you "
            "called this back in Chapter One, save the receipt. I'm Ivy. I'll see you beyond the panel."
        ),
        emotional_arc="stunned silence → playful protest → audience invitation → cinematic warmth",
        target_seconds=19.0,
        pause_after=0.0,
        preferred_hybrid_path="transfer",
        desired_pitch_variation="high",
        desired_pause_fraction=0.13,
    ),
]


DIRECT_SETTINGS = {
    "temperature": 0.90,
    "min_p": 0.00,
    "top_p": 0.97,
    "top_k": 1000,
    "repetition_penalty": 1.13,
}
GUIDE_SETTINGS = {
    "temperature": 0.97,
    "min_p": 0.00,
    "top_p": 0.98,
    "top_k": 1000,
    "repetition_penalty": 1.11,
}


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def trim(y: np.ndarray, sr: int, threshold: float = 1e-5, pad_seconds: float = 0.025) -> np.ndarray:
    y = np.asarray(y, dtype=np.float32).reshape(-1)
    nz = np.flatnonzero(np.abs(y) > threshold)
    if not nz.size:
        return y
    pad = int(round(pad_seconds * sr))
    return y[max(0, int(nz[0]) - pad): min(len(y), int(nz[-1]) + pad + 1)]


def anti_click(y: np.ndarray, seconds: float = 0.005) -> np.ndarray:
    y = np.asarray(y, dtype=np.float32).copy()
    n = min(int(round(seconds * MASTER_SR)), len(y) // 2)
    if n:
        ramp = np.linspace(0.0, 1.0, n, endpoint=False, dtype=np.float32)
        y[:n] *= ramp
        y[-n:] *= ramp[::-1]
    return y


def scalar_peak(y: np.ndarray, ceiling_dbfs: float = -2.0) -> tuple[np.ndarray, float]:
    peak = float(np.max(np.abs(y)) + 1e-12)
    max_peak = 10 ** (ceiling_dbfs / 20)
    gain = min(1.0, max_peak / peak)
    return (y * gain).astype(np.float32), 20 * math.log10(max(gain, 1e-12))


def write_audio(path: Path, y: np.ndarray, subtype: str = "PCM_24") -> None:
    sf.write(path, np.asarray(y, dtype=np.float32), MASTER_SR, subtype=subtype)


def build_ivy_reference() -> Path:
    """Build a fresh, dry af_heart target reference with varied but restrained delivery."""
    pipeline = KPipeline(lang_code="a")
    phrases = [
        ("I'm Ivy, and welcome to Beyond the Panel.", 0.95),
        ("I get to become every voice inside the story.", 0.99),
        ("When something makes me laugh, you'll know.", 1.03),
        ("And when the story hurts... I won't rush past it.", 0.89),
    ]
    parts: list[np.ndarray] = []
    for index, (text, speed) in enumerate(phrases):
        chunks: list[np.ndarray] = []
        for _g, _p, audio in pipeline(text, voice=VOICE, speed=speed):
            arr = np.asarray(audio, dtype=np.float32).reshape(-1)
            if arr.size:
                chunks.append(arr)
        if not chunks:
            raise RuntimeError(f"No Kokoro reference audio for {text!r}")
        y = trim(np.concatenate(chunks), KOKORO_SR)
        y = resample_poly(y, MASTER_SR, KOKORO_SR).astype(np.float32)
        y = anti_click(y)
        parts.append(y)
        if index < len(phrases) - 1:
            parts.append(np.zeros(int(round(0.16 * MASTER_SR)), dtype=np.float32))
    ref = np.concatenate(parts)
    ref, _ = scalar_peak(ref, -3.0)
    path = OUT / "Ivy_C2_af_heart_target_reference_48k_PCM16.wav"
    write_audio(path, ref, "PCM_16")
    return path


def generate_turbo(
    model: ChatterboxTurboTTS,
    text: str,
    seed: int,
    settings: dict[str, Any],
    audio_prompt_path: Path | None,
) -> np.ndarray:
    set_seed(seed)
    kwargs: dict[str, Any] = {
        "temperature": float(settings["temperature"]),
        "min_p": float(settings["min_p"]),
        "top_p": float(settings["top_p"]),
        "top_k": int(settings["top_k"]),
        "repetition_penalty": float(settings["repetition_penalty"]),
        "norm_loudness": False,
    }
    if audio_prompt_path is not None:
        kwargs["audio_prompt_path"] = str(audio_prompt_path)
    with torch.inference_mode():
        wav = model.generate(text, **kwargs)
    y = wav.detach().cpu().float().numpy().reshape(-1)
    if not np.isfinite(y).all() or y.size < int(0.4 * model.sr):
        raise RuntimeError("Turbo produced invalid or implausibly short audio")
    y = trim(y, int(model.sr))
    y = resample_poly(y, MASTER_SR, int(model.sr)).astype(np.float32)
    y = anti_click(y)
    y, _ = scalar_peak(y, -2.8)
    return y


def convert_to_ivy(vc: ChatterboxVC, source_path: Path, target_path: Path) -> np.ndarray:
    with torch.inference_mode():
        wav = vc.generate(audio=str(source_path), target_voice_path=str(target_path))
    y = wav.detach().cpu().float().numpy().reshape(-1)
    if not np.isfinite(y).all() or y.size < int(0.4 * vc.sr):
        raise RuntimeError("Voice conversion produced invalid or implausibly short audio")
    y = trim(y, int(vc.sr))
    y = resample_poly(y, MASTER_SR, int(vc.sr)).astype(np.float32)
    y = anti_click(y)
    y, _ = scalar_peak(y, -2.8)
    return y


def features(y: np.ndarray) -> dict[str, float | int | None]:
    duration = len(y) / MASTER_SR
    rms = librosa.feature.rms(y=y, frame_length=2048, hop_length=512)[0]
    db = librosa.amplitude_to_db(np.maximum(rms, 1e-8), ref=np.max)
    pause_fraction = float(np.mean(db < -34.0))
    pause_regions = int(np.sum((db[1:] < -34.0) & (db[:-1] >= -34.0)))
    try:
        f0, _voiced, _prob = librosa.pyin(
            y,
            fmin=librosa.note_to_hz("C2"),
            fmax=librosa.note_to_hz("C7"),
            sr=MASTER_SR,
            frame_length=2048,
            hop_length=512,
        )
        vals = f0[np.isfinite(f0)] if f0 is not None else np.array([])
    except Exception:
        vals = np.array([])
    return {
        "duration": round(duration, 6),
        "pause_fraction": round(pause_fraction, 6),
        "pause_regions": pause_regions,
        "rms_std": round(float(np.std(rms)), 8),
        "f0_mean_hz": round(float(np.mean(vals)), 4) if vals.size else None,
        "f0_std_hz": round(float(np.std(vals)), 4) if vals.size else None,
        "f0_range_hz": round(float(np.ptp(vals)), 4) if vals.size else None,
        "peak_dbfs": round(20 * math.log10(max(float(np.max(np.abs(y))), 1e-12)), 3),
    }


def score_take(scene: Scene, feat: dict[str, float | int | None]) -> float:
    duration = float(feat["duration"])
    pause_fraction = float(feat["pause_fraction"])
    f0_std = float(feat["f0_std_hz"] or 0.0)
    rms_std = float(feat["rms_std"] or 0.0)
    pause_regions = int(feat["pause_regions"] or 0)

    duration_penalty = abs(duration - scene.target_seconds) / max(scene.target_seconds, 1.0)
    pause_penalty = abs(pause_fraction - scene.desired_pause_fraction)

    if scene.desired_pitch_variation == "high":
        pitch_score = min(f0_std / 52.0, 1.4)
    elif scene.desired_pitch_variation == "medium":
        pitch_score = 1.0 - min(abs(f0_std - 34.0) / 50.0, 1.0)
    else:
        pitch_score = 1.0 - min(abs(f0_std - 21.0) / 40.0, 1.0)

    # Controlled variation is good; extreme jumpiness or no variation is not.
    energy_score = min(rms_std / 0.035, 1.3)
    pause_region_score = min(pause_regions / 5.0, 1.0)

    return round(
        2.2 * pitch_score
        + 1.2 * energy_score
        + 0.55 * pause_region_score
        - 3.0 * duration_penalty
        - 1.8 * pause_penalty,
        6,
    )


def render_path_takes(
    scene: Scene,
    path_name: Literal["direct", "transfer"],
    turbo: ChatterboxTurboTTS,
    vc: ChatterboxVC,
    target_reference: Path,
    base_seed: int,
) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for take_index in range(1, TAKES_PER_PATH + 1):
        seed = base_seed + take_index * 137
        if path_name == "direct":
            y = generate_turbo(
                turbo,
                scene.text,
                seed,
                DIRECT_SETTINGS,
                target_reference,
            )
            guide_path = None
        else:
            guide = generate_turbo(
                turbo,
                scene.text,
                seed,
                GUIDE_SETTINGS,
                None,
            )
            guide_path = GUIDES / f"{scene.key}_guide_take_{take_index:02d}.wav"
            write_audio(guide_path, guide)
            y = convert_to_ivy(vc, guide_path, target_reference)

        take_path = TAKES / f"{scene.key}_{path_name}_take_{take_index:02d}.wav"
        write_audio(take_path, y)
        feat = features(y)
        record = {
            "take": take_index,
            "seed": seed,
            "path": path_name,
            "file": str(take_path),
            "guide_file": str(guide_path) if guide_path else None,
            "features": feat,
            "score": score_take(scene, feat),
            "post_effects": [],
        }
        records.append(record)
    return records


def assemble(selected: list[tuple[Scene, np.ndarray]]) -> np.ndarray:
    parts: list[np.ndarray] = []
    for index, (scene, y) in enumerate(selected):
        parts.append(y)
        if index < len(selected) - 1 and scene.pause_after > 0:
            parts.append(np.zeros(int(round(scene.pause_after * MASTER_SR)), dtype=np.float32))
    master = np.concatenate(parts) if parts else np.zeros(1, dtype=np.float32)
    master, _ = scalar_peak(master, -1.8)
    return master


def load_audio(path: str) -> np.ndarray:
    y, sr = sf.read(path, dtype="float32", always_2d=False)
    y = np.asarray(y, dtype=np.float32).reshape(-1)
    if sr != MASTER_SR:
        y = resample_poly(y, MASTER_SR, sr).astype(np.float32)
    return y


def main() -> None:
    torch.set_num_threads(max(1, min(8, torch.get_num_threads())))
    target_reference = build_ivy_reference()
    turbo = ChatterboxTurboTTS.from_pretrained(device="cpu")
    vc = ChatterboxVC.from_pretrained("cpu")

    manifest: dict[str, Any] = {
        "status": "ok",
        "audition": "Ivy C2",
        "scenario_status": "fictional non-canon performance audition; no real viewer comment attributed",
        "voice_identity_source": "fresh Kokoro af_heart dry target reference",
        "target_reference": str(target_reference),
        "sample_rate": MASTER_SR,
        "channels": 1,
        "takes_per_scene_per_path": TAKES_PER_PATH,
        "signal_path": {
            "direct": "Chatterbox Turbo conditioned directly on Ivy target reference",
            "transfer": "Chatterbox Turbo expressive guide, then ChatterboxVC to Ivy target reference",
            "hybrid": "scene-level comp using the preferred path and highest-scoring valid take",
        },
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
        "scenes": {},
        "masters": {},
    }

    direct_selection: list[tuple[Scene, np.ndarray]] = []
    transfer_selection: list[tuple[Scene, np.ndarray]] = []
    hybrid_selection: list[tuple[Scene, np.ndarray]] = []

    for scene_index, scene in enumerate(SCENES, start=1):
        direct_takes = render_path_takes(
            scene, "direct", turbo, vc, target_reference, 62000 + scene_index * 1000
        )
        transfer_takes = render_path_takes(
            scene, "transfer", turbo, vc, target_reference, 72000 + scene_index * 1000
        )
        best_direct = max(direct_takes, key=lambda x: x["score"])
        best_transfer = max(transfer_takes, key=lambda x: x["score"])

        direct_y = load_audio(best_direct["file"])
        transfer_y = load_audio(best_transfer["file"])
        direct_selection.append((scene, direct_y))
        transfer_selection.append((scene, transfer_y))

        preferred = best_transfer if scene.preferred_hybrid_path == "transfer" else best_direct
        hybrid_selection.append((scene, load_audio(preferred["file"])))

        manifest["scenes"][scene.key] = {
            "title": scene.title,
            "text": scene.text,
            "emotional_arc": scene.emotional_arc,
            "target_seconds": scene.target_seconds,
            "preferred_hybrid_path": scene.preferred_hybrid_path,
            "direct_takes": direct_takes,
            "transfer_takes": transfer_takes,
            "selected_direct": best_direct,
            "selected_transfer": best_transfer,
            "selected_hybrid": preferred,
        }

    masters = {
        "C2_1_Direct_Ivy": assemble(direct_selection),
        "C2_2_Performance_Transfer": assemble(transfer_selection),
        "C2_3_Hybrid_Comp": assemble(hybrid_selection),
    }

    comparison_parts: list[np.ndarray] = []
    markers: list[dict[str, Any]] = []
    cursor = 0.0
    for idx, (name, y) in enumerate(masters.items()):
        wav_path = OUT / f"Ivy_{name}_48k_PCM24.wav"
        write_audio(wav_path, y)
        feat = features(y)
        manifest["masters"][name] = {
            "wav": str(wav_path),
            "duration": round(len(y) / MASTER_SR, 6),
            "features": feat,
            "post_effects": [],
        }
        markers.append({
            "name": name,
            "start": round(cursor, 6),
            "end": round(cursor + len(y) / MASTER_SR, 6),
        })
        comparison_parts.append(y)
        cursor += len(y) / MASTER_SR
        if idx < len(masters) - 1:
            comparison_parts.append(np.zeros(MASTER_SR, dtype=np.float32))
            cursor += 1.0

    comparison = np.concatenate(comparison_parts)
    comparison, _ = scalar_peak(comparison, -1.8)
    comparison_path = OUT / "Ivy_C2_Audition_All_Three_48k_PCM24.wav"
    write_audio(comparison_path, comparison)
    manifest["comparison"] = {
        "wav": str(comparison_path),
        "duration": round(len(comparison) / MASTER_SR, 6),
        "markers": markers,
    }

    (OUT / "Ivy_C2_Audition_Manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    (OUT / "Ivy_C2_Audition_Script.md").write_text(
        "# Ivy C2 Audition\n\n"
        "**Status:** Fictional, non-canon performance audition. No real viewer comment or handle is attributed.\n\n"
        + "\n\n".join(
            f"## {scene.title}\n\n**Emotional arc:** {scene.emotional_arc}\n\n> {scene.text}"
            for scene in SCENES
        )
        + "\n",
        encoding="utf-8",
    )

    print(json.dumps({
        "status": "ok",
        "target_reference_seconds": round(sf.info(target_reference).duration, 3),
        "masters": {
            name: {
                "duration": manifest["masters"][name]["duration"],
                "features": manifest["masters"][name]["features"],
            }
            for name in masters
        },
    }, indent=2))


if __name__ == "__main__":
    main()
