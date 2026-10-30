extends SceneTree
const Surface = preload("res://desert_battlefield/terrain.gd")
var failures: Array[String] = []

func _initialize() -> void: call_deferred("run")

func check(condition: bool, message: String) -> void:
	if not condition: failures.append(message)

func run() -> void:
	var game := (load("res://desert_battlefield/desert_battlefield.tscn") as PackedScene).instantiate()
	root.add_child(game)
	game.set_process(false)
	for car in game.get_node("Patrol").get_children(): car.set_physics_process(false)
	var soil = game.soil
	if game.quality_name == "notebook":
		check(soil.tile_vertices == 67, "Notebook fine terrain did not reduce triangle density")
		var base := game.get_node("SculptedDunes") as MeshInstance3D
		check(base.mesh == null and base.get_child_count() == 225, "Notebook terrain was not partitioned for culling")
		var authored := load("res://desert_battlefield/dune_terrain.res") as ArrayMesh
		var source: PackedVector3Array = authored.surface_get_arrays(0)[Mesh.ARRAY_VERTEX]
		var triangle_indices := 0
		for child: MeshInstance3D in base.get_children():
			var coords := String(child.name).split("_")
			var tx := int(coords[1])
			var tz := int(coords[2])
			var arrays := child.mesh.surface_get_arrays(0)
			var vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
			triangle_indices += (arrays[Mesh.ARRAY_INDEX] as PackedInt32Array).size()
			for z in [0, 16, 32]:
				for x in [0, 16, 32]:
					check(vertices[z * 33 + x].distance_to(source[(tz * 32 + z) * 481 + tx * 32 + x]) < 0.00001, "Partition changed an authored dune vertex or seam")
			check(child.material_override == base.material_override, "Dune chunk lost the shared soil mask")
		check(triangle_indices == 480 * 480 * 6, "Partition lost or duplicated terrain triangles")
	else:
		check(soil.tile_vertices == 131, "Reference fine geometry changed")
	for lane in 3:
		for i in 20:
			var route := Surface.route(float(i) * 0.3, lane)
			check(Surface.route_xz(float(i) * 0.3, lane).is_equal_approx(Vector2(route.x, route.z)), "Horizontal patrol search changed the route")
	var point := Vector3(80, 0, 20)
	var original := Surface.height_at(point.x, point.z)
	for i in 10: soil.stamp(point, Vector3.FORWARD, 0.2, 0.0)
	var first: float = soil.depth_at(80, 20)
	check(first > 0.05 and first < 0.26, "Rolling wheel must dig a bounded depression")
	check(soil.height_at(80, 20) < original - 0.025, "Physical surface did not fall with the rut")
	check(soil.depth_at(80.75, 20) < -0.008, "Displaced sand did not form a raised shoulder")
	check(is_zero_approx(soil.depth_at(82, 20)), "Stamp modified distant terrain")
	for i in 100: soil.stamp(point, Vector3.FORWARD, 0.2, 1.0)
	check(soil.depth_at(80, 20) > first + 0.04, "Drifting must dig deeper than ordinary rolling")
	check(soil.depth_at(80, 20) <= soil.MAX_DEPTH, "Repeated passes exceeded the depth bound")
	check(soil.depth_at(80.75, 20) >= -soil.MAX_BANK, "Raised sand exceeded the bank bound")
	check(soil.normal_at(80.25, 20).distance_to(Surface.normal_at(80.25, 20)) > 0.05, "Rut walls did not change terrain normals")
	var before: int = soil.revision
	soil.stamp_segment(Vector3(-70, 0, -70), Vector3(70, 0, 10), Vector3.FORWARD, 1)
	check(soil.revision == before, "Teleport excavated a diagonal trench")
	soil.stamp(Vector3(-19, 0, -13), Vector3.FORWARD, 1, 1)
	check(is_zero_approx(soil.depth_at(-19, -13)), "Concrete station foundation was excavated")
	# Simulate successive vehicles, each with front and rear wheels following
	# exactly the same straight line. Ordinary rolling must progress without slip.
	var pass_depths: Array[float] = []
	for passage in 16:
		for axle in 2:
			for segment in 40:
				var a := Vector3(90, 0, 26.0 + segment * 0.2)
				soil.stamp_segment(a, a + Vector3(0, 0, 0.2), Vector3.BACK, 0.0)
		pass_depths.append(soil.depth_at(90, 30))
	check(pass_depths[0] > 0.08 and pass_depths[0] < 0.24, "First vehicle pass excavates too much or too little")
	check(pass_depths[4] > pass_depths[0] + 0.05, "Five ordinary passes did not deepen the rut")
	check(pass_depths[11] > pass_depths[4] + 0.015, "Repeated passes stop deepening too early")
	check(pass_depths.back() > 0.2 and pass_depths.back() <= soil.MAX_DEPTH, "Repeated rolling did not form a deeper bounded rut")
	for i in range(1, pass_depths.size()):
		check(pass_depths[i] >= pass_depths[i - 1], "A repeat pass raised the bottom of the rut")
	check(pass_depths[15] - pass_depths[14] < pass_depths[1] - pass_depths[0], "Compaction did not slow near its limit")
	# A stopped wheel cannot deepen a rut. Subdividing equal travel must have
	# the same response, independent of physics tick or imprint sampling rate.
	var stopped_depth: float = soil.depth_at(90, 30)
	for i in 100: soil.stamp(Vector3(90, 0, 30), Vector3.BACK, 0.0, 1.0)
	check(is_equal_approx(stopped_depth, soil.depth_at(90, 30)), "Stationary stamping deepened a rut")
	soil.stamp(Vector3(96, 0, 36), Vector3.BACK, 1.0, 0.3)
	for i in 10: soil.stamp(Vector3(100, 0, 36), Vector3.BACK, 0.1, 0.3)
	check(absf(soil.depth_at(96, 36) - soil.depth_at(100, 36)) < 0.001, "Depth depends on travel subdivision")
	print("REPEAT PASSES metres: first=", pass_depths[0], " fifth=", pass_depths[4], " twelfth=", pass_depths[11], " sixteenth=", pass_depths[15])
	# This exact coordinate is shared by four terrain patches. Soil probes sit on
	# open dune sand south of the rocky test trail.
	soil.stamp(Vector3(64, 0, 0), Vector3.FORWARD, 1, 0.5)
	while not soil.pending.is_empty(): soil.update(0.1, true)
	check(soil.tiles.has(Vector2i(11, 7)) and soil.tiles.has(Vector2i(12, 8)), "Stamp crossing tile corners omitted a neighbouring patch")
	check(absf(soil.depth_at(64 - 0.001, 0) - soil.depth_at(64 + 0.001, 0)) < 0.005, "Height field is discontinuous at tile boundary")
	check(soil.uploaded_revision == soil.revision, "Renderer did not receive latest soil revision")
	# Reconstruct the uploaded half-float texture with the same cubic weights.
	# Check off-grid locations too: comparing one raw texel is no longer valid.
	for probe in [Vector2(80, 20), Vector2(80.17, 20.08), Vector2(80.5, 20), Vector2(64.02, -0.03)]:
		var grid: Vector2 = (probe + Vector2.ONE * soil.EXTENT) / soil.CELL
		var cell := Vector2i(floori(grid.x), floori(grid.y))
		var wx: Vector4 = soil.spline_weights(grid.x - cell.x)
		var wz: Vector4 = soil.spline_weights(grid.y - cell.y)
		var reconstructed := 0.0
		for z in 4:
			for x in 4: reconstructed += soil.image.get_pixel(cell.x + x - 1, cell.y + z - 1).r * wx[x] * wz[z]
		check(absf(reconstructed - soil.depth_at(probe.x, probe.y)) < 0.00025, "Uploaded cubic surface differs from wheel support by more than 0.25 mm")
	var worst_kink := 0.0
	for i in range(-3, 4):
		var x: float = 80.0 + float(i) * soil.CELL
		var middle: float = soil.depth_at(x, 20)
		var left: float = (middle - soil.depth_at(x - 0.0005, 20)) / 0.0005
		var right: float = (soil.depth_at(x + 0.0005, 20) - middle) / 0.0005
		worst_kink = maxf(worst_kink, absf(left - right))
	check(worst_kink < 0.02, "Rut slopes have visible steps at the old height-field grid")
	# A stopped car must settle into the four wheel ruts, without excavating further.
	var car = game.get_node("Patrol/Scout01")
	car.place_on_ground(Vector3(70, 0, 10), 0.0)
	car.manual = true
	var supported_y: float = car.position.y
	for contact: Vector3 in car.wheel_contacts:
		for i in 12: soil.stamp(contact, Vector3.FORWARD, 0.2, 0.0)
	var stamped_revision: int = soil.revision
	# Suspension settles with elapsed time; a zero-duration tick must never snap.
	car._physics_process(0.0)
	check(is_equal_approx(car.position.y, supported_y), "A zero-duration tick snapped the chassis to the ground")
	for i in 180: car.simulate(1.0 / 60.0, 0.0, 1.0, 0.0, false)
	check(car.position.y < supported_y - 0.08, "Vehicle suspension ignored deformation beneath its tyres")
	check(soil.revision == stamped_revision, "Stationary tyres excavated without travel")
	var key := InputEventKey.new()
	key.keycode = KEY_P
	key.pressed = true
	game._unhandled_key_input(key)
	for particle in car.find_children("*", "GPUParticles3D", true, false):
		check(is_zero_approx(particle.speed_scale), "Pause left a dust or grain emitter running")
	# Long-lived tread meshes are immutable; only a short tail may be rebuilt.
	var trail_root := Node3D.new()
	root.add_child(trail_root)
	var trail := preload("res://desert_battlefield/track_trail.gd").new()
	var mat := ShaderMaterial.new()
	mat.shader = preload("res://desert_battlefield/tracks.gdshader")
	trail.configure(trail_root, mat)
	for i in 40: trail.append(Vector3(float(i) * 0.3, 0, 0), Vector3.FORWARD)
	var first_mesh: int = trail.chunks.front().mesh.get_instance_id()
	for i in range(40, 60): trail.append(Vector3(float(i) * 0.3, 0, 0), Vector3.FORWARD)
	check(trail.chunks.front().mesh.get_instance_id() == first_mesh, "Old tread geometry was rebuilt")
	for i in range(60, 1000): trail.append(Vector3(float(i) * 0.3, 0, 0), Vector3.FORWARD)
	check(trail.chunks.size() <= trail.MAX_CHUNKS, "Tread chunk cache is unbounded")
	check(trail.generated_vertices < 10000, "Tread work scales with history instead of new travel")
	print("SOIL CHECKS ", "PASS" if failures.is_empty() else failures, " depth=", soil.depth_at(80, 20), " bank=", soil.depth_at(80.75, 20), " slope_kink=", worst_kink, " tiles=", soil.tiles.size(), " cached_trail_chunks=", trail.chunks.size())
	quit(0 if failures.is_empty() else 1)
