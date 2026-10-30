extends CharacterBody3D
## Four spring/damper contacts, force-limited tyres and ballistic flight.
const Surface = preload("res://desert_battlefield/terrain.gd")
const GRAVITY := 9.81
const MAX_STEP := 1.0 / 120.0
@export var lane: int = 0
@export var route_phase: float = 0.0
@export var cruise_speed: float = 10.0
@export var half_track: float = 1.12
@export var half_wheelbase: float = 1.425
@export var wheel_radius: float = 0.53
## Zero uses the model's gameplay preset, not a certified real-world specification.
@export_range(0, 5000, 10) var mass_override_kg := 0.0
@export_range(0, 1200, 10) var payload_kg := 0.0
var curb_mass_kg := 1850.0
var mass_kg: float:
	get: return maxf(500.0, (mass_override_kg if mass_override_kg > 0.0 else curb_mass_kg) + payload_kg)
var engine_force := 11000.0
var engine_power := 110000.0
var top_speed := 30.0
var spring_rate := 22000.0
var damping := 3200.0
## Soft sand consumes energy in tyre sinkage and ploughing, beyond air drag.
var rolling_resistance := 0.085
var droop := 0.24
var bump_travel := 0.26
## Anti-roll bar: N per metre of left/right compression difference on one axle.
var anti_roll_rate := 7000.0
## Lateral tyre forces act at an effective roll-centre height. At walking and
## ordinary driving speeds the sprung body leans but does not trip over its own
## tyres; above ROLLOVER_SPEED_FULL the full contact-patch lever arm returns,
## so fast, hard cornering and dune slopes can still roll the car.
const ROLL_ARM_LOW := 0.45
const ROLLOVER_SPEED_START := 15.0
const ROLLOVER_SPEED_FULL := 23.0
var front_drive := false
## Drivetrain (Jeep): part-time 4x4 (rigid transfer case, no centre diff) or
## rear-wheel drive 4x2; axle differentials open (torque per axle limited by the
## wheel with less traction, a lifted wheel spins) or locked (each wheel pushes
## up to its own grip).
var four_wheel_drive := true
var diff_locked := false
## 0..1 share of each wheel's drive torque that only spins the tyre.
var drive_slip: Array[float] = [0.0, 0.0, 0.0, 0.0]
var manual := false
var heading := 0.0
var pitch := 0.0
var roll := 0.0
## Position and velocity belong to the centre of mass; mesh pivots stay unchanged.
const COM := Vector3(0, 0.38, 0)
var attitude := Basis.IDENTITY
var angular_velocity := Vector3.ZERO
var chassis_grounded := false
var overturned: bool:
	get: return attitude.y.dot(Vector3.UP) < 0.25
var wheel_normals: Array[Vector3] = []
## Ground type under each tyre (terrain.gd SAND/ROAD/MUD...).
var wheel_surface := PackedInt32Array([0, 0, 0, 0])
## Dominant ground under the car, for dust/HUD.
var surface_kind := 0
## Depth of the chassis floor below the ford's water surface.
var water_submersion := 0.0
## Pedal of the latest tick; a spinning driven tyre on a stuck car digs in.
var last_throttle := 0.0
var parked_time := 0.0
## Tyre contacts when the soil field was last uploaded (see soil.gd live segments).
var upload_contacts: Array[Vector3] = []
var wheel_reachable: Array[bool] = []
var hull_points: Array[Vector3] = []
var speed := 0.0
var speed_kmh: float:
	get: return Vector2(velocity.x, velocity.z).length() * 3.6
var steer := 0.0
var grounded := false
var grounded_wheels := 0
var air_time := 0.0
var longest_flight := 0.0
var landing_count := 0
var landing_impact_speed := 0.0
var distance_travelled := 0.0
var stalled_time := 0.0
var recovery_time := 0.0
var recovery_turn := 0.0
var recovery_count := 0
var last_obstacle := ""
var last_static_obstacle := ""
var wheel_contacts: Array[Vector3] = []
var wheel_offsets: Array[Vector3] = []
var wheel_gaps := PackedFloat32Array([0, 0, 0, 0])
var wheel_loads := PackedFloat32Array([0, 0, 0, 0])
var wheel_grounded: Array[bool] = [false, false, false, false]
var previous_grounded: Array[bool] = [false, false, false, false]
var previous_contacts: Array[Vector3] = []
var support_height := 0.0
var ground_normal := Vector3.UP
var slip_amount := 0.0
var track_clock := 0.0
var samples: Array[Vector3] = []
var sides: Array[Vector3] = []
var track_node: Node3D
var trail := preload("res://desert_battlefield/track_trail.gd").new()
var track_material: ShaderMaterial
var dust_clock := 0.0
var dust_jets: Array[GPUParticles3D] = []
var sand_grains: Array[GPUParticles3D] = []
## Per-emitter grain sprite materials, base particle scale and current sprite.
var grain_materials: Array[ShaderMaterial] = []
var grain_scale: Array[Vector2] = []
var grain_kind: Array[int] = [-1, -1, -1, -1]
## Wheel index of each grain emitter (rear left/right, front left/right).
const GRAIN_WHEELS := [1, 3, 0, 2]
## Visual wheel rotation speed (rad/s) per wheel; driven tyres spin up when stuck.
var wheel_spin_rate: Array[float] = [0.0, 0.0, 0.0, 0.0]
## 0..1 how hard the driven tyres are spinning without progress.
var wheelspin := 0.0
var wheels: Array[Node3D] = []
var wheel_indices: Array[int] = []
## Tests may substitute another surface; the live scene calls the soil directly.
var height_sampler: Callable:
	set(value):
		height_sampler = value
		_direct_soil = false
		invalidate_hull_cache()
