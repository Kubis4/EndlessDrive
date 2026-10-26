# Endless Drive — Godot migration

This is a playable migration slice, targeting Godot **4.7.2**, with a new
desert art set and a high, fixed oblique overview camera.
The original Android Studio application remains at the repository root.

## V4 — current Godot version

V4 includes the audit stabilisation changes, textured expedition models,
the supplied low-poly vegetation with sparse placement, graphics settings,
and the revised camera/equipment controls. See [AUDIT_CHANGES.md](AUDIT_CHANGES.md)
and [the vegetation notes](assets/models/vegetation/README.md) for details.

**APK generation is temporarily paused.** The user reports that the existing
APKs do not work. Successful packaging/signing checks below do not establish
that the game runs on Android. Run V4 in the Godot editor; fixing Android
runtime/export issues is still pending.

## V3 — terrain, visibility and frame pacing

Mountain hairpins now climb about 55–67 metres, with narrower graded road
benches and steep physical slopes between the returning arms. The starter 4×4
completed the full first ascent with a 60.9 m gain and no recovery teleport.
Off-road shoulders, mud, snow/ice and water dissipate speed progressively;
water resistance vanishes at rest so an equipped vehicle can still crawl out.

Copper Canyon uses eroded sandstone terraces, dry washes and continuous
stratified walls. Dense forest mixes tall conifers and broadleaf crowns.
Wetlands contain rooted trees, reeds and shallow backwaters beside the road.
Tree geometry and materials are shared in per-chunk MultiMeshes. Occluded parts
of the actual car get an amber x-ray rim; visible parts retain their materials.
The rim follows the body and wheels and also works behind visual foliage.

Scenery root probes and placement now run as small frame-budgeted jobs.
Rear-light materials update only when their state changes; the hidden legacy
dashboard no longer performs a second set of terrain/UI updates every frame.
An isolated eight-second native sand driving sample on the local RTX 3070
improved p95 from 11.73 to 4.13 ms and p99 from 17.09 to 11.10 ms; its slowest
frame went from 23.30 to 16.38 ms. Different terrain, hardware and longer runs
can produce different timings. These figures are not mobile measurements.
The separate dense-forest drive measured 11.16 ms p99 / 16.76 ms maximum
over eight seconds, with no frames above 20 ms on the same desktop.

Open `scenes/world/canyon_preview.tscn`, `forest_preview.tscn`,
`swamp_preview.tscn` or `mountain_preview.tscn` for saved editable previews.
Visibility material: `shaders/vehicle_outline.gdshader`; controller and saved
instance: `scripts/vehicle_outline.gd`, `scenes/vehicle_visibility.tscn`.
`tests/v3_world_checks.gd` covers resistance, shortcuts, biomes and shared rims.

## Long off-road expedition

The variant C HUD keeps distance and essential warnings in the upper corners,
with a collapsible equipment fan at the bottom. The tool button exposes drive
mode, differential, winch, engine, cruise and vehicle service. Touch steering
and pedals support independent fingers and release on drag-out or modal entry.
Touch Auto/On/Off is saved separately; keyboard controls remain available.
The landscape layout expands to phone aspect ratios and respects mobile safe
areas. Edit `scenes/ui/expedition_hud.tscn`; its Preview Equipment property shows
the fan in the editor. `tests/hud_c_checks.gd` covers input and adaptive layout.

The drivetrain starts in 4×4. **V** switches rear 4×2 / 4×4 at low speed,
**B** toggles the differential lock, **G** attaches/releases the front winch to
a visible tree, pole or rock within 30 m, and **H** starts/stops reeling.
The winch consumes battery and pulls through normal collision physics. Open
menus stop reeling. Range limits are 79 km/h in 4×2, 58 km/h in 4×4, and
25 km/h with the differential locked; reverse is limited to 18 km/h. Off-road
traction improves with the driven front axle and lock, at a cost in wear and
fuel. Water increases drag and gradually cools a warm engine.

Covered roadside refuges supplement garages. Sandstorms begin only when real
cover is reachable ahead, with a warning before the moving dust wall advances.
Seeded mud, rock gardens, jumps, ditches, potholes and washouts grow more severe
over a 100 km expedition. Junction materials, ford sides and curves vary;
terrain texture layers blend across biome borders. The former tiled concrete
road uses graded gravel.

