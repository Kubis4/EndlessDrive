extends CharacterBody3D
## Four spring/damper contacts, force-limited tyres and ballistic flight.
const Surface = preload("res://desert_battlefield/terrain.gd")
const Tyres = preload("res://offroad/tyres.gd")
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
## Handbrake: kinetic share of tyre grip on the locked rear wheels.
const LOCKED_FRICTION := 0.8
## Locked differential: extra rolling loss per unit of steering lock (tyre scrub).
const LOCKED_SCRUB := 0.12
## Handbrake held during the last tick (rear wheels shown standing still).
var handbrake_on := false
## Traction (load × grip) of each tyre in the last step, for the open differential.
var wheel_traction := PackedFloat32Array([0, 0, 0, 0])
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
## Hull points resting on the ground this step (world space): the body ploughs
## the soil there too (bellying out in mud, sliding on the roof).
var hull_contacts: Array[Vector3] = []
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
## Live particles take their colour, sprite and size from the emitter's material
## every frame, so retinting one emitter would repaint dust already in the air.
## Each emitter therefore gets a copy per ground (base -> {surface: emitter}):
## crossing onto new ground stops the old copy, whose cloud settles unchanged,
## while the copy made for the new surface takes over.
var surface_emitters := {}
var active_emitters := {}
## Set when a surface copy took over during this dust update.
var emitter_switched := false
## Wheel index of each grain emitter (rear left/right, front left/right).
const GRAIN_WHEELS := [1, 3, 0, 2]
## Visual wheel rotation speed (rad/s) per wheel; driven tyres spin up when stuck.
var wheel_spin_rate: Array[float] = [0.0, 0.0, 0.0, 0.0]
## 0..1 how hard the driven tyres are spinning without progress.
var wheelspin := 0.0
var wheels: Array[Node3D] = []
var wheel_indices: Array[int] = []
## Solid axle pivots of rigs that model them (see endless_models.gd).
var axles: Array[Node3D] = []
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
	if body.get_meta("endless_drive_model", "jeep") == "m1009":
		# Full-size K5 Blazer chassis: heavier, softer and taller than the Jeep,
		# with the torquey 6.2 diesel geared low.
		curb_mass_kg = 2300.0
		engine_force = 12500.0
		spring_rate = 27000.0
		anti_roll_rate = 16000.0
		damping = 4300.0
		droop = 0.24
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
		# Own mesh+material per emitter so each wheel can throw its surface's
		# sprite (the authored quad is shared with the airborne specks).
		var quad := grains.draw_pass_1.duplicate() as QuadMesh
		quad.material = quad.material.duplicate()
		grains.draw_pass_1 = quad
		# Surface copies rescale the authored grain size (terrain.gd SPRITE_SIZE).
		var process := grains.process_material as ParticleProcessMaterial
		grains.set_meta("base_scale", Vector2(process.scale_min, process.scale_max))
	for wheel: Node3D in $Body/Wheels.get_children():
		if wheel.get_meta("axle", false):
			axles.append(wheel)
			continue
		wheels.append(wheel)
		wheel_indices.append((2 if wheel.position.x > 0 else 0) + (0 if wheel.get_meta("front", false) else 1))
	_add_axle_contacts()
	_build_lights()
	_install_grime()
	reset_to_route()

## Headlights, tail/brake lights and a reversing light, hung on the lamp markers
## and lenses both Endless Drive models carry (+X is forward in the rig).
var lights_on := true:
	set(value):
		lights_on = value
		for light in headlights: light.visible = value
		for glow in headlight_glows: glow.visible = value
		if lamp_material != null: lamp_material.set_shader_parameter("head_energy", HEAD_GLOW if value else 0.0)
var headlights: Array[SpotLight3D] = []
var headlight_glows: Array[MeshInstance3D] = []
## The model's own lamp surface lit from inside (rigs whose lamps are modelled,
## the M1009); then the painted lens quads are hidden.
var lamp_material: ShaderMaterial
const HEAD_GLOW := 2.2
var tail_lights: Array[OmniLight3D] = []
var tail_lens: StandardMaterial3D
var reversing_lights: Array[OmniLight3D] = []
## Tail lamp glow: parked/driving with lights, braking.
const TAIL_GLOW := Vector2(0.9, 4.0)
const REVERSE_GLOW := 3.0
## Painted headlight lens per model, measured from the lamp markers in an
## orthographic front and side views: [offset (forward, up, outward), lens
## size, round].
const HEADLAMPS := {
	"jeep": [Vector3(-0.06, 0.087, 0.017), Vector2(0.2, 0.13), false],
	"izh": [Vector3(0.05, -0.1, -0.127), Vector2(0.19, 0.19), true],
	"m1009": [Vector3(0.0, 0.0, 0.0), Vector2(0.24, 0.14), false],
}

func _build_lights() -> void:
	var headlamp: Array = HEADLAMPS.get(body.get_meta("endless_drive_model", "jeep"), HEADLAMPS["jeep"])
	var glow_material := ShaderMaterial.new()
	glow_material.shader = preload("res://desert_battlefield/lamp_glow.gdshader")
	glow_material.set_shader_parameter("round_lens", headlamp[2])
	var quad := QuadMesh.new()
	quad.size = headlamp[1]
	quad.material = glow_material
	for marker: Marker3D in body.find_children("Headlight*", "Marker3D", true, false):
		var centre: Vector3 = headlamp[0]
		centre.z *= signf(marker.position.z)
		var glow := MeshInstance3D.new()
		glow.name = "Lens"
		glow.mesh = quad
		glow.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		# The quad faces +Z; turn it to face forward (+X).
		glow.basis = Basis(Vector3.UP, PI * 0.5)
		glow.position = centre
		marker.add_child(glow)
		headlight_glows.append(glow)
		var beam := SpotLight3D.new()
		beam.name = "Beam"
		# Spot lights shine along -Z: turn it to +X and dip it slightly.
		beam.basis = Basis(Vector3.UP, -PI * 0.5) * Basis(Vector3.RIGHT, deg_to_rad(-5.0))
		beam.position = centre + Vector3(0.05, 0.0, 0.0)
		beam.light_color = Color(1.0, 0.93, 0.8)
		beam.light_energy = 5.0
		beam.spot_range = 32.0
		beam.spot_angle = 26.0
		beam.spot_angle_attenuation = 0.6
		beam.spot_attenuation = 0.8
		beam.light_specular = 0.4
		# Shadows follow the quality profile (battlefield.gd), never on phones.
		beam.shadow_enabled = false
		beam.distance_fade_enabled = true
		beam.distance_fade_begin = 70.0
		beam.distance_fade_length = 20.0
		marker.add_child(beam)
		headlights.append(beam)
	_light_model_lamps()
	var lamps: Array = body.find_children("BrakeLamp*", "MeshInstance3D", true, false)
	if not lamps.is_empty():
		# Own lens material per car, so one car braking does not light the other.
		tail_lens = (lamps[0].material_override as StandardMaterial3D).duplicate()
		tail_lens.emission_energy_multiplier = TAIL_GLOW.x
	for lamp: MeshInstance3D in lamps:
		lamp.material_override = tail_lens
		var glow := OmniLight3D.new()
		glow.name = "TailGlow"
		glow.light_color = Color(1.0, 0.08, 0.03)
		glow.light_energy = 0.15
		glow.omni_range = 2.6
		glow.light_specular = 0.2
		# 15 cm behind the lens (rig -X), expressed in the lamp's own frame.
		glow.position = lamp.basis.inverse() * Vector3(-0.15, 0.0, 0.0)
		lamp.add_child(glow)
		tail_lights.append(glow)
		# Reversing light: from each rear lamp cluster, lighting the ground behind.
		var reverse := OmniLight3D.new()
		reverse.name = "ReversingLight"
		reverse.light_color = Color(1.0, 0.97, 0.9)
		reverse.light_energy = 0.0
		reverse.omni_range = 4.0
		reverse.light_specular = 0.3
		reverse.position = lamp.basis.inverse() * Vector3(-0.1, 0.0, 0.0)
		reverse.visible = false
		lamp.add_child(reverse)
		reversing_lights.append(reverse)

