#!/usr/bin/env python3
"""Analyze public-domain human audiobook readers and compare Ivy C2.

This is a small descriptive case study, not a universal norm. It measures:
- overall and local speaking rate;
- articulation rate after meaningful pauses are removed;
- pause frequency and duration;
- pitch span/variation;
- energy variation;
- phrase-boundary timing.

Human samples are public-domain LibriVox readings:
- Anne of Green Gables — Karen Savage
- Jane Eyre — Elizabeth Klett
- Wuthering Heights — Ruth Golding
- Anne of Green Gables dramatic reading — Arielle Lipshaw/full cast

Ivy C2 is supplied by the workflow from the existing project artifact.
"""
from __future__ import annotations

import json
import math
import re
import subprocess
from pathlib import Path
from typing import Any

import librosa
import numpy as np
import pandas as pd
import soundfile as sf
from faster_whisper import WhisperModel

ROOT = Path("human_reader_research")
RAW = ROOT / "raw"
CLIPS = ROOT / "clips"
OUT = ROOT / "analysis"
for directory in (RAW, CLIPS, OUT):
    directory.mkdir(parents=True, exist_ok=True)

TARGET_SR = 16000
HUMAN_CLIP_START = 35.0
HUMAN_CLIP_DURATION = 240.0

SOURCES: dict[str, dict[str, Any]] = {
    "anne_karen_savage": {
        "title": "Anne of Green Gables — Chapter 1",
        "reader": "Karen Savage",
        "style": "solo humorous/literary narration",
        "url": "https://archive.org/download/anne_greengables_librivox/anne_of_green_gables_01_montgomery.mp3",
        "start": HUMAN_CLIP_START,
        "duration": HUMAN_CLIP_DURATION,
    },
    "jane_eyre_elizabeth_klett": {
        "title": "Jane Eyre — Chapter 1",
        "reader": "Elizabeth Klett",
        "style": "solo first-person drama/romance narration",
        "url": "https://archive.org/download/jane_eyre_ver03_0809_librivox/janeeyre_01_bronte.mp3",
        "start": HUMAN_CLIP_START,
        "duration": HUMAN_CLIP_DURATION,
    },
    "wuthering_ruth_golding": {
        "title": "Wuthering Heights — Chapter 1",
        "reader": "Ruth Golding",
        "style": "solo gothic/dramatic narration",
        "url": "https://archive.org/download/wuthering_heights_rg_librivox/wutheringheights_01_bronte.mp3",
        "start": HUMAN_CLIP_START,
        "duration": HUMAN_CLIP_DURATION,
    },
    "anne_dramatic_full_cast": {
        "title": "Anne of Green Gables — Chapter 1 dramatic reading",
        "reader": "Arielle Lipshaw and full cast",
        "style": "dramatic/full-cast reading",
        "url": "https://archive.org/download/anneofgreengables_1102_librivox/anneofgreengables_01_montgomery.mp3",
        "start": HUMAN_CLIP_START,
        "duration": HUMAN_CLIP_DURATION,
    },
}

IVY_PATH = Path("ivy_input/Ivy_C2_3_Hybrid_Comp_48k_PCM24.wav")


def run(command: list[str]) -> None:
    subprocess.run(command, check=True)


def fetch_and_clip(key: str, source: dict[str, Any]) -> Path:
    raw = RAW / f"{key}.mp3"
    clip = CLIPS / f"{key}.wav"
    if not raw.exists():
        run([
            "curl", "-L", "--fail", "--retry", "5", "--retry-delay", "3",
            "--user-agent", "Mozilla/5.0 Beyond-the-Panel-Research",
            "-o", str(raw), source["url"],
        ])
    run([
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
        "-ss", str(source["start"]), "-t", str(source["duration"]),
        "-i", str(raw), "-ac", "1", "-ar", str(TARGET_SR),
        "-c:a", "pcm_s16le", str(clip),
    ])
    return clip


def prepare_ivy() -> Path:
    if not IVY_PATH.exists():
        raise FileNotFoundError(f"Missing Ivy input: {IVY_PATH}")
    clip = CLIPS / "ivy_c2_hybrid.wav"
    run([
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
        "-i", str(IVY_PATH), "-ac", "1", "-ar", str(TARGET_SR),
        "-c:a", "pcm_s16le", str(clip),
    ])
    return clip


