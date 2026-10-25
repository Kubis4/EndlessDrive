# Renderer review, 2026-09-06

These PNGs were rendered by BackdropTransitionTest on an Android emulator.

The far artwork now occupies 36% of screen height, and mid/near artwork 34%.
Horizontal tile scale is 0.85, with 0.70 for industrial scenes. This reduces
oversized buildings and the horizontally stretched industrial appearance.
The upper sky uses a gradient sampled from the artwork, with a soft masked join
to the far layer, instead of stretching a one-pixel image strip.

Biome transitions composite each complete three-layer backdrop before fading
the incoming scene. Old objects therefore fade through transparent gaps in the
incoming mid/near layers. The five industrial snapshots cover the transition.
All seven biomes were rendered. BackdropTransitionTest verifies blend pixels at
six progress values, including endpoints and almost-complete transitions.

The two car previews show the same geometry with standard and expedition racks.
The test also verifies that replacing the expedition kit restores the standard
render exactly. All three instrumentation tests passed, alongside 171 unit tests.

Background artwork backups remain in `../upgrade-2026-09-05/originals/`.
