extends Node3D
## Runtime-built test grounds: surface lanes (asphalt, gravel, clay, mud, water
## ford, moguls with a tabletop jump), an asphalt skid pad and the mixed-surface
## circuit with its obstacles. Geometry follows terrain.gd exactly, so wheels and
## the rendered ground share one height field.
const Surface = preload("res://desert_battlefield/terrain.gd")
const CELL := 0.25
const CHUNK_CELLS := 64
var material: ShaderMaterial
var water_material: ShaderMaterial
var shared_indices := {}
var triangle_count := 0
var chunk_count := 0
var signs: Array[Label3D] = []
var ripples := preload("res://desert_battlefield/water_ripples.gd").new()
var soil

func build(root: Node3D) -> void:
	name = "TestGrounds"
	soil = root.soil
	material = ShaderMaterial.new()
	material.shader = preload("res://desert_battlefield/course.gdshader")
	soil.bind_material(material)
	material.set_shader_parameter("course_rect", Surface.COURSE)
	material.set_shader_parameter("pad_rect", Surface.PAD)
	material.set_shader_parameter("pad_circle", Vector3(Surface.PAD_CENTER.x, Surface.PAD_CENTER.y, Surface.PAD_RADIUS))
	material.set_shader_parameter("margin", Surface.MARGIN)
	material.set_shader_parameter("lane_x0", Surface.LANE_X0)
	material.set_shader_parameter("lane_x1", Surface.LANE_X1)
	material.set_shader_parameter("lane_half", Surface.LANE_HALF)
	material.set_shader_parameter("lane_spacing", Surface.LANE_SPACING)
	material.set_shader_parameter("lane_z", PackedFloat32Array(Surface.LANE_Z))
	material.set_shader_parameter("lane_type", PackedInt32Array(Surface.LANE_TYPE))
	material.set_shader_parameter("moguls_lane", Surface.LANE_MOGULS)
	material.set_shader_parameter("jump_lip", Vector2(Surface.JUMP_LIP - 0.4, Surface.JUMP_LIP))
	material.set_shader_parameter("water_x", Vector2(Surface.WATER_X0, Surface.WATER_X1))
	material.set_shader_parameter("circuit_rect", Surface.CIRCUIT)
	material.set_shader_parameter("circuit_half", Surface.CIRCUIT_HALF)
	material.set_shader_parameter("circuit_length", Surface.circuit_length())
	material.set_shader_parameter("circuit_start_line", Surface.CIRCUIT_START_LINE)
	material.set_shader_parameter("section_start", Surface.circuit_path[2])
	material.set_shader_parameter("section_kind", Surface.circuit_path[3])
	for rect: Vector4 in [Surface.COURSE, Surface.PAD, Surface.ROCKS, Surface.CIRCUIT]:
		_build_platform(rect)
	_build_water()
	_build_markers()
	_build_circuit_obstacles()

