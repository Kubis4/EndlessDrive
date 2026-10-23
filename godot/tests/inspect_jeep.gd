extends SceneTree
## Read-only asset analysis; output images go into ignored build/.
func _initialize() -> void:
	call_deferred("_run")

func _run() -> void:
	var model := (load("res://assets/models/jeep/jeep_cherokee.glb") as PackedScene).instantiate()
	root.add_child(model)
	for instance: Node in model.find_children("*", "MeshInstance3D", true, false):
		var mesh := (instance as MeshInstance3D).mesh
		print("MESH ", instance.get_path(), " transform=", instance.global_transform, " bounds=", mesh.get_aabb())
		for surface in mesh.get_surface_count():
			var arrays := mesh.surface_get_arrays(surface)
			var vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
			var indices: PackedInt32Array = arrays[Mesh.ARRAY_INDEX]
			var welded: Dictionary = {}
			var ids: Array[int] = []
			var neighbors: Dictionary = {}
			for vertex: Vector3 in vertices:
				var key := Vector3i(roundi(vertex.x * 1000), roundi(vertex.y * 1000), roundi(vertex.z * 1000))
				if not welded.has(key): welded[key] = welded.size()
				var id := int(welded[key])
				ids.append(id)
				neighbors[id] = []
			for triangle in range(0, indices.size(), 3):
				for edge in 3:
					var a := ids[indices[triangle + edge]]
					var b := ids[indices[triangle + (edge + 1) % 3]]
					neighbors[a].append(b)
					neighbors[b].append(a)
			var groups: Dictionary = {}
			var count := 0
			for id: int in neighbors:
				if groups.has(id): continue
				var stack: Array[int] = [id]
				groups[id] = count
				while not stack.is_empty():
					var current: int = stack.pop_back()
					for next: int in neighbors[current]:
						if not groups.has(next):
							groups[next] = count
							stack.append(next)
				count += 1
			var bounds: Dictionary = {}
			var sizes: Dictionary = {}
			for i in vertices.size():
				var group: int = groups[ids[i]]
				var point: Vector3 = instance.global_transform * vertices[i]
				if not bounds.has(group):
					bounds[group] = AABB(point, Vector3.ZERO)
					sizes[group] = 0
				bounds[group] = (bounds[group] as AABB).expand(point)
				sizes[group] += 1
			for group: int in bounds:
				print("ISLAND ", group, " verts=", sizes[group], " world bounds=", bounds[group])
			var material := mesh.surface_get_material(surface) as StandardMaterial3D
			print("MATERIAL ", material, " albedo=", material.albedo_color, " roughness=", material.roughness, " metallic=", material.metallic)
			if material.albedo_texture:
				print("ALBEDO ", material.albedo_texture.resource_path, " size=", material.albedo_texture.get_size())
				material.albedo_texture.get_image().save_png(ProjectSettings.globalize_path("res://../build/jeep-albedo.png"))
	model.queue_free()
	if ResourceLoader.exists("res://assets/models/jeep/jeep_body.res"):
		var body := load("res://assets/models/jeep/jeep_body.res") as ArrayMesh
		var arrays := body.surface_get_arrays(0)
		var positions: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
		var normals: PackedVector3Array = arrays[Mesh.ARRAY_NORMAL]
		var indices: PackedInt32Array = arrays[Mesh.ARRAY_INDEX]
		for triangle in range(0, indices.size(), 3):
			var p := (positions[indices[triangle]] + positions[indices[triangle + 1]] + positions[indices[triangle + 2]]) / 3.0
			if p.x < -2.1 and p.y > 0.2 and p.y < 0.85 and normals[indices[triangle]].x < -0.5:
				print("REAR FACE ", p, " normal=", normals[indices[triangle]])
	await process_frame
	quit()