def active_intervals(y: np.ndarray, sr: int) -> list[tuple[float, float]]:
    intervals = librosa.effects.split(y, top_db=35, frame_length=1024, hop_length=128)
    return [(float(start / sr), float(end / sr)) for start, end in intervals]


def silence_intervals(y: np.ndarray, sr: int) -> list[tuple[float, float]]:
    speech = active_intervals(y, sr)
    duration = len(y) / sr
    result: list[tuple[float, float]] = []
    cursor = 0.0
    for start, end in speech:
        if start > cursor:
            result.append((cursor, start))
        cursor = max(cursor, end)
    if cursor < duration:
        result.append((cursor, duration))
    return result


def pause_metrics(silences: list[tuple[float, float]], duration: float) -> dict[str, float]:
    durations = np.array([end - start for start, end in silences if end > start], dtype=float)
    meaningful = durations[durations >= 0.12]
    if meaningful.size == 0:
        meaningful = np.array([0.0])
    return {
        "silence_fraction": round(float(np.sum(durations) / max(duration, 1e-9)), 6),
        "pause_count_per_min": round(float(len(meaningful) / duration * 60), 3),
        "pause_ge_250ms_per_min": round(float(np.sum(durations >= 0.25) / duration * 60), 3),
        "pause_ge_500ms_per_min": round(float(np.sum(durations >= 0.50) / duration * 60), 3),
        "pause_ge_1s_per_min": round(float(np.sum(durations >= 1.00) / duration * 60), 3),
        "pause_median_ms": round(float(np.median(meaningful) * 1000), 2),
        "pause_p75_ms": round(float(np.percentile(meaningful, 75) * 1000), 2),
        "pause_p90_ms": round(float(np.percentile(meaningful, 90) * 1000), 2),
        "pause_max_ms": round(float(np.max(meaningful) * 1000), 2),
    }


def pitch_metrics(y: np.ndarray, sr: int) -> dict[str, float | None]:
    try:
        f0, _voiced, _prob = librosa.pyin(
            y,
            fmin=librosa.note_to_hz("C2"),
            fmax=librosa.note_to_hz("C7"),
            sr=sr,
            frame_length=2048,
            hop_length=256,
        )
        values = f0[np.isfinite(f0)] if f0 is not None else np.array([])
    except Exception:
        values = np.array([])
    if not values.size:
        return {
            "f0_median_hz": None,
            "f0_p10_hz": None,
            "f0_p90_hz": None,
            "f0_span_semitones_p10_p90": None,
            "f0_std_semitones": None,
        }
    midi = librosa.hz_to_midi(values)
    return {
        "f0_median_hz": round(float(np.median(values)), 3),
        "f0_p10_hz": round(float(np.percentile(values, 10)), 3),
        "f0_p90_hz": round(float(np.percentile(values, 90)), 3),
        "f0_span_semitones_p10_p90": round(float(np.percentile(midi, 90) - np.percentile(midi, 10)), 3),
        "f0_std_semitones": round(float(np.std(midi)), 3),
    }


def energy_metrics(y: np.ndarray) -> dict[str, float]:
    rms = librosa.feature.rms(y=y, frame_length=2048, hop_length=256)[0]
    db = 20 * np.log10(np.maximum(rms, 1e-9))
    active = db[db > (np.max(db) - 35)]
    if not active.size:
        active = db
    return {
        "rms_db_std": round(float(np.std(active)), 3),
        "rms_db_p10": round(float(np.percentile(active, 10)), 3),
        "rms_db_p90": round(float(np.percentile(active, 90)), 3),
        "rms_db_span_p10_p90": round(float(np.percentile(active, 90) - np.percentile(active, 10)), 3),
    }


def transcribe(model: WhisperModel, path: Path) -> tuple[str, list[dict[str, Any]]]:
    segments, _info = model.transcribe(
        str(path),
        language="en",
        beam_size=5,
        vad_filter=True,
        word_timestamps=True,
        condition_on_previous_text=True,
    )
    text_parts: list[str] = []
    words: list[dict[str, Any]] = []
    for segment in segments:
        clean = segment.text.strip()
        if clean:
            text_parts.append(clean)
        for word in segment.words or []:
            if word.start is None or word.end is None:
                continue
            token = word.word.strip()
            if not token:
                continue
            words.append({
                "word": token,
                "start": float(word.start),
                "end": float(word.end),
                "probability": float(word.probability),
            })
    return " ".join(text_parts), words


