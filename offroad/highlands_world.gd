extends Node3D
## Runtime dressing of Lesná vrchovina on top of the baked terrain: ground
## textures and map for the shader, the river (with interactive ripples at the
## main ford), the forest, feature trees, undergrowth, ground cover, boulders,
## the camp and the test-spot signs. Placement reads the same ground map the
## physics uses, so nothing grows on a trail or in water. Models come from the
## Sketchfab packs via build_props.gd (see props.gd).
## The world script it dresses (highlands.gd, or a generated world that
## answers the same queries: see generated_world.gd).
var H
const Props = preload("res://offroad/props.gd")
const Surface = preload("res://desert_battlefield/terrain.gd")
const EndlessModels = preload("res://desert_battlefield/endless_models.gd")
## Vegetation is batched per 90 m cell: few draw calls, still culled in pieces.
const CELL := 90.0
## Wake simulation square (x0, z0, size x, size z), first over the main ford;
## it then follows the lead car (see _follow_wakes).
const RIPPLE_AREA := Vector4(-1.0, -33.0, 32.0, 32.0)
## Forest trees used (the kit's three species, a full-size and a lighter variant each).
const Settings = preload("res://offroad/game_settings.gd")
const FOREST_TREES := ["tree_01_1", "tree_01_3", "tree_02_2", "tree_02_3", "tree_03_1", "tree_03_3"]
## The forest's models and a colour over all foliage (endless_world.gd changes
## them with the landscape).
var forest_models: Array = FOREST_TREES
var foliage_tint := Color.WHITE
## Ground cover: grasses, low plants, tall flowers.
var cover_grass: Array = []
var cover_low: Array = []
var cover_tall: Array = []
var ripples := preload("res://desert_battlefield/water_ripples.gd").new()
var signs: Array[Label3D] = []
var rng := RandomNumberGenerator.new()
var mobile := Settings.is_mobile()
## Solid trunks and boulders within the drivable area.
var collision: StaticBody3D
var meshes := {}
var river_material: ShaderMaterial
var water_near := false
var water_near_clock := 0.0
var wakes_shown := false
var hull_shown := 0.0
var pond_material: ShaderMaterial
## Water surfaces and how much of the flood each rises by (river 1, ponds 0.5).
var water_nodes: Array[MeshInstance3D] = []
var ground_materials: Array[ShaderMaterial] = []
var weather := preload("res://offroad/weather.gd").new()

func build(root: Node3D) -> void:
	name = "Highlands"
	rng.seed = H.seed_value
	collision = StaticBody3D.new()
	collision.name = "PropCollision"
	# (Added to the world once all its shapes are in: each shape added to a body
	# already in the world rebuilds it, which made thousands of trunks quadratic.)
	var started := Time.get_ticks_usec()
	lap_clock = started
	_ground_material(root)
	_lap("ground textures")
	_build_ground(root)
	_lap("ground")
	_build_bounds()
	_build_river()
	_lap("water")
	_build_forest()
	_lap("forest")
	_build_feature_trees()
	_lap("feature trees")
	_build_undergrowth()
	_lap("undergrowth")
	_build_ground_cover()
	_lap("ground cover")
	_build_boulders()
	_lap("boulders")
	add_child(collision)
	_lap("collision")
	_build_camp()
	_build_signs()
	# Hand-placed extras from the layout scene (offroad/highlands_layout.tscn).
	add_child(weather)
	weather.setup(root, [(root.get_node(root.ground_node) as MeshInstance3D).material_override, root.soil.fine_material], river_material)
	weather._collect_overlays()
	_lap("weather")
	print("HIGHLANDS load %d ms: %s" % [(Time.get_ticks_usec() - started) / 1000, ", ".join(laps)])

func _mesh(name: String) -> Mesh:
	if not meshes.has(name): meshes[name] = load(Props.mesh_path(name))
	return meshes[name]

## Ground map plus the generated tileable textures, as three texture arrays
## (albedo with roughness in alpha, normal maps, heights) in Props.GROUND_LAYERS order.
func _ground_material(root: Node3D) -> void:
	var ground_map := ImageTexture.create_from_image(H.ground_image() as Image)
	var albedo := Texture2DArray.new()
	var normal := Texture2DArray.new()
	var albedo_images: Array[Image] = []
	var normal_images: Array[Image] = []
	var height := Texture2DArray.new()
	var height_images: Array[Image] = []
	for layer: String in Props.GROUND_LAYERS:
		albedo_images.append((load(Props.GROUND + layer + "_albedo.png") as Texture2D).get_image())
		normal_images.append((load(Props.GROUND + layer + "_normal.png") as Texture2D).get_image())
		height_images.append((load(Props.GROUND + layer + "_height.png") as Texture2D).get_image())
	albedo.create_from_images(albedo_images)
	normal.create_from_images(normal_images)
	height.create_from_images(height_images)
	for material: ShaderMaterial in [(root.get_node(root.ground_node) as MeshInstance3D).material_override, root.soil.fine_material]:
		ground_materials.append(material)
		material.set_shader_parameter("ground_map", ground_map)
		material.set_shader_parameter("map_half", H.HALF)
		material.set_shader_parameter("map_step", H.STEP)
		material.set_shader_parameter("water_level", H.WATER_LEVEL)
		material.set_shader_parameter("ground_albedo", albedo)
		material.set_shader_parameter("ground_normal", normal)
		material.set_shader_parameter("ground_height", height)
		# Asphalt (ROAD ground: the generated expedition's first stretch).
		material.set_shader_parameter("asphalt_albedo", load("res://assets/textures/weathered-asphalt.png"))

