class_name BiomeModels
extends RefCounted
## Saved supplied vegetation. Runtime placement never constructs tree geometry.

static var materials: Dictionary = {}
static var _reed_scene: PackedScene
static var _primitives: Dictionary = {}
static var _supplied: Dictionary = {}
static var _supplied_materials: Dictionary = {}
static var _tree_bounds: Dictionary = {}

static func warmup() -> void:
	# Resolve the small saved scene bank once, before any frame-budgeted tree
	# job. Entering a new biome must not perform first-use texture/file I/O.
	for label: String in ["broadleaf_a", "broadleaf_b", "broadleaf_c", "calocedrus", "bush", "grass"]:
		if not _supplied.has(label): _supplied[label] = load("res://assets/models/vegetation/" + label + ".tscn")

static func supplied(label: String) -> Node3D:
	if not _supplied.has(label): _supplied[label] = load("res://assets/models/vegetation/" + label + ".tscn")
	return (_supplied[label] as PackedScene).instantiate() as Node3D


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
	var label := "calocedrus" if variant == 4 else ["broadleaf_a", "broadleaf_b", "broadleaf_c"][posmod(variant, 3)] as String
	var instance := supplied(label)
	instance.name = "WetlandTree" if wetland else "MountainPine"
	var foliage := instance.get_node("Foliage") as MeshInstance3D
	if not _supplied_materials.has(coverage):
		var shaded := foliage.material_override.duplicate() as ShaderMaterial
		shaded.set_shader_parameter("snow_cover", coverage)
		_supplied_materials[coverage] = shaded
	foliage.material_override = _supplied_materials[coverage]
	if not _tree_bounds.has(label): _tree_bounds[label] = _visibility_bounds(instance)
	instance.set_meta("occlusion_bounds", _tree_bounds[label])
	instance.set_meta("vegetation_source", label)
	instance.position = pos
	instance.scale = Vector3.ONE * size
	instance.rotation.y = fposmod(pos.x * 1.73 + pos.z * 0.49, TAU)
	parent.add_child(instance, true)
	instance.add_to_group("vehicle_occluder", true)

static func _visibility_bounds(root: Node3D) -> AABB:
	var bounds := AABB()
	var first := true
	for mesh: MeshInstance3D in root.find_children("*", "MeshInstance3D", true, false):
		var transform := mesh.transform
		var parent := mesh.get_parent()
		while parent != root and parent is Node3D:
			transform = parent.transform * transform
			parent = parent.get_parent()
		var box := transform * mesh.mesh.get_aabb()
		bounds = box if first else bounds.merge(box)
		first = false
	return bounds

static func reeds(parent: Node3D, pos: Vector3, size: float, yaw: float) -> Node3D:
	if _reed_scene == null:
		var prototype := preload("res://scripts/expedition_scenery.gd").instance("marsh_reeds")
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

static func undergrowth(parent: Node3D, pos: Vector3, size: float, yaw: float, forest: bool) -> Node3D:
	var node := supplied("bush" if forest else "grass")
	node.position = pos
	node.scale = Vector3.ONE * size
	node.rotation.y = yaw
	parent.add_child(node, true)
	return node

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
