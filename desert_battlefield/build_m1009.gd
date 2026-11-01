extends SceneTree
## Builds the 1986 Chevrolet M1009 rig (assets/models/m1009) from the Sketchfab
## GLB in assets/source/m1009 (CC BY 4.0, see ATTRIBUTION.md), in the layout the
## Jeep rig uses: +X forward, origin at axle height midway between the axles,
## the four wheels split out of the single wheel mesh onto their own pivots, a
## convex body collision, headlight markers and brake-lamp lenses.
## godot --headless --path . --script res://desert_battlefield/build_m1009.gd
const SOURCE := "res://assets/source/m1009/1986_chevrolet_m1009.glb"
const DIR := "res://assets/models/m1009/"
## The model is about 88 % of the real truck (4.69 m long, 2.69 m wheelbase).
const SCALE := 1.13
## Source +Z forward -> rig +X forward.
const TURN := Basis(Vector3.UP, PI * 0.5)
## Materials of the body shell (the convex collision is built from these).
const HULL_MATERIALS := ["Body", "Metalic_Body", "Windows"]
## Materials of the wiper arms and blades (split out to move).
const WIPER_MATERIALS := ["Metal.004", "Plastic"]

func _initialize() -> void: call_deferred("run")

func run() -> void:
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path(DIR))
	var doc := GLTFDocument.new()
	var state := GLTFState.new()
	var error := doc.append_from_file(ProjectSettings.globalize_path(SOURCE), state)
	if error != OK:
		push_error("M1009 source missing: %s" % SOURCE)
		quit(1)
		return
	var scene := doc.generate_scene(state)
	# Textures go to compressed, imported PNGs (embedded they would be raw).
	if _extract_textures(scene):
		print("M1009 textures written; run an import, then this script again")
		scene.free()
		quit()
		return
	var parts: Array = []
	_collect(scene, Transform3D.IDENTITY, parts)
	# 1. Wheels: one mesh holding all four; split its triangles by quadrant.
	var wheel_part: Array = parts.filter(func(p): return (p[1] as Mesh).surface_get_material(0) != null and (p[1] as Mesh).surface_get_material(0).resource_name == "Wheel")[0]
	var wheel_arrays: Array = (wheel_part[1] as Mesh).surface_get_arrays(0)
	var wheel_material: Material = (wheel_part[1] as Mesh).surface_get_material(0)
	# Into the source's scene space (the mesh node carries a rotation).
	var wheel_xform: Transform3D = wheel_part[0]
	var positions: PackedVector3Array = wheel_arrays[Mesh.ARRAY_VERTEX]
	for i in positions.size(): positions[i] = wheel_xform * positions[i]
	var wheel_normals: PackedVector3Array = wheel_arrays[Mesh.ARRAY_NORMAL]
	for i in wheel_normals.size(): wheel_normals[i] = (wheel_xform.basis * wheel_normals[i]).normalized()
	var indices: PackedInt32Array = wheel_arrays[Mesh.ARRAY_INDEX]
	var bounds := AABB(positions[0], Vector3.ZERO)
	for p in positions: bounds = bounds.expand(p)
	var mid_z := bounds.get_center().z
	var quadrants := {"FL": [], "FR": [], "RL": [], "RR": []}
	for t in range(0, indices.size(), 3):
		var c := (positions[indices[t]] + positions[indices[t + 1]] + positions[indices[t + 2]]) / 3.0
		# Front is +Z in the source; the rig's left (+Z after turning) is source -X.
		var key := ("F" if c.z > mid_z else "R") + ("L" if c.x < 0.0 else "R")
		quadrants[key].append(t)
	var centres := {}
	for key: String in quadrants:
		var box := AABB()
		var first := true
		for t: int in quadrants[key]:
			for k in 3:
				var p := positions[indices[t + k]]
				box = AABB(p, Vector3.ZERO) if first else box.expand(p)
				first = false
		centres[key] = box.get_center()
		print("M1009 wheel %s centre=%s size=%s" % [key, box.get_center(), box.size])
	var origin: Vector3 = (centres.FL + centres.FR + centres.RL + centres.RR) / 4.0
	var to_rig := func(p: Vector3) -> Vector3: return TURN * ((p - origin) * SCALE)
	# 2. Body: every other surface, in rig space, keeping its own material.
	var body := ArrayMesh.new()
	var hull_points := PackedVector3Array()
	var lights: Array = []
	# Wiper parts (arms and blades) and the windscreen's facing.
	var wiper_parts: Array = []
	var screen_normal := Vector3.ZERO
	var screen_points: Array = []
	for part: Array in parts:
		if part == wheel_part: continue
		var mesh: Mesh = part[1]
		var xform: Transform3D = part[0]
		for s in mesh.get_surface_count():
			# (The source also carries a stray line strip; only triangles are body.)
			if mesh.surface_get_primitive_type(s) != Mesh.PRIMITIVE_TRIANGLES: continue
			var arrays: Array = mesh.surface_get_arrays(s)
			var vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
			if arrays[Mesh.ARRAY_INDEX] != null and (arrays[Mesh.ARRAY_INDEX] as PackedInt32Array).is_empty(): continue
			if vertices.size() < 3: continue
			var material := mesh.surface_get_material(s)
			for i in vertices.size():
				var p: Vector3 = xform * vertices[i]
				vertices[i] = to_rig.call(p)
				# Collision from the body shell only: axles, diffs and mirrors in a
				# convex hull would make one flat slab down to the ground.
				if i % 7 == 0 and material != null and material.resource_name in HULL_MATERIALS: hull_points.append(vertices[i])
				if material != null and material.resource_name.begins_with("Lights"): lights.append(vertices[i])
			arrays[Mesh.ARRAY_VERTEX] = vertices
			if arrays[Mesh.ARRAY_NORMAL] != null:
				var n: PackedVector3Array = arrays[Mesh.ARRAY_NORMAL]
				for i in n.size(): n[i] = (xform.basis * n[i]).normalized()
				arrays[Mesh.ARRAY_NORMAL] = n
			_turn_vectors(arrays, Mesh.ARRAY_NORMAL)
			_turn_tangents(arrays)
			arrays = _strip(arrays)
			if material != null and material.resource_name in WIPER_MATERIALS:
				wiper_parts.append([arrays, material])
				continue
			if material != null and material.resource_name == "Windows":
				var normals: PackedVector3Array = arrays[Mesh.ARRAY_NORMAL]
				var glass: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
				for i in glass.size():
					if glass[i].x > 0.2 and normals[i].x > 0.3:
						screen_normal += normals[i]
						screen_points.append([glass[i], normals[i]])
			if material != null and material.resource_name == "Undercarriage":
				# The solid axles (tube, differential, hubs) ride with the wheels:
				# their triangles go to their own meshes, centred on each axle.
				var groups := _split_axles(arrays, [to_rig.call(centres.FL).x, to_rig.call(centres.RL).x])
				for a in 2:
					var axle := ArrayMesh.new()
					axle.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, groups[a])
					axle.surface_set_material(0, material)
					ResourceSaver.save(axle, DIR + "m1009_axle_%s.res" % ["front", "rear"][a])
				for a in 2:
					var suspension := ArrayMesh.new()
					suspension.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, groups[3 + a])
					suspension.surface_set_material(0, material)
					ResourceSaver.save(suspension, DIR + "m1009_suspension_%s.res" % ["front", "rear"][a])
				arrays = groups[2]
			body.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
			body.surface_set_material(body.get_surface_count() - 1, material)
	ResourceSaver.save(body, DIR + "m1009_body.res")
	# 3. Wheel meshes, each about its own centre.
	for key: String in quadrants:
		var verts := PackedVector3Array()
		var normals := PackedVector3Array()
		var uvs := PackedVector2Array()
		var src_normals := wheel_normals
		var src_uvs = wheel_arrays[Mesh.ARRAY_TEX_UV]
		for t: int in quadrants[key]:
			for k in 3:
				var i := indices[t + k]
				verts.append(TURN * ((positions[i] - centres[key]) * SCALE))
				normals.append(TURN * src_normals[i])
				if src_uvs != null: uvs.append(src_uvs[i])
		var arrays := []
		arrays.resize(Mesh.ARRAY_MAX)
		arrays[Mesh.ARRAY_VERTEX] = verts
		arrays[Mesh.ARRAY_NORMAL] = normals
		if not uvs.is_empty(): arrays[Mesh.ARRAY_TEX_UV] = uvs
		var wheel := ArrayMesh.new()
		wheel.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
		wheel.surface_set_material(0, wheel_material)
		ResourceSaver.save(wheel, DIR + "m1009_wheel_%s.res" % key.to_lower())
	# 4. Convex body collision (rig space, as the catalog's shapes are).
	var shape := ConvexPolygonShape3D.new()
	shape.points = _hull(hull_points)
	ResourceSaver.save(shape, DIR + "m1009_chassis.res")
	# 5. Lamps: headlight faces at the front of the light mesh, tail lamps at the back.
	var front_x := -INF
	var back_x := INF
	for p: Vector3 in lights:
		front_x = maxf(front_x, p.x)
		back_x = minf(back_x, p.x)
	var head := {1.0: [], -1.0: []}
	var tail := {1.0: [], -1.0: []}
	for p: Vector3 in lights:
		if p.x > front_x - 0.06 and absf(p.z) > 0.3: head[signf(p.z)].append(p)
		if p.x < back_x + 0.06 and absf(p.z) > 0.3: tail[signf(p.z)].append(p)
	var rig_wheels := {}
	for key: String in centres: rig_wheels[key] = to_rig.call(centres[key])
	var radius: float = (bounds.size.y * 0.5) * SCALE
	var wheelbase: float = (rig_wheels.FL.x - rig_wheels.RL.x)
	var track: float = (rig_wheels.FL.z - rig_wheels.FR.z)
	print("M1009 wheelbase=%.3f track=%.3f radius=%.3f body=%s" % [wheelbase, track, radius, body.get_aabb()])
	var wipers := _build_wipers(wiper_parts, screen_normal.normalized(), screen_points)
	_save_scene(wipers, rig_wheels, _centre(head[1.0]), _centre(head[-1.0]), _centre(tail[1.0]), _centre(tail[-1.0]))
	scene.free()
	quit()