## Lamps the model itself carries (a surface whose material is named "Lights"):
## lit by lamp_lights.gdshader; the overlay lens quads are hidden then.
func _light_model_lamps() -> void:
	for mesh_node: MeshInstance3D in body.find_children("*", "MeshInstance3D", true, false):
		if mesh_node.mesh == null or mesh_node.get_parent().name == "Spin": continue
		for s in mesh_node.mesh.get_surface_count():
			var source := mesh_node.mesh.surface_get_material(s) as BaseMaterial3D
			if source == null or not source.resource_name.begins_with("Lights"): continue
			lamp_material = ShaderMaterial.new()
			lamp_material.shader = preload("res://desert_battlefield/lamp_lights.gdshader")
			lamp_material.set_shader_parameter("albedo_texture", source.albedo_texture)
			lamp_material.set_shader_parameter("head_energy", HEAD_GLOW if lights_on else 0.0)
			lamp_material.set_shader_parameter("tail_energy", TAIL_GLOW.x * 1.5)
			mesh_node.set_surface_override_material(s, lamp_material)
	if lamp_material == null: return
	for glow in headlight_glows: glow.queue_free()
	headlight_glows.clear()
	for lamp: MeshInstance3D in body.find_children("BrakeLamp*", "MeshInstance3D", true, false):
		lamp.transparency = 1.0
		lamp.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF

## Brake lamps follow the brake pedal, the reversing light reverse gear.
func _update_lights(brake: float, throttle: float) -> void:
	# A parked car holding its brake keeps the lamps dark.
	# (A car floating through a ford still brakes, with no wheel on the bed.)
	var braking := brake > 0.05 and (grounded_wheels > 0 or water_submersion > 0.05) and absf(speed) > 0.2
	if tail_lens != null:
		tail_lens.emission_energy_multiplier = TAIL_GLOW.y if braking else (TAIL_GLOW.x if lights_on else 0.0)
	if lamp_material != null: lamp_material.set_shader_parameter("tail_energy", TAIL_GLOW.y * 1.5 if braking else (TAIL_GLOW.x * 1.5 if lights_on else 0.0))
	for glow in tail_lights:
		glow.light_energy = 1.1 if braking else (0.15 if lights_on else 0.0)
	var reversing := throttle < -0.05 and speed < 0.5
	for light in reversing_lights:
		light.visible = reversing
		light.light_energy = 0.6 if reversing else 0.0
	if lamp_material != null: lamp_material.set_shader_parameter("reverse_energy", REVERSE_GLOW if reversing else 0.0)

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
		# Hill hold: stopped with no pedal pressed, the brakes keep the car still.
		if pedal == 0.0 and absf(speed) < 0.4: brake = 1.0
		# Keys steer digitally; the touch steering zone is analog (action strength).
		turn = clampf(float(Input.is_physical_key_pressed(KEY_A) or Input.is_physical_key_pressed(KEY_LEFT)) - float(Input.is_physical_key_pressed(KEY_D) or Input.is_physical_key_pressed(KEY_RIGHT))
			+ _touch_strength("left") - _touch_strength("right"), -1.0, 1.0)
		handbrake = Input.is_physical_key_pressed(KEY_SHIFT) or Input.is_physical_key_pressed(KEY_SPACE) or _touch("handbrake")
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
	if get_meta("expedition_no_fuel", false): throttle = 0.0
	throttle *= float(get_meta("expedition_engine_factor", 1.0))
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
	last_brake = clampf(brake, 0.0, 1.0)
	handbrake_on = handbrake
	var steps := maxi(1, ceili(delta / MAX_STEP))
	var dt := delta / steps
	# The previous substep already sampled its final pose. Refresh once for
	# outside changes (soil stamps/reset), then reuse contacts between substeps.
	_sample_contacts()
	for step in steps:
		steer = move_toward(steer, clampf(turn, -1.0, 1.0) * steering_limit(), dt * 1.65)
		_integrate(dt, clampf(throttle, -1.0, 1.0), clampf(brake, 0.0, 1.0), handbrake)
	var limit := Surface.play_limit()
	if absf(position.x) > limit or absf(position.z) > limit or not position.is_finite():
		reset_to_route()
	_update_visuals(delta)
	_update_lights(brake, throttle)
	_update_grime(delta)
	_update_suspension()
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
	hull_contacts.clear()
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
		# A diff or axle tube on soft ground (mud, dirt, forest floor, grass,
		# snow, sand) ploughs a furrow (_update_tracks stamps it) instead of
		# holding the car up: it never beaches the car there and lifts a wheel
		# off. On rock, gravel or a road it does hold it.
		if index < axle_point.size() and axle_point[index] == 1 and gap < 0.015 and Surface.deform_at(point.x, point.z) > 0.75:
			hull_contacts.append(point)
			continue
		penetration = maxf(penetration, -gap)
		if gap < 0.015:
			chassis_grounded = true
			hull_contacts.append(point)
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
	var was_grounded := grounded
	grounded_wheels = 0
	var maximum_slip := 0.0
	var driven_load := 0.0
	var engine_output := 0.0
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
		# Rear wheels held by the handbrake take no share of the engine torque.
		if wheel_grounded[i] and is_driven(i) and not (handbrake and i % 2 == 1): driven_load += load
	# Traction of each tyre, for the open differentials below.
	var traction := PackedFloat32Array([0, 0, 0, 0])
	for i in 4:
		if wheel_grounded[i]: traction[i] = wheel_loads[i] * tyre_grip(wheel_surface[i])
	wheel_traction = traction
	for i in 4:
		drive_slip[i] = 0.0
		# Open diff: a lifted driven wheel takes all the torque and just spins.
		if is_driven(i) and not wheel_grounded[i] and not diff_locked and wheel_grounded[(i + 2) % 4] and absf(motor) * clampf((ground_limit - speed) / maxf(ground_limit * 0.15, 1.5), 0.0, 1.0) > 1.0 and not (handbrake and i % 2 == 1):
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
		var grip: float = tyre_grip(ground)
		var capacity := load * grip
		if handbrake and not front:
			# Handbrake: the rear wheel is locked. It takes no drive torque and
			# its contact patch slides, so kinetic friction acts against the
			# slide in any direction: it brakes and loses side grip together.
			var slide := Vector2(longitudinal_speed, lateral_speed)
			var sliding := slide.length()
			if sliding > 0.001:
				var friction := minf(capacity * LOCKED_FRICTION, sliding * mass_kg * 0.25 / dt) / sliding
				var locked_force := tyre_forward * (-slide.x * friction) + tyre_right * (-slide.y * friction)
				force += locked_force
				torque += arm.cross(locked_force)
			maximum_slip = maxf(maximum_slip, clampf(sliding / 3.0, 0.0, 1.0))
			continue
		var braking_force := brake * 13500.0 * 0.25
		# Loose sand keeps the tuned default; other grounds scale it by their rolling loss.
		var rolling: float = rolling_resistance * _tyre_rolling(ground) / float(Surface.ROLLING[Surface.SAND]) * tyre_type.get("rolling", 1.0)
		# Sinkage: in mud and the ford bed the deeper the rut, the harder the
		# tyre ploughs. A spinning, digging car can bury itself.
		if ground == Surface.MUD: rolling *= _mud_drag(i)
		# A locked axle cannot let its wheels turn at different speeds: in a bend
		# the tyres scrub, which drags the car (more the sharper the turn).
		if diff_locked and is_driven(i): rolling += LOCKED_SCRUB * absf(steer) / 0.55 * grip
		var resistance := minf(braking_force + load * rolling, absf(longitudinal_speed) * mass_kg * 0.25 / dt)
		var longitudinal := 0.0
		var pushing := false
		if is_driven(i) and driven_load > 0.0:
			var partner := (i + 2) % 4
			var requested := 0.0
			# Each wheel pushes only up to the speed its own ground and tyre allow.
			var wheel_motor := motor
			if motor > 0.0:
				var wheel_limit := wheel_speed_limit(ground)
				if diff_locked: wheel_limit = minf(wheel_limit, DIFF_LOCK_SPEED)
				wheel_motor *= clampf((wheel_limit - longitudinal_speed) / maxf(wheel_limit * 0.15, 1.5), 0.0, 1.0)
			if diff_locked:
				# Locked: each tyre takes torque in proportion to its load and
				# pushes up to its own grip (clamped with rolling loss below).
				requested = wheel_motor * load / driven_load
				longitudinal = requested
			else:
				# Open: equal torque to both sides of the axle; the side with less
				# traction sets it, the surplus only spins the tyres.
				var axle_load := load + (wheel_loads[partner] if wheel_grounded[partner] else 0.0)
				requested = wheel_motor * axle_load / driven_load * 0.5
				var limit := minf(traction[i], traction[partner]) + resistance
				longitudinal = clampf(requested, -limit, limit)
			if absf(requested) > 1.0:
				var net := absf(clampf(longitudinal - signf(longitudinal_speed) * resistance, -capacity, capacity) + signf(longitudinal_speed) * resistance)
				drive_slip[i] = clampf(1.0 - net / absf(requested), 0.0, 1.0)
				# Open diff: the axle's spare torque spins only the tyre with less
				# traction (ties go to the left one); its partner stands and pushes.
				if not diff_locked and wheel_grounded[partner] and (traction[i] > traction[partner] or (traction[i] == traction[partner] and i > partner)):
					drive_slip[i] = 0.0
			# What spins the tyre instead of moving the car.
			engine_output += absf(requested) * drive_slip[i] * SPIN_SPEED
			pushing = true
		longitudinal -= signf(longitudinal_speed) * resistance
		longitudinal = clampf(longitudinal, -capacity, capacity)
		# What the engine gives through this tyre: the push that the ground takes, at
		# road speed (the torque converter turns it faster than a crawling wheel).
		if pushing: engine_output += absf(longitudinal + signf(longitudinal_speed) * resistance) * maxf(absf(longitudinal_speed), CONVERTER_SPEED)
		var lateral_limit := sqrt(maxf(0.0, capacity * capacity - longitudinal * longitudinal))
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
	force -= velocity * velocity.length() * air_drag
	wheel_power = lerpf(wheel_power, engine_output, 1.0 - exp(-dt * 8.0))
	force += _bog_down_force()
	torque += bog_torque
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

