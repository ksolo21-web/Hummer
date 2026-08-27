#!/usr/bin/env python3
"""Render the Ivy C2 Human-Reader Pass audition.

This pass targets the specific failure Kaleb identified: one mostly unchanged
voice tone, pitch posture, and pace across emotionally different thoughts.

The script applies a human-reader production map instead of one global speaking
style:

- the copy is divided into thought units, not paragraphs;
- each unit has its own vocal posture, pitch range, pace target, articulation,
  breath state, processing pause, and emotional residue;
- separate Ivy-conditioned references are created for interruption, processing,
  playfulness, adult romantic recognition, serious emotion, shock, direct
  audience address, and the cinematic close;
- connective thoughts can move quickly while recognition, intimacy, grief,
  cliffhangers, and direct questions receive more time;
- a laugh and gasp are generated only where the thought causes them;
- the director comp preserves the same Ivy identity while deliberately changing
  tone, pitch behavior, rhythm, and pace from unit to unit.

No reverb, echo, room simulation, delay, widening, doubling, chorus, EQ,
compression, ambience, post pitch shift, or post time stretch is used. Allowed
post operations are silence trim, 48 kHz resampling, scalar gain, thought-unit
placement, and 5 ms anti-click fades.

The scenario is fictional and non-canon. It does not attribute a comment to a
real viewer.
"""
from __future__ import annotations

import gc
import json
import math
import random
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Literal

import librosa
import numpy as np
import soundfile as sf
import torch
from scipy.signal import resample_poly

from chatterbox.tts import ChatterboxTTS
from chatterbox.tts_turbo import ChatterboxTurboTTS
from kokoro import KPipeline

OUT = Path("out")
REFS = OUT / "tone_references"
TAKES = OUT / "thought_unit_takes"
REFS.mkdir(parents=True, exist_ok=True)
TAKES.mkdir(parents=True, exist_ok=True)

MASTER_SR = 48_000
KOKORO_SR = 24_000
VOICE = "af_heart"
BASE_SEED = 91120


@dataclass(frozen=True)
class Style:
    exaggeration: float
    cfg_weight: float
    temperature: float
    repetition_penalty: float = 1.18
    min_p: float = 0.03
    top_p: float = 0.96


@dataclass(frozen=True)
class Mode:
    key: str
    anchor_text: str
    posture: str
    natural: Style
    contrast: Style
    pitch_target: Literal["low", "medium", "high"]


@dataclass(frozen=True)
class Unit:
    key: str
    text: str
    mode: str
    intent: str
    target_seconds: float
    pause_after: float
    director_style: Literal["natural", "contrast"]
    special_turbo: Literal["none", "laugh", "gasp"] = "none"


MODES: dict[str, Mode] = {
    "interrupt": Mode(
        key="interrupt",
        anchor_text="Wait—hold on. I just noticed something.",
        posture="Bright, forward, interrupted thought; quick onset and wide pitch movement.",
        natural=Style(exaggeration=0.66, cfg_weight=0.32, temperature=0.82),
        contrast=Style(exaggeration=0.82, cfg_weight=0.24, temperature=0.90),
        pitch_target="high",
    ),
    "process": Mode(
        key="process",
        anchor_text="Give me a second. I need to think about that.",
        posture="Settled register, slower internal tempo, thought forming in real time.",
        natural=Style(exaggeration=0.46, cfg_weight=0.27, temperature=0.74),
        contrast=Style(exaggeration=0.58, cfg_weight=0.20, temperature=0.80),
        pitch_target="medium",
    ),
    "playful": Mode(
        key="playful",
        anchor_text="No, because that was actually funny.",
        posture="Smiling, bright, rhythmically unpredictable, affectionate rather than mocking.",
        natural=Style(exaggeration=0.66, cfg_weight=0.31, temperature=0.84),
        contrast=Style(exaggeration=0.84, cfg_weight=0.24, temperature=0.94),
        pitch_target="high",
    ),
    "romantic": Mode(
        key="romantic",
        anchor_text="They both felt that moment, even if they will not admit it.",
        posture="Warmer and slightly lower; knowing, grown, unhurried, never childish.",
        natural=Style(exaggeration=0.48, cfg_weight=0.24, temperature=0.74),
        contrast=Style(exaggeration=0.60, cfg_weight=0.18, temperature=0.80),
        pitch_target="low",
    ),
    "serious": Mode(
        key="serious",
        anchor_text="She did not need another answer. She needed her mother.",
        posture="Lower energy, narrower melody, softened articulation, emotional restraint.",
        natural=Style(exaggeration=0.30, cfg_weight=0.21, temperature=0.68),
        contrast=Style(exaggeration=0.38, cfg_weight=0.15, temperature=0.72),
        pitch_target="low",
    ),
    "shock": Mode(
        key="shock",
        anchor_text="No. That changes everything.",
        posture="Breath interruption, short first response, uneven recovery, then meaning lands.",
        natural=Style(exaggeration=0.66, cfg_weight=0.27, temperature=0.82),
        contrast=Style(exaggeration=0.88, cfg_weight=0.20, temperature=0.94),
        pitch_target="high",
    ),
    "direct": Mode(
        key="direct",
        anchor_text="Tell me what you think.",
        posture="Close, calm, one-to-one, genuinely waiting for a viewer's answer.",
        natural=Style(exaggeration=0.38, cfg_weight=0.25, temperature=0.70),
        contrast=Style(exaggeration=0.48, cfg_weight=0.20, temperature=0.76),
        pitch_target="medium",
    ),
    "close": Mode(
        key="close",
        anchor_text="Take a breath. I will see you beyond the panel.",
        posture="Warm, grounded, personal cinematic close without trailer-announcer weight.",
        natural=Style(exaggeration=0.44, cfg_weight=0.27, temperature=0.72),
        contrast=Style(exaggeration=0.56, cfg_weight=0.21, temperature=0.78),
        pitch_target="medium",
    ),
}


