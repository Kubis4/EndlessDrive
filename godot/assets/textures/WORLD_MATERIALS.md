# World prop materials

`world-materials-atlas.png` was generated with the built-in imagegen tool and
copied into this project. Native resolution: **1254 × 1254**; four **627 × 627**
layers, individually mipmapped by Godot's `2d_array_texture` importer. The 2048 px
request was not returned at that size; the source has not been enlarged.

Layers: weathered timber, worn lime plaster, galvanized/rusted steel, green foliage.
`shaders/prop_surface.gdshader` applies them by triplanar mapping with subtle bump;
stone uses the existing terrain atlas. The authored model scenes contain these
materials, including the five stop types. Procedural meshes use the same cached
materials through `scripts/world_materials.gd`. Car textures, glass and lights
retain their specialist materials. Binary tile periods keep their phase during
2048 m origin shifts. Both texture arrays use isolated mip chains.

The surface palettes remain editable per material through `tint`, `surface_kind`,
`roughness_value` and `metallic_value`. The explicit migration tool
`tests/apply_world_materials.gd` installs detail on previously plain source
surfaces; it does not rebuild model geometry or collision.

## Final generation prompt

Built-in tool mode, opaque background:

> Use case: photorealistic-natural. Asset type: production PBR game material albedo atlas for offroad adventure game, square 2048 by 2048 or highest native square resolution. Create ONE 2 by 2 equal quadrant material atlas, no borders no gutters. TOP LEFT: weathered warm brown timber boards, long vertical natural woodgrain with fine pores, some dark seams between boards, never nails or hardware. TOP RIGHT: light warm grey lime plaster masonry, fine porous stucco with subtle worn patches revealing small muted red-brown bricks, predominantly pale plaster. BOTTOM LEFT: weathered neutral grey galvanized corrugated sheet steel, vertical corrugations, small realistic orange rust speckling, no large shiny highlights. BOTTOM RIGHT: dense rich green pine and broadleaf foliage, uniformly overhead pattern of small crisp leaves and fine needles, natural tiny dark interstices, no ground, no branches larger than 1 cm. All four are flat orthographic material swatches, consistent soft flat neutral illumination, no cast shadows, no perspective, no gradients across quadrant, no text, no symbols, no logos. Rich natural color and crisp fine detail, tile-friendly edges for mirror repetition. No large depicted objects. This is actual repeating bitmap material data for 3D buildings, poles, fences, rocks and trees, never a presentation board.