## Platform grid plus a one-cell halo. The halo follows the authored coarse dune
## triangles and is clipped in the shader, like the fine sand patches.
func _build_platform(rect: Vector4) -> void:
	var cells_x := roundi((rect.z - rect.x) / CELL)
	var cells_z := roundi((rect.w - rect.y) / CELL)
	var side_x := cells_x + 3
	var side_z := cells_z + 3
	var heights := PackedFloat32Array()
	heights.resize(side_x * side_z)
	# Rock relief and trail mask per vertex (only the rocky ground uses them).
	var rocky := rect == Surface.ROCKS
	var paints := PackedColorArray()
	paints.resize(side_x * side_z)
	# Circuit track coordinates per vertex (course.gdshader circuit_data).
	var circuit := rect == Surface.CIRCUIT
	var track := PackedColorArray()
	if circuit: track.resize(side_x * side_z)
	for z in side_z:
		var pz := rect.y + (z - 1) * CELL
		for x in side_x:
			var px := rect.x + (x - 1) * CELL
			var border := minf(minf(px - rect.x, rect.z - px), minf(pz - rect.y, rect.w - pz))
			var h: float
			if circuit:
				var coords := Surface.circuit_coords(px, pz)
				h = Surface.circuit_height_from(px, pz, coords) if border > 0.0 else Surface.height_at(px, pz)
				track[z * side_x + x] = Surface.circuit_paint(coords)
			else:
				h = Surface.height_at(px, pz)
			if border < 0.75: h = lerpf(soil.coarse_height(px, pz), h, smoothstep(0.0, 0.75, maxf(border, 0.0)))
			heights[z * side_x + x] = h
			paints[z * side_x + x] = Surface.rock_paint(px, pz) if rocky else Color(0, 0, 0, 0)
	var container := Node3D.new()
	container.name = "Platform_%d_%d" % [roundi(rect.x), roundi(rect.y)]
	add_child(container)
	for cz in range(0, side_z - 1, CHUNK_CELLS):
		for cx in range(0, side_x - 1, CHUNK_CELLS):
			var nx := mini(CHUNK_CELLS, side_x - 1 - cx) + 1
			var nz := mini(CHUNK_CELLS, side_z - 1 - cz) + 1
			var vertices := PackedVector3Array()
			var normals := PackedVector3Array()
			var colors := PackedColorArray()
			var custom := PackedFloat32Array()
			vertices.resize(nx * nz)
			normals.resize(nx * nz)
			colors.resize(nx * nz)
			if circuit: custom.resize(nx * nz * 4)
			for z in nz:
				for x in nx:
					var gx := cx + x
					var gz := cz + z
					var h := heights[gz * side_x + gx]
					var hl := heights[gz * side_x + maxi(gx - 1, 0)]
					var hr := heights[gz * side_x + mini(gx + 1, side_x - 1)]
					var hd := heights[maxi(gz - 1, 0) * side_x + gx]
					var hu := heights[mini(gz + 1, side_z - 1) * side_x + gx]
					var span_x := float(mini(gx + 1, side_x - 1) - maxi(gx - 1, 0)) * CELL
					var span_z := float(mini(gz + 1, side_z - 1) - maxi(gz - 1, 0)) * CELL
					vertices[z * nx + x] = Vector3(rect.x + (gx - 1) * CELL, h, rect.y + (gz - 1) * CELL)
					colors[z * nx + x] = paints[gz * side_x + gx]
					normals[z * nx + x] = Vector3((hl - hr) / span_x, 1.0, (hd - hu) / span_z).normalized()
					if circuit:
						var t := track[gz * side_x + gx]
						var i := (z * nx + x) * 4
						custom[i] = t.r
						custom[i + 1] = t.g
						custom[i + 2] = t.b
						custom[i + 3] = t.a
			var arrays := []
			arrays.resize(Mesh.ARRAY_MAX)
			arrays[Mesh.ARRAY_VERTEX] = vertices
			arrays[Mesh.ARRAY_NORMAL] = normals
			arrays[Mesh.ARRAY_COLOR] = colors
			arrays[Mesh.ARRAY_INDEX] = _indices(nx, nz)
			var flags := 0
			if circuit:
				# Full float precision: arc lengths reach 230 m.
				arrays[Mesh.ARRAY_CUSTOM0] = custom
				flags = Mesh.ARRAY_CUSTOM_RGBA_FLOAT << Mesh.ARRAY_FORMAT_CUSTOM0_SHIFT
			var mesh := ArrayMesh.new()
			mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays, [], {}, flags)
			var chunk := MeshInstance3D.new()
			chunk.name = "Ground_%d_%d" % [cx / CHUNK_CELLS, cz / CHUNK_CELLS]
			chunk.mesh = mesh
			chunk.material_override = material
			chunk.set_instance_shader_parameter("bounds", rect)
			# Ruts and jump relief move vertices on the GPU.
			chunk.extra_cull_margin = soil.MAX_DEPTH + soil.MAX_BANK
			container.add_child(chunk)
			triangle_count += (nx - 1) * (nz - 1) * 2
			chunk_count += 1

## Index buffers are shared between equally sized chunks.
func _indices(nx: int, nz: int) -> PackedInt32Array:
	var key := Vector2i(nx, nz)
	if shared_indices.has(key): return shared_indices[key]
	var indices := PackedInt32Array()
	indices.resize((nx - 1) * (nz - 1) * 6)
	var i := 0
	for z in nz - 1:
		for x in nx - 1:
			var a := z * nx + x
			indices[i] = a
			indices[i + 1] = a + 1
			indices[i + 2] = a + nx
			indices[i + 3] = a + 1
			indices[i + 4] = a + nx + 1
			indices[i + 5] = a + nx
			i += 6
	shared_indices[key] = indices
	return indices

func _build_water() -> void:
	water_material = ShaderMaterial.new()
	water_material.shader = preload("res://desert_battlefield/water.gdshader")
	ripples.configure()
	var rect: Vector4 = ripples.bounds()
	water_material.set_shader_parameter("ripple_map", ripples.texture)
	water_material.set_shader_parameter("ripple_rect", rect)
	water_material.set_shader_parameter("ripple_cells", Vector2(ripples.nx, ripples.nz))
	# One vertex per ripple node, so waves visibly lift and part around a car.
	var plane := PlaneMesh.new()
	plane.size = Vector2(rect.z, rect.w)
	plane.subdivide_width = ripples.nx - 2
	plane.subdivide_depth = ripples.nz - 2
	var water := MeshInstance3D.new()
	water.name = "FordWater"
	water.mesh = plane
	water.material_override = water_material
	water.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	water.position = Vector3(rect.x + rect.z * 0.5, Surface.WATER_LEVEL, rect.y + rect.w * 0.5)
	# Waves stay within ±25 cm; keep the displaced surface inside the cull box.
	water.extra_cull_margin = 0.3
	add_child(water)