var _direct_soil := false
## Temporal broad-phase for hull/terrain contacts. Each support point remembers
## where its gap was last measured; a point cannot reach the ground before it
## has moved far enough for the steepest possible surface to close that gap.
const HULL_SLOPE_BOUND := 3.5
const HULL_SAFETY := 0.23
var hull_cache_points := PackedVector3Array()
var hull_cache_gaps := PackedFloat32Array()
@onready var body: Node3D = $Body
@onready var dust: GPUParticles3D = $Dust
@onready var specks: GPUParticles3D = $AirborneSpecks
@onready var game: Node = get_parent().get_parent()
@onready var soil = game.soil

func _ready() -> void:
	if body.get_meta("endless_drive_model", "jeep") == "izh":
		curb_mass_kg = 1450.0
		engine_force = 6500.0
		engine_power = 65000.0
		top_speed = 23.0
		spring_rate = 19000.0
		anti_roll_rate = 6000.0
		damping = 2700.0
		droop = 0.22
		front_drive = true
	height_sampler = Callable(soil, "height_at")
	_direct_soil = true
	_configure_hull()
	floor_snap_length = 0.0
	track_clock = float(lane) * 0.025
	track_node = Node3D.new()
	track_node.name = "TyreRuts%d" % lane
	track_material = ShaderMaterial.new()
	track_material.shader = preload("res://desert_battlefield/tracks.gdshader")
	trail.configure(track_node, track_material)
	trail.half_track = half_track
	get_parent().add_child.call_deferred(track_node)
	for particle in find_children("*", "GPUParticles3D", true, false):
		particle.process_material = particle.process_material.duplicate()
	dust_jets.assign([$WheelDustLeft, $WheelDustRight])
	sand_grains.assign([$SandGrainsLeft, $SandGrainsRight])
	# Front-wheel emitters (copies of the rear ones): spray comes from whichever
	# wheels are driven, so 4x4, 4x2 and the front-drive van look different.
	for name in ["SandGrainsFrontLeft", "SandGrainsFrontRight"]:
		var copy := $SandGrainsLeft.duplicate() as GPUParticles3D
		copy.name = name
		copy.process_material = copy.process_material.duplicate()
		add_child(copy)
		sand_grains.append(copy)
	for grains in sand_grains:
		# Own mesh+material per emitter so each wheel can throw its surface's sprite.
		var quad := grains.draw_pass_1.duplicate() as QuadMesh
		quad.material = quad.material.duplicate()
		grains.draw_pass_1 = quad
		grain_materials.append(quad.material)
		var process := grains.process_material as ParticleProcessMaterial
		grain_scale.append(Vector2(process.scale_min, process.scale_max))
	for wheel: Node3D in $Body/Wheels.get_children():
		wheels.append(wheel)
		wheel_indices.append((2 if wheel.position.x > 0 else 0) + (0 if wheel.get_meta("front", false) else 1))
	reset_to_route()

func chassis_basis() -> Basis:
	return attitude

func _configure_hull() -> void:
	var catalog = preload("res://scripts/vehicle_catalog.gd")
	var model: String = body.get_meta("endless_drive_model", "jeep")
	var hull := load(catalog.DATA[model].shape) as ConvexPolygonShape3D
	$CollisionShape3D.shape = hull
	var points := hull.points
	# Use actual body vertices for bumpers, roof and underbody, not a low box
	# spanning the wheelbase that catches every dune crest like a snowplough.
	var selected: Array[int] = []
	var adapter := Basis(Vector3.UP, PI * 0.5)
	for x in [-1, 0, 1]:
		for y in [-1, 0, 1]:
			for z in [-1, 0, 1]:
				var direction := Vector3(x, y, z)
				if direction.is_zero_approx(): continue
				var best := -INF
				var index := 0
				for i in points.size():
					var projection := points[i].dot(direction)
					if projection > best:
						best = projection
						index = i
				if index not in selected: selected.append(index)
	for index in selected: hull_points.append(adapter * points[index] - COM)
	# The skid plate is a broad supporting face between the axles.
	for x in [-0.60, 0.60]:
		for z in [-1.1, 1.1]: hull_points.append(Vector3(x, -0.10, z) - COM)
	hull_cache_points.resize(hull_points.size())
	hull_cache_gaps.resize(hull_points.size())
	invalidate_hull_cache()

func invalidate_hull_cache() -> void:
	hull_cache_gaps.fill(-INF)

func terrain_height(x: float, z: float) -> float:
	if _direct_soil: return soil.height_at(x, z)
	return float(height_sampler.call(x, z))

## Only spawning/resetting may place a car directly on the surface.
func place_on_ground(point: Vector3, yaw: float) -> void:
	invalidate_hull_cache()
	water_submersion = 0.0
	position = point
	heading = yaw
	pitch = 0.0
	roll = 0.0
	angular_velocity = Vector3.ZERO
	attitude = Basis(Vector3.UP, heading)
	chassis_grounded = false
	velocity = Vector3.ZERO
	steer = 0.0
	_sample_contacts()
	pitch = atan((wheel_contacts[0].y + wheel_contacts[2].y - wheel_contacts[1].y - wheel_contacts[3].y) / (4.0 * half_wheelbase))
	roll = atan((wheel_contacts[2].y + wheel_contacts[3].y - wheel_contacts[0].y - wheel_contacts[1].y) / (4.0 * half_track))
	attitude = Basis(Vector3.UP, heading) * Basis(Vector3.RIGHT, pitch) * Basis(Vector3.BACK, roll)
	_sample_contacts()
	var rest_y := 0.0
	for i in 4: rest_y += wheel_contacts[i].y + wheel_radius - wheel_offsets[i].y
	position.y = rest_y * 0.25 + droop - mass_kg * GRAVITY / (4.0 * spring_rate)
	_sample_contacts()
	grounded = true
	grounded_wheels = 4
	air_time = 0.0
	speed = 0.0
	stalled_time = 0.0
	recovery_time = 0.0
	previous_contacts.clear()
	previous_grounded.fill(false)
	trail.break_trail()
	for i in 4:
		wheel_grounded[i] = true
		wheel_loads[i] = mass_kg * GRAVITY * 0.25
	_update_visuals(0.0)
	$CollisionShape3D.transform = Transform3D(attitude * Basis(Vector3.UP, PI * 0.5), -(attitude * COM))

