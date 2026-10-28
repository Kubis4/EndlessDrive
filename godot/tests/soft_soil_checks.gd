extends SceneTree
## Real collision/vehicle integration, not a cosmetic body offset or forced stop.
var checks := 0
var failures := 0

class SoilTerrain extends DriveTerrain:
	func center_at(_x: float) -> float: return 0.0
	func dry_height(_x: float, _z: float) -> float: return 0.0
	func feature_at(_x: float) -> Dictionary: return {}
	func pools_near(_x: float) -> Array[Dictionary]: return []
	func sample(x: float, z: float, _include_water: bool = true, _visual_blend: bool = false) -> Dictionary:
		var soft := absf(z) > 8.0
		return {"color": Color("857357"), "road": 0.0 if soft else 1.0, "lateral": z,
			"paved": 0.0 if soft else 1.0, "grip": 0.42 if soft else 1.0, "drag": 1.0 if soft else 0.0,
			"water": 0.0, "snow": 0.0, "label": "Blato" if soft else "Asfalt", "roughness": 0.0,
			"soil_softness": 1.0 if soft else 0.0, "soil_bearing": 0.4 if soft else 2.0,
			"soil_depth": 1.1 if soft else 0.0, "rut_depth": deformation.depth_at(x, z)}

class SoilWorld extends DriveWorld:
	func _ready() -> void: pass
	func _process(_dt: float) -> void: pass
	func _soil_snapshot() -> DriveTerrain:
		# Preserve this fixture's saturated material in worker commands. The
		# production snapshot intentionally constructs the standard world profile.
		var base := super._soil_snapshot()
		var soil := SoilTerrain.new()
		soil.deformation = base.deformation
		soil.seed_value = base.seed_value
		return soil

func _initialize() -> void:
	if not "--test-session" in OS.get_cmdline_user_args():
		quit(2)
		return
	call_deferred("_run")

func check(ok: bool, title: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])

func frames(n: int) -> void:
	for i in n:
		await physics_frame
		await process_frame