func _build_markers() -> void:
	var cone_mesh := CylinderMesh.new()
	cone_mesh.top_radius = 0.03
	cone_mesh.bottom_radius = 0.19
	cone_mesh.height = 0.62
	cone_mesh.radial_segments = 10
	cone_mesh.rings = 1
	var orange := StandardMaterial3D.new()
	orange.albedo_color = Color("e2621b")
	orange.roughness = 0.6
	cone_mesh.material = orange
	var cones := Node3D.new()
	cones.name = "SlalomCones"
	add_child(cones)
	# Visual slalom gates on the asphalt lane; they have no collision.
	for i in 12:
		var x := 106.0 - i * 7.0
		var z: float = Surface.LANE_Z[Surface.LANE_ROAD] + (1.2 if i % 2 == 0 else -1.2)
		var cone := MeshInstance3D.new()
		cone.mesh = cone_mesh
		cone.position = Vector3(x, Surface.height_at(x, z) + 0.31, z)
		cones.add_child(cone)
	for i in 12:
		var angle := TAU * i / 12.0
		var p := Surface.PAD_CENTER + Vector2(cos(angle), sin(angle)) * 8.0
		var cone := MeshInstance3D.new()
		cone.mesh = cone_mesh
		cone.position = Vector3(p.x, Surface.height_at(p.x, p.y) + 0.31, p.y)
		cones.add_child(cone)
	var spots := Surface.test_spots()
	for i in spots.size():
		var spot: Array = spots[i]
		var label := Label3D.new()
		label.name = "Sign%d" % (i + 1)
		label.text = "%d · %s" % [i + 1, spot[0]]
		label.billboard = BaseMaterial3D.BILLBOARD_ENABLED
		label.font_size = 72
		label.outline_size = 18
		label.pixel_size = 0.02
		label.modulate = Color("f3e4c8")
		label.outline_modulate = Color(0.09, 0.08, 0.06, 0.85)
		label.shaded = false
		label.double_sided = true
		var x: float = spot[1]
		var z: float = spot[2]
		label.position = Vector3(x + (2.0 if spot[0] in Surface.LANE_NAMES else 0.0), Surface.height_at(x, z) + 3.0, z)
		label.visible = false
		add_child(label)
		signs.append(label)

## Solid circuit obstacles: drums to weave through on the gravel climb and tyre
## walls on the outside of the four hairpins (static collision, one draw call
## for all tyres).
func _build_circuit_obstacles() -> void:
	var root := Node3D.new()
	root.name = "CircuitObstacles"
	add_child(root)
	var drum_mesh := CylinderMesh.new()
	drum_mesh.top_radius = 0.33
	drum_mesh.bottom_radius = 0.33
	drum_mesh.height = 0.95
	drum_mesh.radial_segments = 14
	drum_mesh.rings = 1
	var drum_paint := StandardMaterial3D.new()
	drum_paint.albedo_color = Color("9a3b22")
	drum_paint.roughness = 0.55
	drum_paint.metallic = 0.4
	drum_mesh.material = drum_paint
	var gravel := Surface.circuit_section_start(Surface.GRAVEL)
	for i in 3:
		var drum := MeshInstance3D.new()
		drum.name = "Drum%d" % i
		drum.mesh = drum_mesh
		root.add_child(drum)
		drum.position = _on_track(gravel + 4.0 + i * 5.0, 2.3 if i % 2 == 0 else -2.3, 0.475)
		_solid_cylinder(root, "DrumCollision%d" % i, drum.position, 0.33, 0.95)
	var tyre_mesh := CylinderMesh.new()
	tyre_mesh.top_radius = 0.38
	tyre_mesh.bottom_radius = 0.38
	tyre_mesh.height = 0.72
	tyre_mesh.radial_segments = 12
	tyre_mesh.rings = 2
	var rubber := StandardMaterial3D.new()
	rubber.albedo_color = Color("1d1b1a")
	rubber.roughness = 0.9
	tyre_mesh.material = rubber
	var stacks: Array[Vector3] = []
	var primitives: Array = Surface.circuit_path[0]
	for index in [5, 7, 9, 11]:
		var arc: PackedFloat32Array = primitives[index]
		var length := arc[3] * absf(arc[5])
		var outside := -signf(arc[5]) * (Surface.CIRCUIT_HALF + 1.5)
		for j in int(length / 1.6) + 1:
			stacks.append(_on_track(arc[6] + j * length / int(length / 1.6), outside, 0.36))
	var multimesh := MultiMesh.new()
	multimesh.transform_format = MultiMesh.TRANSFORM_3D
	multimesh.mesh = tyre_mesh
	multimesh.instance_count = stacks.size()
	for i in stacks.size():
		multimesh.set_instance_transform(i, Transform3D(Basis.IDENTITY, stacks[i]))
		_solid_cylinder(root, "TyreCollision%d" % i, stacks[i], 0.38, 0.72)
	var tyres := MultiMeshInstance3D.new()
	tyres.name = "TyreWalls"
	tyres.multimesh = multimesh
	root.add_child(tyres)
	_build_start_gantry(root)

