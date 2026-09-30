#!/usr/bin/env python3
from pathlib import Path
import base64,hashlib,json,subprocess,sys,tempfile,zlib

PATCH_SHA='ca6f8edce5470b9813dda02b930778b98316092a50bc7604e479c6e4a18f348b'
CHUNK_SHA=[
    'ebbf7657ee2ecddad2cedddfde7c0451a18fd17e9329dd520f88c1fa1b8355c1',
    'ca7cae4333b3ef33265ce04b21b6832c93d2556219bcc154fb90bdf8ddf8d19a',
]
BASE={
'app/build.gradle.kts':'63e71224cf196d3710edb89d89f3c860f444350d78cd38b3cbdfb34696a8fc70',
'app/src/main/java/com/koenterprises/territorycardstudio/NativeAuthoringUi.kt':'244dade1dfc7a320104be6c22621977da45d2943aa80c7c860ddf8e6269365e0',
'app/src/androidTest/java/com/koenterprises/territorycardstudio/SimpleCreate101InstrumentationTest.kt':'425e51a5837e601df2a5f0a8ff9082991445c4a0d27015d103f741ecc646196a',
}
FIXED={
'app/build.gradle.kts':'9969616cedb747d56a7e4a014896f1f14b0d2094537dce49bf7379b01dcef99e',
'app/src/main/java/com/koenterprises/territorycardstudio/NativeAuthoringUi.kt':'d7e41f1f44c7073552ef794ce6658fa36f980e20c325b851a9f880226fc90884',
'app/src/androidTest/java/com/koenterprises/territorycardstudio/SimpleCreate101InstrumentationTest.kt':'d0dc6704d8e286ec15df08eeddcc82c3d5278a76020b49c582cb2a41a4c70440',
'app/src/main/java/com/koenterprises/territorycardstudio/AndroidPreservedSourceDraftService.kt':'dfd0c63a1662e8ae1adfac9a3545a3321a85ee6c06021c325cd11518996647ea',
}

def sha(p:Path): return hashlib.sha256(p.read_bytes()).hexdigest()

def main():
    if len(sys.argv)!=2: raise SystemExit('Usage: apply.py <exact-territory-1.3.1-source>')
    root=Path(sys.argv[1]).resolve(strict=True)
    here=Path(__file__).resolve().parent
    for rel,expected in BASE.items():
        assert sha(root/rel)==expected,f'Unexpected 1.3.1 base: {rel}'
    chunks=[]
    for i,expected in enumerate(CHUNK_SHA):
        raw=(here/'chunks'/f'c{i:02}').read_text().strip()
        assert hashlib.sha256(raw.encode()).hexdigest()==expected,f'Patch chunk c{i:02} changed'
        chunks.append(raw)
    patch=zlib.decompress(base64.b64decode(''.join(chunks)))
    assert hashlib.sha256(patch).hexdigest()==PATCH_SHA,'1.4.0 patch identity changed'
    with tempfile.NamedTemporaryFile(prefix='tcs140-',suffix='.patch',delete=False) as f:
        f.write(patch); name=f.name
    try:
        subprocess.run(['git','apply','--check','--directory='+root.name,name],cwd=root.parent,check=True)
        subprocess.run(['git','apply','--directory='+root.name,name],cwd=root.parent,check=True)
    finally:
        Path(name).unlink(missing_ok=True)
    for rel,expected in FIXED.items():
        assert sha(root/rel)==expected,f'1.4.0 output mismatch: {rel}'
    print(json.dumps({
        'schema':'territory-standard-engine-140-v2',
        'version':'1.4.0','versionCode':51,
        'primarySimpleCreateBuilder':'preserve_supplied_map',
        'rawRasterSkeletonizerDefault':False,
        'unresolvedRoadWorksheetDefault':False,
        'fieldReleaseFromDraft':False,
        'status':'APPLIED'
    },indent=2))
if __name__=='__main__': main()
