#!/usr/bin/env python3
"""Render all Ivy C2 Human-Reader audio for the full Beyond the Panel demo.

This production render creates:
- Ivy's special first-video host introduction;
- all 47 spoken lines for The Unmapped Sun sample chapter;
- Ivy's personalized post-chapter recap and take.

The delivery is built from thought units, not long paragraphs. Every unit has a
meaning-driven vocal posture, its own Chatterbox performance parameters, and a
processing pause. Tone, pitch behavior, pace, rhythm, breath state, and energy
therefore change with the thought instead of remaining uniform.

No reverb, echo, room simulation, delay, widening, doubling, chorus, EQ,
compression, ambience, post pitch shifting, or post time stretching is used.
Allowed post operations are silence trim, 48 kHz resampling, scalar gain,
timeline placement, and 5 ms anti-click fades.
"""
from __future__ import annotations

import gc
import json
import math
import random
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

import numpy as np
import soundfile as sf
import torch
from scipy.signal import resample_poly

from chatterbox.tts import ChatterboxTTS
from kokoro import KPipeline

OUT = Path("out")
REFS = OUT / "mode_references"
HOST_LINES = OUT / "host_intro_units"
STORY_LINES = OUT / "story_lines"
RECAP_LINES = OUT / "recap_units"
for directory in (OUT, REFS, HOST_LINES, STORY_LINES, RECAP_LINES):
    directory.mkdir(parents=True, exist_ok=True)

MASTER_SR = 48_000
KOKORO_SR = 24_000
VOICE = "af_heart"
BASE_SEED = 260827


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
    anchor_texts: tuple[tuple[str, float], ...]
    posture: str
    style: Style


@dataclass(frozen=True)
class Unit:
    key: str
    text: str
    mode: str
    intent: str
    pause_after: float


@dataclass(frozen=True)
class StoryLine:
    index: int
    role: str
    text: str
    mode: str
    intent: str
    original_start: float
    original_slot_end: float


