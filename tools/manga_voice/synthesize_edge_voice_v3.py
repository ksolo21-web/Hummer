from __future__ import annotations

import ast
import asyncio
import json
import math
import subprocess
from dataclasses import dataclass, asdict
from pathlib import Path

import edge_tts
from pydub import AudioSegment

SOURCE = Path('tools/manga_voice/synthesize_voice.py')
OUT = Path('voice_output_v3')
LINES = OUT / 'lines'
CANDIDATES = OUT / 'candidates'
for p in (OUT, LINES, CANDIDATES): p.mkdir(parents=True, exist_ok=True)
MASTER_MS, SR = 173_125, 48_000
PRIMARY = 'en-US-AvaMultilingualNeural'
ALTERNATE = 'en-US-EmmaMultilingualNeural'

@dataclass(frozen=True)
class U:
    index: int
    role: str
    text: str
    start: float
    end: float

    @property
    def slot(self) -> float: return max(.12, self.end - self.start - .085)


def load_utterances() -> list[U]:
    tree = ast.parse(SOURCE.read_text(encoding='utf-8'))
    for node in tree.body:
        if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'UTTERANCES' for t in node.targets):
            result = []
            for item in node.value.elts:
                vals = [ast.literal_eval(a) for a in item.args]
                result.append(U(int(vals[0]), str(vals[1]), str(vals[2]), float(vals[4]), float(vals[5])))
            if len(result) != 47: raise RuntimeError(f'Expected 47 lines, found {len(result)}')
            return result
    raise RuntimeError('UTTERANCES not found')

UTTERANCES = load_utterances()
ROLE = {
    'TITLE': (-4, 0), 'NARRATOR': (-1, 0), 'NIA': (2, 1), 'SORI': (4, 2),
    'GRANDMA': (-4, -1), 'ORIN': (-5, -3), 'COMPASS': (-4, 0), 'SUN': (-5, 0),
}


def spoken(u: U) -> str:
    text = u.text.replace('Piko', 'Pee-koh').replace('Nia', 'Nee-uh').replace('Eelbone', 'Eel-bone').replace('Orin', 'Or-in')
    cues = {
        1:'The Unmapped Sun. Sample chapter... The Eleventh Key.',
        2:'Tenfold. A city held beneath ten artificial suns. But this morning... one street has vanished.',
        8:'My grandma... forgot me.', 10:"At his grandmother's door, the impossible becomes personal.",
        12:"Grandma... it's me.", 13:'No.',
        16:'Before Nee-uh can examine the eleven-toothed key... the Registry arrives.',
        19:'Courier Nee-uh Sable.', 21:'Warden Or-in seals the block.',
        24:"At the roof's edge, Nee-uh draws across empty air.",
        30:'Beyond the impossible bridge... a hidden ruin waits.',
        40:'Nee-uh.', 43:'Then Or-in arrives.',
        45:'She erased herself... to keep the sun from finding you.',
    }
    return cues.get(u.index, text)


def duration(path: Path) -> float:
    return float(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','default=nw=1:nk=1',str(path)], text=True).strip())

async def raw_tts(text: str, voice: str, rate: int, pitch: int, path: Path) -> None:
    await edge_tts.Communicate(text, voice, rate=f'{rate:+d}%', volume='+0%', pitch=f'{pitch:+d}Hz').save(str(path))

async def line_audio(u: U, voice: str, tag: str) -> tuple[AudioSegment, dict]:
    rate, pitch = ROLE[u.role]
    src = LINES / f'{u.index:02d}_{u.role.lower()}_{tag}.mp3'
    wav = LINES / f'{u.index:02d}_{u.role.lower()}_{tag}.wav'
    attempts = []
    for _ in range(8):
        src.unlink(missing_ok=True)
        await raw_tts(spoken(u), voice, rate, pitch, src)
        d = duration(src); attempts.append({'rate':rate,'duration':round(d,4)})
        if d <= u.slot: break
        rate += max(3, min(12, math.ceil((d/u.slot - 1) * 70)))
    if d > u.slot: raise RuntimeError(f'Line {u.index} does not fit: {d:.3f}s > {u.slot:.3f}s')
    subprocess.run(['ffmpeg','-y','-v','error','-i',str(src),'-af','highpass=f=60,afade=t=in:st=0:d=.012,areverse,afade=t=in:st=0:d=.018,areverse','-ar',str(SR),'-ac','1','-c:a','pcm_s24le',str(wav)], check=True)
    seg = AudioSegment.from_file(wav).set_frame_rate(SR).set_channels(1).set_sample_width(3)
    return seg, {**asdict(u),'spoken':spoken(u),'voice':voice,'rate_percent':rate,'pitch_hz':pitch,'rendered_duration':round(len(seg)/1000,4),'attempts':attempts}

async def render(voice: str, count: int, tag: str) -> tuple[AudioSegment,list[dict]]:
    master = AudioSegment.silent(duration=MASTER_MS, frame_rate=SR).set_channels(1).set_sample_width(3)
    records=[]
    for u in UTTERANCES[:count]:
        seg, rec = await line_audio(u, voice, tag)
        master = master.overlay(seg, position=round(u.start*1000)); records.append(rec)
    return master, records


def master_audio(seg: AudioSegment, stem: str, seconds: float | None = None) -> tuple[Path,Path]:
    if seconds is not None: seg = seg[:round(seconds*1000)]
    raw=OUT/f'_{stem}_raw.wav'; wav=OUT/f'{stem}_48k.wav'; mp3=OUT/f'{stem}.mp3'
    seg.export(raw, format='wav', parameters=['-c:a','pcm_s24le'])
    subprocess.run(['ffmpeg','-y','-v','error','-i',str(raw),'-af','loudnorm=I=-19:TP=-2:LRA=6','-ar',str(SR),'-ac','2','-c:a','pcm_s24le',str(wav)], check=True)
    subprocess.run(['ffmpeg','-y','-v','error','-i',str(wav),'-c:a','libmp3lame','-b:a','256k',str(mp3)], check=True)
    raw.unlink(missing_ok=True); return wav,mp3

async def main() -> None:
    full, records = await render(PRIMARY,47,'ava')
    wav,mp3 = master_audio(full,'The_Unmapped_Sun_Crisp_Natural_Female_Voiceover')
    _,sample = master_audio(full,'The_Unmapped_Sun_Crisp_Voice_Approval_Sample_Ava',31.2)
    alt,_ = await render(ALTERNATE,9,'emma')
    _,alt_sample = master_audio(alt,'The_Unmapped_Sun_Crisp_Voice_Alternate_Emma',31.2)
    manifest={'engine':'Microsoft Edge neural speech via edge-tts','primary_voice':PRIMARY,'alternate_sample_voice':ALTERNATE,'sample_rate':SR,'duration':MASTER_MS/1000,'utterance_count':47,'audio_policy':{'dry_isolated_voice':True,'reverb':False,'echo':False,'delay':False,'room_ambience':False,'post_pitch_shift':False,'time_stretch':False,'denoise':False},'utterances':records}
    (OUT/'voice_manifest_v3.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    for p in (wav,mp3,sample,alt_sample):
        if not p.exists() or p.stat().st_size==0: raise RuntimeError(f'Missing {p}')
    print(json.dumps({'voice':PRIMARY,'duration':duration(wav),'lines':len(records)},indent=2))

if __name__=='__main__': asyncio.run(main())