## Average reachable speed over the loaded tyres' grounds (m/s). A locked
## differential binds the driveline, so the car is held to crawling speeds.
func ground_speed_limit() -> float:
	var total := 0.0
	var count := 0
	for i in 4:
		if wheel_grounded[i] or wheel_gaps[i] < droop:
			total += wheel_speed_limit(wheel_surface[i])
			count += 1
	var limit: float = total / count if count > 0 else top_speed * tyre_type.get("speed", 1.0)
	return minf(limit, DIFF_LOCK_SPEED) if diff_locked else limit

## How fast a wheel can go on a ground (m/s), with the fitted tyre.
func wheel_speed_limit(ground: int) -> float:
	return top_speed * _ground_speed_share(ground) * tyre_type.get("speed", 1.0)

func _ground_speed_share(ground: int) -> float:
	var share: float = Surface.speed(ground)
	if not tyre_type.is_empty(): share = minf(1.0, share * tyre_type.speed_on[ground])
	return share

## Fuel per distance against a road-tyred 4x2 on asphalt: each wheel by what its
## ground and tyre roll against (the driven ones more), more through two axles and
## a locked differential.
func fuel_factor() -> float:
	var total := 0.0
	var weights := 0.0
	for i in 4:
		var weight := 1.0 if is_driven(i) else 0.55
		var ground_loss: float = _tyre_rolling(wheel_surface[i]) if wheel_grounded[i] else float(Surface.ROLLING[Surface.ROAD])
		total += weight * (1.0 + (ground_loss - float(Surface.ROLLING[Surface.ROAD])) * FUEL_PER_ROLLING)
		weights += weight
	return total / weights * tyre_type.get("rolling", 1.0) * (FUEL_FOUR_WHEEL if four_wheel_drive else 1.0) * (FUEL_LOCKED if diff_locked else 1.0)

## What a ground rolls against with the fitted tyre: the part beyond a hard road's
## is the soft ground's drag, which a lugged or wide tyre feels less of.
func _tyre_rolling(ground: int) -> float:
	var loss: float = Surface.rolling(ground)
	var hard := float(Surface.ROLLING[Surface.ROAD])
	return hard + maxf(0.0, loss - hard) * tyre_type.get("soft", 1.0) if loss > hard else loss

## In mud the deeper the rut, the harder the tyre ploughs, a lugged or wide one less.
func _mud_drag(i: int) -> float:
	var soft: float = tyre_type.get("soft", 1.0)
	return 1.0 + soil.depth_at(wheel_contacts[i].x, wheel_contacts[i].z) * 5.0 * soft * soft

## The ramp a tyre digs out of a hole: well within what it can climb on this ground
## with its grip, the engine's pull and its rolling loss. In a hole the car twists, and an open differential gives
## only what the lighter wheel can take, so a locked one gets out by a steeper way.
## Half of it with two driven wheels.
func _climb_slope(i: int) -> float:
	var ground := wheel_surface[i]
	var loss: float = _tyre_rolling(ground) * tyre_type.get("rolling", 1.0) * (_mud_drag(i) if ground == Surface.MUD else 1.0)
	var margin: float = minf(tyre_grip(ground), engine_force / (mass_kg * GRAVITY)) - loss
	if not four_wheel_drive: margin *= 0.55
	return clampf(margin * 0.5, 0.06, 0.42 if diff_locked else 0.2)

const DIG_RATE := 1.6

## Air drag (N per (m/s)²; 0.5 · air density · Cd · frontal area).
var air_drag := 0.58
## Power going into the driven wheels (W, smoothed).
var wheel_power := 0.0
var last_brake := 0.0
const CONVERTER_SPEED := 2.5
const SPIN_SPEED := 6.0
## Idling, and litres per kWh at the crank of a pre-chamber diesel, best at high load.
const IDLE_LITRES_PER_HOUR := 1.5
const LITRES_PER_KWH := 0.29
const LITRES_PER_KWH_LIGHT := 0.14

## The share of the engine's power that gets through the driveline to the wheels: the
## front axle's gears in 4x4 and a locked differential cost more.
func driveline_efficiency() -> float:
	if not four_wheel_drive: return 0.85
	return 0.77 if diff_locked else 0.8

## Fuel flow now (l/h) from the engine's output: all the ground, the air, the slope, the
## load and spinning tyres cost fuel through this one figure.
func fuel_rate() -> float:
	var crank := minf(wheel_power / driveline_efficiency(), engine_power)
	var unused := 1.0 - crank / engine_power
	return IDLE_LITRES_PER_HOUR + crank / 1000.0 * (LITRES_PER_KWH + LITRES_PER_KWH_LIGHT * unused * unused)

