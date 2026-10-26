extends SceneTree
## Editor-time baking: no FBX dependency, material explosion or texture loading at runtime.
const OUT := "res://assets/models/vegetation/"
const LAYERS := ["plants_0.png", "plants_1.png", "plants_2.png", "plants_3.png", "plants_4.png", "plants_5.png", "plants_6.png", "plants_9.png", "plants_10.png", "plants_18.png", "plants_19.png"]
var material: ShaderMaterial
var failures := 0

func _initialize() -> void: call_deferred("_run")

func _run() -> void:
	material = ShaderMaterial.new()
	material.resource_name = "SuppliedPlants"
	material.shader = load("res://shaders/supplied_vegetation.gdshader")
	material.set_shader_parameter("plants_atlas", load(OUT + "plants-atlas.png"))
	material.set_shader_parameter("snow_cover", 0.0)
	_save(material, OUT + "plants.tres")
	material = load(OUT + "plants.tres")
	var source := (load("res://art/source_plants/shapespark/plants.fbx") as PackedScene).instantiate()
	for spec: Array in [["Tree-01-3", "broadleaf_a", 6.0], ["Tree-02-3", "broadleaf_b", 6.0], ["Tree-03-3", "broadleaf_c", 6.0], ["Bush-03", "bush", 0.9], ["Grass-01", "grass", 0.65]]:
		var node := source.find_child(spec[0], true, false) as MeshInstance3D
		if node == null:
			failures += 1
			continue
		_bake([node], spec[1], spec[2], false)
	source.free()
	var cedar := (load("res://art/source_plants/calocedrus/MESH_CL04_CalocedrusDecurrens_Y_LOD2.fbx") as PackedScene).instantiate()
	var nodes: Array[MeshInstance3D] = []
	for node: Node in cedar.find_children("*", "MeshInstance3D", true, false): nodes.append(node as MeshInstance3D)
	_bake(nodes, "calocedrus", 8.0, true)
	cedar.free()
	print("SUPPLIED PLANTS BUILD: ", failures, " failures")
	quit(0 if failures == 0 else 1)

func _bake(nodes: Array[MeshInstance3D], label: String, target_height: float, cedar: bool) -> void:
	var bounds := _source_transform(nodes[0]) * nodes[0].mesh.get_aabb()
	for node: MeshInstance3D in nodes: bounds = bounds.merge(_source_transform(node) * node.mesh.get_aabb())
	var factor := target_height / bounds.size.y
	# Centre the actual trunk base, not the asymmetrical crown or kit layout.
	var base := Vector3.ZERO
	var base_count := 0
	for node: MeshInstance3D in nodes:
		for surface in node.mesh.get_surface_count():
			for p: Vector3 in node.mesh.surface_get_arrays(surface)[Mesh.ARRAY_VERTEX]:
				var point := _source_transform(node) * p
				if point.y < bounds.position.y + bounds.size.y * 0.07:
					base += point
					base_count += 1
	base /= maxi(1, base_count)
	base.y = bounds.position.y
	var vertices := PackedVector3Array()
	var normals := PackedVector3Array()
	var uvs := PackedVector2Array()
	var colors := PackedColorArray()
	var indices := PackedInt32Array()
	for node: MeshInstance3D in nodes:
		var transform := _source_transform(node)
		for surface in node.mesh.get_surface_count():
			var arrays: Array = node.mesh.surface_get_arrays(surface)
			var mat := node.get_active_material(surface) as StandardMaterial3D
			var leaf := mat.resource_name.to_lower().contains("leaves") if cedar else mat.transparency != BaseMaterial3D.TRANSPARENCY_DISABLED
			var layer := (12 if leaf else 11) if cedar else LAYERS.find(mat.albedo_texture.resource_path.get_file())
			assert(layer >= 0, "Every surface requires a supplied texture")
			var selected: PackedInt32Array = arrays[Mesh.ARRAY_INDEX]
			if selected.is_empty():
				for i in arrays[Mesh.ARRAY_VERTEX].size(): selected.append(i)
			# Retain leaf cards intact; reduce only the dense cedar trunk/branches.
			if cedar and not leaf:
				var simplified := ImporterMesh.new()
				simplified.add_surface(Mesh.PRIMITIVE_TRIANGLES, arrays)
				simplified.generate_lods(60, 25, [])
				for lod in simplified.get_surface_lod_count(0):
					var candidate := simplified.get_surface_lod_indices(0, lod)
					if candidate.size() / 3 >= 400: selected = candidate
			var remap: Dictionary = {}
			for original: int in selected:
				if not remap.has(original):
					remap[original] = vertices.size()
					vertices.append((transform * arrays[Mesh.ARRAY_VERTEX][original] - base) * factor)
					normals.append((transform.basis.inverse().transposed() * arrays[Mesh.ARRAY_NORMAL][original]).normalized())
					uvs.append(arrays[Mesh.ARRAY_TEX_UV][original])
					colors.append(Color(layer / 255.0, 1.0 if leaf else 0.0, 0, 1))
				indices.append(remap[original])
	var output: Array = []
	output.resize(Mesh.ARRAY_MAX)
	output[Mesh.ARRAY_VERTEX] = vertices
	output[Mesh.ARRAY_NORMAL] = normals
	output[Mesh.ARRAY_TEX_UV] = uvs
	output[Mesh.ARRAY_COLOR] = colors
	output[Mesh.ARRAY_INDEX] = indices
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, output)
	_save(mesh, OUT + label + ".res")
	mesh = load(OUT + label + ".res")
	var tree := Node3D.new()
	tree.name = label.to_pascal_case()
	tree.set_meta("source_pack", "CL04 Calocedrus LOD2" if cedar else "Shapespark low-poly plants")
	tree.set_meta("triangles", indices.size() / 3)
	var radius := 0.0
	for point: Vector3 in vertices: radius = maxf(radius, Vector2(point.x, point.z).length())
	tree.set_meta("canopy_radius", radius)
	var visual := MeshInstance3D.new()
	visual.name = "Foliage"
	visual.mesh = mesh
	visual.material_override = material
	tree.add_child(visual)
	visual.owner = tree
	if target_height > 3:
		var body := StaticBody3D.new()
		body.name = "TrunkBody"
		tree.add_child(body)
		body.owner = tree
		var collision := CollisionShape3D.new()
		collision.name = "TrunkCollision"
		var cylinder := CylinderShape3D.new()
		cylinder.radius = 0.20 if cedar else 0.23
		cylinder.height = 3.0
		collision.shape = cylinder
		collision.position.y = cylinder.height * 0.5
		body.add_child(collision)
		collision.owner = tree
	var packed := PackedScene.new()
	packed.pack(tree)
	_save(packed, OUT + label + ".tscn")
	print("BAKED ", label, " triangles=", indices.size() / 3, " surfaces=1 bounds=", mesh.get_aabb())
	tree.free()

func _source_transform(node: Node3D) -> Transform3D:
	var result := node.transform
	var parent := node.get_parent()
	while parent is Node3D:
		result = parent.transform * result
		parent = parent.get_parent()
	return result

func _save(resource: Resource, path: String) -> void:
	var code := ResourceSaver.save(resource, path)
	if code != OK:
		push_error("Could not save " + path + ": " + error_string(code))
		failures += 1
