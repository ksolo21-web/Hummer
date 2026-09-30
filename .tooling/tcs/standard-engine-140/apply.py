#!/usr/bin/env python3
from pathlib import Path
import base64,hashlib,json,subprocess,sys,tempfile,zlib

PATCH_SHA='57592587557e3b3b7f42e3f8f7bf5b0164641f34331890f4411f37d685699fca'
CHUNKS=[
    ('c00','b056b6054ec81769cc09f841539f13ad4e4bb8c22e4fd6ee3065c4388dd604e5'),
    ('s01a','3cceb4451fcfef8fded984fa8aefcce6e1bb0e2dad80707ce2cae2ff52a5ab31'),
    ('s01b','1a62ab70dd9772865b9a022ba6b1033fb42e55e9237ecf4e12127a797ffb744e'),
    ('s01c','39a011645493b7195cf0427838e102fb3c09ea8cd9beef4eb7adba0790405fc5'),
    ('s02a','902714503ae70b33421400019f370f7f47f3c0f8204a49eeda7d0694887035b9'),
    ('s02b','fa639696ee96a8cfecc88212d40af03ea991643c09b6ebabf7db97f9db718f9c'),
    ('s02c','2a3c31d9c02ecef56b1eaaefae5f3bd75ce1f6ba8fd66c2571816031786d7145'),
    ('c03','b64b8e91ad931685c14bd39f7034ce59ec7bfe5683ccfabc2709564e555e9489'),
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
    encoded=[]
    for name,expected in CHUNKS:
        raw=(here/'chunks'/name).read_text().strip()
        assert hashlib.sha256(raw.encode()).hexdigest()==expected,f'Patch segment {name} changed'
        encoded.append(raw)
    patch=zlib.decompress(base64.b64decode(''.join(encoded)))
    assert hashlib.sha256(patch).hexdigest()==PATCH_SHA,'1.4.0 patch identity changed'
    with tempfile.NamedTemporaryFile(prefix='tcs140-',suffix='.patch',delete=False) as out:
        out.write(patch); patch_path=out.name
    try:
        subprocess.run(['git','apply','--check','--directory='+root.name,patch_path],cwd=root.parent,check=True)
        subprocess.run(['git','apply','--directory='+root.name,patch_path],cwd=root.parent,check=True)
    finally:
        Path(patch_path).unlink(missing_ok=True)
    for rel,expected in FIXED.items():
        assert sha(root/rel)==expected,f'1.4.0 output mismatch: {rel}'
    print(json.dumps({
        'schema':'territory-standard-engine-140-v3',
        'version':'1.4.0',
        'versionCode':51,
        'primarySimpleCreateBuilder':'preserve_supplied_map',
        'rawRasterSkeletonizerDefault':False,
        'legacyTracedDraftSuppressedWhenPreservedDraftExists':True,
        'unresolvedRoadWorksheetDefault':False,
        'fieldReleaseFromDraft':False,
        'status':'APPLIED'
    },indent=2))
if __name__=='__main__': main()
