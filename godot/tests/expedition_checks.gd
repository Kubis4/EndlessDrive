extends SceneTree
var checks := 0
var failures := 0


func _initialize() -> void: call_deferred("_run")


func frames(count: int) -> void:
	for i in count: await process_frame


func check(ok: bool, label: String) -> void:
	checks += 1
	if ok: print("PASS: " + label)
	else:
		failures += 1
		push_error("FAIL: " + label)


func _run() -> void:
	var upper := DriveTerrain.new()
	var lower := DriveTerrain.new()
	upper.route_choices = {"0": -1}
	lower.route_choices = {"0": 1}
	check(absf(upper.center_at(1200) - lower.center_at(1200)) > 150.0, "Chosen directions remain spatially separate well past the junction")
	check(upper.seed_at(1200) != lower.seed_at(1200) and upper.biome_at(1200) != lower.biome_at(1200), "The route choice changes the future region and procedural world seed")
	for profile: DriveTerrain in [upper, lower]:
		var before := profile.center_at(599.99)
		var after := profile.center_at(600.01)
		check(absf(after - before) < 0.02 and absf(profile.height_at(599.99, before) - profile.height_at(600.01, after)) < 0.05, "Committed junction exit is continuous without a teleport or terrain step")
	var snow := DriveTerrain.new()
	var road := snow.sample(4100, snow.center_at(4100))
	var shoulder := snow.sample(4100, snow.center_at(4100) + 18)
	check(float(road.grip) > float(shoulder.grip) and float(road.drag) < float(shoulder.drag), "Deep snow outside the road is less grippy and harder to cross")
	var run := DriveRun.new()
	run.route_choices = upper.route_choices.duplicate()
	run.weather_state = {"next": 500.0, "edge": 1000.0, "left": 20.0}
	var restored := DriveRun.from_data(run.to_data())
	check(restored != null and restored.route_choices == run.route_choices and restored.weather_state == run.weather_state, "Save preserves the chosen world branch and active storm")
	var garage := DriveProfile.new()
	garage.prepare_run(run)
	check(float(run.parts.engine) < 0.4 and float(run.parts.brakes) < 0.4 and run.fuel < 4, "A new expedition begins with a genuinely worn, poorly supplied car")
	check(not garage.buy("engine"), "Garage cannot buy upgrades without collected scrap")
	run.found_scrap = 12
	run.inventory.scrap = 14
	var old_save := DriveRun.from_data(run.to_data())
	check(garage.deposit(run) == 12 and garage.bank == 12 and run.inventory.scrap == 2, "Depositing transfers only earned scrap, never free starting stock")
	check(garage.deposit(old_save) == 0 and garage.bank == 12, "Reloading an earlier journey cannot duplicate a garage deposit")
	check(garage.buy("engine") and garage.bank == 2, "Purchased garage upgrade deducts its real price")
	var improved := DriveRun.new()
	garage.prepare_run(improved)
	check(float(improved.parts.engine) > float(run.parts.engine) and improved.variants.engine == "efficient", "Purchased upgrade improves the next starting car and variant")
	var path := ProjectSettings.globalize_path("res://../build/godot-tests/garage-profile.json")
	check(garage.store(path) == OK and DriveProfile.restore(path).upgrades == garage.upgrades, "Separate garage progression persists without replacing the journey save")
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	await frames(5)
	check(not game.hud._junction_panel.visible and not game.hud._next_stop.visible, "Exploration no longer reveals upcoming route contents or building distances")
	var world: DriveWorld = game.world
	var lanes := world.terrain.road_centers(480)
	var choice := world.choose_route(Vector3(480, 0, lanes[0]))
	check(not choice.is_empty() and world.terrain.route_choices["0"] == -1, "Driving into an arm commits the physical route rather than a menu selection")
	world.update_stream(480)
	for side: int in [-1, 1]:
		world.async_streaming = false
		world.configure(24601)
		world.terrain.route_choices = {"0": side}
		world.terrain.clear_caches()
		world.update_stream(480)
		var lane_index := 0 if side < 0 else 1
		var z := world.terrain.road_centers(480)[lane_index]
		game.vehicle.park()
		game.vehicle.position = Vector3(480, world.height_at(480, z) + 0.6, z)
		game.vehicle.heading = atan((world.terrain.road_centers(481)[lane_index] - world.terrain.road_centers(479)[lane_index]) * 0.5)
		game.run.engine_running = true
		game.run.parts.engine = 0.9
		game.run.parts.tires = 0.9
		game.run.fuel = 20
		game.run.distance = 480
		var rescues: int = game.vehicle.boundary_recoveries
		world.async_streaming = true
		for frame in 6000:
			var x: float = game.vehicle.position.x
			if x >= 850: break
			var target_x := x + 5.0
			var road_lanes := world.terrain.road_centers(target_x)
			var target_z: float = road_lanes[lane_index] if road_lanes.size() == 2 else road_lanes[0]
			var error := wrapf(atan2(target_z - game.vehicle.position.z, 5.0) - game.vehicle.heading, -PI, PI)
			Input.action_press("right", clampf(error * 5.0, 0.0, 1.0))
			Input.action_press("left", clampf(-error * 5.0, 0.0, 1.0))
			Input.action_press("throttle", 1.0 if game.vehicle.speed < 9.0 else 0.12)
			await process_frame
		for action: String in ["right", "left", "throttle"]: Input.action_release(action)
		check(game.vehicle.position.x >= 850 and game.vehicle.boundary_recoveries == rescues, "Actual vehicle continues beyond the old merge onto permanent route %d with threaded streaming" % side)
		if side < 0:
			var deck_z := world.center_at(744)
			var deck_height := world.height_at(744, deck_z)
			check(deck_height - world.terrain.ground_height_at(744, deck_z) > 5.8 and not world.find_children("BridgeDeck", "MeshInstance3D", true, false).is_empty(), "River flows under a separate physical bridge deck, not a solid terrain causeway")
			var query := PhysicsRayQueryParameters3D.create(Vector3(744, deck_height + 5, deck_z), Vector3(744, deck_height - 8, deck_z))
			query.exclude = [game.vehicle.get_rid()]
			var hit := world.get_world_3d().direct_space_state.intersect_ray(query)
			check(not hit.is_empty() and absf(float(hit.position.y) - deck_height) < 0.08, "Actual bridge collision matches the drivable surface above the river")
	world.async_streaming = false
	game.run.engine_running = false
	game.vehicle.park()
	var building: Dictionary = world.buildings[0] if world.buildings.has(0) else {}
	world.configure(24601)
	world.update_stream(0)
	building = world.buildings[0]
	game.vehicle.park()
	game.vehicle.position = building.position + Vector3.UP * 0.6
	game.weather.storm_active = true
	game.weather._storm_left = 30
	game.weather.storm_edge = game.vehicle.position.x + 20
	var health: float = game.run.parts.radiator
	game.weather._process(1.0)
	check(game.weather.sheltered and game.run.parts.radiator == health, "Parking inside a covered workshop protects the car from the chasing dust storm")
	game.vehicle.position.z = world.center_at(game.vehicle.position.x)
	game.weather._process(1.0)
	check(not game.weather.sheltered and float(game.run.parts.radiator) < health, "Being caught in the dust storm damages an exposed vehicle")
	game.weather.storm_active = false
	world.async_streaming = true
	game.vehicle.park()
	game.vehicle.position = Vector3(96, world.height_at(96, world.center_at(96)) + 0.6, world.center_at(96))
	world.update_stream(96)
	var started := Time.get_ticks_msec()
	while (not world.chunks.has(5) or not world._jobs.is_empty() or world._worker != null) and Time.get_ticks_msec() - started < 10000:
		await process_frame
	check(world.chunks.has(5) and world.last_worker_ms > 0.0, "A live streamed chunk is baked on a worker and installed while the scene keeps processing")
	print("STREAM PERFORMANCE: worker %.2f ms; maximum main-thread streaming slice %.2f ms" % [world.last_worker_ms, world.max_commit_ms])
	check(world.max_commit_ms < 80.0, "Streaming avoids the previous large synchronous whole-chunk build spike")
	world.async_streaming = false
	world._stop_worker()
	world.update_stream(744)
	var crossing := RouteHazard.new()
	crossing.world = world
	crossing.kind = "rail"
	crossing.feature_x = 744
	world.add_child(crossing)
	crossing.build()
	world.clock = 0
	crossing._physics_process(0.01)
	check(crossing._train.collision_layer == 1 and is_zero_approx(crossing._gates[0].rotation.x), "Active crossing has a moving collidable train and a closed physical barrier")
	world.clock = 25
	crossing._physics_process(0.01)
	check(crossing._train.collision_layer == 0 and absf(crossing._gates[0].rotation.x) > 1.0, "The crossing opens after the train has passed")
	check(game.effects._tracks.multimesh.instance_count == 640 and game.effects._emitters.size() == 2, "Tyre imprints and surface sprites use bounded buffers instead of accumulating nodes")
	game.queue_free()
	await frames(2)
	print("EXPEDITION: %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
