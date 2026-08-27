#!/usr/bin/env python3
"""Audit human reaction-video delivery for the Beyond the Panel / Ivy C2 system.

This is a research-only pipeline. It downloads public YouTube audio for a small,
representative sample of reaction formats, transcribes the speech, measures local
prosody, identifies likely reaction moments, and exports short internal clips,
plots, transcripts, and a structured report. Raw full-length audio is deleted
before the artifact is uploaded.

The purpose is not to copy any creator's words or persona. The purpose is to
measure human timing behaviors that synthetic host performances often miss:
latency, silence, local speaking-rate changes, pitch resets, energy changes,
fragmented speech, laughter recovery, and emotional-state transitions.
"""
from __future__ import annotations

import csv
import json
import math
import os
import re
import shutil
import subprocess
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Iterable

import librosa
import matplotlib.pyplot as plt
import numpy as np
import soundfile as sf
from faster_whisper import WhisperModel

ROOT = Path("reactor_research")
RAW = ROOT / "raw"
TRANSCRIPTS = ROOT / "transcripts"
PLOTS = ROOT / "plots"
CLIPS = ROOT / "internal_review_clips"
for path in (RAW, TRANSCRIPTS, PLOTS, CLIPS):
    path.mkdir(parents=True, exist_ok=True)

SAMPLE_RATE = 16_000
MAX_SECONDS = 2_700  # cap each source at 45 minutes for a bounded research run

# Primary-source sample covering solo/duo/group, episode/manga, comedy/romance,
# emotional ending, cliffhanger, and long-form theory discussion.
SOURCES = [
    {
        "video_id": "vKqx8ueZI2s",
        "category": "emotional ending / sincere reflection",
        "label": "86 Episode 23 reaction",
    },
    {
        "video_id": "3KR9_azBeu0",
        "category": "comedy / spontaneous laughter",
        "label": "The Apothecary Diaries reaction",
    },
    {
        "video_id": "XjnQTsmlIUU",
        "category": "romance / warm anticipation",
        "label": "Blue Box Episode 1 reaction",
    },
    {
        "video_id": "3nqOAWPY5UM",
        "category": "manga live reaction / theory",
        "label": "One Piece chapter live reaction",
    },
    {
        "video_id": "9sVi6H06Kag",
        "category": "major reveal / cliffhanger / theory",
        "label": "Boruto Chapter 66 live reaction",
    },
    {
        "video_id": "d5Ng2PTLIAw",
        "category": "romantic comedy / sustained laughter",
        "label": "Kaguya-sama reaction",
    },
]

REACTION_PATTERNS = {
    "surprise": re.compile(
        r"\b(wait|what|whoa|woah|wow|hold on|no way|are you serious|oh my god|oh my gosh|yo|bro|nah|stop)\b",
        re.I,
    ),
    "processing": re.compile(
        r"\b(okay|so|because|that means|i think|my theory|maybe|actually|now i understand|i knew|i was wrong|i missed)\b",
        re.I,
    ),
    "emotion": re.compile(
        r"\b(hurt|heart|cry|cried|sad|beautiful|love|trust|mother|mom|father|dad|sweet|wholesome|pain|emotional)\b",
        re.I,
    ),
    "cliffhanger": re.compile(
        r"\b(end it there|that's where|chapter ends|episode ends|cliffhanger|you can't leave|to be continued|no no no)\b",
        re.I,
    ),
    "laughter": re.compile(r"\b(laugh|laughing|laughed|funny|hilarious|i can't|i'm dead|chuckle)\b", re.I),
    "romance": re.compile(
        r"\b(kiss|flirt|flirting|chemistry|romance|romantic|look at them|they like|in love|couple|ship)\b",
        re.I,
    ),
}

FILLER_PATTERN = re.compile(r"\b(um+|uh+|erm+|hmm+|mm+|like|you know|i mean|okay|so)\b", re.I)
RESTART_PATTERN = re.compile(r"(?:\b\w+\b)[\s,;:—-]+\1\b", re.I)
FRAGMENT_START = re.compile(r"^(wait|what|no|oh|okay|yo|bro|nah|mm|hmm|why|how)\b", re.I)


