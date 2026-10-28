# IZH-2715

User-provided model: `art/source_vehicles/izh/IZH-2715.fbx`.
Native playable rig: `izh_2715.tscn`; forward is +X, left is +Z.
Four independent wheel pivots share the Jeep animation contract. Dimensions,
front-drive capability and collider path live in `scripts/vehicle_catalog.gd`.

Body: 10,397 original triangles; each wheel: 568 triangles. No supplied bitmap
textures or vertex colours were present. The conversion assigns paint/glass
masks and tyre/hub colours without adding a large texture dependency.
The convex chassis is simplified offline, not rebuilt during gameplay.

Rebuild: native Godot, `--headless --script res://tests/build_izh_rig.gd`.
The source FBX is kept for editing and excluded from runtime exports.
