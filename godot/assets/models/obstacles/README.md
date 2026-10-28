# Road obstacles on the V5 base

Open `fallen_tree.tscn`, `wreck.tscn`, `rubble.tscn` or `barrier.tscn` directly
in Godot to edit their native meshes and fitted collision shapes. They are
generated from `art/road_obstacle_models.gd` and reuse the project's materials.

The fallen tree has a solid trunk, broken branches and an uprooted base.
Instances now vary between broad across-road, diagonal and partial-road
placements. Seeded yaw, lateral offset and uniform size are applied to the
complete prefab, so branch/trunk collisions follow the visible model. A fitted
ground plane aligns the trunk to the slope; support samples prevent burying it
in a hump or leaving a badly floating log. Partial placements keep an open
lane, while wider falls still require the off-road detour.
The other obstacles are two supplied rusted car models (`wreck.tscn` and
`wreck_1.tscn`), a small rockfall and damaged
concrete barriers. Instances keep winch anchors. These are static scenery;
they do not add per-frame physics simulation or moving rigid bodies.

`scripts/road_obstacle_layout.gd` selects sparse sites from the world seed and
committed route choices. Nearby candidates are mutually exclusive: at most
two sites per 960 m region, separated by at least 100 m. Sites are skipped
near junctions, mountains, bridges, tunnels, crossings, supplies and shelters.
Both shoulders are considered; a 17-point detour reaches 16 m off the lane.
Soft soil, ruts and shallow water are intentionally retained; the more demanding
valid side is preferred. Water is limited to 52 cm at path centres, 58 cm at
body-width probes, with abrupt terrain breaks and cliffs still rejected.
The detour is not graded or dried. Tyres, gearing, load and weather matter.
Scenery is excluded only from the actual driving corridor and obstacle footprint.
Existing map boundary collision is retained.

Fallen trees require two or more valid neighbouring tree cells. Up to five
companion trees are planted on the other side of the detour using the shared
world lattice, including across chunk boundaries. A site without enough tree
context becomes a wreck rather than leaving a lone fallen trunk in an empty
field. Normal vegetation remains around the obstacle outside the bypass.

Layout preparation runs in the terrain worker. Instantiation is a bounded
publication job using preloaded scenes and existing MultiMesh batching.
Revisiting or loading a rutted landscape does not reroll the obstacles.
Sampling proves initial geometric clearance, not all possible player-made
rut, weather or vehicle-condition outcomes.

Asphalt now uses `assets/textures/weathered-asphalt.png`: a seamless 24 m
world-space swatch with broad oxidation patches and sparse branching cracks.
It has mipmaps and replaces the asphalt's high-frequency gravel detail.
Its alpha channel controls surviving road paint, not ground transparency.
Existing other V5 material textures are retained.

Potholes are real 12–40 cm terrain depressions in the shared render/collision
mesh, with exposed gravel, a darker bottom and worn paint. Their centres
follow the road's bends. Paved starting regions receive a shallow introductory
cluster at 174 m; later eligible asphalt events favour pothole damage.

Rebuild and verify:

```powershell
./tools/godot.ps1 BuildRoadArt
./tools/godot.ps1 Test -Suites road_obstacle_checks,streaming_visual_checks,surface_balance_checks
```

`road_obstacle_checks` includes multiple seeded worlds, actual baked pothole
collision triangles, a car-sized collision query and an actual 4x4 drive
around a naturally generated fallen tree without impact or recovery teleport.
APK generation remains paused.

Initial validation on 2026-10-27: road obstacles **55 checks, 0 failures**; streamed
visual publication **22 checks, 0 failures**. Surface balance, terrain/water
grounding, route commits, vegetation, preview geometry and frame-pacing
contract suites also passed during this change. The actual bypass drive
completed 17/17 waypoints with zero obstacle impacts and zero recoveries.
These checks do not establish a particular frame rate or phone performance.