## The M1009 as built (see README, Reálne údaje): 155 hp, 2359 kg, about 75 mph flat out,
## and the drag that gives what owners report at 60 and 75 mph.
## The real car's numbers (offroad/garage.gd), its engine as upgraded.
func fit_specs(spec: Dictionary, power_factor := 1.0) -> void:
	curb_mass_kg = spec.mass
	engine_power = spec.power * power_factor
	top_speed = spec.top
	air_drag = spec.drag

## How hard the tyres are spinning, on average (burns fuel without going anywhere).
func spin_share() -> float:
	var total := 0.0
	for i in 4: total += _tyre_spinning(i) if is_driven(i) and wheel_grounded[i] else 0.0
	return total / 4.0

const FUEL_PER_ROLLING := 7.0
const FUEL_FOUR_WHEEL := 1.18
const FUEL_LOCKED := 1.1

## The fitted tyre set (offroad/tyres.gd); empty: the model's own, neutral.
var tyre_type := {}
var fitted_materials: Array = []

func tyre_grip(ground: int) -> float:
	return Surface.grip(ground) * (tyre_type.grip[ground] if not tyre_type.is_empty() else 1.0)

## Puts a tyre set on: road tyres are the model's own; any other keeps its rim
## and gets the set's tyre, under the same dirt layer.
func fit_tyres(kind: String) -> void:
	tyre_type = Tyres.TYPES[kind]
	if fitted_materials.is_empty():
		var rubber := StandardMaterial3D.new()
		rubber.albedo_color = Color(0.045, 0.045, 0.045)
		var steel := StandardMaterial3D.new()
		steel.albedo_color = Color(0.46, 0.46, 0.48)
		steel.metallic = 0.8
		for source: StandardMaterial3D in [rubber, steel]:
			var material := _grime_material(source, true)
			fitted_materials.append(material)
			grime_materials.append(material)
	for wheel: Node3D in wheels:
		for node: MeshInstance3D in wheel.find_children("*", "MeshInstance3D", true, false):
			if node.name == "Lens": continue
			if node.has_meta("fitted"):
				node.get_parent().remove_child(node)
				node.queue_free()
				continue
			if not node.has_meta("stock_mesh"): node.set_meta("stock_mesh", node.mesh)
			var stock: Mesh = node.get_meta("stock_mesh")
			var dirt := node.get_surface_override_material(0)
			node.mesh = stock if kind == "road" else Tyres.rim_of(stock)
			node.set_surface_override_material(0, dirt)
			if kind == "road": continue
			var tyre := MeshInstance3D.new()
			tyre.name = "FittedTyre"
			tyre.set_meta("fitted", true)
			tyre.mesh = Tyres.mesh_of(kind)
			# (The sets are made for the M1009's wheels.)
			tyre.scale = Vector3.ONE * wheel_radius / Tyres.RADIUS
			for s in tyre.mesh.get_surface_count(): tyre.set_surface_override_material(s, fitted_materials[s])
			node.add_child(tyre)

## Highest speed the engine pushes to with a locked differential (30 km/h).
const DIFF_LOCK_SPEED := 30.0 / 3.6

## Slows a car that is faster than its ground allows, instead of letting it
## coast across at road speed: mud, water, clay and snow bog it down within a
## few seconds, rough ground (gravel, rock, grass) shakes speed off more gently,
## and a locked differential binds the driveline. Ice lets the car slide on.
## Each wheel drags by its own ground and at its own place, so one in the mud pulls
## the car towards it (the turning part in bog_torque).
var bog_torque := Vector3.ZERO

func _bog_down_force() -> Vector3:
	bog_torque = Vector3.ZERO
	if not grounded: return Vector3.ZERO
	var horizontal := Vector3(velocity.x, 0.0, velocity.z)
	var moving := horizontal.length()
	if moving < 0.01: return Vector3.ZERO
	var count := 0
	for i in 4:
		if wheel_grounded[i]: count += 1
	if count == 0: return Vector3.ZERO
	var along := horizontal / moving
	var total := Vector3.ZERO
	for i in 4:
		if not wheel_grounded[i]: continue
		var ground := wheel_surface[i]
		var excess := moving - wheel_speed_limit(ground)
		if excess <= 0.0 or Surface.grip(ground) < 0.2: continue
		var slowness := clampf((0.95 - _ground_speed_share(ground)) / 0.5, 0.0, 1.0)
		var deceleration := (minf(excess * 1.8, 8.0) if Surface.deform(ground) >= 0.5 else minf(excess * 0.9, 3.5)) * slowness / count
		var drag := -along * mass_kg * deceleration
		total += drag
		bog_torque += (wheel_contacts[i] - position).cross(drag) * 0.5
	if diff_locked:
		var over := moving - DIFF_LOCK_SPEED
		if over > 0.0 and total.length() < mass_kg * minf(over * 0.8, 3.0): total = -along * mass_kg * minf(over * 0.8, 3.0)
	return total

## Wading: hydrodynamic drag on the submerged body and partial (leaky) buoyancy,
## both applied through the centre of mass. Zero outside the ford.
func _water_force() -> Vector3:
	water_submersion = 0.0
	if Surface.water_depth_at(position.x, position.z) <= 0.0: return Vector3.ZERO
	var floor_y := position.y - (COM.y + 0.1) * maxf(attitude.y.y, 0.0)
	water_submersion = clampf(Surface.water_level_at(position.x, position.z) - floor_y, 0.0, 1.4)
	if water_submersion <= 0.0: return Vector3.ZERO
	# Drag against the water, not the ground: a current pushes a wading car
	# downstream, harder the deeper it sits (and the lighter buoyancy makes it).
	var flow := Surface.water_flow_at(position.x, position.z)
	var relative := Vector3(velocity.x - flow.x, 0.0, velocity.z - flow.y)
	var drag := -relative * relative.length() * 520.0 * water_submersion
	drag += -relative * 900.0 * water_submersion * water_submersion
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
		if handbrake_on and index % 2 == 1:
			# The handbrake holds the rear wheels: they slide without turning.
			wheel_spin_rate[index] = 0.0
			continue
		if driven and absf(last_throttle) > 0.15 and (wheel_grounded[index] or water_submersion > 0.0 or drive_slip[index] > 0.0):
			var slip_rate := last_throttle * 14.0 * (1.0 - clampf(absf(speed) / 6.0, 0.0, 1.0)) * _spin_share(index)
			# Torque the ground cannot take (open diff, ice, a lifted wheel) spins it.
			slip_rate += last_throttle * 22.0 * drive_slip[index]
			if signf(slip_rate) == signf(rate) or absf(rate) < 0.5: rate += slip_rate
		elif not wheel_grounded[index]:
			rate = wheel_spin_rate[index] * maxf(0.0, 1.0 - dt * 0.6)
		wheel_spin_rate[index] = move_toward(wheel_spin_rate[index], rate, maxf(dt, 1e-3) * 40.0)
		wheel.get_node("Spin").rotate_x(-wheel_spin_rate[index] * dt)
	# Solid axles sit between their two wheels: up and down with them, and
	# tilted across when one side rises (articulation).
	for axle in axles:
		var ends: Array[Node3D] = []
		for wheel in wheels:
			if wheel.get_meta("front", false) == axle.get_meta("front", false): ends.append(wheel)
		if ends.size() != 2: continue
		var left := ends[0] if ends[0].position.x < ends[1].position.x else ends[1]
		var right := ends[1] if left == ends[0] else ends[0]
		axle.position.y = (left.position.y + right.position.y) * 0.5
		axle.rotation.z = atan2(right.position.y - left.position.y, right.position.x - left.position.x)

