extends RefCounted
## Original native meshes. Generate once with tests/build_expedition_art.gd;
## gameplay loads saved scenes, never runs these construction loops.
static var _materials: Dictionary = {}

static func mat(key: String) -> Material:
	if _materials.has(key): return _materials[key]
	var colors := {
		"stone": Color("817c69"), "stone_light": Color("a39b80"), "stone_dark": Color("66695e"),
		"snow": Color("a9bab8"), "bark": Color("65533f"), "bark_light": Color("84715a"),
		"pine": Color("425e4e"), "pine_light": Color("647958"), "leaf": Color("72825b"),
		"leaf_light": Color("89916b"), "reed": Color("858860"), "reed_head": Color("66583e"),
		"steel": Color("515b57"), "dark": Color("293535"), "teal": Color("53776e"),
		"ochre": Color("ad8c55"), "rust": Color("956849"), "ivory": Color("d1c09a"),
		"glass": Color("344e55"), "lamp": Color("f1d49b"),
	}
	var material: Material
	if key == "lamp":
		var glow := StandardMaterial3D.new()
		glow.albedo_color = colors[key]
		glow.emission_enabled = true
		glow.emission = colors[key]
		glow.emission_energy_multiplier = 0.6
		material = glow
	else:
		var shaded := ShaderMaterial.new()
		shaded.shader = load("res://shaders/expedition_surface.gdshader")
		shaded.set_shader_parameter("tint", colors.get(key, Color.WHITE))
		shaded.set_shader_parameter("roughness_value", 0.68 if key in ["steel", "teal", "rust", "glass"] else 0.94)
		shaded.set_shader_parameter("metallic_value", 0.2 if key in ["steel", "teal", "rust"] else 0.0)
		preload("res://art/expedition_materials.gd").configure(shaded, key)
		material = shaded
	material.resource_name = key.to_pascal_case()
	_materials[key] = material
	return material

static func part(root: Node3D, label: String, mesh: Mesh, pos: Vector3, key: String) -> MeshInstance3D:
	var node := MeshInstance3D.new()
	node.name = label
	node.mesh = mesh
	node.material_override = mat(key)
	node.position = pos
	root.add_child(node, true)
	return node

static func box(root: Node3D, label: String, pos: Vector3, size: Vector3, key: String) -> MeshInstance3D:
	var mesh := BoxMesh.new()
	mesh.size = size
	return part(root, label, mesh, pos, key)

static func cylinder(root: Node3D, label: String, pos: Vector3, radius: float, height: float, key: String, sides := 10, top := -1.0) -> MeshInstance3D:
	var mesh := CylinderMesh.new()
	mesh.bottom_radius = radius
	mesh.top_radius = radius if top < 0 else top
	mesh.height = height
	mesh.radial_segments = sides
	return part(root, label, mesh, pos, key)

static func beam(root: Node3D, label: String, a: Vector3, b: Vector3, radius: float, key: String) -> void:
	var node := cylinder(root, label, (a + b) * 0.5, radius, a.distance_to(b), key, 6, radius * 0.75)
	node.quaternion = Quaternion(Vector3.UP, (b - a).normalized())

static func crown(root: Node3D, pos: Vector3, size: Vector3, key: String, phase := 0.0, conifer := false) -> void:
	# Irregular radial rings, not stacked cylinders or spherical blobs.
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	var rings: Array[PackedVector3Array] = []
	for row in 5:
		var points := PackedVector3Array()
		var radii := [0.95, 0.8, 0.56, 0.27, 0.02] if conifer else [0.25, 0.85, 1.0, 0.63, 0.04]
		for j in 9:
			var a := TAU * j / 8.0
			var r: float = radii[row] * (1.0 + sin(a * 3.0 + phase + row) * 0.12)
			points.append(pos + Vector3(cos(a) * r * size.x, (row / 4.0 - 0.5) * size.y, sin(a) * r * size.z))
		rings.append(points)
	for row in 4:
		for j in 8:
			for p: Vector3 in [rings[row][j], rings[row][j + 1], rings[row + 1][j], rings[row][j + 1], rings[row + 1][j + 1], rings[row + 1][j]]: st.add_vertex(p)
	st.generate_normals()
	part(root, "Canopy", st.commit(), Vector3.ZERO, key)

