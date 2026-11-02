extends "res://offroad/highlands_world.gd"
## Streams the endless highland (endless_terrain.gd) around the car, in 60 m
## tiles: made on worker threads within RADIUS tiles, dressed (forest, bushes,
## grass, rocks, river, ponds, trunks and boulders as colliders) within
## DRESS_RADIUS, one tile a frame, and freed with everything on them beyond
## DROP_RADIUS. The ground is a fixed pool of chunk meshes laid on the tiles
## around the car, lifted by the ground shader from the ring height map.
const T = preload("res://offroad/endless_terrain.gd")
const Roadside = preload("res://offroad/roadside.gd")
const CrushMap = preload("res://offroad/crush_map.gd")
const RADIUS := 5
const DRESS_RADIUS := 4
const DROP_RADIUS := 6
## Worker threads making tiles at once.
const JOBS := 6
## Tiles dressed around the car (fewer on a weak phone, which also draws less far).
var dress_radius := 3 if mobile and Settings.effective_quality() == "low" else DRESS_RADIUS

## Made tiles: Vector2i → {low, high, node (once dressed)}.
var tiles := {}
## Tiles being made (Vector2i → task id) and finished results (under mutex).
var pending := {}
var finished: Array = []
var mutex := Mutex.new()
## Ground chunk meshes: [near, far] per pool entry, and the tile each shows.
var pool: Array = []
var pool_tiles: Array[Vector2i] = []
var grid_meshes := {}
var height_texture: ImageTexture
var ground_texture: ImageTexture
var textures_dirty := false
var centre := Vector2i(1 << 30, 0)
var planning := -1
## Snow overlays for models loaded after the start (see _mesh).
var overlay_seen := {}
var overlays_ready := false
var stream_ms := 0.0
## The far land: its mesh, the point it was made around, the worker making the
## next one (and its result, under mutex).
var far_land: MeshInstance3D
var far_material: ShaderMaterial
var far_at := Vector2(INF, INF)
var far_task := -1
var far_done: Array = []
## Rebuilt when the car has gone this far from where it was made.
const FAR_MOVE := 400.0
## Where sites' buildings will stand: kept free of trees and rocks.
var clearings: Array[Vector2] = []
## What the car has flattened, and every material that reads it.
var crush := CrushMap.new()
var plant_materials: Array[ShaderMaterial] = []

func _init() -> void:
	H = T
	cover_grass = ["pack_wheatgrass", "pack_buffalo", "pack_longgrass"]
	cover_low = ["pack_phlox", "pack_buffalo", "pack_longgrass"]
	cover_tall = ["pack_yarrow", "pack_blanketflower", "pack_beardtongue"]

func build(root: Node3D) -> void:
	name = "Endless"
	quiet = true
	var started := Time.get_ticks_usec()
	lap_clock = started
	if mobile:
		for material: ShaderMaterial in [(root.get_node(root.ground_node) as MeshInstance3D).material_override, root.soil.fine_material]:
			material.shader = preload("res://offroad/ground_phone.gdshader")
	_ground_material(root)
	ground_texture = ground_materials[0].get_shader_parameter("ground_map")
	for material in ground_materials:
		material.set_shader_parameter("wrap_maps", true)
		material.set_shader_parameter("ground_map", ground_texture)
	_lap("ground textures")
	_build_ground(root)
	_lap("ground")
	_water_materials()
	add_child(weather)
	weather.setup(root, [(root.get_node(root.ground_node) as MeshInstance3D).material_override, root.soil.fine_material], river_material)
	# The first tiles, around the camp. (A saved run far from it makes its own, where it is:
	# no sense in making the land twice.)
	if not _saved_far_away(): await stream_all(Vector2(T.CAMP.x, T.CAMP.y))
	_lap("tiles")
	await _build_far_land()
	_lap("far land")
	Roadside.prepare()
	_crop_material(0)
	_lap("roadside models")
	# (The fog thins, or the far land would never show.)
	weather.clear_fog = 0.0011
	root.soil.recenter(Vector2(T.CAMP.x, T.CAMP.y))
	_build_camp()
	_build_signs()
	weather._collect_overlays()
	for mesh: Mesh in meshes.values(): _register_overlays(mesh)
	overlays_ready = true
	_lap("weather")
	print("ENDLESS load %d ms: %s" % [(Time.get_ticks_usec() - started) / 1000, ", ".join(laps)])

## The ring maps as textures, and the pool of ground chunks (11 × 11 tiles,
## near and far meshes, as highlands_world.gd builds them for a fixed map).
func _build_ground(root: Node3D) -> void:
	var base := root.get_node(root.ground_node) as MeshInstance3D
	height_texture = ImageTexture.create_from_image(T.mesh_height_image())
	for material in ground_materials:
		material.set_shader_parameter("ground_heights", height_texture)
		material.set_shader_parameter("heights_half", 0.0)
		material.set_shader_parameter("heights_step", T.STEP)
	# Grid step → the distances it is drawn from and to (a phone has one step more).
	var bands := {1: [0.0, 75.0], FAR_STEP: [75.0, 170.0], 4: [170.0, 0.0]} if mobile else {1: [0.0, 200.0], FAR_STEP: [200.0, 0.0]}
	for i in (2 * RADIUS + 1) * (2 * RADIUS + 1):
		var pair := []
		for step: int in bands:
			var chunk := MeshInstance3D.new()
			chunk.name = "Ground_%d%s" % [i, "" if step == 1 else "_far"]
			chunk.mesh = _grid_mesh(CHUNK / step, step) if not grid_meshes.has(step) else grid_meshes[step]
			grid_meshes[step] = chunk.mesh
			chunk.material_override = base.material_override
			chunk.set_instance_shader_parameter("grid_step", SPACING * step)
			chunk.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
			chunk.visibility_range_begin = bands[step][0]
			chunk.visibility_range_end = bands[step][1]
			chunk.visible = false
			base.add_child(chunk)
			pair.append(chunk)
		pool.append(pair)
		pool_tiles.append(T.EMPTY)

## The first far land, made at once.
func _build_far_land() -> void:
	far_material = ShaderMaterial.new()
	far_material.shader = preload("res://offroad/far_terrain.gdshader")
	far_land = MeshInstance3D.new()
	far_land.name = "FarLand"
	far_land.material_override = far_material
	far_land.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(far_land)
	_near_rect()
	far_at = Vector2(T.CAMP.x, T.CAMP.y).snapped(Vector2.ONE * T.FAR_GRID)
	var job := T.far_job(far_at)
	far_task = WorkerThreadPool.add_task(_make_far.bind(job))
	while not WorkerThreadPool.is_task_completed(far_task): await get_tree().process_frame
	_update_far(far_at)

func _show_far(arrays: Array) -> void:
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
	far_land.mesh = mesh