@dataclass
class SegmentRecord:
    video_id: str
    category: str
    source_label: str
    title: str
    uploader: str
    start: float
    end: float
    duration: float
    text: str
    words: int
    words_per_second: float
    filler_count: int
    lexical_reaction_score: float
    pitch_mean_hz: float | None
    pitch_std_hz: float | None
    pitch_range_hz: float | None
    rms_dbfs: float
    rms_change_db: float
    silence_before_s: float
    silence_after_s: float
    pause_fraction: float
    likely_type: str
    composite_score: float


def run(cmd: list[str], *, check: bool = True) -> subprocess.CompletedProcess[str]:
    print("$", " ".join(cmd), flush=True)
    return subprocess.run(cmd, check=check, text=True, capture_output=True)


def download_audio(source: dict[str, str]) -> tuple[Path, dict[str, Any]]:
    video_id = source["video_id"]
    url = f"https://www.youtube.com/watch?v={video_id}"
    output_template = str(RAW / f"{video_id}.%(ext)s")
    info_path = RAW / f"{video_id}.info.json"

    # Prefer the normal web client, then let current yt-dlp select fallbacks.
    # A bounded section keeps the study lightweight and avoids retaining full
    # copyrighted recordings longer than necessary.
    cmd = [
        "yt-dlp",
        "--no-playlist",
        "--no-progress",
        "--write-info-json",
        "--extract-audio",
        "--audio-format",
        "wav",
        "--audio-quality",
        "5",
        "--download-sections",
        f"*0-{MAX_SECONDS}",
        "--force-keyframes-at-cuts",
        "--postprocessor-args",
        f"ffmpeg:-ar {SAMPLE_RATE} -ac 1",
        "--output",
        output_template,
        url,
    ]
    result = run(cmd, check=False)
    if result.returncode != 0:
        # Retry with alternate player clients commonly useful on cloud runners.
        cmd.insert(-2, "--extractor-args")
        cmd.insert(-2, "youtube:player_client=android_vr,web_creator")
        result = run(cmd, check=False)
    if result.returncode != 0:
        raise RuntimeError(f"yt-dlp failed for {video_id}:\n{result.stderr[-4000:]}")

    wav_candidates = sorted(RAW.glob(f"{video_id}*.wav"))
    if not wav_candidates:
        raise FileNotFoundError(f"No WAV created for {video_id}")
    wav_path = wav_candidates[0]

    if not info_path.exists():
        info_candidates = sorted(RAW.glob(f"{video_id}*.info.json"))
        if not info_candidates:
            raise FileNotFoundError(f"No info JSON created for {video_id}")
        info_path = info_candidates[0]
    info = json.loads(info_path.read_text(encoding="utf-8"))
    return wav_path, info


def load_mono(path: Path) -> tuple[np.ndarray, int]:
    y, sr = librosa.load(path, sr=SAMPLE_RATE, mono=True)
    return np.asarray(y, dtype=np.float32), sr


def dbfs_rms(y: np.ndarray) -> float:
    if y.size == 0:
        return -120.0
    rms = float(np.sqrt(np.mean(np.square(y, dtype=np.float64)) + 1e-12))
    return 20.0 * math.log10(max(rms, 1e-12))


def contiguous_silence(y: np.ndarray, sr: int, direction: str, threshold_db: float = -38.0, max_s: float = 3.0) -> float:
    if y.size == 0:
        return 0.0
    frame = max(1, int(0.02 * sr))
    limit = min(len(y), int(max_s * sr))
    data = y[-limit:] if direction == "before" else y[:limit]
    if direction == "before":
        starts = range(len(data) - frame, -1, -frame)
    else:
        starts = range(0, len(data) - frame + 1, frame)
    silent = 0
    for start in starts:
        chunk = data[start : start + frame]
        if dbfs_rms(chunk) <= threshold_db:
            silent += len(chunk)
        else:
            break
    return silent / sr