func _collect(node: Node, parent: Transform3D, out: Array) -> void:
	var xform := parent
	if node is Node3D: xform = parent * (node as Node3D).transform
	if node is MeshInstance3D and (node as MeshInstance3D).mesh != null: out.append([xform, (node as MeshInstance3D).mesh])
	for child in node.get_children(): _collect(child, xform, out)

func _turn_vectors(arrays: Array, slot: int) -> void:
	if arrays[slot] == null: return
	var vectors: PackedVector3Array = arrays[slot]
	for i in vectors.size(): vectors[i] = TURN * vectors[i]
	arrays[slot] = vectors

func _turn_tangents(arrays: Array) -> void:
	if arrays[Mesh.ARRAY_TANGENT] == null: return
	var tangents: PackedFloat32Array = arrays[Mesh.ARRAY_TANGENT]
	for i in range(0, tangents.size(), 4):
		var t := TURN * Vector3(tangents[i], tangents[i + 1], tangents[i + 2])
		tangents[i] = t.x
		tangents[i + 1] = t.y
		tangents[i + 2] = t.z
	arrays[Mesh.ARRAY_TANGENT] = tangents

## Drops skinning arrays the static mesh does not need.
func _strip(arrays: Array) -> Array:
	for slot in [Mesh.ARRAY_BONES, Mesh.ARRAY_WEIGHTS, Mesh.ARRAY_CUSTOM0, Mesh.ARRAY_CUSTOM1, Mesh.ARRAY_CUSTOM2, Mesh.ARRAY_CUSTOM3]: arrays[slot] = null
	return arrays

