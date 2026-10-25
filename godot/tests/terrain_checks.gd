class_name TerrainChecks
extends RefCounted


static func run(game, suite) -> void:
	var world: DriveWorld = game.world
	var vehicle: DriveVehicle = game.vehicle
	var profile := world.terrain
	var second := DriveTerrain.new()
	second.seed_value = profile.seed_value
	suite.check(is_equal_approx(profile.height_at(1500, 10), second.height_at(1500, 10)), "Terrain and hazards reproduce from the same seed")
	second.seed_value += 47
	suite.check(absf(profile.center_at(800) - second.center_at(800)) > 0.5, "Different seeds produce distinct road layouts")
	var biomes: Dictionary = {}
	var surfaces: Dictionary = {}
	for i in 6:
		var x := i * DriveTerrain.REGION + 720.0
		biomes[profile.biome_at(x)] = true
		surfaces[profile.sample(x, profile.center_at(x)).label] = true
	suite.check(biomes.size() == 6 and surfaces.size() == 6, "Six distinct regions and road materials are reachable")
	var continuous := true
	var smooth_physics := true
	for i in range(1, 28):
		var x := i * 240.0
		var a := profile.height_at(x - 0.01, profile.center_at(x - 0.01))
		var b := profile.height_at(x + 0.01, profile.center_at(x + 0.01))
		continuous = continuous and absf(a - b) < 0.03 and absf(profile.center_at(x + 0.01) - profile.center_at(x - 0.01)) < 0.04
		var sa := profile.sample(x - 0.01, profile.center_at(x - 0.01))
		var sb := profile.sample(x + 0.01, profile.center_at(x + 0.01))
		var ca: Color = sa.color
		var cb: Color = sb.color
		smooth_physics = smooth_physics and absf(float(sa.grip) - float(sb.grip)) < 0.01 and Vector3(ca.r - cb.r, ca.g - cb.g, ca.b - cb.b).length() < 0.01
	suite.check(continuous, "Section and biome borders have no height or curve steps")
	suite.check(smooth_physics, "Biome transitions blend rendering and tyre friction continuously")
	var maximum := -INF
	var minimum := INF
	var turns := 0.0
	for i in range(480, 720, 4):
		maximum = maxf(maximum, profile.base_height(i))
		minimum = minf(minimum, profile.base_height(i))
	for i in range(240, 480, 4):
		turns = maxf(turns, absf(profile.center_at(i + 1) - profile.center_at(i - 1)) * 0.5)
	# Graded benches retain elevation changes without forcing a steep saddle.
	var max_grade := 0.0
	for i in range(480, 720): max_grade = maxf(max_grade, absf(profile.base_height(i + 0.5) - profile.base_height(i - 0.5)))
	suite.check(maximum - minimum > 3.0 and max_grade < 0.25 and turns > 0.3, "Generated hills and bends change road geometry while retaining a driveable grade")
	var hole_depth := 0.0
	for section in 200:
		var event := profile.road_event(section)
		if event.is_empty() or event.kind != "potholes": continue
		# Three staggered holes: the middle hole (i=1) is one metre to +Z.
		var actual := profile.height_at(float(event.x), float(event.z) + 1.0)
		profile._events[section] = {}
		hole_depth = profile.height_at(float(event.x), float(event.z) + 1.0) - actual
		profile._events[section] = event
		break
	suite.check(hole_depth > 0.20, "Seeded potholes physically depress the road surface")
	var wz := profile.center_at(207) + 1.0
	var wet := profile.sample(207, wz)
	var dry := profile.sample(207, wz - 6.0)
	suite.check(float(wet.water) > 0.4 and float(wet.drag) > float(dry.drag) + 1.0 and float(wet.grip) < float(dry.grip), "Wading water has real depth, lower traction and greater rolling resistance")
	var fork_profile := DriveTerrain.new()
	fork_profile.seed_value = 24600 # The Y layout owns the optional ford.
	var lanes := fork_profile.road_centers(480)
	var style := fork_profile.junction_style(0)
	var ford_branch := 0 if int(style.ford_side) < 0 else 1
	suite.check(lanes.size() == 2 and lanes[1] - lanes[0] > 40.0, "Junction creates two distinct physical road branches")
	suite.check(int(fork_profile.sample(480, lanes[0]).material) == int(style.upper_material) and int(fork_profile.sample(480, lanes[1]).material) == int(style.lower_material), "Each seeded junction branch has its advertised road material")
	suite.check(float(fork_profile.sample(480, lanes[ford_branch]).water) > 0.4 and float(fork_profile.sample(480, lanes[1 - ford_branch]).water) == 0.0, "Only the seeded ford branch crosses deep water")
	suite.check(fork_profile.road_centers(360)[0] == fork_profile.road_centers(360)[1] and fork_profile.road_centers(599)[1] - fork_profile.road_centers(599)[0] > 80.0, "Junction arms diverge instead of rejoining the same road")
	# A genuine physics proof: explicitly disable all out-of-map clamping/recovery.
	vehicle.park()
	vehicle.boundary_recovery_enabled = false
	game.run.engine_running = false
	var recoveries := vehicle.boundary_recoveries
	for x: float in [88.0, 96.0, 104.0, 192.0, 480.0]:
		world.update_stream(x)
		await suite.frames(2)
		for side: float in [-1.0, 1.0]:
			vehicle.park()
			game.run.distance = x
			var z := profile.corridor_center(x) + side * (profile.boundary_width(x) - 12.0)
			vehicle.position = Vector3(x, profile.height_at(x, z) + (5.0 if x == 104 else 0.6), z)
			vehicle.heading = side * PI * 0.5
			vehicle.speed = 42.0
			await suite.frames(90)
			var offset := absf(vehicle.position.z - profile.corridor_center(vehicle.position.x))
			suite.check(offset < profile.boundary_width(vehicle.position.x) - 3.0 and vehicle.position.y > profile.height_at(vehicle.position.x, vehicle.position.z) - 1.0,
				"Real rock collision stops %s travel at x=%.0f without fallback" % ["upper" if side < 0 else "lower", x])
	suite.check(vehicle.boundary_recoveries == recoveries, "Boundary collision tests did not use a single rescue teleport")
	# This fixture specifically exercises the optional Y-layout ford.
	world.configure(24600)
	game.route_decisions_enabled = false
	# Drive all the way through each fork with a small test-only steering controller.
	# The real throttle/steering inputs, engine, terrain collision and water drag stay active.
	for branch in 2:
		world.update_stream(450)
		vehicle.park()
		game.run.distance = 430
		game.run.fuel = 20
		game.run.engine_running = true
		game.run.parts.engine = 0.9
		game.run.parts.tires = 0.9
		var z := profile.road_centers(430)[branch]
		vehicle.position = Vector3(430, profile.height_at(430, z) + 0.6, z)
		vehicle.heading = atan((profile.road_centers(431)[branch] - profile.road_centers(429)[branch]) * 0.5)
		await suite.frames(5)
		var max_water := 0.0
		var stayed_safe := true
		for frame in 3600:
			var x := vehicle.position.x
			if x >= 545.0: break
			var target_x := minf(598.0, x + 5.0)
			var target_z := profile.road_centers(target_x)[branch]
			var desired := atan2(target_z - vehicle.position.z, target_x - x)
			var error := wrapf(desired - vehicle.heading, -PI, PI)
			Input.action_press("right", clampf(error * 4.0, 0.0, 1.0))
			Input.action_press("left", clampf(-error * 4.0, 0.0, 1.0))
			Input.action_press("throttle", 1.0 if vehicle.speed < 10.0 else 0.12)
			await suite.frames(1)
			max_water = maxf(max_water, float(vehicle.surface_info.get("water", 0.0)))
			stayed_safe = stayed_safe and vehicle.position.y > profile.height_at(vehicle.position.x, vehicle.position.z) - 1.0
		Input.action_release("right")
		Input.action_release("left")
		Input.action_release("throttle")
		suite.check(vehicle.position.x >= 545.0 and stayed_safe and vehicle.boundary_recoveries == recoveries,
			"Starter vehicle physically traverses fork %d without rescue" % branch)
		if branch == ford_branch:
			suite.check(max_water > 0.35, "Seeded ford drive really passes through deep water instead of bypassing it")
	game.run.engine_running = false
	# Raycast the actual collision mesh, including both branches, a pothole and water bed.
	for x: float in [150.0, 207.0, 480.0, 960.0, 3840.0]:
		world.update_stream(x)
		vehicle.park()
		game.run.distance = x
		vehicle.position = Vector3(x - 70, 20, profile.center_at(x - 70))
		await suite.frames(2)
		for z: float in profile.road_centers(x):
			var height := profile.height_at(x, z)
			var query := PhysicsRayQueryParameters3D.create(Vector3(x, height + 20, z), Vector3(x, height - 5, z))
			query.exclude = [vehicle.get_rid()]
			var hit := world.get_world_3d().direct_space_state.intersect_ray(query)
			suite.check(not hit.is_empty() and absf(float(hit.position.y) - height) < 0.25, "Road physics matches shared terrain profile at x=%.0f z=%.1f" % [x, z])
	vehicle.boundary_recovery_enabled = true
	world.update_stream(480)
	vehicle.park()
	game.run.distance = 480
	vehicle.position = Vector3(480, profile.height_at(480, lanes[ford_branch]) + 0.6, lanes[ford_branch])
	await suite.frames(5)
	suite.check(not game.hud._junction_panel.visible and not game.hud._next_stop.visible, "Exploration HUD hides route and upcoming-building predictions")
	suite.check(float(profile.sample(vehicle.position.x, vehicle.position.z).water) > 0.0 and not game.hud._road_info.get_parent().visible, "Water remains physically active while verbose surface descriptions stay hidden")
	game.hud.toast("Kontrola bezpečnej cesty.")
	await suite.frames(2)
	suite.check(not game.hud._toast_panel.get_global_rect().intersects(game.hud._junction_panel.get_global_rect()), "Driving messages never obscure junction choices")
