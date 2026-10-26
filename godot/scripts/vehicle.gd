class_name DriveVehicle
extends CharacterBody3D
## Bicycle steering ported from PlanarMotion.kt, with Godot terrain/wall collision.

const MODEL_PATH := "res://assets/models/jeep/jeep_cherokee.tscn"
const CHASSIS_SHAPE := preload("res://assets/models/jeep/jeep_chassis.res")
const WHEELBASE := 2.96
const WHEEL_RADIUS := 0.44
const WHEEL_TRACK := 1.606
const GRAVITY := 9.81
const TERRAIN_STEP := 0.32
var run: DriveRun
var world: DriveWorld
var speed := 0.0
var heading := 0.0
var wheel_angle := 0.0
var throttle_input := 0.0
var brake_input := 0.0
var handbrake_input := 0.0
var stationary_time := 0.0
var steering_input := 0.0
var winch_pull_velocity := Vector3.ZERO
var controls_enabled := true
var boundary_recovery_enabled := true # Tests disable this to prove real collision, not clamping.
var surface_info: Dictionary = {}
var boundary_recoveries := 0
var cruise_enabled := false
var cruise_amount := 0.0
var stop_reason := ""
var terrain_contacts := 0
var terrain_steps := 0
var terrain_step_rejections := 0
var obstacle_impacts := 0
var reverse_limit_hits := 0 # Retained diagnostic: backward travel has no odometer clamp.
var unexplained_stops := 0
var last_speed_loss := 0.0
var last_motion_distance := 0.0
var last_collision_normal := Vector3.ZERO
var _brake_pressure := 0.0
var _last_manual_throttle := 0.0
var _manual_age := 100.0
var _yaw_rate := 0.0
var _lateral_speed := 0.0
var _reverse_time := 0.0
var _reverse_engaged := false
var _spin := 0.0
var _road_spin := 0.0
var _visual: Node3D
var _wheels: Array[Dictionary] = []
var _lights: Array[SpotLight3D] = []
var _audio: AudioStreamPlayer3D
var _touches: Dictionary = {}
var _brake_materials: Array[StandardMaterial3D] = []
var _reverse_materials: Array[StandardMaterial3D] = []
var _reverse_lights: Array[SpotLight3D] = []
var _last_light_mode := -1
var _last_rear_mode := -1
var _snorkel: Node3D
var _visual_rest_position := Vector3.ZERO
var _visual_rest_rotation := Vector3.ZERO
var _last_compaction := Vector3.INF
var wheel_contacts: Array[Vector3] = []
var max_wheel_contact_error := 0.0
var sink_depth := 0.0
var soil_slip := 0.0
var wheel_speed := 0.0
var belly_contact := false
var soil_resistance := 0.0
var wheel_support := 1.0
var rear_wheel_support := 1.0
var _soil_timer := 0.0
var body_pitch := 0.0
var body_roll := 0.0
var _physics_age := 0.0
var _debris_contacts: Dictionary = {}


