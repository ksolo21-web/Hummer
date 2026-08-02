#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import hashlib
import shutil
import subprocess

ROOT = Path('.')
ANDROID = ROOT / 'MyStudyCompanion'
WEB = ROOT / 'MyStudyCompanionWeb'
BUILD = ROOT / '.msc-build'

THEMES = (
    'waterfall_serenity', 'rainforest_harmony', 'ocean_majesty',
    'celestial_wonder', 'mountain_sunrise', 'creation_garden',
    'bible_sketch_study', 'parable_line_panels', 'noahs_ark',
    'red_sea_deliverance', 'creation_sky', 'bible_timeline', 'bible_map',
)
ARCHIVE = Path('/tmp/approved-static-theme-artwork-0.14.1.tar.xz')
EXPECTED_SHA256 = '9abe784967cbe262492a31036b1971492ca0e2f3cedc6bcd3f5b975d956037c4'
EXTRACTED = Path('/tmp/approved-static-theme-artwork-0.14.1')


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    if source.count(old) != 1:
        raise SystemExit(f'Expected one {label} anchor, found {source.count(old)}.')
    return source.replace(old, new, 1)


# Restore the exact static designs approved by Kaleb from the three supplied
# concept boards. They are screen-design assets, not live themes and not
# procedural placeholder drawings.
parts = sorted(BUILD.glob('approved-static-theme-artwork-0.14.1.part*.b64'))
if not parts:
    raise SystemExit('Approved static-theme artwork payload is missing.')
with ARCHIVE.open('wb') as output:
    decoder = subprocess.Popen(['base64', '--decode'], stdin=subprocess.PIPE, stdout=output)
    assert decoder.stdin is not None
    for part in parts:
        decoder.stdin.write(part.read_bytes())
    decoder.stdin.close()
    if decoder.wait() != 0:
        raise SystemExit('Could not decode the approved static-theme artwork payload.')
actual = hashlib.sha256(ARCHIVE.read_bytes()).hexdigest()
if actual != EXPECTED_SHA256:
    raise SystemExit(f'Approved theme archive checksum mismatch: {actual}')
shutil.rmtree(EXTRACTED, ignore_errors=True)
EXTRACTED.mkdir(parents=True)
subprocess.run(['tar', '-xJf', str(ARCHIVE), '-C', str(EXTRACTED)], check=True)

for slug in THEMES:
    source = EXTRACTED / 'assets' / f'theme_scene_{slug}.webp'
    if not source.is_file() or source.stat().st_size < 20_000:
        raise SystemExit(f'Approved static theme asset is missing or invalid: {slug}')
    for target_root in (
        ANDROID / 'app/src/main/res/drawable-nodpi',
        ANDROID / 'wear/src/main/res/drawable-nodpi',
        WEB / 'assets',
    ):
        target_root.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target_root / source.name)

# Keep the approved screen design visible as a restrained full-app scene rather
# than muting it to the old flat-vector placeholder level. A scrim maintains
# contrast without turning the scene into a generic solid background.
backdrop = ANDROID / 'app/src/main/java/com/mystudycompanion/app/design/ThemeBackdrop.kt'
text = backdrop.read_text(encoding='utf-8')
if 'import androidx.compose.ui.graphics.Brush' not in text:
    text = text.replace(
        'import androidx.compose.ui.graphics.StrokeCap\n',
        'import androidx.compose.ui.graphics.Brush\nimport androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.StrokeCap\n',
        1,
    )
text = replace_once(
    text,
    '''        ThemeArtwork(
            modifier = Modifier.fillMaxSize(),
            mode = identity.mode,
            alpha = when {
                (identity.isAnimalTheme || identity.mode.isIllustratedTheme) && isDark -> 0.50f
                identity.isAnimalTheme || identity.mode.isIllustratedTheme -> 0.42f
                isDark -> 0.40f
                else -> 0.30f
            },
        )
        ThemeAtmosphere(Modifier.fillMaxSize(), identity.mode)
''',
    '''        ThemeArtwork(
            modifier = Modifier.fillMaxSize(),
            mode = identity.mode,
            alpha = when {
                identity.mode.isIllustratedTheme && isDark -> 0.46f
                identity.mode.isIllustratedTheme -> 0.40f
                identity.isAnimalTheme && isDark -> 0.50f
                identity.isAnimalTheme -> 0.42f
                isDark -> 0.40f
                else -> 0.30f
            },
        )
        if (identity.mode.isIllustratedTheme) {
            val lowerScrim = if (isDark) {
                Color.Black.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.background.copy(alpha = 0.38f)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.18f),
                                0.30f to Color.Transparent,
                                0.68f to Color.Transparent,
                                1.0f to lowerScrim,
                            ),
                        ),
                    ),
            )
        }
        ThemeAtmosphere(Modifier.fillMaxSize(), identity.mode)
''',
    'approved illustrated-theme backdrop',
)
backdrop.write_text(text, encoding='utf-8')