## Ground chunks (60 m): every chunk shares one flat grid mesh (0.75 m, or
## 1.5 m beyond near_range) that the ground shader lifts to the baked height
## grid, so loading builds no terrain geometry at all. Skirts (3 m down) hide
## the seams where a coarse chunk meets a full-detail one.
const CHUNK := 80
const SPACING := 0.75
const FAR_STEP := 2
var grid := PackedFloat32Array()
var grid_side := 0

func _build_ground(root: Node3D) -> void:
	var base := root.get_node(root.ground_node) as MeshInstance3D
	var image := H.mesh_height_image() as Image
	grid_side = image.get_width()
	grid = image.get_data().to_float32_array()
	var heights := ImageTexture.create_from_image(image)
	for material in ground_materials:
		material.set_shader_parameter("ground_heights", heights)
		material.set_shader_parameter("heights_half", H.HALF)
		material.set_shader_parameter("heights_step", SPACING)
	var meshes_by_step := {1: _grid_mesh(CHUNK, 1), FAR_STEP: _grid_mesh(CHUNK / FAR_STEP, FAR_STEP)}
	var count := (grid_side - 1) / CHUNK
	var near_range := 150.0 if mobile else 200.0
	for tz in count:
		for tx in count:
			# Height range of the chunk (every 4th node) for its culling box.
			var low := INF
			var high := -INF
			for z in range(0, CHUNK + 1, 4):
				for x in range(0, CHUNK + 1, 4):
					var h := _height(tx * CHUNK + x, tz * CHUNK + z)
					low = minf(low, h)
					high = maxf(high, h)
			for step: int in meshes_by_step:
				var chunk := MeshInstance3D.new()
				chunk.name = "Ground_%d_%d%s" % [tx, tz, "" if step == 1 else "_far"]
				chunk.mesh = meshes_by_step[step]
				chunk.position = Vector3(-H.HALF + tx * CHUNK * SPACING, 0.0, -H.HALF + tz * CHUNK * SPACING)
				chunk.custom_aabb = AABB(Vector3(0.0, low - 3.5, 0.0), Vector3(CHUNK * SPACING, high - low + 4.0, CHUNK * SPACING))
				chunk.material_override = base.material_override
				chunk.set_instance_shader_parameter("grid_step", SPACING * step)
				chunk.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
				# A hard switch at one distance (no hysteresis, which could leave a gap).
				if step == 1: chunk.visibility_range_end = near_range
				else: chunk.visibility_range_begin = near_range
				base.add_child(chunk)
	print("HIGHLANDS ground chunks=%d" % (count * count))

## A flat chunk grid (x, z from 0) with its skirt ring at y = -3.
func _grid_mesh(quads: int, step: int) -> ArrayMesh:
	var side := quads + 1
	var vertices := PackedVector3Array()
	vertices.resize(side * side)
	for z in side:
		for x in side:
			vertices[z * side + x] = Vector3(x * step * SPACING, 0.0, z * step * SPACING)
	for k in _border(side): vertices.append(vertices[k] - Vector3(0, 3.0, 0))
	var normals := PackedVector3Array()
	normals.resize(vertices.size())
	normals.fill(Vector3.UP)
	var arrays := []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = vertices
	arrays[Mesh.ARRAY_NORMAL] = normals
	arrays[Mesh.ARRAY_INDEX] = _grid_indices(quads) + _skirt_indices(side)
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
	return mesh

static func _grid_indices(quads: int) -> PackedInt32Array:
	var side := quads + 1
	var indices := PackedInt32Array()
	indices.resize(quads * quads * 6)
	var i := 0
	for z in quads:
		for x in quads:
			var a := z * side + x
			indices[i] = a
			indices[i + 1] = a + 1
			indices[i + 2] = a + side
			indices[i + 3] = a + 1
			indices[i + 4] = a + side + 1
			indices[i + 5] = a + side
			i += 6
	return indices

func _height(gx: int, gz: int) -> float:
	return grid[clampi(gz, 0, grid_side - 1) * grid_side + clampi(gx, 0, grid_side - 1)]

## Grid indices around the edge of a side × side grid, in order.
static func _border(side: int) -> PackedInt32Array:
	var ring := PackedInt32Array()
	for x in side - 1: ring.append(x)
	for z in side - 1: ring.append(z * side + side - 1)
	for x in range(side - 1, 0, -1): ring.append((side - 1) * side + x)
	for z in range(side - 1, 0, -1): ring.append(z * side)
	return ring

## Skirt quads (both windings) between the border ring and its lowered copy,
## which follows the side × side grid in the vertex array.
static func _skirt_indices(side: int) -> PackedInt32Array:
	var ring := _border(side)
	var first := side * side
	var indices := PackedInt32Array()
	for k in ring.size():
		var a := ring[k]
		var b := ring[(k + 1) % ring.size()]
		var la := first + k
		var lb := first + (k + 1) % ring.size()
		for index in [a, b, la, b, lb, la, a, la, b, b, la, lb]: indices.append(index)
	return indices