Pause offers **Restart journey** (same seed, progress reset), **Main menu**
(saves), and **Save and quit** (saves and exits). Settings offer Slovak and
English; `locales/en.json` supplies the extendable source-key catalog. Gameplay
saves keep their language-independent identifiers.

The October 24 integration passed the 127-check core suite and the full
46-check physical driving regression (4.6 km without a rescue/reset), plus
drivetrain, recovery, real prop anchors, localization and isolated disk-save/quit
checks. Physical slices at 10/50/100/120 km also pass. On the current RTX 3070,
the scripted tunnel-stream benchmark measured 20.1 ms p99 / 27.9 ms maximum;
first headlight activation measured 20.2 ms maximum. These are local desktop
measurements, not a guarantee for other hardware.

Fresh journey seeds now select distinct section curves, hill amplitudes and
secondary bends, alongside the varied starting biomes. The route keeps streaming
seven chunks beyond 100 km; there is no 5 km finish. A 2,048 m floating origin
keeps physics/camera coordinates close to the car while saves, route decisions,
terrain history and the distance counter retain journey coordinates. Terrain
textures retain their route-space coordinates across a rebase.

The physical tyre/chassis rig follows its support plane and settles quickly
after a bank or landing. Releasing steering damps residual yaw. Native tests
exercise a 22 m/s bank-to-flat transition and an origin shift during driving.

Rail crossings have open portals through both cliff belts and dark staging
tunnels for the actual moving train. The road tunnel connects to solid,
terrain-following mountain masses on both shoulders; its central arch remains
the driveable passage. Sand, exposed dirt on vegetation, compressed snow,
gravel and wet mud use different tyre-trail colours/materials and tread detail.

Seeded weather fronts last 130–290 seconds with varying strength, smooth
precipitation/visibility changes and gusts. Rain wets the ground, which dries
more slowly afterwards. Front state, precipitation mixes and wetness survive
saving; menus pause the simulation. Sandstorm chases remain a desert/canyon
hazard with real shelter, alongside the moving train and slippery terrain.

All six mechanical components wear; braking load increases brake wear and
radiator condition affects cooling. Every 8 km of actual driven travel adds a
small seeded service fault, including reverse/off-road travel. Progress through
this schedule is saved. A separate garage profile stores the best distance.
Buildings still provide consumables, spare parts, repairs and finite fuel;
additional roadside caches and abandoned wrecks offer tools, fluids and parts.
They use persistent, selective looting rather than automatically granting stock.

Validation distinguishes resource planning from physical driving:

- `journey_balance.gd`: 64 fresh seeds × 120 km with supply/repair planning.
- `long_expedition_checks.gd`: real generated-physics slices near 10, 50, 100
  and 120 km, plus weather persistence, wear and source/trail checks.
- `vehicle_stability_checks.gd`: bank recovery and a live origin crossing.
- `route_structure_checks.gd`: actual portal openings and mountain collision;
  with a display, writes `build/rail-portals.png` and `build/tunnel-mountain.png`.

This does not claim a continuous 120 km physical-phone endurance run; physical
Android profiling and a full human-played long expedition remain to be done.

## Surface variety and 4x4 balance

New journeys start in a seed-selected desert, canyon, countryside, forest
or wetland. Snow is reserved for later regions, after equipment can be found.
Consecutive new rides avoid repeating the same starting biome; Continue retains
the saved seed, including existing snowy starts. A separate seed range preserves the
original starting environment of older journeys, including test seed 24601.

Fourteen physical materials have individual procedural texture detail: sand,
gravel, packed dirt, mud, grass, rock, snow, asphalt, concrete, red clay, salt
crust, forest litter, ice and peat. Independent material weights blend across
mesh triangles without accidentally creating road markings at material edges.
Rain changes ground darkening and roughness at runtime. Native 2K packed material
height, photographic albedo swatches and 512px dust plumes now add finer detail;
all imports have mipmaps. See [asset resolutions and generation prompt](assets/textures/HD_MATERIALS.md).
The editor snapshot uses the current four material-weight channels and exactly
the same shaders and texture resources as gameplay.