func _ready() -> void:
	physics_interpolation_mode = Node.PHYSICS_INTERPOLATION_MODE_ON
	floor_snap_length = 0.8
	floor_max_angle = deg_to_rad(48)
	floor_constant_speed = true
	collision_mask |= 2
	# A vehicle must never inherit the tangential/exit velocity of a rotating
	# crate under a tyre. Floor collision and floor snapping remain active.
	platform_floor_layers = 0
	platform_wall_layers = 0
	platform_on_leave = CharacterBody3D.PLATFORM_ON_LEAVE_DO_NOTHING
	var collision := get_node_or_null("ChassisCollision") as CollisionShape3D
	# Separate load-bearing wheels and rounded belly: the wheels can descend
	# into soft soil until the physical chassis catches on the remaining ground.
	if collision == null:
		collision = CollisionShape3D.new()
		collision.name = "ChassisCollision"
		collision.shape = CHASSIS_SHAPE
		add_child(collision)
	for axle: float in [-1.48, 1.48]:
		for side: float in [-WHEEL_TRACK * 0.5, WHEEL_TRACK * 0.5]:
			var wheel_name := "Contact_%s_%s" % ["F" if axle > 0 else "R", "L" if side > 0 else "R"]
			if has_node(wheel_name): continue
			var contact := CollisionShape3D.new()
			contact.name = wheel_name
			var tyre := CylinderShape3D.new()
			tyre.radius = WHEEL_RADIUS
			tyre.height = 0.286
			contact.shape = tyre
			contact.position = Vector3(axle, 0, side)
			contact.rotation.x = PI * 0.5
			add_child(contact)
	_visual = get_node_or_null("Visual") as Node3D
	if _visual == null:
		_visual = (load(MODEL_PATH) as PackedScene).instantiate()
		_visual.name = "Visual"
		add_child(_visual)
	_prepare_model(_visual)
	if not has_node("VisibilityOutline"):
		add_child(preload("res://scenes/vehicle_visibility.tscn").instantiate())
	_visual_rest_position = _visual.position
	_visual_rest_rotation = _visual.rotation
	for side: float in [-0.63, 0.63]:
		var light := SpotLight3D.new()
		var marker := _visual.get_node_or_null("HeadlightL" if side > 0 else "HeadlightR") as Node3D
		light.position = Vector3.ZERO if marker != null else Vector3(2.25, 0.55, side)
		light.rotation.y = -PI / 2.0
		light.rotation.x = -0.09
		light.light_color = Color("ffe7b8")
		light.light_energy = 5.0
		light.spot_range = 55.0
		light.spot_angle = 40.0
		light.shadow_enabled = false
		light.visible = run != null and run.headlights and run.battery > 0.01
		if marker != null: marker.add_child(light)
		else: add_child(light)
		_lights.append(light)
	var equipment := _visual.get_node_or_null("ExpeditionEquipment") as Node3D
	if equipment == null:
		equipment = load("res://scenes/vehicle_equipment.tscn").instantiate() as Node3D
		_visual.add_child(equipment)
	for label: String in ["ReverseLampL", "ReverseLampR"]:
		var lamp := equipment.get_node(label) as MeshInstance3D
		var lens := lamp.material_override.duplicate() as StandardMaterial3D
		lamp.material_override = lens
		_reverse_materials.append(lens)
		_reverse_lights.append(lamp.get_node("GroundLight") as SpotLight3D)
	_snorkel = equipment.get_node("Snorkel") as Node3D
	_snorkel.visible = run != null and run.snorkel_installed
	_audio = AudioStreamPlayer3D.new()
	_audio.stream = DesertAudio.engine_loop()
	_audio.max_distance = 35.0
	_audio.unit_size = 9.0
	_audio.volume_db = -12.0
	_audio.finished.connect(func() -> void:
		if run.engine_running:
			_audio.play())
	add_child(_audio)


