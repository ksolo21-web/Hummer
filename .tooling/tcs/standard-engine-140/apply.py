#!/usr/bin/env python3
from pathlib import Path
import base64,hashlib,json,subprocess,sys,tempfile,zlib

PATCH_SHA='57592587557e3b3b7f42e3f8f7bf5b0164641f34331890f4411f37d685699fca'
CHUNK_SHA=[
    'b056b6054ec81769cc09f841539f13ad4e4bb8c22e4fd6ee3065c4388dd604e5',
    '286dd31fbfda75f7af316003869294690c2f047552f4ab2b5cc3746f5fd39e74',
    '4e3e6dc9b4dfa5d55e6d254b0969bb5fc1f967eacabf97904e99c46042cec89d',
    'b64b8e91ad931685c14bd39f7034ce59ec7bfe5683ccfabc2709564e555e9489',
]
BASE={
'app/build.gradle.kts':'63e71224cf196d3710edb89d89f3c860f444350d78cd38b3cbdfb34696a8fc70',
'app/src/main/java/com/koenterprises/territorycardstudio/NativeAuthoringUi.kt':'244dade1dfc7a320104be6c22621977da45d2943aa80c7c860ddf8e6269365e0',
'app/src/androidTest/java/com/koenterprises/territorycardstudio/SimpleCreate101InstrumentationTest.kt':'425e51a5837e601df2a5f0a8ff9082991445c4a0d27015d103f741ecc646196a',
}
FIXED={
'app/build.gradle.kts':'9969616cedb747d56a7e4a014896f1f14b0d2094537dce49bf7379b01dcef99e',
'app/src/main/java/com/koenterprises/territorycardstudio/NativeAuthoringUi.kt':'84243d53cbe971e72b2098f58cbad052d35fd453baf96fe1176436b905fae8b9',
'app/src/androidTest/java/com/koenterprises/territorycardstudio/SimpleCreate101InstrumentationTest.kt':'d0dc6704d8e286ec15df08eeddcc82c3d5278a76020b49c582cb2a41a4c70440',
'app/src/main/java/com/koenterprises/territorycardstudio/AndroidPreservedSourceDraftService.kt':'b8617bc28b3cbf6a5a3cc7f5e8e391efc8079e5f11d03e35286fc70ae7ccb0a2',
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
