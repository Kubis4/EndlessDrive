extends SceneTree
## Random track generation, material tracks, weather restore and physical 100+ km slices.
var failures := 0
var checks := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])
func frames(n: int) -> void:
	for tick in n:
		await physics_frame
		await process_frame
func _run() -> void:
	var game := (load("res://scenes/main.tscn") as PackedScene).instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	var car: DriveVehicle = game.vehicle
	var world: DriveWorld = game.world
	car.controls_enabled = false
	var a := DriveTerrain.new()
	var b := DriveTerrain.new()
	a.seed_value = DriveTerrain.VARIED_SEED_MIN + 3
	b.seed_value = DriveTerrain.VARIED_SEED_MIN + 37
	check(a.section_shape(8) != b.section_shape(8) and a.section_shape(8) == a.section_shape(8), "New seeds generate distinct repeatable curves and hills")
	var sand: Dictionary = game.effects.track_style({"material": DriveTerrain.SurfaceKind.SAND, "snow": 0.0})
	var soil: Dictionary = game.effects.track_style({"material": DriveTerrain.SurfaceKind.GRASS, "snow": 0.0})
	var snow: Dictionary = game.effects.track_style({"material": DriveTerrain.SurfaceKind.SNOW, "snow": 1.0})
	check(sand.kind != soil.kind and soil.kind != snow.kind and sand.color != soil.color and soil.color != snow.color, "Sand, exposed earth on grass and compressed snow have distinct tracks")
	game.weather.forced_mode = "Dážď"
	game.weather._process(20)
	var moist: float = game.weather.wetness
	game.weather.forced_mode = "Jasno"
	game.weather._process(1)
	check(game.weather.wetness > 0 and game.weather.wetness < moist, "Wet ground dries gradually after a passing rain front")
	var saved := DriveRun.from_data(game.run.to_data())
	check(saved != null and saved.weather_state == game.run.weather_state, "Front timing, wetness and precipitation survive journey serialization")
	var resumed_weather := DriveWeather.new()
	resumed_weather.run = saved
	resumed_weather.world = world
	resumed_weather.vehicle = car
	game.add_child(resumed_weather)
	resumed_weather.set_process(false)
	check(resumed_weather.wetness == game.weather.wetness and resumed_weather._front_until == game.weather._front_until, "A newly restored weather controller resumes the same wet ground/front")
	resumed_weather.queue_free()
	game.weather.forced_mode = ""
	var first: int = game.weather._front_serial
	game.run.elapsed = game.weather._front_until + 1
	game.weather._process(0.1)
	check(game.weather._front_serial > first and game.weather._front_until > game.run.elapsed + 100, "Dynamic front advances with a seeded variable duration")
	var state := DriveRun.new()
	state.fuel = 40
	state.engine_running = true
	state.oil = 4
	state.coolant = 6
	for slot: String in state.parts: state.parts[slot] = 1.0
	for tick in 1600: state.advance(0.5, 12, 0.5, false, false, 0.3)
	check(state.mechanical_events == 1 and state.parts.brakes < 1 and state.parts.alternator < 1 and state.parts.radiator < 1, "Long-distance wear covers all components and triggers a service event")
	var restored := DriveRun.from_data(state.to_data())
	check(restored != null and restored.wear_distance == state.wear_distance and restored.mechanical_events == state.mechanical_events, "Wear progress is persistent and cannot reset its fault schedule on reload")
	var wreck := state.open_loot(-3000002)
	var cache := state.open_loot(-2000002)
	check(wreck.any(func(item: Dictionary) -> bool: return item.kind == "part") and cache.any(func(item: Dictionary) -> bool: return item.kind == "repairkit"), "Wrecks and supply caches provide real collectable parts/tools")
	var before_repair := float(state.parts.engine)
	state.repair_at_shop(-3000002, "engine")
	check(float(state.parts.engine) == before_repair, "An abandoned wreck cannot masquerade as a professional repair shop")
	world.hazards_enabled = true
	world.configure(24601)
	world.update_stream(270)
	await frames(2)
	var source := world.supply_source(0)
	var searchable := world.nearest_building(world._point(float(source.x), float(source.offset)))
	check(not searchable.is_empty() and int(searchable.id) == int(source.id), "A naturally streamed roadside source is actually searchable through the normal interaction")
	world.hazards_enabled = false
	# Generated terrain and real physics near 10, 50, 100 and 120 km. These are
	# physical slices; journey_balance separately checks complete resource planning.
	game.origin_rebasing_enabled = true
	game.route_decisions_enabled = false
	game.weather.forced_mode = "Jasno"
	for region in [10, 52, 104, 125]:
		car.park()
		world.async_streaming = false
		world.configure(DriveTerrain.VARIED_SEED_MIN + 3)
		for choice in region: world.terrain.route_choices[str(choice)] = -1 if choice % 2 == 0 else 1
		world.terrain.clear_caches()
		var start_x: float = region * 960.0 + 20.0
		world.update_stream(start_x)
		var z := world.center_at(start_x)
		car.position = Vector3(start_x, world.height_at(start_x, z) + 0.6, z)
		car.heading = atan((world.center_at(start_x + 1) - world.center_at(start_x - 1)) * 0.5)
		game.run.world_seed = world.world_seed
		game.run.route_choices = world.terrain.route_choices.duplicate()
		game.run.fuel = 40
		game.run.oil = 4
		game.run.coolant = 6
		for slot: String in game.run.parts: game.run.parts[slot] = 1.0
		game._rebase_world(true)
		await frames(20)
		car.controls_enabled = true
		game.run.engine_running = true
		for tick in 240:
			var look := 5.0
			var target_z := world.center_at(car.position.x + look)
			var error := wrapf(atan2(target_z - car.position.z, look) - car.heading, -PI, PI)
			Input.action_press("throttle", 0.7)
			Input.action_press("right", clampf(error * 4, 0, 1))
			Input.action_press("left", clampf(-error * 4, 0, 1))
			await frames(1)
		var physical := car.global_position
		check(car.position.x > start_x + 10 and world.chunks.size() == 7 and Vector2(physical.x, physical.z).length() < 3000 and car.boundary_recoveries == 0, "Real generated terrain drives with a bounded floating origin at %.0f km" % (start_x / 1000))
		print("LONG SLICE route=%.2f physical=%s roll=%.3f pitch=%.3f speed=%.2f" % [car.position.x, physical, car.body_roll, car.body_pitch, car.speed])
		car.controls_enabled = false
		for action: String in ["throttle", "left", "right"]: Input.action_release(action)
	game.queue_free()
	await frames(2)
	print("LONG EXPEDITION: %d checks, %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
