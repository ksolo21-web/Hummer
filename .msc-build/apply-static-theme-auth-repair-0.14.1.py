#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path('.')
android = ROOT / 'MyStudyCompanion'
web = ROOT / 'MyStudyCompanionWeb'

STATIC_THEME_ENUMS = (
    'CALM_LIGHT', 'PREMIUM_DARK', 'WARM_EDITORIAL',
    'OWL', 'FOX', 'LION', 'TIGER', 'MOONLIT_WOLF', 'GOLDEN_OWL', 'SAKURA_TIGER',
    'WATERFALL_SERENITY', 'RAINFOREST_HARMONY', 'OCEAN_MAJESTY',
    'CELESTIAL_WONDER', 'MOUNTAIN_SUNRISE', 'CREATION_GARDEN',
    'BIBLE_SKETCH_STUDY', 'PARABLE_LINE_PANELS', 'NOAHS_ARK',
    'RED_SEA_DELIVERANCE', 'CREATION_SKY', 'BIBLE_TIMELINE', 'BIBLE_MAP',
)
STATIC_THEME_LABELS = (
    'Calm Light', 'Premium Dark', 'Warm Editorial — White',
    'Owl', 'Fox', 'Lion', 'Tiger', 'Moonlit Wolf', 'Golden Owl', 'Sakura Tiger',
    'Waterfall Serenity', 'Rainforest Harmony', 'Ocean Majesty',
    'Celestial Wonder', 'Mountain Sunrise', 'Creation Garden',
    'Bible Sketch Study', 'Parable Line Panels', 'Noah’s Ark',
    'Red Sea Deliverance', 'Creation Sky', 'Bible Timeline', 'Bible Map',
)

firebase_sync = web / 'firebase-sync.js'
source = firebase_sync.read_text(encoding='utf-8')
new_header = r'''const SDK = "https://www.gstatic.com/firebasejs/12.16.0/";
let auth, db, modules, currentUser;
let initializationPromise;
let redirectHandled = false;

export function configured(){
  return Boolean(window.MSC_FIREBASE_CONFIG?.appId);
}

async function initialize(){
  if(!configured()) throw new Error("Firebase Web App registration is not configured yet.");
  if(initializationPromise) return initializationPromise;
  initializationPromise = (async()=>{
    const [appMod, authMod, fireMod] = await Promise.all([
      import(SDK + "firebase-app.js"),
      import(SDK + "firebase-auth.js"),
      import(SDK + "firebase-firestore.js")
    ]);
    modules = {...authMod, ...fireMod};
    const app = appMod.getApps().length ? appMod.getApp() : appMod.initializeApp(window.MSC_FIREBASE_CONFIG);
    auth = authMod.getAuth(app);
    await authMod.setPersistence(auth, authMod.browserLocalPersistence);
    db = fireMod.getFirestore(app);
  })();
  return initializationPromise;
}

async function finishRedirectIfNeeded(){
  await initialize();
  if(redirectHandled) return;
  redirectHandled = true;
  const result = await modules.getRedirectResult(auth);
  if(result?.user) currentUser = result.user;
}

export async function restoreSession(onState){
  await finishRedirectIfNeeded();
  return new Promise((resolve, reject)=>{
    const unsubscribe = modules.onAuthStateChanged(auth, user=>{
      currentUser = user;
      onState?.(user);
      unsubscribe();
      resolve(user);
    }, reject);
  });
}

export async function connect(onState){
  const existing = await restoreSession(onState);
  if(existing) return existing;
  const provider = new modules.GoogleAuthProvider();
  provider.setCustomParameters({prompt:"select_account"});
  try {
    const result = await modules.signInWithPopup(auth, provider);
    currentUser = result.user;
    onState?.(currentUser);
    return currentUser;
  } catch(error) {
    if(error?.code === "auth/popup-blocked") {
      await modules.signInWithRedirect(auth, provider);
      return null;
    }
    throw error;
  }
}
'''
source, count = re.subn(
    r'^const SDK = .*?\n\}\n\nasync function workbookPageId',
    new_header + '\nasync function workbookPageId',
    source,
    count=1,
    flags=re.S,
)
if count != 1:
    raise SystemExit('Failed to restore the Firebase auth header.')
firebase_sync.write_text(source, encoding='utf-8')

app_js = web / 'app.js'
app_source = app_js.read_text(encoding='utf-8')
app_source = re.sub(r'from "\./firebase-sync\.js\?v=\d+";', 'from "./firebase-sync.js?v=0145";', app_source, count=1)
old_click = '    const user=await connect(updateAuthUi);\n    await syncHousehold(user);'
new_click = '    const user=await connect(updateAuthUi);\n    if(!user){ setSync("Completing Google sign-in…"); return; }\n    await syncHousehold(user);'
if old_click in app_source:
    app_source = app_source.replace(old_click, new_click, 1)
elif new_click not in app_source:
    raise SystemExit('Missing web sign-in button anchor.')
app_js.write_text(app_source, encoding='utf-8')

