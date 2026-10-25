class_name DriveEffects
extends Node3D
## Contact-driven dust, ballistic grit and a bounded terrain-conforming tyre trail.
## Physical height/collision changes remain owned by DriveWorld.
const TRACK_CAPACITY := 2048
const TRACK_SPACING := 0.38
var vehicle: DriveVehicle
var world: DriveWorld
var particles_enabled := true
var trails_enabled := false
var _sand_clumps: Array[CPUParticles3D] = []
var _clump_emitters: Array[CPUParticles3D] = []
var _emitters: Array[CPUParticles3D] = []
var _grit: Array[CPUParticles3D] = []
var _tracks: MultiMeshInstance3D
var _cursor := 0
var _count := 0
var _last_contacts: Array[Vector3] = []
var _splash_material: StandardMaterial3D
var _exhaust: CPUParticles3D
var _track_records: Array[Dictionary] = []
var _refresh_cursor := 0
var _surface_pools: Array[Dictionary] = []
const PARTICLE_COLORS := [Color("b9aa89"), Color("80715c"), Color("96938b"), Color("dce0de"), Color("9daead"), Color("9aa5ad")]

func _ready() -> void:
	physics_interpolation_mode = Node.PHYSICS_INTERPOLATION_MODE_OFF
	var dust_scene := load("res://assets/effects/wheel_dust.tscn") as PackedScene
	var grit_scene := load("res://assets/effects/wheel_grit.tscn") as PackedScene
	for wheel in 4:
		var dust := dust_scene.instantiate() as CPUParticles3D
		dust.emitting = false
		add_child(dust)
		_emitters.append(dust)
		var grit := grit_scene.instantiate() as CPUParticles3D
		grit.emitting = false
		if _splash_material == null:
			_splash_material = grit.mesh.material.duplicate() as StandardMaterial3D
			_splash_material.roughness = 0.18
			_splash_material.metallic_specular = 0.7
			_splash_material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
			_splash_material.albedo_color = Color(0.78, 0.91, 1, 0.7)
		add_child(grit)
		_grit.append(grit)
		_last_contacts.append(Vector3.INF)
	# A material change must not recolor or turn existing airborne grit into
	# water droplets. Each wheel has a bounded set of immutable surface banks.
	var powder_shader := load("res://shaders/sand_powder.gdshader")
	var grain_mesh := SphereMesh.new()
	grain_mesh.radius = 0.018
	grain_mesh.height = 0.036
	grain_mesh.radial_segments = 4
	grain_mesh.rings = 1
	grain_mesh.material = _grit[0].mesh.material
	for wheel in 4:
		var pool := {}
		for family in PARTICLE_COLORS.size():
			var dust := _emitters[wheel] if family == 0 else dust_scene.instantiate() as CPUParticles3D
			var grit := _grit[wheel] if family == 0 else grit_scene.instantiate() as CPUParticles3D
			if family != 0:
				dust.emitting = false
				grit.emitting = false
				add_child(dust)
				add_child(grit)
			dust.color = Color(PARTICLE_COLORS[family].lightened(0.08), [0.48, 0.20, 0.40, 0.55, 0.0, 0.32][family])
			dust.material_override = null
			grit.color = Color(PARTICLE_COLORS[family].darkened(0.05), 1)
			grit.material_override = _splash_material if family == 4 else null
			var clumps: CPUParticles3D
			if family in [0, 1, 2, 3]:
				var veil := ShaderMaterial.new()
				veil.shader = powder_shader
				veil.set_shader_parameter("powder_tint", PARTICLE_COLORS[family].lightened(0.06))
				dust.material_override = veil
				dust.color = Color(1, 1, 1, [0.38, 0.24, 0.20, 0.36][family])
				dust.amount = [28, 24, 16, 28][family]
				dust.lifetime = [1.6, 1.1, 0.85, 1.35][family]
				grit.mesh = grain_mesh
				grit.amount = [192, 128, 96, 160][family]
				grit.lifetime = [0.65, 0.65, 0.55, 0.95][family]
				grit.emission_shape = CPUParticles3D.EMISSION_SHAPE_SPHERE
				grit.emission_sphere_radius = 0.10
				clumps = grit_scene.instantiate() as CPUParticles3D
				clumps.emitting = false
				clumps.amount = [24, 28, 16, 20][family]
				clumps.lifetime = [0.55, 0.6, 0.45, 0.8][family]
				clumps.color = PARTICLE_COLORS[family].darkened(0.06)
				clumps.scale_amount_min = 0.4
				clumps.scale_amount_max = 0.85
				add_child(clumps)
				_clump_emitters.append(clumps)
				if family == 0: _sand_clumps.append(clumps)
			pool[family] = {"dust": dust, "grit": grit, "clumps": clumps}
		_surface_pools.append(pool)

	_tracks = MultiMeshInstance3D.new()
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.use_colors = true
	mm.use_custom_data = true
	mm.mesh = _track_mesh()
	mm.instance_count = TRACK_CAPACITY if trails_enabled else 0
	mm.visible_instance_count = 0
	_tracks.multimesh = mm
	_tracks.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(_tracks)
	_tracks.visible = trails_enabled
	_exhaust = (load("res://assets/effects/exhaust.tscn") as PackedScene).instantiate() as CPUParticles3D
	add_child(_exhaust)