## Convex hull of a point cloud, reduced to at most ~64 points.
func _hull(points: PackedVector3Array) -> PackedVector3Array:
	var mesh := ArrayMesh.new()
	var arrays := []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = points
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_POINTS, arrays)
	var shape := mesh.create_convex_shape(true, false)
	return (shape as ConvexPolygonShape3D).points if shape != null else points

func _centre(points: Array) -> Vector3:
	if points.is_empty(): return Vector3.ZERO
	var box := AABB(points[0], Vector3.ZERO)
	for p: Vector3 in points: box = box.expand(p)
	return box.get_center()

func _save_scene(wipers: Array, wheels: Dictionary, head_l: Vector3, head_r: Vector3, tail_l: Vector3, tail_r: Vector3) -> void:
	var root := Node3D.new()
	root.name = "ChevroletM1009"
	root.set_meta("model_author", "snafuj (https://sketchfab.com/snafuj)")
	root.set_meta("model_license", "CC-BY-4.0")
	root.set_meta("model_source", "https://sketchfab.com/3d-models/1986-chevrolet-m1009-d2a462c09f054bb093e360a263478732")
	var body := MeshInstance3D.new()
	body.name = "Body"
	body.mesh = load(DIR + "m1009_body.res")
	root.add_child(body)
	body.owner = root
	for key: String in wheels:
		var hub := Node3D.new()
		hub.name = "Wheel_" + key
		hub.position = wheels[key]
		root.add_child(hub)
		hub.owner = root
		var tyre := MeshInstance3D.new()
		tyre.name = "TyreAndRim"
		tyre.mesh = load(DIR + "m1009_wheel_%s.res" % key.to_lower())
		hub.add_child(tyre)
		tyre.owner = root
	# Springs and shocks stay on the body but bend towards their axle.
	for side: Array in [["Suspension_Front", "front", wheels.FL.x], ["Suspension_Rear", "rear", wheels.RL.x]]:
		var springs := MeshInstance3D.new()
		springs.name = side[0]
		springs.mesh = load(DIR + "m1009_suspension_%s.res" % side[1])
		springs.set_meta("axle_x", side[2])
		root.add_child(springs)
		springs.owner = root
	# Wipers: each turns about the windscreen normal at its pivot (vehicle.gd).
	for w in wipers.size():
		var wiper: Dictionary = wipers[w]
		var pivot := Node3D.new()
		pivot.name = "Wiper_%d" % (w + 1)
		pivot.position = wiper.pivot
		for key in ["axis", "rest", "reach", "sweep_sign"]: pivot.set_meta(key, wiper[key])
		root.add_child(pivot)
		pivot.owner = root
		var blade := MeshInstance3D.new()
		blade.name = "Blade"
		blade.mesh = load(DIR + "m1009_wiper_%d.res" % (w + 1))
		pivot.add_child(blade)
		blade.owner = root
	# Solid axles: each pivots between its two wheels (vehicle.gd moves it).
	for axle: Array in [["Axle_Front", wheels.FL, "front"], ["Axle_Rear", wheels.RL, "rear"]]:
		var pivot := Node3D.new()
		pivot.name = axle[0]
		pivot.position = Vector3(axle[1].x, 0.0, 0.0)
		root.add_child(pivot)
		pivot.owner = root
		var housing := MeshInstance3D.new()
		housing.name = "Housing"
		housing.mesh = load(DIR + "m1009_axle_%s.res" % axle[2])
		pivot.add_child(housing)
		housing.owner = root
	var lens := StandardMaterial3D.new()
	lens.albedo_color = Color(0.32, 0.025, 0.012)
	lens.roughness = 0.65
	lens.emission_enabled = true
	lens.emission = Color(0.8, 0.025, 0.008)
	lens.emission_energy_multiplier = 0.0
	var panel := QuadMesh.new()
	panel.size = Vector2(0.14, 0.16)
	for side: Array in [["BrakeLampL", tail_l], ["BrakeLampR", tail_r]]:
		var lamp := MeshInstance3D.new()
		lamp.name = side[0]
		lamp.mesh = panel
		lamp.material_override = lens
		# Just behind the lens, facing back (-X).
		lamp.position = side[1] - Vector3(0.012, 0, 0)
		lamp.rotation.y = -PI * 0.5
		root.add_child(lamp)
		lamp.owner = root
	for side: Array in [["HeadlightL", head_l], ["HeadlightR", head_r]]:
		var marker := Marker3D.new()
		marker.name = side[0]
		marker.position = side[1]
		root.add_child(marker)
		marker.owner = root
	var packed := PackedScene.new()
	packed.pack(root)
	print("M1009 scene=%s head=%s,%s tail=%s,%s" % [error_string(ResourceSaver.save(packed, DIR + "m1009.tscn")), head_l, head_r, tail_l, tail_r])
	root.free()

