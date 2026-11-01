extends SceneTree
## Rendered check of the highlands water: the overview at the start, then a
## chase view crossing the ford (wake, spray), the glacial stream, the muddy
## river, the forest pond, and the ford in a downpour. Also measures how far
## the current carries a car standing in the river.
## godot --path . --resolution 1600x900 --script res://offroad/capture_water.gd
const H = preload("res://offroad/highlands.gd")
var game

func _initialize() -> void: call_deferred("run")

func shot(name: String) -> void:
	for i in 4: await process_frame
	await RenderingServer.frame_post_draw
	var path := ProjectSettings.globalize_path("res://build/water-" + name + ".png")
	root.get_texture().get_image().save_png(path)
	print("CAPTURE ", path)

func drive(frames: int, throttle: float) -> void:
	for f in frames:
		await physics_frame
		game.lead.simulate(1.0 / 60.0, throttle, 0.0, 0.0, false)

## Place the car at the river's edge at z, facing across it.
func river_spot(z: float, side: float) -> void:
	var x := H.river_x(z) - side * 14.0
	game.lead.place_on_ground(Vector3(x, 0, z), -PI * 0.5 * side)
	game.lead.reset_physics_interpolation()

func run() -> void:
	root.size = Vector2i(1600, 900)
	game = (load("res://offroad/highlands.tscn") as PackedScene).instantiate()
	root.add_child(game)
	await process_frame
	game.lead.set_physics_process(false)
	await shot("start")
	game.teleport_to_test(1)
	game._set_camera_mode(2)
	await drive(150, 0.6)
	print("FORD speed=%d km/h submersion=%.2f" % [roundi(game.lead.speed_kmh), game.lead.water_submersion])
	await shot("ford-wake")
	for look: Array in [["glacial", 205.0], ["muddy", -205.0]]:
		river_spot(look[1], -1.0)
		game._set_camera_mode(0)
		game.camera_size = 30.0
		await drive(10, 0.0)
		await shot(look[0])
	game.teleport_to_test(11)
	game._set_camera_mode(2)
	await drive(200, 0.5)
	print("POND submersion=%.2f ground=%s" % [game.lead.water_submersion, game.Surface.surface_name(game.lead.wheel_surface[0])])
	await shot("pond")
	# The current: a car stopped in the mountain stream drifts downstream.
	var z := 190.0
	game.lead.place_on_ground(Vector3(H.river_x(z), 0, z), PI * 0.5)
	game.lead.reset_physics_interpolation()
	var start: Vector3 = game.lead.position
	await drive(180, 0.0)
	print("CURRENT drift in 3 s=%.2f m (dz=%.2f) submersion=%.2f" % [game.lead.position.distance_to(start), game.lead.position.z - start.z, game.lead.water_submersion])
	# Downpour over the ford.
	var weather = game.test_grounds.weather
	weather._enter(3)
	weather.cloud = 1.0
	weather.rain = 1.0
	weather.wetness = 1.0
	weather._publish()
	weather._visuals()
	game.teleport_to_test(1)
	game._set_camera_mode(2)
	await drive(100, 0.5)
	await shot("rain")
	quit()