func _physics_process(dt: float) -> void:
	if run == null or world == null:
		return
	_physics_age += dt
	_ensure_safe_position()
	var manual_throttle := _strength("throttle") if controls_enabled else 0.0
	brake_input = _strength("brake") if controls_enabled else 0.0
	handbrake_input = _strength("handbrake") if controls_enabled else 0.0
	steering_input = (_strength("right") - _strength("left")) if controls_enabled else 0.0
	_manual_age += dt
	if manual_throttle > 0.02:
		_last_manual_throttle = manual_throttle
		_manual_age = 0.0
	if brake_input > 0.0 or handbrake_input > 0.0 or not run.engine_running or not controls_enabled or speed < -0.1:
		cancel_cruise()
	throttle_input = manual_throttle if manual_throttle > 0.02 else (cruise_amount if cruise_enabled else 0.0)
	# The brake has priority even while W/touch throttle remains held.
	throttle_input *= 1.0 - brake_input
	throttle_input *= 1.0 - handbrake_input * 0.95
	surface_info = world.terrain.sample(position.x, position.z)
	var offroad := float(surface_info.road) < 0.5
	var grip := run.tyre_grip(surface_info) * world.weather_grip
	var direction := Vector3(cos(heading), 0, sin(heading))
	var ahead := position + direction * (WHEELBASE * 0.5)
	var behind := position - direction * (WHEELBASE * 0.5)
	var slope := (world.height_at(ahead.x, ahead.z) - world.height_at(behind.x, behind.z)) / WHEELBASE
	if throttle_input > 0.0 or brake_input == 0.0 or handbrake_input > 0.0:
		_reverse_time = 0.0
		_reverse_engaged = false
	elif absf(speed) < 0.08:
		_reverse_time += dt
		if _reverse_time >= 0.45:
			_reverse_engaged = true
	elif not _reverse_engaged:
		_reverse_time = 0.0
	var acceleration := -GRAVITY * slope / (1.0 + slope * slope) if is_on_floor() else 0.0
	var load_factor := 1.0 + run.cargo_weight() / 1000.0
	var engine_force := (9.0 if run.differential_locked else 8.0) * run.power_factor() / load_factor
	var speed_limit := run.drive_speed_limit()
	# Progressively reduce drive near the selected range limit. Braking and
	# downhill gravity remain physical, without an instantaneous speed change.
	var governor := 1.0 - smoothstep(speed_limit * 0.82, speed_limit, maxf(speed, 0.0))
	var effort := brake_input if _reverse_engaged else throttle_input
	var traction := _soil_traction(dt, grip, engine_force, effort if run.engine_running else 0.0)
	if run.engine_running and is_on_floor():
		acceleration += governor * (minf(throttle_input * engine_force, traction) if float(surface_info.get("soil_softness", 0.0)) > 0.01 else throttle_input * minf(engine_force, traction))
		if _reverse_engaged and throttle_input == 0.0:
			acceleration -= minf(brake_input * engine_force * 0.85, traction) if float(surface_info.get("soil_softness", 0.0)) > 0.01 else brake_input * minf(engine_force * 0.85, traction)
	speed += acceleration * dt
	# Fluid resistance grows with movement; it must not behave like a parking
	# brake at zero speed. Weak vehicles can crawl out of a shallow ford.
	speed = move_toward(speed, 0.0, dt * (preload("res://scripts/surface_resistance.gd").deceleration(surface_info, speed) + soil_resistance))
	var water_depth := maxf(0.0, float(surface_info.get("water", 0.0)))
	# Hydrodynamic drag grows with depth and speed, while remaining zero at
	# rest so a serviceable 4x4 can crawl out under its own power.
	# Hydraulic/pedal take-up is progressive. DriveRun owns type, condition,
	# cargo and traction limits; pressure is applied once, after driving forces.
	_brake_pressure = move_toward(_brake_pressure, brake_input, dt * (4.0 if brake_input > _brake_pressure else 8.0))
	if not _reverse_engaged and _brake_pressure > 0.0:
		speed = move_toward(speed, 0.0, dt * _brake_pressure * run.brake_deceleration(grip, absf(speed)))
		if absf(speed) < 0.08:
			stop_reason = "brake"
	if handbrake_input > 0.0 and is_on_floor():
		speed = move_toward(speed, 0.0, dt * handbrake_input * (4.5 + grip * 4.5))
		if absf(speed) < 0.08: stop_reason = "handbrake"
	if brake_input < 0.02 and speed > speed_limit:
		speed = move_toward(speed, speed_limit, dt * 3.5)
	speed = clampf(speed, -5.0, 34.0)
	var steering_limit := lerpf(0.56, 0.18, smoothstep(3, 23, absf(speed))) * (0.82 if run.differential_locked else 1.0)
	var steering_rate := 2.8 if absf(steering_input) < 0.02 else 1.9
	wheel_angle = move_toward(wheel_angle, steering_input * steering_limit, steering_rate * dt)
	var yaw_limit := minf(grip * 6.5 / maxf(absf(speed), 2.0), 1.15)
	var requested := clampf(speed / WHEELBASE * tan(wheel_angle), -yaw_limit, yaw_limit)
	_yaw_rate = lerpf(_yaw_rate, requested if is_on_floor() else 0.0, 1.0 - exp(-(18.0 if absf(steering_input) < 0.02 else 9.0) * dt))
	if absf(speed) < 0.08:
		_yaw_rate = 0.0
	heading = wrapf(heading + _yaw_rate * dt, -PI, PI)
	var lateral_damping := 7.0 + grip * 16.0 if is_on_floor() else 0.4
	if handbrake_input > 0.0 and absf(speed) > 2.5 and is_on_floor(): lateral_damping *= 0.25
	_lateral_speed = (_lateral_speed - speed * _yaw_rate * dt) * exp(-lateral_damping * dt)
	_lateral_speed += world.wind_force * dt
	direction = Vector3(cos(heading), 0, sin(heading))
	var right := Vector3(-sin(heading), 0, cos(heading))
	velocity.x = direction.x * speed + right.x * _lateral_speed
	velocity.z = direction.z * speed + right.z * _lateral_speed
	if controls_enabled and winch_pull_velocity.is_finite():
		var pull := winch_pull_velocity.limit_length(2.4)
		velocity.x += pull.x
		velocity.z += pull.z
	var falling_speed := velocity.y
	var was_grounded := is_on_floor()
	velocity.y -= GRAVITY * dt
	rotation = Vector3(body_roll, -heading, body_pitch)
	var previous_speed := absf(speed)
	var before_move := position
	var _recoveries_before_move := boundary_recoveries
	var intended_motion := Vector3(velocity.x, 0.0, velocity.z) * dt
	move_and_slide()
	belly_contact = false
	var has_obstacle := false
	var terrain_edge := false
	var floor_cos := cos(floor_max_angle)
	var obstacle_normals: Array[Vector3] = []
	var terrain_normals: Array[Vector3] = []
	for i in get_slide_collision_count():
		var hit := get_slide_collision(i)
		for contact_index in hit.get_collision_count():
			if _is_terrain(hit.get_collider(contact_index)) and hit.get_local_shape(contact_index) == get_node("ChassisCollision") and hit.get_normal(contact_index).y > 0.6 and sink_depth > 0.12:
				belly_contact = true
		var normal := hit.get_normal()
		last_collision_normal = normal
		var hit_body := hit.get_collider()
		if hit_body is RigidBody3D and hit_body.mass < 100.0 and normal.y < floor_cos:
			_push_debris(hit_body, normal)
		elif _is_terrain(hit_body):
			terrain_contacts += 1
			if normal.y < floor_cos:
				terrain_edge = true
				terrain_normals.append(normal)
		elif normal.y < floor_cos:
			has_obstacle = true
			obstacle_normals.append(normal)
	# Concave mesh edge normals can be horizontal even when the adjacent faces
	# are shallow. A bounded, fully swept step verifies clearance AND a real
	# driveable landing. No collision mask/exclusion changes or height teleports.
	var actual_motion := Vector3(position.x - before_move.x, 0.0, position.z - before_move.z)
	if terrain_edge and not has_obstacle and (was_grounded or is_on_floor()):
		var remaining := intended_motion - actual_motion
		if remaining.dot(intended_motion) > intended_motion.length_squared() * 0.15:
			if _try_terrain_step(remaining):
				terrain_steps += 1
			else:
				terrain_step_rejections += 1
				has_obstacle = true
				obstacle_normals.append_array(terrain_normals)
	elif terrain_edge and not has_obstacle and not was_grounded and not is_on_floor():
		has_obstacle = true
		obstacle_normals.append_array(terrain_normals)
	if is_on_floor() and not was_grounded and falling_speed < -7.0:
		run.impact((-falling_speed - 5.0) * (1.0 - float(run.parts.suspension) * 0.35))
	# Rough ground is a cost at speed, not an invisible speed cap. Water remains wadeable.
	if is_on_floor() and absf(speed) > 11.0:
		run.parts.suspension = maxf(0.0, float(run.parts.suspension) - dt * float(surface_info.roughness) * (absf(speed) - 11.0) * 0.00007)
	if has_obstacle:
		# Resolve only normals of actual blockers. move_and_slide can report a
		# wall and zero its velocity on an almost flat floor/triangle edge; its
		# output must not become the drivetrain's stored longitudinal speed.
		var resolved := direction * speed + right * _lateral_speed
		for normal: Vector3 in obstacle_normals:
			var horizontal := Vector3(normal.x, 0.0, normal.z).normalized()
			if resolved.dot(horizontal) < 0.0:
				resolved = resolved.slide(horizontal)
		speed = resolved.dot(direction)
		_lateral_speed = resolved.dot(right)
		last_speed_loss = maxf(0.0, previous_speed - absf(speed))
		if last_speed_loss > 0.1:
			obstacle_impacts += 1
			stop_reason = "obstacle"
			run.impact(last_speed_loss)
		if last_speed_loss > 2.0: cancel_cruise()
	else:
		last_speed_loss = 0.0
	last_motion_distance = position.distance_to(before_move)
	stationary_time = stationary_time + dt if last_motion_distance < dt * 0.15 and throttle_input > 0.02 else 0.0
	if previous_speed > 2.0 and absf(speed) < 0.1 and not has_obstacle:
		unexplained_stops += 1
		stop_reason = "unexplained"
	elif absf(speed) > 0.5 and not has_obstacle:
		stop_reason = ""
	elif absf(speed) < 0.08 and not has_obstacle and stop_reason not in ["obstacle", "unexplained", "fallen_recovery"]:
		if handbrake_input > 0.0:
			stop_reason = "handbrake"
		elif brake_input > 0.0:
			stop_reason = "brake"
		elif not run.engine_running:
			stop_reason = "engine_off"
		elif throttle_input > 0.02:
			stop_reason = "water_drag" if float(surface_info.get("water", 0.0)) > 0.03 else "grade_or_resistance"
		else:
			stop_reason = "coasting"
	if sink_depth > 0.20 and absf(speed) < 0.15 and effort > 0.02:
		stop_reason = "high_centered" if belly_contact or wheel_support < 0.3 else "bogged"
	_ensure_safe_position()
	# Only swept vehicle motion counts. Rescue moves and world-origin rebasing
	# happen outside this interval and must never award kilometres.
	run.odometer += Vector2(position.x - before_move.x, position.z - before_move.z).length() if boundary_recoveries == _recoveries_before_move else 0.0
	run.distance = maxf(run.distance, position.x)
	run.advance(dt, speed, throttle_input, offroad, cruise_enabled and manual_throttle < 0.02, maxf(_brake_pressure, handbrake_input * 0.6), water_depth)
	_update_visual(dt, slope)
	wheel_speed = speed + (-1.0 if _reverse_engaged else 1.0) * soil_slip * (effort if run.engine_running else 0.0) * 9.0