## Invisible walls at the foot of the boundary range.
func _build_bounds() -> void:
	for side in 4:
		var shape := CollisionShape3D.new()
		var box := BoxShape3D.new()
		box.size = Vector3(2.0 * H.DRIVABLE + 4.0, 200.0, 2.0) if side < 2 else Vector3(2.0, 200.0, 2.0 * H.DRIVABLE + 4.0)
		shape.shape = box
		var sign := 1.0 if side % 2 == 0 else -1.0
		shape.position = Vector3(0, 50, sign * (H.DRIVABLE + 1.0)) if side < 2 else Vector3(sign * (H.DRIVABLE + 1.0), 50, 0)
		collision.add_child(shape)

## Water looks (offroad/river.gdshader presets): name, colour scattered back by
## the water, tint of light crossing it, clarity (m), wave strength, foam,
## reflection.
const WATER_LOOKS := [
	["Ľadovcový potok", Color(0.38, 0.66, 0.66), Color(0.78, 0.96, 0.96), 1.7, 1.3, 1.0, 0.85],
	["Lesná rieka", Color(0.1, 0.28, 0.25), Color(0.72, 0.92, 0.84), 1.1, 1.0, 0.8, 0.9],
	["Bahnitá rieka", Color(0.36, 0.28, 0.17), Color(0.78, 0.64, 0.46), 0.35, 0.8, 0.7, 0.7],
	["Stojaté jazierko", Color(0.05, 0.11, 0.08), Color(0.58, 0.7, 0.48), 0.8, 0.35, 0.3, 1.0],
	["Rozvodnená voda", Color(0.45, 0.34, 0.2), Color(0.72, 0.56, 0.38), 0.18, 1.4, 1.0, 0.6],
]
## Look index of the forest pond.
const POND_LOOK := 3

## Water: a strip following the river at its level (banks rise above it) and a
## disc per pond, sharing one material. The look changes along the river (see
## H.river_look) and each vertex carries the current (H.water_flow_at, what a car feels)
## and its place across the river for the flowing ripples.
## The wake simulation follows the lead car wherever it meets water.
func _build_river() -> void:
	_water_materials()
	_add_water("River", _river_strip(-H.HALF, H.HALF), river_material)
	# Ponds share the river's looks but freeze on their own (still water).
	for p in H.ponds.size():
		_add_water("Pond%d" % p, _pond_mesh(H.ponds[p]), pond_material, 0.5)

## The river's material (looks, waves, the wake simulation) and the ponds' copy.
func _water_materials() -> void:
	var material := ShaderMaterial.new()
	river_material = material
	material.shader = preload("res://offroad/river.gdshader")
	# (A phone keeps a smaller, coarser grid, the lowest quality a coarser one still and plainer
	# water; the car feels the same current on all of them.)
	var settings := preload("res://offroad/game_settings.gd")
	var plain: bool = settings.effective_quality() == "low"
	var phone: bool = settings.is_mobile()
	ripples.configure(Vector4(RIPPLE_AREA.x, RIPPLE_AREA.y, 26.0, 26.0) if phone else RIPPLE_AREA, 0.5 if plain else (0.4 if phone else 0.35))
	material.set_shader_parameter("water_detail", 0.0 if plain else 1.0)
	material.set_shader_parameter("ripple_map", ripples.texture)
	material.set_shader_parameter("ripple_rect", ripples.bounds())
	material.set_shader_parameter("ripple_cells", Vector2(ripples.nx, ripples.nz))
	var body := PackedVector3Array()
	var tint := PackedVector3Array()
	var looks := PackedVector4Array()
	for look: Array in WATER_LOOKS:
		var b: Color = (look[1] as Color).srgb_to_linear()
		var t: Color = (look[2] as Color).srgb_to_linear()
		body.append(Vector3(b.r, b.g, b.b))
		tint.append(Vector3(t.r, t.g, t.b))
		looks.append(Vector4(look[3], look[4], look[5], look[6]))
	material.set_shader_parameter("body_colors", body)
	material.set_shader_parameter("tint_colors", tint)
	material.set_shader_parameter("looks", looks)
	material.set_shader_parameter("sky_tint", Color(0.68, 0.78, 0.84))
	material.set_shader_parameter("wave_normal_a", _noise_texture(0.05, 7, true))
	material.set_shader_parameter("wave_normal_b", _noise_texture(0.02, 19, true))
	material.set_shader_parameter("foam_noise", _noise_texture(0.06, 31, false))
	pond_material = material.duplicate()

