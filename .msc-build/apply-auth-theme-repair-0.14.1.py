from pathlib import Path
import re

ROOT = Path('.')
android = ROOT / 'MyStudyCompanion'
web = ROOT / 'MyStudyCompanionWeb'

rejected_enum = [
    'WATERFALL_SERENITY', 'RAINFOREST_HARMONY', 'OCEAN_MAJESTY', 'CELESTIAL_WONDER',
    'MOUNTAIN_SUNRISE', 'CREATION_GARDEN', 'BIBLE_SKETCH_STUDY', 'PARABLE_LINE_PANELS',
    'NOAHS_ARK', 'RED_SEA_DELIVERANCE', 'CREATION_SKY', 'BIBLE_TIMELINE', 'BIBLE_MAP',
]
rejected_keys = [
    'waterfall_serenity', 'rainforest_harmony', 'ocean_majesty', 'celestial_wonder',
    'mountain_sunrise', 'creation_garden', 'bible_sketch_study', 'parable_line_panels',
    'noahs_ark', 'red_sea_deliverance', 'creation_sky', 'bible_timeline', 'bible_map',
]
rejected_names = [
    'Waterfall Serenity', 'Rainforest Harmony', 'Ocean Majesty', 'Celestial Wonder',
    'Mountain Sunrise', 'Creation Garden', 'Bible Sketch Study', 'Parable Line Panels',
    "Noah’s Ark", 'Red Sea Deliverance', 'Creation Sky', 'Bible Timeline', 'Bible Map',
]

# Keep only the previously approved original and animal themes.
app_theme = android / 'app/src/main/java/com/mystudycompanion/app/design/AppThemeMode.kt'
app_theme.write_text('''package com.mystudycompanion.app.design

enum class AppThemeMode(
    val displayName: String,
    val motif: String,
    val description: String,
    val isAnimalTheme: Boolean = false,
    val isIllustratedTheme: Boolean = false,
) {
    CALM_LIGHT("Calm Light", "✦", "Misty sunrise lake, airy ivory, warm gold, and blue-gray"),
    PREMIUM_DARK("Premium Dark", "◐", "Starlit mountain lake, deep navy, muted teal, and refined gold"),
    WARM_EDITORIAL("Warm Editorial — White", "❧", "Bright white mountain lake, editorial foliage, bronze, and olive"),
    OWL("Owl", "🦉", "Great horned owl in a moonlit slate mountain forest", isAnimalTheme = true),
    FOX("Fox", "🦊", "Red fox above an amber mountain forest in charcoal and copper", isAnimalTheme = true),
    LION("Lion", "🦁", "Noble lion overlooking a bronze-and-gold sunrise savanna", isAnimalTheme = true),
    TIGER("Tiger", "🐅", "Bengal tiger moving through a refined black-and-orange forest", isAnimalTheme = true),
    MOONLIT_WOLF("Moonlit Wolf", "🐺", "Icy moonlight, charcoal mountains, silver mist, and a watchful wolf", isAnimalTheme = true),
    GOLDEN_OWL("Golden Owl", "🪶", "Soft parchment light, burnished gold, mountain mist, and a noble owl", isAnimalTheme = true),
    SAKURA_TIGER("Sakura Tiger", "🌸", "Warm ivory, coral blossoms, bamboo green, and a calm tiger", isAnimalTheme = true),
    AUTOMATIC("Automatic", "◑", "Calm Light by day and Premium Dark with system dark mode"),
}
''', encoding='utf-8')

