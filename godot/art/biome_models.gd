class_name BiomeModels
extends RefCounted
## New code-native scenery: layered low-poly conifers, wetland trees and snow caps.

static var materials: Dictionary = {}


static func material(color: Color) -> StandardMaterial3D:
	if not materials.has(color):
		var value := StandardMaterial3D.new()
		value.albedo_color = color
		value.roughness = 0.95
		materials[color] = value
	return materials[color]


static func tree(parent: Node3D, pos: Vector3, size: float, snowy: bool, wetland: bool, snow_cover: float = -1.0) -> void:
	var tree_root := Node3D.new()
	tree_root.name = "WetlandTree" if wetland else "MountainPine"
	tree_root.position = pos
	parent.add_child(tree_root, true)
	DesertModels.cylinder(tree_root, "Trunk", Vector3.UP * 2.2 * size, 0.23 * size, 4.4 * size, DesertModels.mat("wood"), 6, 0.15 * size)
	for layer in 3:
		var radius := (2.2 - layer * 0.45) * size
		var height := (2.8 - layer * 0.3) * size
		var y := (2.8 + layer * 1.35) * size
		var coverage := (1.0 if snowy else 0.0) if snow_cover < 0.0 else snow_cover
		var color := (Color("6c8355") if wetland else Color("425b47")).lerp(Color("849ca4"), coverage * 0.85)
		DesertModels.cylinder(tree_root, "Foliage", Vector3.UP * y, radius, height, material(color.lightened(layer * 0.035)), 7, 0.06)
	DesertModels.collider(tree_root, Vector3.UP * 2.0 * size, Vector3(0.45, 4.0, 0.45) * size)


static func snow_cap(rock: Node3D, coverage: float = 1.0) -> void:
	DesertModels.cylinder(rock, "SnowCap", Vector3(0.05, 1.30, 0), 0.74, 0.12 * coverage, material(Color("9b9d8e").lerp(Color("8fa5b6"), coverage)), 7, 0.66)
