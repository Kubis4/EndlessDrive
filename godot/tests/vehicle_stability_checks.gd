extends SceneTree
## Physical bank-to-flat recovery and a live floating-origin crossing.
var failures := 0
class BankTerrain extends DriveTerrain:
	func center_at(_x: float) -> float: return 0.0
	func road_centers(_x: float) -> PackedFloat32Array: return PackedFloat32Array([0.0])
	func dry_height(x: float, z: float) -> float: return z * 0.20 * (1.0 - smoothstep(40.0, 70.0, x))
	func pools_near(_x: float) -> Array[Dictionary]: return []
	func feature_at(_x: float) -> Dictionary: return {}
	func fork_at(_x: float) -> Dictionary: return {}
	func sample(_x: float, z: float, _include_water: bool = true) -> Dictionary:
		return {"color": Color("777777"), "material": SurfaceKind.ASPHALT, "road": 1.0, "grip": 1.0, "drag": 0.0, "roughness": 0.0, "paved": 1.0, "lateral": z, "label": "Asfalt", "water": 0.0, "snow": 0.0, "soil_softness": 0.0, "soil_depth": 0.0, "deformable": false}
func _initialize() -> void: call_deferred("_run")
func frames(n: int) -> void:
	for tick in n:
		await physics_frame
		await process_frame
func check(ok: bool, title: String) -> void:
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])
func _run() -> void:
	var game := (load("res://scenes/main.tscn") as PackedScene).instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	var world: DriveWorld = game.world
	var car: DriveVehicle = game.vehicle
	world.terrain = BankTerrain.new()
	world.configure(24601)
	world.update_stream(0)
	world.deformation_enabled = false
	for chunk: Node3D in world.chunks.values():
		for node: Node in chunk.get_children():
			if node.name != "SandAndTracks": node.queue_free()
	car.park()
	car.position = Vector3(15, world.height_at(15, 16) + 0.6, 16)
	car.heading = 0
	car.reset_physics_interpolation()
	game.run.fuel = 40
	game.run.oil = 4
	game.run.coolant = 6
	for slot: String in game.run.parts: game.run.parts[slot] = 1.0
	game.weather.forced_mode = "Jasno"
	await frames(35)
	var bank_roll := absf(car.body_roll)
	game.run.engine_running = true
	car.speed = 22.0
	var flat_z := INF
	for tick in 300:
		Input.action_press("throttle", 0.65 if car.speed < 22 else 0.2)
		await frames(1)
		if car.position.x > 85 and is_inf(flat_z): flat_z = car.position.z
	print("BANK RECOVERY x=%.2f initial roll=%.3f final roll=%.3f pitch=%.3f lateral=%.3f" % [car.position.x, bank_roll, car.body_roll, car.body_pitch, car._lateral_speed])
	check(bank_roll > 0.12 and car.position.x > 85 and absf(car.body_roll) < 0.025 and absf(car._visual.rotation.x - car._visual_rest_rotation.x) < 0.025, "High-speed bank exit levels the physical chassis and model promptly")
	check(is_finite(flat_z) and absf(car.position.z - flat_z) < 0.4, "Flat ground does not retain a one-sided drift after the bank")
	Input.action_press("right", 0.5)
	await frames(12)
	Input.action_release("right")
	await frames(24)
	check(absf(car._yaw_rate) < 0.03, "Released steering quickly stops its residual yaw")
	# Test the actual production rebase during motion, not a teleport between slices.
	car.park()
	car.position = Vector3(2500, 0.6, 0)
	car.heading = 0
	game.route_origin = Vector3.ZERO
	game.position = Vector3.ZERO
	game.origin_rebasing_enabled = true
	world.update_stream(2500)
	for chunk: Node3D in world.chunks.values():
		for node: Node in chunk.get_children():
			if node.name != "SandAndTracks": node.queue_free()
	var debris := RigidBody3D.new()
	debris.position = Vector3(2570, 0.6, 12)
	debris.mass = 22
	var debris_shape := CollisionShape3D.new()
	debris_shape.shape = BoxShape3D.new()
	debris.add_child(debris_shape)
	world.add_child(debris)
	await frames(20)
	game.run.engine_running = true
	car.speed = 20
	var previous := car.position
	var maximum_step := 0.0
	for tick in 300:
		Input.action_press("throttle", 0.2)
		await frames(1)
		maximum_step = maxf(maximum_step, car.position.distance_to(previous))
		previous = car.position
	print("ORIGIN CROSSING x=%.3f origin=%s physical=%s maximum_step=%.4f recoveries=%d speed=%.2f" % [car.position.x, game.route_origin, car.global_position, maximum_step, car.boundary_recoveries, car.speed])
	check(game.route_origin.x > 0 and car.position.x > 2570 and maximum_step < 1.0 and car.boundary_recoveries == 0 and absf(car.global_position.x) < 2500, "Moving origin keeps logical progress continuous without a rescue teleport")
	check(absf(world.to_local(debris.global_position).x - 2570) < 0.1, "Dynamic roadside bodies retain their route position through a live rebase")
	Input.action_release("throttle")
	game.queue_free()
	await frames(2)
	quit(0 if failures == 0 else 1)
