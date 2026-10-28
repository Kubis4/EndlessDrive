# Boundary rock models

Five original native Godot models, each also supplied with a snowy granite material:

- `weathered_boulder`: rounded weathered sandstone.
- `split_outcrop`: two interlocking fractured masses.
- `layered_slab`: broad angular ledges and a low crown.
- `talus_cluster`: three differently sized stones sharing a buried foundation.
- `jagged_crag`: a taller, asymmetric angular outcrop.

Each `.tscn` contains a textured mesh and a reusable convex collision shape. The
existing expedition material atlas supplies sandstone/granite detail; no new
external assets are required. Shape bounds are normalized to X ±1, Z 0–2, and a
buried foot below Y=0. All variants use fewer than 800 mesh vertices. Runtime
instances share mesh/material resources and use per-chunk instance batches.

Rebuild with `tools/godot.ps1 BuildBoundaryRocks` or run
`res://tests/build_boundary_rocks.gd`. Source geometry is in
`art/boundary_rock_models.gd`; placement is in `scripts/track_edges.gd`.
