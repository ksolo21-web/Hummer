#!/usr/bin/env python3
from __future__ import annotations

import re
from pathlib import Path


ROOT = Path("MyStudyCompanionWeb")
INDEX = ROOT / "index.html"
STYLES = ROOT / "styles.css"
SERVICE_WORKER = ROOT / "sw.js"

APPEARANCE_BUTTON = (
    '      <button id="appearanceButton" class="icon-button" '
    'type="button">Appearance</button>\n'
)

APPEARANCE_MODAL = '''  <div id="appearanceModal" class="appearance-modal hidden" aria-hidden="true">
    <section class="appearance-dialog" role="dialog" aria-modal="true" aria-labelledby="appearanceTitle">
      <header class="appearance-topbar">
        <div><p class="eyebrow">THEME GALLERY</p><h2 id="appearanceTitle">Choose your Study Companion scenery</h2><p class="muted">Choose from the permanent animal, creation, and Bible-study themes.</p></div>
        <button id="closeAppearance" type="button" aria-label="Close appearance settings">✕</button>
      </header>
      <div class="appearance-body">
        <div id="appearanceThemeGrid" class="appearance-theme-grid" aria-label="Available themes"></div>
        <section class="appearance-color-studio" aria-labelledby="colorWheelTitle">
          <div><p class="eyebrow">CUSTOM COLOR WHEEL</p><h3 id="colorWheelTitle">Tune the selected theme visually</h3><p class="muted">Choose a color role, drag around the wheel, then adjust brightness. No color code is required.</p></div>
          <div class="appearance-role-grid">
            <button type="button" data-color-role="primary" aria-pressed="true"><span class="appearance-swatch"></span><span>Primary accent</span></button>
            <button type="button" data-color-role="secondary" aria-pressed="false"><span class="appearance-swatch"></span><span>Secondary accent</span></button>
            <button type="button" data-color-role="background" aria-pressed="false"><span class="appearance-swatch"></span><span>App background</span></button>
            <button type="button" data-color-role="surface" aria-pressed="false"><span class="appearance-swatch"></span><span>Cards and panels</span></button>
          </div>
          <div class="appearance-wheel-layout">
            <div class="appearance-wheel-wrap"><canvas id="appearanceColorWheel" width="300" height="300" aria-label="Custom color wheel"></canvas><span id="appearanceCurrentColor" class="appearance-current-color" aria-hidden="true"></span></div>
            <div class="appearance-wheel-controls"><strong id="appearanceActiveRole">Primary accent</strong><label>Brightness<input id="appearanceBrightness" type="range" min="5" max="100" value="100"></label><button id="appearanceReset" type="button">Reset this theme’s colors</button></div>
          </div>
        </section>
      </div>
    </section>
  </div>

'''

APPEARANCE_CSS = r'''

/* Permanent static-theme gallery and visual color wheel. */
html{background:var(--bg)}
body{position:relative;background:linear-gradient(145deg,color-mix(in srgb,var(--bg) 94%,var(--secondary,#fff)) 0%,var(--bg) 64%,color-mix(in srgb,var(--bg) 88%,var(--accent)) 100%);isolation:isolate}
body::before{content:"";position:fixed;inset:0;z-index:-2;background-image:linear-gradient(var(--theme-overlay,transparent),var(--theme-overlay,transparent)),var(--theme-art,none);background-size:cover;background-position:center;opacity:var(--theme-art-opacity,0);pointer-events:none}
body::after{content:"";position:fixed;inset:0;z-index:-1;background:radial-gradient(circle at 85% 5%,color-mix(in srgb,var(--bronze) 18%,transparent),transparent 28rem);pointer-events:none}
.appearance-modal{position:fixed;inset:0;z-index:130;background:rgba(3,8,14,.78);display:grid;place-items:center;padding:max(10px,env(safe-area-inset-top)) max(10px,env(safe-area-inset-right)) max(10px,env(safe-area-inset-bottom)) max(10px,env(safe-area-inset-left))}
.appearance-modal.hidden{display:none}
.appearance-dialog{width:min(1180px,100%);max-height:min(97vh,1120px);display:flex;flex-direction:column;background:var(--surface);color:var(--text);border:1px solid var(--line);border-radius:28px;overflow:hidden;box-shadow:0 30px 100px rgba(0,0,0,.48)}
.appearance-topbar{display:flex;justify-content:space-between;align-items:flex-start;gap:16px;padding:20px 22px;border-bottom:1px solid var(--line);background:var(--strong)}
.appearance-topbar h2,.appearance-topbar p{margin:.15rem 0}
.appearance-topbar>button{width:46px;height:46px;flex:none;border:1px solid var(--line);border-radius:50%;background:var(--surface);color:var(--text);font-size:1.15rem}
.appearance-body{overflow:auto;padding:20px;display:grid;gap:22px}
.appearance-theme-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}
.appearance-theme-card{position:relative;min-height:245px;border:2px solid transparent;border-radius:24px;overflow:hidden;background-size:cover;background-position:center;box-shadow:var(--shadow);padding:0;color:#fff;text-align:left;cursor:pointer}
.appearance-theme-card .theme-motif{position:absolute;top:12px;right:12px;display:grid;place-items:center;width:38px;height:38px;border-radius:50%;background:rgba(0,0,0,.48);font-size:1.2rem}
.appearance-theme-card .theme-copy{position:absolute;inset:auto 0 0;padding:42px 16px 16px;display:grid;gap:6px;background:linear-gradient(transparent,rgba(0,0,0,.88))}
.appearance-theme-card strong{font-size:1.08rem}
.appearance-theme-card small{font-size:.78rem;line-height:1.35;color:rgba(255,255,255,.9)}
.appearance-theme-card.selected{border-color:var(--accent);box-shadow:0 0 0 4px color-mix(in srgb,var(--accent) 27%,transparent),var(--shadow)}
.appearance-color-studio{display:grid;gap:17px;border:1px solid var(--line);border-radius:24px;padding:20px;background:var(--strong)}
.appearance-color-studio h3,.appearance-color-studio p{margin:.2rem 0}
.appearance-role-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}
.appearance-role-grid button{display:flex;align-items:center;gap:10px;border:1px solid var(--line);border-radius:16px;padding:12px;background:var(--surface);color:var(--text);font-weight:750;text-align:left}
.appearance-role-grid button.selected{border-color:var(--accent);box-shadow:0 0 0 3px color-mix(in srgb,var(--accent) 20%,transparent)}
.appearance-swatch{width:34px;height:34px;flex:none;border-radius:50%;border:2px solid color-mix(in srgb,var(--text) 24%,transparent);box-shadow:inset 0 0 0 2px rgba(255,255,255,.35)}
.appearance-wheel-layout{display:grid;grid-template-columns:minmax(230px,340px) minmax(220px,1fr);gap:22px;align-items:center}
.appearance-wheel-wrap{position:relative;display:grid;place-items:center}
.appearance-wheel-wrap canvas{width:min(100%,300px);height:auto;border-radius:50%;touch-action:none;cursor:crosshair;box-shadow:0 10px 30px rgba(0,0,0,.24)}
.appearance-current-color{position:absolute;width:54px;height:54px;border-radius:50%;border:4px solid var(--surface);box-shadow:0 4px 16px rgba(0,0,0,.35);pointer-events:none}
.appearance-wheel-controls{display:grid;gap:16px}
.appearance-wheel-controls>strong{font-size:1.15rem}
.appearance-wheel-controls label{display:grid;gap:8px;font-weight:750}
.appearance-wheel-controls input[type=range]{width:100%;accent-color:var(--accent)}
.appearance-wheel-controls button{justify-self:start}
@media(max-width:900px){.appearance-theme-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.appearance-role-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:680px){.appearance-theme-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.appearance-theme-card{min-height:205px}.appearance-wheel-layout{grid-template-columns:1fr}.appearance-body{padding:12px}.appearance-topbar{padding:15px}}
@media(max-width:420px){.appearance-theme-grid{grid-template-columns:1fr}.appearance-theme-card{min-height:220px}.appearance-role-grid{grid-template-columns:1fr}}
'''


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"Expected one {label} anchor, found {count}.")
    return source.replace(old, new, 1)


