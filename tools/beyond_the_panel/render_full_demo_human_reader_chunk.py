#!/usr/bin/env python3
"""Parallel chunk renderer for the Beyond the Panel full Human-Reader demo."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import soundfile as sf
import torch
from chatterbox.tts import ChatterboxTTS

import render_full_demo_human_reader_audio as base


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--section", choices=["host", "story", "recap"], required=True)
    parser.add_argument("--story-start", type=int, default=1)
    parser.add_argument("--story-end", type=int, default=47)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    out = args.output_dir.resolve()
    out.mkdir(parents=True, exist_ok=True)
    refs_dir = out / "mode_references"
    host_dir = out / "host_intro_units"
    story_dir = out / "story_lines"
    recap_dir = out / "recap_units"
    for d in (refs_dir, host_dir, story_dir, recap_dir):
        d.mkdir(parents=True, exist_ok=True)

    # Redirect imported module output paths into this chunk artifact.
    base.OUT = out
    base.REFS = refs_dir
    base.HOST_LINES = host_dir
    base.STORY_LINES = story_dir
    base.RECAP_LINES = recap_dir

    base.set_seed(base.BASE_SEED)
    torch.set_num_threads(max(1, min(8, torch.get_num_threads())))
    refs = base.build_mode_references()
    model = ChatterboxTTS.from_pretrained(device="cpu")

    chunk: dict = {
        "status": "passed",
        "section": args.section,
        "story_start": args.story_start,
        "story_end": args.story_end,
        "engine": "ChatterboxTTS conditioned on Kokoro af_heart mode references",
        "voice_identity": "Ivy / af_heart",
        "sample_rate_hz": base.MASTER_SR,
        "channels": 1,
        "mode_references": {k: str(v) for k, v in refs.items()},
    }

    if args.section == "host":
        temp_manifest = {"host_intro_units": []}
        master, timeline = base.render_units(
            model,
            base.HOST_INTRO,
            refs,
            host_dir,
            "host_intro_units",
            temp_manifest,
            base.BASE_SEED + 10000,
        )
        master_path = out / "Ivy_First_Video_Intro_Human_Reader_48k_PCM24.wav"
        sf.write(master_path, master, base.MASTER_SR, subtype="PCM_24")
        chunk.update({
            "host_intro_master": str(master_path),
            "host_intro_duration_seconds": round(len(master) / base.MASTER_SR, 6),
            "host_intro_units": timeline,
        })
    elif args.section == "recap":
        temp_manifest = {"recap_units": []}
        master, timeline = base.render_units(
            model,
            base.RECAP,
            refs,
            recap_dir,
            "recap_units",
            temp_manifest,
            base.BASE_SEED + 50000,
        )
        master_path = out / "Ivy_Post_Chapter_Recap_Human_Reader_48k_PCM24.wav"
        sf.write(master_path, master, base.MASTER_SR, subtype="PCM_24")
        chunk.update({
            "recap_master": str(master_path),
            "recap_duration_seconds": round(len(master) / base.MASTER_SR, 6),
            "recap_units": timeline,
        })
    else:
        selected = [
            item for item in base.STORY
            if args.story_start <= item.index <= args.story_end
        ]
        records = []
        for item in selected:
            mode = base.MODES[item.mode]
            y, meta = base.generate_unit(
                model,
                item.text,
                mode,
                refs[item.mode],
                base.BASE_SEED + 30000 + item.index * 103,
            )
            path = story_dir / f"{item.index:02d}_{item.role.lower()}.wav"
            sf.write(path, y, base.MASTER_SR, subtype="PCM_24")
            records.append({
                "index": item.index,
                "role": item.role,
                "text": item.text,
                "mode": item.mode,
                "intent": item.intent,
                "original_start": item.original_start,
                "original_slot_end": item.original_slot_end,
                "original_slot_duration": round(item.original_slot_end - item.original_start, 6),
                "file": str(path),
                **meta,
            })
            print(json.dumps({"section": "story", "index": item.index, "duration": meta["duration_seconds"]}), flush=True)
        chunk["story_lines"] = records

    (out / "chunk_manifest.json").write_text(json.dumps(chunk, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({"status": "passed", "section": args.section, "count": len(chunk.get("story_lines", []))}, indent=2))


if __name__ == "__main__":
    main()