func _push_debris(body: RigidBody3D, normal: Vector3) -> void:
	var id := body.get_instance_id()
	if _physics_age - float(_debris_contacts.get(id, -10.0)) < 0.20: return
	var push := -Vector3(normal.x, 0, normal.z).normalized()
	if push.length_squared() < 0.5: return
	var car_motion := Vector3(cos(heading) * speed - sin(heading) * _lateral_speed, 0, sin(heading) * speed + cos(heading) * _lateral_speed)
	var relative := car_motion - Vector3(body.linear_velocity.x, 0, body.linear_velocity.z)
	var closing := maxf(0, relative.dot(push))
	if closing < 0.25: return
	_debris_contacts[id] = _physics_age
	if _debris_contacts.size() > 64:
		for key: int in _debris_contacts.keys():
			if _physics_age - float(_debris_contacts[key]) > 2.0: _debris_contacts.erase(key)
	var delta_speed := minf(closing * 0.65, maxf(0, 12.0 - body.linear_velocity.dot(push)))
	body.apply_central_impulse(push * body.mass * delta_speed)
	if closing > 1.5:
		run.impact(1.0 + closing * 0.10)
		speed *= 0.94
		obstacle_impacts += 1
		cancel_cruise()


func _soil_traction(dt: float, grip: float, engine_force: float, effort: float) -> float:
	var soft := float(surface_info.get("soil_softness", 0.0))
	if soft <= 0.01:
		soil_slip = move_toward(soil_slip, 0.0, dt * 5.0)
		soil_resistance = 0.0
		return grip * GRAVITY * (1.0 if run.four_wheel_drive else 0.67)
	var tyre_type: String = run.variants.get("tires", "road")
	var suitable := 1.35 if tyre_type == "allterrain" else (1.30 if tyre_type == "winter" and float(surface_info.get("snow", 0)) > 0.4 else 1.0)
	# DriveRun.tyre_grip already applies tyre wear; do not penalize it twice.
	var burial := clampf(sink_depth / WHEEL_RADIUS, 0.0, 1.5)
	# Four driven wheels retain useful contact on a bank or in a shallow rut.
	# A genuinely grounded belly in deep peat can still unload all four tyres.
	var shallow := 1.0 - smoothstep(0.18, 0.42, sink_depth)
	var driven_support := wheel_support if run.four_wheel_drive else rear_wheel_support
	var support := maxf(lerpf(0.025, 0.82 if run.four_wheel_drive else 0.52, shallow), driven_support * (0.12 if belly_contact else 1.0))
	var available := grip * GRAVITY * 1.28 * suitable * support / (1.0 + burial * soft * 1.1)
	available *= 1.40 if run.differential_locked else (1.0 if run.four_wheel_drive else 0.62)
	var demand := engine_force * effort
	var slip := clampf((demand - available) / maxf(demand, 0.01), 0.0, 1.0)
	soil_slip = lerpf(soil_slip, slip, 1.0 - exp(-8.0 * dt))
	soil_resistance = soft * (sink_depth * 2.6 + burial * burial * 0.8) / suitable
	# Saturated ground takes work to displace before a deep rut exists. Without
	# this bearing loss a worn car skated over bottomless peat while its mesh
	# deformation always lagged behind the moving tyres. Ordinary soil is zero.
	var saturation := smoothstep(0.80, 0.98, soft)
	var bearing_overload := maxf(0.0, (1.0 + run.cargo_weight() / 1000.0) / suitable / maxf(0.20, float(surface_info.get("soil_bearing", 2.0))) - 1.0)
	soil_resistance += saturation * bearing_overload * 1.4
	if belly_contact: soil_resistance += soft * 2.0
	# Spinning tread shears the supporting soil; a gentle pedal retains more grip.
	return available * (1.0 - soil_slip * soft * 0.45)