## How hard a tyre spins under power (0..1); the handbrake holds the rear ones
## still, so they slide and throw nothing of their own.
func _tyre_spinning(wheel: int) -> float:
	if handbrake_on and wheel % 2 == 1: return 0.0
	return clampf(wheelspin + drive_slip[wheel] * absf(last_throttle), 0.0, 1.0)

## Share of a stuck car's wheelspin a driven tyre gets: a locked axle turns
## both tyres together; an open differential spins only the one with less
## traction (twice as fast, as the carrier sees their average) while the
## other stands.
func _spin_share(index: int) -> float:
	var partner := (index + 2) % 4
	if diff_locked or not is_driven(partner) or not wheel_grounded[partner] or not wheel_grounded[index]: return 1.0
	var weaker := wheel_traction[index] < wheel_traction[partner] or (wheel_traction[index] == wheel_traction[partner] and index < partner)
	return 2.0 if weaker else 0.0

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
			soil.tyre_sink = tyre_type.get("sink", 1.0)
			soil.stamp_segment(previous_contacts[i], wheel_contacts[i], -body.basis.z, slip_amount)
			previous_contacts[i] = wheel_contacts[i]
	# Wheelspin: full throttle with little forward progress excavates a hole
	# beneath the driven tyres (deepest drift profile), as in real soft sand.
	var spin := clampf(absf(last_throttle) * (1.0 - absf(speed) / 2.5), 0.0, 1.0)
	wheelspin = spin if (wheel_grounded[1] or wheel_grounded[3]) else 0.0
	# With the rear held by the handbrake only driven front tyres can spin.
	if handbrake_on and not (is_driven(0) and (wheel_grounded[0] or wheel_grounded[2])): wheelspin = 0.0
	# Only torque the ground cannot take digs: a locked diff / 4x4 spreads it
	# over more tyres and digs less than one spinning wheel of an open axle.
	var digging := false
	for i in 4:
		if not (wheel_grounded[i] and is_driven(i)): continue
		if handbrake_on and i % 2 == 1: continue
		var tyre_spin := clampf(drive_slip[i] * absf(last_throttle) * (1.0 - absf(speed) / 2.5), 0.0, 1.0)
		# (With a tyre set: pushing against the hole it stands in, it digs a way out.)
		if not tyre_type.is_empty():
			var pushing := clampf(absf(last_throttle) * (1.0 - absf(speed) / 2.5), 0.0, 1.0)
			if pushing > 0.3:
				soil.dig_out(wheel_contacts[i], -body.basis.z * signf(last_throttle), _climb_slope(i), DIG_RATE * tyre_type.bite * pushing * dt)
				digging = true
		if tyre_spin > 0.3:
			soil.tyre_sink = tyre_type.get("sink", 1.0)
			soil.stamp(wheel_contacts[i], -body.basis.z * signf(last_throttle), 2.4 * tyre_spin * dt, 1.0)
			digging = true
	# The body itself (underbody, sides, roof after a rollover) ploughs and
	# presses the ground wherever it rests on it.
	# Only while it moves (a resting body would keep sinking), and only in the
	# worlds whose field follows the car (the desert keeps its tuned ruts).
	# A beached car spinning its wheels also works the frame, axles and diff
	# into the ground beneath them (churned by the tyres, rocked by the drive).
	var sliding := Vector2(velocity.x, velocity.z).length() * dt
	# (Also when the tyres hang free in their holes: the body then settles until
	# they bite again.)
	var churn := clampf(absf(last_throttle) * (1.0 - absf(speed) / 2.5), 0.0, 1.0) * dt * 0.8
	soil.tyre_sink = 1.0
	if soil.follow and (sliding > 0.002 or churn > 0.002):
		var heading := Vector3(velocity.x, 0.0, velocity.z).normalized() if sliding > churn else -body.basis.z * signf(last_throttle)
		for point: Vector3 in hull_contacts:
			soil.stamp(point, heading, maxf(sliding, churn), 0.8)
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
	for emitter in find_children("*", "GPUParticles3D", false, false): emitter.emitting = false

## The wake cloud is (still) being emitted, by whichever surface copy is active.
func dust_emitting() -> bool:
	return (active_emitters.get(dust, dust) as GPUParticles3D).emitting

## Emitter standing in for `base` on `ground`. Stopping only clears `emitting`
## (particles already alive finish their flight); switching ground stops the old
## copy and starts, or first creates, the one tinted for the new surface.
func _surface_emitter(base: GPUParticles3D, ground: int, emit: bool) -> GPUParticles3D:
	var current: GPUParticles3D = active_emitters.get(base, base)
	var copies: Dictionary = surface_emitters.get_or_add(base, {})
	if not emit or copies.get(ground) == current:
		current.emitting = emit and copies.get(ground) == current
		return current
	current.emitting = false
	var emitter: GPUParticles3D = copies.get(ground)
	if emitter == null:
		# The authored node serves the first ground it meets; later grounds get copies.
		emitter = base if copies.is_empty() else _copy_emitter(base, ground)
		copies[ground] = emitter
		_tint_emitter(emitter, base, ground)
	active_emitters[base] = emitter
	emitter.emitting = true
	emitter_switched = true
	return emitter

func _copy_emitter(base: GPUParticles3D, ground: int) -> GPUParticles3D:
	var copy := base.duplicate() as GPUParticles3D
	copy.name = "%s_%s" % [base.name, ground]
	copy.emitting = false
	copy.process_material = base.process_material.duplicate()
	if base in sand_grains:
		var quad := base.draw_pass_1.duplicate() as QuadMesh
		quad.material = quad.material.duplicate()
		copy.draw_pass_1 = quad
	add_child(copy)
	copy.global_position = base.global_position
	return copy

## Fixed per copy: tint, and for thrown material its sprite and size.
func _tint_emitter(emitter: GPUParticles3D, base: GPUParticles3D, ground: int) -> void:
	var process := emitter.process_material as ParticleProcessMaterial
	process.color = Surface.TINT[ground]
	if base in sand_grains:
		((emitter.draw_pass_1 as QuadMesh).material as ShaderMaterial).set_shader_parameter("sprite_kind", Surface.SPRITE[ground])
		var size: float = Surface.SPRITE_SIZE[ground]
		var authored: Vector2 = base.get_meta("base_scale")
		process.scale_min = authored.x * size
		process.scale_max = authored.y * size