## The river from z0 to z1: rows 1 m apart across its course.
func _river_strip(z0: float, z1: float) -> Mesh:
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	# Wide enough for the flooded river: the banks hide what lies under them.
	const WIDTH := 22.0
	const ACROSS := 36
	var rows := 0
	var z: float = z0
	while z <= z1:
		var centre: float = H.river_x(z)
		var look: float = H.river_look(z)
		var level: float = H.river_level(z)
		var current: Vector2 = H.river_current(z) if H.river_off(z, H.river_gap) <= 0.5 else Vector2.ZERO
		var pond_near: bool = H.pond_at(centre, z, WIDTH + 6.0) >= 0
		for i in ACROSS + 1:
			var off := -WIDTH + 2.0 * WIDTH * i / ACROSS
			st.set_color(Color(look / 4.0, 0, 0))
			# (The same as H.water_flow_at, without asking about ponds where there are none.)
			st.set_uv(H.water_flow_at(centre + off, z) if pond_near else current * H.flow_share(off, z))
			st.set_uv2(Vector2(off, z))
			st.set_normal(Vector3.UP)
			st.add_vertex(Vector3(centre + off, level, z))
		rows += 1
		# 1 m rows: the wake simulation displaces the vertices anywhere now.
		z += 1.0
	for r in rows - 1:
		for i in ACROSS:
			var a := r * (ACROSS + 1) + i
			# Clockwise seen from above: Godot's front face, so the surface faces the sky.
			for index in [a, a + 1, a + ACROSS + 1, a + 1, a + ACROSS + 2, a + ACROSS + 1]: st.add_index(index)
	return st.commit()

func _add_water(title: String, mesh: Mesh, material: ShaderMaterial, rise: float = 1.0) -> void:
	var water := MeshInstance3D.new()
	water.name = title
	water.mesh = mesh
	water.material_override = material
	water.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	water.extra_cull_margin = 0.6
	water.set_meta("rise", rise)
	_place(water)
	water_nodes.append(water)

## A still disc at the pond's level, in rings so the wakes can displace it.
func _pond_mesh(pond: Vector4) -> Mesh:
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	const RINGS := 14
	const SEGMENTS := 56
	var radius := pond.z + 1.0
	st.set_color(Color(POND_LOOK / 4.0, 0, 0))
	st.set_uv(Vector2.ZERO)
	st.set_uv2(Vector2(pond.x, pond.y))
	st.set_normal(Vector3.UP)
	st.add_vertex(Vector3(pond.x, pond.w, pond.y))
	for ring in range(1, RINGS + 1):
		for s in SEGMENTS:
			var a := TAU * s / SEGMENTS
			var place := Vector2(pond.x + cos(a) * radius * ring / RINGS, pond.y + sin(a) * radius * ring / RINGS)
			st.set_uv2(place)
			st.add_vertex(Vector3(place.x, pond.w, place.y))
	for s in SEGMENTS:
		var n := (s + 1) % SEGMENTS
		# Clockwise seen from above (front face up).
		for index in [0, 1 + s, 1 + n]: st.add_index(index)
	for ring in range(1, RINGS):
		var inner := 1 + (ring - 1) * SEGMENTS
		var outer := 1 + ring * SEGMENTS
		for s in SEGMENTS:
			var n := (s + 1) % SEGMENTS
			for index in [inner + s, outer + s, inner + n, inner + n, outer + s, outer + n]: st.add_index(index)
	return st.commit()

## Seamless noise for the water: normal maps for the waves, plain for foam.
func _noise_texture(frequency: float, seed: int, normal: bool) -> NoiseTexture2D:
	var noise := FastNoiseLite.new()
	noise.noise_type = FastNoiseLite.TYPE_SIMPLEX_SMOOTH if normal else FastNoiseLite.TYPE_CELLULAR
	noise.frequency = frequency
	noise.fractal_octaves = 3
	noise.seed = seed
	var texture := NoiseTexture2D.new()
	texture.width = 256
	texture.height = 256
	texture.seamless = true
	texture.noise = noise
	texture.as_normal_map = normal
	texture.bump_strength = 6.0
	texture.generate_mipmaps = true
	return texture

## Moves the wake simulation with the lead car whenever water is near it: looked for a
## few times a second, far enough ahead for the grid to be ready when the car gets there.
func _follow_wakes(vehicles: Array, delta: float) -> void:
	if vehicles.is_empty(): return
	var car = vehicles[0]
	var p := Vector2(car.position.x, car.position.z)
	water_near_clock -= delta
	if water_near_clock <= 0.0:
		water_near_clock = 0.25
		var was_near := water_near
		water_near = car.water_contact > 0.0 or H.surface_at(p.x, p.y) == Surface.WATER
		for a in 8:
			if water_near: break
			var o := Vector2.from_angle(a * TAU / 8.0) * 24.0
			water_near = H.surface_at(p.x + o.x, p.y + o.y) == Surface.WATER
		# (Coming to water: the grid looks again at what lies around it, the flood may have changed.)
		if water_near and not was_near: ripples.look_up_all()
	if water_near: ripples.follow(p)