UNITS: list[Unit] = [
    Unit(
        key="01_interruption",
        text="Okay—wait.",
        mode="interrupt",
        intent="The realization interrupts her before she has organized the thought.",
        target_seconds=1.55,
        pause_after=0.62,
        director_style="contrast",
    ),
    Unit(
        key="02_search_memory",
        text="No, because... that mark was on the key.",
        mode="process",
        intent="She searches her memory and arrives at recognition while speaking.",
        target_seconds=4.10,
        pause_after=0.46,
        director_style="contrast",
    ),
    Unit(
        key="03_quiet_callback",
        text="In Chapter One.",
        mode="serious",
        intent="The full time-distance lands quietly rather than as a dramatic announcement.",
        target_seconds=1.85,
        pause_after=0.72,
        director_style="natural",
    ),
    Unit(
        key="04_previous_belief",
        text="I thought it was background detail.",
        mode="process",
        intent="Reflective admission; she is reconsidering her earlier interpretation.",
        target_seconds=3.05,
        pause_after=0.40,
        director_style="natural",
    ),
    Unit(
        key="05_realization",
        text="It wasn't.",
        mode="serious",
        intent="Low, simple, and restrained. No ornamental emotional melody.",
        target_seconds=1.35,
        pause_after=0.82,
        director_style="contrast",
    ),
    Unit(
        key="06_fan_theory_admission",
        text="And whoever said the key was responding to Sori, not Nia... I owe you an apology.",
        mode="direct",
        intent="She turns toward the audience and admits they may have outsmarted her.",
        target_seconds=7.00,
        pause_after=0.58,
        director_style="natural",
    ),
    Unit(
        key="07_bad_decision_setup",
        text="Also, Sori—sweetheart—if a hallway whispers your name, maybe don't open the door.",
        mode="playful",
        intent="Affectionate frustration; quick connective words, then a deliberate warning.",
        target_seconds=6.75,
        pause_after=0.38,
        director_style="contrast",
    ),
    Unit(
        key="08_laugh_recovery",
        text="I'm sorry... I support you emotionally. I do not support this decision.",
        mode="playful",
        intent="A real laugh interrupts her; the next words retain the laugh and recovery breath.",
        target_seconds=6.10,
        pause_after=0.72,
        director_style="contrast",
        special_turbo="laugh",
    ),
    Unit(
        key="09_romantic_notice",
        text="And that look between Nia and Sori? Mm-hmm.",
        mode="romantic",
        intent="Grown, knowing recognition; warmer, lower, and unhurried.",
        target_seconds=4.35,
        pause_after=0.42,
        director_style="natural",
    ),
    Unit(
        key="10_romantic_read",
        text="That was not relief. That pause was flirting.",
        mode="romantic",
        intent="She lets the evidence sit before naming it; no childish giggle.",
        target_seconds=4.85,
        pause_after=0.76,
        director_style="contrast",
    ),
    Unit(
        key="11_trust_setup",
        text="But then she put the key in his hand... and didn't ask for it back.",
        mode="serious",
        intent="The smile fades. The pace opens as Ivy realizes the emotional meaning.",
        target_seconds=7.10,
        pause_after=0.82,
        director_style="natural",
    ),
    Unit(
        key="12_trust_meaning",
        text="That wasn't flirting. That was trust.",
        mode="serious",
        intent="Plain, low, emotionally precise; the second sentence receives more space.",
        target_seconds=4.25,
        pause_after=0.68,
        director_style="contrast",
    ),
    Unit(
        key="13_personal_impact",
        text="And after everything she's lost... yeah. That got me.",
        mode="serious",
        intent="Softened diction and a small processing pause before the honest admission.",
        target_seconds=5.35,
        pause_after=0.92,
        director_style="natural",
    ),
    Unit(
        key="14_cliffhanger_reveal",
        text="Then the door opened... and we heard her mother's voice.",
        mode="shock",
        intent="Tension slows the sentence; 'mother's voice' changes the emotional posture.",
        target_seconds=5.85,
        pause_after=1.45,
        director_style="natural",
    ),
    Unit(
        key="15_stunned_no",
        text="No.",
        mode="shock",
        intent="A breath-stopped, isolated response. Silence must follow it.",
        target_seconds=1.10,
        pause_after=0.82,
        director_style="contrast",
        special_turbo="gasp",
    ),
    Unit(
        key="16_cliffhanger_protest",
        text="You cannot leave us there.",
        mode="shock",
        intent="Uneven recovery becomes personal, playful frustration—not shouting.",
        target_seconds=3.10,
        pause_after=0.72,
        director_style="contrast",
    ),
    Unit(
        key="17_viewer_question",
        text="Tell me what you think that voice means.",
        mode="direct",
        intent="Drops out of performance mode and asks one viewer a genuine question.",
        target_seconds=3.80,
        pause_after=0.66,
        director_style="natural",
    ),
    Unit(
        key="18_theory",
        text="My theory? The key isn't opening places. It's opening what the Registry tried to erase.",
        mode="process",
        intent="Thoughtful and clearly speculative; the two conclusions arrive separately.",
        target_seconds=7.90,
        pause_after=0.70,
        director_style="natural",
    ),
    Unit(
        key="19_close",
        text="I'm Ivy. Take a breath... and I'll see you beyond the panel.",
        mode="close",
        intent="Warm, close, and memorable; cinematic without sounding like a trailer.",
        target_seconds=6.10,
        pause_after=0.0,
        director_style="natural",
    ),
]


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def trim(y: np.ndarray, sr: int, threshold: float = 1e-5, pad_seconds: float = 0.035) -> np.ndarray:
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
    allowed = 10 ** (ceiling_dbfs / 20)
    gain = min(1.0, allowed / peak)
    return (y * gain).astype(np.float32), 20 * math.log10(max(gain, 1e-12))


