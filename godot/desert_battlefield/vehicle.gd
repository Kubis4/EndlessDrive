extends CharacterBody3D
## Lightweight bicycle steering, lateral tyre grip and terrain-following suspension.
const Surface = preload("res://desert_battlefield/terrain.gd")
@export var lane: int = 0
@export var route_phase: float = 0.0
@export var cruise_speed: float = 10.0
@export var half_track: float = 1.12
@export var half_wheelbase: float = 1.425
@export var wheel_radius: float = 0.53
var manual := false
var heading := 0.0
var speed := 0.0
var steer := 0.0
var age := 0.0
var track_clock := 0.0
var track_distance := 0.0
var wheel_contacts: Array[Vector3] = []
var previous_contacts: Array[Vector3] = []
var support_height := 0.0
var slip_amount := 0.0
var samples: Array[Vector3] = []
var sides: Array[Vector3] = []
var track_node: Node3D
var trail := preload("res://desert_battlefield/track_trail.gd").new()
var track_material: ShaderMaterial
var dust_clock := 0.0
var dust_jets: Array[GPUParticles3D] = []
var sand_grains: Array[GPUParticles3D] = []
@onready var body: Node3D = $Body
@onready var dust: GPUParticles3D = $Dust
@onready var specks: GPUParticles3D = $AirborneSpecks
@onready var soil = get_parent().get_parent().soil

func _ready() -> void:
	# Spread imprint/mesh work across physics ticks instead of rebuilding all
	# three vehicles' track histories in the same frame.
	track_clock = float(lane) * 0.025
	var tangent := Surface.route(route_phase + 0.01, lane) - Surface.route(route_phase, lane)
	heading = atan2(-tangent.x, -tangent.z)
	velocity = tangent.normalized() * cruise_speed
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

func reset_to_route() -> void:
	position = Surface.route(route_phase, lane) + Vector3.UP * (wheel_radius + 0.02)
	velocity = Vector3.ZERO
	previous_contacts.clear()
	trail.break_trail()