artwork = android / 'app/src/main/java/com/mystudycompanion/app/design/ThemeArtwork.kt'
artwork.write_text('''package com.mystudycompanion.app.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.mystudycompanion.app.R

@DrawableRes
fun themeArtworkRes(mode: AppThemeMode): Int = when (mode) {
    AppThemeMode.CALM_LIGHT,
    AppThemeMode.AUTOMATIC -> R.drawable.theme_scene_calm_light
    AppThemeMode.PREMIUM_DARK -> R.drawable.theme_scene_premium_dark
    AppThemeMode.WARM_EDITORIAL -> R.drawable.theme_scene_warm_editorial
    AppThemeMode.OWL -> R.drawable.theme_scene_owl
    AppThemeMode.FOX -> R.drawable.theme_scene_fox
    AppThemeMode.LION -> R.drawable.theme_scene_lion
    AppThemeMode.TIGER -> R.drawable.theme_scene_tiger
    AppThemeMode.MOONLIT_WOLF -> R.drawable.theme_scene_moonlit_wolf
    AppThemeMode.GOLDEN_OWL -> R.drawable.theme_scene_golden_owl
    AppThemeMode.SAKURA_TIGER -> R.drawable.theme_scene_sakura_tiger
}

private fun themeArtworkAlignment(mode: AppThemeMode): Alignment = when (mode) {
    AppThemeMode.WARM_EDITORIAL -> Alignment.BottomCenter
    AppThemeMode.OWL,
    AppThemeMode.FOX,
    AppThemeMode.LION,
    AppThemeMode.TIGER,
    AppThemeMode.MOONLIT_WOLF,
    AppThemeMode.GOLDEN_OWL,
    AppThemeMode.SAKURA_TIGER -> Alignment.CenterEnd
    else -> Alignment.Center
}

@Composable
fun ThemeArtwork(
    modifier: Modifier = Modifier,
    mode: AppThemeMode = LocalThemeVisualIdentity.current.mode,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f,
) {
    Image(
        painter = painterResource(themeArtworkRes(mode)),
        contentDescription = "${mode.displayName} theme scenery",
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        alignment = themeArtworkAlignment(mode),
        contentScale = contentScale,
    )
}
''', encoding='utf-8')

study_theme = android / 'app/src/main/java/com/mystudycompanion/app/design/StudyTheme.kt'
source = study_theme.read_text(encoding='utf-8')
default_fn = '''private fun defaultColorsFor(mode: AppThemeMode): ColorScheme = when (mode) {
    AppThemeMode.CALM_LIGHT -> CalmLight
    AppThemeMode.PREMIUM_DARK -> PremiumDark
    AppThemeMode.WARM_EDITORIAL -> WarmEditorial
    AppThemeMode.OWL -> Owl
    AppThemeMode.FOX -> Fox
    AppThemeMode.LION -> Lion
    AppThemeMode.TIGER -> Tiger
    AppThemeMode.MOONLIT_WOLF -> MoonlitWolf
    AppThemeMode.GOLDEN_OWL -> GoldenOwl
    AppThemeMode.SAKURA_TIGER -> SakuraTiger
    AppThemeMode.AUTOMATIC -> CalmLight
}
'''
source, count = re.subn(
    r'private fun defaultColorsFor\(mode: AppThemeMode\): ColorScheme = when \(mode\) \{.*?\n\}\n\nfun themeColorScheme',
    default_fn + '\nfun themeColorScheme', source, count=1, flags=re.S,
)
if count != 1:
    raise SystemExit('Failed to replace defaultColorsFor.')
typography_fn = '''private fun typographyFor(mode: AppThemeMode): Typography = when (mode) {
    AppThemeMode.CALM_LIGHT -> CalmTypography
    AppThemeMode.PREMIUM_DARK -> PremiumTypography
    AppThemeMode.WARM_EDITORIAL -> EditorialTypography
    AppThemeMode.OWL,
    AppThemeMode.FOX,
    AppThemeMode.LION,
    AppThemeMode.TIGER,
    AppThemeMode.MOONLIT_WOLF,
    AppThemeMode.GOLDEN_OWL,
    AppThemeMode.SAKURA_TIGER -> AnimalTypography
    AppThemeMode.AUTOMATIC -> CalmTypography
}
'''
source, count = re.subn(
    r'private fun typographyFor\(mode: AppThemeMode\): Typography = when \(mode\) \{.*?\n\}\n\n@Composable',
    typography_fn + '\n@Composable', source, count=1, flags=re.S,
)
if count != 1:
    raise SystemExit('Failed to replace typographyFor.')
study_theme.write_text(source, encoding='utf-8')