html = INDEX.read_text(encoding="utf-8")
html = html.replace('manifest.webmanifest?v=0141', 'manifest.webmanifest?v=0143')
html = html.replace('styles.css?v=0141', 'styles.css?v=0143')

if 'id="appearanceButton"' not in html:
    sync_button = '      <button id="syncButton" class="icon-button" type="button">Connect Firebase</button>\n'
    html = replace_once(html, sync_button, APPEARANCE_BUTTON + sync_button, "appearance-button")

if 'id="appearanceModal"' not in html:
    reader_modal = '  <div id="readerModal" class="reader-modal hidden" aria-hidden="true">\n'
    html = replace_once(html, reader_modal, APPEARANCE_MODAL + reader_modal, "appearance-modal")

if 'src="appearance.js' not in html:
    script_pattern = re.compile(
        r'  <script src="pointers\.js[^\n]*?'
        r'<script type="module" src="app\.js[^\n]*?</script>'
    )
    scripts = (
        '  <script src="appearance.js?v=0143"></script>'
        '<script src="pointers.js?v=0143"></script>'
        '<script src="journeys.js?v=0143"></script>'
        '<script src="event-programs.js?v=0143"></script>'
        '<script src="firebase-config.js?v=0143"></script>'
        '<script type="module" src="app.js?v=0143"></script>'
    )
    html, count = script_pattern.subn(scripts, html, count=1)
    if count != 1:
        raise SystemExit("Expected one PWA script bundle anchor.")

INDEX.write_text(html, encoding="utf-8")

css = STYLES.read_text(encoding="utf-8")
if "Permanent static-theme gallery and visual color wheel" not in css:
    STYLES.write_text(css.rstrip() + APPEARANCE_CSS + "\n", encoding="utf-8")

service_worker = SERVICE_WORKER.read_text(encoding="utf-8")
service_worker, count = re.subn(
    r'const CACHE = "[^"]+";',
    'const CACHE = "msc-web-v0143-theme-gallery";',
    service_worker,
    count=1,
)
if count != 1:
    raise SystemExit("Expected one service-worker cache declaration.")
if '  "appearance.js",\n' not in service_worker:
    service_worker = replace_once(
        service_worker,
        '  "styles.css",\n',
        '  "styles.css",\n  "appearance.js",\n',
        "appearance service-worker asset",
    )
SERVICE_WORKER.write_text(service_worker, encoding="utf-8")

for marker in (
    'id="appearanceButton"',
    'id="appearanceModal"',
    'id="appearanceThemeGrid"',
    'id="appearanceColorWheel"',
    'id="appearanceBrightness"',
    'src="appearance.js?v=0143"',
):
    if marker not in INDEX.read_text(encoding="utf-8"):
        raise SystemExit(f"Missing repaired appearance marker: {marker}")

print("PASS: repaired the permanent PWA theme gallery, color wheel, and offline shell.")
