# Supplied loot source

`fbx.fbx` and the accompanying `textures/` directory were supplied for future
off-road loot locations. Godot imports the FBX as a scene with separate mesh
nodes, including several `SM_asset_*` and `SM_ivy_*` nodes. The imported scene
references textures in this directory. Keep the files together when editing.

The two full-size cars are converted to `assets/models/wrecks/` with
`tests/build_supplied_wrecks.gd`. The game occasionally places them off-road as
searchable wrecks. The smaller assets remain available for later use.
