extends SceneTree
## Independent energy, gravity and roof/side support regressions.
var failures: Array[String] = []
var car
var game

func _initialize() -> void: call_deferred("run")
func flat(_x: float, _z: float) -> float: return 0.0
func check(ok: bool, message: String) -> void:
	if not ok: failures.append(message)
func reset() -> void:
	car.height_sampler = flat
	car.payload_kg = 0.0
	car.place_on_ground(Vector3(0, 0, 60), 0.0)
func step(seconds: float) -> void:
	for i in roundi(seconds * 120): car.simulate(1.0 / 120.0, 0, 0, 0, false)
func energy() -> float:
	var spin: Vector3 = car.attitude.transposed() * car.angular_velocity
	var total: float = car.mass_kg * (0.5 * car.velocity.length_squared() + car.GRAVITY * car.position.y)
	total += 0.5 * spin.dot(car.inertia_diagonal() * spin)
	for i in 4:
		if car.wheel_reachable[i]:
			var compression: float = clampf(car.droop - car.wheel_gaps[i], 0, car.droop + car.bump_travel)
			total += 0.5 * car.spring_rate * compression * compression
	# Anti-roll bar potential energy per axle (left/right compression difference).
	for axle in 2:
		var left: float = clampf(car.droop - car.wheel_gaps[axle], 0, car.droop + car.bump_travel) if car.wheel_reachable[axle] else 0.0
		var right: float = clampf(car.droop - car.wheel_gaps[axle + 2], 0, car.droop + car.bump_travel) if car.wheel_reachable[axle + 2] else 0.0
		total += 0.5 * car.anti_roll_rate * (left - right) * (left - right)
	return total

func run() -> void:
	game = load("res://desert_battlefield/desert_battlefield.tscn").instantiate()
	root.add_child(game)
	game.set_process(false)
	for vehicle in game.get_node("Patrol").get_children():
		vehicle.set_physics_process(false)
		vehicle.collision_layer = 0
	car = game.get_node("Patrol/Scout01")
	car.collision_mask = 0
	car.manual = true
	await physics_frame
	var falls: Array[float] = []
	for payload in [0.0, 700.0]:
		reset()
		car.payload_kg = payload
		car.position.y = 10.0
		step(0.5)
		falls.append(car.velocity.y)
	check(absf(falls[0] - falls[1]) < 0.01 and absf(falls[0] + 4.905) < 0.01, "Gravity depends on payload or is not 9.81 m/s2")
	# Both vehicle masses receive equal/opposite impact impulses.
	reset()
	var other = game.get_node("Patrol/Scout02")
	other.height_sampler = flat
	other.place_on_ground(Vector3(0, 0, 55), 0.0)
	car.velocity = Vector3(0, 0, -10)
	var initial_momentum: Vector3 = car.velocity * car.mass_kg
	var initial_kinetic: float = 0.5 * car.mass_kg * car.velocity.length_squared()
	car._vehicle_collision(other, (car.global_position + other.global_position) * 0.5, Vector3.BACK)
	var final_momentum: Vector3 = car.velocity * car.mass_kg + other.velocity * other.mass_kg
	check(final_momentum.distance_to(initial_momentum) < 0.1, "Car collision did not conserve linear momentum")
	check(other.velocity.z < -1.0, "A struck car did not receive the impact")
	check(0.5 * car.mass_kg * car.velocity.length_squared() + 0.5 * other.mass_kg * other.velocity.length_squared() <= initial_kinetic, "Car collision added kinetic energy")
	# Coasting over a real dune must not gain mechanical energy from contacts.
	reset()
	car.height_sampler = Callable(game.soil, "height_at")
	car.place_on_ground(Vector3(-25, 0, 45), -PI * 0.5)
	car.velocity = -car.attitude.z * 24.0
	var initial := energy()
	var peak := initial
	for frame in 600:
		car.simulate(1.0 / 120.0, 0, 0, 0, false)
		peak = maxf(peak, energy())
	check(peak < initial * 1.02, "Unpowered dune added mechanical energy")
	print("ENERGY dune initial_J=", initial, " peak_J=", peak, " final_J=", energy())
	# A grounded body beyond its tipping point must fall over without a scripted
	# animation or an angular clamp, and settle on a side or the roof.
	reset()
	car.attitude = Basis(Vector3.BACK, deg_to_rad(105))
	car.position.y = 3.0
	car.angular_velocity = Vector3(0, 0, 2.0)
	var inverted := false
	var deepest := 0.0
	for frame in 960:
		car.simulate(1.0 / 120.0, 0, 0, 0, false)
		inverted = inverted or car.attitude.y.y < -0.8
		deepest = minf(deepest, car.hull_clearance())
	check(inverted, "Vehicle could not rotate onto its roof")
	check(car.grounded, "Tumbled car did not land again")
	if car.overturned: check(car.grounded_wheels == 0 and not car.dust.emitting, "Roof contact gave tyres traction or dust")
	check(deepest > -0.01, "Roof or side penetrated the terrain")
	check(car.velocity.length() < 0.2 and car.angular_velocity.length() < 0.2, "Roof landing did not settle")
	print("ROLLOVER up=", car.attitude.y, " velocity=", car.velocity, " omega=", car.angular_velocity, " clearance=", deepest)
	# Separately drop directly onto the roof and verify stable upside-down support.
	reset()
	car.attitude = Basis(Vector3.BACK, PI)
	car.position.y = 4.0
	step(5.0)
	check(car.attitude.y.y < -0.85 and car.chassis_grounded, "Roof landing failed to stay inverted")
	check(car.hull_clearance() >= -0.01 and car.velocity.length() < 0.2, "Roof landing penetrated or kept bouncing")
	check(car.grounded_wheels == 0, "Inverted wheels retained traction")
	print("ROOF up=", car.attitude.y, " clearance=", car.hull_clearance(), " velocity=", car.velocity)
	var position_before: Vector3 = car.position
	for i in 240: car.simulate(1.0 / 120.0, 1, 0, 1, false)
	check(car.position.distance_to(position_before) < 0.1, "Throttle drove the upside-down car")
	car.reset_to_route()
	check(not car.overturned and car.angular_velocity.is_zero_approx(), "Reset did not recover the overturned car")
	print("ROLLOVER CHECKS ", "PASS" if failures.is_empty() else failures)
	quit(0 if failures.is_empty() else 1)
