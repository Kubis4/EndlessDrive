extends SceneTree
## Bakes the roadside models from the Sketchfab packs in assets/source/roadside:
## each becomes one scene under DIR (merged mesh per material, real-world size,
## base at the origin, a box collider per solid part), with its textures resized
## and embedded next to it. Run after `godot --import`.
const DIR := "res://assets/models/roadside/"
const BUILDINGS := "res://assets/source/roadside/lowpoly_buildings.glb"
const STATION := "res://assets/source/roadside/old_gas_station.glb"
## Parts merged into one model (the rest of the pack: one model per part).
const GROUPS := {
	"house01": ["house01", "house01_roof", "house01_add"], "house03": ["house03", "house03_add", "house03_roof"],
	"barn01": ["barn01", "barn01_add"],
}
const SKIP := ["ac01", "ac02", "metalplate01", "metalplate02", "metalplate03", "wooden_plate01", "wooden_plate02"]
## Not solid: nothing to run into.
const GHOSTS := ["pylon_line01", "sign01", "sign02", "sign03", "sign04", "fence_pole01", "fusebox01"]
## The gas station without its ground plate, road and power line (it comes in
## centimetres; its forecourt faces -X).
const STATION_SKIP := ["GROUND_LOW_POLY", "ROAD_LOW_POLY", "POST_1_LOW_POLY", "POST_2_LOW_POLY", "CABLE_LOW_POLY", "CABLE_2_LOW_POLY", "TEXT_LOW_POLY"]
## What of the station a car can hit: not the slab it drives on, nor what hangs above.
const STATION_SOLID := ["STATION_LOW_POLY", "GLASS_LOW_POLY", "DOOR_LOW_POLY", "JALOUSIE_LOW_POLY", "WOOD_FRAME_LOW_POLY", "CONCRETE_POST_LOW_POLY", "AUTOMAT_1_LOW_POLY", "AUTOMAT_2_LOW_POLY", "GAS_CORPUS_LOW_POLY", "GAS_CORPUS_2_LOW_POLY", "TRASH_1_LOW_POLY", "TRASH_2_LOW_POLY", "GENERATOR_LOW_POLY", "GENERATOR_2_LOW_POLY", "FENCE_LOW_POLY", "ARRAY_LOW_POLY", "GATE_LOW_POLY", "SIGH_METAL_LOW_POLY"]
var textures := {}
var materials := {}

func _initialize() -> void: call_deferred("run")

func run() -> void:
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path(DIR))
	var pack := _parts(BUILDINGS)
	var used := {}
	for model: String in GROUPS:
		var parts := []
		for part: String in GROUPS[model]:
			parts.append(pack[part])
			used[part] = true
		_bake_centred(model, parts, model not in GHOSTS)
	for part: String in pack:
		if part in used or part in SKIP: continue
		_bake_centred(part, [pack[part]], part not in GHOSTS)
	_bake_station()
	quit()

## Top-level parts of a pack: name → [mesh, transform in the pack's frame].
func _parts(path: String) -> Dictionary:
	var scene: Node3D = (load(path) as PackedScene).instantiate()
	root.add_child(scene)
	var found := {}
	for node: MeshInstance3D in scene.find_children("*", "MeshInstance3D", true, false):
		if node.mesh == null: continue
		var owner_node: Node = node
		# The named node sits just under RootNode, the mesh one or two levels down.
		while owner_node.get_parent() != null and owner_node.get_parent().name != "RootNode": owner_node = owner_node.get_parent()
		found[str(owner_node.name)] = [node.mesh, node.global_transform]
	return found

func _bake_centred(model: String, parts: Array, solid: bool) -> void:
	var box := AABB()
	var first := true
	for part: Array in parts:
		var bounds: AABB = part[1] * (part[0] as Mesh).get_aabb()
		box = bounds if first else box.merge(bounds)
		first = false
	var shift := Transform3D(Basis.IDENTITY, -Vector3(box.get_center().x, box.position.y, box.get_center().z))
	var scene := Node3D.new()
	scene.name = model
	_merged(scene, parts, shift)
	if solid: _solid(scene, parts, shift)
	_save(scene, model, box.size)

