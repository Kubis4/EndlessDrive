class_name BiomeModels
extends RefCounted
## New code-native scenery: layered low-poly conifers, wetland trees and snow caps.

static var materials: Dictionary = {}
static var _trees: Dictionary = {}
static var _reed_scene: PackedScene
static var _primitives: Dictionary = {}


static func material(color: Color) -> StandardMaterial3D:
	if not materials.has(color):
		var value := StandardMaterial3D.new()
		var luma := color.r * 0.2126 + color.g * 0.7152 + color.b * 0.0722
		value.albedo_color = Color(luma, luma, luma, color.a).lerp(color, 0.76)
		value.roughness = 0.95
		materials[color] = value
	return materials[color]


static func tree(parent: Node3D, pos: Vector3, size: float, snowy: bool, wetland: bool, snow_cover: float = -1.0, variant: int = 0) -> void:
	var coverage := (1.0 if snowy else 0.0) if snow_cover < 0.0 else snappedf(snow_cover, 0.25)
	var key := "%s/%s/%d" % [coverage, wetland, variant]
	if not _trees.has(key):
		var prototype := _tree_prototype(coverage, wetland, variant)
		preload("res://scripts/world_materials.gd").apply(prototype)
		_share_meshes(prototype)
		_own(prototype, prototype)
		var packed := PackedScene.new()
		packed.pack(prototype)
		_trees[key] = packed
		prototype.free()
	var instance := (_trees[key] as PackedScene).instantiate() as Node3D
	instance.position = pos
	instance.scale = Vector3.ONE * size
	instance.rotation.y = fposmod(pos.x * 1.73 + pos.z * 0.49, TAU)
	parent.add_child(instance, true)
	instance.add_to_group("vehicle_occluder", true)

static func _tree_prototype(coverage: float, wetland: bool, variant: int) -> Node3D:
	var tree_root := Node3D.new()
	tree_root.name = "WetlandTree" if wetland else "MountainPine"
	var broadleaf := wetland or variant == 2
	var size := 1.0 + variant * 0.10
	DesertModels.cylinder(tree_root, "Trunk", Vector3.UP * 3.4 * size, 0.29, 6.8 * size, DesertModels.mat("wood"), 7, 0.13)
	if broadleaf:
		for layer in 4:
			var a := layer * 2.4
			var crown := Vector3(cos(a) * 1.65, 7.1 + (layer % 2) * 1.8, sin(a) * 1.65) * size
			DesertModels.beam(tree_root, "Branch", Vector3.UP * 4.8 * size, crown, 0.16, DesertModels.mat("wood"))
			DesertModels.cylinder(tree_root, "LeafCrown", crown, (2.5 if wetland else 3.1) * size, 1.8 if wetland else 2.7, material(Color("56654b") if wetland else Color("435d47")), 8, 1.8 * size)
		if wetland:
			for root in 4:
				var a := root * PI * 0.5
				DesertModels.beam(tree_root, "ButtressRoot", Vector3(0, 1.2, 0), Vector3(cos(a) * 1.3, 0.0, sin(a) * 1.3), 0.22, DesertModels.mat("wood"))
	else:
		for layer in 4:
			var radius := (3.0 - layer * 0.55) * size
			var height := (4.0 - layer * 0.35) * size
			var y := (4.2 + layer * 1.75) * size
			var color := Color("3b5846").lerp(Color("b2bcb5"), coverage * 0.85)
			DesertModels.cylinder(tree_root, "Foliage", Vector3.UP * y, radius, height, material(color.lightened(layer * 0.025)), 8, 0.04)
	DesertModels.collider(tree_root, Vector3.UP * 3.0, Vector3(0.55, 6.0, 0.55))
	return tree_root

static func reeds(parent: Node3D, pos: Vector3, size: float, yaw: float) -> Node3D:
	if _reed_scene == null:
		var prototype := Node3D.new()
		prototype.name = "MarshReeds"
		for i in 7:
			var a := i * 2.4
			var p := Vector3(cos(a) * 0.65, 0.9, sin(a) * 0.65)
			DesertModels.cylinder(prototype, "ReedStem", p, 0.045, 1.8 + (i % 3) * 0.2, material(Color("6a714d")), 4, 0.02)
			DesertModels.cylinder(prototype, "ReedHead", p + Vector3.UP * 0.9, 0.085, 0.35, material(Color("62533f")), 5, 0.06)
		_share_meshes(prototype)
		_own(prototype, prototype)
		_reed_scene = PackedScene.new()
		_reed_scene.pack(prototype)
		prototype.free()
	var instance := _reed_scene.instantiate() as Node3D
	instance.position = pos
	instance.scale = Vector3.ONE * size
	instance.rotation.y = yaw
	parent.add_child(instance, true)
	return instance

static func _share_meshes(node: Node) -> void:
	# Unit cylinders let trunks, branches and differently sized crowns share
	# one GPU geometry resource. Per-instance scale carries their dimensions.
	if node is MeshInstance3D and node.mesh is CylinderMesh:
		var source: CylinderMesh = node.mesh
		var ratio := snappedf(source.top_radius / maxf(source.bottom_radius, 0.001), 0.05)
		var key := "%d/%.2f" % [source.radial_segments, ratio]
		if not _primitives.has(key):
			var mesh := CylinderMesh.new()
			mesh.bottom_radius = 1.0
			mesh.top_radius = ratio
			mesh.height = 1.0
			mesh.radial_segments = source.radial_segments
			_primitives[key] = mesh
		node.scale *= Vector3(source.bottom_radius, source.height, source.bottom_radius)
		node.mesh = _primitives[key]
	for child in node.get_children(): _share_meshes(child)

static func _own(node: Node, root: Node) -> void:
	for child in node.get_children():
		child.owner = root
		_own(child, root)


static func snow_cap(rock: Node3D, coverage: float = 1.0) -> void:
	DesertModels.cylinder(rock, "SnowCap", Vector3(0.05, 1.30, 0), 0.74, 0.12 * coverage, material(Color("9b9d8e").lerp(Color("8fa5b6"), coverage)), 7, 0.66)
