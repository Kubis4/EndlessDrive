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
- **W / up** accelerates; **S / down** brakes, then reverses once stopped.
- **A/D / left/right** steers, with speed-dependent steering travel.
- **Shift** reduces tyre grip for a handbrake slide.
- **Escape** returns to the cinematic patrol (a car left on a test ground is put
  back on its route).
- **R** restarts the current test, or places the car back on its patrol route.
- **P** pauses/resumes vehicles and dust; the orbit camera can still be moved.
- **C / Shift+C** cycles cameras: Overview (orthographic), Close top-down
  (orthographic, 8–60 m), Chase, Low chase, Orbit (mouse drag), Roof view.
- **+ / − / mouse wheel** zooms the current camera or changes follow distance.
- **1–8** teleports to a test, **T** to the next one (see *Test grounds*).
- **V** takes over the next patrol car; **Q** cycles Notebook/Reference/Low
  rendering at runtime; **H / F1** shows the controls overlay.

The cinematic view has no HUD. Manual driving displays speed in km/h, total mass
in kg, ground type, ford depth, reverse and airborne state in a small corner
readout, plus camera, quality and FPS along the bottom edge. Perspective cameras
use a pale procedural sky, stronger depth haze and 90 m shadow cascades; the
orthographic views keep the original lighting. Cameras follow the interpolated
body pose and never dip below the dunes.

## Test grounds

`terrain.gd` blends two flattened grounds into the shared height function, so
suspension, ruts, particles and rendering use the same surface:

- **Course** (x 62…120, z −112…−44, level 0.7 m): six 47 × 8.6 m lanes driven
  west from x = 114 — asphalt with slalom cones, gravel, clay, mud, a water ford
  (0.85 m basin, 7 m ramps) and staggered moguls followed by a 1.2 m tabletop jump.
- **Skid pad** (centre −78, −75): a 19 m radius asphalt circle with painted rings.

Each ground type sets tyre grip, rolling loss, rut compaction (asphalt none, mud
1.35×), dust/spray amounts and particle tint (`GRIP`, `ROLLING`, `DEFORM`, `DUST`,
`SPRAY`, `TINT`). Deformation is evaluated per sand texel only where a footprint
straddles two grounds. In the ford the submerged body receives quadratic drag and
partial buoyancy through the centre of mass; the water shader draws depth-tinted
murk, foam and vehicle wakes. `test_course.gd` builds the ground meshes at startup
(25 cm grid, 36 culled chunks, 0.2 M triangles, shared index buffers) and
`course.gdshader` draws asphalt with markings and cracks, rounded gravel, cracked
clay and wet mud with puddles procedurally, with no texture files. The dune shader
discards its fragments over both grounds, and fine sand patches hidden entirely
beneath a ground are never built. The authored dune mesh is unchanged; the builder
now writes it from `Surface.dune_height` so a rebuild keeps the two consistent.

## Ground speed, rut timing and tread marks

- Each ground caps the reachable speed as a fraction of the model's top speed
  (`SPEED`: sand 0.85, packed sand 0.92, asphalt 1.0, gravel 0.8, clay 0.72,
  mud 0.4, ford 0.28). Beyond it the engine stops pushing; on clay, mud and water
  an overspeeding car is also dragged down to the limit (bogging). The Jeep
  reaches about 86 km/h on dunes, 43 km/h in mud and 30 km/h in the ford.
- Ruts are stamped every physics tick once a tyre has travelled 12 cm from its
  last stamp, instead of every 75 ms, and the field reaches the GPU at 25 Hz
  (13 Hz on Android). The fine sand patch ahead of a moving car (0.7 s of
  travel) is queued in advance and built 24 rows per frame (12 on Android) with
  grid normals, so deformation appears directly beneath the wheels.
- Painted tread ribbons are off on every ground: the deformed sand, clay and mud
  show the passage; asphalt, gravel, rock and water show nothing.
- Deformation is more aggressive: rolling bearing layer 40 cm (was 28), slide
  limit 50 cm (was 34), raised banks up to 20 cm with 30 % of the rut depth (was
  14 cm / 20 %), compaction 0.55 per metre (was 0.32). On the dunes a first Jeep
  pass leaves about 15 cm (was 7), repeated passes 31–33 cm (was 22). Wheelspin
  at full throttle with little progress digs holes beneath the driven tyres.
  Rut floors are tinted as freshly turned sand. Two minutes of patrol still run
  without recoveries. The soil regressions now probe open dune sand at z ≈ 20,
  south of the rocky trail, with thresholds for the new depths.
