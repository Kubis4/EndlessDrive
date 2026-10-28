# Supplied environment assets

The source GLBs are in `art/source_environment/`. `tests/build_supplied_environment.gd` extracts the selected buildings, station and four rock variants for each of the desert, plain, mossy and snowy materials. Runtime meshes, materials and resized mipmapped textures live here; the source GLBs are excluded from exports.

Stop scenes are saved in `assets/models/stops/`. The station omits its source road, ground, fence, gate and distant power lines. It is rotated so its forecourt faces local +Z, towards the road. Collision follows the building mesh. Stop kind determines the type and approximate quantity of loot; existing searched loot remains saved.

Rock scenes use normalized dimensions and include collision. The world chooses materials by biome and scales them for loose rocks and boundary formations. The source models were supplied by the project owner; no separate license information was included.
