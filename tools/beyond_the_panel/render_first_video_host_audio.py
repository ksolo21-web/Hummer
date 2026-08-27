#!/usr/bin/env python3
"""Render Ivy's first-video host introduction and post-chapter recap.

The signal path is deliberately dry and minimal. No reverb, echo, room
simulation, delay, widening, doubling, chorus, EQ, compression, ambience,
or post-synthesis time stretching is allowed.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly
from kokoro import KPipeline

OUT = Path("out")
LINES = OUT / "individual_lines"
OUT.mkdir(parents=True, exist_ok=True)
LINES.mkdir(parents=True, exist_ok=True)

VOICE = "af_heart"
MODEL_SR = 24_000
MASTER_SR = 48_000

SEGMENTS = {
    "first_video_intro": [
        {
            "text": "Hi! Okay... this is actually happening.",
            "speed": 0.98,
            "pause": 0.36,
            "direction": "Bright, excited, a little adorably nervous; smile on 'actually happening'.",
        },
        {
            "text": "I'm Ivy, and welcome to the very first video here on Beyond the Panel.",
            "speed": 0.96,
            "pause": 0.30,
            "direction": "Warm personal welcome, speaking to one viewer rather than announcing to a crowd.",
        },
        {
            "text": "I'll be your host, your narrator, and—somehow—every character we meet along the way.",
            "speed": 0.98,
            "pause": 0.30,
            "direction": "Confident, playful lift on 'somehow'.",
        },
        {
            "text": "Which means I may be a fearless hero, a terrifying villain, and somebody's sweet grandmother in the same five minutes.",
            "speed": 1.01,
            "pause": 0.22,
            "direction": "Playfully build the contrast; affectionate amusement on 'sweet grandmother'.",
        },
        {
            "text": "Heh... we're going to have fun.",
            "speed": 0.93,
            "pause": 0.40,
            "direction": "A small natural amused breath/chuckle, not a performed cartoon laugh.",
        },
        {
            "text": "That's the fun. We step into these worlds together, get attached, celebrate the wins, and question some truly terrible decisions.",
            "speed": 1.00,
            "pause": 0.28,
            "direction": "Genuine enthusiasm, then playful disbelief on 'truly terrible decisions'.",
        },
        {
            "text": "I'll probably be yelling at the page right along with you.",
            "speed": 0.99,
            "pause": 0.48,
            "direction": "Friendly, conspiratorial, smiling.",
        },
        {
            "text": "For our first trip beyond the panel, we're opening a full-color sample story called The Unmapped Sun.",
            "speed": 0.95,
            "pause": 0.30,
            "direction": "Shift smoothly from playful host to cinematic curiosity.",
        },
        {
            "text": "Ten artificial suns watch over Tenfold—but an entire street has vanished, and the people connected to it are disappearing from memory.",
            "speed": 0.94,
            "pause": 0.32,
            "direction": "Controlled mystery; more emotional weight on 'disappearing from memory'.",
        },
        {
            "text": "This is Sample Chapter One: The Eleventh Key.",
            "speed": 0.94,
            "pause": 0.38,
            "direction": "Clear title identification, confident but not announcer-like.",
        },
        {
            "text": "I'm really glad you're here at the beginning.",
            "speed": 0.91,
            "pause": 0.30,
            "direction": "Sincere and intimate; smile gently through the line.",
        },
        {
            "text": "All right... let's open the first page.",
            "speed": 0.94,
            "pause": 0.00,
            "direction": "Warm invitation with a small lift into the story.",
        },
    ],
    "post_chapter_recap": [
        {
            "text": "Okay... wait. We need to talk about that ending.",
            "speed": 0.96,
            "pause": 0.34,
            "direction": "Fresh, genuine reaction; surprised but not theatrical.",
        },
        {
            "text": "Sori's own grandmother forgetting him was already cruel. But Nia paying with the memory of her mother's voice? That was the moment that got me.",
            "speed": 0.95,
            "pause": 0.32,
            "direction": "Tender and emotionally affected, with natural emphasis on the sacrifice.",
        },
        {
            "text": "She acts like every cost is hers to carry, and then Orin walks in and says her mother erased herself to keep the sun from finding her.",
            "speed": 0.97,
            "pause": 0.30,
            "direction": "Thoughtful character observation, building disbelief toward the reveal.",
        },
        {
            "text": "Sir... what exactly are we supposed to do with that information?",
            "speed": 0.93,
            "pause": 0.25,
            "direction": "Playful disbelief; a tiny smile without breaking the stakes.",
        },
        {
            "text": "Heh. Also, I do not trust Orin. At all.",
            "speed": 0.91,
            "pause": 0.30,
            "direction": "Small amused exhale followed by firm suspicion; punch 'at all'.",
        },
        {
            "text": "But I don't think he's lying about everything—and somehow, that makes him worse.",
            "speed": 0.94,
            "pause": 0.40,
            "direction": "Lower, more serious reflection; restrained concern.",
        },
        {
            "text": "My theory? The eleventh key doesn't only open hidden roads. I think it reconnects people to whatever the Registry tried to erase... and Nia may be part of that.",
            "speed": 0.95,
            "pause": 0.38,
            "direction": "Curious and intelligent, sharing a real theory rather than declaring a fact.",
        },
        {
            "text": "What hit you hardest: Sori being forgotten, Nia's sacrifice, or that final reveal about her mother?",
            "speed": 0.98,
            "pause": 0.28,
            "direction": "Direct, genuinely interested in the viewer's answer.",
        },
        {
            "text": "Tell me your take in the comments, because I already have way too many theories.",
            "speed": 1.00,
            "pause": 0.38,
            "direction": "Playful and self-aware; smile on 'way too many theories'.",
        },
        {
            "text": "And since this was our very first trip beyond the panel... thank you for being here.",
            "speed": 0.92,
            "pause": 0.32,
            "direction": "Slow, sincere gratitude; make the launch feel shared.",
        },
        {
            "text": "I'm Ivy. I'll see you in the next story.",
            "speed": 0.94,
            "pause": 0.00,
            "direction": "Sweet, confident, memorable goodbye.",
        },
    ],
}


def synthesize(pipeline: KPipeline, text: str, speed: float) -> np.ndarray:
    chunks: list[np.ndarray] = []
    for _graphemes, _phonemes, audio in pipeline(text, voice=VOICE, speed=speed):
        y = np.asarray(audio, dtype=np.float32).reshape(-1)
        if y.size:
            chunks.append(y)
    if not chunks:
        raise RuntimeError(f"No audio generated for {text!r}")
    y = np.concatenate(chunks)
    nz = np.flatnonzero(np.abs(y) > 1e-5)
    if nz.size:
        pad = int(0.025 * MODEL_SR)
        y = y[max(0, int(nz[0]) - pad): min(len(y), int(nz[-1]) + pad + 1)]
    return y


def anti_click(y: np.ndarray, seconds: float = 0.005) -> np.ndarray:
    y = y.copy()
    n = min(int(seconds * MASTER_SR), len(y) // 2)
    if n > 0:
        ramp = np.linspace(0.0, 1.0, n, endpoint=False, dtype=np.float32)
        y[:n] *= ramp
        y[-n:] *= ramp[::-1]
    return y


def scalar_level(y: np.ndarray, target_rms_dbfs: float = -20.5, ceiling_dbfs: float = -2.5) -> tuple[np.ndarray, float]:
    rms = float(np.sqrt(np.mean(np.square(y, dtype=np.float64)) + 1e-12))
    gain = 10 ** ((target_rms_dbfs - 20 * math.log10(max(rms, 1e-12))) / 20)
    gain = min(gain, (10 ** (ceiling_dbfs / 20)) / (float(np.max(np.abs(y))) + 1e-12))
    return (y * gain).astype(np.float32), 20 * math.log10(max(gain, 1e-12))


def srt_time(seconds: float) -> str:
    ms = int(round(seconds * 1000))
    h, ms = divmod(ms, 3_600_000)
    m, ms = divmod(ms, 60_000)
    s, ms = divmod(ms, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


pipeline = KPipeline(lang_code="a")
manifest: dict[str, object] = {
    "engine": "Kokoro-82M",
    "voice": VOICE,
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
        "pitch_shift": False,
        "time_stretch": False,
        "eq": False,
        "compression": False,
        "noise_reduction": False,
        "ambience": False,
    },
    "allowed_processing": [
        "native synthesis speed",
        "scalar gain",
        "48 kHz polyphase resampling",
        "timeline placement",
        "5 ms anti-click fades",
    ],
    "segments": {},
}

for segment_name, lines in SEGMENTS.items():
    assembled: list[np.ndarray] = []
    timeline: list[dict[str, object]] = []
    cursor = 0.0
    srt_entries: list[str] = []

    for idx, line in enumerate(lines, start=1):
        raw = synthesize(pipeline, line["text"], float(line["speed"]))
        y = resample_poly(raw, MASTER_SR, MODEL_SR).astype(np.float32)
        y = anti_click(y)
        y, gain_db = scalar_level(y)

        line_path = LINES / f"{segment_name}_{idx:02d}.wav"
        sf.write(line_path, y, MASTER_SR, subtype="PCM_24")

        start = cursor
        end = start + len(y) / MASTER_SR
        assembled.append(y)
        pause = float(line["pause"])
        if pause > 0:
            assembled.append(np.zeros(int(round(pause * MASTER_SR)), dtype=np.float32))

        timeline.append({
            "index": idx,
            "text": line["text"],
            "direction": line["direction"],
            "native_speed": line["speed"],
            "start": round(start, 6),
            "end": round(end, 6),
            "duration": round(end - start, 6),
            "pause_after": pause,
            "gain_db": round(gain_db, 3),
            "post_effects": [],
            "line_file": str(line_path),
        })
        srt_entries.append(
            f"{idx}\n{srt_time(start)} --> {srt_time(end)}\n{line['text']}\n"
        )
        cursor = end + pause

    master = np.concatenate(assembled) if assembled else np.zeros(1, dtype=np.float32)
    peak = float(np.max(np.abs(master)) + 1e-12)
    global_gain = min(1.0, (10 ** (-1.5 / 20)) / peak)
    master = (master * global_gain).astype(np.float32)

    wav_path = OUT / f"Ivy_{segment_name}_48k_PCM24.wav"
    mp3_source = OUT / f"Ivy_{segment_name}_48k_FLOAT.wav"
    srt_path = OUT / f"Ivy_{segment_name}.srt"
    sf.write(wav_path, master, MASTER_SR, subtype="PCM_24")
    sf.write(mp3_source, master, MASTER_SR, subtype="FLOAT")
    srt_path.write_text("\n".join(srt_entries), encoding="utf-8")

    manifest["segments"][segment_name] = {
        "duration": round(len(master) / MASTER_SR, 6),
        "global_gain_db": round(20 * math.log10(max(global_gain, 1e-12)), 3),
        "peak_dbfs": round(20 * math.log10(max(float(np.max(np.abs(master))), 1e-12)), 3),
        "wav": str(wav_path),
        "float_wav_for_mp3_encoding": str(mp3_source),
        "srt": str(srt_path),
        "lines": timeline,
    }

(OUT / "Ivy_first_video_host_audio_manifest.json").write_text(
    json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8"
)

(OUT / "Ivy_first_video_host_scripts.md").write_text(
    "# Beyond the Panel — First Video Host Scripts\n\n"
    + "## First-video introduction\n\n"
    + "\n\n".join(line["text"] for line in SEGMENTS["first_video_intro"])
    + "\n\n## Post-chapter recap and Ivy's take\n\n"
    + "\n\n".join(line["text"] for line in SEGMENTS["post_chapter_recap"])
    + "\n",
    encoding="utf-8",
)

print(json.dumps({
    "status": "ok",
    "voice": VOICE,
    "segments": {
        name: manifest["segments"][name]["duration"]
        for name in SEGMENTS
    },
}, indent=2))
