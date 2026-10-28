extends RefCounted
## Five distinct native rock silhouettes, with shared real stone textures.
const KINDS := ["weathered_boulder", "split_outcrop", "layered_slab", "talus_cluster", "jagged_crag"]
const TINTS := [Color("ac9b7e"), Color("978d78"), Color("9e917c"), Color("8c8f84"), Color("ab9275")]

static func build(kind: String, snowy: bool) -> Node3D:
	var id := KINDS.find(kind)
	var raw := PackedVector3Array()
	match id:
		0: _lobe(raw, Vector3.ZERO, Vector3.ONE, 12, 0.3, false)
		1:
			_lobe(raw, Vector3(-0.43, 0, -0.06), Vector3(0.68, 1.08, 0.92), 9, 1.2, false)
			_lobe(raw, Vector3(0.48, -0.03, 0.16), Vector3(0.62, 0.83, 0.82), 8, 2.8, false)
		2: _lobe(raw, Vector3.ZERO, Vector3(1.0, 0.80, 0.76), 10, 0.8, true)
		3:
			_lobe(raw, Vector3(-0.56, -0.02, 0.02), Vector3(0.60, 0.62, 0.75), 8, 0.2, false)
			_lobe(raw, Vector3(0.10, 0, 0.35), Vector3(0.72, 1.0, 0.86), 9, 2.0, false)
			_lobe(raw, Vector3(0.68, -0.03, -0.05), Vector3(0.49, 0.50, 0.65), 8, 3.0, false)
		4: _lobe(raw, Vector3.ZERO, Vector3(0.88, 1.25, 0.94), 7, 2.3, true)
	var bounds := AABB(raw[0], Vector3.ZERO)
	for p: Vector3 in raw: bounds = bounds.expand(p)
	var points := PackedVector3Array()
	for p: Vector3 in raw:
		points.append(Vector3((p.x - bounds.get_center().x) * 2.0 / bounds.size.x, p.y / bounds.end.y, (p.z - bounds.position.z) * 2.0 / bounds.size.z))
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	for p: Vector3 in points: st.add_vertex(p)
	st.generate_normals()
	var material := ShaderMaterial.new()
	material.resource_name = "BoundaryStone_" + kind + ("_Snow" if snowy else "")
	material.shader = load("res://shaders/rock_surface.gdshader")
	material.set_shader_parameter("rock_albedo", load("res://assets/textures/mountain-rock.png"))
	material.set_shader_parameter("tint", Color("88938f") if snowy else TINTS[id])
	material.set_shader_parameter("snow_cover", 0.70 if snowy else 0.0)
	var root := Node3D.new()
	root.name = kind.to_pascal_case() + ("Snow" if snowy else "")
	root.set_meta("rock_kind", kind)
	var visual := MeshInstance3D.new()
	visual.name = "TexturedRock"
	visual.mesh = st.commit()
	visual.material_override = material
	root.add_child(visual)
	var body := StaticBody3D.new()
	body.name = "BoundaryRockBody"
	body.add_to_group("boundary_rock", true)
	body.add_to_group("track_boundary", true)
	root.add_child(body)
	var collision := CollisionShape3D.new()
	collision.name = "RockHull"
	var hull := ConvexPolygonShape3D.new()
	var unique := {}
	for p: Vector3 in points: unique[p] = true
	var hull_points := PackedVector3Array()
	for p: Vector3 in unique: hull_points.append(p)
	hull.points = hull_points
	collision.shape = hull
	body.add_child(collision)
	return root

static func _lobe(out: PackedVector3Array, center: Vector3, size: Vector3, corners: int, phase: float, layered: bool) -> void:
	var radii := [0.88, 1.0, 0.93, 0.70, 0.36, 0.0] if not layered else [0.92, 1.0, 0.98, 0.73, 0.58, 0.0]
	var heights := [-0.16, 0.08, 0.34, 0.70, 0.94, 1.0] if not layered else [-0.16, 0.12, 0.35, 0.44, 0.88, 1.0]
	var rings: Array[PackedVector3Array] = []
	for row in 6:
		var ring := PackedVector3Array()
		for i in corners:
			var angle := i * TAU / corners
			var r: float = radii[row] * (1.0 + sin(angle * 3.0 + phase) * 0.15 + cos(angle * 5.0 - row * 0.7) * 0.06)
			var p := Vector3(cos(angle) * r + row * 0.035 * sin(phase), float(heights[row]) + sin(angle * 2.0 + phase) * r * 0.07, sin(angle) * r)
			ring.append(center + p * size)
		rings.append(ring)
	for row in 5:
		for i in corners:
			var next := (i + 1) % corners
			out.append_array(PackedVector3Array([rings[row][i], rings[row][next], rings[row + 1][i]]))
			if row < 4: out.append_array(PackedVector3Array([rings[row][next], rings[row + 1][next], rings[row + 1][i]]))
