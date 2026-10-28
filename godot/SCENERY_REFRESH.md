# Scenery, materials and compact menu

Runtime trees use the supplied Shapespark/Calocedrus scenes directly in every biome. No layered cone crowns or runtime tree mesh packing. A deterministic 14 m grid, bounded jitter and smaller scale range preserve crown clearance across chunk seams and fallen-tree groves. The heavier cedar is a 2.5% landmark. Saved trees keep trunk collision and winch anchors. The atlas/scene bank is loaded before tree publication jobs.

Sparse supplied grass tufts are limited to actual green soil, outside water, roads, detours and service yards. They use the existing 10-triangle mesh, shared material, no shadows and no collision. Placement is split into at most three candidates per job, with 18/36/54 candidates per chunk according to quality.

Instanced scenery publishes explicit bounds for the visible instances instead of relying on unused reserved slots. Terrain samples only contributing photographic layers and reuses existing wear channels instead of evaluating additional pixel-noise fields. The broad dirt-road blotches are reduced; actual physical ruts and wetness are preserved.

The tunnel runs under a connected mountain roof with tapered shoulders and reused native rock crests. Portal openings, swept arches and real roof/side collisions follow the route. A numeric geology helper prepares the mountain arrays on the terrain worker. Publication creates only render/collision resources. Private renderer warmup exposes the interior before restoring its roof, so the opaque mountain cannot hide the materials being primed.

Play buttons wait for this renderer preparation to finish, including after
changing graphics quality in the menu. This keeps a quick Continue/New click
from carrying background shader compilation into the first driving frames.

The home screen retains ENDLESS DRIVE, its small gold rule and the landscape. The right side is a compact button group without a panel, distance heading or record paragraphs. Authored nodes and editor layout bindings are preserved. The in-game HUD geometry is unchanged.

## New image assets

Both images were generated with the built-in imagegen tool, then copied unchanged into the project. No CLI/API fallback and no old bitmap was reused. Actual image dimensions are 1254 × 1254. The ground atlas has four 627 × 627 array slices with mipmaps; its measured luminance and source hash are in `assets/textures/ground-surfaces.json`.

- `assets/textures/ground-surfaces.png`
- `assets/textures/mountain-rock.png`

### Ground prompt

Use case: photorealistic-natural. Asset type: a production game ground albedo texture atlas, square 2048 by 2048, exact 2 by 2 equal square quadrants with no gutters, no borders, no labels. Top left: fine dry neutral beige sand with extremely subtle grain, no ripples. Top right: weathered mineral gravel and stone fragments in neutral medium gray, tiny diverse pebbles embedded densely in compact ground, no large stones. Bottom left: natural compact dry soil with small organic granules, tiny embedded stones and very sparse root fibers, neutral taupe earth, no large dark mud blotches. Bottom right: dense short meadow grass seen straight down with fine individual blades, small dry straw and tiny sparse soil gaps, restrained grayish olive green. Each quadrant is a seamless repeating PBR albedo tile of a roughly 4 metre patch of material, each quadrant's opposite edges must match. Orthographic flat top down scans, sharp authentic photographic fine structure, no perspective, evenly diffuse illumination, no cast shadows or baked highlights, flat even exposure from edge to edge, limited small-scale contrast. No painterly smears, no cloudy blotches, no grid, no illustration, no shiny surface, no oversaturation, no tire tracks, no snow. All quadrants have similar medium luminance so a shader can retain the existing biome palette; realistic material texture detail rather than drawings.

### Rock prompt

Use case: photorealistic-natural. Asset type: one square seamless PBR albedo texture for a natural rock cliff in a 3D driving game. Fill the entire image with real weathered grey-brown sandstone and slate rock, photographed straight on under perfectly diffuse overcast illumination. Mineral grains, thin irregular fractured sedimentary strata, a few restrained eroded cracks, organic weathering. Subtle cool grey and dusty tan variation, muted neutral saturation, medium luminance. Small to medium geological detail, not gravel or loose stones. No loose pebbles, moss, snow, soil, plants, perspective, vignette, borders, cast shadows, dramatic baked lighting, text, painterly strokes, swirly marbling, or a repeating brick pattern. Opposite edges must meet seamlessly for tiling. Flat even exposure and authentic crisp photographic rock detail.

## Verification

`scenery_refresh_checks`, `vegetation_checks`, `ground_surface_checks`, `route_structure_checks`, `grounding_water_checks`, `menu_ui_checks`, `ui_localization_checks`, `winch_anchor_checks`, `forest_detail_checks`, `track_edge_checks`, `road_obstacle_checks` and `streaming_visual_checks` cover the changed behavior. Native images are in `build/scenery-refresh/`. APK generation remains paused.

The stationary forest probe at 88 attempts changed from 146 trees to 112 over the seven loaded chunks. The warmed median was 4.94 ms before and 4.82 ms after on this Intel/Compatibility setup; the global draw-call monitor remained 229. This small stationary improvement is not a guarantee of long-drive or phone FPS. First-use tunnel shaders were separately profiled because they can dominate rare frame spikes.

The real moving route probe at 36 m/s from 1500 to 1850 m exposed first-use
render stalls of 374.69 and 333.04 ms near the tunnel. After exposing the private
warmup interior, a fresh-cache run measured median 16.73 ms, p99 36.23 ms and
maximum 37.35 ms. Streaming publication peaked at 4.84 ms; its heaviest step
was 3.96 ms. The original shader cache was temporarily moved within the
workspace and restored after testing. These are desktop measurements, not
Android results, and do not establish a universal worst-frame bound.