Structures stream as small, cancellable steps within the world commit budget.
Bridge/train parts share unit meshes; tunnel shells and mountain collisions have
separate commits. Numeric driveway/yard geometry is baked on the worker. A small
offscreen viewport primes structure, terrain, vehicle, light and sign shader variants while the
main menu is displayed. Freed chunk owners and obsolete epochs discard pending
jobs before invoking lambdas. `streaming_visual_checks.gd` checks completed
structure parity, cancellation, asset mipmaps and saved preview resources.
`structure_stream_benchmark.gd` measures individual events;
`route_stream_benchmark.gd` traverses 2.5 km with real terrain/prop/event streaming
and camera rendering at a scripted 36 m/s (physical driving is tested separately).

Ordinary soil has greater bearing strength and a shallower, slower compaction
response. Tyre wear is applied once, with useful four-wheel traction on banks
and shallow ruts. Rare dark peat pockets remain dangerous. Old excessive ruts in
ordinary ground are capped to the new bearing layer on world restore; journey
progress and recent track locations remain intact. Broad pre-existing wheel
channels are 15–24 cm deep, have tapered entries/exits, and use the same physical
height profile as rendering. The first 130 m and fork lanes stay clear of them.

`surface_balance_checks.gd` checks biome variety, all 14 materials, reduced sand
digging, exceptional peat and a real worn-4x4 sand-bank drive. The wetland field
test now verifies that ordinary mud can be crossed. `surface_gallery_capture.gd`
creates six GPU previews under `build/surface-*.png` when run with a display.

## Sand terrain reference update

The desert now has larger asymmetric wind-sculpted dunes, a warmer sand palette
and subtle world-space ripples. Dunes blend into the existing road and out at
biome boundaries. The rendered terrain uses a 1 m grid; collision retains the
proven 2 m support grid to avoid internal-edge catches in GodotPhysics. Both use
the same persistent compaction field. Collision commits remain throttled to 0.3 s:
more frequent replacement can trap a tyre during the initial acceleration.

All four tyre contacts now produce drifting, fading dust and short-lived ballistic
grit, with different colours/gravity on snow and water. Reverse changes the spray
direction; wheelspin can emit while stationary. Airborne and paved dry contacts
stop emitting. Continuous tyre strips follow turns and slopes, with small raised
shoulders and a bounded 2,048-segment ring. These strips are visual detail over the
physical ruts; the soil model remains a coarse height field, not granular sand.
Only loaded wheels compact, and each wheel checks its own surface material.

`tests/terrain_reference_checks.gd` drives over sand, checks trail/particle bounds,
airborne behaviour and per-wheel asphalt protection. Run it with a display to
write `build/terrain-reference.png`, without opening the player's save:

```powershell
& $env:GODOT_BIN --path godot --fixed-fps 60 --script res://tests/terrain_reference_checks.gd -- --test-session
```

## Terrain / frame-pacing update (Godot source)

- Vehicle physics interpolation and a render-time camera following the displayed
  vehicle pose remove the 60 Hz camera stepping. Static terrain does not interpolate.
- Tiny daylight changes are batched before updating the sky material, avoiding
  repeated reflection-cubemap invalidation on every rendered frame.
- Rocks, fences and poles join vegetation in geometry/material instance batches.
  Water-pool queries are cached per terrain row. Ground collision creation is staged
  in three 32 m bands instead of one whole-chunk collision bake on the main thread.
- The detailed mesh covers BOTH fork lanes, including the far lane 84 m away, and
  keeps a continuous coordinate frame at fork exits. The previous sparse outer cells
  could create a physical wall in the lower ford. Fork material exits blend over
  160 m; biome colour/grip transitions now span 360 m, with mixed scenery between them.
- Shallow fords follow longitudinal road elevation and are clipped against the
  rendered triangle bed and pool footprint. Rivers still have horizontal water levels.
  Fluid resistance grows with motion rather than acting as a static parking brake.
  Five real-physics worn-starter tests restart from rest and exit the water, including snow.
- Four wheel contact probes align the body and individual suspension to actual
  collision geometry, including lateral banking, not just the analytic road centre.
- Tunels have a swept arched shell following road curves and grades. The near half
  remains a cutaway for the overview camera; matching physical walls/roof remain solid.
  Crossing rails extend beyond the playable width, trains enter/leave off-map, and
  solid fences connect both crossing approaches to the existing map boundaries.
