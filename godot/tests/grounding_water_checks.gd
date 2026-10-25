extends SceneTree
var failures := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	print("%s %s" % ["PASS" if ok else "FAIL", title])
	if not ok: failures += 1
func _run() -> void:
	var profile := DriveTerrain.new()
	var baked := DriveTerrainBake.bake(profile, 2)
	var depths: PackedFloat32Array = baked.water_depths
	var wet := 0
	var shore := 0
	var error := 0.0
	for i in baked.water.size():
		var p: Vector3 = baked.water[i]
		var bed := DriveTerrainBake.mesh_height(baked.arrays, int(baked.width), p.x, p.z)
		error = maxf(error, absf(maxf(0, p.y - 0.004 - bed) - depths[i]))
		wet += 1 if depths[i] > 0.3 else 0
		shore += 1 if depths[i] < 0.02 else 0
	check(wet > 20 and shore > 20 and error < 0.025, "Water opacity data meets the actual visible bed at the shoreline")
	var pool: Dictionary = profile.pools_near(207)[0]
	var bank := Vector2(207, float(pool.z) + float(pool.rz) * 0.99)
	check(profile.height_at(bank.x, bank.y) > profile.water_level(pool, bank.x, bank.y), "Ford has a real bank above its waterline instead of a floating ellipse edge")
	check(absf(profile.water_depth(207, float(pool.z)) - 0.62) < 0.01, "Shore improvements retain the starter ford's physical wading depth")
	var world := DriveWorld.new()
	root.add_child(world)
	world.batch_scenery = false
	var planted := 0
	var floating := 0
	var outside := 0
	for seed in [24601, 1000004]:
		world.configure(seed)
		for index in range(32, 35): world._create_chunk(index)
		for chunk: Node3D in world.chunks.values():
			for p: Vector3 in chunk.get_meta("vegetation_contacts", PackedVector3Array()):
				planted += 1
				var floor_height := world.rendered_height_at(p.x, p.z)
				if p.y > floor_height - 0.025 or floor_height - p.y > 0.65: floating += 1
				if absf(p.z - world.terrain.corridor_center(p.x)) > world.terrain.boundary_width(p.x) - 6.0: outside += 1
	check(planted > 80 and floating == 0, "Vegetation roots seat in the rendered hairpin triangles across both mountain seeds")
	check(outside == 0, "Vegetation stays within the real terrain corridor when a hairpin has several road centres")
	world.configure(24601)
	world._create_chunk(7)
	var rail := RouteHazard.new()
	rail.world = world
	rail.kind = "rail"
	rail.feature_x = 744
	world.add_child(rail)
	rail.build()
	var sleepers := 0
	var min_clearance := INF
	var rail_center := rail._rail_center()
	for child: Node in rail.get_children():
		if child is MeshInstance3D and str(child.name).begins_with("Sleeper"):
			var top: float = child.position.y + child.scale.y * 0.5
			var ground := world.rendered_height_at(child.position.x, child.position.z)
			if absf(child.position.z - rail_center.z) > 10 and absf(child.position.z - rail_center.z) < 25:
				sleepers += 1
				min_clearance = minf(min_clearance, top - ground)
	check(sleepers > 6 and min_clearance > 0.1, "Sleepers sit above the graded rail bed without terrain z-fighting")
	check(rail.has_node("RailBallast") and rail.get_children().any(func(n: Node) -> bool: return str(n.name).begins_with("CrossingPanel")), "Crossing has ballast and flush road infill around the rails")
	check(not world.terrain.sample(744, world.center_at(744)).deformable, "The solid crossing cannot be excavated underneath its panels")
	world.queue_free()
	await process_frame
	print("GROUNDING AND WATER failures=", failures, " water error=", error, " roots=", planted)
	quit(1 if failures else 0)
