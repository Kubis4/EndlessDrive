extends SceneTree
var failures := 0
var checks := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, message: String) -> void:
	checks += 1
	print("%s %s" % ["PASS" if ok else "FAIL", message])
	if not ok: failures += 1
func frames(count := 2) -> void:
	for i in count:
		await physics_frame
		await process_frame
func _run() -> void:
	var terrain := DriveTerrain.new()
	var forks := 0
	for region in 40:
		check(str(terrain.junction_style(region).layout) != "roundabout", "Generated junction %d has no roundabout" % region)
		if not terrain.fork_at(region * 960 + 470).is_empty(): forks += 1
	check(forks >= 8 and forks < 18, "Forks are occasional instead of appearing in every region")
	var previous := 42.0
	var stop_spacing := true
	for district in range(1, 20):
		var stop := DriveStops.next_stop(district * DriveStops.REGION_LENGTH, 24601)
		stop_spacing = stop_spacing and float(stop.x) - previous > 1800 and float(stop.x) - previous < 2050
		previous = float(stop.x)
	check(stop_spacing, "Buildings are roughly 1.9 km apart, with deterministic random placement")
	var path := terrain.mountain_path(3)
	var backwards := 0.0
	var max_elevation := 0.0
	var matches := true
	var width_error := 0.0
	for i in path.size() - 1:
		var delta := Vector2(path[i + 1].x - path[i].x, path[i + 1].z - path[i].z)
		backwards += maxf(0, -delta.x)
		max_elevation = maxf(max_elevation, path[i].y - terrain.base_height(path[i].x))
		if delta.length() < 0.01: continue
		var p := path[i].lerp(path[i + 1], 0.5)
		matches = matches and absf(terrain.height_at(p.x, p.z) - p.y) < 0.20 and absf(terrain.road_distance(p.x, p.z)) < 0.01
		var side := Vector2(-delta.y, delta.x).normalized() * 3.0
		width_error = maxf(width_error, absf(absf(terrain.road_distance(p.x + side.x, p.z + side.y)) - 3.0))
	check(backwards > 100 and max_elevation > 40, "Mountain route genuinely doubles back and climbs over 40 metres")
	check(matches and width_error < 0.25, "Hairpin elevation and road width follow the same continuous physical path")
	var long_mud := false
	var deep_ford := false
	for section in range(7, 180):
		var event := terrain.road_event(section)
		if not event.is_empty() and str(event.kind) == "mud":
			var x := float(event.x) + 22
			long_mud = long_mud or (float(event.length) >= 32 and terrain.event_influence(event, x, terrain.center_at(x)) > 0.20)
		for pool: Dictionary in terrain.pools_near(section * 240.0 + 120):
			if pool.get("deep", false): deep_ford = deep_ford or terrain.water_depth(float(pool.x), float(pool.z) + 1) > 0.85
	check(long_mud, "Mud obstacles extend along bends for several car lengths")
	check(deep_ford, "Later fords contain physically deeper pockets")
	check(terrain.authored_rut_depth(180, terrain.center_at(180) + 1.15) > 0.04, "Loose roads contain pre-existing physical wheel channels")
	var stock := DriveRun.new()
	stock.engine_running = true
	for i in 600: stock.advance(1.0 / 60, 1, 0.4, true, false, 0, 1.20)
	check(stock.water_ingress >= 1 and not stock.engine_running, "Deep water floods an unprotected intake and stalls the engine")
	stock.toggle_engine()
	check(not stock.engine_running, "A flooded engine cannot be restarted underwater")
	stock.drain_intake()
	check(stock.water_ingress >= 1, "Intake drying is blocked while still submerged")
	stock.advance(0.1, 0, 0, false, false, 0, 0)
	stock.drain_intake()
	stock.toggle_engine()
	check(stock.water_ingress == 0 and stock.engine_running, "Recovering and drying the intake makes engine restart possible")
	var protected := DriveRun.new()
	protected.inventory.scrap = 20
	protected.install_snorkel(0)
	protected.engine_running = true
	for i in 600: protected.advance(1.0 / 60, 1, 0.4, true, false, 0, 1.20)
	check(protected.snorkel_installed and protected.engine_running and protected.water_ingress == 0 and protected.inventory.scrap == 6, "Installed snorkel protects the intake and has a real workshop cost")
	protected.high_beams = true
	var restored := DriveRun.from_data(protected.to_data())
	check(restored != null and restored.snorkel_installed and restored.high_beams, "New equipment and lighting preferences survive existing save validation")
	var game = load("res://scenes/main.tscn").instantiate()
	game._ephemeral = true
	root.add_child(game)
	game.set_process(false)
	game.weather.set_process(false)
	await frames(12)
	var car: DriveVehicle = game.vehicle
	var camp: Dictionary = game.world.supply_source(3)
	check(camp.get("camp", false) and not camp.wreck, "Mountain summit replaces the ordinary roadside cache with a ridge campsite")
	game.world._create_supply_source(game.world.chunks[0], camp)
	var camp_position: Vector3 = game.world._point(float(camp.x), float(camp.offset))
	var found_camp: Dictionary = game.world.nearest_building(camp_position)
	check(not found_camp.is_empty() and int(found_camp.id) == int(camp.id), "Ridge campsite supplies use the real search interaction")
	car.heading = PI
	game.weather.start_sandstorm(0)
	check(game.weather.storm_center.x < car.position.x, "A front spawns ahead even while the car is travelling towards decreasing X")
	game.weather.reset_weather()
	car.heading = 0.0
	game.run.engine_running = true
	car.speed = 7
	car.cruise_enabled = true
	car.cruise_amount = 0.7
	car.set_touch("handbrake", true, 401)
	await frames(100)
	check(absf(car.speed) < 0.2 and not car.cruise_enabled and not car._reverse_engaged, "Holding handbrake stops the real car without engaging reverse or cruise")
	car.clear_inputs()
	car.set_touch("brake", true, 402)
	await frames(70)
	check(car.speed < -0.1 and car._reverse_materials[0].emission_energy_multiplier > 0 and car._reverse_lights[0].visible, "Actual reversing enables white rear lenses and ground illumination")
	car.park()
	car.set_physics_process(false)
	game.run.headlights = true
	game.run.high_beams = false
	car._update_visual(0.016, 0)
	var low_range := car._lights[0].spot_range
	game._action("high_beams")
	car._update_visual(0.016, 0)
	check(car._lights[0].spot_range > low_range and car._lights.size() == 2 and not car._lights[0].shadow_enabled, "High beams extend existing lamps without adding shadow lights")
	var weather: DriveWeather = game.weather
	var weather_ok := true
	for variant in 3:
		weather.start_sandstorm(variant)
		weather.storm_warning = 0
		weather._storm_left = 55
		car.position.x = weather.storm_center.x
		car.position.z = weather.storm_center.y
		for i in 30:
			game.run.elapsed += 0.2
			weather._process(0.2)
		weather_ok = weather_ok and weather.storm_visibility > 0.25
		car.position.z += 400
		for i in 20:
			game.run.elapsed += 0.2
			weather._process(0.2)
		weather_ok = weather_ok and weather.storm_visibility < 0.05
		for i in 120:
			game.run.elapsed += 1
			weather._process(1)
		weather_ok = weather_ok and not weather.storm_active and weather._next_storm > game.run.elapsed + 150
	check(weather_ok, "All three finite storm directions allow entry, exit and a long clear interval afterwards")
	game._update_lighting()
	check(not game.environment.fog_enabled, "Clear weather restores an unobscured view")
	game.queue_free()
	await frames()
	print("ADVENTURE SYSTEMS %d checks; %d failures" % [checks, failures])
	quit(1 if failures else 0)
