#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,subprocess,sys

PATCH_SHA='720d7cfbaf6caae17f2d70d931a05380c065e70d541dadbe7c838ccd3c5811ba'
BASE={
'app/build.gradle.kts':'ffec339cf4ac1d4073f1877b123a15b263077f49961b6c5894181ac29d12a91b',
'app/src/main/java/com/koenterprises/territorycardstudio/AndroidMapImageInterpreter.kt':'1a2c19ea99ed657e85545b90a1e657c2a8422db2803626142884162070b2ec19',
'app/src/main/java/com/koenterprises/territorycardstudio/NativeAuthoringUi.kt':'2fae39b2411ef7f78b6aa6d34e585288c31b733b27ea64bd7d2ceccd35a5b9b6',
}
FIXED={
'app/build.gradle.kts':'63e71224cf196d3710edb89d89f3c860f444350d78cd38b3cbdfb34696a8fc70',
'app/src/main/java/com/koenterprises/territorycardstudio/AndroidMapImageInterpreter.kt':'5c0a6befa2b37d6b45a3a6420fd61c0eabecdae1cbf1047a85c8fe9300c26719',
'app/src/main/java/com/koenterprises/territorycardstudio/NativeAuthoringUi.kt':'244dade1dfc7a320104be6c22621977da45d2943aa80c7c860ddf8e6269365e0',
}
def sha(p:Path): return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    if len(sys.argv)!=2: raise SystemExit('Usage: apply.py <exact-phase-c-1.3.0-source>')
    root=Path(sys.argv[1]).resolve(strict=True)
    here=Path(__file__).resolve().parent
    patch=here/'ocr-recovery.patch'
    assert sha(patch)==PATCH_SHA,'hotfix patch changed'
    for rel,expected in BASE.items():
        assert sha(root/rel)==expected,f'Unexpected 1.3.0 base: {rel}'
    subprocess.run(['git','apply','--check','--directory='+root.name,str(patch)],cwd=root.parent,check=True)
    subprocess.run(['git','apply','--directory='+root.name,str(patch)],cwd=root.parent,check=True)
    for rel,expected in FIXED.items():
        assert sha(root/rel)==expected,f'Hotfix output mismatch: {rel}'
    test=root/'app/src/androidTest/java/com/koenterprises/territorycardstudio/MapOcrRecoveryInstrumentationTest.kt'
    assert test.is_file()
    print(json.dumps({'schema':'territory-maintenance-131-ocr-recovery-v1','version':'1.3.1','versionCode':50,'ocrRetryAttempts':2,'geometryFallback':True,'rawSdkErrorHidden':True,'status':'APPLIED'},indent=2))
if __name__=='__main__': main()