## What the river's shader needs to know of the car and the waves. The car's motion through the
## water parts the surface around the hull (river.gdshader), asleep it reads nothing at all.
func _show_wakes(vehicles: Array) -> void:
	if ripples.moved:
		ripples.moved = false
		river_material.set_shader_parameter("ripple_rect", ripples.bounds())
	if ripples.active != wakes_shown:
		wakes_shown = ripples.active
		river_material.set_shader_parameter("ripple_awake", wakes_shown)
	if vehicles.is_empty(): return
	var car = vehicles[0]
	if car.water_contact <= 0.0 and hull_shown <= 0.0: return
	hull_shown = car.water_contact
	var forward := Vector2(-car.attitude.z.x, -car.attitude.z.z).normalized()
	river_material.set_shader_parameter("hull_wet", hull_shown)
	river_material.set_shader_parameter("hull_centre", Vector2(car.position.x, car.position.z))
	river_material.set_shader_parameter("hull_forward", forward)
	river_material.set_shader_parameter("hull_half", Vector2(car.hull_half_length(), car.hull_half_width()))
	river_material.set_shader_parameter("hull_motion", car.water_relative)

## True where the ground around a point (radius r) is all one of `kinds`.
func _clear(p: Vector2, r: float, kinds: Array) -> bool:
	for o: Vector2 in [Vector2(r, 0), Vector2(-r, 0), Vector2(0, r), Vector2(0, -r), Vector2.ZERO]:
		if H.surface_at(p.x + o.x, p.y + o.y) not in kinds: return false
	return true

func _drivable(p: Vector2) -> bool:
	return absf(p.x) < H.DRIVABLE and absf(p.y) < H.DRIVABLE

func _trunk(p: Vector2, y: float, radius: float, height: float) -> void:
	if radius <= 0.0 or not _drivable(p): return
	var shape := CollisionShape3D.new()
	var cylinder := CylinderShape3D.new()
	cylinder.radius = radius
	cylinder.height = height
	shape.shape = cylinder
	shape.position = Vector3(p.x, y + height * 0.5, p.y)
	collision.add_child(shape)

## One MultiMeshInstance per (cell, model): instances [Transform3D, Color]; seen from `begin`
## to `end` metres.
func _batches(name: String, cells: Dictionary, end: float, shadows: bool, begin: float = 0.0) -> int:
	var total := 0
	for key: Vector3i in cells:
		var items: Array = cells[key]
		var multimesh := MultiMesh.new()
		multimesh.transform_format = MultiMesh.TRANSFORM_3D
		multimesh.use_colors = true
		multimesh.mesh = _mesh(Props.catalogue()[key.z])
		multimesh.instance_count = items.size()
		for i in items.size():
			multimesh.set_instance_transform(i, items[i][0])
			multimesh.set_instance_color(i, items[i][1])
		var batch := MultiMeshInstance3D.new()
		batch.name = "%s_%d_%d_%d" % [name, key.x, key.y, key.z]
		batch.multimesh = multimesh
		batch.visibility_range_begin = begin
		batch.visibility_range_begin_margin = begin * 0.15
		batch.visibility_range_end = end
		batch.visibility_range_end_margin = end * 0.1
		batch.visibility_range_fade_mode = GeometryInstance3D.VISIBILITY_RANGE_FADE_SELF
		batch.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON if shadows else GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		_place(batch)
		total += items.size()
	return total

## Where placed models go: this node, or (for a world built in tiles, see
## endless_world.gd) the tile being built, which is freed with them.
var place_parent: Node3D
var quiet := false

func _place(node: Node) -> void:
	(place_parent if place_parent != null else self).add_child(node)

func _report(what: String, count: int) -> void:
	if not quiet: print("HIGHLANDS %s=%d" % [what, count])

## The drivable square, where the scattered models go by default.
func _whole() -> Rect2:
	return Rect2(-H.DRIVABLE, -H.DRIVABLE, 2.0 * H.DRIVABLE, 2.0 * H.DRIVABLE)

## Share of the drivable square an area covers (counts scale with it; 1 for
## the whole map).
func _share(area: Rect2) -> float:
	return area.get_area() / _whole().get_area() if area.has_area() else 1.0

func _add(cells: Dictionary, model: String, p: Vector2, transform: Transform3D, color: Color) -> void:
	var key := Vector3i(floori(p.x / CELL), floori(p.y / CELL), Props.catalogue().find(model))
	if not cells.has(key): cells[key] = []
	cells[key].append([transform, color])

## Size of the cells the winter trees up close are batched in (m).
const WINTER_CELL := 20.0

## True in a belt of trees that closes the world off (endless_world.gd).
func _in_belt(_p: Vector2, _area: Rect2) -> bool:
	return false

