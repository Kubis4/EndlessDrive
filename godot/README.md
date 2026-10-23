# Endless Drive — Godot migration

This is a playable migration slice, targeting Godot **4.7.2**, with a new
desert art set and a high, fixed oblique overview camera.
The original Android Studio application remains at the repository root.

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
  1 m sparse samples, the existing 2 m rendered/physical grid, at most 4,096 recent samples,
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
`tests/soft_soil_field_checks.gd` additionally bogs the worn starter in naturally generated
wetlands using the real asynchronous terrain worker. Run it without `--headless` to
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
- Two bounded surface-sprite emitters and a 640-mark tyre-imprint buffer for loose
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
  subsequent junctions repeat every 960 m. Steer onto the upper asphalt/hilly
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

Infinite-road streaming is bounded, but large-coordinate rebasing and physical
Android device profiling are still needed before long-run production use.

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