func reset_to_route() -> void:
	var p := Surface.route(route_phase, lane)
	var tangent := Surface.route_xz(route_phase + 0.01, lane) - Vector2(p.x, p.z)
	place_on_ground(p, atan2(-tangent.x, -tangent.y))
	_stop_emitters()
	reset_physics_interpolation()

func _physics_process(delta: float) -> void:
	if not manual and not game.patrol_enabled:
		# A car nobody drives is parked: it brakes, settles and then sleeps (no
		# physics cost) until it is driven again or something knocks it.
		if parked_time > 1.5 and velocity.length_squared() < 0.0025 and angular_velocity.length_squared() < 0.0025:
			return
		parked_time += delta
		simulate(delta, 0.0, 1.0, 0.0, false)
		return
	parked_time = 0.0
	var throttle := 0.0
	var brake := 0.0
	var turn := 0.0
	var handbrake := false
	speed = velocity.dot(Vector3(-sin(heading), 0, -cos(heading)))
	if manual:
		stalled_time = 0.0
		recovery_time = 0.0
		var pedal := float(Input.is_physical_key_pressed(KEY_W) or Input.is_physical_key_pressed(KEY_UP) or _touch("throttle")) - float(Input.is_physical_key_pressed(KEY_S) or Input.is_physical_key_pressed(KEY_DOWN) or _touch("reverse"))
		if pedal * speed < -0.5: brake = absf(pedal)
		else: throttle = pedal
		# Keys steer digitally; the touch steering zone is analog (action strength).
		turn = clampf(float(Input.is_physical_key_pressed(KEY_A) or Input.is_physical_key_pressed(KEY_LEFT)) - float(Input.is_physical_key_pressed(KEY_D) or Input.is_physical_key_pressed(KEY_RIGHT))
			+ _touch_strength("left") - _touch_strength("right"), -1.0, 1.0)
		handbrake = Input.is_physical_key_pressed(KEY_SHIFT) or _touch("handbrake")
	else:
		var nearest := route_phase
		var distance := INF
		var position_xz := Vector2(position.x, position.z)
		for i in range(-12, 31):
			var t := route_phase + float(i) * 0.018
			var d := Surface.route_xz(t, lane).distance_squared_to(position_xz)
			if d < distance:
				distance = d
				nearest = t
		route_phase = nearest
		var target := Surface.route_xz(route_phase + 0.24, lane) - position_xz
		var angle := wrapf(atan2(-target.x, -target.y) - heading, -PI, PI)
		var steering_angle := atan2(4.0 * half_wheelbase * sin(angle), maxf(4.0, target.length()))
		turn = clampf(steering_angle / steering_limit(), -1.0, 1.0)
		# The patrol must respect the same lateral grip as a human driver. Slow
		# before tight bends rather than pushing the new physical tyres off-route.
		var corner_speed := sqrt(1.6 * half_wheelbase * 2.0 / maxf(absf(tan(steering_angle)), 0.025))
		var a := Surface.route_xz(route_phase + 0.16, lane) - Surface.route_xz(route_phase, lane)
		var b := Surface.route_xz(route_phase + 0.32, lane) - Surface.route_xz(route_phase + 0.16, lane)
		var curvature := 2.0 * absf(a.cross(b)) / maxf(a.length() * b.length() * (a + b).length(), 0.01)
		var target_speed := minf(minf(cruise_speed, corner_speed), sqrt(1.6 / maxf(curvature, 0.001)))
		var facing := Vector3(-sin(heading), 0, -cos(heading))
		for other in get_parent().get_children():
			if other == self or not other is CharacterBody3D: continue
			var separation: Vector3 = other.position - position
			var ahead := separation.dot(facing)
			var lateral := absf(separation.dot(Vector3(-facing.z, 0, facing.x)))
			if ahead > 0.0 and ahead < 16.0 and lateral < 2.8:
				target_speed = minf(target_speed, maxf(0.0, other.velocity.dot(facing) + (ahead - 7.0) * 0.6))
		throttle = clampf((target_speed - speed) * 0.65, 0.0, 1.0)
		brake = clampf((speed - target_speed - 0.2) * 0.45, 0.0, 1.0)
		if recovery_time > 0.0:
			recovery_time -= delta
			throttle = -0.8 if speed < 0.5 else 0.0
			brake = 1.0 if speed >= 0.5 else 0.0
			turn = recovery_turn
		else:
			stalled_time = stalled_time + delta if absf(speed) < 1.0 and throttle > 0.7 and grounded else 0.0
			if stalled_time > 2.0:
				recovery_time = 2.0
				recovery_turn = -signf(turn) * 0.75 if absf(turn) > 0.1 else (0.75 if lane % 2 == 0 else -0.75)
				stalled_time = 0.0
				recovery_count += 1
	simulate(delta, throttle, brake, turn, handbrake)