- **Rocky trail** (`ROCKS`, x 40…118, z 68…120): a 4.6 m track along ten
  waypoints that crosses a lowered dune ridge twice, with embedded rocks,
  alternating 22 cm rock steps every 16 m and off-camber rolls, flanked by
  sandstone boulder fields (gravel ground). Rock grip 0.9, no ruts, half top
  speed. Rock relief and trail mask are baked into vertex colours for the shader.
  Both vehicles complete it (about 30 s at walking pace, up to 23° tilt).
- Touch controls are about 1.5× larger (176 × 150 pedals, 260 × 78 buttons in two
  columns, including AUTO for switching cars); HUD text is larger as well.
- Dust puffs fade within 6 m of the camera so a low chase view stays readable.

## Test-driving build (mobile performance pass)

- **Terrain-only scene.** Buildings, fences, utility poles and cables, figures,
  wrecks, barricades and rock fields were removed from the saved scene
  (1,116 → 70 nodes; 350 KB → 30 KB). `build_scene.gd` skips them via
  `BUILD_PROPS := false`, so a rebuild stays consistent.
- **Two vehicles:** the Jeep and the IZH van. The autonomous patrol is off
  (`patrol_enabled`; `-- --patrol` restores the cinematic diorama, and the
  two-minute regression turns it on). The game starts in control of the Jeep at
  the asphalt lane with the van parked on the gravel lane. A car nobody drives
  brakes, settles and then sleeps with no physics cost until driven (V) or hit.
- **Worker threads.** Fine sand patches are computed on the WorkerThreadPool and
  only turned into meshes on the main thread; the ford wave simulation (now
  30 Hz) also steps on a worker. Neither shows up in frame time any more.
- **Mobile field uploads** every 0.15 s instead of 0.075 s (half the bandwidth of
  the 2 MB rut texture); the live tyre segments (up to 5 m) hide the interval.
  The shaders only loop over occupied live-segment slots.
- **Fewer draw calls on Android:** the dune mesh is split into 36 m chunks
  instead of 24 m (100 instead of 225 instances per view/shadow pass).
- **Cheaper ground shader:** the sand material is evaluated only where it shows
  (lane borders, margins), not under every lane pixel.
- Headless main-thread cost while driving: **2.75 → 1.12 ms per frame** on the
  same machine (original 3-car patrol scene vs. this build). GPU time on phones
  falls mainly from the ~1,000 removed draw calls and their shadow passes.

## Four more lanes, full-width ford, treacherous mud

The course now has ten lanes (x 68–115) and extends north to z = −6. New, north
of the asphalt: **snow** (grip 0.34, a 14 cm raised layer that ruts like soft
sand, grey-blue compacted ruts, sparkle), **ice** (grip 0.10, glossy blue-grey
with frost and cracks; sliding leaves pale scratches), **grass** (grip 0.55, dry
patches, ruts tear it open to dark soil) and **wet asphalt** (grip 0.60, dark and
glossy with standing puddles). Keys 1–9 and 0 select the first ten tests (the
original six lanes first), T / Shift+T cycle through all thirteen.

- **Ford:** water now covers the whole lane: 6 m entry/exit ramps, 0.7 m deep
  centre and a few centimetres even at the lane edges; the wave grid and water
  plane cover the full 47 m × 8.6 m.
- **Mud:** top speed 22 % (about 24 km/h for the Jeep), rolling loss 0.16, rut
  scale 1.6, and arriving fast drags the car down at up to 8 m/s². Rolling
  resistance grows with the rut depth under each tyre, and a spinning tyre digs,
  so a car can bury itself. The front-drive van gets stuck; the Jeep grinds through.

## Interactive water, skid marks and live ruts

- **Ford water** (`water_ripples.gd`): a damped wave equation on a 0.35 m grid
  (about 2,700 cells) at 40 Hz. A car's floor pushes the surface down inside its
  footprint, water piles up ahead and along the sides in proportion to speed, the
  moving trough becomes a wake, and spinning tyres churn the surface. Dry cells
  are reflecting walls; shallow margins absorb energy like a beach. Height and
  vertical speed go to `water.gdshader` as a small RG float texture: the plane has
  one vertex per node and is displaced, normals come from the wave gradient, and
  crests/fast water turn into foam and silt-coloured, more opaque water. The
  simulation sleeps 8 s after the last car leaves the water.