func _run() -> void:
	for action: String in ["throttle", "brake", "handbrake", "left", "right"]:
		if not InputMap.has_action(action): InputMap.add_action(action)
	var material := {"soil_softness": 1.0, "soil_bearing": 0.4, "soil_depth": 1.1}
	var a := DriveDeformation.new()
	var b := DriveDeformation.new()
	var gentle := DriveDeformation.new()
	for i in 1800: a.load_soil(Vector3.ZERO, material, 1.0, 0.95, 1.0, 1.0 / 60.0)
	for i in 900: b.load_soil(Vector3.ZERO, material, 1.0, 0.95, 1.0, 1.0 / 30.0)
	for i in 1800: gentle.load_soil(Vector3.ZERO, material, 1.0, 0.0, 0.2, 1.0 / 60.0)
	check(a.depth_at(0, 0) > 1.0 and absf(a.depth_at(0, 0) - b.depth_at(0, 0)) < 0.0001, "Soft soil exceeds shallow ruts and settles independently of frame rate")
	check(gentle.depth_at(0, 0) > 0.25 and gentle.depth_at(0, 0) < a.depth_at(0, 0) * 0.6, "Static weight sinks; wheelspin digs much deeper than a gentle pedal")
	var light := DriveDeformation.new()
	var loaded := DriveDeformation.new()
	for i in 480:
		light.load_soil(Vector3.ZERO, material, 0.77, 0.0, 0.0, 1.0 / 60.0)
		loaded.load_soil(Vector3.ZERO, material, 1.60, 0.0, 0.0, 1.0 / 60.0)
	check(loaded.depth_at(0, 0) > light.depth_at(0, 0) * 2.0, "Cargo/poor footprint pressure compacts soft soil more than a light wide-tyred car")
	var restored := DriveDeformation.new()
	restored.restore(a.to_data())
	check(absf(restored.depth_at(0, 0) - a.depth_at(0, 0)) < 0.001, "Metre-deep soil deformation survives save/restore")
	var world := SoilWorld.new()
	world.async_streaming = true
	world.terrain = SoilTerrain.new()
	root.add_child(world)
	# Create the exact same renderer and collision bands used in the game.
	world._create_chunk(0, DriveTerrainBake.bake(world.terrain, 0))
	# This fixture disables the world's process loop. Publish the same terrain
	# jobs explicitly before removing scenery jobs; never drive on missing bands.
	for entry: Dictionary in world._jobs:
		if entry.label in ["terrain_mesh", "collision_bands"]: entry.job.call()
	for band in 3: world._update_rut_band(world.chunks[0], band)
	world._jobs.clear()
	var state := DriveRun.new()
	state.engine_running = true
	state.fuel = 40
	state.parts.engine = 1.0
	state.parts.tires = 0.35
	var car := (load("res://scenes/vehicle.tscn") as PackedScene).instantiate() as DriveVehicle
	car.run = state
	car.world = world
	car.boundary_recovery_enabled = false
	car.position = Vector3(16, 0.6, 18)
	root.add_child(car)
	await frames(20)
	var start_y := car.position.y
	var start_x := car.position.x
	Input.action_press("throttle")
	var max_sink := 0.0
	var hit_belly := false
	for tick in 1800:
		if tick % 12 == 0:
			world.flush_soil_updates()
			for band in 3: world._update_rut_band(world.chunks[0], band)
		await frames(1)
		max_sink = maxf(max_sink, car.sink_depth)
		hit_belly = hit_belly or car.belly_contact
	var end_x := car.position.x
	await frames(120)
	print("SOIL actual descent=%.3f m sink=%.3f m travel=%.2f m speed=%.3f spin=%.2f support=%.2f belly=%s reason=%s" % [start_y - car.position.y, max_sink, end_x - start_x, car.speed, car.soil_slip, car.wheel_support, hit_belly, car.stop_reason])
	check(start_y - car.position.y > 0.20 and max_sink > 0.30, "Physical car chassis descends into deformed collision by over 20 cm")
	check(car.is_on_floor() and car.boundary_recoveries == 0, "Sinking retains real ground support without clamping or recovery teleport")
	check(absf(car.speed) < 0.15 and absf(car.position.x - end_x) < 0.30 and car.soil_slip > 0.7 and car.wheel_speed > 3, "Throttle spins tyres but cannot propel a deeply bogged car")
	check(car.stop_reason in ["bogged", "high_centered"], "Deep soil reports bogging instead of an unexplained instant stop")
	Input.action_release("throttle")
	car.park()
	# Two pre-existing deep wheel channels with an intact centre ridge. A real
	# chassis must rest on the ridge even though all four wheels are above soil.
	var channels: Array = []
	for x in range(10, 25):
		for z in [15, 16, 17, 19, 20, 21]: channels.append([x, z, DriveDeformation.MAX_DEPTH])
	world.terrain.deformation.restore(channels)
	world.deformation_enabled = false
	for band in 3: world._update_rut_band(world.chunks[0], band)
	car.position = Vector3(16, 0.6, 18)
	await frames(60)
	print("BELLY y=%.3f sink=%.3f support=%.3f contact=%s" % [car.position.y, car.sink_depth, car.wheel_support, car.belly_contact])
	# Narrower cylindrical Jeep tyres can still touch an inner rut edge; the
	# belly must genuinely carry the body, but wheel support need not be zero.
	check(car.belly_contact and car.wheel_support < 0.95 and car.position.y > 0.05, "Actual underbody rests on an intact ridge above deep tyre channels")
	# No permanent stuck flag: a solid surface must immediately drive normally.
	Input.action_release("throttle")
	car.park()
	car.position = Vector3(16, 0.6, 0)
	await frames(20)
	Input.action_press("throttle")
	await frames(180)
	check(car.position.x > 25 and car.speed > 5 and car.sink_depth < 0.01, "Firm road supports the same car and allows normal acceleration")
	Input.action_release("throttle")
	# Equipment and load are inputs to the actual force model, not UI labels.
	car.surface_info = world.terrain.sample(20, 18)
	car.sink_depth = 0.25
	car.wheel_support = 1.0
	car.belly_contact = false
	car.soil_slip = 0.0
	state.variants.tires = "road"
	var road_grip := car._soil_traction(1, 0.3, 12, 1)
	state.variants.tires = "allterrain"
	car.soil_slip = 0.0
	var at_grip := car._soil_traction(1, 0.3, 12, 1)
	check(at_grip > road_grip * 1.20, "All-terrain tyres retain more actual tractive force in a rut")
	state.variants.tires = "road"
	var snow_surface := {"grip": 0.20, "paved": 0.0, "snow": 1.0, "label": "Hlboký sneh"}
	var road_snow := state.tyre_grip(snow_surface)
	state.variants.tires = "winter"
	check(state.tyre_grip(snow_surface) > road_snow * 1.5, "Winter compound works in deep shoulder snow, not only a road label")
	print("SOFT SOIL: %d checks; %d failures" % [checks, failures])
	car.queue_free()
	world.queue_free()
	await frames(2)
	quit(0 if failures == 0 else 1)