func is_driven(i: int) -> bool:
	if front_drive: return i % 2 == 0
	return four_wheel_drive or i % 2 == 1

func _touch_strength(action: String) -> float:
	var name := "battlefield_" + action
	return Input.get_action_strength(name) if InputMap.has_action(name) else 0.0

func _touch(action: String) -> bool:
	var name := "battlefield_" + action
	return InputMap.has_action(name) and Input.is_action_pressed(name)

func steering_limit() -> float:
	return 0.55 / (1.0 + speed * speed * 0.0025)

## Shared by live input and deterministic regressions; movement uses explicit dt.
func simulate(delta: float, throttle: float, brake: float, turn: float, handbrake: bool) -> void:
	if delta <= 0.0: return
	last_throttle = clampf(throttle, -1.0, 1.0)
	var steps := maxi(1, ceili(delta / MAX_STEP))
	var dt := delta / steps
	# The previous substep already sampled its final pose. Refresh once for
	# outside changes (soil stamps/reset), then reuse contacts between substeps.
	_sample_contacts()
	for step in steps:
		steer = move_toward(steer, clampf(turn, -1.0, 1.0) * steering_limit(), dt * 1.65)
		_integrate(dt, clampf(throttle, -1.0, 1.0), clampf(brake, 0.0, 1.0), handbrake)
	if absf(position.x) > 125 or absf(position.z) > 125 or not position.is_finite():
		reset_to_route()
	_update_visuals(delta)
	_update_tracks(delta)
	dust_clock += delta
	_update_dust(Vector3(-sin(heading), 0, -cos(heading)), Vector3(cos(heading), 0, -sin(heading)))

func terrain_normal(x: float, z: float) -> Vector3:
	const E := 0.18
	return Vector3(terrain_height(x - E, z) - terrain_height(x + E, z), 2.0 * E,
		terrain_height(x, z - E) - terrain_height(x, z + E)).normalized()

func _sample_contacts() -> void:
	wheel_contacts.clear()
	wheel_offsets.clear()
	wheel_normals.clear()
	wheel_reachable.clear()
	support_height = 0.0
	ground_normal = Vector3.ZERO
	for i in 4:
		var offset := attitude * (Vector3(half_track * (-1.0 if i < 2 else 1.0), 0, -half_wheelbase if i % 2 == 0 else half_wheelbase) - COM)
		var hub := position + offset
		var normal := terrain_normal(hub.x, hub.z)
		var alignment := attitude.y.dot(normal)
		# Signed travel along the suspension axis. Side/roof contacts cannot drive.
		var gap := ((hub.y - terrain_height(hub.x, hub.z)) * normal.y - wheel_radius) / maxf(alignment, 0.35)
		var contact := hub - attitude.y * clampf(gap, -bump_travel, droop) - normal * wheel_radius
		contact.y = terrain_height(contact.x, contact.z)
		wheel_offsets.append(offset)
		wheel_contacts.append(contact)
		wheel_normals.append(normal)
		wheel_reachable.append(alignment > 0.35)
		wheel_surface[i] = Surface.surface_at(contact.x, contact.z)
		wheel_gaps[i] = gap
		support_height += contact.y * 0.25
		ground_normal += normal * 0.25
	ground_normal = ground_normal.normalized()

func inertia_diagonal() -> Vector3:
	var length := half_wheelbase * 2.0 + 1.0
	var width := half_track * 2.0 + 0.35
	return mass_kg / 12.0 * Vector3(length * length + 2.25, length * length + width * width, width * width + 2.25)

func inverse_inertia(world_vector: Vector3) -> Vector3:
	return attitude * ((attitude.transposed() * world_vector) / inertia_diagonal())

func _impulse(impulse: Vector3, arm: Vector3) -> void:
	velocity += impulse / mass_kg
	angular_velocity += inverse_inertia(arm.cross(impulse))

## Inelastic contact impulses include angular effective mass. Position correction
## never becomes upward velocity, so a bump stop cannot catapult the whole car.
func _contact_impulse(arm: Vector3, normal: Vector3, friction: float) -> void:
	var point_velocity := velocity + angular_velocity.cross(arm)
	var closing := point_velocity.dot(normal)
	if closing >= 0.0: return
	var denominator := 1.0 / mass_kg + normal.dot(inverse_inertia(arm.cross(normal)).cross(arm))
	var impulse := -closing / denominator
	_impulse(normal * impulse, arm)
	var tangent := (velocity + angular_velocity.cross(arm)).slide(normal)
	if tangent.length_squared() < 0.000001: return
	var direction := tangent.normalized()
	var tangent_mass := 1.0 / mass_kg + direction.dot(inverse_inertia(arm.cross(direction)).cross(arm))
	_impulse(-direction * minf(tangent.length() / tangent_mass, impulse * friction), arm)

func _vehicle_collision(other: CharacterBody3D, point: Vector3, normal: Vector3) -> void:
	var arm := point - global_position
	var other_arm := point - other.global_position
	var relative: Vector3 = velocity + angular_velocity.cross(arm) - other.velocity - other.angular_velocity.cross(other_arm)
	var closing := relative.dot(normal)
	if closing >= 0.0: return
	var denominator: float = 1.0 / mass_kg + 1.0 / other.mass_kg
	denominator += normal.dot(inverse_inertia(arm.cross(normal)).cross(arm))
	denominator += normal.dot(other.inverse_inertia(other_arm.cross(normal)).cross(other_arm))
	var impulse := normal * (-closing / denominator)
	_impulse(impulse, arm)
	other._impulse(-impulse, other_arm)

