# Asset provenance and release constraints

This inventory records known sources; it does not grant additional rights.

- **Jeep Cherokee and extracted rig/textures:** Renafox, supplied GLB metadata
  records CC BY-NC-SA 4.0. The source, changes and attribution are in
  `models/jeep/ATTRIBUTION.md`. Obtain suitable permission or replace this model
  before commercial use. The repository's code terms do not override asset terms.
- **Active winch:** `scenes/winch_mount.tscn`, constructed from native Godot
  primitive meshes in this project. It has an editable `CableOutlet`. The active
  vehicle no longer depends on the large imported winch GLB.
- **Imported off-road winch GLB:** retained as a source/reference file. Its
  licence was not established by the audit. It is excluded from the Android
  resource export and must not be reintroduced into a release without provenance.
- **Expedition scenery:** native mesh sources are `art/expedition_models.gd`;
  saved editable models are in `assets/models/expedition/`. Image texture source
  and processing notes are in `textures/EXPEDITION_TEXTURES.md`.
- **Supplied vegetation:** the user supplied `shapespark-low-poly-plants-kit.fbx`
  and `CL04_CalocedrusDecurrens_FBX.zip`. Selected meshes and their supplied
  textures were normalized, combined and baked into `models/vegetation/`.
  No licence document was included in the supplied ZIP; this entry records
  provenance, not a new licence grant. Source FBX files and source texture
  copies are excluded from Android export. See `models/vegetation/README.md`.
- **Other models, UI and audio:** verify their source records before public
  distribution. This inventory is not a completed clearance for every file in
  the repository or the archived Android project.

Official licence text/deed: https://creativecommons.org/licenses/by-nc-sa/4.0/