def write_audio(path: Path, y: np.ndarray, subtype: str = "PCM_24") -> None:
    sf.write(path, np.asarray(y, dtype=np.float32), MASTER_SR, subtype=subtype)


def word_count(text: str) -> int:
    return len(re.findall(r"\b[\w']+\b", text.replace("Mm-hmm", "Mm hmm")))


def audio_features(y: np.ndarray) -> dict[str, float | int | None]:
    duration = len(y) / MASTER_SR
    rms = librosa.feature.rms(y=y, frame_length=2048, hop_length=512)[0]
    db = librosa.amplitude_to_db(np.maximum(rms, 1e-8), ref=np.max)
    pause_fraction = float(np.mean(db < -35.0))
    pause_regions = int(np.sum((db[1:] < -35.0) & (db[:-1] >= -35.0)))
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
        "wpm": round(word_count("") * 60 / duration, 3) if duration else 0.0,
        "pause_fraction": round(pause_fraction, 6),
        "pause_regions": pause_regions,
        "rms_std": round(float(np.std(rms)), 8),
        "f0_mean_hz": round(float(np.mean(vals)), 4) if vals.size else None,
        "f0_std_hz": round(float(np.std(vals)), 4) if vals.size else None,
        "f0_range_hz": round(float(np.ptp(vals)), 4) if vals.size else None,
        "peak_dbfs": round(20 * math.log10(max(float(np.max(np.abs(y))), 1e-12)), 3),
    }