static func tree(kind: String) -> Node3D:
	var root := Node3D.new()
	root.name = kind.to_pascal_case()
	var pine := kind in ["mountain_pine", "alpine_pine", "juniper"]
	var dead := kind == "dead_tree"
	var size := 0.62 if kind == "juniper" else 1.0
	cylinder(root, "Trunk", Vector3.UP * 3.8 * size, 0.28 * size, 7.6 * size, "bark", 8, 0.08)
	for i in (9 if pine else 7):
		var a := i * 2.39996
		var y := (2.7 + i * (0.58 if pine else 0.48)) * size
		var reach := (2.45 - i * 0.17) * size if pine else (2.1 + (i % 3) * 0.4) * size
		var tip := Vector3(cos(a) * reach, y + (0.1 if pine else 1.5) * size, sin(a) * reach)
		beam(root, "Branch", Vector3(0, y, 0), tip, 0.12 * size, "bark")
		if dead:
			beam(root, "ForkedTwig", tip * Vector3(0.8, 1, 0.8), tip + Vector3(0.3, 0.8, -0.3), 0.045, "bark_light")
		else:
			crown(root, tip + Vector3.UP * 0.35 * size, Vector3(reach * 0.7, (2.8 if pine else 2.4) * size, reach * 0.65), "pine" if pine else ("leaf" if i % 2 == 0 else "leaf_light"), a, pine)
	if pine:
		crown(root, Vector3.UP * 7.5 * size, Vector3(1.25, 3.6, 1.15) * size, "pine_light", 1.5, true)
	if kind in ["wetland_tree", "dead_tree"]:
		for i in 5:
			var a := TAU * i / 5.0
			beam(root, "ExposedRoot", Vector3.UP * 0.9, Vector3(cos(a) * 1.1, 0.02, sin(a) * 1.1), 0.16, "bark")
	merge_static(root)
	if kind == "alpine_pine":
		for node in root.get_children():
			if node is MeshInstance3D and node.name in ["Pine", "PineLight"]:
				var snowy := node.material_override.duplicate() as ShaderMaterial
				snowy.set_shader_parameter("snow_cover", 0.9)
				node.material_override = snowy
	DesertModels.collider(root, Vector3.UP * 2.5 * size, Vector3(0.52, 5.0, 0.52) * size)
	return root

static func undergrowth(kind: String) -> Node3D:
	var root := Node3D.new()
	root.name = kind.to_pascal_case()
	if kind == "marsh_reeds":
		for i in 11:
			var a := i * 2.4
			var p := Vector3(cos(a) * 0.65, 0, sin(a) * 0.65)
			var tip := p + Vector3(sin(a) * 0.24, 1.35 + (i % 4) * 0.19, cos(a) * 0.18)
			beam(root, "Reed", p, tip, 0.035, "reed")
			cylinder(root, "SeedHead", tip, 0.075, 0.35, "reed_head", 6, 0.065)
			beam(root, "ReedLeaf", p + Vector3.UP * 0.6, tip + Vector3(cos(a) * 0.35, -0.15, sin(a) * 0.35), 0.045, "reed")
	elif kind == "fern_patch":
		var st := SurfaceTool.new()
		st.begin(Mesh.PRIMITIVE_TRIANGLES)
		for frond in 9:
			var a := frond * 2.4
			var direction := Vector3(cos(a), 0, sin(a))
			var right := Vector3(-sin(a), 0, cos(a))
			for leaflet in 5:
				var t := (leaflet + 1) / 6.0
				var p := direction * t * 1.15 + Vector3.UP * sin(t * PI) * 0.72
				var width := sin(t * PI) * 0.28
				for side: float in [-1, 1]:
					var points := [p, p - direction * 0.14 + right * width * side, p + direction * 0.21]
					for point: Vector3 in points: st.add_vertex(point)
		st.generate_normals()
		var leaves := mat("leaf").duplicate() as ShaderMaterial
		# Solid double-sided blades avoid alpha-card shimmer.
		var mesh := part(root, "FernLeaves", st.commit(), Vector3.ZERO, "leaf")
		mesh.material_override = leaves
	else:
		for i in 6:
			var a := i * 2.4
			var tip := Vector3(cos(a) * 0.6, 0.65 + (i % 3) * 0.14, sin(a) * 0.6)
			beam(root, "ShrubBranch", Vector3.ZERO, tip, 0.04, "bark")
			crown(root, tip, Vector3(0.6, 0.8, 0.5), "leaf_light" if kind == "meadow_shrub" else "pine_light", a)
	merge_static(root)
	return root

