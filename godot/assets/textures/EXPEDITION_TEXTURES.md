# Expedition model textures

The active set is now [Natural materials V2](NATURAL_MATERIALS_V2.md), with
photographic-style surfaces and rounded rocks. The V1 assets below are retained
as historical sources; the texture builder now packs V2.

Generated on 26 October 2026 with the built-in image generation tool (imagegen
skill), then copied into this project. No API key or generation service is
needed to open, edit or play the saved project.

## Files and sampling

- `expedition-materials-v1.png`: original AI-generated 1774 x 887 image,
  preserved without painting or upscaling. Four columns, two rows.
- `expedition-materials-v1.res`: eight independently mipmapped 443 x 443
  Texture2DArray layers, packed from the source image. Tiny boundary fractions
  are omitted while extracting the odd-sized source; pixels are not enlarged.
- Layers 0–7: sandstone, granite, bark, foliage, timber, masonry, painted
  metal, snow. Glass and emitting lenses intentionally remain specialist
  materials rather than being covered with stone/wood patterns.

All twenty scenes in `../models/expedition/` directly reference the texture
resource in their saved ShaderMaterials. This is not a concept sheet or a
runtime-only colour effect. `expedition_surface.gdshader` samples the image
layers in local-space triplanar coordinates: models do not require hand-authored
UVs, moving train textures stay attached, and world rebasing cannot slide them.
Mirrored inset sampling handles imperfect generated edge continuity; layers
have separate mip chains, anisotropic filtering, restrained contrast and no
noisy micro-normal map. Existing model colours remain adjustable tints.

`art/expedition_materials.gd` selects layers, scale, average colour and roughness.
The alpine models use granite; train roofs use metal rather than masonry.

## Rebuilding and preserving edits

`tests/build_expedition_textures.gd` packs the source image with the real
renderer. Do not use `--headless`: the dummy renderer cannot serialize GPU
Texture2DArray images. The builder verifies the saved resource can reload.

`tests/apply_expedition_textures.gd` patches saved materials only, checking
that nodes, geometry, transforms and collision resources stay unchanged. It
does not rebuild meshes. `BuildExpeditionArt` is a separate, destructive-to-
manual-model-edits geometry generator; it is NOT needed to attach textures.
Refresh generated editor previews after intentional changes.

From the repository root: `./tools/godot.ps1 TextureExpeditionArt` repacks the
existing source image and reapplies textures without rebuilding geometry.
Then `./tools/godot.ps1 RefreshPreview` updates the saved editor snapshots.

## Final generation prompt

```text
Use case: stylized-concept. Asset type: production albedo texture atlas for a low-poly 3D survival driving game, NOT a screenshot, moodboard, rendered object, or material spheres. Create ONE rectangular 2048x1024 pixel atlas, precisely four equal columns and two equal rows, eight square 512x512 swatches, touching with no gutters or borders, exact boundaries at x=25%,50%,75% and y=50%. Each swatch is flat, front-facing, uniformly lit, seamless on its own opposing edges. Art direction: softly hand-painted believable materials that fit a warm, quiet sandy desert game; broad readable forms, restrained mid-scale detail, low contrast, matte, NOT grainy, no tiny noisy speckles, no harsh pores, no baked shadows. TOP ROW left to right: (0) warm pale sandstone with a few broad eroded horizontal strata and sparse shallow fractures; (1) cool muted grey alpine granite with broad mineral veins, no peppered grain; (2) warm grey-brown tree bark, readable vertical grooved strips with soft dark creases; (3) muted sage/olive foliage texture with overlapping broad leaf and needle-cluster silhouettes, no holes and no alpha. BOTTOM ROW left to right: (4) weathered pale timber, broad wood grain running vertically and a few subdued knots, NO plank boundary lines; (5) dusty warm grey limestone masonry, large gently irregular rectangular blocks and shallow subdued mortar joints, nothing black; (6) dusty neutral mid-grey painted metal with subtle broad scuffs, sparse small rust stains, NO rivets/panels/bolts/letters/stripes; (7) subdued cool grey snow with soft wind-compressed bands and a few shallow icy swirls, NOT bright white. This is an actual texture image to be sampled by shaders on mountains, tunnels, trains and vegetation. Avoid text, labels, logos, watermarks, frames, cast lighting, perspective, horizons, recognizable objects, props, terrain landscapes, checkerboards, fine grain and photographic high-frequency noise. Keep all swatches independently tileable and flat, with visible but calm material structure.
```
