# Startup and UI refresh

F5 starts `scenes/boot.tscn`. This deliberately small scene has no gameplay
preloads. It displays the game's title and a progress bar before loading
`scenes/main.tscn`. Scene/script loading stays on the main thread; terrain
preparation uses a numeric worker. The default Godot
splash image is disabled; the background matches the game's loading screen.
Load failure offers Retry and Quit instead of leaving a permanent progress bar.

The authored main scene remains the place to edit the car, camera, lighting and
UI. Its terrain preview wrapper loads the saved geometry with an editor-only
tool script. The large generated snapshot is no longer a runtime dependency.
Open `scenes/world/generated/editor_terrain.scn` to edit its native mesh nodes.
The other saved environment previews remain available.

The menu renders before world preparation. Saved graphics preferences are read
before creating terrain materials. Three nearby chunks are prepared by the
existing numeric worker and frame-budgeted publication queue. The loading loop
pumps only that queue with zero simulation delta; survival time, trains,
zombies and physics stay paused. Play controls remain disabled until the whole
initial queue, including collision and scenery jobs, completes. The regular
seven-chunk horizon then streams during driving.
While the opaque loading menu is shown, 3D drawing is disabled and publication
uses an 8 ms budget; both return to their previous settings before driving.

On a fresh install, New journey reuses the seed and terrain already prepared for
the menu. Later New journeys still change the seed, using the loading menu to
prepare their new ground safely. Continue restores the actual saved position
and its neighbours. Renderer warmup remains under the menu; play buttons now
wait for it to complete so a quick click cannot move compilation into driving.

The driving HUD uses muted olive/charcoal surfaces, cream text and sand accents.
Only colours changed in the driving scenes and skins: their SVG geometry,
control positions, content margins, touch sectors and sizes are unchanged.
Menu buttons have a separate matte skin, clear hover/pressed/focus states,
and a highlighted New journey or Continue according to save availability.

## Local measurements

Measured in a clean isolated Windows profile with Godot 4.7.2 Compatibility and
Intel integrated graphics. The probe loads the same main scene and waits for
a rendered, fully prepared menu, including renderer warmup construction.

- Before: 13,246 ms; seven initial chunks; synchronous ready call 7,752 ms.
- After: 5,578 ms; three initial chunks; initial ready call 104 ms.
- Headless check: 14,492 ms before, 5,094 ms after.
- Final complete boot probe: title 76 ms, menu visible 1,706 ms,
  playable menu 5,672 ms. Scene creation stays on the main thread to avoid
  additional script-loader shutdown warnings observed with threaded scenes.

The selected starting environment and shader cache affect these timings.
These are local source-project measurements with imported assets available,
not Android APK startup or cold-disk guarantees. No APK was generated.

The subsequent scenery update also waits for the complete GPU warmup, rather
than measuring only its construction. After the new shaders were prepared,
the full boot measured title 60 ms, menu visible 1,768 ms and play ready
8,719 ms. The first rebuild of the new shader cache took 30,310 ms in total.
The branded menu remains visible during this preparation. These additional
one-time compilation costs prevent the corresponding stalls during driving.

`tests/startup_benchmark.gd` measures stages; pass `--boot` to include the real
entry screen. `tests/startup_checks.gd` checks production loading, early input,
the first journey, later regeneration and saved-position restoration. Both
require APPDATA redirected to the repository's `build/startup-profile`.
`tests/ui_palette_checks.gd` compares actual HUD rectangles, visibility and
margins against `build/ui-layout-before.json`, recorded before retinting with
`--record-layout`. It covers two resolutions and touch/desktop modes, with
0.01-pixel tolerance for JSON numeric precision.

## Notebook update

The default IZH and Balanced rendering now use 16 representative warmup frames,
no duplicate live-world light primer and no second collidable mountain build.
A new isolated Godot profile measured 23,179 ms to play-ready; its repeated boot
measured 6,216 ms (title 60 ms, menu 1,687 ms). A subsequent instanced-tree
warmup verification measured 8,223 ms while preparing that added variant.
Starting biome, cache, competing apps and thermal state still affect the result.
The full 350 m tunnel render/streaming probe measured median 16.84 ms,
p99 29.59 ms and maximum 33.89 ms with the cache prepared. No APK was built.
See NOTEBOOK_UPDATE.md for the vehicle, resource-pack and physical route checks.