static func mountain(alpine := false) -> Node3D:
	var root := Node3D.new()
	root.name = "AlpinePeak" if alpine else "MountainRidge"
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	var rows: Array[PackedVector3Array] = []
	# Front face at z=0; all mass is OUTSIDE the playable boundary. This
	# keeps switchbacks, service yards and existing boundary collision intact.
	for i in 9:
		var x := -6.0 + i * 1.5
		var ridge := 8.5 + sin(i * 1.27) * 1.4 + (3.8 if alpine else 0.0)
		rows.append(PackedVector3Array([Vector3(x, -4, 0), Vector3(x, 1.5, 0.4), Vector3(x, ridge * 0.58, 3.0), Vector3(x + sin(i) * 0.4, ridge * 0.92, 6.0), Vector3(x, ridge, 8.0), Vector3(x, ridge * 0.90, 10.0), Vector3(x, ridge * 0.65, 12), Vector3(x, -4, 18)]))
	for i in 8:
		for j in 7:
			for p: Vector3 in [rows[i][j], rows[i + 1][j], rows[i][j + 1], rows[i + 1][j], rows[i + 1][j + 1], rows[i][j + 1]]: st.add_vertex(p)
	st.generate_normals()
	var material := mat("stone").duplicate() as ShaderMaterial
	material.set_shader_parameter("snow_cover", 0.65 if alpine else 0.0)
	if alpine: material.set_shader_parameter("tint", Color("737e79"))
	preload("res://art/expedition_materials.gd").configure(material, "stone", "alpine_peak" if alpine else "mountain_ridge")
	part(root, "ErodedRidge", st.commit(), Vector3.ZERO, "stone").material_override = material
	return root

static func arch(root: Node3D, radius: float, rise: float, spring: float, thickness: float, depth: float) -> void:
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	for i in 16:
		var a := i * PI / 16.0
		var b := (i + 1) * PI / 16.0
		var inner_a := Vector3(cos(a) * radius, spring + sin(a) * rise, 0)
		var inner_b := Vector3(cos(b) * radius, spring + sin(b) * rise, 0)
		var outer_a := Vector3(cos(a) * (radius + thickness), spring + sin(a) * (rise + thickness), 0)
		var outer_b := Vector3(cos(b) * (radius + thickness), spring + sin(b) * (rise + thickness), 0)
		for face: Array in [[inner_a, outer_a, inner_b, outer_a, outer_b, inner_b], [inner_a, inner_b, inner_a + Vector3.BACK * depth, inner_b, inner_b + Vector3.BACK * depth, inner_a + Vector3.BACK * depth], [outer_a, outer_a + Vector3.BACK * depth, outer_b, outer_b, outer_a + Vector3.BACK * depth, outer_b + Vector3.BACK * depth]]:
			for p: Vector3 in face: st.add_vertex(p - Vector3.BACK * depth * 0.5)
	st.generate_normals()
	part(root, "VoussoirArch", st.commit(), Vector3.ZERO, "stone_light")
	for side: float in [-1, 1]:
		box(root, "PortalPier", Vector3(side * (radius + thickness * 0.5), spring * 0.5, 0), Vector3(thickness, spring, depth), "stone")

