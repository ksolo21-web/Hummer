#!/usr/bin/env python3
"""
Generate a completely dry, isolated narration stem for The Unmapped Sun.
No reverb, echo, room simulation, stereo widening, chorus, delay, EQ,
compression, pitch shifting, time stretching, ambience, or doubled voice.
Only native Kokoro synthesis speed, scalar gain, resampling, placement,
and 5 ms anti-click fades are used.
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
LINES = OUT / "lines"
OUT.mkdir(parents=True, exist_ok=True)
LINES.mkdir(parents=True, exist_ok=True)
MASTER_SR = 48000
MODEL_SR = 24000
MASTER_DURATION = 173.125
VOICE = "af_heart"

UTTERANCES = [
{"index":1,"role":"TITLE","spoken":"The Unmapped Sun... Sample chapter: The Eleventh Key.","start":0.48,"slot_end":4.282,"speed":0.94},
{"index":2,"role":"NARRATOR","spoken":"Tenfold. A city held beneath ten artificial suns... But this morning, one street has vanished.","start":5.712,"slot_end":12.032,"speed":0.96},
{"index":3,"role":"NIA","spoken":"Move, [Piko](/ˈpiːkoʊ/) !","start":13.412,"slot_end":14.888,"speed":1.05},
{"index":4,"role":"NARRATOR","spoken":"A voice rises from the alley...","start":16.212,"slot_end":18.247,"speed":0.94},
{"index":5,"role":"SORI","spoken":"[Nia](/ˈniːə/) !","start":18.467,"slot_end":19.27,"speed":1.08},
{"index":6,"role":"SORI","spoken":"They erased [Eelbone](/ˈiːlboʊn/) Street!","start":20.6,"slot_end":22.288,"speed":1.05},
{"index":7,"role":"NIA","spoken":"Streets... don't vanish.","start":24.3,"slot_end":25.873,"speed":0.96},
{"index":8,"role":"SORI","spoken":"My grandma... forgot me.","start":27.3,"slot_end":29.103,"speed":0.94},
{"index":9,"role":"NIA","spoken":"Show me.","start":29.453,"slot_end":30.448,"speed":0.93},
{"index":10,"role":"NARRATOR","spoken":"At his grandmother's door... the impossible becomes personal.","start":33.15,"slot_end":37.28,"speed":0.95},
{"index":11,"role":"GRANDMA","spoken":"Can I help you, young man?","start":38.66,"slot_end":41.116,"speed":0.91},
{"index":12,"role":"SORI","spoken":"Grandma... it's me.","start":41.466,"slot_end":42.976,"speed":0.92},
{"index":13,"role":"SORI","spoken":"No...","start":45.06,"slot_end":45.82,"speed":0.86},
{"index":14,"role":"NIA","spoken":"Where did you get it?","start":48.06,"slot_end":49.269,"speed":1.03},
{"index":15,"role":"SORI","spoken":"Her sewing box.","start":49.569,"slot_end":50.853,"speed":0.98},
{"index":16,"role":"NARRATOR","spoken":"Before [Nia](/ˈniːə/) can examine the eleven-toothed key... the Registry arrives.","start":53.26,"slot_end":57.06,"speed":0.99},
{"index":17,"role":"ORIN","spoken":"Hand over the illegal chart.","start":57.31,"slot_end":59.627,"speed":0.88},
{"index":18,"role":"NIA","spoken":"Run!","start":61.107,"slot_end":61.869,"speed":1.1},
{"index":19,"role":"ORIN","spoken":"Courier... [Nia](/ˈniːə/) Sable.","start":62.089,"slot_end":63.967,"speed":0.87},
{"index":20,"role":"NARRATOR","spoken":"The street closes around them.","start":66.107,"slot_end":68.332,"speed":0.94},
{"index":21,"role":"NARRATOR","spoken":"Warden [Orin](/ˈɔːrɪn/) seals the block.","start":70.662,"slot_end":72.497,"speed":0.96},
{"index":22,"role":"ORIN","spoken":"Seal the block.","start":74.662,"slot_end":75.945,"speed":0.86},
{"index":23,"role":"NIA","spoken":"Stay on my heels!","start":77.862,"slot_end":79.318,"speed":1.07},
{"index":24,"role":"NARRATOR","spoken":"At the roof's edge, [Nia](/ˈniːə/) draws across empty air.","start":81.262,"slot_end":84.662,"speed":0.97},
{"index":25,"role":"NIA","spoken":"Roads... listen.","start":84.882,"slot_end":85.964,"speed":0.9},
{"index":26,"role":"SORI","spoken":"That jump is impossible!","start":87.394,"slot_end":89.322,"speed":1.08},
{"index":27,"role":"NIA","spoken":"Good.","start":89.582,"slot_end":90.315,"speed":0.89},
{"index":28,"role":"NARRATOR","spoken":"The forbidden line... rejects the Registry.","start":92.994,"slot_end":95.919,"speed":0.96},
{"index":29,"role":"ORIN","spoken":"She opened another forbidden line.","start":97.399,"slot_end":99.905,"speed":0.87},
{"index":30,"role":"NARRATOR","spoken":"Beyond the impossible bridge... a hidden ruin waits.","start":102.849,"slot_end":106.019,"speed":0.94},
{"index":31,"role":"NIA","spoken":"Blank Compass. One question.","start":107.449,"slot_end":109.662,"speed":0.92},
{"index":32,"role":"COMPASS","spoken":"What... will you pay?","start":111.649,"slot_end":113.361,"speed":0.84},
{"index":33,"role":"NIA","spoken":"A memory.","start":115.949,"slot_end":116.905,"speed":0.86},
{"index":34,"role":"NARRATOR","spoken":"The price takes the memory... of the voice that once guided her.","start":119.249,"slot_end":122.659,"speed":0.96},
{"index":35,"role":"COMPASS","spoken":"Accepted.","start":122.919,"slot_end":124.449,"speed":0.8},
{"index":36,"role":"SORI","spoken":"You paid for me?","start":126.029,"slot_end":127.409,"speed":1.01},
{"index":37,"role":"NIA","spoken":"Don't waste it.","start":127.729,"slot_end":128.705,"speed":0.91},
{"index":38,"role":"NARRATOR","spoken":"An eleventh road rises... toward a sealed sun.","start":132.229,"slot_end":135.339,"speed":0.94},
{"index":39,"role":"NARRATOR","spoken":"At the end of the hidden road... ancient machinery cages a living sunrise.","start":137.719,"slot_end":142.139,"speed":0.95},
{"index":40,"role":"SUN","spoken":"[Nia](/ˈniːə/) ...","start":143.569,"slot_end":144.612,"speed":0.77},
{"index":41,"role":"NIA","spoken":"Who said that?","start":146.869,"slot_end":148.039,"speed":1.0},
{"index":42,"role":"SUN","spoken":"The first sunrise... remembers your name.","start":150.269,"slot_end":153.484,"speed":0.82},
{"index":43,"role":"NARRATOR","spoken":"Then... [Orin](/ˈɔːrɪn/) arrives.","start":156.069,"slot_end":157.469,"speed":0.91},
{"index":44,"role":"ORIN","spoken":"Then your mother failed.","start":157.669,"slot_end":159.469,"speed":0.85},
{"index":45,"role":"ORIN","spoken":"She erased herself... to keep the sun from finding you.","start":161.469,"slot_end":165.302,"speed":0.88},
{"index":46,"role":"NIA","spoken":"My mother?","start":165.622,"slot_end":166.641,"speed":0.86},
{"index":47,"role":"NARRATOR","spoken":"To be continued...","start":169.569,"slot_end":170.779,"speed":0.86}
]

def synthesize_one(pipeline, text, speed):
    chunks=[]
    for _g,_p,audio in pipeline(text,voice=VOICE,speed=speed):
        a=np.asarray(audio,dtype=np.float32).reshape(-1)
        if a.size: chunks.append(a)
    if not chunks: raise RuntimeError(f"No audio generated for {text!r}")
    y=np.concatenate(chunks)
    nz=np.flatnonzero(np.abs(y)>1e-5)
    if nz.size:
        pad=int(0.025*MODEL_SR); y=y[max(0,int(nz[0])-pad):min(len(y),int(nz[-1])+pad+1)]
    return y

def fit_native(pipeline,text,initial,max_duration):
    speed=max(0.78,min(1.18,float(initial))); attempts=[]
    for _ in range(7):
        y=synthesize_one(pipeline,text,speed); dur=len(y)/MODEL_SR; attempts.append((speed,dur))
        if dur<=max_duration: return y,speed,attempts
        speed=min(1.35,speed*(dur/max_duration)*1.025)
    raise RuntimeError(f"Line too long: {text!r} {attempts}")

def scalar_level(y,target=-20.5,ceiling=-2.5):
    rms=float(np.sqrt(np.mean(np.square(y,dtype=np.float64))+1e-12))
    gain=10**((target-20*math.log10(max(rms,1e-12)))/20)
    gain=min(gain,(10**(ceiling/20))/(float(np.max(np.abs(y)))+1e-12))
    return (y*gain).astype(np.float32),20*math.log10(max(gain,1e-12))

pipeline=KPipeline(lang_code="a")
master=np.zeros(int(round(MASTER_DURATION*MASTER_SR)),dtype=np.float32)
rendered=[]
for u in UTTERANCES:
    max_duration=max(0.20,float(u["slot_end"])-float(u["start"])-0.035)
    raw,used_speed,attempts=fit_native(pipeline,u["spoken"],u["speed"],max_duration)
    y=resample_poly(raw,MASTER_SR,MODEL_SR).astype(np.float32)
    fade=min(int(0.005*MASTER_SR),len(y)//2)
    if fade:
        ramp=np.linspace(0,1,fade,endpoint=False,dtype=np.float32); y[:fade]*=ramp; y[-fade:]*=ramp[::-1]
    y,gain_db=scalar_level(y)
    s=int(round(float(u["start"])*MASTER_SR)); e=min(len(master),s+len(y)); master[s:e]+=y[:e-s]
    line=LINES/f"{u['index']:02d}_{u['role'].lower()}.wav"; sf.write(line,y,MASTER_SR,subtype="PCM_24")
    rendered.append({**u,"voice":VOICE,"post_effects":[],"native_speed_used":round(used_speed,5),"rendered_duration":round(len(y)/MASTER_SR,6),"gain_db":round(gain_db,3),"attempts":[{"speed":round(a,5),"duration":round(b,6)} for a,b in attempts],"line_file":str(line)})
peak=float(np.max(np.abs(master))+1e-12); global_gain=min(1.0,(10**(-1.5/20))/peak); master*=global_gain
sf.write(OUT/"The_Unmapped_Sun_Pure_Isolated_Voiceover_48k_PCM24.wav",master,MASTER_SR,subtype="PCM_24")
sf.write(OUT/"The_Unmapped_Sun_Pure_Isolated_Voiceover_48k.wav",master,MASTER_SR,subtype="FLOAT")
a=int(round(52.78*MASTER_SR)); b=int(round(84.78*MASTER_SR)); sf.write(OUT/"The_Unmapped_Sun_Pure_Isolated_Voice_Approval_Sample.wav",master[a:b],MASTER_SR,subtype="PCM_24")
manifest={"engine":"Kokoro-82M","voice":VOICE,"sample_rate":MASTER_SR,"channels":1,"duration":MASTER_DURATION,"acoustic_processing":{"reverb":False,"echo":False,"delay":False,"room_simulation":False,"stereo_widening":False,"doubling":False,"chorus":False,"pitch_shift":False,"time_stretch":False,"eq":False,"compression":False,"noise_reduction":False,"ambience":False},"allowed_processing":["native synthesis speed","scalar gain","48 kHz polyphase resampling","timeline placement","5 ms anti-click fades"],"global_gain_db":round(20*math.log10(max(global_gain,1e-12)),3),"utterances":rendered}
(OUT/"pure_isolated_voice_manifest.json").write_text(json.dumps(manifest,indent=2,ensure_ascii=False),encoding="utf-8")
print(json.dumps({"status":"ok","utterances":len(rendered),"duration":MASTER_DURATION,"peak":float(np.max(np.abs(master))),"global_gain_db":manifest["global_gain_db"]},indent=2))
