extends SceneTree
## Rendered check of Lesná vrchovina: the valley from above, then a chase view
## at each test spot after a short drive.
## godot --path . --resolution 1600x900 --script res://offroad/capture_highlands.gd
const H = preload("res://offroad/highlands.gd")
var game

func _initialize() -> void: call_deferred("run")

func shot(name: String) -> void:
	for i in 4: await process_frame
	await RenderingServer.frame_post_draw
	var path := ProjectSettings.globalize_path("res://build/highlands-" + name + ".png")
	root.get_texture().get_image().save_png(path)
	print("CAPTURE ", path)

func run() -> void:
	root.size = Vector2i(1600, 900)
	game = (load("res://offroad/highlands.tscn") as PackedScene).instantiate()
	root.add_child(game)
	await process_frame
	game.lead.set_physics_process(false)
	game._set_camera_mode(0)
	game.camera_size = 400.0
	game.focus = Vector3(0, 0, 0)
	game.lead.place_on_ground(Vector3(0, 0, 0), 0.0)
	game.camera_snap = true
	await shot("overview")
	var spots: Array = game.test_spots
	for i in spots.size():
		game.teleport_to_test(i)
		game._set_camera_mode(2)
		var start: Vector3 = game.lead.position
		for f in 150:
			await physics_frame
			game.lead.simulate(1.0 / 60.0, 0.55, 0.0, 0.0, false)
		print("SPOT %s moved=%.1f m speed=%d km/h ground=%s water=%.2f" % [spots[i][0], game.lead.position.distance_to(start), roundi(game.lead.speed_kmh), game.Surface.SURFACE_NAMES[game.lead.wheel_surface[0]], game.lead.water_submersion])
		await shot("spot%d" % (i + 1))
	# Ruts on grass and the forest road by the camp: a curve, then a look from above.
	game.teleport_to_test(0)
	var revision: int = game.soil.revision
	var deepest := {}
	for f in 300:
		await physics_frame
		game.lead.simulate(1.0 / 60.0, 0.4, 0.0, 0.85, false)
		# Rut depth just behind the rear tyres, per ground crossed.
		for w in [1, 3]:
			var c: Vector3 = game.lead.wheel_contacts[w]
			var ground: String = game.Surface.SURFACE_NAMES[game.lead.wheel_surface[w]]
			deepest[ground] = maxf(deepest.get(ground, 0.0), game.soil.depth_at(c.x, c.z))
	for f in 30: await process_frame
	print("RUTS soil edits=%d deepest per ground=%s" % [game.soil.revision - revision, deepest])
	game._set_camera_mode(6)
	game.orbit_pitch = 1.05
	game.follow_distance[6] = 11.0
	await shot("ruts")
	# Weather at the camp in the chase view: downpour, then snow, then frost.
	var weather = game.test_grounds.weather
	game.teleport_to_test(0)
	game._set_camera_mode(2)
	for f in 90:
		await physics_frame
		game.lead.simulate(1.0 / 60.0, 0.5, 0.0, 0.3, false)
	for look: Array in [["rain", 3, 0.92, 1.0, 0.0, 1.0, 0.0, 0.0], ["snow-early", 5, 0.95, 0.0, 0.9, 0.2, 0.4, 0.8], ["snow", 5, 0.95, 0.0, 0.9, 0.2, 1.0, 0.8], ["frost", 4, 0.2, 0.0, 0.0, 0.9, 0.0, 1.0]]:
		weather._enter(look[1])
		weather.cloud = look[2]
		weather.rain = look[3]
		weather.snowfall = look[4]
		weather.temperature = weather.STATES[look[1]][4]
		weather.wetness = look[5]
		weather.snow_cover = look[6]
		weather.frost = look[7]
		# A downpour has swollen the river.
		H.flood = H.MAX_FLOOD if look[0] == "rain" else 0.0
		weather._publish()
		weather._visuals()
		for f in 60: await process_frame
		print("WEATHER %s grip(grass)=%.2f" % [weather.weather_text(), game.Surface.grip(game.Surface.GRASS)])
		await shot("weather-" + look[0])
	quit()