## The far land stops one tile inside the real ground, which covers the seam.
func _near_rect() -> void:
	var low := Vector2(centre - Vector2i.ONE * (RADIUS - 1)) * T.TILE_M
	var high := Vector2(centre + Vector2i.ONE * RADIUS) * T.TILE_M
	far_material.set_shader_parameter("near_rect", Vector4(low.x, low.y, high.x, high.y))

## Takes a finished far land, or starts the next when the car has moved on.
func _update_far(p: Vector2) -> void:
	if far_land == null: return
	far_material.set_shader_parameter("snow_cover", weather.snow_cover)
	if far_task != -1:
		if not WorkerThreadPool.is_task_completed(far_task): return
		WorkerThreadPool.wait_for_task_completion(far_task)
		far_task = -1
		mutex.lock()
		var done := far_done
		far_done = []
		mutex.unlock()
		if not done.is_empty(): _show_far(done[0])
		return
	if p.distance_to(far_at) < FAR_MOVE or not T.plan_lock.try_lock(): return
	far_at = p.snapped(Vector2.ONE * T.FAR_GRID)
	var job := T.far_job(far_at)
	T.plan_lock.unlock()
	far_task = WorkerThreadPool.add_task(_make_far.bind(job))

func _make_far(job: Dictionary) -> void:
	var arrays := T.far_bake(job)
	mutex.lock()
	far_done = [arrays]
	mutex.unlock()

## Workers finish before the world goes (they report back to it).
func _exit_tree() -> void:
	if far_task != -1: WorkerThreadPool.wait_for_task_completion(far_task)
	if planning != -1: WorkerThreadPool.wait_for_task_completion(planning)
	for tile: Vector2i in pending.keys(): WorkerThreadPool.wait_for_task_completion(pending[tile])
	for id in tuft_tasks: WorkerThreadPool.wait_for_task_completion(id)

# ---------------------------------------------------------------- fields

## Crops: texture, height (m), metres per texture repeat, the grid (m) of ribbons
## along the field's rows and across them.
const CROPS := [["corn", 2.5, 3.0, 1, 3], ["barley", 0.85, 1.6, 1, 2], ["wheat", 1.0, 2.0, 1, 2], ["rapeseed", 1.25, 1.6, 1, 2], ["sunflower", 2.15, 3.0, 1, 3]]
## Crops with real plants up close, in cells around the car (their ribbons give way
## there, see crop.gdshader): models, spacing (m) across and along, size, colour.
const CLOSE_CROPS := {T.WHEAT: [["crop_wheat"], Vector2(0.8, 0.75), 1.0, Color(0.95, 0.92, 0.8)], T.BARLEY: [["crop_wheat"], Vector2(0.75, 0.7), 0.82, Color(1.12, 1.08, 0.72)],
	T.SUNFLOWER: [["crop_sunflower_1", "crop_sunflower_2", "crop_sunflower_3"], Vector2(1.3, 1.15), 1.0, Color(1, 1, 1)]}
## (On a computer at medium quality and up; a phone has the tufts alone, one look all the way
## out to the ribbons and nothing that changes as the car comes near.)
var close_tier: String = Settings.effective_quality() if not mobile else "low"
var close_plants: bool = close_tier != "low"
var use_tufts: bool = close_plants or (mobile and Settings.effective_quality() != "low")
const CLOSE_CELL := 12.0
## How far the real plants reach, where tufts (crossed quads with the plants' picture)
## take over from them, and where the ribbons take over from those: by quality.
var close_reach := 16.0 if mobile else (40.0 if close_tier == "high" else 30.0)
var tuft_from := 0.0 if mobile else (26.0 if close_tier == "high" else 18.0)
var tuft_to := 85.0 if mobile else (150.0 if close_tier == "high" else 95.0)
## One gives way to the next over this many metres, and the last ribbons to the bare field.
const CROP_BAND := 30.0
const RIBBON_END := 125.0
const TUFT_SPACING := 1.0
const TUFT_WIDTH := 1.25
const RIBBON := 3.0
## Foliage colour per landscape (the order of endless_terrain.gd's enum).
const TINTS := [Color(1, 1, 1), Color(1.08, 1.1, 0.85), Color(1.2, 1.0, 0.65), Color(0.92, 0.98, 0.92), Color(0.85, 0.92, 0.62), Color(1.1, 1.08, 0.75), Color(1.3, 1.02, 0.5), Color(0.75, 0.88, 0.78), Color(1.0, 1.06, 0.82)]
var crop_materials: Array[ShaderMaterial] = []
var tuft_materials: Array[ShaderMaterial] = []

func _crop_material(crop: int) -> ShaderMaterial:
	if crop_materials.is_empty():
		for index in CROPS.size():
			var spec: Array = CROPS[index]
			var material := ShaderMaterial.new()
			material.shader = preload("res://offroad/crop.gdshader")
			material.set_shader_parameter("plants", load("res://assets/models/crops/%s.res" % spec[0]))
			material.set_shader_parameter("tall", spec[1])
			material.set_shader_parameter("field_colour", T.CROP_COLOURS[index])
			if use_tufts:
				material.set_shader_parameter("near_cut", tuft_to - CROP_BAND)
				material.set_shader_parameter("near_band", CROP_BAND)
			if mobile:
				material.set_shader_parameter("far_cut", RIBBON_END - CROP_BAND)
				material.set_shader_parameter("far_band", CROP_BAND)
			var tuft := material.duplicate() as ShaderMaterial
			tuft.set_shader_parameter("near_cut", tuft_from)
			tuft.set_shader_parameter("near_band", 6.0)
			tuft.set_shader_parameter("far_cut", tuft_to - CROP_BAND)
			tuft.set_shader_parameter("far_band", CROP_BAND)
			tuft_materials.append(tuft)
			_listen(tuft)
			crop_materials.append(material)
			_listen(material)
	return crop_materials[crop]

## A tile's crops as ribbons of plants: one mesh of all the rows up close, one of
## every third row further off. Ribbons run both ways, so no view looks through.
func _build_crops(area: Rect2) -> void:
	var any := false
	for a in 5:
		for b in 5:
			if T.crop_at(area.position.x + 6.0 + a * 12.0, area.position.y + 6.0 + b * 12.0) >= 0: any = true
	if not any: return
	var near := {}
	var far := {}
	for axis in 2:
		for row in int(T.TILE_M):
			for step in int(T.TILE_M / RIBBON):
				var along := step * RIBBON
				var ax := area.position.x + (along if axis == 0 else row + 0.5)
				var az := area.position.y + (row + 0.5 if axis == 0 else along)
				var bx := ax + (RIBBON if axis == 0 else 0.0)
				var bz := az + (0.0 if axis == 0 else RIBBON)
				var crop: int = T.crop_at((ax + bx) * 0.5, (az + bz) * 0.5)
				if crop < 0 or crop == T.FALLOW: continue
				var spec: Array = CROPS[crop]
				var spacing: int = spec[3] if T.field_axis((ax + bx) * 0.5, (az + bz) * 0.5) == axis else spec[4]
				if row % spacing != 0: continue
				# (On phones half the rows and nothing further off.)
				if mobile:
					if row % (spacing * 2) == 0: _ribbon(near, crop, ax, az, bx, bz, spec)
					continue
				_ribbon(near, crop, ax, az, bx, bz, spec)
				if row % (spacing * 3) == 0: _ribbon(far, crop, ax, az, bx, bz, spec)
	_ribbons_mesh(near, 0.0, RIBBON_END + 40.0 if mobile else 115.0)
	wants_tufts = use_tufts
	_ribbons_mesh(far, 100.0, 270.0)