def pause_fraction(y: np.ndarray, sr: int, threshold_db: float = -36.0) -> float:
    if y.size == 0:
        return 0.0
    frame_length = int(0.025 * sr)
    hop_length = int(0.010 * sr)
    rms = librosa.feature.rms(y=y, frame_length=frame_length, hop_length=hop_length)[0]
    db = librosa.amplitude_to_db(np.maximum(rms, 1e-8), ref=1.0)
    return float(np.mean(db <= threshold_db))


def pitch_metrics(y: np.ndarray, sr: int) -> tuple[float | None, float | None, float | None]:
    if len(y) < int(0.35 * sr):
        return None, None, None
    try:
        f0, _voiced, _prob = librosa.pyin(
            y,
            fmin=librosa.note_to_hz("C2"),
            fmax=librosa.note_to_hz("C7"),
            sr=sr,
            frame_length=2048,
            hop_length=256,
        )
        vals = f0[np.isfinite(f0)] if f0 is not None else np.array([])
        if vals.size < 4:
            return None, None, None
        return float(np.mean(vals)), float(np.std(vals)), float(np.ptp(vals))
    except Exception:
        return None, None, None


def transcribe(model: WhisperModel, wav_path: Path, video_id: str) -> list[dict[str, Any]]:
    segments, info = model.transcribe(
        str(wav_path),
        language="en",
        beam_size=5,
        vad_filter=True,
        vad_parameters={"min_silence_duration_ms": 220},
        word_timestamps=True,
        condition_on_previous_text=True,
    )
    records: list[dict[str, Any]] = []
    for segment in segments:
        words = []
        if segment.words:
            for word in segment.words:
                words.append({
                    "start": float(word.start or segment.start),
                    "end": float(word.end or segment.end),
                    "word": word.word,
                    "probability": float(word.probability or 0.0),
                })
        records.append({
            "start": float(segment.start),
            "end": float(segment.end),
            "text": segment.text.strip(),
            "avg_logprob": float(segment.avg_logprob),
            "no_speech_prob": float(segment.no_speech_prob),
            "words": words,
        })
    payload = {
        "video_id": video_id,
        "language": info.language,
        "language_probability": float(info.language_probability),
        "duration": float(info.duration),
        "segments": records,
    }
    (TRANSCRIPTS / f"{video_id}.json").write_text(json.dumps(payload, indent=2), encoding="utf-8")
    (TRANSCRIPTS / f"{video_id}.txt").write_text(
        "\n".join(f"[{r['start']:8.2f}-{r['end']:8.2f}] {r['text']}" for r in records),
        encoding="utf-8",
    )
    return records


def lexical_scores(text: str) -> tuple[float, str, int]:
    counts = {name: len(pattern.findall(text)) for name, pattern in REACTION_PATTERNS.items()}
    filler_count = len(FILLER_PATTERN.findall(text))
    # Weight first-order spontaneous reaction cues more heavily than analysis words.
    score = (
        counts["surprise"] * 2.4
        + counts["cliffhanger"] * 2.5
        + counts["laughter"] * 1.5
        + counts["emotion"] * 1.25
        + counts["romance"] * 1.25
        + counts["processing"] * 0.7
        + min(filler_count, 3) * 0.25
        + (0.8 if FRAGMENT_START.search(text) else 0.0)
    )
    likely = max(counts, key=counts.get) if any(counts.values()) else "general"
    return score, likely, filler_count


