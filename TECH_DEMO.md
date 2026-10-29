# Sirocco — Endless Drive desert tech demo

A playable source demo for **Godot 4.7.2 / Forward+**, composed for 1920×1080.
Three vehicles patrol a desert outpost, throwing layered dust and excavating
smooth, persistent tyre ruts. Take over the lead Jeep to drift and deepen the tracks.

![Sirocco desert tech demo](design/desert-battlefield/preview.png)

## Run

Clone or download the **`codex/desert-tech-demo`** branch of this repository.
Keep the whole `godot/` directory: the demo shares the game's native vehicle,
building and rock assets. This is a Godot source demo, not a packaged executable.

On Windows, from the repository root:

```powershell
.\tools\desert-battlefield.ps1 Play -GodotPath 'C:\path\to\Godot_console.exe'
```

The launcher also accepts `GODOT_BIN` or Godot on PATH, imports assets on the first
run, and selects Forward+ without changing the main project settings.

On other platforms, with `godot` on PATH:

```sh
godot --headless --editor --import --path godot --rendering-method forward_plus
godot --path godot --rendering-method forward_plus --resolution 1920x1080 res://desert_battlefield/desert_battlefield.tscn
```

For editing, import `godot/project.godot`, open
`res://desert_battlefield/desert_battlefield.tscn` and run that scene with **F6**.
Use Forward+ for the full lighting, fog and dust presentation.

## Controls

- **Space:** take control of the lead Jeep / return to cinematic mode.
- **WASD or arrows:** accelerate, reverse and steer.
- **Shift:** handbrake drift.
- **Escape:** return to autonomous patrol.
- **R:** reset the lead vehicle to its route.
- **P:** pause vehicles, camera and particles.
- **+ / −:** zoom.

## Included

- Jeep Cherokee and IZH-2715 meshes and materials from Endless Drive, with rotating
  wheels and front-wheel steering. Wheel support, dust and tread spacing match each rig.
- Two repair barracks and four desert rock variants reused from the main game,
  alongside the demo's relay station, barricades, poles and small patrol figures.
- Smooth dune displacement shared by rendering and vehicle support. Repeated
  passes deepen existing ruts and gradually approach a bounded depth.
- Cached tyre ribbons, local terrain patches and GPU dust/grain particles.
- Cinematic orthographic camera, warm shadows, ambient occlusion and atmospheric haze.

Deformation lasts for the current session. Dust is layered particles, not fluid
simulation. The cars use lightweight terrain-following controls; this demo does
not include Endless Drive's survival systems, save progression or combat.

## Verify and edit

```powershell
.\tools\desert-battlefield.ps1 Check
.\tools\desert-battlefield.ps1 Capture
```

Checks cover imported wheel alignment, tyre spacing, progressive soil depth,
terrain continuity, suspension, bounded caches, two minutes of patrol and manual
steering. Capture renders a native 1080p screenshot under `build/`.

The integrated demo passed these checks. A 60-second 1080p Forward+ run with 4× MSAA
on an RTX 3070 measured **8.52 ms median / 11.78 ms p95 / 12.46 ms p99** frame
intervals after warmup. These local wall-clock measurements are not a guarantee
for other hardware.

![Jeep, dust and deformed sand detail](design/desert-battlefield/endless-drive-detail.png)

See [scene documentation](godot/desert_battlefield/README.md) for implementation,
measurements and the scene rebuild command. Rebuilding replaces the generated scene.

## Model credits

**Jeep Cherokee:** Renafox, **CC BY-NC-SA 4.0**. See the complete
[attribution, source and adaptation notes](godot/assets/models/jeep/ATTRIBUTION.md).
The model and its adaptations remain under those terms, including non-commercial use.

**IZH-2715:** model supplied by the project owner; native rig documented
[here](godot/assets/models/izh/README.md).

**Repair barracks and desert rocks:** models supplied by the project owner;
[source and conversion notes](godot/assets/models/supplied_environment/README.md).
No separate license information was supplied for those assets or the IZH model.
This demo does not assign a new license to them. See also the game's
[asset license inventory](godot/assets/ASSET_LICENSES.md).