## Saves every material texture as a PNG beside the rig (with import settings)
## and points the materials at the imported copies. Returns true when new files
## were written (they need an import before the meshes can reference them).
func _extract_textures(scene: Node) -> bool:
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path(DIR + "textures"))
	var written := false
	var parts: Array = []
	_collect(scene, Transform3D.IDENTITY, parts)
	var slots := {"albedo_texture": BaseMaterial3D.TEXTURE_ALBEDO, "normal_texture": BaseMaterial3D.TEXTURE_NORMAL,
		"roughness_texture": BaseMaterial3D.TEXTURE_ROUGHNESS, "metallic_texture": BaseMaterial3D.TEXTURE_METALLIC,
		"emission_texture": BaseMaterial3D.TEXTURE_EMISSION, "ao_texture": BaseMaterial3D.TEXTURE_AMBIENT_OCCLUSION}
	for part: Array in parts:
		var mesh: Mesh = part[1]
		for s in mesh.get_surface_count():
			var material := mesh.surface_get_material(s) as BaseMaterial3D
			if material == null: continue
			for property: String in slots:
				var texture := material.get(property) as Texture2D
				if texture == null or texture.resource_path.begins_with(DIR): continue
				var file := DIR + "textures/%s_%s.png" % [material.resource_name.validate_filename(), property.trim_suffix("_texture")]
				if not ResourceLoader.exists(file):
					var image := texture.get_image()
					if image.is_compressed(): image.decompress()
					image.save_png(ProjectSettings.globalize_path(file))
					var settings := FileAccess.open(ProjectSettings.globalize_path(file + ".import"), FileAccess.WRITE)
					settings.store_string("[remap]\n\nimporter=\"texture\"\ntype=\"CompressedTexture2D\"\n\n[params]\n\ncompress/mode=2\ncompress/normal_map=%d\nmipmaps/generate=true\n" % (1 if property == "normal_texture" else 2))
					settings.close()
					written = true
				else:
					material.set(property, load(file))
	return written