for key in rejected_keys:
    for path in (
        android / f'app/src/main/res/drawable-nodpi/theme_scene_{key}.webp',
        web / f'assets/theme_scene_{key}.webp',
    ):
        path.unlink(missing_ok=True)

android_test = android / 'app/src/test/java/com/mystudycompanion/app/design/AppThemeModeTest.kt'
test_source = android_test.read_text(encoding='utf-8')
test_source, count = re.subn(
    r'    @Test\n    fun twentyThreePermanentThemesPlusAutomaticAreAvailable\(\) \{.*?\n    \}\n\n    @Test\n    fun thirteenCreationAndBibleAccountThemesAreFullIllustratedThemes\(\) \{.*?\n    \}\n',
    '''    @Test
    fun tenApprovedPermanentThemesPlusAutomaticAreAvailable() {
        val permanent = AppThemeMode.entries.filterNot { it == AppThemeMode.AUTOMATIC }
        assertEquals(10, permanent.size)
        assertEquals(10, permanent.map { it.displayName }.toSet().size)
        assertTrue(permanent.all { it.description.isNotBlank() })
        assertTrue(permanent.none { it.isIllustratedTheme })
    }

''', test_source, count=1, flags=re.S,
)
if count != 1:
    raise SystemExit('Failed to replace rejected-theme Android tests.')
android_test.write_text(test_source, encoding='utf-8')

