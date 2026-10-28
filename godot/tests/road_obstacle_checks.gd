extends SceneTree
const Layout = preload("res://scripts/road_obstacle_layout.gd")
var failures := 0
var checks := 0
class UnbrokenRoad extends DriveTerrain:
	func pothole_depth(_event: Dictionary, _x: float, _z: float) -> float: return 0.0

func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	checks += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])
	if not ok: failures += 1

func _run() -> void:
	var profile := DriveTerrain.new()
	var totals: Dictionary = {}
	var chosen := {}
	var holes := 0
	var hole_fixture := {}
	var challenging := 0
	var wet_bypasses := 0
	var tree_coverage := {}
	var tree_angles := {}
	var partial_fixture := {}
	for seed in [24601, 1000001, 1000002, 1000003, 1000004, 1000005, 1000006]:
		profile.seed_value = seed
		profile.clear_caches()
		var safe := true
		var count := 0
		for region in 8:
			var sites := profile.obstacles_in_region(region)
			safe = safe and sites.size() <= 2
			for site: Dictionary in sites:
				count += 1
				totals[site.kind] = true
				safe = safe and site.bypass.size() == 17 and site.basis.is_finite()
				for point: Vector3 in site.bypass:
					var water := profile.water_depth(point.x, point.z)
					safe = safe and water <= Layout.MAX_BYPASS_WATER
					if water > 0.10: wet_bypasses += 1
				if float(site.challenge) > 0.25: challenging += 1
				if site.kind == "fallen_tree":
					safe = safe and site.grove.size() >= 2
					tree_coverage[site.tree_coverage] = true
					tree_angles[roundi(rad_to_deg(float(site.tree_yaw)) / 10.0)] = true
					if site.tree_coverage == "partial" and partial_fixture.is_empty(): partial_fixture = {"seed": seed, "site": site.duplicate(true)}
				if site.kind == "fallen_tree" and chosen.is_empty(): chosen = {"seed": seed, "site": site.duplicate(true)}
		check(safe, "Obstacles keep off-road bypasses with bounded water and a forest context for seed %d (%d sites)" % [seed, count])
		for section in 24:
			var event := profile.road_event(section)
			if event.get("kind", "") != "potholes": continue
			holes += 1
			var asphalt := profile.biome_at(float(event.x)) == 2
			if hole_fixture.is_empty() or (asphalt and not hole_fixture.get("asphalt", false)):
				hole_fixture = {"seed": seed, "event": event.duplicate(true), "asphalt": asphalt}
			var x := float(event.x)
			var z := profile.center_at(x) + 1.4
			var depth := profile.pothole_depth(event, x, z)
			check(depth > 0.12 and depth <= 0.4, "Potholes have bounded physical depth for seed %d section %d" % [seed, section])
	check(totals.size() == 4 and holes > 4, "Several seeds actually generate all obstacle types and potholes")
	check(challenging > 0, "Natural soft terrain remains present on obstacle detours")
	check(wet_bypasses > 0, "Shallow water is allowed on naturally generated detours")
	check(tree_coverage.size() == 3 and tree_angles.size() >= 6, "Seeded trees span across-road, angled and partial-road positions with diverse yaw")
	print("DETOUR CHALLENGE soft_sites=", challenging, " wet_samples=", wet_bypasses)
	if not hole_fixture.is_empty():
		check(bool(hole_fixture.asphalt), "Physical pothole fixture lies on an actual asphalt road")
		var broken := DriveTerrain.new()
		var unbroken := UnbrokenRoad.new()
		broken.seed_value = hole_fixture.seed
		unbroken.seed_value = hole_fixture.seed
		var index := floori(float(hole_fixture.event.x) / 96)
		var baked := DriveTerrainBake.bake(broken, index)
		var actual_cut := 0.0
		for band: PackedVector3Array in baked.collision_bands:
			for point: Vector3 in band:
				actual_cut = maxf(actual_cut, unbroken.ground_height_at(point.x, point.z) - point.y)
		check(actual_cut > 0.10 and actual_cut <= 0.41, "Streamed collision triangles contain the actual pothole depression")
		print("POTHOLE FIXTURE seed=", hole_fixture.seed, " x=", hole_fixture.event.x, " physical_cut=", actual_cut)
	check(not chosen.is_empty(), "A naturally generated fallen tree exists")
	if chosen.is_empty():
		quit(1)
		return
	profile.seed_value = chosen.seed
	profile.clear_caches()
	var original := profile.obstacles_in_region(floori(float(chosen.site.x) / 960)).duplicate(true)
	profile.clear_caches()
	check(original == profile.obstacles_in_region(floori(float(chosen.site.x) / 960)), "Obstacle layout regenerates identically after cache eviction")
	var dug: Vector3 = chosen.site.bypass[8]
	profile.deformation._store(Vector2i(floori(dug.x / DriveDeformation.CELL_SIZE), floori(dug.z / DriveDeformation.CELL_SIZE)), 0.8)
	profile.clear_caches()
	check(original == profile.obstacles_in_region(floori(float(chosen.site.x) / 960)), "Previously excavated soil cannot reroll the world's obstacles")
	print("FALLEN TREE FIXTURE seed=", chosen.seed, " x=", chosen.site.x, " bypass=", chosen.site.bypass_side)
	var world := DriveWorld.new()
	root.add_child(world)
	var batch_chunk := Node3D.new()
	world.add_child(batch_chunk)
	for kind: String in Layout.KINDS:
		for copy in 2:
			var model := (load("res://assets/models/obstacles/" + kind + ".tscn") as PackedScene).instantiate() as Node3D
			batch_chunk.add_child(model)
			var colliders := model.find_children("*", "CollisionShape3D", true, false).size()
			world._batch_visuals(model, batch_chunk)
			check(model.find_children("*", "MeshInstance3D", true, false).is_empty() and model.find_children("*", "CollisionShape3D", true, false).size() == colliders and colliders > 0, "%s instance %d batches without losing physics or leaving duplicate nodes" % [kind, copy])
	batch_chunk.queue_free()
	await process_frame
	world.configure(chosen.seed)
	world.hazards_enabled = true
	world.set_process(false)
	var chunk_index := floori(float(chosen.site.x) / 96)
	for index in range(chunk_index - 1, chunk_index + 2): world._create_chunk(index)
	await physics_frame
	await process_frame
	var node: Node3D
	for obstacle: Node in get_nodes_in_group("road_obstacle"):
		if world.is_ancestor_of(obstacle) and int(obstacle.get_meta("obstacle_site").id) == int(chosen.site.id): node = obstacle
	check(node != null and node.find_children("*", "CollisionShape3D", true, false).size() >= 1, "Streamed fallen tree has real physical colliders")
	var companions := 0
	var planted_cells := {}
	for chunk: Node3D in world.chunks.values():
		for tree: Dictionary in chunk.get_meta("tree_sites", []):
			var p: Vector3 = tree.position
			var origin: Vector3 = chosen.site.position
			if Vector2(p.x - origin.x, p.z - origin.z).length() < 46: companions += 1
			planted_cells[preload("res://scripts/tree_scatter.gd").cell(p)] = true
	check(companions >= 2, "The actual streamed fallen tree stands beside other living trees")
	var planted_grove := 0
	for p: Vector3 in chosen.site.grove:
		if planted_cells.has(preload("res://scripts/tree_scatter.gd").cell(p)): planted_grove += 1
	check(planted_grove >= 2, "Planned companion-tree cells are physically populated across chunk boundaries")
	if node != null:
		var shape := BoxShape3D.new()
		shape.size = Vector3(4.7, 1.4, 1.95)
		var query := PhysicsShapeQueryParameters3D.new()
		query.shape = shape
		query.collision_mask = 3
		var clear := true
		for point: Vector3 in chosen.site.bypass:
			query.transform = Transform3D(Basis.IDENTITY, point + Vector3.UP * 1.25)
			for hit: Dictionary in world.get_world_3d().direct_space_state.intersect_shape(query, 32):
				if node.is_ancestor_of(hit.collider): clear = false
		check(clear, "A car-sized body clears the entire generated bypass around the actual tree")
		var p: Vector3 = node.global_position
		var ray := PhysicsRayQueryParameters3D.create(p + Vector3(-3, 0.5, 0), p + Vector3(3, 0.5, 0), 3)
		var hit := world.get_world_3d().direct_space_state.intersect_ray(ray)
		check(not hit.is_empty() and node.is_ancestor_of(hit.collider), "The fallen trunk blocks straight-through driving rather than being decorative")
		await _drive_bypass(world, chosen.site.bypass)
	if not partial_fixture.is_empty():
		world.configure(partial_fixture.seed)
		var partial_site: Dictionary = partial_fixture.site
		var partial_index := floori(float(partial_site.x) / 96)
		for index in range(partial_index - 1, partial_index + 2): world._create_chunk(index)
		await physics_frame
		await process_frame
		var model: Node3D
		for obstacle: Node in get_nodes_in_group("road_obstacle"):
			if world.is_ancestor_of(obstacle) and int(obstacle.get_meta("obstacle_site").id) == int(partial_site.id): model = obstacle
		var expected := (partial_site.tree_basis as Basis).scaled(Vector3.ONE * float(partial_site.tree_scale))
		check(model != null and model.basis.is_equal_approx(expected), "The actual tree and its colliders use the planned angle, tilt and uniform size")
		var points: Array[Vector3] = []
		for i in 17:
			var x := float(partial_site.x) - 18.0 + i * 2.25
			var z := world.center_at(x)
			var frame := world.terrain.road_frame(x, z)
			var across := Vector3(-float(frame.slope), 0, 1).normalized()
			var point := Vector3(x, 0, z) + across * float(partial_site.bypass_side) * 2.7
			point.y = world.height_at(point.x, point.z)
			points.append(point)
		print("PARTIAL TREE FIXTURE seed=", partial_fixture.seed, " x=", partial_site.x, " yaw=", rad_to_deg(float(partial_site.tree_yaw)))
		await _drive_bypass(world, points, "A real 4x4 passes the open lane beside a tree occupying only part of the road")
	world.queue_free()
	await process_frame
	print("ROAD OBSTACLES: %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)

func _drive_bypass(world: DriveWorld, points: Array, title: String = "A real 4x4 physically drives around the fallen tree without impact or rescue") -> void:
	for action: String in ["left", "right", "throttle", "brake", "handbrake"]:
		if not InputMap.has_action(action): InputMap.add_action(action)
	var run := DriveRun.new()
	for key: String in run.parts: run.parts[key] = 0.95
	run.engine_running = true
	run.four_wheel_drive = true
	var car := load("res://scenes/vehicle.tscn").instantiate() as DriveVehicle
	car.run = run
	car.world = world
	car.boundary_recovery_enabled = false
	car.position = points[0] + Vector3.UP * 0.65
	var initial: Vector3 = points[1] - points[0]
	car.heading = atan2(initial.z, initial.x)
	root.add_child(car)
	for step in 8:
		await physics_frame
		await process_frame
	var waypoint := 1
	for tick in 3600:
		var target: Vector3 = points[waypoint]
		if Vector2(target.x - car.position.x, target.z - car.position.z).length() < 3.2:
			waypoint += 1
			if waypoint >= points.size(): break
			target = points[waypoint]
		var desired := atan2(target.z - car.position.z, target.x - car.position.x)
		var error := wrapf(desired - car.heading, -PI, PI)
		Input.action_press("right", clampf(error * 3.0, 0, 1))
		Input.action_press("left", clampf(-error * 3.0, 0, 1))
		Input.action_press("throttle", 0.9 if car.speed < 5.0 else 0.1)
		Input.action_press("brake", 0.25 if car.speed > 6.5 else 0.0)
		await physics_frame
		await process_frame
	for action: String in ["left", "right", "throttle", "brake"]: Input.action_release(action)
	print("BYPASS DRIVE waypoints=%d/%d speed=%.2f impacts=%d recoveries=%d position=%s" % [waypoint, points.size(), car.speed, car.obstacle_impacts, car.boundary_recoveries, car.position])
	check(waypoint == points.size() and car.boundary_recoveries == 0 and car.obstacle_impacts == 0, title)
	car.queue_free()
	await process_frame