## Splits an undercarriage surface into [front axle, rear axle, the rest] by
## whole parts: triangles joined through shared corners form islands (a leaf
## spring, the axle tube, a shock), and an island goes with an axle only when it
## lies low around that axle line and is short along the car (the frame and the
## springs, which run along it, stay on the body). Unindexed results; axle
## vertices relative to the axle centre.
func _split_axles(arrays: Array, axle_xs: Array) -> Array:
	var vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
	var indices: PackedInt32Array = arrays[Mesh.ARRAY_INDEX] if arrays[Mesh.ARRAY_INDEX] != null else PackedInt32Array(range(vertices.size()))
	# Union-find over vertices welded by position (seams split normals/UVs).
	var parent := PackedInt32Array()
	parent.resize(vertices.size())
	var welded := {}
	for i in vertices.size():
		var key := Vector3i((vertices[i] * 500.0).round())
		if welded.has(key): parent[i] = welded[key]
		else:
			welded[key] = i
			parent[i] = i
	for t in range(0, indices.size(), 3):
		_union(parent, indices[t], indices[t + 1])
		_union(parent, indices[t], indices[t + 2])
	var islands := {}
	for t in range(0, indices.size(), 3):
		var root := _find(parent, indices[t])
		if not islands.has(root): islands[root] = [AABB(vertices[indices[t]], Vector3.ZERO), []]
		var island: Array = islands[root]
		for k in 3: island[0] = (island[0] as AABB).expand(vertices[indices[t + k]])
		island[1].append(t)
	# Groups: 0 front axle, 1 rear axle, 2 body, 3 front suspension, 4 rear.
	var picks: Array = [[], [], [], [], []]
	for root in islands:
		var box: AABB = islands[root][0]
		var centre := box.get_center()
		var group := 2
		for a in 2:
			var near := absf(centre.x - axle_xs[a]) < 0.4 and box.size.x < 0.8 and centre.y < 0.25
			# The axle: the tube spanning the track, or parts at a hub. A plate
			# or pan in the middle (the engine guard) stays on the body.
			if near and (box.size.z > 1.2 or absf(centre.z) > 0.6): group = a
			# Suspension: springs, shackles and shocks beside the frame rails
			# around the axle (they bend with it, see tyre_grime.gdshader).
			elif group == 2 and absf(centre.x - axle_xs[a]) < 0.95 and box.size.x < 1.9 and absf(centre.z) > 0.35 and absf(centre.z) < 0.95 and centre.y < 0.6 and box.size.y < 0.7 and box.size.z < 0.4: group = 3 + a
		picks[group].append_array(islands[root][1])
	print("M1009 islands: axles %d/%d, suspension %d/%d, body %d triangles (of %d islands)" % [picks[0].size() / 3, picks[1].size() / 3, picks[3].size() / 3, picks[4].size() / 3, picks[2].size() / 3, islands.size()])
	var result: Array = []
	for g in 5:
		var offset := Vector3(axle_xs[g], 0.0, 0.0) if g < 2 else Vector3.ZERO
		result.append(_gather(arrays, indices, picks[g], offset))
	return result