MODES: dict[str, Mode] = {
    "host_bright": Mode(
        "host_bright",
        (("Hi! Okay—this is actually happening.", 1.05), ("I cannot believe we are finally here.", 1.02)),
        "Bright, spontaneous, excited, slightly adorably nervous; wide but believable pitch movement.",
        Style(0.72, 0.28, 0.86),
    ),
    "host_playful": Mode(
        "host_playful",
        (("No, because that was actually funny.", 1.02), ("I support you. I question your choices.", 0.98)),
        "Smiling, playful, rhythmically varied, affectionate, and conversational rather than announcer-like.",
        Style(0.74, 0.27, 0.87),
    ),
    "host_warm": Mode(
        "host_warm",
        (("I am really glad you are here with me.", 0.89), ("Some moments deserve a little more space.", 0.87)),
        "Warm, intimate, one-to-one, softened onset, unhurried and sincere.",
        Style(0.42, 0.26, 0.74),
    ),
    "host_sincere": Mode(
        "host_sincere",
        (("This means more to me than I expected.", 0.85), ("Thank you for being here at the beginning.", 0.84)),
        "Lower energy, narrow melody, emotionally truthful, no polished emotional flourish.",
        Style(0.30, 0.22, 0.68),
    ),
    "host_mystery": Mode(
        "host_mystery",
        (("Something is missing, and no one wants to explain why.", 0.90), ("The answer is waiting behind the next page.", 0.88)),
        "Controlled curiosity, darker resonance, deliberate key words, cinematic but close.",
        Style(0.50, 0.24, 0.78),
    ),
    "host_direct": Mode(
        "host_direct",
        (("Tell me what you think. I am listening.", 0.91), ("I want to hear your theory.", 0.90)),
        "Direct-to-viewer, close, calm, genuinely conversational, no performance projection.",
        Style(0.36, 0.24, 0.70),
    ),
    "title": Mode(
        "title",
        (("The story begins here.", 0.90), ("The Unmapped Sun. The Eleventh Key.", 0.88)),
        "Clean, confident title read with restrained cinematic weight.",
        Style(0.42, 0.28, 0.73),
    ),
    "narrator_cinematic": Mode(
        "narrator_cinematic",
        (("A city waits beneath an impossible sky.", 0.91), ("The road ahead has already begun to change.", 0.90)),
        "Warm close narration, grounded and cinematic; meaning-driven pauses rather than trailer cadence.",
        Style(0.43, 0.27, 0.74),
    ),
    "narrator_action": Mode(
        "narrator_action",
        (("The street closes around them.", 1.02), ("The chase tears across the rooftops.", 1.04)),
        "Forward momentum, sharper attacks, faster connective words, still intelligible.",
        Style(0.64, 0.28, 0.83),
    ),
    "narrator_tender": Mode(
        "narrator_tender",
        (("The memory leaves before she can hold it.", 0.84), ("Some losses arrive without a sound.", 0.82)),
        "Soft, lower, restrained; longer vowels and honest silence around emotional meaning.",
        Style(0.28, 0.21, 0.67),
    ),
    "narrator_mystery": Mode(
        "narrator_mystery",
        (("Beyond the bridge, a hidden ruin waits.", 0.88), ("Something ancient is still awake.", 0.86)),
        "Measured suspense, darker posture, controlled pitch range, quiet anticipation.",
        Style(0.48, 0.23, 0.76),
    ),
    "narrator_cliff": Mode(
        "narrator_cliff",
        (("Then everything changes.", 0.84), ("The answer arrives too late.", 0.82)),
        "Slower and consequential, with processing space; never overdramatic.",
        Style(0.50, 0.20, 0.76),
    ),
    "nia_action": Mode(
        "nia_action",
        (("Move. Stay close to me.", 1.08), ("Run. Do not look back.", 1.10)),
        "Firm, alert, protective, urgent; crisp word attacks and controlled breath.",
        Style(0.72, 0.29, 0.85),
    ),
    "nia_guarded": Mode(
        "nia_guarded",
        (("I do not believe you. Show me.", 0.93), ("I am listening, but I do not trust this.", 0.91)),
        "Guarded, skeptical, restrained; lower endings and deliberate emphasis.",
        Style(0.40, 0.23, 0.70),
    ),
    "nia_tender": Mode(
        "nia_tender",
        (("Do not waste what I gave you.", 0.86), ("I am trying not to let this hurt.", 0.84)),
        "Emotionally guarded but softened; low energy, intimate, no melodrama.",
        Style(0.27, 0.20, 0.66),
    ),
    "nia_shock": Mode(
        "nia_shock",
        (("Wait. Who said that?", 0.96), ("My mother?", 0.82)),
        "Breath interruption, short first response, uneven recovery, genuine disbelief.",
        Style(0.68, 0.22, 0.84),
    ),
    "sori_urgent": Mode(
        "sori_urgent",
        (("Nia! You have to listen to me.", 1.06), ("They erased the whole street.", 1.04)),
        "Urgent, emotionally transparent, thoughts arriving faster than breath settles.",
        Style(0.72, 0.27, 0.85),
    ),
    "sori_vulnerable": Mode(
        "sori_vulnerable",
        (("My grandmother looked at me like I was a stranger.", 0.87), ("Please tell me you believe me.", 0.86)),
        "Vulnerable, exposed, softer attacks and uneven breath; no theatrical sadness.",
        Style(0.38, 0.21, 0.70),
    ),
    "sori_hurt": Mode(
        "sori_hurt",
        (("Grandma... it is me.", 0.82), ("No. Please.", 0.78)),
        "Hurt, quiet disbelief, reduced energy and more silence than melody.",
        Style(0.24, 0.18, 0.65),
    ),
    "orin_command": Mode(
        "orin_command",
        (("Seal the block.", 0.84), ("Hand over the chart.", 0.83)),
        "Restrained authority; lower, firm, certain, never shouted.",
        Style(0.38, 0.17, 0.67),
    ),
    "orin_cold": Mode(
        "orin_cold",
        (("I know exactly who you are.", 0.82), ("You have done this before.", 0.81)),
        "Cold, deliberate, minimal melodic movement, quiet control.",
        Style(0.30, 0.16, 0.64),
    ),
    "orin_reveal": Mode(
        "orin_reveal",
        (("Then your mother failed.", 0.80), ("She erased herself to protect you.", 0.78)),
        "Low, consequential reveal, restrained cruelty, silence around the key fact.",
        Style(0.36, 0.15, 0.66),
    ),
    "grandma_gentle": Mode(
        "grandma_gentle",
        (("Can I help you, young man?", 0.84), ("I am sorry. I do not know you.", 0.82)),
        "Gentle and matter-of-fact, warm but unfamiliar; no caricatured age voice.",
        Style(0.22, 0.22, 0.64),
    ),
    "compass_deliberate": Mode(
        "compass_deliberate",
        (("What will you pay?", 0.76), ("The price has been accepted.", 0.74)),
        "Calm, deliberate, uncanny through timing and certainty only; no acoustic effects.",
        Style(0.26, 0.15, 0.64),
    ),
    "sun_intimate": Mode(
        "sun_intimate",
        (("Nia... I remember your name.", 0.76), ("The first sunrise has been waiting for you.", 0.75)),
        "Intimate, ancient, controlled, emotionally close; no reverb or breathy cliché.",
        Style(0.27, 0.18, 0.66),
    ),
}


