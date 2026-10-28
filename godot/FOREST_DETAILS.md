# V5 forest detail follow-up

Fallen trees now require a nearby stand: two to five planned companion cells
are planted using the same 14 m lattice as ordinary trees. Scenery clearance
follows the actual detour and obstacle footprint, rather than clearing a whole
strip of forest. If enough neighbouring trees cannot fit, the site uses a
wreck instead. Tree positions and obstacle layouts remain seeded and do not
reroll after excavating soil or revisiting a chunk.

Forest scenery attempts remain at 88, with crown spacing increased to 14 m.
The heavier Calocedrus frequency is now 2.5%; most trees are the
412-triangle supplied models. Colliders and winch anchors remain present at
all quality settings.

The current scenery/material follow-up is described in
[SCENERY_REFRESH.md](SCENERY_REFRESH.md). The supplied meshes are used in snow
as well; the old multi-cone crowns are retained only as editable art references.

Detours reach 16 m off the road. Both shoulders are evaluated and a more
demanding valid path is preferred. Existing soft ground, ruts and shallow
water remain; nothing flattens or dries the detour. Water limits are 52 cm at
the path centre and 58 cm at footprint probes; cliffs and abrupt breaks are
still rejected. These geometric limits are not a promise that every damaged
vehicle can pass in all weather or after making deep tyre ruts.

Each wooded chunk has up to 42 small leaves and six twigs, grouped into shared
mesh batches without collision bodies. Placement is split into three queued
patch jobs and uses the rendered terrain height. A single local emitter in
`assets/effects/road_leaves.tscn` responds to vehicle motion and wind near
actual trees, with 32/21/11 shadowless particles at high/balanced/low quality.
Ground leaves beneath the car are scattered into the visual wake. The effect
stays off in bare regions and resets after restarting or shifting the origin.

Controlled render comparison, Intel Graphics / Compatibility, balanced quality:
the same stationary forest view loaded 137 trees at 72 attempts and 146 at 88,
with 225 draw calls in both cases. Two warmed passes measured median frame
intervals 4.15/4.27 ms versus 4.17/4.38 ms. These are one view's frame intervals,
not a long-drive benchmark, a guarantee of 60 FPS, or a phone measurement.
Source: `build/forest-details/density-benchmark.log`.

Verification commands:

```powershell
./tools/godot.ps1 Test -Suites road_obstacle_checks,forest_detail_checks,vegetation_checks,streaming_visual_checks
```

The checks cover actual streamed trees beside a fallen trunk, naturally wet
detours, a physical 4x4 bypass without rescue, all obstacle batches, real ground
litter, wind/car emission, quality budgets, and resetting the particle effect.
APK generation remains paused.

Final checks: road obstacles 59/59, forest details 12/12, vegetation 36/36,
streaming visuals 22/22, plus successful HUD, frame-pacing and terrain/water
grounding suites. The checked 4x4 completed all 17 bypass points with no
obstacle impacts or recovery teleport. All nine editor terrain showcases were
refreshed, and both forest/litter and fallen-tree/grove captures were inspected.
