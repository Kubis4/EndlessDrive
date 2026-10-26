# Original desert art set

## Expedition scenery update — 26 October 2026

Twenty new editable native scenes in `assets/models/expedition/` now supply
the actual streamed game, not only concept art:

- mountain/alpine ridge sections plus full rocky/snowy peak silhouettes;
- swept-road tunnel portals/ribs and a smaller railway masonry portal;
- detailed diesel locomotive, tank, covered freight and timber wagons, with
  independent wheels and fitted moving collision boxes;
- mountain/alpine pines, juniper, woodland/wetland trees, dead tree,
  marsh reeds, fern patch and meadow shrub.

These models now also include actual image-based textures, not just colour
materials: `assets/textures/expedition-materials-v1.png` and its eight-layer
`expedition-materials-v1.res`. They cover sandstone/granite, bark, foliage,
timber, masonry, painted metal and snow. Each saved model directly binds its
texture layers, with local-space triplanar mapping and separate mipmaps.
The image was created with the built-in image generation tool. The exact
prompt, provenance, mapping and rebuild instructions are in
`assets/textures/EXPEDITION_TEXTURES.md`.

`scenes/world/expedition_art_gallery.tscn` displays all twenty source models
with a camera and lighting; open it in Godot and press F6. Every model is a
linked `.tscn` scene and can be moved, recoloured or edited independently.
Geometry is grouped by material to limit draw calls; vegetation keeps the
game's existing MultiMesh batching and trunk collision/anchors. The generator
does not run while driving. Tunnel shell/anti-bypass collision and rail gates
retain their existing gameplay logic. All 32 train wheels now turn in motion.

The sand passage is the material reference: ground, props, canyon and tunnel
rock have subdued atlas contrast, much less micro-bump and lower-amplitude
fine noise. Forest colour patches blend softly; snow uses muted blue-grey
rather than near-white. Original texture sources and physical surface types
remain intact. `expedition_surface.gdshader` supplies calm, image-textured
opaque local materials without alpha-card dithering, screen-space noise or
harsh bump.

Source: `art/expedition_models.gd`. Explicit rebuild:
`./tools/godot.ps1 BuildExpeditionArt`. This overwrites the twenty generated
model files and gallery; do not run it after manual model edits unless intended.
The Jeep asset/license and unrelated V3 content are unchanged.

Art direction: high, fixed oblique camera over a dusty low-poly desert road,
sparse vegetation, a sandstone ridge, wire fences, utility poles and an outpost.
The supplied video frame is a composition/mood reference only; no pixels,
models or assets were extracted from it.

Environment/UI art was newly authored. The active vehicle has since been replaced
by the user-supplied **Jeep Cherokee by Renafox**, CC BY-NC-SA 4.0; see
`assets/models/jeep/ATTRIBUTION.md`. It is a third-party model, not original project art.
The old Android GLB, texture atlas and copied
audio were moved out of this project to the recoverable, ignored folder
`../build/godot-first-slice-assets-20261023/`. The Android source application
and its own resources remain separate.

## Models

`assets/models/` contains eight editable Godot scenes authored from new mesh
geometry in `art/desert_models.gd`:

- `dune_courier.tscn`: legacy, inactive two-door 4x4 pickup, cargo bed, spare wheel,
  four independent wheel pivots, bumper, grille, lamps and mirrors.
- `adobe_workshop.tscn`: earthen workshop, timber, corrugated awning and sign.
- `mesa_rock.tscn`: faceted sandstone used at rock and escarpment scales.
- `saguaro.tscn` and `desert_scrub.tscn`: cactus and dry vegetation.
- `utility_pole.tscn`, `ranch_fence.tscn`, `water_tower.tscn`.

`art/biome_models.gd` adds newly authored code-native layered conifers,
wetland trees and faceted snow caps. The terrain mesh, fork roads and clipped
water meshes are generated from `scripts/terrain_profile.gd`; no original
Android geometry, vegetation or textures are used. Terrain colour transitions
and road coordinates are passed to the shader by that shared profile.

`art/stop_models.gd` authors the five stop buildings; `scripts/route_hazard.gd`
authors the train, warning barriers, bridge and cutaway tunnel. `item_icons.gd`
contains new SVG icon sources; `car_diagram.gd` draws the interactive vehicle
schematic. Surface sprites use a new radial SVG; tyre imprints are bounded
instanced meshes. None of these reuse the Android assets.

Open any `.tscn` in Godot to edit its meshes, materials and transforms.
From the repository root, `./tools/godot.ps1 BuildArt` rebuilds these scenes
from the source and overwrites manual scene
edits. Rebuild only when intentionally updating the generator.

The engine loop is newly synthesized by `art/desert_audio.gd`; it uses no
recording from the Android app. Ground/ruts and weathered paint use the new
shaders in `shaders/`.

## Texture provenance

`assets/textures/desert-materials.png` was newly generated with the built-in
image generation tool on 23 October 2026. It is one 2×2 atlas: sand, dusty road,
weathered metal and ivory paint. Its import settings generate mipmaps. It is
used by the model material shader, not just as concept art. The updated ground
shader uses continuous low-frequency procedural variation rather than tiled grain.

Generation prompt:

> Use case: stylized-concept. Asset type: a single square 2x2 material texture
> atlas PNG for an original low-poly desert driving videogame, NOT concept art
> or a screenshot. Four equally sized square tiles with perfectly straight
> boundaries at the exact horizontal and vertical center, no margins, no labels.
> Top left: warm ochre fine desert sand, very subtle broad wind ripples and
> sparse tiny grains. Top right: compacted pale sandy dirt track, faint fine dust
> and a few tiny gravel flecks, no tyre tracks. Bottom left: aged warm gray
> galvanized sheet metal, subdued mottling and occasional faint rust pinpricks,
> no panels or seams. Bottom right: dusty ivory automotive paint, subtle wear
> and faint dust, no features or symbols. Flat unlit orthographic surface scans,
> evenly lit, low contrast, muted warm natural tones. Each individual tile must
> repeat seamlessly along its edges; no directional light, cast shadows,
> horizon, objects, text, gridlines, frames, vehicle, plants or watermark. This
> is a brand-new texture asset, independent of any previous project assets.

Model shaders keep the atlas detail subtle. The ground shader separately draws
road markings and broad sand variation; physical compaction lives in the shared
terrain height field. AI-generated atlas edge continuity is not assumed exact.