# Restore the stable sign-in sequence: local persistence, redirect completion,
# direct popup result handling, and redirect only for a genuinely blocked popup.
firebase_sync = web / 'firebase-sync.js'
fs_source = firebase_sync.read_text(encoding='utf-8')
new_header = '''const SDK = "https://www.gstatic.com/firebasejs/12.16.0/";
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
fs_source, count = re.subn(
    r'^const SDK = .*?\n\}\n\nasync function workbookPageId',
    new_header + '\nasync function workbookPageId', fs_source, count=1, flags=re.S,
)
if count != 1:
    raise SystemExit('Failed to restore the web Firebase auth header.')
firebase_sync.write_text(fs_source, encoding='utf-8')

app_js = web / 'app.js'
app_source = app_js.read_text(encoding='utf-8')
app_source = re.sub(r'from "\./firebase-sync\.js\?v=\d+";', 'from "./firebase-sync.js?v=0144";', app_source, count=1)
old_click = '''    const user=await connect(updateAuthUi);
    await syncHousehold(user);'''
new_click = '''    const user=await connect(updateAuthUi);
    if(!user){ setSync("Completing Google sign-in…"); return; }
    await syncHousehold(user);'''
if old_click not in app_source:
    raise SystemExit('Missing web sign-in button anchor.')
app_js.write_text(app_source.replace(old_click, new_click, 1), encoding='utf-8')

appearance = web / 'appearance.js'
appearance_source = appearance.read_text(encoding='utf-8')
for key in rejected_keys:
    appearance_source = re.sub(rf'^  "{re.escape(key)}": .*?\n', '', appearance_source, flags=re.M)
appearance.write_text(appearance_source, encoding='utf-8')

index = web / 'index.html'
index_source = index.read_text(encoding='utf-8')
index_source = index_source.replace(
    'The original themes remain available, with 13 new creation and Bible-account concepts.',
    'Choose from the approved original and animal themes. Custom colors remain available through the visual color wheel.',
)
index_source = re.sub(r'appearance\.js\?v=\d+', 'appearance.js?v=0144', index_source)
index_source = re.sub(r'firebase-config\.js\?v=\d+', 'firebase-config.js?v=0144', index_source)
index_source = re.sub(r'app\.js\?v=\d+', 'app.js?v=0144', index_source)
index.write_text(index_source, encoding='utf-8')

sw = web / 'sw.js'
sw_source = sw.read_text(encoding='utf-8')
sw_source = re.sub(r'msc-web-v\d+-[A-Za-z0-9-]+', 'msc-web-v0144-auth-theme-repair', sw_source, count=1)
for key in rejected_keys:
    sw_source = re.sub(rf'^  "assets/theme_scene_{re.escape(key)}\.webp",\n', '', sw_source, flags=re.M)
sw.write_text(sw_source, encoding='utf-8')

(web / 'appearance.test.mjs').write_text('''import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const source=fs.readFileSync(new URL("./appearance.js",import.meta.url),"utf8");
test("only the approved permanent themes and automatic are present",()=>{
  const approved=["Calm Light","Premium Dark","Warm Editorial — White","Owl","Fox","Lion","Tiger","Moonlit Wolf","Golden Owl","Sakura Tiger","Automatic"];
  for(const name of approved) assert.ok(source.includes(name),name);
  const rejected=["Waterfall Serenity","Rainforest Harmony","Ocean Majesty","Celestial Wonder","Mountain Sunrise","Creation Garden","Bible Sketch Study","Parable Line Panels","Noah’s Ark","Red Sea Deliverance","Creation Sky","Bible Timeline","Bible Map"];
  for(const name of rejected) assert.ok(!source.includes(name),name);
});
test("visual color wheel remains and manual hex fields remain removed",()=>{
  const html=fs.readFileSync(new URL("./index.html",import.meta.url),"utf8");
  assert.ok(html.includes('id="appearanceColorWheel"'));
  assert.ok(html.includes('id="appearanceBrightness"'));
  assert.ok(!html.includes('appearance-primary-value'));
  assert.ok(!html.includes('type="text" data-color-role'));
});
test("Google login restores persistence and handles popup and redirect results",()=>{
  const auth=fs.readFileSync(new URL("./firebase-sync.js",import.meta.url),"utf8");
  assert.ok(auth.includes("browserLocalPersistence"));
  assert.ok(auth.includes("getRedirectResult"));
  assert.ok(auth.includes("const result = await modules.signInWithPopup"));
  assert.ok(auth.includes('error?.code === "auth/popup-blocked"'));
  assert.ok(!auth.includes('error?.code?.includes("popup")'));
});
''', encoding='utf-8')

# Repair generated validation text so rejected concepts cannot be mistaken for approved scope.
for path in list((ROOT / '.msc-build').glob('*')) + list((ROOT / '.github/workflows').glob('*.yml')):
    if not path.is_file() or path.suffix not in {'.sh', '.py', '.md', '.yml'}:
        continue
    text = path.read_text(encoding='utf-8', errors='ignore')
    text = text.replace('23 permanent themes', '10 approved permanent themes')
    text = text.replace('23-theme gallery', 'approved 10-theme gallery')
    text = text.replace('23-permanent-themes', '10-approved-themes')
    text = text.replace('themes=23', 'themes=10')
    text = text.replace('new_illustrated_themes=13', 'rejected_illustrated_themes_removed=13')
    text = text.replace('msc-web-v0143-theme-gallery', 'msc-web-v0144-auth-theme-repair')
    text = text.replace('msc-web-v0141-unified-study-reader', 'msc-web-v0144-auth-theme-repair')
    text = text.replace("grep -Fq 'Waterfall Serenity' deployed-appearance.js", "grep -Fq 'Owl' deployed-appearance.js")
    text = text.replace("grep -Fq 'Bible Map' deployed-appearance.js", "grep -Fq 'Sakura Tiger' deployed-appearance.js")
    path.write_text(text, encoding='utf-8')

for path in (web / 'README.md',):
    if path.exists():
        text = path.read_text(encoding='utf-8')
        text = text.replace('23 permanent themes', '10 approved permanent themes')
        text = text.replace('13 new illustrated scenes', 'rejected experimental scenes removed')
        path.write_text(text, encoding='utf-8')

# Final rejection and auth gates.
for enum_name in rejected_enum:
    for path in (app_theme, artwork, study_theme):
        if f'AppThemeMode.{enum_name}' in path.read_text(encoding='utf-8'):
            raise SystemExit(f'Rejected theme remains: {enum_name} in {path}.')
for key in rejected_keys:
    if key in appearance.read_text(encoding='utf-8'):
        raise SystemExit(f'Rejected web theme remains: {key}.')
for name in rejected_names:
    if name in appearance.read_text(encoding='utf-8'):
        raise SystemExit(f'Rejected theme label remains: {name}.')

print('Applied Google sign-in regression repair and removed all rejected experimental themes.')