index = web / 'index.html'
index_source = index.read_text(encoding='utf-8')
for old in (
    'The original themes remain available, with 13 new creation and Bible-account concepts.',
    'Choose from the approved original and animal themes. Custom colors remain available through the visual color wheel.',
):
    index_source = index_source.replace(old, 'Choose from 23 polished static themes. Custom colors remain available through the visual color wheel.')
index_source = re.sub(r'appearance\.js\?v=\d+', 'appearance.js?v=0145', index_source)
index_source = re.sub(r'firebase-config\.js\?v=\d+', 'firebase-config.js?v=0145', index_source)
index_source = re.sub(r'app\.js\?v=\d+', 'app.js?v=0145', index_source)
index.write_text(index_source, encoding='utf-8')

sw = web / 'sw.js'
sw_source = sw.read_text(encoding='utf-8')
sw_source = re.sub(r'msc-web-v\d+-[A-Za-z0-9-]+', 'msc-web-v0145-static-theme-auth-repair', sw_source, count=1)
sw.write_text(sw_source, encoding='utf-8')

app_theme = android / 'app/src/main/java/com/mystudycompanion/app/design/AppThemeMode.kt'
app_theme_source = app_theme.read_text(encoding='utf-8')
for enum_name, label in zip(STATIC_THEME_ENUMS, STATIC_THEME_LABELS):
    if enum_name not in app_theme_source or label not in app_theme_source:
        raise SystemExit(f'Static theme is missing from AppThemeMode: {label}')
if app_theme_source.count('isIllustratedTheme = true') != 13:
    raise SystemExit('Expected exactly 13 creation/Bible-account illustrated static themes.')

appearance = web / 'appearance.js'
appearance_source = appearance.read_text(encoding='utf-8')
for label in STATIC_THEME_LABELS:
    if label not in appearance_source:
        raise SystemExit(f'Static web theme is missing: {label}')

for path in (
    android / 'app/src/main/java/com/mystudycompanion/app/design/ThemeArtwork.kt',
    android / 'app/src/main/java/com/mystudycompanion/app/design/ThemeBackdrop.kt',
    appearance,
):
    text = path.read_text(encoding='utf-8', errors='ignore')
    for forbidden in ('isLiveTheme', 'liveTheme', 'rememberInfiniteTransition', 'infiniteRepeatable'):
        if forbidden in text:
            raise SystemExit(f'Live/animated theme implementation remains in {path}: {forbidden}')

(web / 'appearance.test.mjs').write_text(r'''import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const appearance=fs.readFileSync(new URL("./appearance.js",import.meta.url),"utf8");
const html=fs.readFileSync(new URL("./index.html",import.meta.url),"utf8");
const auth=fs.readFileSync(new URL("./firebase-sync.js",import.meta.url),"utf8");
const sw=fs.readFileSync(new URL("./sw.js",import.meta.url),"utf8");

test("all 23 static themes and automatic are present",()=>{
  const themes=[
    "Calm Light","Premium Dark","Warm Editorial — White",
    "Owl","Fox","Lion","Tiger","Moonlit Wolf","Golden Owl","Sakura Tiger",
    "Waterfall Serenity","Rainforest Harmony","Ocean Majesty","Celestial Wonder",
    "Mountain Sunrise","Creation Garden","Bible Sketch Study","Parable Line Panels",
    "Noah’s Ark","Red Sea Deliverance","Creation Sky","Bible Timeline","Bible Map",
    "Automatic"
  ];
  for(const name of themes) assert.ok(appearance.includes(name),name);
});

test("themes are static and visual color controls remain",()=>{
  assert.ok(html.includes('id="appearanceColorWheel"'));
  assert.ok(html.includes('id="appearanceBrightness"'));
  assert.ok(!appearance.includes("isLiveTheme"));
  assert.ok(!appearance.includes("liveTheme"));
  assert.ok(!appearance.includes("requestAnimationFrame"));
  assert.ok(sw.includes("msc-web-v0145-static-theme-auth-repair"));
});

test("Google login restores persistence and handles popup and redirect results",()=>{
  assert.ok(auth.includes("browserLocalPersistence"));
  assert.ok(auth.includes("getRedirectResult"));
  assert.ok(auth.includes("const result = await modules.signInWithPopup"));
  assert.ok(auth.includes('error?.code === "auth/popup-blocked"'));
  assert.ok(!auth.includes('error?.code?.includes("popup")'));
});
''', encoding='utf-8')

for path in list((ROOT / '.msc-build').glob('*')) + list((ROOT / '.github/workflows').glob('*.yml')):
    if not path.is_file() or path.suffix not in {'.sh', '.py', '.md', '.yml'}:
        continue
    text = path.read_text(encoding='utf-8', errors='ignore')
    text = text.replace('msc-web-v0144-auth-theme-repair', 'msc-web-v0145-static-theme-auth-repair')
    text = text.replace('23 permanent themes', '23 polished static themes')
    text = text.replace('23-theme gallery', '23-static-theme gallery')
    path.write_text(text, encoding='utf-8')