def rate_metrics(words: list[dict[str, Any]], duration: float) -> dict[str, float | int | None]:
    count = len(words)
    overall_wpm = count / duration * 60 if duration else 0.0
    gaps = [max(0.0, b["start"] - a["end"]) for a, b in zip(words, words[1:])]
    meaningful_silence = sum(gap for gap in gaps if gap >= 0.12)
    articulation_seconds = max(duration - meaningful_silence, 1e-9)
    articulation_wpm = count / articulation_seconds * 60

    local_rates: list[float] = []
    window = 10.0
    cursor = 0.0
    while cursor < duration:
        local_count = sum(1 for word in words if cursor <= word["start"] < cursor + window)
        local_rates.append(local_count / window * 60)
        cursor += window
    local = np.array(local_rates, dtype=float)
    word_durations = np.array([max(0.0, word["end"] - word["start"]) for word in words], dtype=float)

    return {
        "word_count_asr": count,
        "overall_wpm": round(float(overall_wpm), 3),
        "articulation_wpm": round(float(articulation_wpm), 3),
        "local_10s_wpm_mean": round(float(np.mean(local)), 3),
        "local_10s_wpm_std": round(float(np.std(local)), 3),
        "local_10s_wpm_p10": round(float(np.percentile(local, 10)), 3),
        "local_10s_wpm_p90": round(float(np.percentile(local, 90)), 3),
        "local_10s_wpm_span_p10_p90": round(float(np.percentile(local, 90) - np.percentile(local, 10)), 3),
        "median_word_duration_ms": round(float(np.median(word_durations) * 1000), 2) if word_durations.size else None,
        "asr_mean_word_probability": round(float(np.mean([word["probability"] for word in words])), 4) if words else None,
    }


def boundary_metrics(words: list[dict[str, Any]]) -> dict[str, float | None]:
    punctuation_gaps: list[float] = []
    internal_gaps: list[float] = []
    for current, following in zip(words, words[1:]):
        gap = max(0.0, following["start"] - current["end"])
        if current["word"].endswith((".", "?", "!", ";", ":")):
            punctuation_gaps.append(gap)
        else:
            internal_gaps.append(gap)

    def summarize(values: list[float], prefix: str) -> dict[str, float | None]:
        if not values:
            return {f"{prefix}_median_ms": None, f"{prefix}_p90_ms": None}
        array = np.array(values, dtype=float)
        return {
            f"{prefix}_median_ms": round(float(np.median(array) * 1000), 2),
            f"{prefix}_p90_ms": round(float(np.percentile(array, 90) * 1000), 2),
        }

    result: dict[str, float | None] = {}
    result.update(summarize(punctuation_gaps, "sentence_boundary_gap"))
    result.update(summarize(internal_gaps, "within_phrase_gap"))
    return result


def analyze(model: WhisperModel, key: str, metadata: dict[str, Any], path: Path) -> dict[str, Any]:
    y, sr = librosa.load(path, sr=TARGET_SR, mono=True)
    duration = len(y) / sr
    transcript, words = transcribe(model, path)
    result: dict[str, Any] = {
        "key": key,
        **metadata,
        "clip_duration_seconds": round(duration, 3),
        **pause_metrics(silence_intervals(y, sr), duration),
        **pitch_metrics(y, sr),
        **energy_metrics(y),
        **rate_metrics(words, duration),
        **boundary_metrics(words),
        "transcript": transcript,
        "word_timestamps": words,
    }
    (OUT / f"{key}.json").write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
    return result