func _ribbon(store: Dictionary, crop: int, ax: float, az: float, bx: float, bz: float, spec: Array) -> void:
	if not store.has(crop): store[crop] = [PackedVector3Array(), PackedVector2Array(), PackedColorArray(), PackedInt32Array()]
	var set_of: Array = store[crop]
	var ya: float = T.terrain_height(ax, az)
	var yb: float = T.terrain_height(bx, bz)
	var tall_a: float = spec[1] * (0.88 + 0.24 * (0.5 + 0.5 * sin(ax * 1.7 + az * 2.3)))
	var tall_b: float = spec[1] * (0.88 + 0.24 * (0.5 + 0.5 * sin(bx * 1.7 + bz * 2.3)))
	var ua: float = (ax + az) / spec[2]
	var ub: float = (bx + bz) / spec[2]
	var at: int = (set_of[0] as PackedVector3Array).size()
	var vertices: PackedVector3Array = set_of[0]
	vertices.append_array([Vector3(ax, ya + tall_a, az), Vector3(bx, yb + tall_b, bz), Vector3(bx, yb - 0.05, bz), Vector3(ax, ya - 0.05, az)])
	set_of[0] = vertices
	var uvs: PackedVector2Array = set_of[1]
	uvs.append_array([Vector2(ua, 0.0), Vector2(ub, 0.0), Vector2(ub, 1.0), Vector2(ua, 1.0)])
	set_of[1] = uvs
	var shade := 0.86 + 0.28 * (0.5 + 0.5 * sin(ax * 0.37 + az * 0.53))
	var colours: PackedColorArray = set_of[2]
	var tone := Color(shade, shade, shade)
	colours.append_array([tone, tone, tone, tone])
	set_of[2] = colours
	var indices: PackedInt32Array = set_of[3]
	indices.append_array([at, at + 1, at + 2, at, at + 2, at + 3])
	set_of[3] = indices

func _ribbons_mesh(store: Dictionary, begin: float, end: float) -> void:
	if store.is_empty(): return
	var mesh := ArrayMesh.new()
	for crop: int in store:
		var parts: Array = store[crop]
		var arrays := []
		arrays.resize(Mesh.ARRAY_MAX)
		arrays[Mesh.ARRAY_VERTEX] = parts[0]
		var normals := PackedVector3Array()
		normals.resize((parts[0] as PackedVector3Array).size())
		normals.fill(Vector3.UP)
		arrays[Mesh.ARRAY_NORMAL] = normals
		arrays[Mesh.ARRAY_TEX_UV] = parts[1]
		arrays[Mesh.ARRAY_COLOR] = parts[2]
		arrays[Mesh.ARRAY_INDEX] = parts[3]
		mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
		mesh.surface_set_material(mesh.get_surface_count() - 1, _crop_material(crop))
	var node := MeshInstance3D.new()
	node.name = "Crops"
	node.mesh = mesh
	node.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	node.visibility_range_begin = begin
	node.visibility_range_end = end
	_place(node)

## The middle distance: two crossed quads every metre or so, each a slice of the crop's
## picture, one mesh a tile; put together on a worker thread (thousands of them).
var wants_tufts := false
var tuft_tasks: Array[int] = []

func _make_tufts(tile: Vector2i) -> void:
	var store := _tuft_store(Rect2(tile.x * T.TILE_M, tile.y * T.TILE_M, T.TILE_M, T.TILE_M))
	_show_tufts.call_deferred(tile, store)

func _tuft_store(area: Rect2) -> Dictionary:
	var store := {}
	var random := RandomNumberGenerator.new()
	random.seed = hash([T.seed_value, floori(area.position.x), floori(area.position.y), 3])
	var z := area.position.y + TUFT_SPACING * 0.5
	while z < area.end.y:
		var x := area.position.x + TUFT_SPACING * 0.5
		while x < area.end.x:
			var px := x + random.randf_range(-0.45, 0.45)
			var pz := z + random.randf_range(-0.45, 0.45)
			x += TUFT_SPACING
			var crop: int = T.crop_at(px, pz)
			if crop < 0 or crop == T.FALLOW: continue
			var spec: Array = CROPS[crop]
			var y: float = T.terrain_height(px, pz)
			var angle := random.randf_range(0.0, PI)
			var slice := random.randf()
			for turn in 2:
				var across := Vector2.from_angle(angle + turn * PI * 0.5) * TUFT_WIDTH * 0.5
				_tuft_quad(store, crop, Vector3(px - across.x, y, pz - across.y), Vector3(px + across.x, y, pz + across.y), spec[1] * random.randf_range(0.88, 1.08), slice + turn * 0.37, TUFT_WIDTH / float(spec[2]))
		z += TUFT_SPACING
	return store

## (The tile may have been dropped meanwhile.)
func _show_tufts(tile: Vector2i, store: Dictionary) -> void:
	if store.is_empty() or not tiles.has(tile) or not tiles[tile].has("node"): return
	var mesh := ArrayMesh.new()
	for crop: int in store:
		var parts: Array = store[crop]
		var arrays := []
		arrays.resize(Mesh.ARRAY_MAX)
		arrays[Mesh.ARRAY_VERTEX] = parts[0]
		var normals := PackedVector3Array()
		normals.resize((parts[0] as PackedVector3Array).size())
		normals.fill(Vector3.UP)
		arrays[Mesh.ARRAY_NORMAL] = normals
		arrays[Mesh.ARRAY_TEX_UV] = parts[1]
		arrays[Mesh.ARRAY_COLOR] = parts[2]
		arrays[Mesh.ARRAY_INDEX] = parts[3]
		mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
		mesh.surface_set_material(mesh.get_surface_count() - 1, tuft_materials[crop])
	var node := MeshInstance3D.new()
	node.name = "Crops"
	node.mesh = mesh
	node.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	node.visibility_range_end = tuft_to + 45.0
	(tiles[tile].node as Node).add_child(node)