func _update_dust(forward: Vector3, right: Vector3) -> void:
	var movement := Vector2(velocity.x, velocity.z).length()
	var intensity := clampf(movement / 12.0 + slip_amount * 0.55 + wheelspin * 0.7, 0.05, 1.0)
	surface_kind = wheel_surface[1] if wheel_grounded[1] or not wheel_grounded[3] else wheel_surface[3]
	var cloud: float = Surface.dust(surface_kind)
	var spray: float = Surface.spray(surface_kind)
	var wake := (wheel_contacts[1] + wheel_contacts[3]) * 0.5 + Vector3.UP * 0.38
	var raising := (wheel_grounded[1] or wheel_grounded[3]) and movement > 1.4 and cloud > 0.01
	emitter_switched = false
	var wake_cloud := _surface_emitter(dust, Surface.look_kind(surface_kind), raising)
	var wake_specks := _surface_emitter(specks, Surface.look_kind(surface_kind), raising)
	wake_cloud.global_position = wake
	wake_specks.global_position = wake
	var jets: Array[GPUParticles3D] = []
	for i in 2:
		var wheel := 1 if i == 0 else 3
		var ground := wheel_surface[wheel]
		var jet := _surface_emitter(dust_jets[i], Surface.look_kind(ground), wheel_grounded[wheel] and (movement > 0.9 or _tyre_spinning(wheel) > 0.3) and Surface.dust(ground) > 0.01)
		jet.global_position = wheel_contacts[wheel] + Vector3.UP * 0.13
		jets.append(jet)
	var throwers: Array[GPUParticles3D] = []
	for i in sand_grains.size():
		var wheel: int = GRAIN_WHEELS[i]
		var ground := wheel_surface[wheel]
		var spinning := _tyre_spinning(wheel)
		var grains := _surface_emitter(sand_grains[i], Surface.look_kind(ground), is_driven(wheel) and wheel_grounded[wheel] and (movement > 0.9 or spinning > 0.3) and Surface.spray(ground) > 0.01)
		grains.global_position = wheel_contacts[wheel] + Vector3.UP * 0.13
		throwers.append(grains)
	# Spawn parameters only shape new particles; a copy that just took over needs
	# them at once, the others are refreshed at 20 Hz.
	var refresh := dust_clock >= 0.05 or emitter_switched
	var detail: float = game.dust_detail
	if refresh:
		dust_clock = 0.0
		wake_cloud.amount_ratio = intensity * cloud * game.dust_density
		wake_specks.amount_ratio = intensity * detail * cloud
		(wake_cloud.process_material as ParticleProcessMaterial).direction = (-forward * 0.5 + Vector3(0.35, 0.6, 0.12)).normalized()
	var heavy := 1.0 + (spray - 1.0) * 0.6 + water_submersion
	for i in 2:
		var side := -1.0 if i == 0 else 1.0
		var wheel := 1 if i == 0 else 3
		var jet := jets[i]
		if not refresh: continue
		jet.amount_ratio = clampf(intensity * Surface.dust(wheel_surface[wheel]) * game.dust_density, 0.0, 1.0)
		var process := jet.process_material as ParticleProcessMaterial
		process.direction = (-forward * 0.75 + right * side * (0.45 + slip_amount) + Vector3.UP * 0.65).normalized()
		process.initial_velocity_min = (0.9 + movement * 0.08) * heavy
		process.initial_velocity_max = (2.6 + movement * 0.12 + slip_amount * 3.5 + wheelspin * 3.0) * heavy
	for i in sand_grains.size():
		# Thrown material comes off the driven tyres, opposite to their rotation.
		var wheel: int = GRAIN_WHEELS[i]
		var grains := throwers[i]
		var amount: float = Surface.spray(wheel_surface[wheel])
		var spinning := _tyre_spinning(wheel)
		if not refresh or not grains.emitting: continue
		var side := -1.0 if wheel < 2 else 1.0
		var rotation_sign := signf(wheel_spin_rate[wheel]) if absf(wheel_spin_rate[wheel]) > 0.5 else 1.0
		var wheel_intensity := clampf(intensity + spinning * 0.6, 0.05, 1.0)
		grains.amount_ratio = clampf(wheel_intensity * amount * detail, 0.0, 1.0)
		var process := grains.process_material as ParticleProcessMaterial
		process.direction = (-forward * 0.75 * rotation_sign + right * side * (0.45 + slip_amount) + Vector3.UP * 0.65).normalized()
		if wheel_surface[wheel] == Surface.WATER and water_submersion > 0.05 and movement > 1.5:
			# Wading: the body ploughs sheets of water out to the sides and ahead.
			process.direction = (forward * 0.35 + right * side + Vector3.UP * 0.9).normalized()
			grains.amount_ratio = clampf(grains.amount_ratio * (1.0 + movement * 0.15), 0.0, 1.0)
		# Water and mud throw heavier, faster spray; wading lifts it further.
		process.initial_velocity_min = (0.9 + movement * 0.08) * heavy
		process.initial_velocity_max = (2.6 + movement * 0.12 + slip_amount * 3.5 + spinning * 3.5) * heavy

## Grime on the tyres and axles: mud, dust, snow and water the car picks up
## from the ground it drives on, worn and washed off again (tyre_grime.gdshader).
var grime := {"mud": 0.0, "dust": 0.0, "snow": 0.0, "wet": 0.0}
var grime_color := Color(0.24, 0.17, 0.11)
var grime_materials: Array[ShaderMaterial] = []
var grime_clock := 0.0
## 0..1 how caked the sidewalls and rims are (only from being stuck).
var tyre_side := 0.0
## Suspension materials and whether each belongs to the front axle.
var suspensions: Array = []
## Axle hull points: [axle node, point in its frame, index in hull_points].
var axle_contacts: Array = []
## 1 for the hull points that are axle contacts (see _resolve_terrain).
var axle_point := PackedByteArray()
## The body's dirt and snow (slower than the tyres'), and its layer material.
var body_grime := {"mud": 0.0, "dust": 0.0, "snow": 0.0, "slush": 0.0}
var body_layer: ShaderMaterial
## Glass: its dirt outside and inside the wiped arcs, the wipers and their state.
var glass_layer: ShaderMaterial
## Materials carrying the layers; the layers are attached only while there is
## something on the car (a clean car draws no extra pass).
var layer_hosts: Array[Material] = []
var glass_hosts: Array[Material] = []
var layers_on := true
var glass := {"mud": 0.0, "dust": 0.0, "snow": 0.0}
var glass_wiped := {"mud": 0.0, "dust": 0.0, "snow": 0.0}
var wipers: Array[Node3D] = []
var wiper_phase := 0.0
var wiper_running := false
var wiper_reached := 10.0
var glass_prev := {"mud": 0.0, "dust": 0.0, "snow": 0.0}
## Strokes left from the washer (J) and seconds a stroke takes.
var washer_strokes := 0
const WIPER_SWEEP := PI * 0.5
const WIPER_STROKE := 1.3
const MUD_COLORS := {Surface.MUD: Color(0.3, 0.21, 0.13), Surface.CLAY: Color(0.5, 0.34, 0.22),
	Surface.DIRT: Color(0.38, 0.27, 0.17), Surface.FOREST: Color(0.3, 0.22, 0.14), Surface.GRASS: Color(0.32, 0.25, 0.15)}

func _install_grime() -> void:
	var made := {}
	_install_body_grime()
	# Springs and shocks: their own material each (it bends them per axle).
	for node: MeshInstance3D in body.find_children("Suspension_*", "MeshInstance3D", true, false):
		var source := node.mesh.surface_get_material(0) as BaseMaterial3D
		if source == null: continue
		var material := _grime_material(source, false)
		material.set_shader_parameter("deform", true)
		material.set_shader_parameter("axle_x", node.get_meta("axle_x", 0.0))
		node.set_surface_override_material(0, material)
		grime_materials.append(material)
		suspensions.append([material, node.get_meta("axle_x", 0.0) > 0.0])
	for node: MeshInstance3D in $Body/Wheels.find_children("*", "MeshInstance3D", true, false):
		if node.mesh == null or node.name == "Lens": continue
		var on_tyre: bool = not node.get_parent().get_meta("axle", false)
		for s in node.mesh.get_surface_count():
			var source := node.mesh.surface_get_material(s) as BaseMaterial3D
			if source == null: continue
			var key := [source, on_tyre]
			if not made.has(key):
				var material := _grime_material(source, on_tyre)
				made[key] = material
				grime_materials.append(material)
			node.set_surface_override_material(s, made[key])