- **Skid marks**: on asphalt and rock (no ruts), tyre slip above 0.3 — sliding,
  handbrake, or hard braking at speed, which now also counts as slip — writes a
  4 mm rubber layer into the shared field; the ground shader draws it as dark
  rubber. Physically negligible, and it appears instantly through the live
  segments below.
- **Softer test grounds**: deformation scale packed sand 0.6 (was 0.45), gravel
  0.4 (0.22), clay 0.8 (0.6).
- **Live ruts**: the field reaches the GPU at 25 Hz (13 Hz on Android), which in a
  low chase view showed as ruts appearing a car-length late. Each grounded tyre
  now sends a segment from its position at the last upload to its current contact,
  with the CPU field's rut depth there; `soil.gdshaderinc` takes the maximum of
  the uploaded field and these 12 tread profiles for displacement, normals and
  shading. The rut is therefore drawn immediately behind the wheels in every view.

## Rollover tuning

Previously ordinary full-lock steering rolled the van at 9 m/s and the Jeep at
26 m/s on flat ground, while handbrake slides stayed upright: the shared tyre grip
(μ 0.85) acted at the contact patch below a centre of mass whose static stability
factor is only about 0.96–0.98. Each axle now has an anti-roll bar (7,000 N/m Jeep,
6,000 N/m van), and the lateral tyre force's roll lever arm is 45 % of the contact
arm below 15 m/s, rising to 100 % at 23 m/s. Yaw and pitch use the physical arms.
Body roll in steady cornering is now 3–6° up to 65 km/h. In 100 slalom starts across the dunes
the van's rollovers fell from 10 to 2 at 12 m/s and from 53 to 9 at 20 m/s; the
Jeep's from 6 to 1 and from 14 to 6. Fast cornering, slopes and jumps can still
overturn the car. The rollover energy check includes the bars' stored energy.

## Optimisation in this update

- Hull/terrain contacts use a conservative temporal broad-phase: a support point is
  re-sampled only when it has moved far enough for the steepest possible surface
  (slope bound 3.0, plus 17 cm for raised banks) to close its last measured gap.
  Teleports and sampler changes invalidate the cache. Hull resolution cost fell
  from about 125 to 22 µs per substep.
- `depth_at` skips the 16-texel cubic lookup through a 4 m occupancy mask
  (3.7 → 0.6–1.0 µs); live wheels call the soil directly instead of a `Callable`.
- Pending sand patches are read without allocating key arrays every frame.
- Measured headless on the same machine, one Jeep on dunes: 880 → 630 µs per
  60 Hz tick (wall-clock, noisy, not a GPU benchmark).
- `Low` quality profile and runtime switching (Q) for weak integrated GPUs.

## Force-based vehicle motion

The chassis is no longer placed at the terrain height every tick. Four wheel
contacts apply unilateral spring/damper forces against the same displaced sand
field used for rendering. When all wheel loads fall to zero, the car follows
gravity and its existing linear/angular momentum. A crest therefore launches a
jump naturally; neither throttle nor steering provides airborne propulsion.
Suspension motion is visible at each wheel. Bump stops prevent terrain penetration
during fast landings without pulling airborne vehicles down to the surface.

Model presets are tuned for the demo: Jeep 1,850 kg, 11,000 N peak drive force and
110 kW power limit; van 1,450 kg, 6,500 N and 65 kW. These are gameplay values,
not certified vehicle specifications. The Jeep drives all wheels and the existing
game's van preset drives the front axle. `mass_override_kg` (zero = model default)
and `payload_kg` are inspector fields. Payload changes force/mass acceleration,
angular inertia and suspension sag. The same motor accelerates the loaded car
more slowly; gravity remains independent of mass.

Tyres share a longitudinal/lateral friction budget. Braking consumes grip,
cornering eventually slides, and the handbrake reduces rear lateral grip.
Drive force is limited by power at speed, reverse has its own limit, and steering
travel decreases at speed. Chassis dive and roll come from force application
below the centre of mass. Analytic contacts run at substeps no larger than 1/120 s;
obstacle motion uses swept chassis collisions with explicit time steps.
Drive force is distributed by load across the driven contacts, retaining useful
traction when one wheel unloads. The autonomous patrol brakes for tight corners
and anticipates curvature before bends. Its route follows the valley north of
the station, avoiding slopes beyond the front-drive van's traction. It performs
a short physical reversing manoeuvre if blocked for two seconds;
manual driving has no automatic recovery manoeuvres. Following vehicles brake
for the car ahead. The saved scene was regenerated so rock placement preserves
clearance around the revised patrol route.