static func peak(snowy := false) -> Node3D:
	var root := Node3D.new()
	root.name = "SnowyPeak" if snowy else "RockyPeak"
	var rings: Array[PackedVector3Array] = []
	# Broad eroded shoulders and a rounded crown, without a needle apex.
	# Keep the frontmost edge outside the existing playable/collision boundary.
	var radii := [14.0, 13.7, 12.8, 10.8, 8.0, 5.6, 3.0, 0.0]
	var heights := [-3.0, 0.5, 5.0, 10.0, 14.5, 17.5, 19.0, 19.5]
	for row in radii.size():
		var points := PackedVector3Array()
		for i in 29:
			var a := TAU * (i % 28) / 28.0
			var phase := 1.7 if snowy else 0.2
			var radius: float = radii[row] * (1.0 + 0.13 * sin(a * 3.0 + phase) + 0.06 * cos(a * 5.0 - row * 0.4))
			var shoulder: float = sin(a * 2.0 + phase) * [0.0, 0.3, 0.9, 1.2, 1.0, 0.6, 0.2, 0.0][row]
			points.append(Vector3(cos(a) * radius + sin(row * 0.5) * 1.4, heights[row] + shoulder, 20 + sin(a) * radius * 0.85 + row * 0.25))
		rings.append(points)
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	for row in radii.size() - 1:
		for i in 28:
			for p: Vector3 in [rings[row][i], rings[row][i + 1], rings[row + 1][i]]: st.add_vertex(p)
			if row < radii.size() - 2:
				for p: Vector3 in [rings[row][i + 1], rings[row + 1][i + 1], rings[row + 1][i]]: st.add_vertex(p)
	st.generate_normals()
	var material := mat("stone").duplicate() as ShaderMaterial
	material.set_shader_parameter("tint", Color("7c8b87") if snowy else Color("998a6e"))
	material.set_shader_parameter("snow_cover", 0.8 if snowy else 0.0)
	preload("res://art/expedition_materials.gd").configure(material, "stone", "snowy_peak" if snowy else "rocky_peak")
	part(root, "SculptedPeak", st.commit(), Vector3.ZERO, "stone").material_override = material
	return root

static func rail_portal() -> Node3D:
	var root := Node3D.new()
	root.name = "RailPortal"
	arch(root, 2.5, 2.9, 3.2, 0.8, 1.6)
	for side: float in [-1, 1]:
		box(root, "WingWall", Vector3(side * 4.0, 2.1, 0.6), Vector3(1.4, 4.2, 2.8), "stone_dark")
		box(root, "Cap", Vector3(side * 4.0, 4.35, 0.0), Vector3(1.65, 0.3, 2.0), "stone_light")
	box(root, "Keystone", Vector3(0, 6.45, -0.9), Vector3(0.6, 0.9, 0.25), "ivory")
	merge_static(root)
	return root