func _find(parent: PackedInt32Array, i: int) -> int:
	while parent[i] != i:
		parent[i] = parent[parent[i]]
		i = parent[i]
	return i

func _union(parent: PackedInt32Array, a: int, b: int) -> void:
	var ra := _find(parent, a)
	var rb := _find(parent, b)
	if ra != rb: parent[ra] = rb

## Unindexed copy of the picked triangles (all vertex attributes kept).
func _gather(arrays: Array, indices: PackedInt32Array, triangles: Array, offset: Vector3) -> Array:
	var out := []
	out.resize(Mesh.ARRAY_MAX)
	var source_vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
	var vertices := PackedVector3Array()
	var normals := PackedVector3Array()
	var tangents := PackedFloat32Array()
	var uvs := PackedVector2Array()
	var uv2s := PackedVector2Array()
	var colors := PackedColorArray()
	for t: int in triangles:
		for k in 3:
			var i := indices[t + k]
			vertices.append(source_vertices[i] - offset)
			if arrays[Mesh.ARRAY_NORMAL] != null: normals.append(arrays[Mesh.ARRAY_NORMAL][i])
			if arrays[Mesh.ARRAY_TANGENT] != null:
				for j in 4: tangents.append(arrays[Mesh.ARRAY_TANGENT][i * 4 + j])
			if arrays[Mesh.ARRAY_TEX_UV] != null: uvs.append(arrays[Mesh.ARRAY_TEX_UV][i])
			if arrays[Mesh.ARRAY_TEX_UV2] != null: uv2s.append(arrays[Mesh.ARRAY_TEX_UV2][i])
			if arrays[Mesh.ARRAY_COLOR] != null: colors.append(arrays[Mesh.ARRAY_COLOR][i])
	out[Mesh.ARRAY_VERTEX] = vertices
	if not normals.is_empty(): out[Mesh.ARRAY_NORMAL] = normals
	if not tangents.is_empty(): out[Mesh.ARRAY_TANGENT] = tangents
	if not uvs.is_empty(): out[Mesh.ARRAY_TEX_UV] = uvs
	if not uv2s.is_empty(): out[Mesh.ARRAY_TEX_UV2] = uv2s
	if not colors.is_empty(): out[Mesh.ARRAY_COLOR] = colors
	return out