def features_for_text(y: np.ndarray, text: str) -> dict[str, float | int | None]:
    feat = audio_features(y)
    duration = float(feat["duration"])
    feat["word_count"] = word_count(text)
    feat["wpm"] = round(word_count(text) * 60 / duration, 3) if duration else 0.0
    return feat


def build_base_reference() -> Path:
    pipeline = KPipeline(lang_code="a")
    phrases = [
        ("I'm Ivy, and welcome to Beyond the Panel.", 0.95),
        ("I can laugh with you, worry with you, and become every voice in the story.", 0.98),
        ("When the story gets quiet, I will not rush past it.", 0.88),
        ("Tell me what you think. I really want to know.", 0.92),
    ]
    parts: list[np.ndarray] = []
    for index, (text, speed) in enumerate(phrases):
        chunks: list[np.ndarray] = []
        for _g, _p, audio in pipeline(text, voice=VOICE, speed=speed):
            arr = np.asarray(audio, dtype=np.float32).reshape(-1)
            if arr.size:
                chunks.append(arr)
        if not chunks:
            raise RuntimeError(f"No base-reference audio for {text!r}")
        y = trim(np.concatenate(chunks), KOKORO_SR)
        y = resample_poly(y, MASTER_SR, KOKORO_SR).astype(np.float32)
        y = anti_click(y)
        parts.append(y)
        if index < len(phrases) - 1:
            parts.append(np.zeros(int(round(0.20 * MASTER_SR)), dtype=np.float32))
    master = np.concatenate(parts)
    master, _ = scalar_peak(master, -3.0)
    path = OUT / "Ivy_C2_HR_Base_Reference_48k_PCM16.wav"
    write_audio(path, master, "PCM_16")
    return path


def generate_standard(
    model: ChatterboxTTS,
    text: str,
    prompt: Path,
    style: Style,
    seed: int,
) -> np.ndarray:
    set_seed(seed)
    with torch.inference_mode():
        wav = model.generate(
            text,
            audio_prompt_path=str(prompt),
            exaggeration=style.exaggeration,
            cfg_weight=style.cfg_weight,
            temperature=style.temperature,
            repetition_penalty=style.repetition_penalty,
            min_p=style.min_p,
            top_p=style.top_p,
        )
    y = wav.detach().cpu().float().numpy().reshape(-1)
    if not np.isfinite(y).all() or y.size < int(0.18 * model.sr):
        raise RuntimeError("Standard Chatterbox produced invalid audio")
    y = trim(y, int(model.sr))
    y = resample_poly(y, MASTER_SR, int(model.sr)).astype(np.float32)
    y = anti_click(y)
    y, _ = scalar_peak(y, -2.8)
    return y


def generate_turbo(
    model: ChatterboxTurboTTS,
    text: str,
    prompt: Path,
    seed: int,
    temperature: float,
) -> np.ndarray:
    set_seed(seed)
    with torch.inference_mode():
        wav = model.generate(
            text,
            audio_prompt_path=str(prompt),
            temperature=temperature,
            min_p=0.0,
            top_p=0.98,
            top_k=1000,
            repetition_penalty=1.13,
            norm_loudness=False,
        )
    y = wav.detach().cpu().float().numpy().reshape(-1)
    if not np.isfinite(y).all() or y.size < int(0.18 * model.sr):
        raise RuntimeError("Turbo Chatterbox produced invalid audio")
    y = trim(y, int(model.sr))
    y = resample_poly(y, MASTER_SR, int(model.sr)).astype(np.float32)
    y = anti_click(y)
    y, _ = scalar_peak(y, -2.8)
    return y


def candidate_score(unit: Unit, y: np.ndarray, mode: Mode) -> float:
    feat = features_for_text(y, unit.text)
    duration = float(feat["duration"])
    f0_std = float(feat["f0_std_hz"] or 0.0)
    duration_error = abs(duration - unit.target_seconds) / max(unit.target_seconds, 0.5)
    if mode.pitch_target == "high":
        pitch_fit = min(f0_std / 48.0, 1.25)
    elif mode.pitch_target == "medium":
        pitch_fit = 1.0 - min(abs(f0_std - 31.0) / 45.0, 1.0)
    else:
        pitch_fit = 1.0 - min(abs(f0_std - 20.0) / 36.0, 1.0)
    energy_fit = min(float(feat["rms_std"] or 0.0) / 0.032, 1.20)
    return round(2.0 * pitch_fit + 0.8 * energy_fit - 3.2 * duration_error, 6)


