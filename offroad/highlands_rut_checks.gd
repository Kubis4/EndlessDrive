extends SceneTree
const Surface = preload("res://desert_battlefield/terrain.gd")
var failures := 0
func _initialize() -> void: call_deferred("run")
func check(ok: bool, message: String) -> void:
	if ok: print("PASS: ", message)
	else:
		push_error(message)
		failures += 1

func clear_field(soil) -> void:
	soil.field.fill(0.0)
	soil.snow_field.fill(0.0)
	soil.touched.fill(0)

func sample_depth(soil, point: Vector3, shallow: bool) -> float:
	clear_field(soil)
	soil.shallower_highlands_ruts = shallow
	for i in 120: soil.stamp(point, Vector3.FORWARD, 0.2, 1.0)
	return soil.depth_at(point.x, point.z)

func sample_first_pass(soil, point: Vector3, shallow: bool) -> float:
	clear_field(soil)
	soil.shallower_highlands_ruts = shallow
	# Two axles roll over the same two-metre strip at ordinary low slip.
	for axle in 2:
		for segment in 10:
			var start := point + Vector3(0, 0, -1.0 + segment * 0.2)
			soil.stamp_segment(start, start + Vector3(0, 0, 0.2), Vector3.BACK, 0.0)
	return soil.depth_at(point.x, point.z)

func run() -> void:
	var game = load("res://offroad/highlands.tscn").instantiate()
	root.add_child(game)
	game.set_process(false)
	var soil = game.soil
	# Keep the field at the sampled surface while building its visible mesh.
	soil.cars = []
	check(soil.shallower_highlands_ruts, "Highlands enables shallower soil ruts")
	var found := {}
	for z in range(-300, 301, 3):
		for x in range(-300, 301, 3):
			var kind := Surface.surface_at(x, z)
			if kind not in [Surface.GRASS, Surface.FOREST, Surface.DIRT, Surface.GRAVEL, Surface.MUD, Surface.SNOW, Surface.WATER] or found.has(kind): continue
			var uniform := true
			for corner in [Vector2(-1.2, -1.2), Vector2(-1.2, 1.2), Vector2(1.2, -1.2), Vector2(1.2, 1.2)]:
				if Surface.surface_at(x + corner.x, z + corner.y) != kind: uniform = false
			if uniform: found[kind] = Vector3(x, 0, z)
	for kind in [Surface.GRASS, Surface.FOREST, Surface.DIRT, Surface.GRAVEL, Surface.MUD, Surface.SNOW, Surface.WATER]:
		check(found.has(kind), "locate " + Surface.SURFACE_NAMES[kind])
		if not found.has(kind): continue
		var p: Vector3 = found[kind]
		soil.recenter(Vector2(p.x, p.z))
		Surface.wetness = 0.8
		Surface.snow_cover = 0.0
		Surface.snow_depth = 0.0
		var old_first := sample_first_pass(soil, p, false)
		var first := sample_first_pass(soil, p, true)
		print("FIRST ", Surface.SURFACE_NAMES[kind], " old=", old_first, " new=", first)
		check(first > 0.015 and first >= old_first * 0.65, Surface.SURFACE_NAMES[kind] + " leaves a visible first pass")
		if kind == Surface.GRASS:
			while not soil.pending.is_empty() or soil.build_task != -1: soil.update(0.1, true)
			soil.update(0.1, true)
			check(soil.tiles.size() > 0 and soil.uploaded_revision == soil.revision, "first-pass ruts reach the rendered terrain")
			check(soil.fine_material.get_shader_parameter("soil_enabled"), "terrain shader receives the rut field")
		var old := sample_depth(soil, p, false)
		var now := sample_depth(soil, p, true)
		print("RUT ", Surface.SURFACE_NAMES[kind], " old=", old, " new=", now)
		if kind in [Surface.MUD, Surface.SNOW]:
			check(absf(now - old) < 0.00001, Surface.SURFACE_NAMES[kind] + " is unchanged")
		else:
			check(old > 0.0 and now < old * 0.55, Surface.SURFACE_NAMES[kind] + " is shallower")
	if found.has(Surface.GRASS):
		var p: Vector3 = found[Surface.GRASS]
		soil.recenter(Vector2(p.x, p.z))
		Surface.snow_depth = 0.3
		Surface.snow_cover = 1.0
		var old_snow := sample_depth(soil, p, false)
		var new_snow := sample_depth(soil, p, true)
		check(old_snow > 0.0 and absf(new_snow - old_snow) < 0.00001, "snow pack on grass is unchanged")
	print("HIGHLANDS RUT CHECK failures=", failures)
	game.queue_free()
	await process_frame
	quit(1 if failures else 0)