static func tunnel(portal := true) -> Node3D:
	var root := Node3D.new()
	root.name = "TunnelPortal" if portal else "TunnelRib"
	# Existing swept lining has radius 6.4/rise 4.6/spring 2.4. New visible
	# masonry sits outside it, never intrudes into the driven corridor.
	arch(root, 6.45, 4.65, 2.4, 1.0 if portal else 0.24, 1.3 if portal else 0.28)
	if portal:
		for side: float in [-1, 1]:
			box(root, "WingWall", Vector3(side * 8.4, 2.2, 1.1), Vector3(2.0, 4.4, 3.6), "stone_dark")
			box(root, "ButtressCap", Vector3(side * 8.4, 4.5, 0.5), Vector3(2.25, 0.32, 2.8), "stone_light")
			box(root, "Reflector", Vector3(side * 6.72, 1.0, -0.7), Vector3(0.13, 0.65, 0.08), "ivory")
		box(root, "Keystone", Vector3(0, 7.25, -0.78), Vector3(0.8, 0.9, 0.28), "ivory")
		box(root, "LampHousing", Vector3(0, 6.85, -0.78), Vector3(0.8, 0.15, 0.28), "steel")
		box(root, "LampLens", Vector3(0, 6.74, -0.78), Vector3(0.6, 0.06, 0.18), "lamp")
	merge_static(root)
	return root

static func train(kind: String) -> Node3D:
	var root := Node3D.new()
	root.name = kind.to_pascal_case()
	box(root, "Chassis", Vector3(0, 0.9, 0), Vector3(2.6, 0.38, 6.9), "steel")
	for end: float in [-1, 1]:
		box(root, "Coupler", Vector3(0, 0.85, end * 3.85), Vector3(0.35, 0.22, 0.8), "dark")
		box(root, "BufferBeam", Vector3(0, 1, end * 3.45), Vector3(2.8, 0.3, 0.18), "ochre")
		for side: float in [-1, 1]: cylinder(root, "Buffer", Vector3(side * 0.95, 0.92, end * 3.62), 0.16, 0.25, "dark").rotation.x = PI * 0.5
	for z: float in [-2.3, 2.3]:
		box(root, "BogieFrame", Vector3(0, 0.52, z), Vector3(2.45, 0.3, 1.65), "dark")
		for axle: float in [-0.5, 0.5]:
			for side: float in [-1, 1]:
				var wheel := cylinder(root, "TrainWheel", Vector3(side * 1.17, 0.43, z + axle), 0.43, 0.18, "steel", 12)
				wheel.rotation.z = PI * 0.5
				wheel.add_to_group("train_wheel", true)
				var hub := cylinder(wheel, "WheelHub", Vector3(0, side * -0.12, 0), 0.18, 0.06, "dark", 10)
				hub.owner = null
	if kind == "locomotive":
		box(root, "EngineHood", Vector3(0, 1.75, 0.65), Vector3(2.22, 1.35, 4.6), "teal")
		box(root, "Cabin", Vector3(0, 2.1, -2.0), Vector3(2.55, 2.05, 1.95), "teal")
		box(root, "CabRoof", Vector3(0, 3.2, -2.0), Vector3(2.78, 0.22, 2.18), "ivory")
		box(root, "RoofVent", Vector3(0, 2.52, 0.55), Vector3(1.15, 0.18, 1.9), "steel")
		cylinder(root, "ExhaustStack", Vector3(0.6, 2.85, -0.1), 0.16, 0.65, "dark", 8)
		for side: float in [-1, 1]:
			box(root, "SideWindow", Vector3(side * 1.285, 2.55, -2), Vector3(0.035, 0.68, 1.05), "glass")
			box(root, "IvoryStripe", Vector3(side * 1.13, 1.55, 0.6), Vector3(0.035, 0.18, 4.7), "ivory")
			for vent in 7: box(root, "VentSlat", Vector3(side * 1.13, 2.03, -0.3 + vent * 0.38), Vector3(0.04, 0.46, 0.12), "dark")
			beam(root, "WalkwayRail", Vector3(side * 1.3, 1.7, -0.7), Vector3(side * 1.3, 1.7, 3), 0.035, "ivory")
			for post: float in [-0.7, 1.1, 3]: beam(root, "RailPost", Vector3(side * 1.3, 1.06, post), Vector3(side * 1.3, 1.7, post), 0.03, "ivory")
		for side: float in [-1, 1]: box(root, "FrontWindshield", Vector3(side * 0.63, 2.6, -3.0), Vector3(0.95, 0.64, 0.04), "glass")
		box(root, "Headlamp", Vector3(0, 2.05, 3.02), Vector3(0.65, 0.27, 0.12), "lamp")
	elif kind == "tank_wagon":
		cylinder(root, "TankBarrel", Vector3(0, 2.0, 0), 1.08, 5.6, "ivory", 14).rotation.x = PI * 0.5
		for z: float in [-2.85, 2.85]: crown(root, Vector3(0, 2.0, z), Vector3(1.02, 1.9, 0.25), "ivory")
		for z: float in [-2, 2]: box(root, "TankSaddle", Vector3(0, 1.2, z), Vector3(2.35, 0.3, 0.5), "steel")
		cylinder(root, "FillerHatch", Vector3(0, 3.15, 0), 0.34, 0.18, "steel")
		for rung in 7: beam(root, "LadderRung", Vector3(0.75, 1.0 + rung * 0.3, 0), Vector3(1.25, 1.0 + rung * 0.3, 0), 0.035, "steel")
	else:
		var covered := kind == "freight_wagon"
		box(root, "FreightBody", Vector3(0, 1.9 if covered else 1.25, 0), Vector3(2.55, 1.8 if covered else 0.38, 6.5), "rust")
		if covered:
			box(root, "FreightRoof", Vector3(0, 2.88, 0), Vector3(2.7, 0.2, 6.65), "ivory")
			for side: float in [-1, 1]:
				box(root, "SlidingDoor", Vector3(side * 1.3, 1.95, 0), Vector3(0.04, 1.6, 2.1), "ochre")
				for rib in 9: box(root, "BodyRib", Vector3(side * 1.3, 1.95, -3 + rib * 0.75), Vector3(0.08, 1.7, 0.07), "rust")
		else:
			for i in 3:
				box(root, "TimberCargo", Vector3(0, 1.7, -2 + i * 2), Vector3(2.1, 0.6, 1.6), "bark_light")
				for side: float in [-1, 1]: beam(root, "CargoStake", Vector3(side * 1.22, 1.1, -2 + i * 2), Vector3(side * 1.22, 2.3, -2 + i * 2), 0.065, "steel")
	merge_static(root)
	return root