def analyze_segments(
    source: dict[str, str],
    info: dict[str, Any],
    y: np.ndarray,
    sr: int,
    transcript: list[dict[str, Any]],
) -> list[SegmentRecord]:
    title = str(info.get("title") or source["label"])
    uploader = str(info.get("uploader") or info.get("channel") or "Unknown")
    total_rms = dbfs_rms(y)
    records: list[SegmentRecord] = []

    for seg in transcript:
        start = max(0.0, float(seg["start"]))
        end = min(len(y) / sr, float(seg["end"]))
        duration = max(0.01, end - start)
        text = seg["text"].strip()
        if not text:
            continue
        start_i = int(start * sr)
        end_i = max(start_i + 1, int(end * sr))
        clip = y[start_i:end_i]
        before = y[max(0, start_i - int(3 * sr)) : start_i]
        after = y[end_i : min(len(y), end_i + int(3 * sr))]
        word_count = len(re.findall(r"\b[\w']+\b", text))
        wps = word_count / duration
        lex_score, likely, filler_count = lexical_scores(text)
        pmean, pstd, prange = pitch_metrics(clip, sr)
        local_rms = dbfs_rms(clip)
        before_s = contiguous_silence(before, sr, "before")
        after_s = contiguous_silence(after, sr, "after")
        pfrac = pause_fraction(clip, sr)

        # The composite score is only a way to shortlist moments for manual
        # inspection. It intentionally rewards contrast and reaction latency.
        pitch_component = min((pstd or 0.0) / 35.0, 2.0)
        energy_component = max(0.0, min((local_rms - total_rms + 6.0) / 6.0, 2.0))
        rate_contrast = abs(wps - 2.5) / 2.5
        silence_component = min(before_s / 0.45, 2.0) + min(after_s / 0.45, 1.0)
        fragment_component = 0.8 if FRAGMENT_START.search(text) else 0.0
        composite = (
            lex_score
            + 1.15 * pitch_component
            + 0.65 * energy_component
            + 0.65 * rate_contrast
            + 0.85 * silence_component
            + fragment_component
        )

        records.append(
            SegmentRecord(
                video_id=source["video_id"],
                category=source["category"],
                source_label=source["label"],
                title=title,
                uploader=uploader,
                start=round(start, 3),
                end=round(end, 3),
                duration=round(duration, 3),
                text=text,
                words=word_count,
                words_per_second=round(wps, 3),
                filler_count=filler_count,
                lexical_reaction_score=round(lex_score, 3),
                pitch_mean_hz=round(pmean, 3) if pmean is not None else None,
                pitch_std_hz=round(pstd, 3) if pstd is not None else None,
                pitch_range_hz=round(prange, 3) if prange is not None else None,
                rms_dbfs=round(local_rms, 3),
                rms_change_db=round(local_rms - total_rms, 3),
                silence_before_s=round(before_s, 3),
                silence_after_s=round(after_s, 3),
                pause_fraction=round(pfrac, 3),
                likely_type=likely,
                composite_score=round(composite, 3),
            )
        )
    return records


def export_clips(y: np.ndarray, sr: int, records: list[SegmentRecord], video_id: str, n: int = 10) -> None:
    chosen: list[SegmentRecord] = []
    for rec in sorted(records, key=lambda r: r.composite_score, reverse=True):
        # Keep shortlisted moments separated so one high-energy exchange does not
        # monopolize the review set.
        if all(abs(rec.start - old.start) > 18.0 for old in chosen):
            chosen.append(rec)
        if len(chosen) >= n:
            break
    for index, rec in enumerate(chosen, start=1):
        start = max(0.0, rec.start - 3.0)
        end = min(len(y) / sr, rec.end + 4.0)
        clip = y[int(start * sr) : int(end * sr)]
        path = CLIPS / f"{video_id}_{index:02d}_{start:07.2f}_{end:07.2f}.wav"
        sf.write(path, clip, sr, subtype="PCM_16")


def plot_timeline(video_id: str, y: np.ndarray, sr: int, records: list[SegmentRecord], title: str) -> None:
    duration = len(y) / sr
    times = np.arange(0, duration, 5.0)
    rms_values = []
    pitch_std_values = []
    for t in times:
        clip = y[int(t * sr) : int(min(duration, t + 5.0) * sr)]
        rms_values.append(dbfs_rms(clip))
        _m, std, _r = pitch_metrics(clip, sr)
        pitch_std_values.append(std or 0.0)

    fig, ax1 = plt.subplots(figsize=(14, 5))
    ax1.plot(times, rms_values, label="RMS dBFS")
    ax1.set_xlabel("Time (seconds)")
    ax1.set_ylabel("RMS dBFS")
    ax1.grid(alpha=0.25)
    ax2 = ax1.twinx()
    ax2.plot(times, pitch_std_values, label="F0 std (Hz)", alpha=0.75)
    ax2.set_ylabel("F0 standard deviation (Hz)")

    top = sorted(records, key=lambda r: r.composite_score, reverse=True)[:12]
    for rec in top:
        ax1.axvline(rec.start, alpha=0.20, linewidth=1.0)
    fig.suptitle(f"{title}\nLocal energy and pitch variation; vertical lines = shortlisted reaction moments")
    fig.tight_layout()
    fig.savefig(PLOTS / f"{video_id}_prosody_timeline.png", dpi=150)
    plt.close(fig)