func _tuft_quad(store: Dictionary, crop: int, a: Vector3, b: Vector3, tall: float, u: float, span: float) -> void:
	if not store.has(crop): store[crop] = [PackedVector3Array(), PackedVector2Array(), PackedColorArray(), PackedInt32Array()]
	var parts: Array = store[crop]
	var at: int = (parts[0] as PackedVector3Array).size()
	var vertices: PackedVector3Array = parts[0]
	vertices.append_array([a + Vector3.UP * tall, b + Vector3.UP * tall, b - Vector3.UP * 0.05, a - Vector3.UP * 0.05])
	parts[0] = vertices
	var uvs: PackedVector2Array = parts[1]
	uvs.append_array([Vector2(u, 0.0), Vector2(u + span, 0.0), Vector2(u + span, 1.0), Vector2(u, 1.0)])
	parts[1] = uvs
	var shade := 0.86 + 0.28 * (0.5 + 0.5 * sin(a.x * 0.37 + a.z * 0.53))
	var tone := Color(shade, shade, shade)
	var colours: PackedColorArray = parts[2]
	colours.append_array([tone, tone, tone, tone])
	parts[2] = colours
	var indices: PackedInt32Array = parts[3]
	indices.append_array([at, at + 1, at + 2, at, at + 2, at + 3])
	parts[3] = indices

## The real plants of a tile's fields, in cells seen only from close by (made when
## the car comes near the tile, as they are many).
func _build_close_crops(tile: Vector2i, node: Node3D) -> void:
	var area := Rect2(tile.x * T.TILE_M, tile.y * T.TILE_M, T.TILE_M, T.TILE_M)
	var random := RandomNumberGenerator.new()
	random.seed = hash([T.seed_value, tile.x, tile.y, 9])
	var cells := {}
	for crop: int in CLOSE_CROPS:
		var spec: Array = CLOSE_CROPS[crop]
		var spacing: Vector2 = spec[1]
		var models: Array = spec[0]
		var z := area.position.y + spacing.y * 0.5
		while z < area.end.y:
			var x := area.position.x + spacing.x * 0.5
			while x < area.end.x:
				var p := Vector2(x + random.randf_range(-0.28, 0.28), z + random.randf_range(-0.28, 0.28))
				x += spacing.x
				if not area.has_point(p) or T.crop_at(p.x, p.y) != crop: continue
				var model: String = models[random.randi() % models.size()]
				var key := [floori((p.x - area.position.x) / CLOSE_CELL), floori((p.y - area.position.y) / CLOSE_CELL), model, crop]
				if not cells.has(key): cells[key] = []
				var size: float = spec[2] * random.randf_range(0.85, 1.12)
				# (Sunflowers all look the same way, to the sun.)
				var turn := random.randf_range(-0.5, 0.5) if models.size() > 1 else random.randf_range(0.0, TAU)
				cells[key].append(Transform3D(Basis(Vector3.UP, turn).scaled(Vector3.ONE * size), Vector3(p.x, T.terrain_height(p.x, p.y) - 0.03, p.y)))
			z += spacing.y
	for key: Array in cells:
		var items: Array = cells[key]
		var multimesh := MultiMesh.new()
		multimesh.transform_format = MultiMesh.TRANSFORM_3D
		multimesh.use_colors = true
		multimesh.mesh = _mesh(key[2])
		multimesh.instance_count = items.size()
		var tone: Color = CLOSE_CROPS[key[3]][3]
		for i in items.size():
			multimesh.set_instance_transform(i, items[i])
			multimesh.set_instance_color(i, tone * random.randf_range(0.88, 1.08))
		var batch := MultiMeshInstance3D.new()
		batch.name = "CloseCrops"
		batch.multimesh = multimesh
		batch.visibility_range_end = close_reach
		batch.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		node.add_child(batch)

## Each landscape's own plants: cacti and dry shrubs, reeds in the marsh.
func _build_land_plants(area: Rect2, biome: int) -> void:
	var cells := {}
	var wanted := {}
	match biome:
		T.DESERT: wanted = {"shrub_dry": 16, "cactus_1": 2, "cactus_2": 3}
		T.STEPPE: wanted = {"shrub_dry": 22}
		T.FIELDS: wanted = {"pack_wheatgrass": 90, "pack_longgrass": 40, "pack_yarrow": 12, "pack_beardtongue": 8, "pack_blanketflower": 6}
		T.BOG: wanted = {"reeds": 70}
		_: return
	for model: String in wanted:
		for n in wanted[model]:
			var p := Vector2(rng.randf_range(area.position.x, area.end.x), rng.randf_range(area.position.y, area.end.y))
			var ground: int = T.surface_at(p.x, p.y)
			if biome == T.FIELDS:
				if ground != Surface.GRASS: continue
			elif biome == T.BOG:
				if ground != Surface.MUD and ground != Surface.GRASS: continue
			elif ground not in [Surface.SAND, Surface.GRAVEL, Surface.GRASS, Surface.PACKED]: continue
			if _on_trail(p): continue
			var y: float = T.terrain_height(p.x, p.y)
			var scale := rng.randf_range(0.8, 1.35)
			var basis := Basis(Vector3.UP, rng.randf_range(0.0, TAU)).scaled(Vector3.ONE * scale)
			_add(cells, model, p, Transform3D(basis, Vector3(p.x, y - 0.06, p.y)), Color(1, 1, 1) * rng.randf_range(0.85, 1.1))
			var trunk: float = Props.trunk(model)
			if trunk > 0.0: _trunk(p, y, trunk * scale, 3.0)
	_batches("Plants", cells, 190.0, false)

# ---------------------------------------------------------------- by the road

const POLE_EVERY := 48.0
const PROP_EVERY := 150.0
const OPEN_LAND := [T.DESERT, T.STEPPE, T.FIELDS, T.MEADOW]
## The serpentines' stone wall: one block's length, its height and thickness (m).
const STONE_LENGTH := 2.3
const STONE_SIZE := Vector3(0.6, 2.2, 2.4)
const PROPS_HERE := {T.DESERT: ["container02", "trailer01", "truck01", "sign01", "watertank01", "container03", "board01"], T.STEPPE: ["sign02", "trailer02", "truck01", "generator01", "fence02"],
	T.FIELDS: ["fence01", "fence02", "sign03", "cable_reel01", "watertank01", "generator01"], T.MEADOW: ["fence01", "fence02", "bench01", "sign04"],
	T.BOG: ["sign02", "cable_reel01"], T.FOREST: ["sign01", "cable_reel01", "container01"], T.TAIGA: ["sign04", "container01"], T.FOOTHILLS: ["sign03", "bench01", "fence01"]}