## Forest of the kit trees (10–16 m) on forest ground, a few on meadows.
func _build_forest(area := Rect2()) -> void:
	if not area.has_area(): area = Rect2(-H.HALF + 6.0, -H.HALF + 6.0, 2.0 * H.HALF - 12.0, 2.0 * H.HALF - 12.0)
	var cells := {}
	# Winter conifers: wider crowns, fewer of them, each twice over: finely up close in small
	# cells, lightly further off.
	var winter: bool = str(forest_models[0]).begins_with("winter_")
	var close_cells := {}
	var far_cells := {}
	var spacing := (8.0 if mobile else 6.5) if winter else 5.0
	var x: float = area.position.x
	while x < area.end.x:
		var z: float = area.position.y
		while z < area.end.y:
			var p := Vector2(x + rng.randf_range(-2.0, 2.0), z + rng.randf_range(-2.0, 2.0))
			z += spacing
			# Each tile owns its vegetation; jitter must not put an instance in
			# a neighbour that can be unloaded independently.
			if not area.has_point(p): continue
			var ground: int = H.surface_at(p.x, p.y)
			var belt := _in_belt(p, area) and ground != Surface.WATER
			var chance := 0.95 if belt else (0.8 if ground == Surface.FOREST else (0.03 if ground == Surface.GRASS else 0.0))
			if rng.randf() >= chance or not (belt or _clear(p, 2.6, [Surface.FOREST, Surface.GRASS])): continue
			var model: String = forest_models[rng.randi() % forest_models.size()]
			var scale := rng.randf_range(0.8, 1.25) if winter else rng.randf_range(1.25, 1.7)
			var y: float = H.height_at(p.x, p.y)
			var basis := Basis(Vector3.UP, rng.randf_range(0.0, TAU)).scaled(Vector3(scale, scale * rng.randf_range(0.92, 1.12), scale))
			# Slight per-tree hue and brightness so the canopy is not uniform.
			var tone := Color.from_hsv(rng.randf_range(0.0, 0.08), rng.randf_range(0.0, 0.2), rng.randf_range(0.82, 1.08)) * foliage_tint
			var standing := Transform3D(basis, Vector3(p.x, y - 0.15, p.y))
			if winter:
				var key := Vector3i(floori(p.x / WINTER_CELL), floori(p.y / WINTER_CELL), Props.catalogue().find(model + "_hi"))
				if not close_cells.has(key): close_cells[key] = []
				close_cells[key].append([standing, Color.WHITE * rng.randf_range(0.88, 1.08)])
				_add(far_cells, model + "_lo", p, standing, Color.WHITE * rng.randf_range(0.88, 1.08))
				_trunk(p, y, Props.trunk(model + "_hi") * scale, 3.5)
				continue
			_add(cells, model, p, standing, tone)
			_trunk(p, y, Props.trunk(model) * scale, 3.5)
		x += spacing
	if winter:
		var near_reach := 42.0 if mobile else 80.0
		_batches("ForestClose", close_cells, near_reach, not mobile)
		_batches("ForestFar", far_cells, 170.0 if mobile else 240.0, not mobile, near_reach - 12.0)
		return
	_report("forest trees", _batches("Forest", cells, 220.0, true))

## Realistic feature trees along the trails, around the camp and on meadows,
## as single nodes so each picks its own level of detail and is culled alone.
func _build_feature_trees(area := Rect2()) -> void:
	var share := _share(area)
	if not area.has_area(): area = _whole()
	# The oak source contains three separate trees spread along X. Its baked
	# mesh combines and rotates all three, leaving crowns stacked in mid-air.
	var models: Array = Props.names("hero_").filter(func(model: String) -> bool: return model != "hero_oak")
	var count := 0
	var wanted := roundi((36 if mobile else 110) * share)
	var attempts := 0
	while count < wanted and attempts < roundi(30000 * share):
		attempts += 1
		var p := Vector2(rng.randf_range(area.position.x, area.end.x), rng.randf_range(area.position.y, area.end.y))
		var ground: int = H.surface_at(p.x, p.y)
		if ground not in [Surface.GRASS, Surface.FOREST] or not _clear(p, 4.0, [Surface.GRASS, Surface.FOREST]): continue
		# Favour the edges of trails and meadows, where they are seen up close.
		var near_trail := false
		for o: Vector2 in [Vector2(9, 0), Vector2(-9, 0), Vector2(0, 9), Vector2(0, -9)]:
			if H.surface_at(p.x + o.x, p.y + o.y) in [Surface.DIRT, Surface.GRAVEL, Surface.ROCK, Surface.MUD]: near_trail = true
		if not near_trail and p.distance_to(Vector2(H.CAMP.x, H.CAMP.y)) > 40.0 and rng.randf() > 0.15: continue
		var model: String = models[rng.randi() % models.size()]
		var tree := MeshInstance3D.new()
		tree.name = "Feature_%s_%d" % [model, count]
		tree.mesh = _mesh(model)
		var y: float = H.height_at(p.x, p.y)
		var scale := rng.randf_range(0.85, 1.15)
		tree.transform = Transform3D(Basis(Vector3.UP, rng.randf_range(0.0, TAU)).scaled(Vector3.ONE * scale), Vector3(p.x, y - 0.2, p.y))
		tree.visibility_range_end = 120.0 if not mobile else 70.0
		tree.visibility_range_end_margin = 15.0
		tree.visibility_range_fade_mode = GeometryInstance3D.VISIBILITY_RANGE_FADE_SELF
		_place(tree)
		_trunk(p, y, Props.MODELS[model][3] * scale, 4.0)
		count += 1
	_report("feature trees", count)