func _track_mesh() -> ArrayMesh:
	# Dark compressed tread, flanked by small light-catching displaced shoulders.
	# These details accompany the shared physical rut; they do not support the car.
	var mesh := SurfaceTool.new()
	mesh.begin(Mesh.PRIMITIVE_TRIANGLES)
	var widths := [-0.42, -0.32, -0.23, -0.15, 0.15, 0.23, 0.32, 0.42]
	var heights := [0.0, 0.018, 0.075, 0.008, 0.008, 0.075, 0.018, 0.0]
	for strip in 7:
		for corner: Vector2i in [Vector2i(0, 0), Vector2i(1, 0), Vector2i(0, 1), Vector2i(1, 0), Vector2i(1, 1), Vector2i(0, 1)]:
			var edge := strip + corner.y
			mesh.set_color(Color(0.89, 0.89, 0.89) if strip == 3 else Color(1.04, 1.04, 1.04))
			mesh.set_uv(Vector2(corner.x, (float(widths[edge]) + 0.42) / 0.84))
			mesh.add_vertex(Vector3((corner.x - 0.5) * TRACK_SPACING * 1.08, heights[edge], widths[edge]))
	mesh.generate_normals()
	var result := mesh.commit()
	var material := ShaderMaterial.new()
	material.shader = load("res://shaders/tyre_tracks.gdshader")
	result.surface_set_material(0, material)
	return result

func reset_trails() -> void:
	_cursor = 0
	_count = 0
	_track_records.clear()
	_refresh_cursor = 0
	_tracks.multimesh.visible_instance_count = 0
	_last_contacts.fill(Vector3.INF)
	reset_particles()

func reset_particles() -> void:
	var all_emitters: Array[CPUParticles3D] = [_exhaust]
	all_emitters.append_array(_clump_emitters)
	for pool: Dictionary in _surface_pools:
		for bank: Dictionary in pool.values(): all_emitters.append_array([bank.dust, bank.grit])
	for emitter: CPUParticles3D in all_emitters:
		emitter.emitting = false
		emitter.restart()
		emitter.emitting = false

func particle_family(surface: Dictionary, skid := false) -> int:
	if float(surface.get("water", 0)) > 0.02: return 4
	if float(surface.get("snow", 0)) > 0.5: return 3
	if skid: return 5
	var kind := int(surface.get("material", 0))
	if kind == DriveTerrain.SurfaceKind.SAND: return 0
	if kind in [DriveTerrain.SurfaceKind.MUD, DriveTerrain.SurfaceKind.PEAT, DriveTerrain.SurfaceKind.DIRT, DriveTerrain.SurfaceKind.GRASS, DriveTerrain.SurfaceKind.LEAVES, DriveTerrain.SurfaceKind.CLAY]: return 1
	return 2

func select_particle_bank(wheel: int, family: int) -> void:
	var bank: Dictionary = _surface_pools[wheel][family]
	if _emitters[wheel] == bank.dust: return
	_emitters[wheel].emitting = false
	_grit[wheel].emitting = false
	_emitters[wheel] = bank.dust
	_grit[wheel] = bank.grit