func _update_visual(dt: float, slope: float) -> void:
	wheel_contacts.clear()
	var front := 0.0
	var rear := 0.0
	var left := 0.0
	var right := 0.0
	var grounded := 0
	var total_sink := 0.0
	var support := 0.0
	var rear_support := 0.0
	var loaded_contacts: Array[Vector3] = []
	for wheel: Dictionary in _wheels:
		var pivot: Node3D = wheel.pivot
		var probe := global_transform * Vector3(pivot.position.x + _visual_rest_position.x, 0, pivot.position.z + _visual_rest_position.z)
		var p := world.to_local(probe)
		var query := PhysicsRayQueryParameters3D.create(probe + Vector3.UP * 1.0, probe - Vector3.UP * 3.0)
		query.exclude = [get_rid()]
		var hit := get_world_3d().direct_space_state.intersect_ray(query)
		if not hit.is_empty() and _is_terrain(hit.collider):
			p = world.to_local(hit.position)
			grounded += 1
			if probe.y - WHEEL_RADIUS - world.to_global(p).y < 0.30:
				loaded_contacts.append(p)
		else: p.y = world.height_at(p.x, p.z)
		wheel_contacts.append(p)
		total_sink += maxf(0.0, world.height_at(p.x, p.z) + world.terrain.deformation.depth_at(p.x, p.z) - p.y)
		var contact_support := clampf(1.0 - maxf(0.0, probe.y - WHEEL_RADIUS - world.to_global(p).y - 0.05) / 0.25, 0.0, 1.0)
		support += contact_support
		if not wheel.front: rear_support += contact_support
		if wheel.front: front += p.y
		else: rear += p.y
		if pivot.position.z > 0: left += p.y
		else: right += p.y
	sink_depth = total_sink / maxf(1.0, wheel_contacts.size())
	wheel_support = support / maxf(1.0, wheel_contacts.size())
	rear_wheel_support = rear_support / 2.0
	var pitch := atan((front - rear) / (2.0 * WHEELBASE)) if wheel_contacts.size() == 4 else atan(slope)
	# Positive local Z is the left tyre row. Positive X rotation lowers that
	# row, so cross-slope must have the opposite sign. The old sign leaned the
	# physical contact rig away from the bank and lifted an entire side.
	var roll := -atan((left - right) / (2.0 * WHEEL_TRACK)) if wheel_contacts.size() == 4 else 0.0
	var has_support := grounded >= 2 and is_on_floor()
	body_pitch = lerp_angle(body_pitch, clampf(pitch, -0.55, 0.55) if has_support else 0.0, 1.0 - exp(-18.0 * dt))
	body_roll = lerp_angle(body_roll, clampf(roll, -0.48, 0.48) if has_support else 0.0, 1.0 - exp(-20.0 * dt))
	# The actual collision rig follows the supporting plane. Only the small
	# suspension residual/body lean belongs to the visual mesh.
	_visual.rotation.z = _visual_rest_rotation.z + (clampf(pitch - body_pitch, -0.10, 0.10) if has_support else 0.0)
	_visual.rotation.x = _visual_rest_rotation.x + (clampf(roll - body_roll, -0.08, 0.08) if has_support else 0.0) + clampf(-speed * _yaw_rate * 0.002, -0.025, 0.025)
	if grounded >= 3 and is_on_floor():
		var target_y := (front + rear) * 0.25 + WHEEL_RADIUS - global_position.y + _visual_rest_position.y
		# Do not visually bury the body through its physical supporting belly.
		_visual.position.y = lerpf(_visual.position.y, clampf(target_y, _visual_rest_position.y - (0.04 if sink_depth > 0.12 else 0.5), _visual_rest_position.y + 0.5), 1.0 - exp(-18.0 * dt))
	else: _visual.position.y = lerpf(_visual.position.y, _visual_rest_position.y, 1.0 - exp(-6.0 * dt))
	if grounded >= 3 and is_on_floor() and absf(speed) > 0.6 and _last_compaction.distance_to(position) > 0.65:
		world.compact_tyres(loaded_contacts, surface_info, 1.0 + run.cargo_weight() / 1000.0, Vector2(cos(heading), sin(heading)), clampf(soil_slip + absf(_lateral_speed) * 0.2, 0.0, 1.0))
		_last_compaction = position
	_soil_timer += dt
	if _soil_timer >= 0.10:
		if grounded >= 3 and is_on_floor():
			var tyre_type: String = run.variants.get("tires", "road")
			var footprint := 1.30 if tyre_type == "allterrain" else (1.18 if tyre_type == "winter" else 1.0)
			footprint *= lerpf(0.75, 1.0, float(run.parts.tires))
			var pressure := (1.0 + run.cargo_weight() / 1000.0) / footprint
			var effort := (brake_input if _reverse_engaged else throttle_input) if run.engine_running else 0.0
			world.load_tyres(loaded_contacts, pressure * maxf(wheel_support, 0.12), soil_slip, effort, _soil_timer)
		_soil_timer = 0.0
	_spin -= wheel_speed * dt / WHEEL_RADIUS
	_road_spin -= speed * dt / WHEEL_RADIUS
	max_wheel_contact_error = 0.0
	for index in _wheels.size():
		var wheel: Dictionary = _wheels[index]
		var pivot: Node3D = wheel.pivot
		if grounded >= 3 and is_on_floor():
			var local_contact := _visual.global_transform.affine_inverse() * world.to_global(wheel_contacts[index] + Vector3.UP * WHEEL_RADIUS)
			pivot.position.y = move_toward(pivot.position.y, clampf(local_contact.y, float(wheel.rest_y) - 0.25, float(wheel.rest_y) + 0.25), dt * 2.0)
			max_wheel_contact_error = maxf(max_wheel_contact_error, absf(pivot.global_position.y - WHEEL_RADIUS - wheel_contacts[index].y))
		pivot.rotation.y = -wheel_angle if wheel.front else 0.0
		var contact := wheel.get("contact") as CollisionShape3D
		if contact != null: contact.rotation.y = pivot.rotation.y
		var spin_node: Node3D = wheel.spin
		spin_node.rotation.z = _spin if run.four_wheel_drive or not wheel.front else _road_spin
	var light_mode := (2 if run.high_beams else 1) if run.headlights and run.battery > 0.01 else 0
	if light_mode != _last_light_mode:
		_last_light_mode = light_mode
		for light: SpotLight3D in _lights:
			light.visible = light_mode > 0
			light.spot_range = 88.0 if light_mode == 2 else 55.0
			light.spot_angle = 28.0 if light_mode == 2 else 40.0
			light.light_energy = 7.0 if light_mode == 2 else 5.0
	var reversing := (speed < -0.1 or _reverse_engaged) and run.battery > 0.01
	var rear_mode := int(reversing) + 2 * int(brake_input > 0.0 and not _reverse_engaged) + 4 * int(run.headlights)
	# Material setters upload state to the renderer. Lamps change on transitions,
	# rather than invalidating every lens material on every physics tick.
	if rear_mode != _last_rear_mode:
		_last_rear_mode = rear_mode
		for material: StandardMaterial3D in _reverse_materials: material.emission_energy_multiplier = 3.0 if reversing else 0.0
		for light: SpotLight3D in _reverse_lights: light.visible = reversing
		for mat: StandardMaterial3D in _brake_materials:
			mat.emission_enabled = true
			mat.emission = Color(0.8, 0.025, 0.008)
			mat.emission_energy_multiplier = 2.5 if rear_mode & 2 else (0.5 if run.headlights else 0.0)
	if _snorkel.visible != run.snorkel_installed: _snorkel.visible = run.snorkel_installed
	# Headless verification has no listener/audio clock to drain playback on exit.
	if DisplayServer.get_name() == "headless" or AudioServer.get_driver_name() == "Dummy": return
	if run.engine_running and not _audio.playing:
		_audio.play()
	elif not run.engine_running and _audio.playing:
		_audio.stop()
	_audio.pitch_scale = 0.85 + absf(wheel_speed) / 40.0 + throttle_input * 0.15