## The body's dirt and snow layer: a next pass over each opaque paint material
## (glass is left clean; a layer over a transparent surface sorts badly).
func _install_body_grime() -> void:
	var front := 0.0
	var rear := 0.0
	for wheel in $Body/Wheels.get_children():
		if wheel.get_meta("axle", false): continue
		# Wheel x along the rig (body -Z is the rig's +X, forward).
		if wheel.get_meta("front", false): front = -wheel.position.z
		else: rear = -wheel.position.z
	for node: MeshInstance3D in body.find_children("Body", "MeshInstance3D", true, false):
		if node.mesh == null: continue
		if node.material_override != null:
			node.material_override = _with_body_layer(node.material_override, front, rear)
			continue
		for s in node.mesh.get_surface_count():
			var source := node.mesh.surface_get_material(s)
			if source == null or (source is BaseMaterial3D and (source as BaseMaterial3D).transparency != BaseMaterial3D.TRANSPARENCY_DISABLED): continue
			if source is BaseMaterial3D and (source as BaseMaterial3D).resource_name.begins_with("Lights"): continue
			if source is BaseMaterial3D and (source as BaseMaterial3D).resource_name == "Windows":
				var pane := source.duplicate() as Material
				glass_hosts.append(pane)
				pane.next_pass = _glass_layer()
				node.set_surface_override_material(s, pane)
				continue
			node.set_surface_override_material(s, _with_body_layer(source, front, rear))

## The glass layer (dirt, snow, raindrops, wiped arcs), wired to the wipers.
func _glass_layer() -> ShaderMaterial:
	glass_layer = ShaderMaterial.new()
	glass_layer.shader = preload("res://desert_battlefield/glass_grime.gdshader")
	for node: Node3D in body.find_children("Wiper_*", "Node3D", true, false):
		wipers.append(node)
		node.set_meta("rest_basis", node.basis)
	if wipers.size() == 2:
		glass_layer.set_shader_parameter("has_wipers", true)
		glass_layer.set_shader_parameter("wiper_axis", wipers[0].get_meta("axis"))
		glass_layer.set_shader_parameter("wiper_sign", wipers[0].get_meta("sweep_sign"))
		glass_layer.set_shader_parameter("wiper_reach", wipers[0].get_meta("reach"))
		glass_layer.set_shader_parameter("wiper_sweep", WIPER_SWEEP)
		for w in 2:
			var key: String = ["a", "b"][w]
			glass_layer.set_shader_parameter("wiper_pivot_" + key, wipers[w].position)
			glass_layer.set_shader_parameter("wiper_rest_" + key, wipers[w].get_meta("rest"))
			glass_layer.set_shader_parameter("wiper_axis_" + key, wipers[w].get_meta("axis"))
	return glass_layer

func _with_body_layer(source: Material, front: float, rear: float) -> Material:
	var copy := source.duplicate() as Material
	layer_hosts.append(copy)
	if body_layer == null:
		body_layer = ShaderMaterial.new()
		body_layer.shader = preload("res://desert_battlefield/car_grime.gdshader")
		body_layer.set_shader_parameter("front_axle", front)
		body_layer.set_shader_parameter("rear_axle", rear)
	copy.next_pass = body_layer
	return copy

func _grime_material(source: BaseMaterial3D, on_tyre: bool) -> ShaderMaterial:
	var material := ShaderMaterial.new()
	material.shader = preload("res://desert_battlefield/tyre_grime.gdshader")
	material.set_shader_parameter("albedo_color", source.albedo_color)
	material.set_shader_parameter("has_albedo", source.albedo_texture != null)
	material.set_shader_parameter("albedo_texture", source.albedo_texture)
	material.set_shader_parameter("has_normal", source.normal_enabled and source.normal_texture != null)
	material.set_shader_parameter("normal_texture", source.normal_texture)
	material.set_shader_parameter("has_roughness", source.roughness_texture != null)
	material.set_shader_parameter("roughness_texture", source.roughness_texture)
	material.set_shader_parameter("metallic", source.metallic)
	material.set_shader_parameter("tread", 1.0 if on_tyre else 0.0)
	return material

## Solid axles touch the ground too: the bottom of the differential and of the
## tube near each hub become hull points that ride with the axle (so a car
## bottoming out drags its diff and ploughs a furrow).
func _add_axle_contacts() -> void:
	for axle in axles:
		# The housing mesh sits a level or two below the axle pivot.
		var found := axle.find_children("*", "MeshInstance3D", true, false)
		if found.is_empty(): continue
		var housing := found[0] as MeshInstance3D
		if housing.mesh == null: continue
		var to_axle := housing.transform
		var parent := housing.get_parent()
		while parent != axle and parent is Node3D:
			to_axle = (parent as Node3D).transform * to_axle
			parent = parent.get_parent()
		# The lowest point in each of five slices across the car (axle x): the
		# tubes near the hubs and the differential, wherever it sits.
		var lowest: Array[Vector3] = []
		var points: Array[Vector3] = []
		for s in housing.mesh.get_surface_count():
			for v: Vector3 in housing.mesh.surface_get_arrays(s)[Mesh.ARRAY_VERTEX]: points.append(to_axle * v)
		if points.is_empty(): continue
		var left := INF
		var right := -INF
		for p in points:
			left = minf(left, p.x)
			right = maxf(right, p.x)
		lowest.resize(5)
		lowest.fill(Vector3(0, INF, 0))
		for p in points:
			var slice := clampi(int((p.x - left) / maxf(right - left, 0.01) * 5.0), 0, 4)
			if p.y < lowest[slice].y: lowest[slice] = p
		for offset in lowest:
			if not is_finite(offset.y): continue
			axle_contacts.append([axle, offset, hull_points.size()])
			hull_points.append(axle.transform * offset - COM)
			axle_point.resize(hull_points.size())
			axle_point[hull_points.size() - 1] = 1
	hull_cache_points.resize(hull_points.size())
	hull_cache_gaps.resize(hull_points.size())
	hull_cache_gaps.fill(-1.0)

## Bends the springs and shocks with their axle's wheels (every frame).
func _update_suspension() -> void:
	for entry: Array in axle_contacts:
		hull_points[entry[2]] = (entry[0] as Node3D).transform * (entry[1] as Vector3) - COM
	for entry: Array in suspensions:
		var left := 0.0
		var right := 0.0
		for wheel in wheels:
			if wheel.get_meta("front", false) != entry[1]: continue
			if wheel.position.x > 0.0: left = wheel.position.y
			else: right = wheel.position.y
		(entry[0] as ShaderMaterial).set_shader_parameter("offset_left", left)
		(entry[0] as ShaderMaterial).set_shader_parameter("offset_right", right)

