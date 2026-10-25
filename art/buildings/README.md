# Roadside building artwork

Eighteen sprites generated with imagegen, September 2026: six original objects
and a winter and autumn edit of each. The approved
projection is directly front aligned, with a shallow visible roof and no side
wall. Do not mirror the sprites: the shop signs are part of the artwork.

Runtime sources live in `app/src/main/res/drawable-nodpi/building_*.png`:

- `house`: plaster cottage with tiled roof and teal shutters.
- `garage`: corrugated shed with rolling shutter.
- `fuel`: vintage station, two pumps and a canopy viewed from above.
- `repair`: vehicle workshop, SERVICE sign and repair bay.
- `paint`: approved PAINT SHOP workshop.
- `relay`: separate lattice radio mast with dishes and control cabinet.

`GameAssets` loads `BuildingSprites` once. Existing alpha is preserved and
magenta-backed sources are keyed and cropped during loading. No image decoding
or pixel processing occurs in the draw loop. Sources are decoded at half size.
`BuildingSpritePainter` preserves aspect ratio and anchors the cropped base to
the terrain. Daylight tint, winter roof accumulation, fuel availability and relay
status remain live. AUTO_SHOP uses the paint artwork once the existing 10 km
paint-service gate is met; before that it uses repair artwork. Wrecks retain their
vehicle sprites and are not architectural buildings.

Every source also has `_autumn` and `_winter` siblings. The winter artwork adds
snow, frost and icicles; autumn adds wet materials and fallen leaves. The original
camera, architecture and signs are preserved. `BuildingSeason.forEnvironment`
selects winter for ALPINE or snowy paving and autumn for FOREST_ALIVE, matching its
autumn backdrop. Other environments keep the original art. The same season is
used for the station and its relay mast. All three sets preload with GameAssets.
The previous procedural roof snow line is replaced by the authored winter art.

House, garage, fuel, repair and paint also have a `_night` PNG. These are full
nighttime material textures with their window and lamp light painted into the
image, not emissive masks or runtime overlays. The five silhouettes and canvas
dimensions match their base sprites exactly. `BuildingSpritePainter` crossfades
the whole daytime/seasonal sprite into the whole night sprite through twilight;
looted buildings remain on their dark daytime material. Relay keeps only its
small dynamic status signal and does not have a night texture.

`BuildingArtTest` checks all 18 transparent cutouts, distinct seasonal artwork and
stable rendering. It exports `building-seasons.png` from the actual Android
renderer: house, garage, fuel, repair, paint and relay columns, with original,
autumn and winter rows. It also verifies the five night silhouettes and exports
`building-day-night.png`. `building-review.png` is the previous base-art gallery.

## Validation

- Debug APK and instrumentation APK build successfully.
- All eight current building/damage-art instrumentation checks pass on the
  Android emulator, including exact day/night silhouette alignment.
- Damage animation frame and fixed-capacity burst-buffer unit tests pass.
- Previous base-art full unit suite: 214 passed, 13 failed in physics/world/economy tests; those
  failures remain outside this artwork change.
- The additional Compose screen smoke test cannot initialize Espresso input
  injection on this device (`InputManager.getInstance` is missing).
