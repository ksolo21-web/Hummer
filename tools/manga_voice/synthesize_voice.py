from __future__ import annotations

import json
import subprocess
from dataclasses import dataclass, asdict
from pathlib import Path

import librosa
import numpy as np
import soundfile as sf
from kokoro import KPipeline

SAMPLE_RATE = 24_000
MASTER_SR = 48_000
MASTER_DURATION = 173.089
OUT_DIR = Path("voice_output")
OUT_DIR.mkdir(parents=True, exist_ok=True)
LINE_DIR = OUT_DIR / "lines"
LINE_DIR.mkdir(exist_ok=True)


@dataclass(frozen=True)
class Utterance:
    index: int
    role: str
    text: str
    spoken: str
    start: float
    end: float
    speed: float = 1.0
    pitch: float = 0.0
    gain_db: float = -18.0
    effect: str = "clean"

    @property
    def slot(self) -> float:
        return self.end - self.start


# Caption wording remains exact. The spoken field only adds punctuation and pronunciation cues.
UTTERANCES: list[Utterance] = [
    Utterance(1, "TITLE", "The Unmapped Sun. Sample chapter: The Eleventh Key.", "The Unmapped Sun... Sample chapter: The Eleventh Key.", 0.48, 4.282, 0.94, 0.0, -18.5),
    Utterance(2, "NARRATOR", "Tenfold. A city held beneath ten artificial suns. But this morning, one street has vanished.", "Tenfold. A city held beneath ten artificial suns... But this morning, one street has vanished.", 5.712, 12.032, 0.96, 0.0, -18.0),
    Utterance(3, "NIA", "Move, Piko.", "Move, [Piko](/ˈpiːkoʊ/) !", 13.412, 14.888, 1.05, 0.4, -17.0),
    Utterance(4, "NARRATOR", "A voice rises from the alley.", "A voice rises from the alley...", 16.212, 18.247, 0.94, 0.0, -18.2),
    Utterance(5, "SORI", "Nia!", "[Nia](/ˈniːə/) !", 18.467, 19.270, 1.08, 1.6, -16.8),
    Utterance(6, "SORI", "They erased Eelbone Street.", "They erased [Eelbone](/ˈiːlboʊn/) Street!", 20.600, 22.288, 1.05, 1.2, -17.0),
    Utterance(7, "NIA", "Streets don't vanish.", "Streets... don't vanish.", 24.300, 25.873, 0.96, 0.2, -17.4),
    Utterance(8, "SORI", "My grandma forgot me.", "My grandma... forgot me.", 27.300, 29.103, 0.94, 1.0, -17.5),
    Utterance(9, "NIA", "Show me.", "Show me.", 29.453, 30.448, 0.93, 0.0, -17.2),
    Utterance(10, "NARRATOR", "At his grandmother's door, the impossible becomes personal.", "At his grandmother's door... the impossible becomes personal.", 33.150, 37.280, 0.95, 0.0, -18.0),
    Utterance(11, "GRANDMA", "Can I help you, young man?", "Can I help you, young man?", 38.660, 41.116, 0.91, -0.8, -17.5),
    Utterance(12, "SORI", "Grandma... it's me.", "Grandma... it's me.", 41.466, 42.976, 0.92, 1.0, -17.5),
    Utterance(13, "SORI", "No...", "No...", 45.060, 45.820, 0.86, 0.6, -18.0),
    Utterance(14, "NIA", "Where did you get it?", "Where did you get it?", 48.060, 49.269, 1.03, 0.3, -17.2),
    Utterance(15, "SORI", "Her sewing box.", "Her sewing box.", 49.569, 50.853, 0.98, 0.9, -17.3),
    Utterance(16, "NARRATOR", "Before Nia can examine the eleven-toothed key, the Registry arrives.", "Before [Nia](/ˈniːə/) can examine the eleven-toothed key... the Registry arrives.", 53.260, 57.060, 0.99, 0.0, -18.0),
    Utterance(17, "ORIN", "Hand over the illegal chart.", "Hand over the illegal chart.", 57.310, 59.627, 0.88, -3.0, -17.0, "orin"),
    Utterance(18, "NIA", "Run.", "Run!", 61.107, 61.869, 1.10, 0.4, -16.5),
    Utterance(19, "ORIN", "Courier Nia Sable.", "Courier... [Nia](/ˈniːə/) Sable.", 62.089, 63.967, 0.87, -3.2, -17.0, "orin"),
    Utterance(20, "NARRATOR", "The street closes around them.", "The street closes around them.", 66.107, 68.332, 0.94, 0.0, -18.2),
    Utterance(21, "NARRATOR", "Warden Orin seals the block.", "Warden [Orin](/ˈɔːrɪn/) seals the block.", 70.662, 72.497, 0.96, 0.0, -18.0),
    Utterance(22, "ORIN", "Seal the block.", "Seal the block.", 74.662, 75.945, 0.86, -3.2, -16.8, "orin"),
    Utterance(23, "NIA", "Stay on my heels!", "Stay on my heels!", 77.862, 79.318, 1.07, 0.5, -16.5),
    Utterance(24, "NARRATOR", "At the roof's edge, Nia draws across empty air.", "At the roof's edge, [Nia](/ˈniːə/) draws across empty air.", 81.262, 84.662, 0.97, 0.0, -18.0),
    Utterance(25, "NIA", "Roads listen.", "Roads... listen.", 84.882, 85.964, 0.90, 0.0, -17.0),
    Utterance(26, "SORI", "That jump is impossible!", "That jump is impossible!", 87.394, 89.322, 1.08, 1.4, -16.8),
    Utterance(27, "NIA", "Good.", "Good.", 89.582, 90.315, 0.89, -0.2, -16.7),
    Utterance(28, "NARRATOR", "The forbidden line rejects the Registry.", "The forbidden line... rejects the Registry.", 92.994, 95.919, 0.96, 0.0, -18.0),
    Utterance(29, "ORIN", "She opened another forbidden line.", "She opened another forbidden line.", 97.399, 99.905, 0.87, -3.0, -17.0, "orin"),
    Utterance(30, "NARRATOR", "Beyond the impossible bridge, a hidden ruin waits.", "Beyond the impossible bridge... a hidden ruin waits.", 102.849, 106.019, 0.94, 0.0, -18.0),
    Utterance(31, "NIA", "Blank Compass. One question.", "Blank Compass. One question.", 107.449, 109.662, 0.92, -0.1, -17.2),
    Utterance(32, "COMPASS", "What will you pay?", "What... will you pay?", 111.649, 113.361, 0.84, 2.0, -18.0, "mystic"),
    Utterance(33, "NIA", "A memory.", "A memory.", 115.949, 116.905, 0.86, -0.3, -17.5),
    Utterance(34, "NARRATOR", "The price takes the memory of the voice that once guided her.", "The price takes the memory... of the voice that once guided her.", 119.249, 122.659, 0.96, 0.0, -18.0),
    Utterance(35, "COMPASS", "Accepted.", "Accepted.", 122.919, 124.449, 0.80, 1.5, -18.0, "mystic"),
    Utterance(36, "SORI", "You paid for me?", "You paid for me?", 126.029, 127.409, 1.01, 1.0, -17.2),
    Utterance(37, "NIA", "Don't waste it.", "Don't waste it.", 127.729, 128.705, 0.91, -0.2, -17.0),
    Utterance(38, "NARRATOR", "An eleventh road rises toward a sealed sun.", "An eleventh road rises... toward a sealed sun.", 132.229, 135.339, 0.94, 0.0, -18.0),
    Utterance(39, "NARRATOR", "At the end of the hidden road, ancient machinery cages a living sunrise.", "At the end of the hidden road... ancient machinery cages a living sunrise.", 137.719, 142.139, 0.95, 0.0, -18.0),
    Utterance(40, "SUN", "Nia.", "[Nia](/ˈniːə/) ...", 143.569, 144.612, 0.77, 2.8, -18.5, "sun"),
    Utterance(41, "NIA", "Who said that?", "Who said that?", 146.869, 148.039, 1.00, 0.4, -16.9),
    Utterance(42, "SUN", "The first sunrise remembers your name.", "The first sunrise... remembers your name.", 150.269, 153.484, 0.82, 2.4, -18.2, "sun"),
    Utterance(43, "NARRATOR", "Then Orin arrives.", "Then... [Orin](/ˈɔːrɪn/) arrives.", 156.069, 157.469, 0.91, 0.0, -18.0),
    Utterance(44, "ORIN", "Then your mother failed.", "Then your mother failed.", 157.669, 159.469, 0.85, -3.2, -16.8, "orin"),
    Utterance(45, "ORIN", "She erased herself to keep the sun from finding you.", "She erased herself... to keep the sun from finding you.", 161.469, 165.302, 0.88, -3.0, -16.8, "orin"),
    Utterance(46, "NIA", "My mother?", "My mother?", 165.622, 166.641, 0.86, 0.2, -17.6),
    Utterance(47, "NARRATOR", "To be continued.", "To be continued...", 169.569, 170.779, 0.86, 0.0, -18.0),
]