## Dirt, snow and rain on the glass, and the wipers: they run in rain and
## snowfall and for a few strokes after the washer (J), each stroke clearing
## its arc (dry mud only smears).
func _update_glass(dt: float, thrown: float) -> void:
	if glass_layer == null: return
	# Mud mostly hits the body; the glass gets a light spatter at most.
	var adds := {"mud": thrown * 0.004 if grime.mud > 0.3 and absf(speed) > 4.0 else 0.0,
		"dust": thrown * 0.006 if grime.dust > 0.3 else 0.0,
		"snow": Surface.snowfall * dt * 0.008}
	var most := {"mud": 0.45, "dust": 0.8, "snow": 1.0}
	for key in adds:
		glass[key] = minf(most[key], glass[key] + adds[key])
		glass_wiped[key] = minf(most[key], glass_wiped[key] + adds[key])
		glass_prev[key] = minf(most[key], glass_prev[key] + adds[key])
	var rinse := 0.1 if water_submersion > 1.0 else (0.006 if Surface.rain > 0.3 else 0.0)
	var melt := 0.004 if Surface.snowfall <= 0.02 and Surface.frost < 0.3 else 0.0
	for coat in [glass, glass_wiped]:
		coat.mud = maxf(0.0, coat.mud - dt * rinse)
		coat.dust = maxf(0.0, coat.dust - dt * rinse * 1.5)
		coat.snow = maxf(0.0, coat.snow - dt * (melt + (0.01 if absf(speed) > 11.0 else 0.0)))
	if wipers.size() != 2: return
	var wanted := Surface.rain > 0.1 or Surface.snowfall > 0.1 or washer_strokes > 0
	if wanted or wiper_phase > 0.0:
		if wiper_phase == 0.0:
			# A wipe starts: what the blades leave behind them is cleaner, shown
			# as they pass (wiper_reached).
			for key in glass_prev: glass_prev[key] = glass_wiped[key]
			var washing := washer_strokes > 0
			glass_wiped.mud *= 0.45 if washing else 0.85
			glass_wiped.dust *= 0.3 if washing else 0.6
			glass_wiped.snow *= 0.3
			wiper_reached = 0.0
		wiper_phase += dt / (2.0 * WIPER_STROKE)
		if wiper_phase >= 1.0:
			washer_strokes = maxi(0, washer_strokes - 1)
			wiper_phase = 0.0
	var angle := WIPER_SWEEP * (0.5 - 0.5 * cos(TAU * wiper_phase))
	for w in 2:
		var wiper := wipers[w]
		wiper.basis = Basis(wiper.get_meta("axis"), angle * float(wiper.get_meta("sweep_sign"))) * (wiper.get_meta("rest_basis") as Basis)
	wiper_reached = maxf(wiper_reached, angle)
	glass_layer.set_shader_parameter("wiper_reached", wiper_reached)
	glass_layer.set_shader_parameter("wiper_angle_a", angle)
	glass_layer.set_shader_parameter("wiper_angle_b", angle)

func _update_grime(dt: float) -> void:
	var moving := clampf(absf(speed) / 5.0, 0.0, 1.0)
	var spinning := clampf(wheelspin, 0.0, 1.0)
	var ground: int = wheel_surface[1]
	var shown := Surface.look_kind(ground)
	var soft_wet := Surface.wetness > 0.4 and ground in [Surface.DIRT, Surface.CLAY, Surface.FOREST, Surface.GRASS]
	var pick := (moving + spinning) * dt
	if water_submersion > 0.05:
		# Wading washes the tyres.
		grime.mud = maxf(0.0, grime.mud - dt * 0.25)
		grime.dust = maxf(0.0, grime.dust - dt * 0.6)
		grime.snow = maxf(0.0, grime.snow - dt * 0.6)
		grime.wet = 1.0
		tyre_side = maxf(0.0, tyre_side - dt * 0.3)
	else:
		if shown == Surface.SNOW:
			grime.snow = minf(1.0, grime.snow + pick * 0.25)
			# Snow scours the mud off the tyres and throws slush onto the body.
			grime.mud = maxf(0.0, grime.mud - pick * 0.04)
			body_grime.slush = minf(0.85, body_grime.slush + pick * 0.018)
		elif ground == Surface.MUD or soft_wet or shown == Surface.MUD:
			grime.mud = minf(1.0, grime.mud + pick * (0.3 if ground == Surface.MUD else 0.12))
			grime_color = grime_color.lerp(MUD_COLORS.get(ground, MUD_COLORS[Surface.DIRT]), pick * 0.3)
		elif Surface.dust(ground) > 0.3: grime.dust = minf(1.0, grime.dust + pick * 0.06)
		# Hard roads wear the coats off; snow melts above freezing.
		if ground in [Surface.ROAD, Surface.WET_ROAD, Surface.ROCK, Surface.GRAVEL]:
			grime.mud = maxf(0.0, grime.mud - moving * dt * 0.01)
			grime.dust = maxf(0.0, grime.dust - moving * dt * 0.01)
		if Surface.snow_cover < 0.1 and shown != Surface.SNOW: grime.snow = maxf(0.0, grime.snow - dt * 0.02)
		# Sidewalls and rims cake only while the car is stuck spinning in snow
		# or mud; driving on sheds it slowly.
		var stuck: bool = spinning > 0.4 and absf(speed) < 2.5 and (shown == Surface.SNOW or ground == Surface.MUD or soft_wet or shown == Surface.MUD)
		tyre_side = minf(1.0, tyre_side + spinning * dt * 0.3) if stuck else maxf(0.0, tyre_side - moving * dt * 0.02)
		var rain_wet := 0.7 if Surface.wetness > 0.5 else 0.0
		var target := maxf(rain_wet, 0.8 if (ground in [Surface.MUD, Surface.WATER] or soft_wet) else 0.0)
		grime.wet = move_toward(grime.wet, target, dt * (0.5 if target > grime.wet else 0.02))
	# The body follows the tyres more slowly (only what they throw high enough),
	# rinses where it sits in water, and gathers snow while parked or slow.
	# (Minutes of driving through mud to cake it all over, not one stuck moment.)
	var thrown := (moving + spinning * 0.5) * dt
	body_grime.mud = move_toward(body_grime.mud, grime.mud * 0.85, thrown * 0.006 if grime.mud * 0.85 > body_grime.mud else 0.0)
	body_grime.dust = move_toward(body_grime.dust, grime.dust * 0.9, thrown * 0.006 if grime.dust * 0.9 > body_grime.dust else 0.0)
	if water_submersion > 0.3 or Surface.wetness > 0.8:
		body_grime.mud = maxf(0.0, body_grime.mud - dt * (0.05 if water_submersion > 0.3 else 0.004))
		body_grime.dust = maxf(0.0, body_grime.dust - dt * (0.1 if water_submersion > 0.3 else 0.01))
	if Surface.snowfall > 0.02 and absf(speed) < 11.0: body_grime.snow = minf(1.0, body_grime.snow + Surface.snowfall * dt * 0.003)
	if absf(speed) > 11.0: body_grime.snow = maxf(0.0, body_grime.snow - dt * 0.01)
	if Surface.snowfall <= 0.02 and Surface.frost < 0.3: body_grime.snow = maxf(0.0, body_grime.snow - dt * 0.004)
	if (Surface.frost < 0.3 and shown != Surface.SNOW) or water_submersion > 0.3: body_grime.slush = maxf(0.0, body_grime.slush - dt * 0.006)
	_update_glass(dt, thrown)
	grime_clock += dt
	if grime_clock < 0.2: return
	grime_clock = 0.0
	for material in grime_materials:
		material.set_shader_parameter("mud", grime.mud)
		material.set_shader_parameter("dust", grime.dust)
		material.set_shader_parameter("snow", grime.snow)
		material.set_shader_parameter("wet", grime.wet)
		material.set_shader_parameter("side", tyre_side)
		material.set_shader_parameter("mud_color", grime_color)
	if glass_layer != null:
		for key in ["mud", "dust", "snow"]:
			glass_layer.set_shader_parameter(key, glass[key])
			glass_layer.set_shader_parameter("wiped_" + key, glass_wiped[key])
			glass_layer.set_shader_parameter("prev_" + key, glass_prev[key])
		glass_layer.set_shader_parameter("rain", Surface.rain)
		glass_layer.set_shader_parameter("mud_color", grime_color)
	var needed := Surface.rain > 0.05
	for value in body_grime.values() + glass.values() + glass_wiped.values(): needed = needed or value > 0.01
	if needed != layers_on:
		layers_on = needed
		for host in layer_hosts: host.next_pass = body_layer if needed else null
		for host in glass_hosts: host.next_pass = glass_layer if needed else null
	if body_layer != null:
		body_layer.set_shader_parameter("mud", body_grime.mud)
		body_layer.set_shader_parameter("dust", body_grime.dust)
		body_layer.set_shader_parameter("snow", body_grime.snow)
		body_layer.set_shader_parameter("slush", body_grime.slush)
		body_layer.set_shader_parameter("mud_color", grime_color)