Position and velocity refer to the centre of mass, 0.38 m above the mesh's wheel
hub datum. World angular momentum and the body's inertia tensor advance an
orthonormal basis; there are no pitch/roll limits or Euler-angle integration.
Normal and tyre forces share physical contact arms. Contacts solve inelastic
linear/angular impulses using effective mass, with dissipative hull friction.
Bump-stop correction never assigns upward velocity. The final contact sample is
reused by the next physics substep, avoiding identical terrain queries without
changing integration results. Gravity is 9.81 m/s²; sand
rolling resistance is a tuned 0.085 of normal load, alongside aerodynamic drag.
These are a lightweight real-time approximation, not a certified vehicle model.

The existing model convex shapes handle swept obstacle collisions. A bounded set
of their support vertices plus skid-plate probes handles terrain contacts on the
roof, sides and underside. Complete rolls, roof landings and sliding on the body
are possible. Wheels pointing away from the surface have no traction; throttle
and steering do not right the car. **R** explicitly resets it to the patrol route,
clearing linear/angular momentum and trail history. The HUD offers this reset
when overturned. There is no crash damage, clutch or deformable tyre simulation.
Tracks and dust require loaded tyre contacts, including after a rollover.

`handling_checks.gd` covers mass/sag, acceleration, physical brake/reverse input,
power-limited top speed, steering/roll, handbrake slip, ballistic flight, absence
of airborne control/marks/dust, damped landing, thin-wall collision, reset and
consistent motion at 30/60/120 Hz. Dune probes use the undeformed analytic surface
so their results do not depend on earlier test passes. From (-25, 45), a 6 m/s
approach stays grounded; 16 m/s rises 1.43 m above takeoff and flies 1.88 s. The
24 m/s stress case rises 8.20 m and flies 3.04 s: a steep ramp at that speed can
still produce a large jump. Both stay within v_y²/(2g), and neither is clamped.
The 700 kg payload probe reaches 10.41 m/s in three seconds versus 14.19 unloaded.

`rollover_checks.gd` independently verifies equal gravitational acceleration at
different masses, mechanical energy through an unpowered dune traversal, a full
physical tumble, roof support without penetration, settled angular motion,
absence of upside-down traction and explicit reset. Car-to-car impacts also check
equal/opposite momentum transfer and loss of kinetic energy. A tumble may return to its
wheels naturally; a separate roof drop verifies stable inverted support.
The launcher runs both suites before sand and two-minute patrol checks.

For real rendered jump, landing and rollover captures, run Godot with Forward+:
`--script res://desert_battlefield/capture_jump.gd -- --quality=notebook`.
It writes `build/handling-airborne.png`, `build/handling-landing.png` and
`build/handling-rollover.png`. The jump uses a regulated 16 m/s approach; the roof
capture is a controlled drop to check mesh/contact alignment.

Final verification on 30 October 2026: `Check -Quality Notebook` passed handling,
energy/rollover, soil and two-minute patrol/input checks. Cars travelled
662–719 m each with no reversing recoveries. Real rendered jump/landing and roof
captures also passed and were visually inspected (`build/rollover-render-final.log`).
A 60-second 1920×1080 native Notebook run on Core Ultra 5 135U / Intel Graphics
measured **32.063 ms median / 42.112 ms p95 / 54.455 ms p99** (31.2 median-equivalent
FPS, 1,756 samples after warmup), with 982,299 peak reported primitives. See
`build/rollover-benchmark.log`. Before reusing substep contacts, the same current
scene measured 33.538 / 48.438 / 58.065 ms; see
`build/rollover-benchmark-before-cache.log`. These are local wall-clock runs.
Earlier handling benchmarks used a different patrol route and deeper ruts.


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
- Mass-based chassis motion, four unilateral spring/damper contacts, tyre
  friction limits, aerodynamic/rolling drag, braking and rear handbrake slides.
  Dune crests can launch the chassis into ballistic flight and damped landings.
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
depression is 34 cm and maximum raised sand is 14 cm; cubic reconstruction softens
the visible peak. Ordinary rolling approaches a 28 cm bearing layer in the field;
sliding approaches 34 cm. Compaction slows near that layer instead of sinking forever.
In the straight-line test with both axles, actual reconstructed rut depth is about
7 cm after one vehicle passage, 19 cm after five, and 23 cm after twelve.
The former 72–80 cm bearing layer caused physical chassis grounding in repeated
patrol ruts; the reduced depth preserves visible deformation and passability.
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
- `vehicle.gd`: mass/inertia, spring/damper suspension, tyre forces, flight, contact-gated trails and dust.
- `handling_checks.gd`: deterministic mass, pedal, friction, flight, landing and collision checks.
- `rollover_checks.gd`: energy conservation, payload-independent gravity, full tumble and roof support.
- `capture_jump.gd`: rendered real-dune jump and landing regression.
- `endless_models.gd`: adapts the native Endless Drive rigs and reuses environment scenes.
- `track_trail.gd`: bounded incremental tread cache and stable tread coordinates.
- `battlefield.gd`: camera modes, quality profiles, test teleports and controls.
- `test_course.gd`, `course.gdshader`, `water.gdshader`: test-ground meshes,
  procedural surfaces, ford water and signs.