- Sign text stays in the sign's plane, without independently billboarding. Snow uses
  muted blue-grey ground and evergreen/snow mixes. Rain and snow have separate emitters
  that fade between conditions rather than changing the same particle mesh instantly.
- Terrain/water receive object shadows but do not cast self-shadows: this avoids the
  fine diagonal shadow-acne film in Compatibility rendering. Signed interpolated road
  coordinates eliminate diamond-shaped track artefacts. Ambient colour is now really
  used at night rather than being overridden by the nearly-black sky.
- Physical compaction lowers the height field, rendered vertices AND collision on sand,
  mud and snow. A separate worker computes local rut updates; only the changed band is
  committed on the main thread. Ruts add rolling resistance and influence contact/slope.
  This is a bounded, coarse terrain-height simulation, not granular soil or fluid dynamics:
  1 m sparse samples, a 1 m rendered / 2 m physical grid, at most 4,096 recent samples,
  and up to 1.10 m in the softest off-road mud/snow. Ordinary driven road tracks remain
  shallow (9/16/18 cm), while hard paved surfaces do not compact. Off-road bearing strength
  varies smoothly with biome, soil patches, snow and rain saturation. Static axle pressure
  settles soil; excess wheel torque shears it faster, even when the car is stationary.
  Cargo and tyre condition/footprint affect pressure; all-terrain/winter tyres affect
  usable traction. Four physical tyre contacts and a separate rounded belly allow actual
  chassis descent and high-centring, not a visual-only offset. Buried tyres lose traction
  and must push through soil; excessive throttle can dig the car into an immobile state.
  Wheel animation, particles, engine pitch and HUD penetration/slip match this state.
  Gentle throttle/reverse can help in moderate soil; there is no automatic rescue,
  winch, voxel displacement, soil-volume conservation or SnowRunner-level suspension solver.
  Recent ruts persist in journey schema 5; schemas 1–4 remain supported. Removed old samples
  are not an unlimited permanent world history.

`tests/terrain_rework_checks.gd` adds physical water restarts, collision/vertex rut
agreement, bounded history/save compatibility, arched tunnel clearance, shoulder
barriers and weather/lighting regressions. `tests/render_benchmark.gd` measures actual
GPU frame intervals during driving and threaded streaming on the current Windows PC.
Use F5 for these source changes; an already exported 0.5.0 APK does not update itself.

### Jeep Cherokee vehicle asset

The active car is now the user-supplied Jeep Cherokee, in
`assets/models/jeep/jeep_cherokee.tscn`. The original single-mesh GLB is retained.
An offline conversion separated all four existing tyre islands without removing or
duplicating any original triangles, preserving UVs and the albedo/ORM/normal maps.
The native rig is 4.73 m long / 2.12 m wide at the mirrors, with the existing 2.96 m
wheelbase and 0.44 m rolling radius. Wheel centres and cylindrical contacts use the
Jeep's 1.606 m track. A mesh-derived convex hull plus visible skid plate replace the
old chassis capsule. Front wheels/contacts steer, all tyres spin/reverse, suspension
follows terrain, and the previous driving/braking/cruise/soft-soil systems remain.
The body paint shader selectively recolours the original paint while retaining
glass, trim and wear; native light markers and brake-lamp overlays are functional.

**Third-party license:** model by Renafox, **CC BY-NC-SA 4.0**, not a commercial-use
asset. Attribution, original source/license links and adaptation details are in
`assets/models/jeep/ATTRIBUTION.md` and the in-game How to Play panel. Obtain suitable
permission or replace this asset before commercial release. Generated model resources
retain this asset license; do not treat them as original project artwork.

`tests/jeep_rig_checks.gd` checks native/code-created vehicles, original triangle
preservation, metre scale, pivot/contact alignment, forward/reverse spin, front-only
steering, paint and lights. `tests/build_jeep_rig.gd` is an explicit offline conversion,
not a gameplay startup step; do not run it after hand-editing the generated resources
unless you intend to regenerate them.