## The two wipers: arm and blade triangles grouped by side, each saved as a
## mesh about its pivot. The pivot is the arm's right-hand end (rig -Z, where
## the metal arm is mounted); the blade rests towards +Z and sweeps up the
## screen about its normal. Returns [{pivot, axis, rest, reach, sweep_sign}].
func _build_wipers(parts: Array, screen_axis: Vector3, screen_points: Array) -> Array:
	var result: Array = []
	# Sides: split at the middle of the blades (the plastic parts).
	var low := INF
	var high := -INF
	for part: Array in parts:
		if part[1].resource_name != "Plastic": continue
		for p: Vector3 in part[0][Mesh.ARRAY_VERTEX]:
			low = minf(low, p.z)
			high = maxf(high, p.z)
	var split := (low + high) * 0.5
	for side in 2:
		var mesh := ArrayMesh.new()
		var pieces: Array = []
		var blade: Array = []
		var arm: Array = []
		for part: Array in parts:
			var arrays: Array = part[0]
			var vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
			var indices: PackedInt32Array = arrays[Mesh.ARRAY_INDEX] if arrays[Mesh.ARRAY_INDEX] != null else PackedInt32Array(range(vertices.size()))
			# Whole parts (welded islands) go to the wiper whose blade half they sit
			# nearest, so no blade or arm is cut where the two meet mid-screen.
			var picked: Array = []
			for island: Array in _islands(vertices, indices):
				var zc := 0.0
				for t: int in island: zc += (vertices[indices[t]].z + vertices[indices[t + 1]].z + vertices[indices[t + 2]].z) / 3.0
				zc /= island.size()
				var near_b := absf(zc - (split + high) * 0.5) < absf(zc - (low + split) * 0.5)
				if near_b == (side == 1): picked.append_array(island)
			for t: int in picked:
				for k in 3:
					(arm if part[1].resource_name == "Metal.004" else blade).append(vertices[indices[t + k]])
			pieces.append([arrays, indices, picked, part[1]])
		# The arm turns about its cap on the bonnet, below the windscreen: the
		# wiper's lowest point measured up the glass, and the blade tip the point
		# farthest from it.
		var blade_centre := Vector3.ZERO
		for p: Vector3 in blade: blade_centre += p
		blade_centre /= maxi(blade.size(), 1)
		var axis := screen_axis
		var up_glass := (Vector3.UP - axis * axis.dot(Vector3.UP)).normalized()
		# (All parts: the pivot cap on the bonnet is its own small piece.)
		var parts_all := arm + blade
		var lowest := INF
		for p: Vector3 in parts_all: lowest = minf(lowest, p.dot(up_glass))
		var pivot := Vector3.ZERO
		var count := 0
		for p: Vector3 in parts_all:
			if p.dot(up_glass) < lowest + 0.03:
				pivot += p
				count += 1
		pivot /= maxi(count, 1)
		var tip_point := pivot
		for p: Vector3 in blade + arm:
			if p.distance_to(pivot) > tip_point.distance_to(pivot): tip_point = p
		# Each wiper turns about the normal of the glass it actually sweeps (the
		# pane is not one flat plane), and is lifted to clear it everywhere.
		var reach_r := tip_point.distance_to(pivot)
		var local_normal := Vector3.ZERO
		for entry: Array in screen_points:
			if (entry[0] as Vector3).distance_to(pivot) < reach_r + 0.05: local_normal += entry[1]
		var candidates := [screen_axis]
		if local_normal.length() > 0.0: candidates.append(local_normal.normalized())
		var lift := INF
		for candidate: Vector3 in candidates:
			var needed := _wiper_lift(candidate, pivot, tip_point, blade, screen_points)
			if needed < lift - 0.002:
				lift = needed
				axis = candidate
		# (The mesh keeps its shape about the cap; the whole wiper is raised.)
		var mount := pivot
		pivot += axis * (lift + 0.006)
		var rest := tip_point - pivot
		rest = (rest - axis * rest.dot(axis)).normalized()
		# Sweep the way that lifts the blade up the screen.
		var sign := 1.0 if (Basis(axis, 0.5) * rest).y > rest.y else -1.0
		# Shortened along the arm until the whole sweep stays on the glass.
		var shorten := _wiper_fit(axis, sign, rest, mount, pivot, arm + blade, screen_points)
		print("M1009 wiper %d axis=%s lift=%.3f length x%.2f" % [side + 1, axis, lift, shorten])
		for piece: Array in pieces:
			if piece[2].is_empty(): continue
			var arrays := _gather(piece[0], piece[1], piece[2], mount)
			var vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
			for i in vertices.size(): vertices[i] += rest * vertices[i].dot(rest) * (shorten - 1.0)
			arrays[Mesh.ARRAY_VERTEX] = vertices
			mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
			mesh.surface_set_material(mesh.get_surface_count() - 1, piece[3])
		ResourceSaver.save(mesh, DIR + "m1009_wiper_%d.res" % (side + 1))
		var tip := tip_point - mount
		tip += rest * tip.dot(rest) * (shorten - 1.0)
		result.append({"pivot": pivot, "axis": axis, "rest": rest, "reach": tip.length(), "sweep_sign": sign})
		print("M1009 wiper %d pivot=%s blade=%s reach=%.2f sign=%d" % [side + 1, pivot, blade_centre, tip.length(), sign])
	return result