HOST_INTRO: list[Unit] = [
    Unit("h01", "Hi! Okay—this is actually happening.", "host_bright", "The launch catches her with real excitement before she organizes the thought.", 0.46),
    Unit("h02", "This is kind of a big moment for me.", "host_sincere", "Energy settles; she admits the personal significance without performing sentiment.", 0.48),
    Unit("h03", "I'm Ivy... and welcome to the very first video here on Beyond the Panel.", "host_direct", "A direct, warm introduction to one viewer.", 0.42),
    Unit("h04", "I'll be your host, your narrator, and your guide through every world we open together.", "host_warm", "Confident and inviting; steady but not announcer-like.", 0.36),
    Unit("h05", "And—somehow—I'll also be every character we meet along the way.", "host_playful", "Playful private aside on somehow; she realizes the scale of the job.", 0.40),
    Unit("h06", "So yes... I may go from a fearless hero, to a terrifying villain, to somebody's sweet grandmother in the same five minutes.", "host_playful", "Build the contrast naturally; affectionate amusement, not a scripted joke read.", 0.46),
    Unit("h07", "Yeah. We're going to have fun.", "host_playful", "A small amused recovery and genuine anticipation.", 0.54),
    Unit("h08", "But that is what I love about stories.", "host_warm", "The joke settles into a sincere thought without resetting to neutral.", 0.34),
    Unit("h09", "We get to step into these worlds together... meet people we'll probably become way too attached to... and feel every win, every loss, and every terrible decision right along with them.", "host_warm", "Expansive but intimate; pace changes around the emotional list.", 0.52),
    Unit("h10", "And I will probably be yelling at the page right along with you.", "host_playful", "Conspiratorial and friendly; smile through the admission.", 0.62),
    Unit("h11", "For our first trip beyond the panel, we're opening a full-color sample story called The Unmapped Sun.", "host_mystery", "Shift into story setup; controlled curiosity and cinematic scale.", 0.42),
    Unit("h12", "Ten artificial suns watch over the city of Tenfold... but an entire street has vanished, and the people connected to it are disappearing from memory.", "host_mystery", "The mystery deepens; slow around vanished and disappearing from memory.", 0.52),
    Unit("h13", "This is Sample Chapter One: The Eleventh Key.", "title", "Clear chapter identification without metadata cadence.", 0.52),
    Unit("h14", "I'm really glad you're here at the beginning.", "host_sincere", "Personal gratitude, soft and unpolished.", 0.46),
    Unit("h15", "All right... let's open the first page.", "host_warm", "Warm handoff into the comic; inviting, not a slogan.", 0.0),
]