static func merge_static(root: Node3D) -> void:
	# Save a few material-grouped meshes, rather than hundreds of tiny draws.
	# Wheels stay independent, so their children spin with their hubs.
	var groups: Dictionary = {}
	for child in root.get_children():
		if not child is MeshInstance3D or child.is_in_group("train_wheel"): continue
		var material: Material = child.material_override
		if not groups.has(material): groups[material] = []
		groups[material].append(child)
	for material: Material in groups:
		var st := SurfaceTool.new()
		st.begin(Mesh.PRIMITIVE_TRIANGLES)
		for node: MeshInstance3D in groups[material]:
			# Primitive meshes include UV/tangents; authored silhouettes do not.
			# append_from() switches format and can discard the earlier arch.
			# These world/local-space materials only need positions and normals.
			var arrays := node.mesh.surface_get_arrays(0)
			var vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
			var normals: PackedVector3Array = arrays[Mesh.ARRAY_NORMAL]
			var indices: PackedInt32Array = arrays[Mesh.ARRAY_INDEX] if arrays[Mesh.ARRAY_INDEX] != null else PackedInt32Array()
			var normal_basis := node.basis.inverse().transposed()
			for i in (indices.size() if not indices.is_empty() else vertices.size()):
				var index := indices[i] if not indices.is_empty() else i
				st.set_normal((normal_basis * normals[index]).normalized())
				st.add_vertex(node.transform * vertices[index])
		st.index()
		var combined := MeshInstance3D.new()
		combined.name = material.resource_name
		combined.material_override = material
		combined.mesh = st.commit()
		for node: MeshInstance3D in groups[material]:
			root.remove_child(node)
			node.free()
		root.add_child(combined, true)