func hull_clearance() -> float:
	var clearance := INF
	for local_point in hull_points:
		var point := position + attitude * local_point
		clearance = minf(clearance, point.y - terrain_height(point.x, point.z))
	return clearance

func _resolve_terrain() -> bool:
	chassis_grounded = false
	# Iterative contacts share the support impulse across the hull, instead of
	# bouncing the whole car separately off each corner.
	var arms: Array[Vector3] = []
	var normals: Array[Vector3] = []
	var penetration := 0.0
	for index in hull_points.size():
		var arm := attitude * hull_points[index]
		var point := position + arm
		var then := hull_cache_points[index]
		var reach := absf(point.y - then.y) + HULL_SLOPE_BOUND * Vector2(point.x - then.x, point.z - then.z).length()
		if hull_cache_gaps[index] - reach > HULL_SAFETY: continue
		var gap := point.y - terrain_height(point.x, point.z)
		hull_cache_points[index] = point
		hull_cache_gaps[index] = gap
		penetration = maxf(penetration, -gap)
		if gap < 0.015:
			chassis_grounded = true
			arms.append(arm)
			normals.append(terrain_normal(point.x, point.z))
	for i in 4:
		if not wheel_reachable[i]: continue
		var excess := -bump_travel - wheel_gaps[i]
		penetration = maxf(penetration, excess * maxf(attitude.y.y, 0.0))
		if excess >= -0.005:
			arms.append(wheel_contacts[i] - position)
			normals.append(wheel_normals[i])
	for iteration in 6:
		for i in arms.size():
			_contact_impulse(arms[i], normals[i], 0.6 if chassis_grounded else 0.0)
	position.y += penetration
	return penetration > 0.0