STORY: list[StoryLine] = [
    StoryLine(1, "TITLE", "The Unmapped Sun... Sample chapter: The Eleventh Key.", "title", "Measured title read with a real pause before the chapter name.", 0.48, 5.632),
    StoryLine(2, "NARRATOR", "Tenfold. A city held beneath ten artificial suns... But this morning, one street has vanished.", "narrator_cinematic", "Establish scale, then slow and darken as the missing street is revealed.", 5.712, 13.332),
    StoryLine(3, "NIA", "Move, Piko!", "nia_action", "Immediate protective urgency.", 13.412, 16.132),
    StoryLine(4, "NARRATOR", "A voice rises from the alley...", "narrator_mystery", "Quiet anticipation; leave the thought suspended.", 16.212, 18.387),
    StoryLine(5, "SORI", "Nia!", "sori_urgent", "A breathless call for help.", 18.467, 20.520),
    StoryLine(6, "SORI", "They erased Eelbone Street!", "sori_urgent", "Disbelief and urgency, not clean exposition.", 20.600, 24.220),
    StoryLine(7, "NIA", "Streets... don't vanish.", "nia_guarded", "Skepticism slows into uncertainty.", 24.300, 27.220),
    StoryLine(8, "SORI", "My grandma... forgot me.", "sori_vulnerable", "The personal cost is difficult to say aloud.", 27.300, 29.373),
    StoryLine(9, "NIA", "Show me.", "nia_guarded", "Decision replaces doubt; low and direct.", 29.453, 33.070),
    StoryLine(10, "NARRATOR", "At his grandmother's door... the impossible becomes personal.", "narrator_tender", "Slow, emotionally restrained transition into the loss.", 33.150, 38.580),
    StoryLine(11, "GRANDMA", "Can I help you, young man?", "grandma_gentle", "Kind but genuinely unfamiliar.", 38.660, 41.386),
    StoryLine(12, "SORI", "Grandma... it's me.", "sori_hurt", "Hope collapses while he speaks.", 41.466, 44.980),
    StoryLine(13, "SORI", "No...", "sori_hurt", "Nearly breathless disbelief; let silence do the rest.", 45.060, 47.980),
    StoryLine(14, "NIA", "Where did you get it?", "nia_guarded", "Focused, alert question after emotional shock.", 48.060, 49.489),
    StoryLine(15, "SORI", "Her sewing box.", "sori_vulnerable", "Quiet answer; still emotionally shaken.", 49.569, 53.180),
    StoryLine(16, "NARRATOR", "Before Nia can examine the eleven-toothed key... the Registry arrives.", "narrator_action", "Build quickly, then land Registry arrives as a threat.", 53.260, 57.230),
    StoryLine(17, "ORIN", "Hand over the illegal chart.", "orin_command", "Low, certain command; no shouting.", 57.310, 61.027),
    StoryLine(18, "NIA", "Run!", "nia_action", "Explosive protective command.", 61.107, 62.009),
    StoryLine(19, "ORIN", "Courier... Nia Sable.", "orin_cold", "Recognition with a deliberate pause; he knows more than he should.", 62.089, 66.027),
    StoryLine(20, "NARRATOR", "The street closes around them.", "narrator_action", "Tight, immediate threat.", 66.107, 70.582),
    StoryLine(21, "NARRATOR", "Warden Orin seals the block.", "narrator_action", "Controlled escalation.", 70.662, 74.582),
    StoryLine(22, "ORIN", "Seal the block.", "orin_command", "Absolute, restrained authority.", 74.662, 77.782),
    StoryLine(23, "NIA", "Stay on my heels!", "nia_action", "Fast, urgent protection during movement.", 77.862, 81.182),
    StoryLine(24, "NARRATOR", "At the roof's edge, Nia draws across empty air.", "narrator_action", "Momentum into impossible action; slow slightly on empty air.", 81.262, 84.802),
    StoryLine(25, "NIA", "Roads... listen.", "nia_guarded", "Quiet certainty; this is practiced, not theatrical.", 84.882, 87.314),
    StoryLine(26, "SORI", "That jump is impossible!", "sori_urgent", "Fear arrives before the sentence is organized.", 87.394, 89.502),
    StoryLine(27, "NIA", "Good.", "nia_guarded", "Dry confidence; very little melody.", 89.582, 92.914),
    StoryLine(28, "NARRATOR", "The forbidden line... rejects the Registry.", "narrator_action", "Impact and reversal, with a pause before rejects.", 92.994, 97.319),
    StoryLine(29, "ORIN", "She opened another forbidden line.", "orin_cold", "Disturbed recognition hidden beneath control.", 97.399, 102.769),
    StoryLine(30, "NARRATOR", "Beyond the impossible bridge... a hidden ruin waits.", "narrator_mystery", "Pace opens; suspense replaces action.", 102.849, 107.369),
    StoryLine(31, "NIA", "Blank Compass. One question.", "nia_guarded", "Careful ritual confidence; separated thoughts.", 107.449, 111.569),
    StoryLine(32, "COMPASS", "What... will you pay?", "compass_deliberate", "Unhurried and certain; uncanny only through timing.", 111.649, 116.349),
    StoryLine(33, "NIA", "A memory.", "nia_tender", "Decision made, but the cost is real.", 115.949, 119.169),
    StoryLine(34, "NARRATOR", "The price takes the memory... of the voice that once guided her.", "narrator_tender", "Allow the loss to land; no rush through the emotional phrase.", 119.249, 123.139),
    StoryLine(35, "COMPASS", "Accepted.", "compass_deliberate", "Final and emotionless without becoming robotic.", 122.919, 126.429),
    StoryLine(36, "SORI", "You paid for me?", "sori_vulnerable", "Surprise, guilt, and gratitude arrive together.", 126.029, 127.629),
    StoryLine(37, "NIA", "Don't waste it.", "nia_tender", "Guarded tenderness; low, brief, personal.", 127.729, 132.129),
    StoryLine(38, "NARRATOR", "An eleventh road rises... toward a sealed sun.", "narrator_mystery", "Wonder and danger in equal measure.", 132.229, 138.119),
    StoryLine(39, "NARRATOR", "At the end of the hidden road... ancient machinery cages a living sunrise.", "narrator_mystery", "Slow reveal with awe and unease.", 137.719, 143.489),
    StoryLine(40, "SUN", "Nia...", "sun_intimate", "Ancient recognition, intimate and controlled.", 143.569, 146.789),
    StoryLine(41, "NIA", "Who said that?", "nia_shock", "Startled, searching, real breath interruption.", 146.869, 150.189),
    StoryLine(42, "SUN", "The first sunrise... remembers your name.", "sun_intimate", "Slow, personal revelation; no atmospheric effect.", 150.269, 155.989),
    StoryLine(43, "NARRATOR", "Then... Orin arrives.", "narrator_cliff", "The intrusion changes the emotional temperature.", 156.069, 157.589),
    StoryLine(44, "ORIN", "Then your mother failed.", "orin_reveal", "Low, cruel certainty; let mother and failed land separately.", 157.669, 161.869),
    StoryLine(45, "ORIN", "She erased herself... to keep the sun from finding you.", "orin_reveal", "Consequential reveal with controlled pauses, never rushed.", 161.469, 165.522),
    StoryLine(46, "NIA", "My mother?", "nia_shock", "The words barely form as the implication lands.", 165.622, 169.489),
    StoryLine(47, "NARRATOR", "To be continued...", "narrator_cliff", "Warm but unresolved closing; no commercial announcer cadence.", 169.569, 172.925),
]


