# Supplied vegetation, prepared for gameplay

Open the saved `.tscn` scenes in Godot to inspect the textured models and trunk
colliders. Runtime trees are scaled to 6 m; the occasional Calocedrus is 8 m.

- `broadleaf_a/b/c`: Shapespark `Tree-01/02/03-3`, 412 triangles each.
- `calocedrus`: supplied young-tree LOD2; trunk/branches simplified from 3,538
  to 428 triangles, leaf cards retained at 3,190; 3,618 triangles total.
- `bush`: Shapespark Bush-03, 54 triangles, 0.9 m high.
- `grass`: Shapespark Grass-01, 10 triangles, 0.65 m high.

Each model has one mesh surface. All share the 1024 x 1024 RGBA texture atlas
made from the supplied textures, with alpha clipping, mipmaps and padded tiles.
Original repeating bark UVs are preserved. Snow is applied to upward-facing
foliage; the atlas is not duplicated for seasonal versions.

World placement uses 12 m cells with at most 0.8 m jitter per axis. Position,
variant and scale depend on the world seed and cell, so crowns remain separated
across chunk seams and chunk generation order. Forest scenery attempts per chunk
were reduced from 156 to 72; boundary tree attempts from 24 to at most 8.
The denser Calocedrus is selected for approximately 8% of eligible tree sites.
Map boundary collisions remain independent of visible tree density.

Source imports live in `art/source_plants/`. `art/fbxRT/` supplies texture aliases
for paths referenced by the CL04 FBX files. Sources and aliases are excluded
from Android export; only the baked resources and shared atlas are used in play.

Rebuild after changing the sources (Python with Pillow is needed for the atlas):

```powershell
./tools/godot.ps1 BuildPlants
./tools/godot.ps1 Test -Suites vegetation_checks,hud_c_checks,grounding_water_checks,winch_anchor_checks
```

The user supplied both source packs. No licence file was present in the CL04 ZIP;
this preparation does not establish additional distribution rights.
