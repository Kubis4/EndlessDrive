# Fences and rock boundaries

Roadside fences use one global five-metre lattice rather than restarting nineteen
prefabs at each 96 m chunk. A panel owns its first post and owns an end post only
before a real gap. Three ground probes seat the upright posts and sagging wires.
Bridges, rail approaches, split roads, water, service access and reserved off-road
trails retain openings. The fence fix is independent of boundary scenery.

Map edges use five different textured rock models: weathered boulders, split
outcrops, layered slabs, talus clusters and jagged crags. They have convex
collisions derived from their actual geometry. The former continuous grey ribbon
and its material are removed. Seeded size, yaw, grade, depth and type variation
break up repetition; overlapping foundations prevent obvious gaps at chunk seams.
Snow-covered variants appear with increasing probability during alpine transitions.

Every eight-metre section owns one rock group on each side. Models are loaded
before driving, share textures/geometry, and their visuals are instanced per
chunk. Collider transforms remain orthogonal with positive scales. The distant
safety belt sits behind the visible rocks, while ordinary impacts hit their real
convex hulls. Both barrier systems leave the railway portal opening clear.

The actual native scenes are editable under assets/models/boundary/. Their source
and rebuild instructions are documented in that folder. All environment previews
and the junction gallery are regenerated to replace the grey ribbon with rocks.

Validation in track_edge_checks.gd covers fence ownership/grounding, five distinct
textured models and snow variants, deterministic variation, physical closure across
chunk joins, clear road/hairpin lanes and an actual car impact with the distant
belt and recovery disabled. It also rejects the grey ribbon in saved previews.
route_structure_checks.gd protects rail portals and the tunnel passage.
APK generation remains paused.
