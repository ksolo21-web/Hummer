#!/usr/bin/env python3
"""Render one strict Human-Reader quality patch for the full demo."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import soundfile as sf
import torch
from chatterbox.tts import ChatterboxTTS

import render_full_demo_human_reader_audio as base

PATCHES = {
    "story04": {
        "section": "story", "index": 4, "mode": "narrator_mystery", "pause": 0.52,
        "pieces": ["A voice rises...", "from the alley."],
        "intent": "Suspense needs processing time rather than a fast information read.",
    },
    "story23": {
        "section": "story", "index": 23, "mode": "nia_action", "pause": 0.0,
        "pieces": ["Stay on my heels!"],
        "intent": "Replace an implausibly clipped action line with a complete urgent performance.",
        "minimum_seconds": 0.80,
    },
    "story34": {
        "section": "story", "index": 34, "mode": "narrator_tender", "pause": 0.62,
        "pieces": ["The price takes the memory...", "of the voice that once guided her."],
        "intent": "The emotional cost must land in two thoughts with genuine silence between them.",
    },
    "recap03": {
        "section": "recap", "index": 3, "mode": "host_sincere", "pause": 0.48,
        "pieces": ["But Nia paying with the memory...", "of her mother's voice?"],
        "intent": "Slow the emotionally difficult realization and let the final words carry the weight.",
    },
    "recap06": {
        "section": "recap", "index": 6, "mode": "host_mystery", "pause": 0.46,
        "pieces": ["And then Orin walks in...", "and says her mother erased herself... to keep the sun from finding her."],
        "intent": "Separate the interruption from the reveal so Ivy processes what Orin said.",
    },
    "recap09": {
        "section": "recap", "index": 9, "mode": "host_mystery", "pause": 0.44,
        "pieces": ["But I don't think he's lying about everything...", "and somehow, that makes him worse."],
        "intent": "Allow Ivy to reason through the contradiction instead of rushing to the conclusion.",
    },
    "recap11": {
        "section": "recap", "index": 11, "mode": "host_mystery", "pause": 0.42,
        "pieces": ["I think it reconnects people...", "to whatever the Registry tried to erase.", "And Nia may be part of that."],
        "intent": "Present the theory as three developing thoughts rather than one polished statement.",
    },
    "recap13": {
        "section": "recap", "index": 13, "mode": "host_direct", "pause": 0.38,
        "pieces": ["Tell me your take in the comments...", "because I already have way too many theories."],
        "intent": "Speak directly to one viewer and let the playful admission arrive after the invitation.",
    },
    "recap14": {
        "section": "recap", "index": 14, "mode": "host_sincere", "pause": 0.54,
        "pieces": ["And since this was our very first trip beyond the panel...", "thank you for being here."],
        "intent": "Give the first-video gratitude room to feel personal and unpolished.",
    },
}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--patch-key", choices=sorted(PATCHES), required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    patch = PATCHES[args.patch_key]
    out = args.output_dir.resolve(); out.mkdir(parents=True, exist_ok=True)
    refs = out / "refs"; refs.mkdir(exist_ok=True)
    base.OUT = out; base.REFS = refs
    base.set_seed(base.BASE_SEED + 88000)
    torch.set_num_threads(max(1, min(8, torch.get_num_threads())))
    mode_refs = base.build_mode_references()
    model = ChatterboxTTS.from_pretrained(device="cpu")
    mode = base.MODES[patch["mode"]]

    pieces=[]; records=[]
    for pidx, text in enumerate(patch["pieces"], start=1):
        candidates=[]
        attempts = 3 if patch.get("minimum_seconds") and len(patch["pieces"]) == 1 else 1
        for cidx in range(attempts):
            y, meta = base.generate_unit(
                model, text, mode, mode_refs[patch["mode"]],
                base.BASE_SEED + 88000 + pidx * 1000 + cidx * 173,
            )
            candidates.append((y, meta))
        minimum = float(patch.get("minimum_seconds", 0.0))
        valid=[x for x in candidates if x[1]["duration_seconds"] >= minimum]
        y, meta = max(valid or candidates, key=lambda x: x[1]["duration_seconds"])
        pieces.append(y)
        records.append({"text": text, **meta})

    pause = float(patch["pause"])
    assembled=[]
    for idx,y in enumerate(pieces):
        assembled.append(y)
        if idx < len(pieces)-1 and pause>0:
            assembled.append(np.zeros(int(round(pause*base.MASTER_SR)), dtype=np.float32))
    master=np.concatenate(assembled)
    master,gain=base.scalar_peak(master,-2.6)
    wav=out/f"{args.patch_key}.wav"; sf.write(wav,master,base.MASTER_SR,subtype="PCM_24")
    manifest={**patch,"patch_key":args.patch_key,"file":str(wav),"duration_seconds":round(len(master)/base.MASTER_SR,6),"master_gain_db":round(gain,3),"piece_records":records,"post_effects":[]}
    (out/"patch_manifest.json").write_text(json.dumps(manifest,indent=2,ensure_ascii=False),encoding="utf-8")
    print(json.dumps(manifest,indent=2))

if __name__ == "__main__": main()