`tests/soft_soil_checks.gd` measures real physical descent, stationary wheelspin/bogging,
underbody support between deep channels, firm-road acceleration, tyre force differences,
time-step-independent compaction and deep-field save/restore. The collision contact shapes
are editable in `scenes/vehicle.tscn`.
`tests/soft_soil_field_checks.gd` additionally drives the worn starter across naturally generated
ordinary wetland mud using the real asynchronous terrain worker. Run it without `--headless` to
capture `build/soft-soil-world.png`; it never opens the player's save.

## Expedition update — 0.5.0

The game now starts in a native main menu inspired by the Android layout, using
the newly authored 3D world rather than the Android background image. Continue,
new-ride confirmation, garage, settings, instructions and quit are functional.
Volume/camera preferences are separate from journey progress.

- The production starting car is deliberately worn (30–42% component condition),
  with little fuel and a weak battery. Search stops, manage fluids, swap parts and
  repair it. The main-menu garage accepts **earned** scrap from the current ride;
  six three-level upgrades improve the **next** starting car, not the current one.
  Starting supplies cannot be farmed for upgrades. Deposit receipts prevent replaying
  an older journey save to duplicate a garage deposit.
- Loot has original vector icons, categories, quantity selection, purity/condition
  information and a car schematic. Filter changes keep explicit selections. Worse
  spare-part installations need confirmation; removed parts stay in cargo.
- Engine, tyre, brake, suspension, radiator and alternator variants have actual
  gameplay effects. Drum/disc/vented brakes use progressive pedal pressure and are
  limited by condition, load and grip. Brake input suppresses engine drive torque.
- Fuel/oil/coolant quality mixes by volume and affects power, consumption, wear
  and cooling. Repair kits, jump packs and paint cans have explicit consumable uses.
- Stops are 888–1032 m apart: workshop, home, gas station, repair shop and paint
  shop in a seeded cycle, on either side of the road. Gas pumps have finite stock
  and charge scrap; professional repairs and paint jobs have visible costs. Small
  road-debris containers can also be searched for scrap after stopping nearby.
- Junction arms no longer rejoin. Driving into one commits the route; future road
  position, altitude, seed, regions, stops and features derive from the saved choice.
  Roads still use forward-X streaming coordinates; this is not an unrestricted
  compass-direction road graph or a map with both entire futures loaded at once.
- Terrain arrays/collision data are computed on a worker. Scene additions are
  staged in small main-thread jobs; repeated vegetation uses MultiMesh instancing.
  The worker receives an independent route snapshot, and stale results are discarded.
- Sharper bends, visible climbs/descents, water clipped against the actual rendered
  triangle bed, a river/guard-railed bridge, a cutaway tunnel, and a real moving train
  crossing with collidable barriers, warning lights and a synthesized bell.
- Clear/cloudy/rain/fog/wind/snow weather; rain reduces grip. A spatial dust-storm
  chase can start when a reachable covered garage is nearby. Exposure damages the
  engine/radiator; stopping inside a workshop/repair/paint garage shelters the car.
  Weather and the active storm survive save/restore; menus pause their simulation.
- Four bounded dust/grit emitter pairs and a 2,048-segment tyre-trail buffer for loose
  surfaces, snow and water. These visual tyre details now accompany the physical
  compaction described above. Deep shoulder snow has lower grip and greater drag
  than packed road.
- Upcoming-stop distances and advance route-content forecasts are hidden. Current
  surface, current weather and nearby searchable objects remain visible.

There are no zombies yet. The reactive hazards in this update are the moving train,
barriers, knockable/scavengeable debris, weather, poor traction and the dust chase.

Verification is split deliberately: a real-physics 4.6 km drive checks abrupt stops,
cruise, terrain contacts and snow; a separate 64-seed × 100 km **resource-planning**
simulation checks supplies with looting/maintenance. The latter is not 6,400 km of
physical driving, does not prove an unlimited route, and does not model all player
mistakes, collisions or weather damage. Streaming-slice timing is measured on the
current Windows PC, not a guarantee of Android FPS. Physical-phone profiling remains
pending. `tools/godot.ps1 Test` runs the complete suite; `Balance` runs the planning model.

## Open and play

Import `godot/project.godot` into Godot, open it, and press **F5**.
The entry scene is `scenes/main.tscn`.

## Editing scenes visually