def ensure_mono(audio: np.ndarray) -> np.ndarray:
    audio = np.asarray(audio, dtype=np.float32)
    if audio.ndim == 2:
        audio = np.mean(audio, axis=0 if audio.shape[0] < audio.shape[1] else 1)
    return audio.reshape(-1)


def trim_silence(audio: np.ndarray, threshold_db: float = -48.0, pad_ms: float = 45.0) -> np.ndarray:
    if audio.size == 0:
        return audio
    peak = float(np.max(np.abs(audio))) + 1e-9
    threshold = peak * (10.0 ** (threshold_db / 20.0))
    idx = np.flatnonzero(np.abs(audio) >= threshold)
    if idx.size == 0:
        return audio
    pad = int(SAMPLE_RATE * pad_ms / 1000.0)
    start = max(0, int(idx[0]) - pad)
    end = min(audio.size, int(idx[-1]) + pad + 1)
    return audio[start:end]


def fade(audio: np.ndarray, fade_ms: float = 22.0) -> np.ndarray:
    n = min(int(SAMPLE_RATE * fade_ms / 1000.0), audio.size // 2)
    if n <= 1:
        return audio
    env = np.ones(audio.size, dtype=np.float32)
    env[:n] = np.linspace(0.0, 1.0, n, dtype=np.float32)
    env[-n:] = np.linspace(1.0, 0.0, n, dtype=np.float32)
    return audio * env


def add_delay(audio: np.ndarray, delay_ms: float, decay: float) -> np.ndarray:
    delay = int(SAMPLE_RATE * delay_ms / 1000.0)
    out = np.pad(audio, (0, delay), mode="constant")
    out[delay:delay + audio.size] += decay * audio
    return out.astype(np.float32)


def soft_compress(audio: np.ndarray, drive: float = 1.15) -> np.ndarray:
    return (np.tanh(audio * drive) / np.tanh(drive)).astype(np.float32)


def apply_effect(audio: np.ndarray, effect: str) -> np.ndarray:
    if effect == "orin":
        return soft_compress(audio, 1.28)
    if effect == "mystic":
        wet = add_delay(audio, 92.0, 0.20)
        wet = add_delay(wet, 167.0, 0.12)
        return soft_compress(wet, 1.12)
    if effect == "sun":
        wet = add_delay(audio, 135.0, 0.18)
        wet = add_delay(wet, 255.0, 0.10)
        return soft_compress(wet, 1.08)
    return soft_compress(audio, 1.10)


def normalize_rms(audio: np.ndarray, target_db: float) -> np.ndarray:
    rms = float(np.sqrt(np.mean(np.square(audio), dtype=np.float64))) + 1e-9
    target = 10.0 ** (target_db / 20.0)
    audio = audio * (target / rms)
    peak = float(np.max(np.abs(audio))) + 1e-9
    if peak > 0.92:
        audio = audio * (0.92 / peak)
    return audio.astype(np.float32)


def fit_to_slot(audio: np.ndarray, slot_s: float) -> np.ndarray:
    max_len = max(0.12, slot_s - 0.08)
    duration = audio.size / SAMPLE_RATE
    if duration > max_len:
        rate = min(1.32, duration / max_len)
        audio = librosa.effects.time_stretch(audio, rate=rate).astype(np.float32)
    max_samples = int(max_len * SAMPLE_RATE)
    if audio.size > max_samples:
        audio = audio[:max_samples]
    return fade(audio)


def synthesize_one(pipeline: KPipeline, utt: Utterance) -> np.ndarray:
    chunks: list[np.ndarray] = []
    generator = pipeline(utt.spoken, voice="af_heart", speed=1.0, split_pattern=r"\n+")
    for _graphemes, _phonemes, audio in generator:
        chunks.append(ensure_mono(audio))
    if not chunks:
        raise RuntimeError(f"No audio generated for line {utt.index}: {utt.text}")
    audio = np.concatenate(chunks)
    audio = trim_silence(audio)
    if abs(utt.pitch) > 0.01:
        audio = librosa.effects.pitch_shift(audio, sr=SAMPLE_RATE, n_steps=utt.pitch).astype(np.float32)
    if abs(utt.speed - 1.0) > 0.01:
        audio = librosa.effects.time_stretch(audio, rate=utt.speed).astype(np.float32)
    audio = apply_effect(audio, utt.effect)
    audio = fit_to_slot(audio, utt.slot)
    audio = normalize_rms(audio, utt.gain_db)
    return audio


def write_mp3(wav_path: Path, mp3_path: Path, bitrate: str = "192k") -> None:
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", str(wav_path), "-codec:a", "libmp3lame", "-b:a", bitrate, str(mp3_path)], check=True)


def main() -> None:
    np.random.seed(7)
    pipeline = KPipeline(lang_code="a")
    master = np.zeros(int(MASTER_DURATION * SAMPLE_RATE), dtype=np.float32)
    rendered: list[dict] = []

    for utt in UTTERANCES:
        print(f"[{utt.index:02d}/{len(UTTERANCES)}] {utt.role}: {utt.text}", flush=True)
        audio = synthesize_one(pipeline, utt)
        line_path = LINE_DIR / f"{utt.index:02d}_{utt.role.lower()}.wav"
        sf.write(line_path, audio, SAMPLE_RATE, subtype="PCM_16")
        start_sample = int(round(utt.start * SAMPLE_RATE))
        end_sample = min(master.size, start_sample + audio.size)
        master[start_sample:end_sample] += audio[:end_sample - start_sample]
        rendered.append({**asdict(utt), "rendered_duration": round(audio.size / SAMPLE_RATE, 4), "line_file": str(line_path)})

    peak = float(np.max(np.abs(master))) + 1e-9
    if peak > 0.96:
        master *= 0.96 / peak
    master = soft_compress(master, 1.05)

    mono_wav = OUT_DIR / "The_Unmapped_Sun_Natural_Female_Voiceover_24k_mono.wav"
    sf.write(mono_wav, master, SAMPLE_RATE, subtype="PCM_24")

    stereo = np.stack([master, master], axis=1)
    stereo_48 = librosa.resample(stereo.T, orig_sr=SAMPLE_RATE, target_sr=MASTER_SR, axis=-1).T.astype(np.float32)
    stereo_wav = OUT_DIR / "The_Unmapped_Sun_Natural_Female_Voiceover_48k_stereo.wav"
    sf.write(stereo_wav, stereo_48, MASTER_SR, subtype="PCM_24")
    mp3_path = OUT_DIR / "The_Unmapped_Sun_Natural_Female_Voiceover.mp3"
    write_mp3(stereo_wav, mp3_path, "224k")

    sample_indices = [2, 3, 6, 8, 17, 22, 31, 32, 34, 40, 42, 45, 46]
    sample_parts: list[np.ndarray] = []
    gap = np.zeros(int(0.28 * SAMPLE_RATE), dtype=np.float32)
    for index in sample_indices:
        path = next(LINE_DIR.glob(f"{index:02d}_*.wav"))
        line, sr = sf.read(path, dtype="float32")
        if sr != SAMPLE_RATE:
            line = librosa.resample(ensure_mono(line), orig_sr=sr, target_sr=SAMPLE_RATE)
        sample_parts.extend([ensure_mono(line), gap])
    sample = np.concatenate(sample_parts) if sample_parts else np.zeros(SAMPLE_RATE, dtype=np.float32)
    sample_wav = OUT_DIR / "The_Unmapped_Sun_Natural_Voice_Approval_Sample.wav"
    sf.write(sample_wav, sample, SAMPLE_RATE, subtype="PCM_24")
    write_mp3(sample_wav, OUT_DIR / "The_Unmapped_Sun_Natural_Voice_Approval_Sample.mp3", "224k")

    manifest = {
        "engine": "Kokoro-82M via kokoro Python pipeline",
        "voice": "af_heart",
        "sample_rate_master": MASTER_SR,
        "duration": MASTER_DURATION,
        "utterance_count": len(UTTERANCES),
        "notes": "Natural neural female performance proof. Role modulation uses restrained pitch, pace, punctuation, dynamics, and selective ambience; no robotic system TTS is used.",
        "utterances": rendered,
    }
    (OUT_DIR / "voice_manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print("Voice render complete.")


if __name__ == "__main__":
    main()