## The stretch of main road through this tile: a power line in the open land and
## things left by the road. Everything comes from the distance along the road,
## so a tile made again looks the same.
func _dress_road(area: Rect2) -> void:
	var main: Dictionary = T.roads[0]
	var line: PackedVector2Array = main.points
	var along: PackedFloat32Array = main.along
	var near := T.roads_near(area)
	if not near.has(0): return
	var samples: Array = near[0]
	samples.sort()
	var stones: Array[Transform3D] = []
	for k: int in samples:
		if k < 1 or k + 2 >= line.size() or not area.has_point(line[k]): continue
		if floori(along[k] / POLE_EVERY) != floori(along[k - 1] / POLE_EVERY): _pole(floori(along[k] / POLE_EVERY))
		if floori(along[k] / STONE_LENGTH) != floori(along[k - 1] / STONE_LENGTH) and T.wall_share(line[k].y, T.wall_spans) > 0.8: _stones(k, near[0], stones)
		if floori(along[k] / PROP_EVERY) != floori(along[k - 1] / PROP_EVERY): _road_prop(k, floori(along[k] / PROP_EVERY))
	if not stones.is_empty(): _build_stones(stones)

## Where pole n stands: Vector3(x, z, height of its top), or nothing if the line
## has no pole there. (Remembered: a pole's neighbours ask for it too.)
var pole_spots := {}

func _pole_spot(n: int) -> Variant:
	if pole_spots.has(n): return pole_spots[n]
	if pole_spots.size() > 600: pole_spots.clear()
	pole_spots[n] = _find_pole(n)
	return pole_spots[n]

func _find_pole(n: int) -> Variant:
	var main: Dictionary = T.roads[0]
	var along: PackedFloat32Array = main.along
	var k := along.bsearch(n * POLE_EVERY)
	var line: PackedVector2Array = main.points
	if k < 1 or k + 2 >= line.size(): return null
	var tangent := (line[k + 2] - line[k - 1]).normalized()
	# The line keeps to one side for kilometres at a time.
	var side := 1.0 if hash([T.seed_value, n / 80]) % 2 == 0 else -1.0
	var p: Vector2 = line[k] + Vector2(-tangent.y, tangent.x) * side * (main.half + 6.0)
	if T.biome_at(p.x, p.y) not in OPEN_LAND or absf(p.x - T.river_x(p.y)) < 16.0: return null
	for site: Dictionary in T.sites:
		if site.has("lot") and (site.at as Vector2).distance_to(p) < site.lot + 3.0: return null
	return Vector3(p.x, p.y, maxf((main.levels as PackedFloat32Array)[k], T.land_at(p.x, p.y)) + POLE_HEIGHT)

## The next pole on from n in a direction (a gap of one or two is spanned).
func _pole_after(n: int, way: int) -> Variant:
	for gap in range(1, 4):
		var spot: Variant = _pole_spot(n + way * gap)
		if spot != null: return [n + way * gap, spot]
	return null

## Along which the line runs at pole n: halfway between its two spans.
func _pole_heading(n: int, spot: Vector3) -> Vector2:
	var at := Vector2(spot.x, spot.y)
	var run := Vector2.ZERO
	var before: Variant = _pole_after(n, -1)
	var after: Variant = _pole_after(n, 1)
	if before != null: run += (at - Vector2(before[1].x, before[1].y)).normalized()
	if after != null: run += (Vector2(after[1].x, after[1].y) - at).normalized()
	return run.normalized()

const POLE_HEIGHT := 6.3
## Where the wires hang on a pole: half the crossarm, and below its top.
const ARM := 0.76
const ARM_BELOW := 0.2

func _pole(n: int) -> void:
	var spot: Variant = _pole_spot(n)
	if spot == null: return
	var heading := _pole_heading(n, spot)
	# (Alone, with neither neighbour, it would carry nothing.)
	if heading == Vector2.ZERO: return
	var p := Vector2(spot.x, spot.y)
	var top: float = spot.z
	var base: float = T.terrain_height(p.x, p.y) - 0.1
	var pole := MeshInstance3D.new()
	pole.name = "Pole"
	pole.mesh = Roadside.mesh("pylon01")
	pole.transform = Transform3D(Basis(Vector3.UP, atan2(heading.x, heading.y)).scaled(Vector3(1.0, (top - base) / 6.69, 1.0)), Vector3(p.x, base, p.y))
	pole.visibility_range_end = 420.0
	_place(pole)
	_trunk(p, base, 0.14, top - base)
	var after: Variant = _pole_after(n, 1)
	if after == null: return
	var far_spot: Vector3 = after[1]
	var far_heading := _pole_heading(after[0], far_spot)
	var q := Vector2(far_spot.x, far_spot.y)
	var near_arm := Vector2(heading.y, -heading.x) * ARM
	var far_arm := Vector2(far_heading.y, -far_heading.x) * ARM
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	for arm: float in [1.0, -1.0]:
		var a := p + near_arm * arm
		var b := q + far_arm * arm
		_wire(st, Vector3(a.x, top - ARM_BELOW, a.y), Vector3(b.x, far_spot.z - ARM_BELOW, b.y))
	var wires := MeshInstance3D.new()
	wires.name = "Wires"
	wires.mesh = st.commit()
	wires.material_override = _wire_material()
	wires.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	wires.visibility_range_end = 420.0
	_place(wires)

## One wire hanging from a to b: a thin three-sided tube, sagging in the middle.
func _wire(st: SurfaceTool, a: Vector3, b: Vector3) -> void:
	const PARTS := 8
	const RADIUS := 0.022
	var along := (b - a).normalized()
	var one := along.cross(Vector3.UP).normalized()
	var two := along.cross(one).normalized()
	var sag := a.distance_to(b) * 0.018
	var rim := []
	for i in 3: rim.append((one * cos(TAU * i / 3.0) + two * sin(TAU * i / 3.0)))
	for s in PARTS:
		var t0 := float(s) / PARTS
		var t1 := float(s + 1) / PARTS
		var c0 := a.lerp(b, t0) + Vector3.DOWN * sag * 4.0 * t0 * (1.0 - t0)
		var c1 := a.lerp(b, t1) + Vector3.DOWN * sag * 4.0 * t1 * (1.0 - t1)
		for i in 3:
			var d0: Vector3 = rim[i]
			var d1: Vector3 = rim[(i + 1) % 3]
			var normal := (d0 + d1).normalized()
			for corner: Vector3 in [c0 + d0 * RADIUS, c1 + d0 * RADIUS, c0 + d1 * RADIUS, c0 + d1 * RADIUS, c1 + d0 * RADIUS, c1 + d1 * RADIUS]:
				st.set_normal(normal)
				st.add_vertex(corner)

var wire_material: StandardMaterial3D

func _wire_material() -> StandardMaterial3D:
	if wire_material == null:
		wire_material = StandardMaterial3D.new()
		wire_material.albedo_color = Color(0.07, 0.07, 0.07)
		wire_material.roughness = 0.6
		wire_material.cull_mode = BaseMaterial3D.CULL_DISABLED
	return wire_material