The main scene now contains an editor-visible terrain reference, vehicle,
lighting and camera. Gameplay uses native authored `scenes/ui/hud.tscn` and
`scenes/ui/main_menu.tscn`; select their root and change **Editor View** to preview
each screen in 2D without running the game. Start-camp placement and the five
stop models are also packed scenes used by the real streamed world. Manual
layout edits are not regenerated on startup. Dynamic inventory data and the
endless terrain remain procedural. See [EDITOR.md](EDITOR.md) for the Slovak
editing guide, scene locations and the distinction between preview and runtime assets.

The generated country-road preview in `scenes/world/editor_terrain.tscn` is linked
from the main scene. Select **EditorTerrainPreview**, set **Preview Seed** and
**Preview Distance**, then click **Obnoviť terén zo skriptov**. Baking runs in a
separate headless process and replaces only that generated geometry. Shared
texture/shader edits already update without rebuilding geometry. Standalone
`bridge_preview.tscn`, `rail_preview.tscn` and `tunnel_preview.tscn` show the actual
current structures in the 3D editor. `alpine_preview.tscn` shows the graded gravel
pass and snowy shoulders. Regenerate all five with
`tools/godot.ps1 RefreshPreview`; authored main/UI/vehicle/stop scenes are preserved.

Road width and shoulder shading follow the road's perpendicular direction on
bends. Loose surfaces have compacted wheel paths and native editable delineator
posts. Alpine roads use gravel, occasional shaded snow patches and a gentle
bench through higher ridges. Optional side trails retain 45–65 cm geometric ruts
(shallower in alpine terrain), while main-road ruts stay at 15–24 cm. Their baked
collision triangles carry the actual depressions. Scenery leaves the trails clear.

Steering angle reduces with speed, recentres promptly and damps residual sideways
motion. Coasting drag is lower; reverse is limited to 8 m/s. The vehicle does not
inherit moving-platform velocity from road cargo. Crates use zero bounce, damped
rotation and bounded horizontal contact impulses; collision sweeps still stop
the car against walls and verify small terrain steps.

The editable HUD has a native circular speed instrument, larger colour-coded
supplies with SVG icons, D/R/N status, a prominent throttle control, a synchronized
light button and an immediate road-direction/return cue. The cue hides during
nearby-building interaction and modal windows; it reveals no future loot sites.

Atmospheric fog is disabled in both the authored scene and runtime lighting,
including heavy rain. Saturated terrain/sky palettes retain clear local colour.
Rain and snow, stronger wheel plumes, dirt fragments, translucent water spray,
exhaust puffs and pavement braking marks remain local effects. Their buffers and
particle counts are bounded. New mipmapped timber/plaster/steel/foliage textures
are installed directly in the authored prop/stop scenes as well as generated
scenery. See [WORLD_MATERIALS.md](assets/textures/WORLD_MATERIALS.md) for provenance,
the exact imagegen prompt and editable material controls.

Choosing a fork no longer cancels unfinished collision bands in retained chunks.
Obsolete future chunks detach before their queued jobs run. A late choice inside
an affected chunk keeps its old floor until all replacement collision bands are
installed. Physical support prevents a newer numeric terrain profile from causing
a false fall recovery. Origin shifts preserve the camera's current follow lag
and zoom instead of resetting them. `route_commit_checks.gd` reproduces the
original missing-floor race and checks late replacement; `route_choice_drive.gd`
drives actual live branch decisions through the structures with worker streaming
(run with the real renderer/realtime physics, without `--fixed-fps`).

Outdoor headlight warmup includes the real terrain/car vertex formats, instanced
scenery, grit, tyre tracks and signs with zero tunnel lamps, then mixed tunnel
lighting. On the current RTX 3070/OpenGL desktop, the outdoor first-toggle spike
fell from 755 ms to 20 ms; repeated toggles peaked at 20 ms. A scripted 0.5 km
tunnel-stream traversal at 36 m/s peaked at 24 ms (p99 20 ms). These are desktop
measurements, separate from the physical driving regressions.

From PowerShell at the repository root:

```powershell
.\tools\godot.ps1 Editor
.\tools\godot.ps1 Play
.\tools\godot.ps1 Test
.\tools\godot.ps1 Android
```