## Bushes and hedges along forest edges and scattered over meadows.
func _build_undergrowth(area := Rect2()) -> void:
	var share := _share(area)
	if not area.has_area(): area = _whole()
	var cells := {}
	var kinds := ["bush_1", "bush_2", "bush_3", "bush_4", "bush_5", "hedge"]
	var placed := 0
	var target := roundi((1100 if mobile else 3000) * share)
	var attempts := 0
	while placed < target and attempts < roundi(120000 * share):
		attempts += 1
		var p := Vector2(rng.randf_range(area.position.x, area.end.x), rng.randf_range(area.position.y, area.end.y))
		var ground: int = H.surface_at(p.x, p.y)
		if ground not in [Surface.GRASS, Surface.FOREST] or not _clear(p, 1.6, [Surface.GRASS, Surface.FOREST]): continue
		# Mostly where forest meets meadow or trail.
		var edge := false
		for o: Vector2 in [Vector2(5, 0), Vector2(-5, 0), Vector2(0, 5), Vector2(0, -5)]:
			if H.surface_at(p.x + o.x, p.y + o.y) != ground: edge = true
		if not edge and rng.randf() > 0.2: continue
		var model: String = kinds[rng.randi() % kinds.size()]
		var scale := rng.randf_range(0.8, 1.5)
		var basis := Basis(Vector3.UP, rng.randf_range(0.0, TAU)).scaled(Vector3.ONE * scale)
		_add(cells, model, p, Transform3D(basis, Vector3(p.x, H.height_at(p.x, p.y) - 0.05, p.y)), Color.from_hsv(rng.randf_range(0.0, 0.1), rng.randf_range(0.0, 0.25), rng.randf_range(0.85, 1.1)) * foliage_tint)
		placed += 1
	# Bushes are low: their shadows are not worth a pass in every cascade.
	_report("undergrowth", _batches("Undergrowth", cells, 140.0, false))

## Tufts of tall grass, clover and flowers: dense on meadows, sparse in the
## forest, only drawn close to the camera.
func _build_ground_cover(area := Rect2()) -> void:
	if not area.has_area(): area = _whole()
	var cells := {}
	var spacing := 2.6 if not mobile else 4.0
	var count := 0
	var x: float = area.position.x
	while x < area.end.x:
		var z: float = area.position.y
		while z < area.end.y:
			var p := Vector2(x + rng.randf_range(-1.2, 1.2), z + rng.randf_range(-1.2, 1.2))
			z += spacing
			if not area.has_point(p): continue
			var ground: int = H.surface_at(p.x, p.y)
			if ground == Surface.GRASS:
				if rng.randf() > 0.55: continue
			elif ground == Surface.FOREST:
				if rng.randf() > 0.12: continue
			else: continue
			if not _clear(p, 0.9, [Surface.GRASS, Surface.FOREST]): continue
			var roll := rng.randf()
			var model: String = cover_grass[rng.randi() % cover_grass.size()] if roll < 0.6 else (cover_low[rng.randi() % cover_low.size()] if roll < 0.9 else cover_tall[rng.randi() % cover_tall.size()])
			var scale := rng.randf_range(0.6, 1.1)
			var basis := Basis(Vector3.UP, rng.randf_range(0.0, TAU)).scaled(Vector3(scale, scale * rng.randf_range(0.7, 1.2), scale))
			_add(cells, model, p, Transform3D(basis, Vector3(p.x, H.height_at(p.x, p.y) - 0.03, p.y)), Color.from_hsv(rng.randf_range(0.0, 0.08), rng.randf_range(0.0, 0.2), rng.randf_range(0.8, 1.05)) * foliage_tint)
			count += 1
		x += spacing
	_batches("GroundCover", cells, 55.0 if not mobile else 35.0, false)
	_report("ground cover", count)

## Granite boulders on rocky ground, in the quarry and along the river; mossy
## ones in the forest. Larger ones are solid.
func _build_boulders(area := Rect2()) -> void:
	var share := _share(area)
	if not area.has_area(): area = _whole()
	var cells := {}
	var plain: Array = Props.names("rock_plain")
	var mossy: Array = Props.names("rock_moss")
	var placed := 0
	var attempts := 0
	while placed < roundi(700 * share) and attempts < roundi(40000 * share):
		attempts += 1
		var p := Vector2(rng.randf_range(area.position.x, area.end.x), rng.randf_range(area.position.y, area.end.y))
		var ground: int = H.surface_at(p.x, p.y)
		var near_quarry: bool = p.distance_to(Vector2(H.QUARRY.x, H.QUARRY.y)) < H.QUARRY.z
		var near_river := absf(p.x - H.river_x(p.y)) < 12.0
		var ok := ground == Surface.ROCK or (near_quarry and ground == Surface.GRAVEL) or (near_river and ground in [Surface.MUD, Surface.GRASS]) \
			or (ground == Surface.FOREST and rng.randf() < 0.12) or (ground == Surface.GRASS and rng.randf() < 0.02)
		if not ok or not _clear(p, 2.5, [Surface.ROCK, Surface.GRAVEL, Surface.GRASS, Surface.FOREST, Surface.MUD]) or _on_trail(p): continue
		var size := rng.randf_range(0.5, 2.0) * (1.8 if ground == Surface.ROCK else 1.0)
		var pool: Array = mossy if ground == Surface.FOREST else plain
		var model: String = pool[rng.randi() % pool.size()]
		var y: float = H.height_at(p.x, p.y)
		# Pack rocks are about 0.75 m: scale to the wanted size and sink a quarter in.
		var scale := size / 0.75
		var basis := Basis(Vector3.UP, rng.randf_range(0.0, TAU)) * Basis(Vector3.RIGHT, rng.randf_range(-0.2, 0.2)).scaled(Vector3(scale, scale * rng.randf_range(0.7, 1.1), scale))
		_add(cells, model, p, Transform3D(basis, Vector3(p.x, y - size * 0.22, p.y)), Color.from_hsv(0.1, 0.05, rng.randf_range(0.8, 1.05)))
		if size > 0.7 and _drivable(p):
			var shape := CollisionShape3D.new()
			var sphere := SphereShape3D.new()
			sphere.radius = size * 0.55
			shape.shape = sphere
			shape.position = Vector3(p.x, y + size * 0.15, p.y)
			collision.add_child(shape)
		placed += 1
	_report("boulders", _batches("Boulders", cells, 220.0, true))