func _road_prop(k: int, n: int) -> void:
	var main: Dictionary = T.roads[0]
	var line: PackedVector2Array = main.points
	var chance := hash([T.seed_value, n, 5])
	if chance % 100 > 45: return
	var biome: int = T.biome_at(line[k].x, line[k].y)
	if not PROPS_HERE.has(biome): return
	var kinds: Array = PROPS_HERE[biome]
	var model: String = kinds[(chance >> 8) % kinds.size()]
	var tangent := (line[k + 2] - line[k - 1]).normalized()
	var side := 1.0 if (chance >> 16) % 2 == 0 else -1.0
	var normal := Vector2(-tangent.y, tangent.x) * side
	var fence := model.begins_with("fence")
	var off: float = main.half + (3.0 if fence or model.begins_with("sign") else 7.0)
	for part in (6 if fence else 1):
		var s: int = mini(k + part * 2, line.size() - 3)
		var along_road := (line[s + 2] - line[s - 1]).normalized()
		var p: Vector2 = line[s] + Vector2(-along_road.y, along_road.x) * side * off
		var ground: int = T.surface_at(p.x, p.y)
		if ground == Surface.WATER or ground == Surface.ROCK: continue
		var blocked := false
		for hole in clearings:
			if hole.distance_to(p) < 16.0: blocked = true
		if blocked: continue
		var prop := Roadside.spawn(model)
		prop.position = Vector3(p.x, T.terrain_height(p.x, p.y) - 0.03, p.y)
		# Long things along the road, signs facing it.
		prop.rotation.y = atan2(along_road.y, -along_road.x)
		_place(prop)

## The road's edge at sample k gets a block of the stone wall on each side where the ground
## falls away (not where the road's own next leg runs close by). Each block stops the car.
func _stones(k: int, samples: Array, stones: Array[Transform3D]) -> void:
	var main: Dictionary = T.roads[0]
	var line: PackedVector2Array = main.points
	var levels: PackedFloat32Array = main.levels
	var tangent := (line[mini(k + 2, line.size() - 1)] - line[k - 1]).normalized()
	var normal := Vector2(-tangent.y, tangent.x)
	var off: float = main.half + 0.75
	# (Nothing on the inside of a bend: that is where the wheels cut across.)
	var ahead := line[mini(k + 8, line.size() - 1)] - line[k]
	var behind := line[k] - line[maxi(k - 8, 0)]
	var inside := signf(behind.cross(ahead)) if absf(behind.angle_to(ahead)) > 0.2 else 0.0
	for side: float in [-1.0, 1.0]:
		if side == inside: continue
		var p: Vector2 = line[k] + normal * side * off
		var beyond: Vector2 = line[k] + normal * side * (off + 4.0)
		if T.terrain_height(beyond.x, beyond.y) > levels[k] - 0.7: continue
		var clear := true
		for j: int in samples:
			if absi(j - k) > 8 and line[j].distance_squared_to(p) < (main.half + 1.2) * (main.half + 1.2):
				clear = false
				break
		if not clear: continue
		var unit := Vector3(p.x, 0.0, p.y)
		var cell := Vector2i(floori(p.x * 3.0), floori(p.y * 3.0))
		# Following the road's level and grade, a stone or two out of line; its foot is in the ground.
		var yaw := atan2(tangent.x, tangent.y) + (T._unit(cell, 7) - 0.5) * 0.03
		var grade := atan2(levels[mini(k + 2, levels.size() - 1)] - levels[k - 1], line[mini(k + 2, line.size() - 1)].distance_to(line[k - 1]))
		unit.y = levels[k] + 0.72 + 0.07 * T._unit(cell, 8) - 0.5 * STONE_SIZE.y
		stones.append(Transform3D(Basis(Vector3.UP, yaw) * Basis(Vector3.RIGHT, -grade), unit))

func _build_stones(stones: Array[Transform3D]) -> void:
	if stone_mesh == null:
		var shape := BoxMesh.new()
		shape.size = STONE_SIZE
		var material := StandardMaterial3D.new()
		material.albedo_texture = load(Props.GROUND + "rock_albedo.png")
		material.albedo_color = Color(0.82, 0.8, 0.76)
		material.uv1_triplanar = true
		material.uv1_world_triplanar = true
		material.uv1_scale = Vector3.ONE * 0.55
		material.vertex_color_use_as_albedo = true
		material.roughness = 1.0
		shape.material = material
		stone_mesh = shape
		stone_shape = BoxShape3D.new()
		stone_shape.size = Vector3(STONE_SIZE.x, STONE_SIZE.y, STONE_SIZE.z)
	var multimesh := MultiMesh.new()
	multimesh.transform_format = MultiMesh.TRANSFORM_3D
	multimesh.use_colors = true
	multimesh.mesh = stone_mesh
	multimesh.instance_count = stones.size()
	for i in stones.size():
		multimesh.set_instance_transform(i, stones[i])
		var shade := 0.82 + 0.3 * T._unit(Vector2i(floori(stones[i].origin.x), floori(stones[i].origin.z)), 9)
		multimesh.set_instance_color(i, Color(shade, shade, shade))
		var collider := CollisionShape3D.new()
		collider.shape = stone_shape
		collider.transform = Transform3D(stones[i].basis.orthonormalized(), stones[i].origin)
		collision.add_child(collider)
	var wall := MultiMeshInstance3D.new()
	wall.name = "StoneWall"
	wall.multimesh = multimesh
	wall.visibility_range_end = 170.0 if mobile else 300.0
	wall.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF if mobile else GeometryInstance3D.SHADOW_CASTING_SETTING_ON
	_place(wall)

var stone_mesh: BoxMesh
var stone_shape: BoxShape3D

## What a winch can hook on around a point: the trunks, poles and boulders of the tiles
## there (their colliders), as points on them a little above the ground.
func anchors_near(at: Vector2, reach: float) -> Array[Vector3]:
	var found: Array[Vector3] = []
	var from := T.tile_of(at.x - reach, at.y - reach)
	var to := T.tile_of(at.x + reach, at.y + reach)
	for tz in range(from.y, to.y + 1):
		for tx in range(from.x, to.x + 1):
			var info: Dictionary = tiles.get(Vector2i(tx, tz), {})
			if not info.has("node"): continue
			var body := (info.node as Node).get_node_or_null("Collision")
			if body == null: continue
			for shape: Node in body.get_children():
				if not shape is CollisionShape3D: continue
				var p: Vector3 = (shape as CollisionShape3D).position
				if Vector2(p.x, p.z).distance_squared_to(at) < reach * reach: found.append(Vector3(p.x, T.terrain_height(p.x, p.z) + 0.8, p.z))
	return found