def best_standard_take(
    model: ChatterboxTTS,
    unit: Unit,
    prompt: Path,
    style_name: Literal["natural", "contrast"],
    base_seed: int,
) -> tuple[np.ndarray, list[dict[str, Any]], dict[str, Any]]:
    mode = MODES[unit.mode]
    style = mode.natural if style_name == "natural" else mode.contrast
    records: list[dict[str, Any]] = []
    candidates: list[np.ndarray] = []
    for attempt in range(1, 3):
        seed = base_seed + attempt * 173
        y = generate_standard(model, unit.text, prompt, style, seed)
        feat = features_for_text(y, unit.text)
        score = candidate_score(unit, y, mode)
        path = TAKES / f"{unit.key}_{style_name}_take_{attempt:02d}.wav"
        write_audio(path, y)
        candidates.append(y)
        records.append({
            "attempt": attempt,
            "seed": seed,
            "file": str(path),
            "style": style_name,
            "features": feat,
            "score": score,
            "post_effects": [],
        })
    best_index = max(range(len(records)), key=lambda i: records[i]["score"])
    return candidates[best_index], records, records[best_index]


def assemble(units_audio: list[tuple[Unit, np.ndarray]]) -> tuple[np.ndarray, list[dict[str, Any]]]:
    parts: list[np.ndarray] = []
    timeline: list[dict[str, Any]] = []
    cursor = 0.0
    for index, (unit, y) in enumerate(units_audio):
        start = cursor
        end = start + len(y) / MASTER_SR
        timeline.append({
            "unit": unit.key,
            "text": unit.text,
            "mode": unit.mode,
            "intent": unit.intent,
            "start": round(start, 6),
            "end": round(end, 6),
            "duration": round(end - start, 6),
            "wpm": round(word_count(unit.text) * 60 / max(end - start, 1e-9), 3),
            "pause_after": unit.pause_after,
        })
        parts.append(y)
        cursor = end
        if index < len(units_audio) - 1 and unit.pause_after > 0:
            parts.append(np.zeros(int(round(unit.pause_after * MASTER_SR)), dtype=np.float32))
            cursor += unit.pause_after
    master = np.concatenate(parts) if parts else np.zeros(1, dtype=np.float32)
    master, _ = scalar_peak(master, -1.8)
    return master, timeline


def write_srt(path: Path, timeline: list[dict[str, Any]]) -> None:
    def stamp(seconds: float) -> str:
        ms = int(round(seconds * 1000))
        h, ms = divmod(ms, 3_600_000)
        m, ms = divmod(ms, 60_000)
        s, ms = divmod(ms, 1000)
        return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"

    blocks: list[str] = []
    for index, item in enumerate(timeline, start=1):
        blocks.append(
            f"{index}\n{stamp(item['start'])} --> {stamp(item['end'])}\n{item['text']}\n"
        )
    path.write_text("\n".join(blocks), encoding="utf-8")


