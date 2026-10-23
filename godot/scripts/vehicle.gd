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
var steering_input := 0.0
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
var _visual: Node3D
var _wheels: Array[Dictionary] = []
var _lights: Array[SpotLight3D] = []
var _audio: AudioStreamPlayer3D
var _touches: Dictionary = {}
var _brake_materials: Array[StandardMaterial3D] = []
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
var _soil_timer := 0.0


func _ready() -> void:
	physics_interpolation_mode = Node.PHYSICS_INTERPOLATION_MODE_ON
	floor_snap_length = 0.8
	floor_max_angle = deg_to_rad(48)
	floor_constant_speed = true
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
	_ensure_safe_position()
	var manual_throttle := _strength("throttle") if controls_enabled else 0.0
	brake_input = _strength("brake") if controls_enabled else 0.0
	steering_input = (_strength("right") - _strength("left")) if controls_enabled else 0.0
	_manual_age += dt
	if manual_throttle > 0.02:
		_last_manual_throttle = manual_throttle
		_manual_age = 0.0
	if brake_input > 0.0 or not run.engine_running or not controls_enabled or speed < -0.1:
		cancel_cruise()
	throttle_input = manual_throttle if manual_throttle > 0.02 else (cruise_amount if cruise_enabled else 0.0)
	# The brake has priority even while W/touch throttle remains held.
	throttle_input *= 1.0 - brake_input
	surface_info = world.terrain.sample(position.x, position.z)
	var offroad := float(surface_info.road) < 0.5
	var grip := run.tyre_grip(surface_info) * world.weather_grip
	var direction := Vector3(cos(heading), 0, sin(heading))
	var ahead := position + direction * (WHEELBASE * 0.5)
	var behind := position - direction * (WHEELBASE * 0.5)
	var slope := (world.height_at(ahead.x, ahead.z) - world.height_at(behind.x, behind.z)) / WHEELBASE
	if throttle_input > 0.0 or brake_input == 0.0:
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
	var engine_force := 14.0 * run.power_factor() / load_factor
	var effort := brake_input if _reverse_engaged else throttle_input
	var traction := _soil_traction(dt, grip, engine_force, effort if run.engine_running else 0.0)
	if run.engine_running:
		acceleration += minf(throttle_input * engine_force, traction) if float(surface_info.get("soil_softness", 0.0)) > 0.01 else throttle_input * minf(engine_force, traction)
		if _reverse_engaged and throttle_input == 0.0:
			acceleration -= minf(brake_input * engine_force * (13.2 / 14.0), traction) if float(surface_info.get("soil_softness", 0.0)) > 0.01 else brake_input * minf(13.2 * run.power_factor() / load_factor, traction)
	speed += acceleration * dt
	# Fluid resistance grows with movement; it must not behave like a parking
	# brake at zero speed. Weak vehicles can crawl out of a shallow ford.
	var standing_drag := lerpf(0.6, 0.12, smoothstep(0.03, 0.3, float(surface_info.get("water", 0.0))))
	speed = move_toward(speed, 0.0, dt * (0.82 + speed * speed * 0.011 + float(surface_info.drag) * (standing_drag + absf(speed) * 0.09) + soil_resistance))
	# Hydraulic/pedal take-up is progressive. DriveRun owns type, condition,
	# cargo and traction limits; pressure is applied once, after driving forces.
	_brake_pressure = move_toward(_brake_pressure, brake_input, dt * (4.0 if brake_input > _brake_pressure else 8.0))
	if not _reverse_engaged and _brake_pressure > 0.0:
		speed = move_toward(speed, 0.0, dt * _brake_pressure * run.brake_deceleration(grip, absf(speed)))
		if absf(speed) < 0.08:
			stop_reason = "brake"
	speed = clampf(speed, -22.0, 42.0)
	var steering_limit := 0.55 / (1.0 + absf(speed) * 0.035)
	wheel_angle = lerpf(wheel_angle, steering_input * steering_limit, 1.0 - exp(-12.0 * dt))
	var yaw_limit := minf(grip * 8.5 / maxf(absf(speed), 2.0), 1.45)
	var requested := clampf(speed / WHEELBASE * tan(wheel_angle), -yaw_limit, yaw_limit)
	_yaw_rate = lerpf(_yaw_rate, requested if is_on_floor() else 0.0, 1.0 - exp(-7.0 * dt))
	if absf(speed) < 0.08:
		_yaw_rate = 0.0
	heading = wrapf(heading + _yaw_rate * dt, -PI, PI)
	_lateral_speed = (_lateral_speed - speed * _yaw_rate * dt) * exp(-(5.0 + grip * 13.0) * dt)
	_lateral_speed += world.wind_force * dt
	direction = Vector3(cos(heading), 0, sin(heading))
	var right := Vector3(-sin(heading), 0, cos(heading))
	velocity.x = direction.x * speed + right.x * _lateral_speed
	velocity.z = direction.z * speed + right.z * _lateral_speed
	var falling_speed := velocity.y
	var was_grounded := is_on_floor()
	velocity.y -= GRAVITY * dt
	rotation.y = -heading
	var previous_speed := absf(speed)
	var before_move := position
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
			hit_body.apply_central_impulse(direction * absf(speed) * hit_body.mass * 0.5)
			run.impact(3.0 + absf(speed) * 0.12)
			speed *= 0.86
			obstacle_impacts += 1
			cancel_cruise()
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
	if previous_speed > 2.0 and absf(speed) < 0.1 and not has_obstacle:
		unexplained_stops += 1
		stop_reason = "unexplained"
	elif absf(speed) > 0.5 and not has_obstacle:
		stop_reason = ""
	elif absf(speed) < 0.08 and not has_obstacle and stop_reason not in ["obstacle", "unexplained", "fallen_recovery"]:
		if brake_input > 0.0:
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
	run.distance = maxf(run.distance, position.x)
	run.advance(dt, speed, throttle_input, offroad, cruise_enabled and manual_throttle < 0.02)
	_update_visual(dt, slope)
	wheel_speed = speed + (-1.0 if _reverse_engaged else 1.0) * soil_slip * (effort if run.engine_running else 0.0) * 9.0