The helper locates Godot on PATH, from `GODOT_BIN`, or in the ignored local
`build/godot-tools/` folder. Pass `-GodotPath 'C:\path\Godot_console.exe'`
to choose another installation. It imports assets before running checks.
For Android export, matching Godot export templates, an Android SDK and JDK
must be configured in Godot's editor settings.

## Controls

- **W / up:** throttle. **S / down:** brake; hold after stopping to reverse.
- **A/D / left/right:** steering.
- **E:** start/stop engine. **L:** headlights. **C:** three overview zoom levels.
- **R:** cruise throttle hold. Hold the throttle while driving forward, then
  press R (or within 0.8 seconds after release). It captures the pedal setting,
  as in the original game; it is not an automatic steering or speed controller.
  Brake, engine off, impact, map recovery, pause and service cancel it. Automatic
  throttle uses the original 1.5× fuel multiplier; manual override does not.
- **F:** search a nearby building while stopped.
- **Tab:** vehicle service while stopped. **Esc:** pause/resume.
- Touch buttons support simultaneous steering and pedals with separate finger IDs.

Start the engine, drive to the first workshop, stop next to it, and press F.
Searching reveals a paused loot window; nothing is collected automatically.
Select individual resources or spare parts, set fluid quantities, and use
"Vziať vybrané". Unselected supplies persist in that building across saves and
chunk unloading. Cargo has 10 slots and a 230 kg limit, matching the original
boot limits. Matching fluids merge into one stack; scrap is separate currency.

Open the vehicle panel to repair components with scrap, transfer fluids into
the vehicle, or inspect the cargo tab. Installing a spare exchanges component
condition and keeps the removed part in cargo. Scrapping a spare converts it
to repair material. Cargo weight also affects acceleration. Suspension condition
currently tracks wear/damage; the full original suspension simulation is pending.

## Implemented slice

- New Dune Courier pickup scene with independent rotating/steering wheels,
  newly synthesized engine audio, headlights and brake lights.
- 60 Hz arcade movement based on the Kotlin bicycle steering model, gravity,
  collidable terrain, trees, rocks and buildings, and impact wear.
- Seeded terrain with real bends, climbs/descents, crests and shallow potholes.
  A shared profile drives the collision mesh, road rendering and tyre physics.
  Six regions (desert, red canyon, countryside, forest, alpine pass, wetlands)
  blend over 160 m, including ground colours and traction, not just chunk palettes.
  Sand, gravel, asphalt, dirt, snow and concrete have different grip/rolling drag.
  All eight source model scenes are newly authored; new procedural conifers,
  wetland trees and snow caps are in `art/biome_models.gd`.
- Driveable shallow pools/bowls and flooded fords: water depth lowers grip and
  increases rolling resistance. The waterline is clipped to the actual terrain
  bed, not a floating rectangular overlay. First puddle near 207 m.
- Real divergent junctions: the first splits at 360 m and commits before 600 m;
  subsequent junctions are spaced across several 960 m regions. Steer onto the upper asphalt/hilly
  route or lower gravel/flooded route. Both have actual terrain/collision and
  are visible in the actual world. The chosen exit determines the continuing
  route and future generated regions; there is no advance destination forecast.
- Cacti, scrub, trees, poles and sagging wires, fences, workshop and water tower;
  seven streamed chunks with old geometry and collisions unloaded.
- Searchable stops, selective/partial loot, capacity preview, persistent
  remaining containers, spare parts, component exchange and salvage.
- Continuous overlapping large rock escarpments on BOTH sides, now near the
  road rather than beyond a climbable berm. Overlapping colliders cover chunk
  seams and airborne impacts. Base rock centre is 36 m from road centre and
  widens with the two junction arms. A separate last-resort recovery handles
  already-fallen/out-of-bounds saves. Terrain skirts extend to 120 m per side.
- Original-style cruise throttle capture with manual override and cancellation.
- Simplified engine health, fuel/oil/coolant, temperature, battery, alternator,
  tyre traction, brakes, wear, consumables and scrap repairs.
- Orthographic overview camera matching the reference composition: wide frame,
  high oblique angle and stable world azimuth while steering. Day/night cycle
  and long directional shadows. Camera settings are exposed on the main node.
- Restyled Slovak HUD with fluid/battery/temperature bars, warnings, compact
  driving controls, cruise status, service/cargo tabs and explicit loot selection.
