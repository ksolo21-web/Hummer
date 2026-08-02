#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import base64
import hashlib
import json
import shutil

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageOps

ROOT = Path('.')
ANDROID = ROOT / 'MyStudyCompanion'
WEB = ROOT / 'MyStudyCompanionWeb'
BUILD = ROOT / '.msc-build'
SPRITE = Path('/tmp/msc-approved-theme-sprite-v2.webp')
EXPECTED_SPRITE_SHA256 = '4e7720a6a2fc0ff0add3a0b75cd4e2c2b5e20d550940902e4c38774940698a3f'
CELL_W = 180
CELL_H = 360

THEMES = (
    ('waterfall_serenity', False),
    ('rainforest_harmony', True),
    ('ocean_majesty', False),
    ('celestial_wonder', True),
    ('mountain_sunrise', False),
    ('creation_garden', False),
    ('bible_sketch_study', False),
    ('parable_line_panels', False),
    ('noahs_ark', False),
    ('red_sea_deliverance', False),
    ('creation_sky', False),
    ('bible_timeline', False),
    ('bible_map', False),
)


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected one anchor, found {count}')
    return source.replace(old, new, 1)


parts = sorted(BUILD.glob('approved-theme-sprite-v2.part*.b64'))
if not parts:
    raise SystemExit('Approved theme sprite payload is missing.')
SPRITE.write_bytes(base64.b64decode(''.join(part.read_text(encoding='ascii').strip() for part in parts)))
actual_sprite_sha = hashlib.sha256(SPRITE.read_bytes()).hexdigest()
if actual_sprite_sha != EXPECTED_SPRITE_SHA256:
    raise SystemExit(f'Approved theme sprite checksum mismatch: {actual_sprite_sha}')

sprite = Image.open(SPRITE).convert('RGB')
if sprite.size != (900, 1080):
    raise SystemExit(f'Approved theme sprite dimensions are wrong: {sprite.size}')

manifest: dict[str, object] = {
    'sprite_sha256': actual_sprite_sha,
    'source': 'Kaleb-approved My Study Companion theme collection board',
    'themes': {},
}

for index, (slug, is_dark) in enumerate(THEMES):
    col = index % 5
    row = index // 5
    approved = sprite.crop((col * CELL_W, row * CELL_H, (col + 1) * CELL_W, (row + 1) * CELL_H))

    preview_background = ImageOps.fit(
        approved,
        (720, 1380),
        method=Image.Resampling.LANCZOS,
    ).filter(ImageFilter.GaussianBlur(18))
    preview_background = ImageEnhance.Brightness(preview_background).enhance(0.70 if is_dark else 0.90)
    preview_foreground = ImageOps.contain(
        approved,
        (690, 1350),
        method=Image.Resampling.LANCZOS,
    )
    preview = preview_background.convert('RGBA')
    x = (720 - preview_foreground.width) // 2
    y = (1380 - preview_foreground.height) // 2
    shadow = Image.new('RGBA', (720, 1380), (0, 0, 0, 0))
    draw = ImageDraw.Draw(shadow)
    draw.rounded_rectangle(
        (x + 8, y + 10, x + preview_foreground.width + 8, y + preview_foreground.height + 10),
        radius=24,
        fill=(0, 0, 0, 90),
    )
    preview = Image.alpha_composite(preview, shadow.filter(ImageFilter.GaussianBlur(12)))
    preview.alpha_composite(preview_foreground.convert('RGBA'), (x, y))
    preview = preview.convert('RGB')

    scene = approved.filter(ImageFilter.GaussianBlur(1.45))
    scene = ImageOps.fit(scene, (1200, 2400), method=Image.Resampling.LANCZOS)
    scene = ImageEnhance.Contrast(scene).enhance(1.05)
    scene = ImageEnhance.Color(scene).enhance(1.05)
    scene = scene.filter(ImageFilter.UnsharpMask(radius=1.0, percent=55, threshold=6))

    generated = {
        'scene': Path('/tmp') / f'theme_scene_{slug}.webp',
        'preview': Path('/tmp') / f'theme_preview_{slug}.webp',
    }
    scene.save(generated['scene'], 'WEBP', quality=93, method=6)
    preview.save(generated['preview'], 'WEBP', quality=94, method=6)

    for target_root in (
        ANDROID / 'app/src/main/res/drawable-nodpi',
        ANDROID / 'wear/src/main/res/drawable-nodpi',
        WEB / 'assets',
    ):
        target_root.mkdir(parents=True, exist_ok=True)
        for source in generated.values():
            shutil.copyfile(source, target_root / source.name)

    manifest['themes'][slug] = {
        'scene_sha256': hashlib.sha256(generated['scene'].read_bytes()).hexdigest(),
        'preview_sha256': hashlib.sha256(generated['preview'].read_bytes()).hexdigest(),
        'scene_dimensions': [1200, 2400],
        'preview_dimensions': [720, 1380],
    }

