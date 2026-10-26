extends SceneTree

func _initialize() -> void: call_deferred("_run")

func _run() -> void:
	for path: String in ["res://art/source_plants/shapespark/plants.fbx", "res://art/source_plants/calocedrus/MESH_CL04_CalocedrusDecurrens_A_LOD2.fbx", "res://art/source_plants/calocedrus/MESH_CL04_CalocedrusDecurrens_M_LOD2.fbx", "res://art/source_plants/calocedrus/MESH_CL04_CalocedrusDecurrens_Y_LOD2.fbx"]:
		var packed := load(path) as PackedScene
		if packed == null:
			quit(1)
			return
		var scene := packed.instantiate()
		print("SOURCE ", path)
		_inspect(scene, Transform3D.IDENTITY)
		scene.free()
	quit()

func _inspect(node: Node, parent_transform: Transform3D) -> void:
	var transform := parent_transform
	if node is Node3D: transform *= node.transform
	if node is MeshInstance3D:
		var triangles := 0
		var materials: Array = []
		for surface in node.mesh.get_surface_count():
			var arrays: Array = node.mesh.surface_get_arrays(surface)
			triangles += (arrays[Mesh.ARRAY_INDEX].size() if arrays[Mesh.ARRAY_INDEX] != null and not arrays[Mesh.ARRAY_INDEX].is_empty() else arrays[Mesh.ARRAY_VERTEX].size()) / 3
			var mat: Material = node.get_active_material(surface)
			var uv_min := Vector2(INF, INF)
			var uv_max := Vector2(-INF, -INF)
			for uv: Vector2 in arrays[Mesh.ARRAY_TEX_UV]:
				uv_min = uv_min.min(uv)
				uv_max = uv_max.max(uv)
			if str(node.name) in ["Tree-01-3", "Tree-02-3", "Tree-03-3", "Bush-03", "Grass-01"]: print("UV ", node.name, " ", surface, " ", uv_min, " ", uv_max)
			if str(node.name).begins_with("MI_LOD"):
				var im := ImporterMesh.new()
				im.add_surface(Mesh.PRIMITIVE_TRIANGLES, arrays)
				im.generate_lods(60, 25, [])
				var levels: Array = []
				for lod in im.get_surface_lod_count(0): levels.append(im.get_surface_lod_indices(0, lod).size() / 3)
				print("LOD ", node.name, " ", levels)
			materials.append({"name": mat.resource_name if mat != null else "", "texture": mat.albedo_texture.resource_path if mat is StandardMaterial3D and mat.albedo_texture != null else "", "color": str(mat.albedo_color) if mat is StandardMaterial3D else "", "transparency": mat.transparency if mat is StandardMaterial3D else 0})
		print(JSON.stringify({"name": str(node.name), "triangles": triangles, "surfaces": node.mesh.get_surface_count(), "bounds": str(transform * node.mesh.get_aabb()), "materials": materials}))
	for child: Node in node.get_children(): _inspect(child, transform)
