# Sirocco — Endless Drive desert tech demo

A standalone native Godot scene, built for a quiet, cinematic 1920×1080 view.
The existing Endless Drive startup scene and project rendering settings are unchanged.

## Open and play

From the repository root, run:

```powershell
.\tools\desert-battlefield.ps1 Play
```

The launcher selects **Forward+** for this run, enables the intended atmosphere and
indirect lighting, and uses a separate shader-cache profile under `build/`.
It discovers the local Godot installation; `-GodotPath 'C:\path\Godot_console.exe'`
or `GODOT_BIN` can select another installation. Tested with Godot 4.7.2 on Windows.

In the existing Godot editor, open `res://desert_battlefield/desert_battlefield.tscn`
and press **F6**. The editor's Compatibility renderer will omit Forward+ effects;
use the launcher for the full presentation without changing the main project.

## Controls

- **Space** switches between the autonomous cinematic view and driving the lead scout.
- **WASD / arrow keys** accelerate, reverse and steer.
- **Shift** reduces tyre grip for a handbrake slide.
- **Escape** returns to the cinematic patrol.
- **R** places the lead scout back on its patrol route.
- **P** pauses/resumes vehicles, dust and camera motion.
- **+ / −** adjusts the orthographic camera size.

No HUD, titles or other interface elements are drawn.

## Scene content

- A 360 × 360 metre dune field with smoothly interpolated terrain normals,
  restrained wind ripples, warm sand, and a flattened outpost foundation.
- A concrete relay shelter, two Endless Drive repair barracks, a reservoir, radio mast, supply crates, drums,
  wooden barricades, broken fences, seven utility poles and sagging cables.
- Seeded faceted limestone fields mixed with four Endless Drive desert rock variants,
  two abandoned vehicles, eight human figures, and a moving patrol of two Jeep
  Cherokees and one IZH-2715. The imported rigs retain their native mesh/material
  resources, wheel rotation and steering. Tyre contacts and tracks match each model.
- A roughly 60° downward orthographic camera with a slow cinematic drift and
  smooth follow mode. The terrain fills the frame without a visible horizon.
- Warm low-angle sunlight, four shadow cascades, SSAO, screen-space indirect
  lighting, volumetric atmospheric haze and matte physically based materials.
- Ground-following bicycle steering with lateral grip, slope response,
  handbraking, obstacle/vehicle collisions and animated wheels.
- Layered world-space dust: two low tyre jets, a spreading wind-driven wake,
  tumbling ballistic grains and small airborne specks. Tiny, softly edged GPU
  sprites replace the old box-shaped grains. Depth-based soft clouds blend into dunes and objects.
  Emission and sideways spray increase with speed and lateral slip; all emitters
  and their billow animation freeze with **P**.
- Actual sand deformation: all four tyres press ruts into a shared 25 cm height
  field, with cubic B-spline reconstruction in both CPU suspension and GPU rendering.
  This removes the old sharp cell transitions without increasing field memory.
  Raised shoulders and stronger excavation during a slide remain physical.
  Each repeated passage adds compaction according to actual wheel travel; a
  first passage is shallow and later passes progressively deepen the same rut.
- Fine 12.5 cm terrain patches replace the coarse surface only where tyres disturb
  it. Cubic displacement moves actual vertices and updates lighting normals.
  A shared clipping halo keeps patch boundaries free of MSAA cracks.
- Tread ribbons cache finished chunks. Only the newest 32-sample tail is rebuilt;
  earlier geometry and world-distance tread phase stay unchanged. Fine marks fade
  with distance and use pixel-derivative filtering to avoid shimmering.

The soil field covers the 256 × 256 metre playable area and retains deformation
throughout the current scene session, including after old tread ribbons expire.
It resets when the scene restarts. The station foundation stays firm, and vehicle
resets do not excavate a line between the old and new positions. The field's maximum
depression is 80 cm and maximum raised sand is 14 cm; cubic reconstruction softens
the visible peak. Ordinary rolling approaches a 72 cm bearing layer in the field;
sliding approaches 80 cm. Compaction slows near that layer instead of sinking forever.
In the straight-line test with both axles, actual reconstructed rut depth is about
18 cm after one vehicle passage, 50 cm after five, and 59 cm after twelve.
Stationary wheels do not dig, and subdividing identical travel does not change depth.
Geometry is bounded to 256 patches, built eight vertex rows per frame; patches share
their triangle indices. Field uploads are batched approximately 13 times/s and use
half floats, halving transfer size with under 0.25 mm height error.
Each vehicle keeps at most 24 tread chunks, 320 cloud/jet particles and 264 grain/speck
particles: **584 total, down from 610**. Fine particle emission decreases when zoomed
out. Particle parameters update at 20 Hz while emitters follow the wheels every tick.