def main() -> None:
    prepared: dict[str, tuple[dict[str, Any], Path]] = {}
    for key, source in SOURCES.items():
        prepared[key] = (source, fetch_and_clip(key, source))
    prepared["ivy_c2_hybrid"] = ({
        "title": "Ivy C2.3 Hybrid Comp",
        "reader": "Ivy C2",
        "style": "synthetic host-performance audition",
        "url": None,
        "start": 0.0,
        "duration": None,
    }, prepare_ivy())

    model = WhisperModel("base.en", device="cpu", compute_type="int8", cpu_threads=8)
    results = [analyze(model, key, metadata, path) for key, (metadata, path) in prepared.items()]

    rows = []
    for result in results:
        rows.append({
            key: value
            for key, value in result.items()
            if key not in {"transcript", "word_timestamps", "url"}
        })
    dataframe = pd.DataFrame(rows)
    dataframe.to_csv(OUT / "human_vs_ivy_metrics.csv", index=False)

    human = dataframe[dataframe["reader"] != "Ivy C2"]
    numeric_columns = [
        "overall_wpm", "articulation_wpm", "local_10s_wpm_std",
        "local_10s_wpm_span_p10_p90", "silence_fraction",
        "pause_ge_500ms_per_min", "pause_ge_1s_per_min",
        "pause_median_ms", "pause_p90_ms", "f0_span_semitones_p10_p90",
        "f0_std_semitones", "rms_db_span_p10_p90", "rms_db_std",
        "sentence_boundary_gap_median_ms", "sentence_boundary_gap_p90_ms",
    ]
    ranges: dict[str, Any] = {}
    for column in numeric_columns:
        values = pd.to_numeric(human[column], errors="coerce").dropna().to_numpy()
        if values.size:
            ranges[column] = {
                "min": round(float(np.min(values)), 4),
                "median": round(float(np.median(values)), 4),
                "max": round(float(np.max(values)), 4),
            }
    (OUT / "human_reference_ranges.json").write_text(json.dumps(ranges, indent=2), encoding="utf-8")

    ivy_row = dataframe[dataframe["reader"] == "Ivy C2"].iloc[0].to_dict()
    comparison = {
        "scope_note": "Small four-reading public-domain case study; descriptive, not universal norms.",
        "ivy": ivy_row,
        "human_reference_ranges": ranges,
        "flags": [],
    }
    if ivy_row["overall_wpm"] > ranges["overall_wpm"]["max"]:
        comparison["flags"].append("Ivy overall WPM exceeds every human sample.")
    if ivy_row["local_10s_wpm_span_p10_p90"] < ranges["local_10s_wpm_span_p10_p90"]["min"]:
        comparison["flags"].append("Ivy changes local speaking rate less than every human sample.")
    if ivy_row["pause_p90_ms"] < ranges["pause_p90_ms"]["min"]:
        comparison["flags"].append("Ivy's longer pauses are shorter than every human sample's 90th-percentile pause.")
    if ivy_row["sentence_boundary_gap_p90_ms"] < ranges["sentence_boundary_gap_p90_ms"]["min"]:
        comparison["flags"].append("Ivy compresses major phrase/sentence boundaries relative to every human sample.")
    if ivy_row["f0_std_semitones"] < ranges["f0_std_semitones"]["min"]:
        comparison["flags"].append("Ivy's pitch variation is below every human sample.")
    (OUT / "ivy_vs_human_findings.json").write_text(json.dumps(comparison, indent=2, default=str), encoding="utf-8")

    report_lines = [
        "# Human Reader Prosody Study for Ivy C2",
        "",
        "This is a small public-domain case study, not a universal narrator benchmark.",
        "The recordings span solo humorous/literary narration, first-person drama/romance, gothic narration, and a dramatic/full-cast reading.",
        "",
        "## Measured summary",
        "",
    ]
    for result in results:
        report_lines.append(
            f"- **{result['reader']} — {result['title']}**: "
            f"{result['overall_wpm']} WPM; local 10-second rate span "
            f"{result['local_10s_wpm_span_p10_p90']} WPM; silence "
            f"{result['silence_fraction'] * 100:.1f}%; 90th-percentile pause "
            f"{result['pause_p90_ms']} ms; pitch span "
            f"{result['f0_span_semitones_p10_p90']} semitones; energy span "
            f"{result['rms_db_span_p10_p90']} dB."
        )
    report_lines.extend([
        "",
        "## Automated Ivy flags",
        "",
    ])
    if comparison["flags"]:
        report_lines.extend(f"- {flag}" for flag in comparison["flags"])
    else:
        report_lines.append("- No simple range flag fired; perceptual placement of timing and tone remains the key issue.")
    report_lines.extend([
        "",
        "## Cautions",
        "",
        "- Source age, microphone, editing, and compression affect spectral and energy measures.",
        "- ASR word counts and punctuation are approximate.",
        "- Pitch variation alone does not equal acting; where and why tone changes occur matters more.",
        "- Human listening and direction remain the final approval gate.",
    ])
    (OUT / "human_reader_study.md").write_text("\n".join(report_lines), encoding="utf-8")

    print(dataframe[[
        "reader", "overall_wpm", "local_10s_wpm_span_p10_p90",
        "silence_fraction", "pause_p90_ms", "f0_span_semitones_p10_p90",
        "rms_db_span_p10_p90",
    ]].to_string(index=False))
    print(json.dumps(comparison["flags"], indent=2))


if __name__ == "__main__":
    main()