RECAP: list[Unit] = [
    Unit("r01", "Okay... wait. We need to talk about that ending.", "host_bright", "Immediate real reaction; surprise first, then processing.", 0.56),
    Unit("r02", "Sori's own grandmother forgetting him was already cruel.", "host_sincere", "Quiet emotional acknowledgment; do not rush past it.", 0.48),
    Unit("r03", "But Nia paying with the memory of her mother's voice?", "host_sincere", "The question carries disbelief and hurt, not rhetorical polish.", 0.62),
    Unit("r04", "Yeah... that was the moment that got me.", "host_sincere", "Small pause before admitting the emotional impact.", 0.64),
    Unit("r05", "She acts like every cost is hers to carry.", "host_warm", "Thoughtful character observation, warm but worried.", 0.38),
    Unit("r06", "And then Orin walks in and says her mother erased herself to keep the sun from finding her.", "host_mystery", "Build toward the reveal, then slow around erased herself and finding her.", 0.60),
    Unit("r07", "Sir... what exactly are we supposed to do with that information?", "host_playful", "Playful disbelief emerges from the shock, not a canned joke.", 0.66),
    Unit("r08", "Also, I do not trust Orin. At all.", "host_playful", "Dry suspicion; the final words land lower and firmer.", 0.48),
    Unit("r09", "But I don't think he's lying about everything... and somehow, that makes him worse.", "host_mystery", "Reason through the contradiction; lower and slower on makes him worse.", 0.70),
    Unit("r10", "My theory? The eleventh key does not just open hidden roads.", "host_direct", "Clearly signal that this is a theory, then separate the first conclusion.", 0.44),
    Unit("r11", "I think it reconnects people to whatever the Registry tried to erase... and Nia may be part of that.", "host_mystery", "Speculative and thoughtful, not omniscient; let the final possibility hang.", 0.72),
    Unit("r12", "What hit you hardest—Sori being forgotten, Nia's sacrifice, or that final reveal about her mother?", "host_direct", "Ask one viewer a genuine question and leave room for an answer.", 0.54),
    Unit("r13", "Tell me your take in the comments, because I already have way too many theories.", "host_playful", "Friendly, self-aware, genuinely curious, not generic engagement language.", 0.64),
    Unit("r14", "And since this was our very first trip beyond the panel... thank you for being here.", "host_sincere", "Personal gratitude with emotional space.", 0.46),
    Unit("r15", "I'm Ivy. I'll see you in the next story.", "host_warm", "Sweet, close, confident goodbye; no trailer cadence.", 0.0),
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


def scalar_peak(y: np.ndarray, ceiling_dbfs: float = -2.4) -> tuple[np.ndarray, float]:
    peak = float(np.max(np.abs(y)) + 1e-12)
    allowed = 10 ** (ceiling_dbfs / 20)
    gain = min(1.0, allowed / peak)
    return (y * gain).astype(np.float32), 20 * math.log10(max(gain, 1e-12))


def word_count(text: str) -> int:
    return len(re.findall(r"\b[\w']+\b", text.replace("Mm-hmm", "Mm hmm")))


def synth_kokoro(pipeline: KPipeline, text: str, speed: float) -> np.ndarray:
    chunks: list[np.ndarray] = []
    for _g, _p, audio in pipeline(text, voice=VOICE, speed=speed):
        arr = np.asarray(audio, dtype=np.float32).reshape(-1)
        if arr.size:
            chunks.append(arr)
    if not chunks:
        raise RuntimeError(f"Kokoro produced no reference audio for {text!r}")
    y = trim(np.concatenate(chunks), KOKORO_SR)
    y = resample_poly(y, MASTER_SR, KOKORO_SR).astype(np.float32)
    y = anti_click(y)
    y, _ = scalar_peak(y, -3.0)
    return y


def build_mode_references() -> dict[str, Path]:
    pipeline = KPipeline(lang_code="a")
    refs: dict[str, Path] = {}
    manifest: dict[str, Any] = {}
    for mode_key, mode in MODES.items():
        parts: list[np.ndarray] = []
        for index, (text, speed) in enumerate(mode.anchor_texts):
            parts.append(synth_kokoro(pipeline, text, speed))
            if index < len(mode.anchor_texts) - 1:
                parts.append(np.zeros(int(round(0.18 * MASTER_SR)), dtype=np.float32))
        master = np.concatenate(parts)
        master, gain_db = scalar_peak(master, -3.0)
        path = REFS / f"Ivy_{mode_key}_reference_48k_PCM16.wav"
        sf.write(path, master, MASTER_SR, subtype="PCM_16")
        refs[mode_key] = path
        manifest[mode_key] = {
            "path": str(path),
            "posture": mode.posture,
            "style": asdict(mode.style),
            "anchors": [{"text": text, "speed": speed} for text, speed in mode.anchor_texts],
            "gain_db": round(gain_db, 3),
        }
    (OUT / "mode_reference_manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    return refs


def generate_unit(
    model: ChatterboxTTS,
    text: str,
    mode: Mode,
    reference_path: Path,
    seed: int,
) -> tuple[np.ndarray, dict[str, Any]]:
    last_error: Exception | None = None
    for attempt in range(1, 4):
        attempt_seed = seed + (attempt - 1) * 137
        set_seed(attempt_seed)
        try:
            with torch.inference_mode():
                wav = model.generate(
                    text,
                    audio_prompt_path=str(reference_path),
                    exaggeration=mode.style.exaggeration,
                    cfg_weight=mode.style.cfg_weight,
                    temperature=mode.style.temperature,
                    repetition_penalty=mode.style.repetition_penalty,
                    min_p=mode.style.min_p,
                    top_p=mode.style.top_p,
                )
            if torch.is_tensor(wav):
                y = wav.detach().cpu().float().numpy().reshape(-1)
            else:
                y = np.asarray(wav, dtype=np.float32).reshape(-1)
            if not np.isfinite(y).all() or y.size < int(0.18 * model.sr):
                raise RuntimeError("invalid or implausibly short output")
            y = trim(y, int(model.sr))
            y = resample_poly(y, MASTER_SR, int(model.sr)).astype(np.float32)
            y = anti_click(y)
            y, gain_db = scalar_peak(y, -2.8)
            return y, {
                "attempt": attempt,
                "seed": attempt_seed,
                "duration_seconds": round(len(y) / MASTER_SR, 6),
                "words": word_count(text),
                "speech_wpm": round(word_count(text) / (len(y) / MASTER_SR) * 60.0, 3),
                "gain_db": round(gain_db, 3),
                "post_effects": [],
            }
        except Exception as exc:
            last_error = exc
            gc.collect()
    raise RuntimeError(f"Failed to render {text!r}: {last_error}")


def save_checkpoint(manifest: dict[str, Any]) -> None:
    (OUT / "Ivy_Full_Demo_Human_Reader_Manifest.partial.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8"
    )


def render_units(
    model: ChatterboxTTS,
    units: list[Unit],
    refs: dict[str, Path],
    output_dir: Path,
    section_key: str,
    manifest: dict[str, Any],
    seed_base: int,
) -> tuple[np.ndarray, list[dict[str, Any]]]:
    parts: list[np.ndarray] = []
    timeline: list[dict[str, Any]] = []
    cursor = 0.0
    for index, unit in enumerate(units, start=1):
        mode = MODES[unit.mode]
        y, meta = generate_unit(model, unit.text, mode, refs[unit.mode], seed_base + index * 101)
        path = output_dir / f"{index:02d}_{unit.key}.wav"
        sf.write(path, y, MASTER_SR, subtype="PCM_24")
        start = cursor
        end = start + len(y) / MASTER_SR
        parts.append(y)
        if unit.pause_after > 0:
            parts.append(np.zeros(int(round(unit.pause_after * MASTER_SR)), dtype=np.float32))
        cursor = end + unit.pause_after
        item = {
            "index": index,
            "key": unit.key,
            "text": unit.text,
            "mode": unit.mode,
            "intent": unit.intent,
            "start": round(start, 6),
            "end": round(end, 6),
            "pause_after": unit.pause_after,
            "file": str(path),
            **meta,
        }
        timeline.append(item)
        manifest[section_key] = timeline
        save_checkpoint(manifest)
        print(json.dumps({"section": section_key, "index": index, "text": unit.text, "duration": meta["duration_seconds"]}))
    master = np.concatenate(parts) if parts else np.zeros(1, dtype=np.float32)
    master, gain_db = scalar_peak(master, -2.0)
    timeline.append({"section_master_gain_db": round(gain_db, 3)})
    return master, timeline


def render_story(
    model: ChatterboxTTS,
    refs: dict[str, Path],
    manifest: dict[str, Any],
) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for item in STORY:
        mode = MODES[item.mode]
        y, meta = generate_unit(model, item.text, mode, refs[item.mode], BASE_SEED + 30000 + item.index * 103)
        path = STORY_LINES / f"{item.index:02d}_{item.role.lower()}.wav"
        sf.write(path, y, MASTER_SR, subtype="PCM_24")
        record = {
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
        }
        records.append(record)
        manifest["story_lines"] = records
        save_checkpoint(manifest)
        print(json.dumps({"section": "story", "index": item.index, "role": item.role, "duration": meta["duration_seconds"]}))
    return records


def main() -> None:
    set_seed(BASE_SEED)
    torch.set_num_threads(max(1, min(8, torch.get_num_threads())))
    refs = build_mode_references()
    model = ChatterboxTTS.from_pretrained(device="cpu")

    manifest: dict[str, Any] = {
        "status": "rendering",
        "project": "Beyond the Panel — First Video Full Demo — Ivy C2 Human-Reader Pass",
        "sample_status": "The Unmapped Sun is a sample/proof-of-concept, not the official manga.",
        "engine": "ChatterboxTTS conditioned on fresh Kokoro af_heart mode references",
        "voice_identity": "Ivy / af_heart",
        "sample_rate_hz": MASTER_SR,
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
            "silence trim",
            "48 kHz polyphase resampling",
            "scalar gain",
            "thought-unit timeline placement",
            "5 ms anti-click fades",
        ],
        "mode_references": {key: str(path) for key, path in refs.items()},
        "host_intro_units": [],
        "story_lines": [],
        "recap_units": [],
    }
    save_checkpoint(manifest)

    host_master, host_timeline = render_units(
        model, HOST_INTRO, refs, HOST_LINES, "host_intro_units", manifest, BASE_SEED + 10000
    )
    host_path = OUT / "Ivy_First_Video_Intro_Human_Reader_48k_PCM24.wav"
    sf.write(host_path, host_master, MASTER_SR, subtype="PCM_24")

    story_records = render_story(model, refs, manifest)

    recap_master, recap_timeline = render_units(
        model, RECAP, refs, RECAP_LINES, "recap_units", manifest, BASE_SEED + 50000
    )
    recap_path = OUT / "Ivy_Post_Chapter_Recap_Human_Reader_48k_PCM24.wav"
    sf.write(recap_path, recap_master, MASTER_SR, subtype="PCM_24")

    manifest.update({
        "status": "passed",
        "host_intro_master": str(host_path),
        "host_intro_duration_seconds": round(len(host_master) / MASTER_SR, 6),
        "host_intro_units": host_timeline,
        "story_lines": story_records,
        "recap_master": str(recap_path),
        "recap_duration_seconds": round(len(recap_master) / MASTER_SR, 6),
        "recap_units": recap_timeline,
    })
    (OUT / "Ivy_Full_Demo_Human_Reader_Manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    (OUT / "Ivy_Full_Demo_Human_Reader_Manifest.partial.json").unlink(missing_ok=True)

    script = [
        "# Beyond the Panel — Full First-Video Demo — Ivy C2 Human-Reader Script",
        "",
        "## First-video introduction",
        "",
        *[f"- **{u.mode}:** {u.text}" for u in HOST_INTRO],
        "",
        "## The Unmapped Sun sample chapter",
        "",
        *[f"{s.index}. **{s.role} — {s.mode}:** {s.text}" for s in STORY],
        "",
        "## Post-chapter recap and Ivy's take",
        "",
        *[f"- **{u.mode}:** {u.text}" for u in RECAP],
        "",
    ]
    (OUT / "Ivy_Full_Demo_Human_Reader_Script.md").write_text("\n".join(script), encoding="utf-8")

    print(json.dumps({
        "status": "passed",
        "host_intro_seconds": round(len(host_master) / MASTER_SR, 3),
        "story_line_count": len(story_records),
        "recap_seconds": round(len(recap_master) / MASTER_SR, 3),
    }, indent=2))


if __name__ == "__main__":
    main()
