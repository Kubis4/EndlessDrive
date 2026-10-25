extends SceneTree
## Real contacts and motion for selectable drive ranges, fords and winching.
var failures := 0
var checks := 0
var world: DriveWorld
var car: DriveVehicle
var state: DriveRun

class TestTerrain extends DriveTerrain:
	var test_water_depth := 0.0
	func sample(_x: float, _z: float, _include_water: bool = true) -> Dictionary:
		return {"road": 1.0, "grip": 1.0, "drag": 0.0, "roughness": 0.0, "paved": 1.0, "label": "Asfalt", "water": test_water_depth}
class TestWorld extends DriveWorld:
	func _ready() -> void: pass
	func height_at(_x: float, _z: float) -> float: return 0.0
	func center_at(_x: float) -> float: return 0.0

func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])
func frames(count: int) -> void:
	for tick in count:
		await physics_frame
		await process_frame
func reset_car() -> void:
	Input.action_release("throttle")
	car.park()
	car.heading = 0
	car.position = Vector3(10, 0.6, 0)
	car.rotation = Vector3.ZERO
	car.reset_physics_interpolation()
	state.engine_running = false
	await frames(30)

func _run() -> void:
	for action: String in ["throttle", "brake", "left", "right"]:
		if not InputMap.has_action(action): InputMap.add_action(action)
	state = DriveRun.new()
	var saved := state.to_data()
	check(DriveRun.from_data(saved).four_wheel_drive, "Legacy/default run starts with 4×4")
	state.four_wheel_drive = true
	state.differential_locked = true
	var restored := DriveRun.from_data(state.to_data())
	check(restored != null and restored.four_wheel_drive and restored.differential_locked, "Drive settings survive a save round-trip")
	saved.erase("four_wheel_drive")
	saved.erase("differential_locked")
	check(DriveRun.from_data(saved).four_wheel_drive and not DriveRun.from_data(saved).differential_locked, "Old saves migrate to unlocked 4×4")
	saved.four_wheel_drive = "false"
	check(DriveRun.from_data(saved) == null, "Malformed drivetrain save is rejected")
	state.differential_locked = false
	state.four_wheel_drive = false
	var rwd := state.drive_speed_limit()
	state.four_wheel_drive = true
	var awd := state.drive_speed_limit()
	state.differential_locked = true
	check(rwd > awd and awd > state.drive_speed_limit(), "Rear drive is fastest; locked low range is slowest")
	var air := DriveRun.new()
	var water := DriveRun.new()
	air.temperature = 100
	water.temperature = 100
	for tick in 120:
		air.advance(1.0 / 60.0, 5, 0, false)
		water.advance(1.0 / 60.0, 5, 0, false, false, 0, 0.5)
	check(water.temperature < air.temperature - 1.5 and is_equal_approx(water.coolant, air.coolant), "Ford cools a hot radiator gradually without refilling coolant")
	var unlocked := DriveRun.new()
	var locked := DriveRun.new()
	locked.differential_locked = true
	unlocked.advance(100, 7, 0.5, false)
	locked.advance(100, 7, 0.5, false)
	check(float(locked.parts.tires) < float(unlocked.parts.tires) and float(locked.parts.suspension) < float(unlocked.parts.suspension), "Locked driving wears tyres and suspension faster on firm roads")
	world = TestWorld.new()
	world.terrain = TestTerrain.new()
	world.deformation_enabled = false
	root.add_child(world)
	world.set_process(false)
	var floor_mesh := MeshInstance3D.new()
	floor_mesh.name = "SandAndTracks"
	var plane := PlaneMesh.new()
	plane.size = Vector2(5000, 120)
	floor_mesh.mesh = plane
	world.add_child(floor_mesh)
	floor_mesh.create_trimesh_collision()
	car = DriveVehicle.new()
	car.world = world
	car.run = state
	car.boundary_recovery_enabled = false
	root.add_child(car)
	await reset_car()
	car.speed = 6
	var before := state.four_wheel_drive
	car.toggle_drive_mode()
	check(state.four_wheel_drive == before, "Drive selector refuses an unsafe high-speed shift")
	car.speed = 0
	state.differential_locked = false
	state.four_wheel_drive = false
	car.toggle_differential()
	check(state.differential_locked and state.four_wheel_drive, "Engaging axle lock selects 4×4")
	car.toggle_drive_mode()
	check(not state.four_wheel_drive and not state.differential_locked, "Selecting rear drive releases the axle lock")
	car.surface_info = {"soil_softness": 0.8, "snow": 0.0}
	car.sink_depth = 0.20
	var traction: Array[float] = []
	for mode in 3:
		state.four_wheel_drive = mode != 0
		state.differential_locked = mode == 2
		car.soil_slip = 0
		traction.append(car._soil_traction(1.0 / 60.0, 0.45, 8.0, 1.0))
	check(traction[0] < traction[1] and traction[1] < traction[2], "Four driven tyres and axle lock materially improve mud traction")
	var speeds: Array[float] = []
	for mode in 3:
		await reset_car()
		state.four_wheel_drive = mode != 0
		state.differential_locked = mode == 2
		state.fuel = 40
		state.engine_running = true
		for slot: String in state.parts: state.parts[slot] = 1.0
		Input.action_press("throttle")
		await frames(600)
		speeds.append(car.speed)
		check(car.speed <= state.drive_speed_limit() + 0.05 and car.speed > state.drive_speed_limit() * 0.80, "Actual full-throttle drive respects range %d speed" % mode)
	print("DRIVE RANGE SPEEDS ", speeds)
	check(speeds[0] > speeds[1] + 3 and speeds[1] > speeds[2] + 5, "Real RWD/4×4/locked speeds differ materially")
	await reset_car()
	car.speed = 10
	await frames(60)
	var dry_speed := car.speed
	await reset_car()
	(world.terrain as TestTerrain).test_water_depth = 0.5
	car.speed = 10
	await frames(60)
	check(car.speed < dry_speed - 2, "Real ford entry slows a coasting car more than dry road")
	(world.terrain as TestTerrain).test_water_depth = 0.0
	await reset_car()
	var from := car.position
	car.winch_pull_velocity = Vector3(100, 100, 0)
	await frames(60)
	check(car.position.x > from.x + 0.5 and car.position.x < from.x + 2.5 and car.position.y < 0.7, "Winch pulls through bounded horizontal physics without lifting or teleporting")
	car.park()
	check(car.winch_pull_velocity == Vector3.ZERO, "Parking clears transient winch pull")
	# A positive-Z bank must raise the positive-Z tyre row. Reversing this
	# rotation sign leaves the car perched on only two load-bearing tyres.
	floor_mesh.rotation.x = -atan(0.2)
	car.position = Vector3(10, 2.3, 8)
	car.reset_physics_interpolation()
	await frames(90)
	print("BANK CONTACT roll=%.3f support=%.3f visual_gap=%.3f" % [car.body_roll, car.wheel_support, car.max_wheel_contact_error])
	check(car.body_roll < -0.12 and car.max_wheel_contact_error < 0.16, "Cross-slope sign puts all four visual tyres on the real bank")
	state.engine_running = true
	car.speed = 6
	Input.action_press("right", 0.40)
	Input.action_press("throttle", 0.60)
	var bank_gap := 0.0
	for tick in 120:
		await frames(1)
		bank_gap = maxf(bank_gap, car.max_wheel_contact_error)
	print("BANK TURN tyre_gap=%.3f heading=%.2f support=%.3f" % [bank_gap, car.heading, car.wheel_support])
	check(bank_gap < 0.22 and car.is_on_floor() and car.wheel_support > 0.6, "Offroad turning keeps four tyres supported instead of riding one side")
	Input.action_release("right")
	Input.action_release("throttle")
	car.queue_free()
	world.queue_free()
	await frames(2)
	print("DRIVETRAIN: %d checks, %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