- Periodic autosave, save on pause/exit, safe parked restore, validated JSON,
  atomic replacement and backup recovery.
- Compatibility renderer and landscape layout for the Android preview target.

## Saves and Android package

Godot saves schema v5 to the existing `user://journey-v1.json` path and can
read v1/v2/v3/v4 Godot saves. Old already-collected buildings remain empty; inventory
is preserved and new cruise state always starts off. These are **not** Kotlin
DataStore saves; there is no automatic import of existing player progression.
The preview package is `sk.kubis.endlessdrive.godotpreview` so it can coexist
with the original Android app. The preset makes a **debug APK**, not a
production release. Do not replace the original app on the store with it.

## Not migrated yet

This is not feature parity with the Android game. Pending work includes the
complete parts catalog and physical installation/removal, separate backpack
and boot transfers,
original vehicle/suspension model, tyre injuries/chains, complete weather/region rules,
unrestricted spatial road graphs, deep-ravine exploration,
relay missions, depot progression, trailer physics,
translations, achievements, Play Games, consent and rewarded advertisements.
The new regions have terrain, material, vegetation and traction differences,
but not all original regional survival/weather rules. There is no on-foot
exploration or building interiors yet.

Infinite-road streaming and origin rebasing are bounded. Physical Android device
profiling is still needed before long-run production use.

## Verification and previews

`tests/test_runner.gd` checks survival rules, corrupt-save recovery, actual
physics driving/steering/reverse/collisions, pause, touch separation, loot and
chunk streaming. Its disposable saves are under `build/godot-tests/`; it never
loads or overwrites the player's save when run through the helper.

Verified on 23 October 2026 with Godot 4.7.2:

- 127 automated rule/physics/input/save/art/camera/loot/cargo/boundary/cruise/terrain
  checks passed, including high-speed and airborne side impacts with recovery
  disabled, collision/profile agreement, smooth transitions, water effects and
  physically driving both junction arms through hills and the flooded ford.
- The new desert day and night/headlight scenes rendered and visually inspected on
  Windows using Intel Graphics / OpenGL Compatibility.
- Restyled driving, selective-loot, service and spare-cargo screens rendered
  and visually inspected with the real GPU at the desktop preview resolution.
- Fork split, water, lower cliff wall, forest and alpine transition rendered
  and visually inspected with the real GPU.
- Android debug export completed, signed and verified: Android 7.0/API 24
  minimum, target API 36, ARM64 and x86-64, approximately 58 MB.
- No Android device was connected; runtime behaviour, touch usability and
  performance on a physical phone have not yet been verified.

```powershell
.\tools\godot.ps1 Capture
.\tools\godot.ps1 Capture -Night
.\tools\godot.ps1 Capture -Service
.\tools\godot.ps1 Capture -Clean
.\tools\godot.ps1 Capture -Loot
.\tools\godot.ps1 BuildArt
```

Previews are rendered with the real GPU to the ignored repository `build/`
directory. Tests/previews and their scripts are excluded from Android exports.

The Godot project no longer uses the copied Android model, atlas or recordings.
See [art provenance and generation prompt](art/README.md) for the new model
sources and generated material atlas. The Android source application remains
separate. Open `assets/models/*.tscn` in Godot to inspect/edit the new models.


Adventure routes now include occasional elevated mountain roads with four true
hairpins, a ridge supply campsite and a descent; open
`scenes/world/mountain_preview.tscn` in the editor. Roundabouts are removed.
Buildings are about 1.9 km apart. Hold Space for the handbrake; L cycles off,
low and high beams, while K toggles high beams. Reverse lamps illuminate the
road behind the car. The reference-style mobile HUD is a saved editable scene.

Later fords include metre-deep pockets with a shallower side. Stock intake
height is 0.80 m; sustained immersion can stall/flood the engine. Install the
snorkel at a workshop/repair shop for 14 scrap through Vehicle → Cargo to
raise the intake to 1.85 m. Recover the car before drying the flooded intake
for 2 scrap; engine damage remains repairable separately. Finite local sand
fronts cross from three directions, last 65–100 seconds and then leave at
least 280 seconds of clear weather. Only actual dust exposure reduces visibility.