## The density reference for the scattered models (a tile gets its share).
func _whole() -> Rect2:
	return Rect2(-345.0, -345.0, 690.0, 690.0)

func _drivable(_p: Vector2) -> bool:
	return true

func _on_trail(p: Vector2) -> bool:
	for spot in clearings:
		if spot.distance_squared_to(p) < 13.0 * 13.0: return true
	# (The summit's lot is all clear.)
	if Vector2(T.summit.x, T.summit.z).distance_squared_to(p) < (T.SUMMIT_LOT + 6.0) * (T.SUMMIT_LOT + 6.0): return true
	var near := T.roads_near(Rect2(p - Vector2(8, 8), Vector2(16, 16)))
	for r: int in near:
		var road: Dictionary = T.roads[r]
		var line: PackedVector2Array = road.points
		var reach: float = road.half + 2.5
		for k: int in near[r]:
			if k + 1 < line.size() and Geometry2D.get_closest_point_to_segment(p, line[k], line[k + 1]).distance_squared_to(p) < reach * reach: return true
	return T.surface_at(p.x, p.y) == Surface.WATER

const WINTER_TREES := ["winter_a", "winter_b", "winter_c"]
var winter_materials := {}

## A winter tree's bark or needles (one material each per species, shared by all).
func _winter_material(kind: String) -> StandardMaterial3D:
	if winter_materials.has(kind): return winter_materials[kind]
	var material := StandardMaterial3D.new()
	material.albedo_texture = load(Props.TEXTURES + "winter_" + kind + ".png")
	material.vertex_color_use_as_albedo = true
	material.roughness = 0.95
	material.metallic_specular = 0.0
	if kind.ends_with("needles"):
		material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR
		material.alpha_scissor_threshold = 0.4
		material.cull_mode = BaseMaterial3D.CULL_DISABLED
	winter_materials[kind] = material
	return material

func _mesh(model: String) -> Mesh:
	var fresh := not meshes.has(model)
	var mesh := super(model)
	if fresh and model.begins_with("winter_"):
		var species := "pine" if model.begins_with("winter_c") else "spruce"
		mesh.surface_set_material(0, _winter_material(species + "_bark"))
		mesh.surface_set_material(1, _winter_material(species + "_needles"))
	if fresh:
		if overlays_ready: _register_overlays(mesh)
		for i in mesh.get_surface_count():
			var material := mesh.surface_get_material(i)
			if material is ShaderMaterial and material not in plant_materials: _listen(material)
	return mesh

## This material's plants lie down where the car has been.
func _listen(material: ShaderMaterial) -> void:
	material.set_shader_parameter("crush_map", crush.texture)
	plant_materials.append(material)

## Snow overlays for a model's materials (weather.gd collects those present at
## the start; tiles bring new models later).
func _register_overlays(mesh: Mesh) -> void:
	for i in mesh.get_surface_count():
		var material := mesh.surface_get_material(i)
		if material == null: continue
		var before := weather.overlays.size()
		weather._overlay_for(material, overlay_seen, self)
		if weather.overlays.size() > before and weather.overlays_on: material.next_pass = weather.overlays[before][1]

func update_wakes(vehicles: Array, delta: float = 1.0 / 60.0) -> void:
	super(vehicles, delta)
	if vehicles.is_empty(): return
	var started := Time.get_ticks_usec()
	_stream(Vector2(vehicles[0].position.x, vehicles[0].position.z), false)
	crush.update(vehicles[0])
	for material in plant_materials:
		material.set_shader_parameter("car", vehicles[0].position)
		material.set_shader_parameter("wind", weather.wind)
	stream_ms = lerpf(stream_ms, (Time.get_ticks_usec() - started) / 1000.0, 0.05)

func _apply_flood() -> void:
	water_nodes = water_nodes.filter(func(node) -> bool: return is_instance_valid(node))
	super()

func _saved_far_away() -> bool:
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--seed="): return false
	const SAVE := "user://endless_expedition.json"
	if not FileAccess.file_exists(SAVE): return false
	var saved = JSON.parse_string(FileAccess.get_file_as_string(SAVE))
	if not saved is Dictionary or not saved.has("x") or not saved.has("z"): return false
	return Vector2(float(saved.x), float(saved.z)).distance_to(Vector2(T.CAMP.x, T.CAMP.y)) > T.TILE_M * RADIUS

## Streams around p, a frame at a time, until everything in reach is made and dressed.
func stream_all(p: Vector2) -> void:
	while true:
		_stream(p, false)
		if planning == -1 and _all_there(p): return
		await get_tree().process_frame

func _all_there(p: Vector2) -> bool:
	var here := T.tile_of(p.x, p.y)
	for dz in range(-RADIUS, RADIUS + 1):
		for dx in range(-RADIUS, RADIUS + 1):
			var info: Variant = tiles.get(here + Vector2i(dx, dz))
			if info == null: return false
			if maxi(absi(dx), absi(dz)) <= dress_radius and not info.has("node"): return false
	# (And the far land around it.)
	return far_land == null or (far_task == -1 and p.distance_to(far_at) < FAR_MOVE)

## One frame of streaming around point p: plan the road on ahead (on a worker
## thread), collect made tiles, start new ones nearest first, lay the ground
## chunks, dress one tile, drop far ones. With `wait`, everything in reach at
## once (the start).
func _stream(p: Vector2, wait: bool) -> void:
	var here := T.tile_of(p.x, p.y)
	_update_far(p)
	if planning != -1 and WorkerThreadPool.is_task_completed(planning):
		WorkerThreadPool.wait_for_task_completion(planning)
		planning = -1
	if planning == -1 and T.planned_z < p.y + T.PLAN_AHEAD - 200.0:
		# Massifs and ponds for the stretch ahead are placed here, so the
		# planner only reads them.
		T._ensure_zones(T.planned_z + T.PLAN_AHEAD)
		T._ensure_bands(T.planned_z + T.PLAN_AHEAD)
		planning = WorkerThreadPool.add_task(Callable(T, "plan_async").bind(p.y + T.PLAN_AHEAD))
	# Recentre first: tiles that arrive are kept or dropped by the new centre.
	if here != centre:
		centre = here
		_drop_far()
		_lay_ground()
		if far_land != null: _near_rect()
	_collect()
	# The planner holds the lock while it changes the roads; tiles wait then.
	if not T.plan_lock.try_lock():
		_finish_textures()
		return
	var wanted: Array[Vector2i] = []
	for dz in range(-RADIUS, RADIUS + 1):
		for dx in range(-RADIUS, RADIUS + 1):
			var tile := here + Vector2i(dx, dz)
			if not tiles.has(tile) and not pending.has(tile): wanted.append(tile)
	wanted.sort_custom(func(a: Vector2i, b: Vector2i) -> bool: return (a - here).length_squared() < (b - here).length_squared())
	for tile in wanted:
		if pending.size() >= maxi(2, mini(JOBS, OS.get_processor_count() - 2)) and not wait: break
		var job := T.tile_job(tile)
		pending[tile] = WorkerThreadPool.add_task(_make_tile.bind(job))
	T.plan_lock.unlock()
	if wait:
		for tile: Vector2i in pending.keys(): WorkerThreadPool.wait_for_task_completion(pending[tile])
		pending.clear()
		_collect()
	_finish_textures()
	# Dress tiles (all at the start; one a frame while driving).
	var dress: Array[Vector2i] = []
	for tile: Vector2i in tiles:
		if tiles[tile].has("node"): continue
		var d := tile - here
		if maxi(absi(d.x), absi(d.y)) > dress_radius: continue
		var ready := true
		for nz in range(-1, 2):
			for nx in range(-1, 2):
				if not tiles.has(tile + Vector2i(nx, nz)): ready = false
		if ready: dress.append(tile)
	_near_crops(here)
	if dress.is_empty(): return
	dress.sort_custom(func(a: Vector2i, b: Vector2i) -> bool: return (a - here).length_squared() < (b - here).length_squared())
	if not T.plan_lock.try_lock(): return
	for tile in (dress if wait else dress.slice(0, 1)): _dress(tile)
	T.plan_lock.unlock()