func _integrate(dt: float, throttle: float, brake: float, handbrake: bool) -> void:
	var old_position := position
	var force := Vector3.DOWN * mass_kg * GRAVITY
	var torque := Vector3.ZERO
	var forward := -attitude.z
	speed = velocity.dot(forward)
	var motor := minf(engine_force, engine_power / maxf(absf(speed), 3.0)) * throttle
	var ground_limit := ground_speed_limit()
	if throttle < 0.0: motor *= 0.45 * clampf((minf(6.0, ground_limit) + speed) / 2.0, 0.0, 1.0)
	else: motor *= clampf((ground_limit - speed) / maxf(ground_limit * 0.15, 1.5), 0.0, 1.0)
	var was_grounded := grounded
	grounded_wheels = 0
	var maximum_slip := 0.0
	var driven_load := 0.0
	var compressions := PackedFloat32Array([0, 0, 0, 0])
	for i in 4:
		if wheel_reachable[i] and wheel_gaps[i] < droop:
			compressions[i] = clampf(droop - wheel_gaps[i], 0.0, droop + bump_travel)
	for i in 4:
		var normal := wheel_normals[i]
		var arm := wheel_contacts[i] - position
		var alignment := maxf(attitude.y.dot(normal), 0.35)
		var normal_speed := (velocity + angular_velocity.cross(arm)).dot(normal)
		var load := 0.0
		if compressions[i] > 0.0:
			# The bar moves load from the extended to the compressed side of one axle.
			var bar := anti_roll_rate * (compressions[i] - compressions[(i + 2) % 4])
			load = maxf(0.0, (spring_rate * compressions[i] + bar - damping * normal_speed / alignment) / alignment)
		wheel_loads[i] = load
		wheel_grounded[i] = load > 5.0
		if wheel_grounded[i] and is_driven(i): driven_load += load
	# Traction of each tyre, for the open differentials below.
	var traction := PackedFloat32Array([0, 0, 0, 0])
	for i in 4:
		if wheel_grounded[i]: traction[i] = wheel_loads[i] * float(Surface.GRIP[wheel_surface[i]])
	for i in 4:
		drive_slip[i] = 0.0
		# Open diff: a lifted driven wheel takes all the torque and just spins.
		if is_driven(i) and not wheel_grounded[i] and not diff_locked and wheel_grounded[(i + 2) % 4] and absf(motor) > 1.0:
			drive_slip[i] = 1.0
	var roll_arm := lerpf(ROLL_ARM_LOW, 1.0, smoothstep(ROLLOVER_SPEED_START, ROLLOVER_SPEED_FULL, absf(speed)))
	for i in 4:
		if not wheel_grounded[i]: continue
		grounded_wheels += 1
		var front := i % 2 == 0
		var normal := wheel_normals[i]
		var arm := wheel_contacts[i] - position
		var point_velocity := velocity + angular_velocity.cross(arm)
		var load := wheel_loads[i]
		var suspension_force := normal * load
		force += suspension_force
		torque += arm.cross(suspension_force)
		var tyre_forward := (attitude * Basis(Vector3.UP, steer if front else 0.0) * Vector3.FORWARD).slide(normal).normalized()
		var tyre_right := tyre_forward.cross(normal).normalized()
		var longitudinal_speed := point_velocity.dot(tyre_forward)
		var lateral_speed := point_velocity.dot(tyre_right)
		var ground := wheel_surface[i]
		var grip: float = Surface.GRIP[ground]
		var capacity := load * grip
		var braking_force := brake * 13500.0 * 0.25 + (3400.0 if handbrake and not front else 0.0)
		# Loose sand keeps the tuned default; other grounds scale it by their rolling loss.
		var rolling: float = rolling_resistance * float(Surface.ROLLING[ground]) / float(Surface.ROLLING[Surface.SAND])
		# Sinkage: in mud and the ford bed the deeper the rut, the harder the
		# tyre ploughs. A spinning, digging car can bury itself.
		if ground == Surface.MUD: rolling *= 1.0 + soil.depth_at(wheel_contacts[i].x, wheel_contacts[i].z) * 5.0
		var resistance := minf(braking_force + load * rolling, absf(longitudinal_speed) * mass_kg * 0.25 / dt)
		var longitudinal := 0.0
		if is_driven(i) and driven_load > 0.0:
			var partner := (i + 2) % 4
			var requested := 0.0
			if diff_locked:
				# Locked: each tyre takes torque in proportion to its load and
				# pushes up to its own grip (clamped with rolling loss below).
				requested = motor * load / driven_load
				longitudinal = requested
			else:
				# Open: equal torque to both sides of the axle; the side with less
				# traction sets it, the surplus only spins the tyres.
				var axle_load := load + (wheel_loads[partner] if wheel_grounded[partner] else 0.0)
				requested = motor * axle_load / driven_load * 0.5
				var limit := minf(traction[i], traction[partner]) + resistance
				longitudinal = clampf(requested, -limit, limit)
			if absf(requested) > 1.0:
				var net := absf(clampf(longitudinal - signf(longitudinal_speed) * resistance, -capacity, capacity) + signf(longitudinal_speed) * resistance)
				drive_slip[i] = clampf(1.0 - net / absf(requested), 0.0, 1.0)
		longitudinal -= signf(longitudinal_speed) * resistance
		longitudinal = clampf(longitudinal, -capacity, capacity)
		var lateral_limit := sqrt(maxf(0.0, capacity * capacity - longitudinal * longitudinal))
		if handbrake and not front: lateral_limit *= 0.28
		var cornering := curb_mass_kg * 13.0 * sqrt(grip / 0.85)
		var lateral_force := clampf(-lateral_speed * cornering / maxf(absf(longitudinal_speed), 3.0), -lateral_limit, lateral_limit)
		var side_force := tyre_right * lateral_force
		var drive_force := tyre_forward * longitudinal
		force += drive_force + side_force
		# Raising only the lateral lever arm reduces roll torque; yaw response
		# and longitudinal pitch/dive keep their physical contact-patch arms.
		var up_arm := arm.dot(attitude.y)
		torque += arm.cross(drive_force) + (arm - attitude.y * (up_arm * (1.0 - roll_arm))).cross(side_force)
		maximum_slip = maxf(maximum_slip, absf(lateral_speed) / maxf(absf(longitudinal_speed), 2.0))
	# Hard braking at speed locks/scrubs the tyres: more digging, rubber marks.
	var brake_skid := brake * 0.55 * clampf(absf(speed) / 6.0, 0.0, 1.0) if grounded_wheels > 0 else 0.0
	slip_amount = clampf(maximum_slip + (0.5 if handbrake else 0.0) + brake_skid, 0.0, 1.0)
	force -= velocity * velocity.length() * 0.58
	force += _bog_down_force(ground_limit)
	force += _water_force()
	velocity += force / mass_kg * dt
	# Integrate world angular momentum using the body's inertia tensor. A basis
	# permits arbitrary rotations with no pitch/roll clamps or Euler singularity.
	var momentum := attitude * (inertia_diagonal() * (attitude.transposed() * angular_velocity))
	momentum = (momentum + torque * dt) * exp(-0.08 * dt)
	angular_velocity = inverse_inertia(momentum)
	var angle := angular_velocity.length() * dt
	if angle > 0.000001:
		attitude = (Basis(angular_velocity.normalized(), angle) * attitude).orthonormalized()
		angular_velocity = inverse_inertia(momentum)
	heading = atan2(attitude.z.x, attitude.z.z)
	var euler := attitude.get_euler()
	pitch = euler.x
	roll = euler.z
	var shape := $CollisionShape3D as CollisionShape3D
	shape.transform = Transform3D(attitude * Basis(Vector3.UP, PI * 0.5), -(attitude * COM))
	var motion := velocity * dt
	var obstacle_support := false
	for iteration in 3:
		var hit := move_and_collide(motion)
		if hit == null: break
		last_obstacle = str(hit.get_collider().name)
		obstacle_support = obstacle_support or hit.get_normal().y > 0.25
		if hit.get_collider().has_method("_vehicle_collision"):
			_vehicle_collision(hit.get_collider(), hit.get_position(), hit.get_normal())
		else:
			last_static_obstacle = last_obstacle
			_contact_impulse(hit.get_position() - global_position, hit.get_normal(), 0.55)
		motion = hit.get_remainder().slide(hit.get_normal())
	_sample_contacts()
	var impact_speed := maxf(0.0, -velocity.dot(ground_normal))
	if _resolve_terrain(): _sample_contacts()
	chassis_grounded = chassis_grounded or obstacle_support
	for i in 4:
		if not wheel_reachable[i] or wheel_gaps[i] >= droop: wheel_grounded[i] = false
		if not wheel_grounded[i]: previous_grounded[i] = false
	if not (wheel_grounded[1] and wheel_grounded[3]): trail.break_trail()
	grounded_wheels = wheel_grounded.count(true)
	grounded = grounded_wheels > 0 or chassis_grounded
	if grounded:
		if not was_grounded and air_time > 0.08:
			landing_count += 1
			landing_impact_speed = impact_speed
		air_time = 0.0
	else:
		air_time += dt
		longest_flight = maxf(longest_flight, air_time)
	speed = velocity.dot(-attitude.z)
	distance_travelled += Vector2(position.x - old_position.x, position.z - old_position.z).length()