func _prepare_model(node: Node) -> void:
	for child: Node in node.get_children():
		if child is MeshInstance3D:
			var instance := child as MeshInstance3D
			if str(child.name).begins_with("BrakeLamp"):
				var source := instance.material_override as StandardMaterial3D
				var material := source.duplicate() as StandardMaterial3D
				instance.material_override = material
				_brake_materials.append(material)
		if str(child.name).begins_with("Wheel_") and child is Node3D:
			var front := str(child.name).begins_with("Wheel_F")
			var pivot := child as Node3D
			var geometry := pivot.get_children()
			var spin_node := Node3D.new()
			spin_node.name = "AxleSpin"
			pivot.add_child(spin_node)
			for mesh: Node in geometry:
				mesh.reparent(spin_node, false)
			var suffix := str(pivot.name).trim_prefix("Wheel_")
			var contact := get_node_or_null("Contact_%s_%s" % [suffix[0], suffix[1]]) as CollisionShape3D
			_wheels.append({"pivot": pivot, "spin": spin_node, "front": front, "rest_y": pivot.position.y, "contact": contact})
		else:
			_prepare_model(child)


func set_touch(action: String, pressed: bool, source: int) -> void:
	if pressed:
		_touches[source] = action
	else:
		_touches.erase(source)


func clear_inputs() -> void:
	_touches.clear()
	throttle_input = 0.0
	brake_input = 0.0
	handbrake_input = 0.0
	steering_input = 0.0
	_brake_pressure = 0.0
	cancel_cruise()
	_last_manual_throttle = 0.0
	_manual_age = 100.0


