extends SceneTree
## Explicit offline conversion of the supplied single-mesh GLB. Never run on
## gameplay startup: generated meshes and their native scene remain editable.
const SOURCE := "res://assets/models/jeep/jeep_cherokee.glb"
const OUTPUT := "res://assets/models/jeep/"
const TARGET_WHEELBASE := 2.96
const TARGET_WHEEL_RADIUS := 0.44

func _initialize() -> void: call_deferred("_run")

func _run() -> void:
	var source := (load(SOURCE) as PackedScene).instantiate() as Node3D
	root.add_child(source)
	var instance := source.find_children("*", "MeshInstance3D", true, false)[0] as MeshInstance3D
	var arrays := instance.mesh.surface_get_arrays(0)
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
	for i in vertices.size():
		var group: int = groups[ids[i]]
		var point: Vector3 = instance.global_transform * vertices[i]
		if not bounds.has(group): bounds[group] = AABB(point, Vector3.ZERO)
		bounds[group] = (bounds[group] as AABB).expand(point)
	var wheels: Dictionary = {}
	var front := -INF
	var rear := INF
	var wheel_y := 0.0
	var original_radius := 0.0
	for group: int in bounds:
		var box: AABB = bounds[group]
		if box.size.y > 40 and box.size.y < 55 and box.size.z > 40 and box.size.x < 20:
			wheels[group] = box.get_center()
			front = maxf(front, box.get_center().z)
			rear = minf(rear, box.get_center().z)
			wheel_y += box.get_center().y * 0.25
			original_radius = maxf(original_radius, maxf(box.size.y, box.size.z) * 0.5)
	if wheels.size() != 4:
		push_error("Expected exactly four disconnected tyre islands; refusing to split arbitrary geometry")
		source.free()
		quit(1)
		return
	var scale_factor := TARGET_WHEELBASE / (front - rear)
	var axes := Basis(Vector3.UP, PI / 2.0)
	var origin := Vector3((front + rear) * 0.5 * scale_factor, wheel_y * scale_factor, 0)
	var rotation := axes * instance.global_basis
	var tyre_scale := TARGET_WHEEL_RADIUS / (original_radius * scale_factor)
	var group_names: Dictionary = {}
	var pivots: Dictionary = {"body": Vector3.ZERO}
	for group: int in bounds:
		if not wheels.has(group):
			group_names[group] = "body"
			continue
		var center: Vector3 = wheels[group]
		var name := "wheel_%s%s" % ["f" if center.z > (front + rear) * 0.5 else "r", "l" if center.x < 0 else "r"]
		group_names[group] = name
		pivots[name] = axes * center * scale_factor - origin
	var bins: Dictionary = {}
	for triangle in range(0, indices.size(), 3):
		var name: String = group_names[groups[ids[indices[triangle]]]]
		if not bins.has(name): bins[name] = []
		for corner in 3: bins[name].append(indices[triangle + corner])
	var normal: PackedVector3Array = arrays[Mesh.ARRAY_NORMAL]
	var uv: PackedVector2Array = arrays[Mesh.ARRAY_TEX_UV]
	var tangents: PackedFloat32Array = arrays[Mesh.ARRAY_TANGENT]
	var wheel_material := instance.mesh.surface_get_material(0).duplicate() as StandardMaterial3D
	wheel_material.resource_name = "Jeep_original_tyres"
	wheel_material.roughness = 1.0
	if ResourceSaver.save(wheel_material, OUTPUT + "jeep_tyres.tres") != OK:
		quit(1)
		return
	var body_points := PackedVector3Array()
	for name: String in bins:
		var surface := SurfaceTool.new()
		surface.begin(Mesh.PRIMITIVE_TRIANGLES)
		surface.set_material(load(OUTPUT + "jeep_paint.tres") if name == "body" else wheel_material)
		for index: int in bins[name]:
			var p := axes * (instance.global_transform * vertices[index]) * scale_factor - origin - (pivots[name] as Vector3)
			if name != "body": p *= tyre_scale
			else: body_points.append(p)
			surface.set_normal((rotation * normal[index]).normalized())
			surface.set_uv(uv[index])
			if not tangents.is_empty():
				var tangent := rotation * Vector3(tangents[index * 4], tangents[index * 4 + 1], tangents[index * 4 + 2])
				surface.set_tangent(Plane(tangent.normalized(), tangents[index * 4 + 3]))
			surface.add_vertex(p)
		surface.index()
		var mesh := surface.commit()
		mesh.resource_name = "Jeep_" + name
		if ResourceSaver.save(mesh, OUTPUT + "jeep_" + name + ".res") != OK:
			quit(1)
			return
		print("RIG ", name, " pivot=", pivots[name], " bounds=", mesh.get_aabb())
	# Convex collision fits the supplied body, including roof and bumpers. A
	# visible skid plate preserves the previous 34 cm physical belly clearance.
	for x: float in [-1.20, 1.20]:
		for z: float in [-0.67, 0.67]: body_points.append(Vector3(x, -0.10, z))
	var shape := ConvexPolygonShape3D.new()
	shape.points = body_points
	if ResourceSaver.save(shape, OUTPUT + "jeep_chassis.res") != OK:
		quit(1)
		return
	print("RIG SCALE ", scale_factor, " tyre correction=", tyre_scale, " parts=", bins.size())
	source.queue_free()
	await process_frame
	quit()