## Average reachable speed over the loaded tyres' grounds (m/s).
func ground_speed_limit() -> float:
	var factor := 0.0
	var count := 0
	for i in 4:
		if wheel_grounded[i] or wheel_gaps[i] < droop:
			factor += Surface.SPEED[wheel_surface[i]]
			count += 1
	return top_speed * (factor / count if count > 0 else 1.0)

## Mud, water and clay drag an overspeeding car down to their limit within a
## few seconds instead of letting it coast across at road speed.
func _bog_down_force(limit: float) -> Vector3:
	if not grounded: return Vector3.ZERO
	var softness := 0.0
	var count := 0
	for i in 4:
		if wheel_grounded[i]:
			# Only yielding ground bogs a car down; rock is merely slow to drive.
			if Surface.DEFORM[wheel_surface[i]] >= 0.5:
				softness += clampf((0.75 - float(Surface.SPEED[wheel_surface[i]])) / 0.45, 0.0, 1.0)
			count += 1
	if count == 0 or softness <= 0.0: return Vector3.ZERO
	var horizontal := Vector3(velocity.x, 0.0, velocity.z)
	var excess := horizontal.length() - limit
	if excess <= 0.0: return Vector3.ZERO
	return -horizontal.normalized() * mass_kg * minf(excess * 1.8, 8.0) * softness / count

## Wading: hydrodynamic drag on the submerged body and partial (leaky) buoyancy,
## both applied through the centre of mass. Zero outside the ford.
func _water_force() -> Vector3:
	water_submersion = 0.0
	if Surface.water_depth_at(position.x, position.z) <= 0.0: return Vector3.ZERO
	var floor_y := position.y - (COM.y + 0.1) * maxf(attitude.y.y, 0.0)
	water_submersion = clampf(Surface.WATER_LEVEL - floor_y, 0.0, 1.4)
	if water_submersion <= 0.0: return Vector3.ZERO
	var horizontal := Vector3(velocity.x, 0.0, velocity.z)
	var drag := -horizontal * horizontal.length() * 520.0 * water_submersion
	drag.y = -velocity.y * absf(velocity.y) * 900.0 * water_submersion
	return drag + Vector3.UP * 1000.0 * GRAVITY * 1.5 * water_submersion

func _update_visuals(dt: float) -> void:
	body.basis = attitude
	body.position = -(attitude * COM)
	for i in wheels.size():
		var wheel := wheels[i]
		var index := wheel_indices[i]
		wheel.position.y = clampf(-wheel_gaps[index], -droop, bump_travel) if wheel_reachable[index] else -droop
		wheel.rotation.y = steer if wheel.get_meta("front", false) else 0.0
		# Rolling speed, plus slip on driven tyres: with throttle and little
		# progress (stuck in mud/sand, climbing) the wheels keep turning.
		var rate := speed / wheel_radius
		var driven := is_driven(index)
		if driven and absf(last_throttle) > 0.15 and (wheel_grounded[index] or water_submersion > 0.0 or drive_slip[index] > 0.0):
			var slip_rate := last_throttle * 14.0 * (1.0 - clampf(absf(speed) / 6.0, 0.0, 1.0))
			# Torque the ground cannot take (open diff, ice, a lifted wheel) spins it.
			slip_rate += last_throttle * 22.0 * drive_slip[index]
			if signf(slip_rate) == signf(rate) or absf(rate) < 0.5: rate += slip_rate
		elif not wheel_grounded[index]:
			rate = wheel_spin_rate[index] * maxf(0.0, 1.0 - dt * 0.6)
		wheel_spin_rate[index] = move_toward(wheel_spin_rate[index], rate, maxf(dt, 1e-3) * 40.0)
		wheel.get_node("Spin").rotate_x(-wheel_spin_rate[index] * dt)

func _update_tracks(dt: float) -> void:
	# Clear lost contact every tick, so landing never connects a trail across a jump.
	for i in 4:
		if not wheel_grounded[i]: previous_grounded[i] = false
	if not (wheel_grounded[1] and wheel_grounded[3]): trail.break_trail()
	# Ruts are pressed every tick from the last stamped point of each tyre, so
	# deformation appears directly beneath the wheels rather than a step behind.
	if previous_contacts.size() != 4:
		previous_contacts.assign(wheel_contacts)
		previous_grounded.assign(wheel_grounded)
	for i in 4:
		if not wheel_grounded[i]: continue
		if not previous_grounded[i] or absf(speed) <= 0.8:
			previous_contacts[i] = wheel_contacts[i]
			previous_grounded[i] = true
		elif Vector2(wheel_contacts[i].x - previous_contacts[i].x, wheel_contacts[i].z - previous_contacts[i].z).length_squared() >= 0.0144:
			soil.stamp_segment(previous_contacts[i], wheel_contacts[i], -body.basis.z, slip_amount)
			previous_contacts[i] = wheel_contacts[i]
	# Wheelspin: full throttle with little forward progress excavates a hole
	# beneath the driven tyres (deepest drift profile), as in real soft sand.
	var spin := clampf(absf(last_throttle) * (1.0 - absf(speed) / 2.5), 0.0, 1.0)
	wheelspin = spin if (wheel_grounded[1] or wheel_grounded[3]) else 0.0
	# Only torque the ground cannot take digs: a locked diff / 4x4 spreads it
	# over more tyres and digs less than one spinning wheel of an open axle.
	var digging := false
	for i in 4:
		if not (wheel_grounded[i] and is_driven(i)): continue
		var tyre_spin := clampf(drive_slip[i] * absf(last_throttle) * (1.0 - absf(speed) / 2.5), 0.0, 1.0)
		if tyre_spin > 0.3:
			soil.stamp(wheel_contacts[i], -body.basis.z * signf(last_throttle), 2.4 * tyre_spin * dt, 1.0)
			digging = true
	if digging: soil.prefetch(position)
	if absf(speed) > 2.0:
		# Prepare the fine sand patch the car is about to enter.
		soil.prefetch(position + velocity * 0.7)
	track_clock += dt
	if track_clock <= 0.075: return
	track_clock = 0.0
	if absf(speed) > 0.8:
		if wheel_grounded[1] and wheel_grounded[3] and not (Surface.TRACKED[wheel_surface[1]] and Surface.TRACKED[wheel_surface[3]]):
			trail.break_trail()
		elif wheel_grounded[1] and wheel_grounded[3]:
			var rear_center := (wheel_contacts[1] + wheel_contacts[3]) * 0.5
			samples.append(rear_center)
			sides.append(body.basis.x)
			if samples.size() > 650:
				samples.pop_front()
				sides.pop_front()
			trail.append(rear_center, body.basis.x)