Dust uses layered soft particle billboards within volumetric atmospheric fog;
it is not a fluid simulation. Vehicle support samples the deformed height field;
the scene does not implement wheel-by-wheel rigid-body suspension or combat.

## Edit or rebuild

The `.tscn` contains saved, editable native nodes and shared mesh/material resources;
the dune mesh lives in `dune_terrain.res`. Vehicle and environment assets are shared
with `res://assets/models/`; clone the entire project. No runtime downloads are needed.
Local fine terrain patches are created during driving. See [credits](../../TECH_DEMO.md#model-credits)
for the Jeep attribution and the supplied models' provenance.

- `build_scene.gd`: deterministic layout, geometry, lights and materials.
- `terrain.gd`: common terrain height/normal functions and patrol route.
- `soil.gd`, `soil.gdshaderinc`: CPU/GPU displacement, banks, local refinement,
  shared terrain normals and boundary continuity.
- `vehicle.gd`: driving, suspension, tyre tracks and dust intensity.
- `endless_models.gd`: adapts the native Endless Drive rigs and reuses environment scenes.
- `track_trail.gd`: bounded incremental tread cache and stable tread coordinates.
- `battlefield.gd`: cinematic camera and controls.
- `sand.gdshader`, `dust.gdshader`, `grain.gdshader`, `tracks.gdshader`: procedural materials.

```powershell
.\tools\desert-battlefield.ps1 Build
.\tools\desert-battlefield.ps1 Check
.\tools\desert-battlefield.ps1 Capture
```

**Build overwrites the generated scene and terrain resource**, including any manual
edits to those two files. Change the builder to preserve edits across rebuilds.
Changes to the terrain function require rebuilding the visible terrain.

`Check` verifies rut depth, raised banks, progressive depth across sixteen ordinary
vehicle passages, distance-subdivision invariance, drift excavation,
continuous slopes at old grid boundaries, half-float accuracy, cached tread reuse,
tile boundaries, suspension response, pause and teleport protection. It also runs
a two-minute simulated patrol and checks motion, grounding, bounded track history,
manual steering and return to cinematic mode. `Capture` runs the
actual renderer, saves `build/desert-battlefield-1080p.png`, and reports wall-clock
frame intervals after warmup. Headless fixed-step timing is not a GPU benchmark.
The launcher checks the engine log for script and renderer errors as well as the exit code.

The smoothing update was compared in identical sixty-second Forward+ runs at
1920×1080, 4× MSAA, on the local RTX 3070, excluding the first three seconds:
**p95 29.9 → 14.3 ms; p99 32.3 → 15.5 ms; median 17.1 → 11.5 ms.**
Logs are `build/battlefield-smoothing-before.log` and `build/battlefield-smoothing-after.log`.
These are wall-clock samples, not a hardware-independent FPS guarantee.

The deeper repeat-pass update also passed a sixty-second Forward+ run on the local
RTX 3070: median 5.56 ms, p95 7.24 ms, p99 7.80 ms, maximum 12.67 ms after warmup.
This is a separate run, not a controlled comparison against the smoothing baseline.
The log is `build/battlefield-deep-ruts-performance.log`.

With the Endless Drive vehicle, building and rock models integrated, the sixty-second
1080p Forward+ run measured median 8.52 ms, p95 11.78 ms and p99 12.46 ms on the
same RTX 3070. The two-minute patrol and imported wheel/contact alignment checks
passed. Log: `build/battlefield-endless-models.log`.

`rut_gallery.gd` renders the same two tight circles with repeated wheel passes for
visual regression. Before/after images are under `design/desert-battlefield/`.
Its optional `--vehicle-passes=N` argument applies N vehicles' front/rear axle
passes for a matched-camera shallow/deep comparison (`--output=res://../build/name.png`).
The saved pair is `design/desert-battlefield/ruts-one-pass.png` and
`design/desert-battlefield/ruts-repeated-passes.png` (twelve passes with sliding).