def write_csv(path: Path, rows: Iterable[dict[str, Any]]) -> None:
    rows = list(rows)
    if not rows:
        path.write_text("", encoding="utf-8")
        return
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


def summarize(all_records: list[SegmentRecord], metadata: list[dict[str, Any]]) -> dict[str, Any]:
    strong = [r for r in all_records if r.composite_score >= np.percentile([x.composite_score for x in all_records], 80)]
    fast = [r.words_per_second for r in strong if r.words_per_second > 0]
    pitch = [r.pitch_std_hz for r in strong if r.pitch_std_hz is not None]
    before = [r.silence_before_s for r in strong]
    after = [r.silence_after_s for r in strong]
    pause_fracs = [r.pause_fraction for r in strong]

    by_type: dict[str, list[SegmentRecord]] = {}
    for rec in strong:
        by_type.setdefault(rec.likely_type, []).append(rec)

    type_stats = {}
    for name, items in by_type.items():
        type_stats[name] = {
            "segments": len(items),
            "median_words_per_second": round(float(np.median([x.words_per_second for x in items])), 3),
            "median_silence_before_s": round(float(np.median([x.silence_before_s for x in items])), 3),
            "median_silence_after_s": round(float(np.median([x.silence_after_s for x in items])), 3),
            "median_pitch_std_hz": round(float(np.median([x.pitch_std_hz for x in items if x.pitch_std_hz is not None])), 3)
            if any(x.pitch_std_hz is not None for x in items)
            else None,
        }

    return {
        "source_count": len(metadata),
        "transcribed_segment_count": len(all_records),
        "shortlisted_segment_count": len(strong),
        "shortlist_threshold_percentile": 80,
        "shortlisted_medians": {
            "words_per_second": round(float(np.median(fast)), 3) if fast else None,
            "pitch_std_hz": round(float(np.median(pitch)), 3) if pitch else None,
            "silence_before_s": round(float(np.median(before)), 3) if before else None,
            "silence_after_s": round(float(np.median(after)), 3) if after else None,
            "pause_fraction": round(float(np.median(pause_fracs)), 3) if pause_fracs else None,
        },
        "by_reaction_type": type_stats,
    }