func _physics_process(delta: float) -> void:
	age += delta
	var throttle := 1.0
	var handbrake := false
	if manual:
		throttle = float(Input.is_physical_key_pressed(KEY_W) or Input.is_physical_key_pressed(KEY_UP)) - float(Input.is_physical_key_pressed(KEY_S) or Input.is_physical_key_pressed(KEY_DOWN))
		var input_steer := float(Input.is_physical_key_pressed(KEY_A) or Input.is_physical_key_pressed(KEY_LEFT)) - float(Input.is_physical_key_pressed(KEY_D) or Input.is_physical_key_pressed(KEY_RIGHT))
		steer = move_toward(steer, input_steer * 0.52, delta * 1.7)
		handbrake = Input.is_physical_key_pressed(KEY_SHIFT)
	else:
		# Closest route point prevents the target running away after a collision.
		var nearest := route_phase
		var distance := INF
		for i in range(-12, 31):
			var t := route_phase + float(i) * 0.018
			var p := Surface.route(t, lane)
			var d := Vector2(p.x - position.x, p.z - position.z).length_squared()
			if d < distance:
				distance = d
				nearest = t
		route_phase = nearest
		var target := Surface.route(route_phase + 0.26, lane)
		var desired := atan2(-(target.x - position.x), -(target.z - position.z))
		steer = move_toward(steer, clampf(wrapf(desired - heading, -PI, PI) * 1.15, -0.55, 0.55), delta * 0.8)
		throttle = clampf((cruise_speed - velocity.length()) * 0.55, -0.35, 1.0)
	var forward := Vector3(-sin(heading), 0, -cos(heading))
	speed = velocity.dot(forward)
	heading += speed / (half_wheelbase * 2.0) * tan(steer) * delta
	forward = Vector3(-sin(heading), 0, -cos(heading))
	var lateral := Vector3(-forward.z, 0, forward.x)
	var side_speed := velocity.dot(lateral)
	slip_amount = clampf(absf(side_speed) / maxf(absf(speed), 2.0) + (0.55 if handbrake else 0.0), 0.0, 1.0)
	var grip := 1.15 if handbrake else 4.1
	velocity += forward * throttle * 7.2 * delta
	velocity -= lateral * side_speed * minf(1.0, grip * delta)
	velocity -= velocity * (0.14 + (0.85 if handbrake else 0.0)) * delta
	var ground_normal := Surface.normal_at(position.x, position.z)
	velocity += Vector3(ground_normal.x, 0, ground_normal.z) * 2.0 * delta
	velocity.y = 0.0
	$CollisionShape3D.rotation.y = heading
	move_and_slide()
	if absf(position.x) > 125 or absf(position.z) > 125:
		reset_to_route()
	# Fit suspension to all four displaced tyre contacts rather than the chassis centre.
	wheel_contacts.clear()
	for side in [-1.0, 1.0]:
		for axle in [half_wheelbase, -half_wheelbase]:
			var contact: Vector3 = position + lateral * side * half_track + forward * axle
			contact.y = soil.height_at(contact.x, contact.z)
			wheel_contacts.append(contact)
	support_height = (wheel_contacts[0].y + wheel_contacts[1].y + wheel_contacts[2].y + wheel_contacts[3].y) * 0.25
	position.y = support_height + wheel_radius + 0.02
	var right_slope := (wheel_contacts[2].y + wheel_contacts[3].y - wheel_contacts[0].y - wheel_contacts[1].y) / (4.0 * half_track)
	var forward_slope := (wheel_contacts[0].y + wheel_contacts[2].y - wheel_contacts[1].y - wheel_contacts[3].y) / (4.0 * half_wheelbase)
	ground_normal = (Vector3.UP - lateral * right_slope - forward * forward_slope).normalized()
	var right := forward.cross(ground_normal).normalized()
	var slope_basis := Basis(right, ground_normal, right.cross(ground_normal)).orthonormalized()
	body.basis = body.basis.slerp(slope_basis, 1.0 - exp(-delta * 9.0))
	body.position.y = sin(age * 14.0) * minf(0.025, velocity.length() * 0.002)
	for wheel: Node3D in $Body/Wheels.get_children():
		wheel.rotation.y = steer if wheel.get_meta("front", false) else 0.0
		wheel.get_node("Spin").rotate_x(-speed * delta / wheel_radius)
	dust_clock += delta
	_update_dust(forward, lateral)
	track_clock += delta
	if track_clock > 0.075 and velocity.length() > 0.8:
		track_clock = 0.0
		if previous_contacts.size() == 4:
			for i in 4: soil.stamp_segment(previous_contacts[i], wheel_contacts[i], forward, slip_amount)
		previous_contacts.assign(wheel_contacts)
		samples.append(position - forward * half_wheelbase)
		sides.append(right)
		if samples.size() > 650:
			samples.pop_front()
			sides.pop_front()
		trail.append(position - forward * half_wheelbase, lateral)
	elif velocity.length() <= 0.8:
		previous_contacts.assign(wheel_contacts)

func _update_dust(forward: Vector3, right: Vector3) -> void:
	var movement := velocity.length()
	var intensity := clampf(movement / 12.0 + slip_amount * 0.55, 0.05, 1.0)
	var wake := position - forward * 2.0
	wake.y = soil.height_at(wake.x, wake.z) + 0.38
	dust.global_position = wake
	specks.global_position = wake
	var refresh := dust_clock >= 0.05
	var detail := clampf(65.0 / get_parent().get_parent().camera.size, 0.45, 1.0)
	if refresh:
		dust_clock = 0.0
		dust.emitting = movement > 1.4
		dust.amount_ratio = intensity
		specks.emitting = movement > 1.4
		specks.amount_ratio = intensity * detail
		var wake_material := dust.process_material as ParticleProcessMaterial
		wake_material.direction = (-forward * 0.5 + Vector3(0.35, 0.6, 0.12)).normalized()
	for i in 2:
		var side := -1.0 if i == 0 else 1.0
		var contact := wheel_contacts[1 if i == 0 else 3] + Vector3.UP * 0.13
		var jet := dust_jets[i]
		var grains := sand_grains[i]
		var kick := (-forward * 0.75 + right * side * (0.45 + slip_amount) + Vector3.UP * 0.65).normalized()
		for emitter in [jet, grains]:
			emitter.global_position = contact
			if not refresh: continue
			emitter.emitting = movement > 0.9
			emitter.amount_ratio = intensity * (detail if emitter == grains else 1.0)
			var process := emitter.process_material as ParticleProcessMaterial
			process.direction = kick
			process.initial_velocity_min = 0.9 + movement * 0.08
			process.initial_velocity_max = 2.6 + movement * 0.12 + slip_amount * 3.5
