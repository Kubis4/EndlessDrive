extends SceneTree
const OUT := "res://assets/models/izh/"
func _initialize() -> void: call_deferred("_run")
func own(node: Node, owner_node: Node) -> void:
	for child: Node in node.get_children():
		child.owner = owner_node
		own(child, owner_node)
func _run() -> void:
	DirAccess.make_dir_recursive_absolute(OUT)
	var source := (load("res://art/source_vehicles/izh/IZH-2715.fbx") as PackedScene).instantiate()
	root.add_child(source)
	var body := source.get_node("OldCar") as MeshInstance3D
	var wheels: Array[MeshInstance3D] = []
	for child in body.get_children():
		if child is MeshInstance3D: wheels.append(child)
	assert(wheels.size() == 4)
	var origin := Vector3.ZERO
	var centres: Dictionary = {}
	for wheel in wheels:
		var bounds: AABB = wheel.global_transform * wheel.mesh.get_aabb()
		centres[wheel] = bounds.get_center()
		origin += bounds.get_center() / 4.0
	var axes := Basis(Vector3.UP, PI / 2)
	var car := Node3D.new()
	car.name = "IZH2715"
	car.set_meta("model_id", "izh")
	var paint := ShaderMaterial.new()
	paint.resource_name = "Desert_paint"
	paint.shader = load("res://shaders/izh_paint.gdshader")
	paint.set_shader_parameter("albedo_texture", load(OUT + "textures/IZH-2715_albedo.png"))
	paint.set_shader_parameter("roughness_texture", load(OUT + "textures/IZH-2715_roughness.png"))
	paint.set_shader_parameter("metalness_texture", load(OUT + "textures/IZH-2715_metalness.png"))
	ResourceSaver.save(paint, OUT + "izh_paint.tres")
	var rubber := StandardMaterial3D.new()
	rubber.vertex_color_use_as_albedo = false
	rubber.albedo_texture = load(OUT + "textures/IZH-2715_albedo.png")
	rubber.roughness_texture = load(OUT + "textures/IZH-2715_roughness.png")
	rubber.metallic = 1.0
	rubber.metallic_texture = load(OUT + "textures/IZH-2715_metalness.png")
	rubber.roughness = 0.95
	ResourceSaver.save(rubber, OUT + "izh_tyres.tres")
	var body_mesh: ArrayMesh
	for node: MeshInstance3D in [body] + wheels:
		var is_body := node == body
		var pivot_position := Vector3.ZERO if is_body else axes * ((centres[node] as Vector3) - origin)
		var label := "body" if is_body else "wheel_%s%s" % ["f" if pivot_position.x > 0 else "r", "l" if pivot_position.z > 0 else "r"]
		var arrays := node.mesh.surface_get_arrays(0)
		var points: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
		var normals: PackedVector3Array = arrays[Mesh.ARRAY_NORMAL]
		var uvs: PackedVector2Array = arrays[Mesh.ARRAY_TEX_UV]
		assert(uvs.size() == points.size(), "IZH model must have UV coordinates")
		var indices: PackedInt32Array = arrays[Mesh.ARRAY_INDEX]
		if indices.is_empty():
			for i in points.size(): indices.append(i)
		var st := SurfaceTool.new()
		st.begin(Mesh.PRIMITIVE_TRIANGLES)
		var normal_basis := axes * node.global_basis.inverse().transposed()
		for index in indices:
			var position := axes * (node.global_transform * points[index] - origin) - pivot_position
			var normal := (normal_basis * normals[index]).normalized()
			var color := Color(0.82, 0.82, 0.79, 1)
			if is_body:
				if position.x > 0.25 and position.x < 1.45 and position.y > 0.62 and position.y < 1.29 and absf(normal.y) < 0.75: color = Color(0.075, 0.10, 0.11, 0)
				elif position.y < 0.02: color = Color(0.15, 0.16, 0.15, 0)
			else:
				var radius := Vector2(position.x, position.y).length()
				color = Color("70736b") if radius < 0.19 else Color("242724")
			st.set_color(color)
			st.set_normal(normal)
			st.set_uv(uvs[index])
			st.add_vertex(position)
		st.index()
		var mesh := st.commit()
		ResourceSaver.save(mesh, OUT + "izh_" + label + ".res", ResourceSaver.FLAG_COMPRESS)
		var visual := MeshInstance3D.new()
		visual.name = "Body" if is_body else "TyreAndRim"
		visual.mesh = load(OUT + "izh_" + label + ".res")
		visual.material_override = load(OUT + ("izh_paint.tres" if is_body else "izh_tyres.tres"))
		if is_body:
			car.add_child(visual)
			body_mesh = mesh
		else:
			var pivot := Node3D.new()
			pivot.name = "Wheel_" + label.trim_prefix("wheel_").to_upper()
			pivot.position = pivot_position
			car.add_child(pivot)
			pivot.add_child(visual)
		print("IZH RIG ", label, " pivot=", pivot_position, " bounds=", mesh.get_aabb())
	var shape := body_mesh.create_convex_shape(true, true)
	ResourceSaver.save(shape, OUT + "izh_chassis.res", ResourceSaver.FLAG_COMPRESS)
	for side: float in [-0.68, 0.68]:
		var marker := Marker3D.new()
		marker.name = "HeadlightL" if side > 0 else "HeadlightR"
		marker.position = Vector3(2.0, 0.48, side)
		car.add_child(marker)
		var lens := MeshInstance3D.new()
		lens.name = "BrakeLampL" if side > 0 else "BrakeLampR"
		var box := BoxMesh.new()
		box.size = Vector3(0.015, 0.07, 0.12)
		lens.mesh = box
		lens.position = Vector3(-2.46, 0.24, signf(side) * 0.45)
		var material := StandardMaterial3D.new()
		material.albedo_color = Color("8f241b")
		lens.material_override = material
		car.add_child(lens)
	own(car, car)
	var packed := PackedScene.new()
	packed.pack(car)
	ResourceSaver.save(packed, OUT + "izh_2715.tscn")
	car.free()
	source.queue_free()
	await process_frame
	quit()