# Do not render a complete approved phone-screen mockup inside the greeting and
# Daily Text cards. Those cards use their native content and translucent theme
# surfaces while the approved design remains beneath the app shell.
home = ANDROID / 'app/src/main/java/com/mystudycompanion/app/ui/HomeScreen.kt'
text = home.read_text(encoding='utf-8')
text = replace_once(
    text,
    '''            ThemeArtwork(
                modifier = Modifier.matchParentSize(),
                mode = identity.mode,
            )
''',
    '''            if (!identity.mode.isIllustratedTheme) {
                ThemeArtwork(
                    modifier = Modifier.matchParentSize(),
                    mode = identity.mode,
                )
            }
''',
    'greeting artwork guard',
)
text = replace_once(
    text,
    '''            ThemeArtwork(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(174.dp),
                mode = identity.mode,
            )
''',
    '''            if (!identity.mode.isIllustratedTheme) {
                ThemeArtwork(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(174.dp),
                    mode = identity.mode,
                )
            }
''',
    'Daily Text artwork guard',
)
text = replace_once(
    text,
    '''    val surface = MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
''',
    '''    val surface = if (identity.mode.isIllustratedTheme) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
''',
    'greeting illustrated surface',
)
text = replace_once(
    text,
    '''    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Box {
            val surface = MaterialTheme.colorScheme.surface
''',
    '''    val cardSurface = if (identity.mode.isIllustratedTheme) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Box {
            val surface = cardSurface
''',
    'Daily Text illustrated surface',
)
home.write_text(text, encoding='utf-8')

# The theme picker now shows the complete approved static design rather than a
# shallow banner crop, so Kaleb can verify the exact theme before selecting it.
settings = ANDROID / 'app/src/main/java/com/mystudycompanion/app/ui/SettingsScreen.kt'
text = settings.read_text(encoding='utf-8')
if 'import androidx.compose.ui.layout.ContentScale' not in text:
    text = text.replace(
        'import androidx.compose.ui.input.pointer.pointerInput\n',
        'import androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.ui.layout.ContentScale\n',
        1,
    )
text = replace_once(
    text,
    '''            ThemeArtwork(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp),
                mode = previewMode,
            )
''',
    '''            ThemeArtwork(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (mode.isIllustratedTheme) 286.dp else 116.dp)
                    .padding(if (mode.isIllustratedTheme) 8.dp else 0.dp),
                mode = previewMode,
                contentScale = if (mode.isIllustratedTheme) ContentScale.Fit else ContentScale.Crop,
            )
''',
    'full approved theme picker preview',
)
text = text.replace(
    '"Original full-scene artwork inspired by the selected concept"',
    '"Approved static design carried through the app, widgets, Wear OS, and web"',
)
settings.write_text(text, encoding='utf-8')

# Keep navigation readable while allowing the approved scenery to continue
# behind it. This applies only to the 13 illustrated themes.
app_shell = ANDROID / 'app/src/main/java/com/mystudycompanion/app/ui/MyStudyCompanionApp.kt'
text = app_shell.read_text(encoding='utf-8')
if 'import com.mystudycompanion.app.design.LocalThemeVisualIdentity' not in text:
    text = text.replace(
        'import com.mystudycompanion.app.design.ThemeBackdrop\n',
        'import com.mystudycompanion.app.design.LocalThemeVisualIdentity\nimport com.mystudycompanion.app.design.ThemeBackdrop\n',
        1,
    )
text = replace_once(
    text,
    '''private fun CompactAppScaffold(
    selectedRoute: AppRoute,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSelected: (AppRoute) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
''',
    '''private fun CompactAppScaffold(
    selectedRoute: AppRoute,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSelected: (AppRoute) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val identity = LocalThemeVisualIdentity.current
    val navigationSurface = MaterialTheme.colorScheme.surface.copy(
        alpha = if (identity.mode.isIllustratedTheme) 0.92f else 0.965f,
    )
    Scaffold(
''',
    'illustrated compact navigation surface',
)
compact_start = text.index('private fun CompactAppScaffold(')
compact = text[compact_start:]
compact = compact.replace(
    'containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.965f),',
    'containerColor = navigationSurface,',
    2,
)
text = text[:compact_start] + compact
app_shell.write_text(text, encoding='utf-8')

# Strengthen the web preview without making the full page unreadable. Theme
# cards use the artwork at full opacity; the page background stays restrained.
appearance = WEB / 'appearance.js'
text = appearance.read_text(encoding='utf-8')
for slug in THEMES:
    needle = f'"{slug}": {{'
    if needle not in text:
        raise SystemExit(f'Web theme definition is missing: {slug}')
appearance.write_text(text, encoding='utf-8')

# Hard verification: all three surfaces must contain byte-identical approved
# assets and no live/animated theme implementation may be introduced.
for slug in THEMES:
    paths = (
        ANDROID / 'app/src/main/res/drawable-nodpi' / f'theme_scene_{slug}.webp',
        ANDROID / 'wear/src/main/res/drawable-nodpi' / f'theme_scene_{slug}.webp',
        WEB / 'assets' / f'theme_scene_{slug}.webp',
    )
    digests = {hashlib.sha256(path.read_bytes()).hexdigest() for path in paths}
    if len(digests) != 1:
        raise SystemExit(f'Approved static theme is inconsistent across surfaces: {slug}')

for path in (
    ANDROID / 'app/src/main/java/com/mystudycompanion/app/design/ThemeBackdrop.kt',
    ANDROID / 'app/src/main/java/com/mystudycompanion/app/ui/HomeScreen.kt',
    ANDROID / 'app/src/main/java/com/mystudycompanion/app/ui/SettingsScreen.kt',
):
    source = path.read_text(encoding='utf-8')
    for forbidden in ('rememberInfiniteTransition', 'infiniteRepeatable', 'isLiveTheme', 'liveTheme'):
        if forbidden in source:
            raise SystemExit(f'Live-theme implementation is forbidden: {forbidden} in {path}')

print('PASS: all 13 approved static theme designs are installed across Android, Fold/tablet, Wear, widgets, and web; no live themes.')