func _bake_station() -> void:
	var pack := _parts(STATION)
	var scale := Transform3D().scaled(Vector3.ONE * 0.01)
	var parts := []
	var solids := []
	for part: String in pack:
		if part in STATION_SKIP: continue
		var xf: Transform3D = scale * pack[part][1]
		parts.append([pack[part][0], xf])
		if part in STATION_SOLID: solids.append([pack[part][0], xf])
	var scene := Node3D.new()
	scene.name = "gas_station"
	_merged(scene, parts, Transform3D.IDENTITY)
	_solid(scene, solids, Transform3D.IDENTITY)
	var box: AABB = parts[0][1] * (parts[0][0] as Mesh).get_aabb()
	for part: Array in parts: box = box.merge(part[1] * (part[0] as Mesh).get_aabb())
	_save(scene, "gas_station", box.size)

## One mesh with a surface per material.
func _merged(scene: Node3D, parts: Array, shift: Transform3D) -> void:
	var tools := {}
	for part: Array in parts:
		var mesh: Mesh = part[0]
		for s in mesh.get_surface_count():
			var source := mesh.surface_get_material(s)
			if not tools.has(source):
				var tool := SurfaceTool.new()
				tool.begin(Mesh.PRIMITIVE_TRIANGLES)
				tools[source] = tool
			(tools[source] as SurfaceTool).append_from(mesh, s, shift * part[1])
	var merged := ArrayMesh.new()
	for source: Material in tools:
		var tool: SurfaceTool = tools[source]
		merged = tool.commit(merged)
		merged.surface_set_material(merged.get_surface_count() - 1, _material(source))
	var node := MeshInstance3D.new()
	node.name = "Model"
	node.mesh = merged
	scene.add_child(node)
	node.owner = scene

## The collider: the parts' own triangles, so the hitbox is exactly what is seen.
func _solid(scene: Node3D, parts: Array, shift: Transform3D) -> void:
	var tool := SurfaceTool.new()
	tool.begin(Mesh.PRIMITIVE_TRIANGLES)
	for part: Array in parts:
		var mesh: Mesh = part[0]
		for s in mesh.get_surface_count(): tool.append_from(mesh, s, shift * part[1])
	var shape_mesh := tool.commit()
	var solid := shape_mesh.create_trimesh_shape()
	(solid as ConcavePolygonShape3D).backface_collision = true
	var body := StaticBody3D.new()
	body.name = "Solid"
	scene.add_child(body)
	body.owner = scene
	var shape := CollisionShape3D.new()
	shape.shape = solid
	body.add_child(shape)
	shape.owner = scene

func _save(scene: Node3D, model: String, size: Vector3) -> void:
	var packed := PackedScene.new()
	packed.pack(scene)
	ResourceSaver.save(packed, DIR + model + ".scn", ResourceSaver.FLAG_COMPRESS)
	print("ROADSIDE %s size=%s surfaces=%d" % [model, size.snapped(Vector3.ONE * 0.01), scene.get_node("Model").mesh.get_surface_count()])

## A copy of a material on resized textures saved next to the models.
func _material(source: Material) -> Material:
	if materials.has(source): return materials[source]
	var copy := source.duplicate() as BaseMaterial3D
	copy.albedo_texture = _texture(copy.albedo_texture, 1024)
	copy.normal_texture = _texture(copy.normal_texture, 1024)
	copy.roughness_texture = _texture(copy.roughness_texture, 512)
	copy.metallic_texture = _texture(copy.metallic_texture, 512)
	copy.ao_texture = _texture(copy.ao_texture, 512)
	copy.emission_texture = _texture(copy.emission_texture, 512)
	materials[source] = copy
	return copy

func _texture(source: Texture2D, largest: int) -> Texture2D:
	if source == null: return null
	if textures.has(source): return textures[source]
	var image := source.get_image()
	if image.is_compressed(): image.decompress()
	if image.has_mipmaps(): image.clear_mipmaps()
	var side := maxi(image.get_width(), image.get_height())
	if side > largest:
		image.resize(image.get_width() * largest / side, image.get_height() * largest / side, Image.INTERPOLATE_LANCZOS)
	image.generate_mipmaps()
	var saved := ImageTexture.create_from_image(image)
	var path := DIR + "texture_%d.res" % textures.size()
	ResourceSaver.save(saved, path, ResourceSaver.FLAG_COMPRESS)
	textures[source] = load(path)
	return textures[source]