backdrop = ANDROID / 'app/src/main/java/com/mystudycompanion/app/design/ThemeBackdrop.kt'
text = backdrop.read_text(encoding='utf-8')
text = text.replace(
    'identity.mode.isIllustratedTheme && isDark -> 0.90f',
    'identity.mode.isIllustratedTheme && isDark -> 0.88f',
)
text = text.replace(
    'identity.mode.isIllustratedTheme -> 0.86f',
    'identity.mode.isIllustratedTheme -> 0.84f',
)
backdrop.write_text(text, encoding='utf-8')

home = ANDROID / 'app/src/main/java/com/mystudycompanion/app/ui/HomeScreen.kt'
text = home.read_text(encoding='utf-8')
text = replace_once(
    text,
    '''    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(padding.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Greeting(syncStateStore) }
        item { DailyTextCard(daily, onToggleConsidered) { onOpenAi(null) } }
        item { DailyFieldServicePointerCard(daily.date) }
''',
    '''    val illustratedTheme = LocalThemeVisualIdentity.current.mode.isIllustratedTheme
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(padding.dp),
        verticalArrangement = Arrangement.spacedBy(if (illustratedTheme) 12.dp else 16.dp),
    ) {
        item { Greeting(syncStateStore) }
        item { DailyTextCard(daily, onToggleConsidered) { onOpenAi(null) } }
        if (illustratedTheme) {
            item { ApprovedThemeQuickActions(onOpenStudy, onOpenFamily, onOpenAi) }
        }
        item { DailyFieldServicePointerCard(daily.date) }
''',
    'compact approved-theme actions',
)
text = replace_once(
    text,
    '''    val identity = LocalThemeVisualIdentity.current
    val surface = MaterialTheme.colorScheme.surface
    Card(
''',
    '''    val identity = LocalThemeVisualIdentity.current
    val surface = if (identity.mode.isIllustratedTheme) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
''',
    'greeting glass surface',
)
text = text.replace(
    '.heightIn(min = 148.dp),',
    '.heightIn(min = if (identity.mode.isIllustratedTheme) 104.dp else 148.dp),',
    1,
)
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
    'greeting nested-artwork guard',
)
text = replace_once(
    text,
    '''    val context = LocalContext.current
    val identity = LocalThemeVisualIdentity.current
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
''',
    '''    val context = LocalContext.current
    val identity = LocalThemeVisualIdentity.current
    val cardSurface = if (identity.mode.isIllustratedTheme) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardSurface),
''',
    'Daily Text glass surface',
)
text = replace_once(
    text,
    '''            val surface = MaterialTheme.colorScheme.surface
            ThemeArtwork(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(174.dp),
                mode = identity.mode,
            )
''',
    '''            val surface = cardSurface
            if (!identity.mode.isIllustratedTheme) {
                ThemeArtwork(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(174.dp),
                    mode = identity.mode,
                )
            }
''',
    'Daily Text nested-artwork guard',
)
text = text.replace(
    '            Spacer(Modifier.height(72.dp))',
    '            Spacer(Modifier.height(if (identity.mode.isIllustratedTheme) 8.dp else 72.dp))',
    1,
)

quick_actions = '''@Composable
private fun ApprovedThemeQuickActions(
    onOpenStudy: (String?) -> Unit,
    onOpenFamily: () -> Unit,
    onOpenAi: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ApprovedThemeAction(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.MenuBook,
            label = "Weekly Study",
            onClick = { onOpenStudy(null) },
        )
        ApprovedThemeAction(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.FamilyRestroom,
            label = "Family Worship",
            onClick = onOpenFamily,
        )
        ApprovedThemeAction(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.NoteAlt,
            label = "Notes",
            onClick = { onOpenAi("notes") },
        )
    }
}

@Composable
private fun ApprovedThemeAction(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .heightIn(min = 104.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 17.sp,
            )
        }
    }
}

'''
anchor = '@Composable\nprivate fun DailyTextCard(\n'
if quick_actions not in text:
    if text.count(anchor) != 1:
        raise SystemExit('Approved quick-action insertion anchor is missing.')
    text = text.replace(anchor, quick_actions + anchor, 1)
