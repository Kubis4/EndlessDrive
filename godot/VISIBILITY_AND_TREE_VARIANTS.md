# Vehicle visibility and fallen-tree variants

The visibility outline no longer treats tall invisible map safety walls as
opaque scenery. Probes skip these walls and continue to genuine geometry.
Tree/rock/building probes use actual saved mesh bounds, not a generic canopy
box extending well above the tree. Hidden nodes and other viewport worlds
cannot activate the aid. Preparing the outline twice cannot duplicate meshes.

The depth shader also rejects the entire vehicle's own near-depth range.
This prevents near-side car panels or wheels from outlining its farther parts
when a conservative probe produces a false positive. The rejection plane
updates every active frame as the car, camera and suspension move. Actual
external occluders still reveal the identification rim. Three body-core depth
probes require meaningful occlusion (at least two hidden); a low wall covering
only a tyre or a translucent tunnel cutaway no longer highlights a visible body.
The shader supports both Compatibility and the Vulkan NDC depth ranges.

Rendered regression on the Compatibility renderer: forcing the aid on while
the car remained fully visible changed **0 pixels**. An opaque test roof still
produced a visible outline (37,288 changed pixels). The render suite passed
8 checks, including **0 changed pixels** for a low wall hiding only tyres;
the normal headless suite passed 5 checks. Images/logs are in
`build/visibility-fix/` and do not use the player's save.

Fallen-tree placement now selects across-road, angled and partial-road variants
from the world seed. Yaw, lateral offset, uniform size and terrain tilt vary.
The whole editable model and its fitted capsule colliders use one transform.
Root balls remain towards their neighbouring grove. Partial trees can leave
one traffic lane open; larger falls still lead to the existing muddy/wet
shoulder detour. Placement remains bounded and deterministic after revisits.

The road regression passed 62 checks, including real 4x4 drives around a broad
fall and along the open lane of a partial fall, both with no obstacle impacts
or rescue teleport. The legacy V3 world checks also pass; their old count of
Pine-named descendants was replaced with actual bounded, unique planted cells,
consistent with the already requested reduction of overlapping vegetation.

Commands:

```powershell
./tools/godot.ps1 Test -Suites vehicle_outline_checks,road_obstacle_checks,v3_world_checks
```

For rendered outline QA use `tests/vehicle_outline_checks.gd -- --test-session
--render` with a real renderer. `tests/tree_variety_capture.gd` captures the
three naturally seeded variants. APK generation remains paused.