def main() -> None:
    torch.set_num_threads(max(1, min(8, torch.get_num_threads())))
    base_reference = build_base_reference()
    model = ChatterboxTTS.from_pretrained(device="cpu")

    # Create an Ivy-conditioned reference for every vocal posture. These are
    # real generated audio anchors, not labels stored outside the synthesis call.
    mode_refs: dict[str, Path] = {}
    mode_ref_manifest: dict[str, Any] = {}
    for index, mode in enumerate(MODES.values(), start=1):
        ref_audio = generate_standard(
            model,
            mode.anchor_text,
            base_reference,
            mode.contrast,
            BASE_SEED + index * 701,
        )
        ref_path = REFS / f"Ivy_C2_HR_{mode.key}_reference.wav"
        write_audio(ref_path, ref_audio, "PCM_16")
        mode_refs[mode.key] = ref_path
        mode_ref_manifest[mode.key] = {
            "file": str(ref_path),
            "anchor_text": mode.anchor_text,
            "vocal_posture": mode.posture,
            "features": features_for_text(ref_audio, mode.anchor_text),
        }

    natural_audio: dict[str, np.ndarray] = {}
    contrast_audio: dict[str, np.ndarray] = {}
    take_manifest: dict[str, Any] = {}

    # Render standard speech units first. Special laugh/gasp units are replaced
    # after the standard model is released and Turbo is loaded.
    for index, unit in enumerate(UNITS, start=1):
        prompt = mode_refs[unit.mode]
        natural, natural_records, selected_natural = best_standard_take(
            model, unit, prompt, "natural", BASE_SEED + index * 1000
        )
        contrast, contrast_records, selected_contrast = best_standard_take(
            model, unit, prompt, "contrast", BASE_SEED + index * 1000 + 500
        )
        natural_audio[unit.key] = natural
        contrast_audio[unit.key] = contrast
        take_manifest[unit.key] = {
            "text": unit.text,
            "mode": unit.mode,
            "intent": unit.intent,
            "target_seconds": unit.target_seconds,
            "pause_after": unit.pause_after,
            "director_style": unit.director_style,
            "natural_candidates": natural_records,
            "contrast_candidates": contrast_records,
            "selected_natural": selected_natural,
            "selected_contrast": selected_contrast,
        }

    # Continuous baseline: the exact same words, but one global posture. This is
    # included only to make the human-reader improvements easy to hear.
    continuous_text = " ".join(unit.text for unit in UNITS)
    baseline_style = Style(exaggeration=0.52, cfg_weight=0.34, temperature=0.78)
    continuous = generate_standard(
        model,
        continuous_text,
        base_reference,
        baseline_style,
        BASE_SEED + 99001,
    )
    continuous, _ = scalar_peak(continuous, -1.8)

    del model
    gc.collect()

    # Turbo's nonverbal tags are used only where the thought genuinely causes a
    # laugh or gasp. The recovery pauses remain explicit in the thought map.
    turbo = ChatterboxTurboTTS.from_pretrained(device="cpu")
    for index, unit in enumerate(UNITS, start=1):
        if unit.special_turbo == "laugh":
            natural_text = "I'm sorry... I support you emotionally. I do not support this decision."
            contrast_text = "[chuckle] I'm sorry... I support you emotionally. I do not support this decision."
        elif unit.special_turbo == "gasp":
            natural_text = "No."
            contrast_text = "[gasp] No."
        else:
            continue

        prompt = mode_refs[unit.mode]
        natural = generate_turbo(turbo, natural_text, prompt, BASE_SEED + index * 2201, 0.82)
        contrast = generate_turbo(turbo, contrast_text, prompt, BASE_SEED + index * 2201 + 97, 0.94)
        natural_path = TAKES / f"{unit.key}_natural_turbo.wav"
        contrast_path = TAKES / f"{unit.key}_contrast_turbo.wav"
        write_audio(natural_path, natural)
        write_audio(contrast_path, contrast)
        natural_audio[unit.key] = natural
        contrast_audio[unit.key] = contrast
        take_manifest[unit.key]["turbo_override"] = {
            "natural_file": str(natural_path),
            "contrast_file": str(contrast_path),
            "natural_text": natural_text,
            "contrast_text": contrast_text,
            "natural_features": features_for_text(natural, unit.text),
            "contrast_features": features_for_text(contrast, unit.text),
            "post_effects": [],
        }

    del turbo
    gc.collect()

    natural_units = [(unit, natural_audio[unit.key]) for unit in UNITS]
    director_units = [
        (
            unit,
            contrast_audio[unit.key] if unit.director_style == "contrast" else natural_audio[unit.key],
        )
        for unit in UNITS
    ]
    all_contrast_units = [(unit, contrast_audio[unit.key]) for unit in UNITS]

    natural_master, natural_timeline = assemble(natural_units)
    director_master, director_timeline = assemble(director_units)
    contrast_master, contrast_timeline = assemble(all_contrast_units)

    masters = {
        "A_Continuous_Baseline": (continuous, []),
        "B_Natural_Thought_Unit_Pass": (natural_master, natural_timeline),
        "C_Human_Reader_Director_Comp": (director_master, director_timeline),
        "D_Wider_Tonal_Contrast": (contrast_master, contrast_timeline),
    }

    manifest: dict[str, Any] = {
        "status": "ok",
        "audition": "Ivy C2 Human-Reader Pass",
        "scenario_status": "fictional non-canon audition; no real viewer comment attributed",
        "voice_identity": "Ivy / Kokoro af_heart base conditioned through Chatterbox",
        "sample_rate": MASTER_SR,
        "channels": 1,
        "base_reference": str(base_reference),
        "mode_references": mode_ref_manifest,
        "signal_path": {
            "baseline": "single global voice posture",
            "natural": "thought units with mode-specific Ivy references and natural settings",
            "director_comp": "thought units with director-selected natural/contrast posture",
            "wider_contrast": "all thought units use stronger mode-specific contrast",
            "nonverbal": "Turbo tags only for causally motivated laugh and gasp",
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
            "thought-unit timeline placement",
            "5 ms anti-click fades",
        ],
        "thought_units": take_manifest,
        "masters": {},
    }

    for name, (master, timeline) in masters.items():
        wav_path = OUT / f"Ivy_C2_HR_{name}_48k_PCM24.wav"
        write_audio(wav_path, master)
        srt_path = None
        if timeline:
            srt_path = OUT / f"Ivy_C2_HR_{name}.srt"
            write_srt(srt_path, timeline)
        total_words = sum(word_count(unit.text) for unit in UNITS)
        duration = len(master) / MASTER_SR
        manifest["masters"][name] = {
            "wav": str(wav_path),
            "srt": str(srt_path) if srt_path else None,
            "duration": round(duration, 6),
            "word_count": total_words,
            "overall_wpm": round(total_words * 60 / duration, 3),
            "features": audio_features(master),
            "timeline": timeline,
            "post_effects": [],
        }

    comparison_parts: list[np.ndarray] = []
    markers: list[dict[str, Any]] = []
    cursor = 0.0
    for index, (name, info) in enumerate(manifest["masters"].items()):
        y, sr = sf.read(info["wav"], dtype="float32")
        if sr != MASTER_SR:
            raise RuntimeError("Unexpected master sample rate")
        start = cursor
        end = start + len(y) / MASTER_SR
        markers.append({"name": name, "start": round(start, 6), "end": round(end, 6)})
        comparison_parts.append(np.asarray(y, dtype=np.float32).reshape(-1))
        cursor = end
        if index < len(manifest["masters"]) - 1:
            comparison_parts.append(np.zeros(int(round(1.25 * MASTER_SR)), dtype=np.float32))
            cursor += 1.25
    comparison = np.concatenate(comparison_parts)
    comparison, _ = scalar_peak(comparison, -1.8)
    comparison_path = OUT / "Ivy_C2_Human_Reader_Audition_All_Versions_48k_PCM24.wav"
    write_audio(comparison_path, comparison)
    manifest["comparison"] = {
        "wav": str(comparison_path),
        "duration": round(len(comparison) / MASTER_SR, 6),
        "markers": markers,
    }

    (OUT / "Ivy_C2_Human_Reader_Manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8"
    )

    tone_lines = [
        "# Ivy C2 Human-Reader Tone Map",
        "",
        "This audition deliberately changes tone, pitch posture, pace, rhythm, breath state, and silence according to thought and meaning.",
        "",
    ]
    for unit in UNITS:
        mode = MODES[unit.mode]
        tone_lines.extend([
            f"## {unit.key}",
            "",
            f"**Text:** {unit.text}",
            "",
            f"**Intent:** {unit.intent}",
            "",
            f"**Vocal posture:** {mode.posture}",
            "",
            f"**Target speech duration:** {unit.target_seconds:.2f} seconds",
            "",
            f"**Processing silence after:** {unit.pause_after:.2f} seconds",
            "",
            f"**Director comp style:** {unit.director_style}",
            "",
        ])
    (OUT / "Ivy_C2_Human_Reader_Tone_Map.md").write_text("\n".join(tone_lines), encoding="utf-8")

    print(json.dumps({
        "status": "ok",
        "versions": {
            name: {
                "duration": info["duration"],
                "overall_wpm": info["overall_wpm"],
                "f0_mean_hz": info["features"]["f0_mean_hz"],
                "f0_std_hz": info["features"]["f0_std_hz"],
            }
            for name, info in manifest["masters"].items()
        },
        "mode_references": list(mode_refs for mode_refs in mode_ref_manifest),
    }, indent=2))


if __name__ == "__main__":
    main()
