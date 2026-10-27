# Natural materials and rounded rocks — 26 October 2026

The active expedition material set is `expedition-materials-v2.png` and its
eight-layer `expedition-materials-v2.res`. Generated with the built-in imagegen
tool; original 1774 × 887 pixels, extracted as eight independently mipmapped
443 × 443 layers without upscaling. V1 remains available as the previous source.

The saved expedition scenes and mesa rock bind V2 directly. Local triplanar
projection keeps textures attached to moving objects and origin shifts.
Terrain retains its existing photographic albedo array, now with visible
material contrast at driving distance. High quality adds bounded millimetre
normal detail; Balanced keeps photographic albedo; Low keeps its cheaper path.
Wetness still controls material darkening and roughness.

Texture detail only changes albedo, roughness and normals. It never offsets
terrain vertices or fakes tyre depressions. The shared signed soil height field,
fine mesh, physical collision, exposed-earth channels and persistence remain
the source of actual deformation. Normal detail is applied after rut lighting.

Rocky/snowy peaks have broad rounded crowns, offset shoulders and no needle
tips. Boundary ridges have wider crests. Mesa rocks use rounded irregular
rings and retain their original gameplay collider. The mountains stay outside
the road boundary; route collision/anti-bypass walls remain in place.

Rebuild the image array with `tests/build_expedition_textures.gd` using the real
renderer, then run `tests/apply_expedition_textures.gd` to update saved materials.
Rebuild only rock geometry with `tests/build_expedition_art.gd -- --only-rocks`.
Refresh the editor showcases and deformation preview after rebuilding.
The deformation preview builder explicitly finishes the fine soil mesh before
saving, so its channels and banks match the runtime geometry.

V5 (27 October): all nine saved terrain showcases are rebuilt. The editor
preview watches the saved scene modification time and reloads generated nodes
with fresh external mesh dependencies, preserving the preview parent's authored
transform. This fixes old pointed peaks remaining visible in an already-open
editor after source meshes had changed.

## Validation

Verified in Godot 4.7.2 on desktop: 72 expedition asset checks, 11 displaced-soil
checks, 25 terrain/vehicle checks, the physical soft-soil driving scenario and
route-structure collision checks all passed. The real OpenGL renderer produced
previews of all six biomes and the refined deformation scene without shader
errors. These are desktop checks, not an Android device performance claim.
All three quality tiers compiled/rendered successfully. Background GPU timing
was unstable (roughly 5–70 ms even with the original shaders), so these runs
do not establish a reliable performance improvement or regression.
V5 additionally passes 14 preview-geometry checks (including stale external
mesh cache reproduction and all nine saved scenes) and 18 editor-scene checks.
`preview_rocks_capture.gd` captures the actual saved main terrain from road level;
it verifies the rounded silhouettes outside the model gallery.

## Generation prompt

```text
Use case: photorealistic-natural. Asset type: production albedo texture atlas for a realistic off-road driving game. Create ONE rectangular 2048x1024 image, four equal columns and two equal rows, eight square material swatches touching, no gutters. Exact boundaries at 25%, 50%, 75% horizontally and 50% vertically. Photographic scanned natural surfaces, uniformly lit and flat orthographic, no perspective, baked lighting or cast shadows. TOP ROW left to right: warm grey beige weathered sandstone with irregular fine sedimentary strata and shallow erosion; neutral grey granite with subtle mineral aggregates and natural weathering; grey brown bark with realistic narrow fissures; dense desaturated olive green foliage and needles, no alpha. BOTTOM ROW left to right: weathered grey tan timber with fine natural grain, no plank boundaries; grey beige rough limestone masonry with irregular block joints; neutral mid-grey weathered painted steel with small subdued rust and scuffs, no panels or bolts; natural cool off-white compacted snow with fine granular crystals and faint compressed variation. Each swatch should tile independently, restrained realistic contrast, sharp authentic mid-scale material detail, physically plausible colour. No hand painting, illustration, cartoon style, dramatic deep cracks, heavy black outlines, objects, landscape, text, logos, borders or watermarks.
```
