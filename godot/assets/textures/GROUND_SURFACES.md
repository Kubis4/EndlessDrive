# Natural ground materials

`ground-surfaces.png` is a new imagegen atlas, not a return to the previous
procedural grey tiles or the older photographic atlas. The actual delivered
image is **1254 × 1254**, with four **627 × 627** array layers: sand, stone,
earth and grass. It is copied unchanged from the built-in generator. Prompts
and provenance are recorded in [SCENERY_REFRESH.md](../../SCENERY_REFRESH.md).

Texture arrays keep independent mipmaps and repeat sampling. Continuous 6 m
world coordinates and explicit texture gradients retain proper mip selection
through material transitions. The shader samples only contributing layers;
it normally needs one or two array reads. Per-layer luminance normalization
preserves the muted biome palette rather than multiplying colour saturation.

Dirt/gravel roads now receive their own fine material structure. Previously the
source atlas was masked off on roads, leaving broad smooth procedural stains.
Those broad multipliers are reduced. Genuine water, wetness, physical wheel ruts,
deformation and mud traction remain active. Sand's road colour and asphalt
weathering remain separate; fine source detail is mip-filtered at overview scale.

No physical surface IDs, ground heights, water levels, traction, soil bearing
or depth rules changed. Saved blends still interpolate continuously across
chunk and biome boundaries. The integer noise hash remains available for
regression checks, but terrain shading reuses existing wear channels instead
of evaluating several additional procedural noise fields for every pixel.

`python tools/build_ground_surfaces.py` validates the current atlas and refreshes
`ground-surfaces.json`. Pass `--source PATH` to integrate a newly generated
atlas unchanged. This helper no longer recreates the old painted/noise artwork.
The source is imported as a 2 × 2 Texture2DArray with mipmaps. Runtime terrain,
warmup and regenerated native environment previews use this same resource.

`mountain-rock.png` is a separate new photographic rock material used by tunnel
geology and all five saved boundary-rock shapes, including their snow variants.
It uses repeat sampling, mipmaps and 8 m world-space triplanar projection,
which stays aligned through the 2048 m floating-origin step. It does
not use the old swirly expedition stone atlas.

Verification: `ground_surface_checks`, `scenery_refresh_checks`,
`route_structure_checks`, `vegetation_checks`, `streaming_visual_checks`,
native captures and a moving tunnel benchmark. No APK was generated.
