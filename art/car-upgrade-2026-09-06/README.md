# Sedan materials and optional expedition equipment

The second material pass strengthens clearcoat highlights, lower-panel shading,
door creases and glass reflections. `originals/` backs up the installed sprites
before this pass; the untouched original artwork is in
`../car-upgrade-2026-09-05/originals/`. `upgrade.py` reproduces the material pass.

All 17 sprites retain their dimensions, alpha pixels and paint-mask classification.
See `validation.json` for per-file checks. `final/` contains the installed results.

The optional `roof_rack_expedition` item (display name: Expedition kit) mounts in
the existing roof-rack slot. Code-rendered door protection strips, strapped luggage
and side-view auxiliary lighting use the original car coordinate system. Door strips
follow installed doors. Replacing the kit with a standard rack removes these
details. A separate roof-lamp HUD button appears with the kit mounted. It switches
the roof glow and forward cone independently from the headlights and adds battery
load. Its state is saved; older saves default to off. The auxiliary cone is a
visual light effect and does not change the main headlights' terrain reveal mask.

Revision: the rear strip now starts at x=432 in base-car coordinates, safely
inside the opaque rear-door contour above the wheel arch. Its complete bounding
box was checked against the original door alpha. The lamp now has a side housing,
cooling fins, a mounting foot and a narrow lens facing forward. The prior equipment
renderer is backed up in `revision-backup/ExpeditionEquipment.kt`.

Validation: 171 unit tests and all three BackdropTransitionTest emulator tests
passed on 2026-09-06, including item save/load, replacement and rendered restoration.
Actual previews: `../backgrounds/render-review-2026-09-06/car-standard.png` and
`../backgrounds/render-review-2026-09-06/car-expedition.png`.