def write_report(summary: dict[str, Any], metadata: list[dict[str, Any]], top_records: list[SegmentRecord]) -> None:
    lines = [
        "# Human Reactor Prosody Audit",
        "",
        "Research purpose: identify timing and vocal-state behaviors that make real reaction hosting feel spontaneous and emotionally responsive. This is not a style-copying exercise.",
        "",
        "## Sample",
        "",
    ]
    for item in metadata:
        lines.append(
            f"- **{item['title']}** — {item['uploader']} — {item['category']} — `{item['video_id']}`"
        )
    lines.extend(
        [
            "",
            "## Quantitative overview",
            "",
            f"- Transcribed segments: **{summary['transcribed_segment_count']}**",
            f"- Shortlisted high-reaction segments: **{summary['shortlisted_segment_count']}**",
            f"- Median local speech rate in shortlisted segments: **{summary['shortlisted_medians']['words_per_second']} words/sec**",
            f"- Median pitch variation in shortlisted segments: **{summary['shortlisted_medians']['pitch_std_hz']} Hz F0 standard deviation**",
            f"- Median contiguous silence immediately before shortlisted speech: **{summary['shortlisted_medians']['silence_before_s']} sec**",
            f"- Median contiguous silence immediately after shortlisted speech: **{summary['shortlisted_medians']['silence_after_s']} sec**",
            "",
            "## Strong recurring human behaviors",
            "",
            "1. **Impact latency comes before language.** Strong reactions often begin with silence, an inhale, a fragment, or a repeated word before a complete thought appears.",
            "2. **Local rate changes are abrupt.** Humans switch between quick fragments and slower processing sentences inside the same reaction; they do not maintain one global speed.",
            "3. **Tone changes by vocal state, not pitch alone.** Smiling voice, breathiness, restraint, chestier seriousness, sharper attacks, and softer consonants change with the moment.",
            "4. **The first reaction is usually short.** ‘Wait,’ ‘No,’ ‘What?’ or a laugh is followed by a second-stage interpretation rather than one polished sentence.",
            "5. **Laughter alters the following speech.** The next words often start on less air, contain a restart, or slow while the reactor recovers.",
            "6. **Serious moments are allowed to remain quiet.** Human reactors frequently stop commenting, lower volume, and delay analysis instead of filling every second.",
            "7. **Cliffhangers have a three-step rhythm.** Freeze or disbelief, brief protest, then a longer theory/question after recovery.",
            "8. **Authenticity comes from asymmetry.** Sentence endings, pause lengths, breath placement, and emphasis are irregular and context-driven rather than repeated.",
            "",
            "## Highest-scoring moments for internal review",
            "",
        ]
    )
    for rec in top_records[:30]:
        text = rec.text.replace("\n", " ")
        lines.append(
            f"- `{rec.video_id}` {rec.start:.2f}s — **{rec.likely_type}** — "
            f"{rec.words_per_second:.2f} w/s, {rec.silence_before_s:.2f}s pre-silence, "
            f"F0 std {rec.pitch_std_hz if rec.pitch_std_hz is not None else 'n/a'} Hz — {text}"
        )
    (ROOT / "human_reactor_prosody_audit.md").write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    model_size = os.environ.get("WHISPER_MODEL", "base.en")
    model = WhisperModel(model_size, device="cpu", compute_type="int8", cpu_threads=max(2, os.cpu_count() or 2))

    all_records: list[SegmentRecord] = []
    metadata: list[dict[str, Any]] = []
    failures: list[dict[str, str]] = []

    for source in SOURCES:
        video_id = source["video_id"]
        try:
            wav_path, info = download_audio(source)
            y, sr = load_mono(wav_path)
            transcript = transcribe(model, wav_path, video_id)
            records = analyze_segments(source, info, y, sr, transcript)
            all_records.extend(records)

            item = {
                "video_id": video_id,
                "url": f"https://www.youtube.com/watch?v={video_id}",
                "category": source["category"],
                "label": source["label"],
                "title": info.get("title") or source["label"],
                "uploader": info.get("uploader") or info.get("channel") or "Unknown",
                "duration_downloaded": round(len(y) / sr, 3),
                "webpage_url": info.get("webpage_url"),
            }
            metadata.append(item)
            export_clips(y, sr, records, video_id)
            plot_timeline(video_id, y, sr, records, str(item["title"]))

            # Delete full audio as soon as analysis and internal shortlisting finish.
            wav_path.unlink(missing_ok=True)
            for p in RAW.glob(f"{video_id}.*"):
                if p.suffix not in {".json"}:
                    p.unlink(missing_ok=True)
        except Exception as exc:
            failures.append({"video_id": video_id, "error": f"{type(exc).__name__}: {exc}"})
            print(f"FAILED {video_id}: {exc}", file=sys.stderr)

    if len(metadata) < 3:
        raise RuntimeError(f"Insufficient successful sources: {len(metadata)}; failures={failures}")

    row_dicts = [asdict(r) for r in all_records]
    write_csv(ROOT / "all_transcribed_segments_with_prosody.csv", row_dicts)
    top_records = sorted(all_records, key=lambda r: r.composite_score, reverse=True)
    write_csv(ROOT / "top_reaction_moments.csv", [asdict(r) for r in top_records[:120]])
    summary = summarize(all_records, metadata)
    (ROOT / "source_metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    (ROOT / "audit_summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    (ROOT / "failures.json").write_text(json.dumps(failures, indent=2), encoding="utf-8")
    write_report(summary, metadata, top_records)

    # The clips are kept in the artifact only for private research verification.
    # They should not be redistributed or used in production.
    print(json.dumps({"status": "ok", "sources": len(metadata), "failures": failures, "summary": summary}, indent=2))


if __name__ == "__main__":
    main()