## Triangle start indices grouped into welded islands (connected parts).
func _islands(vertices: PackedVector3Array, indices: PackedInt32Array) -> Array:
	var parent := PackedInt32Array()
	parent.resize(vertices.size())
	var welded := {}
	for i in vertices.size():
		var key := Vector3i((vertices[i] * 500.0).round())
		if welded.has(key): parent[i] = welded[key]
		else:
			welded[key] = i
			parent[i] = i
	for t in range(0, indices.size(), 3):
		_union(parent, indices[t], indices[t + 1])
		_union(parent, indices[t], indices[t + 2])
	var groups := {}
	for t in range(0, indices.size(), 3):
		var root := _find(parent, indices[t])
		if not groups.has(root): groups[root] = []
		groups[root].append(t)
	return groups.values()

## The longest share (0.6..1) of a wiper's length that keeps it inside the
## windscreen's top and side edges through its whole 90° sweep.
func _wiper_fit(axis: Vector3, sign: float, rest: Vector3, mount: Vector3, pivot: Vector3, points: Array, screen_points: Array) -> float:
	var up_glass := (Vector3.UP - axis * axis.dot(Vector3.UP)).normalized()
	var top := -INF
	var edge := 0.0
	for entry: Array in screen_points:
		top = maxf(top, (entry[0] as Vector3).dot(up_glass))
		edge = maxf(edge, absf((entry[0] as Vector3).z))
	var shorten := 1.0
	while shorten > 0.6:
		var inside := true
		for deg in range(0, 91, 10):
			var turn := Basis(axis, deg_to_rad(deg) * sign)
			for k in range(0, points.size(), 3):
				var q: Vector3 = points[k] - mount
				q += rest * q.dot(rest) * (shorten - 1.0)
				var p := pivot + turn * q
				if p.dot(up_glass) > top - 0.03 or absf(p.z) > edge - 0.03:
					inside = false
					break
			if not inside: break
		if inside: break
		shorten -= 0.02
	return shorten

## How far a wiper turning about `axis` must be lifted to stay above the glass
## through its whole 90° sweep.
func _wiper_lift(axis: Vector3, pivot: Vector3, tip_point: Vector3, blade: Array, screen_points: Array) -> float:
	var lift := 0.0
	var sign := 1.0 if (Basis(axis, 0.5) * (tip_point - pivot)).y > (tip_point - pivot).y else -1.0
	for deg in range(0, 91, 10):
		var turn := Basis(axis, deg_to_rad(deg) * sign)
		for k in range(0, blade.size(), 5):
			var p: Vector3 = pivot + turn * (blade[k] - pivot)
			var nearest := INF
			var gap := 0.0
			for entry: Array in screen_points:
				var g: Vector3 = entry[0]
				var d := (p - g) - axis * (p - g).dot(axis)
				if d.length() < nearest:
					nearest = d.length()
					gap = (p - g).dot(axis)
			if nearest < 0.1: lift = maxf(lift, -gap)
	return lift
