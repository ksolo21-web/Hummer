# My Study Companion 0.14.1 approved static-theme visual contract

## Theme-only release scope

Google sign-in is already fixed in build 0.14.1 and must remain unchanged. This finish is limited to rebuilding the theme presentation and artwork integration.

All themes are static. Do not implement live, animated, video, GIF, Lottie, parallax, particle, or infinite-motion themes.

The 13 new themes must remain in the app and must match Kaleb's approved concept boards rather than generic gradients, placeholder scenery, simplified icons, or unrelated artwork.

## Required new themes

1. Waterfall Serenity — luminous blue-green waterfall canyon, mist, river, cool translucent cards and blue accents.
2. Rainforest Harmony — deep green forest canopy, warm filtered sunlight, foliage, restrained green cards and navigation.
3. Ocean Majesty — dramatic ocean waves, blue sky and sunlight, crisp white/translucent cards and blue accents.
4. Celestial Wonder — premium dark star field, planets and constellations, violet/indigo glass cards and light text.
5. Mountain Sunrise — golden sunrise over layered mountains, warm ivory cards and amber/brown accents.
6. Creation Garden — bright sky, flowers, meadow and bird, light cream cards with olive, green and soft coral accents.
7. Bible Sketch Study — refined monochrome pencil/ink Bible-account study scene on warm white paper; not childish clip art.
8. Parable Line Panels — professional black-and-white line-art panels based on Bible parables, with clean editorial cards.
9. Noah's Ark — detailed ark on elevated ground, rainbow, animals and bright sky, with warm cream cards and earthy accents.
10. Red Sea Deliverance — cinematic parted sea with a clear dry path and people crossing, white cards and deep blue accents.
11. Creation Sky — expansive golden sunrise, mountains, water, trees and birds, with ivory cards and warm gold/blue accents.
12. Bible Timeline — premium parchment timeline spanning major Bible-history periods, with illustrated historical landmarks, gold nodes and navy/gold navigation.
13. Bible Map — antique parchment map centered on Bible lands, sea, route markings and compass rose, with parchment cards and olive navigation.

## Existing themes that remain

Calm Light, Premium Dark, Warm Editorial — White, Owl, Fox, Lion, Tiger, Moonlit Wolf, Golden Owl and Sakura Tiger remain available.

## Presentation standard

- Use the approved compositions, subjects, lighting, palettes and atmosphere as the visual source of truth.
- Keep scenery and animals prominent rather than reducing themes to icons or flat recolors.
- Use modern translucent cards, controlled blur, refined shadows and high-contrast typography.
- Prevent duplicated UI artwork inside native cards; native controls remain crisp and interactive over the theme scene.
- The illustrated home experience includes Daily Text plus Weekly Study, Family Worship and Notes quick actions.
- The theme selector displays a large, recognizable preview of the approved design.
- Background, system bars and navigation must harmonize with the active theme.

## Cross-device rules

The same visual identity must carry through phone, Galaxy Z Fold layouts, tablets, web/PWA, Wear OS, widgets and preview cards. Cropping may adapt by device, but the subject, palette, lighting and style must remain recognizable.

Text and controls must remain readable without flattening the artwork. Cards may use controlled translucency, blur and tonal overlays but must not hide the defining scenery.

## Release gates

- All 23 permanent themes plus Automatic remain selectable.
- All 13 approved illustrated themes have both scene and selector-preview assets.
- Phone, Wear OS and PWA theme assets are byte-identical for each surface role.
- No live-theme implementation is present.
- Android and PWA source compile and verification gates pass.
- Google sign-in source and Firebase lifecycle are not modified by this theme-only finish.
