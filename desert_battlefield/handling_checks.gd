extends SceneTree
## Exercise the same force integrator used by live controls, including a real dune.
const Surface = preload("res://desert_battlefield/terrain.gd")
var failures: Array[String] = []
var car
var game: Node3D

func _initialize() -> void: call_deferred("run")

func check(condition: bool, message: String) -> void:
	if not condition: failures.append(message)

func flat_height(_x: float, _z: float) -> float: return 0.0

func minimum_gap() -> float:
	var result := INF
	for gap: float in car.wheel_gaps: result = minf(result, gap)
	return result

func prepare_flat(payload: float = 0.0) -> void:
	car.height_sampler = flat_height
	car.payload_kg = payload
	car.collision_mask = 0
	car.place_on_ground(Vector3(0, 0, 80), 0.0)
	car.longest_flight = 0.0
	car.landing_count = 0

func drive(seconds: float, throttle: float, brake: float = 0.0, turn: float = 0.0, handbrake: bool = false, hz: int = 60) -> void:
	for i in roundi(seconds * hz):
		car.simulate(1.0 / hz, throttle, brake, turn, handbrake)

func flight_probe(throttle: float, turn: float) -> Array:
	prepare_flat()
	car.position.y += 4.0
	car.velocity = Vector3(0, 0, -8)
	car.angular_velocity = Vector3(0.1, 0.2, 0)
	var start_y: float = car.position.y
	var revision: int = game.soil.revision
	var samples: int = car.samples.size()
	drive(0.25, throttle, 0.0, turn)
	check(not car.grounded and car.grounded_wheels == 0, "Flying tyres retained ground grip")
	check(absf(car.velocity.y + 9.81 * 0.25) < 0.03, "Airborne vertical acceleration is not gravity")
	check(absf(car.position.y - (start_y - 0.5 * 9.81 * 0.25 * 0.25)) < 0.025, "Ballistic trajectory was snapped to terrain")
	check(game.soil.revision == revision and car.samples.size() == samples, "Flying wheels excavated or drew tracks")
	check(not car.trail.has_previous, "A jump did not break the tread ribbon")
	for particle in car.find_children("*", "GPUParticles3D", true, false):
		check(not particle.emitting, "Airborne tyres emitted ground dust")
	return [car.position, car.velocity, car.heading, car.pitch]

func dune_pass(target_speed: float) -> Dictionary:
	car.payload_kg = 0.0
	car.height_sampler = Surface.height_at
	car.collision_mask = 0
	car.place_on_ground(Vector3(-25, 0, 45), -PI * 0.5)
	car.velocity = -car.chassis_basis().z * target_speed
	car.longest_flight = 0.0
	car.landing_count = 0
	var clearance := 0.0
	var takeoff_x := -1000.0
	var launch := Vector3.ZERO
	var penetration := 0.0
	var takeoff_y := 0.0
	var rise := 0.0
	var first_flight := true
	var ballistic_rise := 0.0
	for frame in 480:
		var pedal := clampf((target_speed - car.speed) * 0.7, 0.0, 1.0)
		car.simulate(1.0 / 60.0, pedal, 0.0, 0.0, false)
		clearance = maxf(clearance, minimum_gap() - car.droop)
		if car.air_time > 0 and first_flight:
			takeoff_y = car.position.y
			first_flight = false
			ballistic_rise = maxf(car.velocity.y, 0.0) ** 2 / (2 * car.GRAVITY)
		if not first_flight: rise = maxf(rise, car.position.y - takeoff_y)
		if car.air_time > 0.1 and takeoff_x == -1000.0:
			takeoff_x = car.position.x
			launch = car.velocity
		check(car.position.is_finite() and car.velocity.is_finite(), "Dune traversal became non-finite")
		penetration = minf(penetration, car.hull_clearance())
		if car.landing_count > 0 and car.longest_flight > 0.15 and frame > 60: break
	return {"flight": car.longest_flight, "clearance": clearance, "landings": car.landing_count, "takeoff_x": takeoff_x, "end": car.position, "launch": launch, "penetration": penetration, "rise": rise, "ballistic_rise": ballistic_rise}

