# Irregular road surfaces

All road families share world-locked wear at two unrelated scales, including
on the low graphics tier. The desert palette remains subdued; other roads use
similarly quiet, larger patches rather than pixel grain or mirrored rock folds.

- Sand: irregular wind deposits and soft broken verges.
- Gravel: sparse aggregate clusters, dusty compacted areas and eroded banks.
- Dirt/forest roads: mottled earth and local darker organic deposits.
- Mud: wet mottles and sparse drying fractures, with actual soil relief intact.
- Alpine roads: subdued gravel with dirty blue-grey snow deposits.
- Asphalt: warped branching cracks, dust islands, chipped paint and worn edges.
- Concrete/ice, where used by surface blends: fractures instead of a slab grid.

`road-weathering.png` is a **linear data texture**, not sRGB albedo. Its RGBA
channels contain broad deposits, clustered earth, aggregate and fractures.
Alpha is fracture data, never transparency. Mipmaps are enabled and alpha border
repair is disabled, to preserve all four channels. Regenerate deterministically
with `python tools/build_road_weathering.py` (Pillow and NumPy). No external art or
additional licensing is required. `weathered-asphalt.png` remains a separate sRGB
albedo swatch; its alpha controls paint survival. Regenerate that with
`python tools/build_weathered_asphalt.py`, or both via `tools/godot.ps1 BuildRoadArt`.

The shared terrain shader uses two samples of the packed wear field, with a
rotated large field warping the smaller field. It does not create decals,
additional draw calls, per-frame meshes or new physical potholes. Existing
physical holes and ruts retain their shared render/collision height field.

Road edges use seeded smooth noise in `DriveTerrain.road_coverage`, independently
on the two banks. The central six metres remain fully road material. The same
coverage controls numeric material blending and is baked into vertex colour
alpha; fine deformed vertices preserve it by interpolation. Road mesh height and
route topology are unchanged. Chunk-end normals now use the neighbouring height
sample, so adjacent chunks have matching light across their shared edge.

Runtime terrain, all three graphics tiers, render warmup and generated editor
previews bind the same material sources. The nine environment previews, the
deformation reference and the junction gallery have been rebuilt.
`road_surface_checks.gd` covers all six
environments, deterministic asymmetric edges, normalized blends, chunk seams and
soil refinement. Other driving/obstacle suites protect the actual vehicle path.

APK generation remains paused. Desktop checks do not establish phone performance.