func _process(_dt: float) -> void:
	if vehicle == null or world == null: return
	var forward := Vector3(cos(vehicle.heading), 0, sin(vehicle.heading))
	if trails_enabled: _refresh_tracks()
	var motion := -1.0 if vehicle.wheel_speed < -0.2 else 1.0
	var agitation := clampf(absf(vehicle.speed) / 14.0 + vehicle.soil_slip * 0.85 + absf(vehicle._lateral_speed) * 0.14, 0.0, 1.0)
	var right := Vector3(-forward.z, 0, forward.x)
	_exhaust.position = vehicle.position - forward * 2.25 + right * 0.65 + Vector3.UP * 0.28
	_exhaust.direction = (-forward + Vector3.UP * 0.2).normalized()
	_exhaust.emitting = particles_enabled and vehicle.run != null and vehicle.run.engine_running
	if vehicle.run != null:
		var damaged := 1.0 - float(vehicle.run.parts.engine)
		_exhaust.color = Color(Color("bcc6cb").lerp(Color("5d6570"), damaged), 0.12 + damaged * 0.26 + vehicle.throttle_input * 0.08)
	for clump: CPUParticles3D in _clump_emitters: clump.emitting = false
	for wheel in 4:
		var dust := _emitters[wheel]
		var grit := _grit[wheel]
		if vehicle.wheel_contacts.size() != 4:
			dust.emitting = false
			grit.emitting = false
			continue
		var p := vehicle.wheel_contacts[wheel]
		var surface := world.terrain.sample(p.x, p.z)
		var wet := float(surface.water) > 0.02
		var kind := int(surface.get("material", 0))
		var damp := kind in [DriveTerrain.SurfaceKind.MUD, DriveTerrain.SurfaceKind.PEAT] and float(surface.get("soil_moisture", 0)) > 0.45
		var snow := float(surface.snow) > 0.5
		var loose := float(surface.paved) < 0.5
		var skid := not loose and absf(vehicle.speed) > 10 and (vehicle.brake_input > 0.85 or absf(vehicle._lateral_speed) > 1.5)
		var family := particle_family(surface, skid)
		select_particle_bank(wheel, family)
		dust = _emitters[wheel]
		grit = _grit[wheel]
		var supported := vehicle.is_on_floor() and vehicle.global_position.y - DriveVehicle.WHEEL_RADIUS - p.y < 0.35
		var active := particles_enabled and supported and agitation > 0.07 and (loose or wet or skid)
		dust.position = p + Vector3.UP * 0.70
		grit.position = p + Vector3.UP * 0.08
		# Water throws short ballistic droplets, rather than a grey dust cloud.
		dust.emitting = active and not wet
		grit.emitting = active and agitation > 0.2
		grit.scale_amount_min = 1.5 if wet else 0.65
		grit.scale_amount_max = 4.2 if wet else 2.0
		# Rearward, low drifting billboards swell after leaving the contact.
		# Mud stays ballistic; snow creates a smaller powder cloud.
		dust.scale_amount_min = 0.35 + agitation * 0.3
		dust.scale_amount_max = (4.0 if snow else 8.0) * (0.45 + agitation * 0.65)
		dust.damping_min = 0.4
		dust.damping_max = 0.9
		# The material already converts sRGB particle vertex colours. Converting
		# twice made dirt and water fragments almost invisible in shadow.
		dust.direction = (-forward * motion - right * vehicle._lateral_speed * 0.12 + Vector3.UP * 0.10).normalized()
		grit.direction = (-forward * motion * 0.65 + Vector3.UP * 0.85).normalized()
		dust.initial_velocity_min = 0.6 + agitation * 1.5
		dust.initial_velocity_max = 1.4 + agitation * 4.0
		grit.initial_velocity_min = 1.0 + agitation
		grit.initial_velocity_max = 2.0 + agitation * 5.0
		if wet: grit.initial_velocity_max += 3.0
		dust.gravity = Vector3(world.wind_force * 0.18, -1.0 if damp else 0.06, 0.12)
		if family in [0, 1, 2, 3]:
			var outward := right * (1.0 if (p - vehicle.position).dot(right) > 0 else -1.0)
			var spray_direction := (-forward * motion + outward * (0.25 + absf(vehicle.steering_input) * 0.4) + Vector3.UP * 0.65).normalized()
			grit.direction = spray_direction
			grit.initial_velocity_min = 1.8 + agitation * 1.5
			grit.initial_velocity_max = 3.2 + agitation * 3.5
			grit.scale_amount_min = 0.65
			grit.scale_amount_max = 1.6
			var clumps: CPUParticles3D = _surface_pools[wheel][family].clumps
			clumps.position = p + Vector3.UP * 0.10
			clumps.direction = spray_direction
			clumps.initial_velocity_min = 1.5 + agitation
			clumps.initial_velocity_max = 2.8 + agitation * 2.0
			clumps.emitting = active and agitation > 0.2
			dust.position = p + Vector3.UP * 0.22
			dust.scale_amount_min = 0.25
			dust.scale_amount_max = 1.3 + agitation * 1.4
			dust.initial_velocity_min = 0.2
			dust.initial_velocity_max = 0.8 + agitation
			dust.gravity = Vector3(world.wind_force * 0.12, 0.12, 0)
			if family == 1:
				grit.initial_velocity_max *= 0.85
				grit.scale_amount_max = 2.3 if damp else 1.5
				clumps.scale_amount_max = 1.4 if damp else 0.85
				dust.scale_amount_max *= 0.55 if damp else 0.8
				dust.emitting = active and not damp
			elif family == 2:
				grit.scale_amount_max = 2.1
				grit.initial_velocity_max *= 0.75
				clumps.initial_velocity_max *= 0.7
				dust.scale_amount_max *= 0.65
			elif family == 3:
				grit.gravity = Vector3(0, -4.5, 0)
				grit.scale_amount_min = 0.5
				clumps.gravity = Vector3(0, -5, 0)
				clumps.scale_amount_max = 1.0
				dust.gravity.y = 0.18
		if not trails_enabled: continue
		if not supported or (not loose and not skid) or wet:
			_last_contacts[wheel] = Vector3.INF
			continue
		var previous := _last_contacts[wheel]
		if not previous.is_finite() or previous.distance_to(p) > 8.0:
			_last_contacts[wheel] = p
			continue
		var distance := Vector2(p.x - previous.x, p.z - previous.z).length()
		if distance < TRACK_SPACING: continue
		var direction := Vector3(p.x - previous.x, 0, p.z - previous.z).normalized()
		var segments := mini(20, floori(distance / TRACK_SPACING))
		for segment in segments:
			var center := previous + direction * TRACK_SPACING * (segment + 0.5)
			# A wheel crossing a biome edge must not paint the whole segment
			# using the surface under its final contact.
			_stamp(center, direction, world.terrain.sample(center.x, center.z))
		_last_contacts[wheel] = previous + direction * TRACK_SPACING * segments