func _stop_emitters() -> void:
	for emitter in [dust, specks, $WheelDustLeft, $WheelDustRight]:
		emitter.emitting = false
	for emitter in sand_grains: emitter.emitting = false

func _update_dust(forward: Vector3, right: Vector3) -> void:
	var movement := Vector2(velocity.x, velocity.z).length()
	var intensity := clampf(movement / 12.0 + slip_amount * 0.55 + wheelspin * 0.7, 0.05, 1.0)
	surface_kind = wheel_surface[1] if wheel_grounded[1] or not wheel_grounded[3] else wheel_surface[3]
	var cloud: float = Surface.DUST[surface_kind]
	var spray: float = Surface.SPRAY[surface_kind]
	var wake := (wheel_contacts[1] + wheel_contacts[3]) * 0.5 + Vector3.UP * 0.38
	dust.global_position = wake
	specks.global_position = wake
	dust.emitting = (wheel_grounded[1] or wheel_grounded[3]) and movement > 1.4 and cloud > 0.01
	specks.emitting = dust.emitting
	var refresh := dust_clock >= 0.05
	var detail: float = game.dust_detail
	if refresh:
		dust_clock = 0.0
		dust.amount_ratio = intensity * cloud * game.dust_density
		specks.amount_ratio = intensity * detail * cloud
		var wake_material := dust.process_material as ParticleProcessMaterial
		wake_material.direction = (-forward * 0.5 + Vector3(0.35, 0.6, 0.12)).normalized()
		wake_material.color = Surface.TINT[surface_kind]
		(specks.process_material as ParticleProcessMaterial).color = Surface.TINT[surface_kind]
	var heavy := 1.0 + (spray - 1.0) * 0.6 + water_submersion
	for i in 2:
		var side := -1.0 if i == 0 else 1.0
		var wheel := 1 if i == 0 else 3
		var jet := dust_jets[i]
		var ground := wheel_surface[wheel]
		var amount: float = Surface.DUST[ground]
		jet.global_position = wheel_contacts[wheel] + Vector3.UP * 0.13
		jet.emitting = wheel_grounded[wheel] and (movement > 0.9 or wheelspin > 0.3) and amount > 0.01
		if not refresh: continue
		jet.amount_ratio = clampf(intensity * amount * game.dust_density, 0.0, 1.0)
		var process := jet.process_material as ParticleProcessMaterial
		process.direction = (-forward * 0.75 + right * side * (0.45 + slip_amount) + Vector3.UP * 0.65).normalized()
		process.color = Surface.TINT[ground]
		process.initial_velocity_min = (0.9 + movement * 0.08) * heavy
		process.initial_velocity_max = (2.6 + movement * 0.12 + slip_amount * 3.5 + wheelspin * 3.0) * heavy
	for i in sand_grains.size():
		# Thrown material comes off the driven tyres, opposite to their rotation.
		var wheel: int = GRAIN_WHEELS[i]
		var grains := sand_grains[i]
		var ground := wheel_surface[wheel]
		var amount: float = Surface.SPRAY[ground]
		var spinning := clampf(wheelspin + drive_slip[wheel] * absf(last_throttle), 0.0, 1.0)
		grains.global_position = wheel_contacts[wheel] + Vector3.UP * 0.13
		grains.emitting = is_driven(wheel) and wheel_grounded[wheel] and (movement > 0.9 or spinning > 0.3) and amount > 0.01
		if not refresh or not grains.emitting: continue
		var side := -1.0 if wheel < 2 else 1.0
		var rotation_sign := signf(wheel_spin_rate[wheel]) if absf(wheel_spin_rate[wheel]) > 0.5 else 1.0
		var wheel_intensity := clampf(intensity + spinning * 0.6, 0.05, 1.0)
		grains.amount_ratio = clampf(wheel_intensity * amount * detail, 0.0, 1.0)
		var process := grains.process_material as ParticleProcessMaterial
		process.direction = (-forward * 0.75 * rotation_sign + right * side * (0.45 + slip_amount) + Vector3.UP * 0.65).normalized()
		process.color = Surface.TINT[ground]
		if grain_kind[i] != ground:
			grain_kind[i] = ground
			grain_materials[i].set_shader_parameter("sprite_kind", Surface.SPRITE[ground])
			var size: float = Surface.SPRITE_SIZE[ground]
			process.scale_min = grain_scale[i].x * size
			process.scale_max = grain_scale[i].y * size
		# Water and mud throw heavier, faster spray; wading lifts it further.
		process.initial_velocity_min = (0.9 + movement * 0.08) * heavy
		process.initial_velocity_max = (2.6 + movement * 0.12 + slip_amount * 3.5 + spinning * 3.5) * heavy