home.write_text(text, encoding='utf-8')

settings = ANDROID / 'app/src/main/java/com/mystudycompanion/app/ui/SettingsScreen.kt'
text = settings.read_text(encoding='utf-8')
text = text.replace(
    '.height(if (previewMode.isIllustratedTheme) 248.dp else 116.dp)',
    '.height(if (previewMode.isIllustratedTheme) 320.dp else 116.dp)',
)
text = text.replace(
    '"Original full-scene artwork inspired by the selected concept"',
    '"Approved full-screen design carried through phone, Fold, Wear OS, widgets, and web"',
)
settings.write_text(text, encoding='utf-8')

appearance = WEB / 'appearance.js'
text = appearance.read_text(encoding='utf-8')
old = 'root.style.setProperty("--theme-art-opacity",String(theme.artOpacity||0));root.style.setProperty("--theme-overlay",luminance(c.background)<.4?"rgba(3,8,12,.54)":"rgba(255,252,247,.48)");'
new = 'const illustrated=Boolean(theme.preview);root.style.setProperty("--theme-art-opacity",String(illustrated?.82:(theme.artOpacity||0)));root.style.setProperty("--theme-overlay",illustrated?(luminance(c.background)<.4?"rgba(3,8,12,.30)":"rgba(255,252,247,.18)"):(luminance(c.background)<.4?"rgba(3,8,12,.54)":"rgba(255,252,247,.48)"));'
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit('Web illustrated-theme overlay anchor is missing.')
appearance.write_text(text, encoding='utf-8')

styles = WEB / 'styles.css'
text = styles.read_text(encoding='utf-8')
illustrated_glass = '''
body[data-appearance-theme="waterfall_serenity"] .card,
body[data-appearance-theme="rainforest_harmony"] .card,
body[data-appearance-theme="ocean_majesty"] .card,
body[data-appearance-theme="celestial_wonder"] .card,
body[data-appearance-theme="mountain_sunrise"] .card,
body[data-appearance-theme="creation_garden"] .card,
body[data-appearance-theme="bible_sketch_study"] .card,
body[data-appearance-theme="parable_line_panels"] .card,
body[data-appearance-theme="noahs_ark"] .card,
body[data-appearance-theme="red_sea_deliverance"] .card,
body[data-appearance-theme="creation_sky"] .card,
body[data-appearance-theme="bible_timeline"] .card,
body[data-appearance-theme="bible_map"] .card {
  background: color-mix(in srgb, var(--surface) 91%, transparent);
  backdrop-filter: blur(18px) saturate(1.08);
  -webkit-backdrop-filter: blur(18px) saturate(1.08);
  border-color: color-mix(in srgb, var(--line) 72%, transparent);
}
'''
if illustrated_glass.strip() not in text:
    text += illustrated_glass
styles.write_text(text, encoding='utf-8')

service_worker = WEB / 'sw.js'
text = service_worker.read_text(encoding='utf-8')
text = text.replace(
    'msc-web-v0145-static-theme-auth-repair',
    'msc-web-v0145-static-theme-auth-repair-v2',
)
service_worker.write_text(text, encoding='utf-8')

(BUILD / 'approved-theme-finish-v2-manifest.json').write_text(
    json.dumps(manifest, indent=2, sort_keys=True) + '\n',
    encoding='utf-8',
)

for slug, _ in THEMES:
    for kind in ('scene', 'preview'):
        paths = (
            ANDROID / 'app/src/main/res/drawable-nodpi' / f'theme_{kind}_{slug}.webp',
            ANDROID / 'wear/src/main/res/drawable-nodpi' / f'theme_{kind}_{slug}.webp',
            WEB / 'assets' / f'theme_{kind}_{slug}.webp',
        )
        if len({hashlib.sha256(path.read_bytes()).hexdigest() for path in paths}) != 1:
            raise SystemExit(f'Approved theme differs across surfaces: {kind}/{slug}')

for path in (backdrop, home, settings, appearance, styles):
    source = path.read_text(encoding='utf-8')
    for forbidden in ('rememberInfiniteTransition', 'infiniteRepeatable', 'isLiveTheme', 'liveTheme'):
        if forbidden in source:
            raise SystemExit(f'Live-theme implementation is forbidden: {forbidden} in {path}')

print('PASS: 13 approved robust static themes installed from Kaleb-approved artwork; Google sign-in code was not changed.')
