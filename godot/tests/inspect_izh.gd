extends SceneTree
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	var source := (load("res://art/source_vehicles/izh/IZH-2715.fbx") as PackedScene).instantiate()
	root.add_child(source)
	for node: MeshInstance3D in source.find_children("*", "MeshInstance3D", true, false):
		var materials: Array = []
		var triangles := 0
		for index in node.mesh.get_surface_count():
			var arrays := node.mesh.surface_get_arrays(index)
			var indices: PackedInt32Array = arrays[Mesh.ARRAY_INDEX]
			var colors: PackedColorArray = arrays[Mesh.ARRAY_COLOR] if arrays[Mesh.ARRAY_COLOR] != null else PackedColorArray()
			print("IZH COLORS ", node.name, " count=", colors.size(), " first=", colors.slice(0, 5))
			triangles += (indices.size() if not indices.is_empty() else arrays[Mesh.ARRAY_VERTEX].size()) / 3
			var material := node.get_active_material(index) as StandardMaterial3D
			materials.append({"name": material.resource_name if material else "", "texture": material.albedo_texture.resource_path if material and material.albedo_texture else "", "color": str(material.albedo_color) if material else ""})
		print("IZH ", JSON.stringify({"node": str(node.get_path()), "bounds": str(node.global_transform * node.mesh.get_aabb()), "triangles": triangles, "materials": materials}))
	source.queue_free()
	await process_frame
	quit()