- `terrain_chunks.gd`: lossless spatial partitioning of the authored dune mesh for notebook culling.
- `sand.gdshader`, `dust.gdshader`, `grain.gdshader`, `tracks.gdshader`: procedural materials.

```powershell
.\tools\desert-battlefield.ps1 Build
.\tools\desert-battlefield.ps1 Check
.\tools\desert-battlefield.ps1 Capture
.\tools\desert-battlefield.ps1 Benchmark -Quality Auto -Duration 60
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

### Integrated GPU profile

`Auto` selects `Notebook` for an integrated GPU and `Reference` otherwise. The
profile applies when the scene starts, including F6 in a Forward+ editor, and
does not edit the shared project settings or generated scene. The standalone
window respects the launcher's requested size instead of forcing a resize.

Notebook retains Forward+, SSAO, all particle emitters, volumetric fog density,
light colours, 4096px directional shadows and the same cubic sand displacement.
It uses 2× MSAA + FXAA at native resolution, disables the subtle SSIL bounce and
reduces the fog volume size from 64 to 32 while retaining depth 64.
Use `-RenderScale 0.85` for optional FSR upscaling or `-Quality Reference` for the original
rendering settings. The launcher's `Benchmark` disables VSync, uses wall-clock
frame intervals and reports median/p95/p99, process/physics timing and primitives.
Headless `Check` is a simulation test, not a GPU benchmark.

The original 480×480 coarse terrain grid is partitioned into 225 independently
culled 24 m meshes, preserving its original vertices, normals and triangle winding.
Fine notebook patches use 67×67 vertices (8,712 triangles) rather than 131×131
(33,800 triangles). Their clipping halo and boundary matching remain intact.
The 25 cm physical field, cubic interpolation, upload interval and wheel support
are unchanged. Patrol route searches avoid computing discarded terrain heights.

`Check -Quality Notebook` additionally verifies the partition's shared boundary
vertices, material/mask binding, total triangle count and horizontal patrol path.
`Check -Quality Reference` retains the original fine grid. Both run the existing
rut, suspension, pause, bounded-cache and manual-driving regressions.

Before the force-based handling update, measured on 29 October 2026 on
**Core Ultra 5 135U / Intel Graphics**, Vulkan
Forward+, 1920×1080 output, VSync disabled: 60-second runs excluding the first
three seconds gave Reference **56.48 ms median / 67.98 ms p95 / 72.33 ms p99**,
native Auto/Notebook **30.74 / 37.99 / 42.45 ms**, and optional 85% FSR
**26.35 / 35.44 / 39.36 ms**. Median-equivalent frame rate rose from **17.7 to
32.5 FPS** at native resolution (1.84×), or **38.0 FPS** with FSR (2.14×).
Peak reported render primitives fell from 4,415,429 to 1,139,872. These are local
measurements, not a 60 FPS guarantee. Native resolution is the default because
FSR's small gain leaves more visible fine-edge noise on the deep ruts.

Logs are `build/demo-final-reference.log`, `build/demo-final-auto.log` and
`build/demo-final-fsr.log`. Overview captures use the same names with `.png`.
Both profiles passed soil checks and two minutes of fixed-step driving, with
identical final vehicle positions, soil edit count and bounded caches. Those
logs are `build/demo-final-check-{notebook,reference}.log` and
`build/demo-final-soil-{notebook,reference}.log`.

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