func toggle_cruise() -> String:
	if cruise_enabled:
		cancel_cruise()
		return "Tempomat vypnutý."
	if not controls_enabled or not run.engine_running or speed <= 0.5 or _strength("brake") > 0.0:
		return "Tempomat zapni počas jazdy vpred so zapnutým motorom."
	var capture := _strength("throttle")
	if capture <= 0.02 and _manual_age <= 0.8:
		capture = _last_manual_throttle
	if capture <= 0.02:
		return "Podrž plyn a zapni tempomat."
	cruise_amount = capture
	cruise_enabled = true
	return "Tempomat zapnutý."


func toggle_drive_mode() -> String:
	if absf(speed) > 4.0: return "Pred zmenou pohonu spomaľ pod 15 km/h."
	run.four_wheel_drive = not run.four_wheel_drive
	if not run.four_wheel_drive: run.differential_locked = false
	cancel_cruise()
	return "4×4" if run.four_wheel_drive else "4×2 · zadný náhon"


func toggle_differential() -> String:
	if absf(speed) > 2.5: return "Pred zmenou uzávierky spomaľ pod 9 km/h."
	run.differential_locked = not run.differential_locked
	if run.differential_locked: run.four_wheel_drive = true
	cancel_cruise()
	return "Uzávierka zapnutá" if run.differential_locked else "Uzávierka vypnutá"


func cancel_cruise() -> void:
	cruise_enabled = false
	cruise_amount = 0.0