func _soil_traction(dt: float, grip: float, engine_force: float, effort: float) -> float:
	var soft := float(surface_info.get("soil_softness", 0.0))
	if soft <= 0.01:
		soil_slip = move_toward(soil_slip, 0.0, dt * 5.0)
		soil_resistance = 0.0
		return grip * GRAVITY
	var tyre_type: String = run.variants.get("tires", "road")
	var suitable := 1.35 if tyre_type == "allterrain" else (1.30 if tyre_type == "winter" and float(surface_info.get("snow", 0)) > 0.4 else 1.0)
	var condition := lerpf(0.65, 1.0, float(run.parts.tires))
	var burial := clampf(sink_depth / WHEEL_RADIUS, 0.0, 1.5)
	var support := maxf(0.025, wheel_support * (0.12 if belly_contact else 1.0))
	var available := grip * GRAVITY * suitable * condition * support / (1.0 + burial * soft * 1.5)
	var demand := engine_force * effort
	var slip := clampf((demand - available) / maxf(demand, 0.01), 0.0, 1.0)
	soil_slip = lerpf(soil_slip, slip, 1.0 - exp(-8.0 * dt))
	soil_resistance = soft * (sink_depth * 4.0 + burial * burial * 1.2) / suitable
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
	var loaded_contacts: Array[Vector3] = []
	for wheel: Dictionary in _wheels:
		var pivot: Node3D = wheel.pivot
		var p := global_transform * Vector3(pivot.position.x + _visual_rest_position.x, 0, pivot.position.z + _visual_rest_position.z)
		var query := PhysicsRayQueryParameters3D.create(p + Vector3.UP * 1.0, p - Vector3.UP * 3.0)
		query.exclude = [get_rid()]
		var hit := get_world_3d().direct_space_state.intersect_ray(query)
		if not hit.is_empty() and _is_terrain(hit.collider):
			p.y = hit.position.y
			grounded += 1
			if global_position.y - WHEEL_RADIUS - p.y < 0.30:
				loaded_contacts.append(p)
		else: p.y = world.height_at(p.x, p.z)
		wheel_contacts.append(p)
		total_sink += maxf(0.0, world.height_at(p.x, p.z) + world.terrain.deformation.depth_at(p.x, p.z) - p.y)
		support += clampf(1.0 - maxf(0.0, global_position.y - WHEEL_RADIUS - p.y - 0.05) / 0.25, 0.0, 1.0)
		if wheel.front: front += p.y
		else: rear += p.y
		if pivot.position.z > 0: left += p.y
		else: right += p.y
	sink_depth = total_sink / maxf(1.0, wheel_contacts.size())
	wheel_support = support / maxf(1.0, wheel_contacts.size())
	var pitch := atan((front - rear) / (2.0 * WHEELBASE)) if wheel_contacts.size() == 4 else atan(slope)
	var roll := atan((left - right) / (2.0 * WHEEL_TRACK)) if wheel_contacts.size() == 4 else 0.0
	_visual.rotation.z = lerp_angle(_visual.rotation.z, _visual_rest_rotation.z + pitch, 1.0 - exp(-14.0 * dt))
	_visual.rotation.x = lerpf(_visual.rotation.x, _visual_rest_rotation.x + roll + clampf(-speed * _yaw_rate * 0.006, -0.06, 0.06), 1.0 - exp(-12.0 * dt))
	if grounded >= 3 and is_on_floor():
		var target_y := (front + rear) * 0.25 + WHEEL_RADIUS - global_position.y + _visual_rest_position.y
		# Do not visually bury the body through its physical supporting belly.
		_visual.position.y = lerpf(_visual.position.y, clampf(target_y, _visual_rest_position.y - (0.04 if sink_depth > 0.12 else 0.5), _visual_rest_position.y + 0.5), 1.0 - exp(-18.0 * dt))
	else: _visual.position.y = lerpf(_visual.position.y, _visual_rest_position.y, 1.0 - exp(-6.0 * dt))
	if grounded >= 3 and is_on_floor() and absf(speed) > 0.6 and _last_compaction.distance_to(position) > 0.65:
		world.compact_tyres(wheel_contacts, surface_info, 1.0 + run.cargo_weight() / 1000.0)
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
	max_wheel_contact_error = 0.0
	for index in _wheels.size():
		var wheel: Dictionary = _wheels[index]
		var pivot: Node3D = wheel.pivot
		if grounded >= 3 and is_on_floor():
			var local_contact := _visual.global_transform.affine_inverse() * (wheel_contacts[index] + Vector3.UP * WHEEL_RADIUS)
			pivot.position.y = move_toward(pivot.position.y, clampf(local_contact.y, float(wheel.rest_y) - 0.25, float(wheel.rest_y) + 0.25), dt * 2.0)
			max_wheel_contact_error = maxf(max_wheel_contact_error, absf(pivot.global_position.y - WHEEL_RADIUS - wheel_contacts[index].y))
		pivot.rotation.y = -wheel_angle if wheel.front else 0.0
		var contact := wheel.get("contact") as CollisionShape3D
		if contact != null: contact.rotation.y = pivot.rotation.y
		var spin_node: Node3D = wheel.spin
		spin_node.rotation.z = _spin
	for light: SpotLight3D in _lights:
		light.visible = run.headlights and run.battery > 0.01
	for mat: StandardMaterial3D in _brake_materials:
		mat.emission_enabled = true
		mat.emission = Color(0.8, 0.025, 0.008)
		mat.emission_energy_multiplier = 2.5 if brake_input > 0.0 else (0.5 if run.headlights else 0.0)
	# Headless verification has no listener/audio clock to drain playback on exit.
	if DisplayServer.get_name() == "headless": return
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
	return "Tempomat drží plyn na %d %%. Brzda ho vypne." % roundi(capture * 100.0)


func cancel_cruise() -> void:
	cruise_enabled = false
	cruise_amount = 0.0


func _ensure_safe_position() -> void:
	if not boundary_recovery_enabled:
		return
	var ground := world.height_at(position.x, position.z)
	var fallen := position.y < ground - 3.0
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
	if not _is_terrain(landing.get_collider()) or landing.get_normal().y < cos(floor_max_angle): return false
	# All three sweeps succeeded; perform exactly the motions that were tested.
	move_and_collide(Vector3.UP * TERRAIN_STEP)
	move_and_collide(motion)
	move_and_collide(Vector3.DOWN * (TERRAIN_STEP + 0.08))
	apply_floor_snap()
	return true


func _strength(action: String) -> float:
	return maxf(Input.get_action_strength(action), 1.0 if action in _touches.values() else 0.0)


func park() -> void:
	speed = 0.0
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
	belly_contact = false
	_soil_timer = 0.0
	stop_reason = "park"
	clear_inputs()