func run() -> void:
	game = (load("res://desert_battlefield/desert_battlefield.tscn") as PackedScene).instantiate()
	root.add_child(game)
	game.set_process(false)
	for vehicle in game.get_node("Patrol").get_children():
		vehicle.set_physics_process(false)
		vehicle.collision_layer = 0
	car = game.get_node("Patrol/Scout01")
	car.manual = true
	await physics_frame
	prepare_flat()
	var unloaded_height: float = car.position.y
	drive(3.0, 1.0)
	var light_speed: float = car.speed
	check(light_speed > 7.0, "Engine did not accelerate the vehicle")
	prepare_flat(700.0)
	check(car.position.y < unloaded_height - 0.06, "Payload did not compress suspension")
	drive(3.0, 1.0)
	var heavy_speed: float = car.speed
	check(heavy_speed < light_speed * 0.85, "Mass did not reduce acceleration for the same motor")
	print("MASS acceleration at 3s: light=", light_speed, " heavy=", heavy_speed)
	prepare_flat()
	car.velocity = Vector3(0, 0, -20)
	drive(4.0, 0.0, 1.0)
	check(absf(car.speed) < 0.4, "Braking did not stop the car or reversed it")
	prepare_flat()
	car.velocity = Vector3(0, 0, -10)
	var reverse_key := InputEventKey.new()
	reverse_key.keycode = KEY_S
	reverse_key.physical_keycode = KEY_S
	reverse_key.pressed = true
	Input.parse_input_event(reverse_key)
	Input.flush_buffered_events()
	await physics_frame
	check(Input.is_physical_key_pressed(KEY_S), "Synthetic reverse key was not delivered")
	for i in 30: car._physics_process(1.0 / 60.0)
	check(car.speed > 0.0 and car.speed < 9.0, "Reverse pedal bypassed braking while moving forward")
	prepare_flat()
	for i in 90: car._physics_process(1.0 / 60.0)
	check(car.speed < -1.0, "Held reverse pedal did not engage reverse from a stop")
	var release_key := reverse_key.duplicate() as InputEventKey
	release_key.pressed = false
	Input.parse_input_event(release_key)
	Input.flush_buffered_events()
	await physics_frame
	prepare_flat()
	drive(3.0, -1.0)
	check(car.speed < -1.0 and car.speed >= -6.1, "Reverse acceleration/limit failed")
	prepare_flat()
	for i in 1800:
		car.simulate(1.0 / 60.0, 1.0, 0.0, 0.0, false)
		# Keep the long speed-limit probe away from the finite map's recovery edge.
		car.position.z = 80
	# Loose sand caps the reachable speed (terrain.gd SPEED); the motor must still reach it.
	check(car.speed > car.ground_speed_limit() - 2.5 and car.speed > 20.0 and car.speed < car.top_speed + 0.1, "Power curve or top speed is invalid")
	print("TOP SPEED km/h=", car.speed_kmh)
	prepare_flat()
	car.velocity = Vector3(0, 0, -12)
	drive(0.8, 0.3, 0.0, 0.7)
	var grip_slip: float = car.slip_amount
	check(car.heading > 0.02, "Grounded front tyres did not turn the vehicle")
	check(absf(car.roll) > 0.005, "Lateral forces did not roll the sprung body")
	prepare_flat()
	car.velocity = Vector3(0, 0, -12)
	drive(0.8, 0.3, 0.0, 0.7, true)
	check(car.slip_amount > grip_slip, "Handbrake did not reduce rear tyre grip")
	var passive := flight_probe(0.0, 0.0)
	var powered := flight_probe(1.0, 1.0)
	check((passive[0] as Vector3).distance_to(powered[0]) < 0.00001, "Throttle or steering propelled the car in mid-air")
	check(absf(float(passive[2]) - float(powered[2])) < 0.00001, "Steering changed airborne yaw")
	drive(3.0, 0.0, 1.0)
	check(car.grounded and car.landing_count >= 1, "Dropped car did not land")
	check(absf(car.velocity.y) < 0.4, "Landing oscillation did not damp out")
	check(minimum_gap() >= -car.bump_travel - 0.002, "Dropped car crossed the surface")
	var endpoints: Array[Vector3] = []
	for hz in [30, 60, 120]:
		prepare_flat()
		drive(3.0, 1.0, 0.0, 0.2, false, hz)
		endpoints.append(car.position)
	check(endpoints[0].distance_to(endpoints[2]) < 0.02 and endpoints[1].distance_to(endpoints[2]) < 0.02, "Handling depends on the outer tick rate")
	var slow := dune_pass(6.0)
	var medium := dune_pass(16.0)
	print("DUNE moderate=", medium)
	var fast := dune_pass(24.0)
	print("DUNE slow=", slow, " fast=", fast)
	check(fast.penetration >= -0.01 and medium.penetration >= -0.01 and slow.penetration >= -0.01, "Dune landing penetrated the hull")
	check(fast.rise <= fast.ballistic_rise + 0.04 and medium.rise <= medium.ballistic_rise + 0.04, "Dune flight rose beyond its ballistic energy")
	check(medium.rise < 1.6, "Moderate approach produced an excessive jump")
	check(fast.flight > 0.2 and fast.clearance > 0.25, "Fast real-dune traversal did not launch a visible jump")
	check(fast.landings > 0, "Dune jump did not land again")
	check(fast.clearance > slow.clearance + 0.15, "Dune takeoff did not respond to approach speed")
	# Real swept collision against a thin wall at speed, not a distance clamp.
	prepare_flat()
	var wall := StaticBody3D.new()
	wall.collision_layer = 1
	var collider := CollisionShape3D.new()
	var box := BoxShape3D.new()
	box.size = Vector3(20, 12, 0.15)
	collider.shape = box
	wall.add_child(collider)
	root.add_child(wall)
	wall.position = Vector3(0, 5, 70)
	await physics_frame
	await physics_frame
	car.collision_mask = 1
	car.velocity = Vector3(0, 0, -28)
	drive(0.7, 0.0)
	check(car.position.z > 71.0, "High-speed chassis sweep passed through a thin obstacle")
	check(car.speed > -2.0 and car.speed < 2.0, "Impact did not dissipate closing velocity")
	wall.queue_free()
	# Reset is the only deliberate teleport and must clear momentum and trails.
	car.reset_to_route()
	check(car.velocity.is_zero_approx() and car.angular_velocity.is_zero_approx(), "Reset retained flight momentum")
	check(car.previous_contacts.is_empty() and not car.trail.has_previous, "Reset retained a contact bridge")
	print("HANDLING CHECKS ", "PASS" if failures.is_empty() else failures)
	quit(0 if failures.is_empty() else 1)
