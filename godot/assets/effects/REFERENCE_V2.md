# Wheel effects and displaced soil

Current production revision: runtime decorative tracks are disabled (including
their instance allocation). Sand emits 192 small grains + 24 clumps + 28 small
procedural powder quads per wheel; earth/mud uses 128/28/24, gravel 96/16/16,
and snow 160/20/28. Wet mud suppresses powder. Water retains a droplet bank.
Each bank owns its immutable tint, so existing particles never change surface.
All banks are preallocated, have no physics bodies or shadow passes, and stop
emitting when wheels leave the ground. Actual surface shader variants are
primed by the startup renderer. The old raster plume remains only for legacy
preview assets and the separate skid bank.

Production soil now uses a 25 cm signed field. Both wheel stamping and
bearing failure run on a private worker snapshot. Updates use 8 m bands;
adjacent changed bands publish together before vehicle physics. Restored
history is rebuilt with at most two forced bands per batch. A worker refines touched
one-metre terrain cells to quarter-metre triangles shared by rendering and
collision. Untouched collision retains its sparse grid. The field is bounded
to 65,536 samples, with amortized FIFO eviction and migration from legacy
metre-grid saves. Compacted loose roads allow half the off-road rut depth;
saturated mud events can exceed that limit through bearing failure.

The sand lab uses a continuous bowl/berm profile and refines only touched visual
tiles to 12.5 cm. Collision remains on its 25 cm heightmap. Refined heights retain
the physical grid vertices and differ by at most 1.8 cm from collision between
them. The lab's `sand_lab_checks.gd` verifies actual render arrays against rays.

Terrain and prop texture periods are doubled. Fine albedo contrast and
normal detail fade with the world-space pixel footprint to reduce grain
from the gameplay camera, while retaining the original high-resolution
mipmapped assets. Autosave serializes soil/JSON and performs atomic file
rotation on a separate worker; explicit saves flush pending soil first.

## Historical reference implementation

The original sprite implementation described below is retained for reference;
the production particle counts and geometry are listed above.

Reference: the user-provided 31.2-second off-road driving MP4, especially
the curved dusty turn around 18–21 seconds. Frames are analysis references
under `build/video-reference/`; no extracted frame is used as a game texture.

The implementation uses directional, signed soil heights: positive values
are depressions and negative values are displaced shoulders. Both are saved
and used by the terrain renderer and collision worker. Ordinary dry soil is
limited to 12 cm of compaction and 7 cm of uplift. Existing deep peat hazards
retain their separate bearing-failure behavior. The model approximates soil
displacement; it is not a mass-conserving granular simulation.

Fine tyre relief follows the underlying height changes with a bounded
32-segment refresh budget per frame. Its tint follows each local material.
Terrain shading preserves the original smooth normals and adds the change
in geometric slope after deformation. Reserved custom channels 14/15 hold
the original baked vertex height and its validity marker.

Dust uses 80 CPU particles per wheel, the existing Compatibility renderer,
a 3-second lifetime, speed/slip-dependent size and opacity, and independent
water droplets/grit. `billboard_keep_scale` is enabled so billboard alignment
preserves the size and growth of each particle. No emitters are added during driving. The original
1254-pixel alpha asset is kept; Godot imports a mipmapped 512-pixel texture
for the in-game particle workload. Phone GPU performance still needs an
on-device check; desktop deformation commit timing is not a mobile benchmark.

## Generated sprite

Asset: `res://assets/textures/wheel-dust-reference-v2.png`

Generated using the built-in ImageGen tool, with a transparent background.
The original generated image was copied into this project without repainting
or extracting artwork from the supplied video. Final generation prompt:

> Create a production game VFX texture: a SINGLE isolated soft billowing dust puff particle sprite, square composition, truly TRANSPARENT background alpha with very soft feathered translucent edges. Neutral white / pale grey monochrome so engine vertex tint can color it sand, earth or snow. A loose organic cluster of 5-7 soft rounded billows, asymmetrical and subtly shaded with a faint brighter upper side, broad airy cloud with wispy torn edges. Low contrast soft volume, no crisp lines, no grit, no ground, no cast shadow, no sky, no scenery, no border, no text, no grid, no checkerboard baked into color. Center the entire cloud within the canvas with at least 15% fully transparent margin on all sides. Designed for overlapping camera-facing sprites in an isometric offroad driving game, a dense low drifting sand plume, NOT an explosion or smoke mushroom. Use 1024x1024 if available.

Godot references: [CPU particle colors and lifetime ramps](https://docs.godotengine.org/en/4.7/classes/class_cpuparticles3d.html),
[MultiMesh instance data](https://docs.godotengine.org/en/4.5/classes/class_multimesh.html).

## Review and reproduce

- Open `scenes/world/deformation_preview.tscn` and use F6 for a saved terrain
  example with eight passes and a dust emitter. This is an isolated visual
  sample, not a change to the player's saved journey.
- The scene is generated by `tests/build_deformation_preview.gd` using the
  production terrain, deformation, material and collision builders.
- `tests/terrain_reference_checks.gd -- --test-session --offroad` runs the
  actual car on sand; a non-headless run saves `build/offroad-reference.png`.
- `tests/displaced_soil_checks.gd` verifies shoulders, direction, persistence,
  actual mesh/collision movement and settling of existing track detail.