func _make_tile(job: Dictionary) -> void:
	var result := T.bake_tile(job)
	mutex.lock()
	finished.append(result)
	mutex.unlock()

func _collect() -> void:
	mutex.lock()
	var done := finished
	finished = []
	mutex.unlock()
	for result: Dictionary in done:
		var tile: Vector2i = result.tile
		if pending.has(tile):
			WorkerThreadPool.wait_for_task_completion(pending[tile])
			pending.erase(tile)
		# Left behind while it was made: dropped (its ring slot may be in use).
		var d := tile - centre
		if centre.x != T.EMPTY.x and maxi(absi(d.x), absi(d.y)) > RADIUS + 1: continue
		T.store_tile(result)
		tiles[tile] = {"low": result.low, "high": result.high}
		textures_dirty = true
		var slot := pool_tiles.find(tile)
		if slot >= 0: _show_chunk(slot)

## Ground chunks onto the tiles around the car.
func _lay_ground() -> void:
	var wanted: Array[Vector2i] = []
	for dz in range(-RADIUS, RADIUS + 1):
		for dx in range(-RADIUS, RADIUS + 1): wanted.append(centre + Vector2i(dx, dz))
	var free_slots: Array[int] = []
	for slot in pool_tiles.size():
		if pool_tiles[slot] not in wanted: free_slots.append(slot)
	for tile in wanted:
		if tile in pool_tiles: continue
		var slot: int = free_slots.pop_back()
		pool_tiles[slot] = tile
		_show_chunk(slot)

func _show_chunk(slot: int) -> void:
	var tile := pool_tiles[slot]
	var made: bool = tiles.has(tile)
	for chunk: MeshInstance3D in pool[slot]:
		chunk.position = Vector3(tile.x * T.TILE_M, 0.0, tile.y * T.TILE_M)
		chunk.visible = made
		if made:
			var info: Dictionary = tiles[tile]
			chunk.custom_aabb = AABB(Vector3(0.0, info.low - 3.5, 0.0), Vector3(T.TILE_M, info.high - info.low + 4.0, T.TILE_M))

func _drop_far() -> void:
	for tile: Vector2i in tiles.keys():
		var d := tile - centre
		if maxi(absi(d.x), absi(d.y)) <= DROP_RADIUS: continue
		var info: Dictionary = tiles[tile]
		if info.has("node"): (info.node as Node).queue_free()
		T.drop_tile(tile)
		tiles.erase(tile)

func _finish_textures() -> void:
	if not textures_dirty: return
	textures_dirty = false
	height_texture.update(T.mesh_height_image())
	ground_texture.update(T.ground_image())

## Real plants for one more tile around the car (one a frame).
func _near_crops(here: Vector2i) -> void:
	if not close_plants: return
	for dz in range(-1, 2):
		for dx in range(-1, 2):
			var tile := here + Vector2i(dx, dz)
			if not tiles.has(tile) or not tiles[tile].has("node") or tiles[tile].has("close"): continue
			tiles[tile].close = true
			_build_close_crops(tile, tiles[tile].node)
			return

## Everything on one tile, in its own node (freed with it): forest, feature
## trees, bushes, grass, rocks, the river stretch and ponds, and the trunks and
## boulders as one static body.
func _dress(tile: Vector2i) -> void:
	var node := Node3D.new()
	node.name = "Tile_%d_%d" % [tile.x, tile.y]
	place_parent = node
	collision = StaticBody3D.new()
	collision.name = "Collision"
	rng.seed = hash([T.seed_value, tile.x, tile.y])
	var area := Rect2(tile.x * T.TILE_M, tile.y * T.TILE_M, T.TILE_M, T.TILE_M)
	clearings.clear()
	for site: Dictionary in T.sites:
		if site.has("lot") and area.grow(site.lot + 4.0).has_point(site.at): clearings.append(site.at)
	var biome: int = T.biome_at(area.get_center().x, area.get_center().y)
	foliage_tint = TINTS[biome]
	# (High up nothing leafy grows, whatever the land is called there.)
	var high: bool = T.altitude_of(area.get_center().y, T.altitude) > T.TREELINE
	forest_models = WINTER_TREES if high or biome in [T.TAIGA, T.MOUNTAINS, T.FOOTHILLS] else FOREST_TREES
	_build_forest(area)
	if not high and biome not in [T.TAIGA, T.DESERT, T.MOUNTAINS]: _build_feature_trees(area)
	# (No green bushes up where it snows.)
	if not high and biome != T.MOUNTAINS: _build_undergrowth(area)
	_build_ground_cover(area)
	_build_boulders(area)
	wants_tufts = false
	_build_crops(area)
	_build_land_plants(area, biome)
	_dress_road(area)
	# The river stretch belongs to the tile it flows through (one per row).
	if floori(T.river_x(area.get_center().y) / T.TILE_M) == tile.x and not T.dry_at(area.get_center().y):
		_add_water("River", _river_strip(area.position.y, area.end.y + 1.0), river_material)
	for pond: Vector4 in T.ponds:
		if area.has_point(Vector2(pond.x, pond.y)): _add_water("Pond", _pond_mesh(pond), pond_material, 0.5)
	node.add_child(collision)
	add_child(node)
	place_parent = null
	tiles[tile].node = node
	if wants_tufts:
		tuft_tasks = tuft_tasks.filter(func(id: int) -> bool: return not WorkerThreadPool.is_task_completed(id))
		tuft_tasks.append(WorkerThreadPool.add_task(_make_tufts.bind(tile)))