func track_style(surface: Dictionary) -> Dictionary:
	var kind := int(surface.get("material", 0))
	if float(surface.get("paved", 0)) > 0.5: return {"kind": 5, "color": Color("353d42")}
	if float(surface.get("snow", 0)) > 0.5: return {"kind": 2, "color": (surface.get("color", Color("e4e9e8")) as Color).darkened(0.035)}
	if kind == DriveTerrain.SurfaceKind.SAND: return {"kind": 0, "color": (surface.get("color", Color("d7bd88")) as Color).darkened(0.025)}
	if kind in [DriveTerrain.SurfaceKind.GRASS, DriveTerrain.SurfaceKind.LEAVES, DriveTerrain.SurfaceKind.DIRT, DriveTerrain.SurfaceKind.CLAY]: return {"kind": 1, "color": Color("6c5942")}
	if kind in [DriveTerrain.SurfaceKind.MUD, DriveTerrain.SurfaceKind.PEAT]: return {"kind": 4, "color": Color("514b39")}
	return {"kind": 3, "color": (surface.color as Color).darkened(0.12)}

func _stamp(p: Vector3, direction: Vector3, surface: Dictionary) -> void:
	if not trails_enabled: return
	var side := Vector3(-direction.z, 0, direction.x)
	var dx := world.height_at(p.x + direction.x * 0.3, p.z + direction.z * 0.3) - world.height_at(p.x - direction.x * 0.3, p.z - direction.z * 0.3)
	var dz := world.height_at(p.x + side.x * 0.3, p.z + side.z * 0.3) - world.height_at(p.x - side.x * 0.3, p.z - side.z * 0.3)
	var tangent := Vector3(direction.x, dx / 0.6, direction.z).normalized()
	var across := Vector3(side.x, dz / 0.6, side.z).normalized()
	var normal := across.cross(tangent).normalized()
	across = tangent.cross(normal).normalized()
	p.y = world.height_at(p.x, p.z) + 0.022
	var relief := 0.01 if float(surface.get("paved", 0)) > 0.5 else (1.4 if float(surface.get("snow", 0)) > 0.5 else 1.0)
	var transform := Transform3D(Basis(tangent, normal * relief, across), p)
	_tracks.multimesh.set_instance_transform(_cursor, transform)
	var record := {"transform": transform, "rest_y": p.y + world.terrain.deformation.depth_at(p.x, p.z)}
	if _cursor >= _track_records.size(): _track_records.append(record)
	else: _track_records[_cursor] = record
	# MultiMesh instance colours are linear, independently of mesh vertex colours.
	var style := track_style(surface)
	_tracks.multimesh.set_instance_color(_cursor, (style.color as Color).srgb_to_linear())
	var linear_color := (style.color as Color).srgb_to_linear()
	_tracks.multimesh.set_instance_custom_data(_cursor, Color(linear_color.r, linear_color.g, linear_color.b, float(style.kind) / 8.0))
	_cursor = (_cursor + 1) % TRACK_CAPACITY
	_count = mini(_count + 1, TRACK_CAPACITY)
	_tracks.multimesh.visible_instance_count = _count

func _refresh_tracks() -> void:
	# Re-seat existing relief after asynchronous soil/collision updates. Bounded
	# work avoids rebuilding a long trail when a wheel digs or revisits a rut.
	for step in mini(32, _track_records.size()):
		_refresh_cursor %= _track_records.size()
		var record := _track_records[_refresh_cursor]
		var transform: Transform3D = record.transform
		var p := transform.origin
		var height := float(record.rest_y) - world.terrain.deformation.depth_at(p.x, p.z)
		if absf(height - p.y) > 0.001:
			transform.origin.y = height
			record.transform = transform
			_tracks.multimesh.set_instance_transform(_refresh_cursor, transform)
		_refresh_cursor += 1