func _ensure_safe_position() -> void:
	if not boundary_recovery_enabled:
		return
	var ground := world.height_at(position.x, position.z)
	# A route worker may have a newer numeric profile while an older, real
	# collision floor still supports us. That is not a physical fall.
	var fallen := position.y < ground - 3.0
	if fallen:
		# Verify current collision, since is_on_floor() can still describe the
		# previous tick after a teleport or a streamed floor replacement.
		var query := PhysicsRayQueryParameters3D.create(global_position + Vector3.UP * 0.1, global_position - Vector3.UP * (WHEEL_RADIUS + 0.45))
		query.exclude = [get_rid()]
		var support := get_world_3d().direct_space_state.intersect_ray(query)
		if not support.is_empty() and support.normal.y > cos(floor_max_angle): fallen = false
	var escaped := absf(position.z - world.terrain.corridor_center(position.x)) > world.terrain.boundary_width(position.x) + 6.0
	# The physical wall handles ordinary shoulder contacts. The recovery path
	# is reserved for an invalid/outside spawn or a genuinely fallen vehicle.
	if fallen or escaped:
		var safe := world.safe_position(position)
		boundary_recoveries += 1
		position = safe
		reset_physics_interpolation()
		if fallen:
			park()
			stop_reason = "fallen_recovery"
		else:
			stop_reason = "boundary_recovery"


func _is_terrain(collider: Object) -> bool:
	return collider is StaticBody3D and collider.get_parent() is MeshInstance3D and collider.get_parent().name in ["SandAndTracks", "BridgeDeck"]


func _try_terrain_step(motion: Vector3) -> bool:
	if motion.length_squared() < 0.000001: return false
	var raised := global_transform
	if test_move(raised, Vector3.UP * TERRAIN_STEP): return false
	raised.origin.y += TERRAIN_STEP
	if test_move(raised, motion): return false
	raised.origin += motion
	var landing := KinematicCollision3D.new()
	if not test_move(raised, Vector3.DOWN * (TERRAIN_STEP + 0.08), landing): return false
	if not _is_terrain(landing.get_collider()): return false
	if landing.get_normal().y < cos(floor_max_angle):
		# A tyre can touch the rounded lip before its tread reaches the flat
		# top. Verify the real supporting face beneath the chassis instead of
		# mistaking that cylinder/edge contact normal for the road's slope.
		var query := PhysicsRayQueryParameters3D.create(raised.origin, raised.origin - Vector3.UP * (TERRAIN_STEP + WHEEL_RADIUS + 0.4))
		query.exclude = [get_rid()]
		var support := get_world_3d().direct_space_state.intersect_ray(query)
		if support.is_empty() or not _is_terrain(support.collider) or support.normal.y < cos(floor_max_angle): return false
	# All three sweeps succeeded; perform exactly the motions that were tested.
	move_and_collide(Vector3.UP * TERRAIN_STEP)
	move_and_collide(motion)
	move_and_collide(Vector3.DOWN * (TERRAIN_STEP + 0.08))
	apply_floor_snap()
	return true


func motion_diagnostic() -> String:
	if not controls_enabled: return ""
	if handbrake_input > 0.0: return "Ručná brzda · uvoľni ju pred rozjazdom"
	if brake_input > 0.0 and not _reverse_engaged: return "Brzdíš · pre cúvanie podrž brzdu po zastavení"
	if stationary_time < 0.8: return ""
	if run.water_ingress >= 1.0: return "Zatopené nasávanie · vytiahni auto a vysuš ho v servise"
	if run.fuel <= 0.0: return "Prázdna nádrž · doplň palivo z kufra"
	if float(run.parts.engine) <= 0.05: return "Poškodený motor · otvor vozidlo a oprav ho"
	if not run.engine_running:
		return "Vybitá batéria · použi štartovací zdroj" if run.battery < 0.08 else "Motor je vypnutý · naštartuj"
	if stop_reason == "high_centered": return "Podvozok leží na teréne · použi navijak"
	if stop_reason == "bogged": return "Zapadol si · uber plyn, skús cúvať alebo použi navijak"
	if stop_reason == "obstacle": return "Prekážka pred autom · skús cúvať a obísť ju"
	if stop_reason == "water_drag": return "Hlboká voda · skús plytšiu trasu alebo navijak"
	return "Nedostatok trakcie · skús 4×4, uzávierku alebo navijak"


func _strength(action: String) -> float:
	return maxf(Input.get_action_strength(action) if InputMap.has_action(action) else 0.0, 1.0 if action in _touches.values() else 0.0)


func park() -> void:
	speed = 0.0
	winch_pull_velocity = Vector3.ZERO
	velocity = Vector3.ZERO
	_yaw_rate = 0.0
	_lateral_speed = 0.0
	_reverse_time = 0.0
	_reverse_engaged = false
	_brake_pressure = 0.0
	wheel_speed = 0.0
	soil_slip = 0.0
	sink_depth = 0.0
	wheel_support = 1.0
	rear_wheel_support = 1.0
	belly_contact = false
	_soil_timer = 0.0
	body_pitch = 0.0
	body_roll = 0.0
	rotation = Vector3(0, -heading, 0)
	stop_reason = "park"
	clear_inputs()
