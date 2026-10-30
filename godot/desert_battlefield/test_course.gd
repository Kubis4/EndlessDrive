extends Node3D
## Runtime-built test grounds: surface lanes (asphalt, gravel, clay, mud, water
## ford, moguls with a tabletop jump) and an asphalt skid pad. Geometry follows
## terrain.gd exactly, so wheels and the rendered ground share one height field.
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
	material.set_shader_parameter("water_x", Vector2(Surface.WATER_X0, Surface.WATER_X1))
	for rect: Vector4 in [Surface.COURSE, Surface.PAD, Surface.ROCKS]:
		_build_platform(rect)
	_build_water()
	_build_markers()

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
	for z in side_z:
		var pz := rect.y + (z - 1) * CELL
		for x in side_x:
			var px := rect.x + (x - 1) * CELL
			var border := minf(minf(px - rect.x, rect.z - px), minf(pz - rect.y, rect.w - pz))
			var h := Surface.height_at(px, pz)
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
			vertices.resize(nx * nz)
			normals.resize(nx * nz)
			colors.resize(nx * nz)
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
			var arrays := []
			arrays.resize(Mesh.ARRAY_MAX)
			arrays[Mesh.ARRAY_VERTEX] = vertices
			arrays[Mesh.ARRAY_NORMAL] = normals
			arrays[Mesh.ARRAY_COLOR] = colors
			arrays[Mesh.ARRAY_INDEX] = _indices(nx, nz)
			var mesh := ArrayMesh.new()
			mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
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
	for i in 6:
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
		label.position = Vector3(x + (2.0 if i < Surface.LANE_Z.size() else 0.0), Surface.height_at(x, z) + 3.0, z)
		label.visible = false
		add_child(label)
		signs.append(label)

func set_signs_visible(show: bool) -> void:
	if signs.is_empty() or signs[0].visible == show: return
	for sign in signs: sign.visible = show

## Ripple wakes for vehicles wading the ford.
## Advance the interactive ford surface (sleeps while calm and empty).
func update_wakes(vehicles: Array, delta: float = 1.0 / 60.0) -> void:
	ripples.update(delta, vehicles)