## Gantry over the start line: posts beside the kerbs and a beam 6 m up, high
## above the chase cameras, with the banner facing the approaching cars.
func _build_start_gantry(root: Node3D) -> void:
	var frame := Surface.circuit_frame(Surface.CIRCUIT_START_LINE)
	var direction: Vector2 = frame[1]
	var gantry := Node3D.new()
	gantry.name = "StartGantry"
	root.add_child(gantry)
	var centre := _on_track(Surface.CIRCUIT_START_LINE, 0.0, 0.0)
	gantry.position = centre
	# Local -Z faces the oncoming traffic, local X spans the track.
	gantry.basis = Basis(Vector3.UP, atan2(direction.x, direction.y))
	var steel := StandardMaterial3D.new()
	steel.albedo_color = Color("6b6a66")
	steel.roughness = 0.5
	steel.metallic = 0.6
	var span := Surface.CIRCUIT_HALF + 1.2
	for side in [-1.0, 1.0]:
		var post := MeshInstance3D.new()
		var post_mesh := BoxMesh.new()
		post_mesh.size = Vector3(0.25, 6.4, 0.25)
		post_mesh.material = steel
		post.mesh = post_mesh
		gantry.add_child(post)
		var foot := _on_track(Surface.CIRCUIT_START_LINE, -side * span, 0.0)
		post.position = Vector3(side * span, foot.y - centre.y + 3.2, 0.0)
		_solid_box(gantry, "GantryPost", post.position, post_mesh.size)
	var beam := MeshInstance3D.new()
	var beam_mesh := BoxMesh.new()
	beam_mesh.size = Vector3(span * 2.0 + 0.25, 0.9, 0.18)
	beam_mesh.material = steel
	beam.mesh = beam_mesh
	beam.position = Vector3(0.0, 6.0, 0.0)
	gantry.add_child(beam)
	var banner := Label3D.new()
	banner.name = "StartBanner"
	banner.text = "ŠTART · CIEĽ"
	banner.font_size = 64
	banner.outline_size = 12
	banner.pixel_size = 0.012
	banner.modulate = Color("f3e4c8")
	banner.outline_modulate = Color(0.09, 0.08, 0.06, 0.85)
	banner.double_sided = true
	banner.position = Vector3(0.0, 6.0, -0.1)
	banner.rotation.y = PI
	gantry.add_child(banner)

func _solid_box(parent: Node3D, title: String, centre: Vector3, size: Vector3) -> void:
	var body := StaticBody3D.new()
	body.name = title
	parent.add_child(body)
	body.position = centre
	var shape := CollisionShape3D.new()
	var box := BoxShape3D.new()
	box.size = size
	shape.shape = box
	body.add_child(shape)

## World point at a distance along the circuit, `across` to the left of the
## centreline and `lift` above the ground.
func _on_track(along: float, across: float, lift: float) -> Vector3:
	var frame := Surface.circuit_frame(along)
	var p: Vector2 = frame[0] + Vector2(-frame[1].y, frame[1].x) * across
	return Vector3(p.x, Surface.height_at(p.x, p.y) + lift, p.y)

func _solid_cylinder(parent: Node3D, title: String, centre: Vector3, radius: float, height: float) -> void:
	var body := StaticBody3D.new()
	body.name = title
	parent.add_child(body)
	body.position = centre
	var shape := CollisionShape3D.new()
	var cylinder := CylinderShape3D.new()
	cylinder.radius = radius
	cylinder.height = height
	shape.shape = cylinder
	body.add_child(shape)

func set_signs_visible(show: bool) -> void:
	if signs.is_empty() or signs[0].visible == show: return
	for sign in signs: sign.visible = show

## Ripple wakes for vehicles wading the ford.
## Advance the interactive ford surface (sleeps while calm and empty).
func update_wakes(vehicles: Array, delta: float = 1.0 / 60.0) -> void:
	ripples.update(delta, vehicles)
