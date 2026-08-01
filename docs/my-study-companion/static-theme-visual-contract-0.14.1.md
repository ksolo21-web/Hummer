# My Study Companion 0.14.1 static-theme visual contract

## Non-negotiable scope

All themes are static. Do not implement live, animated, video, GIF, Lottie, parallax, particle, or infinite-motion themes.

The 13 new themes must remain in the app and must be implemented to match the approved concept boards rather than replaced by generic gradients, placeholder scenery, simplified icons, or unrelated artwork.

Approved concept-board checksums supplied by Kaleb on 2026-08-01:

- Revised Theme Concepts 1–6 (`16799.png`): `56c39878fcbb78b0c59ad4d092ae118ca6bf0de66bafd86eb98858f9e8c5de58`
- Static Theme Concepts 7–11 (`16800.png`): `d2704a1b83d9b45ecb9e930c0493aed94739e1445abf147c8bd98383cceaaed4`
- Bible Timeline and Bible Map (`16803.jpg`): `e5407048f2fff148a92e13198b86d4fa5ba1c5d52a814e9a3475bab90a06bf49`

## Required new themes

1. Waterfall Serenity — luminous blue-green waterfall canyon, mist, river, cool translucent cards and blue accents.
2. Rainforest Harmony — deep green forest canopy, warm filtered sunlight, foliage, restrained green cards and navigation.
3. Ocean Majesty — dramatic ocean waves, blue sky and sunlight, crisp white/translucent cards and blue accents.
4. Celestial Wonder — premium dark star field, planets and constellations, violet/indigo glass cards and light text.
5. Mountain Sunrise — golden sunrise over layered mountains, warm ivory cards and amber/brown accents.
6. Creation Garden — bright sky, flowers, meadow and bird, light cream cards with olive, green and soft coral accents.
7. Bible Sketch Study — refined monochrome pencil/ink Bible-account study scene on warm white paper; not childish clip art.
8. Parable Line Panels — professional black-and-white line-art comic panels based on Bible parables, with clean editorial cards.
9. Noah’s Ark — detailed ark on elevated ground, rainbow, animals and bright sky, with warm cream cards and earthy accents.
10. Red Sea Deliverance — cinematic parted sea with a clear dry path and people crossing, white cards and deep blue accents.
11. Creation Sky — expansive golden sunrise, mountains, water, trees and birds, with ivory cards and warm gold/blue accents.
12. Bible Timeline — premium parchment timeline spanning major Bible-history periods, with illustrated historical landmarks, gold nodes and navy/gold navigation.
13. Bible Map — antique parchment map centered on Bible lands, sea, route markings and compass rose, with parchment cards and olive navigation.

## Existing themes that remain

Calm Light, Premium Dark, Warm Editorial — White, Owl, Fox, Lion, Tiger, Moonlit Wolf, Golden Owl and Sakura Tiger remain available.

## Cross-device rules

The same visual identity must carry through phone, Galaxy Z Fold layouts, tablets, web/PWA, Wear OS, widgets and preview cards. Cropping may adapt by device, but the subject, palette, lighting and style must remain recognizable.

Text and controls must remain readable without flattening the artwork. Cards may use controlled translucency, blur and tonal overlays, but must not hide the defining scenery.

## Authentication release block

Google sign-in must retain the working 0.13.0 lifecycle: durable local persistence, redirect-result completion, direct popup-result handling, redirect fallback only for an actually blocked popup, correct Firebase configuration, correct package identity and the Firebase-registered Android signing certificate. No release is accepted until sign-in succeeds in the installed app.
