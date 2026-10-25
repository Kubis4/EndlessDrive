# Animated damage effects

The nine `fx_*_sheet.png` files are the authored 4×2 source strips. Runtime
uses only `app/src/main/res/drawable-nodpi/damage_fx_atlas.png`.

Atlas order is a 3×3 grid, left-to-right:

1. fuel leak, coolant leak, misfire
2. snapped belt, overheating, critical engine smoke
3. tyre blowout, rock strike, oil splash

Each effect tile contains four frames on the first row and four on the second.
The runtime bitmap is decoded once by `GameAssets`; `DamageFxPainter` selects
source rectangles without creating sliced bitmaps.

## Verification

- `DamageFxTest` covers looping/clamped frame selection, burst expiry and
  fixed-capacity overwrite.
- `DamageFxArtTest` checks atlas alignment, transparency and chroma removal,
  and exports `damage-fx-gallery.png` from the Android renderer.
- In the same 15-second accelerating emulator scene used before this pass,
  median frame time moved from about 29 ms to 25 ms. The emulator still reports
  substantial jank, so this is a relative renderer check rather than a
  real-device FPS guarantee.