print('Restored the 0.13.0 Google sign-in lifecycle and preserved all 23 approved themes as static themes.')

# Synchronize the exact same static artwork to Wear and web.
import shutil
slugs = tuple(name.lower() for name in STATIC_THEME_ENUMS)
app_assets = ROOT / 'MyStudyCompanion/app/src/main/res/drawable-nodpi'
wear_assets = ROOT / 'MyStudyCompanion/wear/src/main/res/drawable-nodpi'
web_assets = ROOT / 'MyStudyCompanionWeb/assets'
for target in (wear_assets, web_assets):
    target.mkdir(parents=True, exist_ok=True)
    for slug in slugs:
        source_matches = tuple(app_assets.glob(f'theme_scene_{slug}.*'))
        if len(source_matches) != 1 or source_matches[0].stat().st_size <= 1000:
            raise SystemExit(f'Missing or invalid Android static theme asset: {slug}')
        source_asset = source_matches[0]
        for stale in target.glob(f'theme_scene_{slug}.*'):
            if stale.name != source_asset.name:
                stale.unlink()
        shutil.copy2(source_asset, target / source_asset.name)

mapping_paths = (
    ROOT / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/ThemeArtwork.kt',
    ROOT / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/widget/DailyStudyWidget.kt',
    ROOT / 'MyStudyCompanion/wear/src/main/java/com/mystudycompanion/app/wear/WearTheme.kt',
    ROOT / 'MyStudyCompanion/wear/src/main/java/com/mystudycompanion/app/wear/WearThemeArtwork.kt',
)
for enum_name in STATIC_THEME_ENUMS:
    for path in mapping_paths:
        if enum_name not in path.read_text(encoding='utf-8', errors='ignore'):
            raise SystemExit(f'Static theme mapping missing: {enum_name} in {path}')

for asset_root in (app_assets, wear_assets, web_assets):
    for path in asset_root.rglob('*'):
        if path.is_file() and path.suffix.lower() in {'.gif', '.mp4', '.webm', '.lottie'}:
            raise SystemExit(f'Animated/live theme asset is forbidden: {path}')

# Final build gates must require the new static themes rather than delete them.
current_marker = 'msc-web-v0145-static-theme-auth-repair'
legacy_markers = (
    'msc-web-v0140-' + 'interactive-workbooks',
    'msc-web-v0141-' + 'unified-study-reader',
    'msc-web-v0142-' + 'complete-reader',
    'msc-web-v0143-' + 'theme-gallery',
    'msc-web-v0144-' + 'auth-theme-repair',
)
gate = ROOT / '.msc-build/fix-unified-study-reader-ci-gate-0.14.1.py'
gate_source = gate.read_text(encoding='utf-8')
for marker in legacy_markers:
    gate_source = gate_source.replace(marker, current_marker)
anchor = "  grep -Fq 'my-study-companion-private' MyStudyCompanionWeb/firebase.json\n"
extra = '''  grep -Fq 'Waterfall Serenity' MyStudyCompanionWeb/appearance.js
  grep -Fq 'Bible Map' MyStudyCompanionWeb/appearance.js
  grep -Fq 'BIBLE_MAP("Bible Map"' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/AppThemeMode.kt
  grep -Fq 'browserLocalPersistence' MyStudyCompanionWeb/firebase-sync.js
  grep -Fq 'getRedirectResult' MyStudyCompanionWeb/firebase-sync.js
  grep -Fq 'auth/popup-blocked' MyStudyCompanionWeb/firebase-sync.js
  grep -Fq 'msc-web-v0145-static-theme-auth-repair' MyStudyCompanionWeb/sw.js
  ! grep -R -F 'isLiveTheme' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design MyStudyCompanionWeb/appearance.js
  ! grep -R -F 'rememberInfiniteTransition' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design
'''
if extra not in gate_source:
    if anchor not in gate_source:
        raise SystemExit('Could not locate the final static-theme gate insertion point.')
    gate_source = gate_source.replace(anchor, anchor + extra, 1)
gate.write_text(gate_source, encoding='utf-8')

runner = ROOT / '.msc-build/run-interactive-workbooks-0.14.0-ci.sh'
runner_source = runner.read_text(encoding='utf-8')
for marker in legacy_markers:
    runner_source = runner_source.replace(marker, current_marker)
for check in (
    "grep -Fq 'Waterfall Serenity' MyStudyCompanionWeb/appearance.js",
    "grep -Fq 'Bible Map' MyStudyCompanionWeb/appearance.js",
    "grep -Fq 'BIBLE_MAP(\"Bible Map\"' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/AppThemeMode.kt",
):
    if check not in runner_source:
        runner_source = runner_source.rstrip() + '\n' + check + '\n'
runner.write_text(runner_source, encoding='utf-8')

print('PASS: 0.13.0 Google login behavior restored; all 23 themes preserved as polished static themes across phone, Fold, Wear, widgets, and web; no live themes.')