## Within a trail's width plus 2.5 m of its centreline (the rock climb is rocky
## ground like the slopes beside it, so the ground kind alone cannot tell).
func _on_trail(p: Vector2) -> bool:
	for t in H.trails.size():
		var line: PackedVector2Array = H.paths[t]
		var reach: float = H.trails[t].half + 2.5
		for k in line.size() - 1:
			if Geometry2D.get_closest_point_to_segment(p, line[k], line[k + 1]).distance_squared_to(p) < reach * reach: return true
	return H.surface_at(p.x, p.y) == Surface.WATER

## The start camp: the Endless Drive repair barrack and a few fuel drums.
func _build_camp() -> void:
	var camp := Node3D.new()
	camp.name = "Camp"
	add_child(camp)
	var barrack := Node3D.new()
	barrack.name = "RepairBarrack"
	camp.add_child(barrack)
	# Where the world puts it: barrack spot and heading, drums' corner and the
	# two directions their 2 × 2 stack runs.
	var layout: Dictionary = H.camp_layout()
	var spot: Vector2 = layout.barrack
	barrack.position = Vector3(spot.x, H.height_at(spot.x, spot.y), spot.y)
	barrack.rotation.y = layout.heading
	EndlessModels.environment(barrack, "res://assets/models/stops/repair.tscn", "Barrack")
	var body := StaticBody3D.new()
	body.name = "BarrackCollision"
	barrack.add_child(body)
	var shape := CollisionShape3D.new()
	var box := BoxShape3D.new()
	box.size = Vector3(6.5, 3.0, 4.5)
	shape.shape = box
	shape.position = Vector3(0, 1.5, 0)
	body.add_child(shape)
	var drum_mesh := CylinderMesh.new()
	drum_mesh.top_radius = 0.33
	drum_mesh.bottom_radius = 0.33
	drum_mesh.height = 0.95
	var paint := StandardMaterial3D.new()
	paint.albedo_color = Color("3f5b3a")
	paint.metallic = 0.4
	paint.roughness = 0.55
	drum_mesh.material = paint
	for i in 4:
		var d: Vector2 = layout.drums + layout.across * ((i % 2) * 0.8) + layout.along * ((i / 2) * 0.8)
		var drum := MeshInstance3D.new()
		drum.mesh = drum_mesh
		drum.position = Vector3(d.x, H.height_at(d.x, d.y) + 0.47, d.y)
		camp.add_child(drum)

func _build_signs() -> void:
	var spots: Array = H.test_spots()
	for i in spots.size():
		var spot: Array = spots[i]
		var label := Label3D.new()
		label.name = "Sign%d" % (i + 1)
		label.text = "%d · %s" % [i + 1, spot[0]]
		label.billboard = BaseMaterial3D.BILLBOARD_ENABLED
		label.font_size = 72
		label.outline_size = 18
		label.pixel_size = 0.02
		label.modulate = Color("f3ecd8")
		label.outline_modulate = Color(0.08, 0.09, 0.07, 0.85)
		label.shaded = false
		label.double_sided = true
		label.position = Vector3(spot[1], H.height_at(spot[1], spot[2]) + 3.0, spot[2])
		label.visible = false
		add_child(label)
		signs.append(label)

func set_signs_visible(show: bool) -> void:
	if signs.is_empty() or signs[0].visible == show: return
	for sign in signs: sign.visible = show

func update_wakes(vehicles: Array, delta: float = 1.0 / 60.0) -> void:
	_follow_wakes(vehicles, delta)
	ripples.update(delta, vehicles)
	_show_wakes(vehicles)
	weather.update(delta)
	_apply_flood()

func next_weather() -> void:
	weather.next_weather()

func weather_text() -> String:
	return weather.weather_text()

## Raises the water surfaces with the flood and tells the ground shader where
## the waterline is now (the banks are wet up to it).
func _apply_flood() -> void:
	for water in water_nodes: water.position.y = H.flood * float(water.get_meta("rise"))
	for material in ground_materials: material.set_shader_parameter("water_level", H.WATER_LEVEL + H.flood)
	pond_material.set_shader_parameter("ice", H.pond_ice)
	pond_material.set_shader_parameter("rain", weather.rain)

## Load timing (printed at the end of build): milliseconds per step.
var laps: Array[String] = []
var lap_clock := 0

func _lap(step: String) -> void:
	var now := Time.get_ticks_usec()
	laps.append("%s %d" % [step, (now - lap_clock) / 1000])
	lap_clock = now
