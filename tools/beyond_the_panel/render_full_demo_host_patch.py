#!/usr/bin/env python3
from __future__ import annotations
import argparse, json
from pathlib import Path
import numpy as np
import soundfile as sf
import torch
from chatterbox.tts import ChatterboxTTS
import render_full_demo_human_reader_audio as base

PATCHES={
 'host02':{'section':'host','index':2,'mode':'host_sincere','pause':0.56,'pieces':['This is kind of a big moment...','for me.'],'intent':'Let the personal significance arrive after Ivy has time to acknowledge the launch.'},
 'host08':{'section':'host','index':8,'mode':'host_warm','pause':0.0,'pieces':['But that... is what I love about stories.'],'minimum_seconds':3.0,'intent':'Replace a rushed transition with a warm thought that actually changes her emotional posture.'},
 'host10':{'section':'host','index':10,'mode':'host_playful','pause':0.40,'pieces':['And I will probably be yelling at the page...','right along with you.'],'intent':'Allow the playful admission to land in two conversational thoughts.'},
 'host14':{'section':'host','index':14,'mode':'host_sincere','pause':0.52,'pieces':["I'm really glad you're here...",'at the beginning.'],'intent':'Give the first-video gratitude room to feel direct and sincere.'},
}

def main():
 p=argparse.ArgumentParser();p.add_argument('--patch-key',choices=sorted(PATCHES),required=True);p.add_argument('--output-dir',type=Path,required=True);a=p.parse_args();patch=PATCHES[a.patch_key];out=a.output_dir.resolve();out.mkdir(parents=True,exist_ok=True);refs=out/'refs';refs.mkdir(exist_ok=True);base.OUT=out;base.REFS=refs;base.set_seed(base.BASE_SEED+99000);torch.set_num_threads(max(1,min(8,torch.get_num_threads())));mode_refs=base.build_mode_references();model=ChatterboxTTS.from_pretrained(device='cpu');mode=base.MODES[patch['mode']];parts=[];records=[]
 for idx,text in enumerate(patch['pieces'],1):
  candidates=[];n=3 if patch.get('minimum_seconds') else 1
  for c in range(n):
   y,m=base.generate_unit(model,text,mode,mode_refs[patch['mode']],base.BASE_SEED+99000+idx*1000+c*173);candidates.append((y,m))
  valid=[x for x in candidates if x[1]['duration_seconds']>=patch.get('minimum_seconds',0)];y,m=max(valid or candidates,key=lambda x:x[1]['duration_seconds']);parts.append(y);records.append({'text':text,**m})
 assembled=[]
 for i,y in enumerate(parts):
  assembled.append(y)
  if i<len(parts)-1:assembled.append(np.zeros(int(round(patch['pause']*base.MASTER_SR)),np.float32))
 master=np.concatenate(assembled);master,g=base.scalar_peak(master,-2.6);wav=out/f'{a.patch_key}.wav';sf.write(wav,master,base.MASTER_SR,subtype='PCM_24');man={**patch,'patch_key':a.patch_key,'file':str(wav),'duration_seconds':round(len(master)/base.MASTER_SR,6),'master_gain_db':round(g,3),'piece_records':records,'post_effects':[]};(out/'patch_manifest.json').write_text(json.dumps(man,indent=2,ensure_ascii=False));print(json.dumps(man,indent=2))
if __name__=='__main__':main()
