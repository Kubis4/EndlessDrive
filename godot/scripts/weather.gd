class_name DriveWeather
extends Node3D
## Finite local weather fronts: driving through or waiting them out are both valid.
var run: DriveRun
var world: DriveWorld
var vehicle: DriveVehicle
var mode := "Jasno"
var intensity := 0.0
var wetness := 0.0
var sheltered := false
var storm_active := false
var storm_edge := -100000.0
var _storm_left := 0.0
var _next_storm := 120.0
var storm_warning := 0.0
var shelter_target := Vector3.ZERO
var has_shelter_target := false
var _shelter_check := 0.0
var forced_mode := "" # Local preview/test override; never persisted.
var _particles: CPUParticles3D
var _precip_mesh: BoxMesh
var _precip_material: StandardMaterial3D
var _wall: MeshInstance3D
var _snow_particles: CPUParticles3D
var rain_mix := 0.0
var snow_mix := 0.0
var _wind_mix := 0.0
var _front_until := 0.0
var _front_serial := 0
var _front_index := 0
var _front_strength := 0.65
var _front_biome := -1
var storm_center := Vector2.ZERO
var storm_velocity := Vector2.ZERO
var storm_visibility := 0.0
var storm_variant := 0
var _storm_serial := 0
var _storm_duration := 90.0
var _dust: CPUParticles3D
var _cloud_material: ShaderMaterial


func _ready() -> void:
	physics_interpolation_mode = Node.PHYSICS_INTERPOLATION_MODE_OFF
	_particles = CPUParticles3D.new()
	_particles.amount = 320
	_particles.lifetime = 1.7
	_particles.local_coords = false
	_particles.emission_shape = CPUParticles3D.EMISSION_SHAPE_BOX
	_particles.emission_box_extents = Vector3(30, 1, 30)
	_particles.direction = Vector3.DOWN
	_particles.spread = 10
	_particles.initial_velocity_min = 12
	_particles.initial_velocity_max = 18
	_particles.gravity = Vector3(1, -2, 0)
	var mesh := BoxMesh.new()
	_precip_mesh = mesh
	mesh.size = Vector3(0.035, 0.7, 0.035)
	var material := StandardMaterial3D.new()
	_precip_material = material
	material.albedo_color = Color("d1edf4")
	material.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	material.vertex_color_use_as_albedo = true
	mesh.material = material
	_particles.mesh = mesh
	_particles.emitting = false
	add_child(_particles)
	_snow_particles = CPUParticles3D.new()
	_snow_particles.amount = 160
	_snow_particles.lifetime = 4.0
	_snow_particles.local_coords = false
	_snow_particles.emission_shape = CPUParticles3D.EMISSION_SHAPE_BOX
	_snow_particles.emission_box_extents = Vector3(30, 1, 30)
	_snow_particles.direction = Vector3.DOWN
	_snow_particles.spread = 24
	_snow_particles.initial_velocity_min = 2
	_snow_particles.initial_velocity_max = 4
	var snow_mesh := BoxMesh.new()
	snow_mesh.size = Vector3(0.09, 0.09, 0.09)
	snow_mesh.material = material.duplicate()
	_snow_particles.mesh = snow_mesh
	_snow_particles.emitting = false
	add_child(_snow_particles)
	_cloud_material = ShaderMaterial.new()
	_cloud_material.shader = load("res://shaders/sand_powder.gdshader")
	_cloud_material.set_shader_parameter("powder_tint", Vector4(0.63, 0.52, 0.36, 1))
	_cloud_material.set_shader_parameter("opacity", 0.16)
	_wall = MeshInstance3D.new()
	var cloud := QuadMesh.new()
	cloud.size = Vector2(220, 70)
	cloud.material = _cloud_material
	_wall.mesh = cloud
	_wall.visible = false
	_wall.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(_wall)
	_dust = CPUParticles3D.new()
	_dust.name = "LocalDustFront"
	_dust.amount = 128
	_dust.lifetime = 8.0
	_dust.local_coords = false
	_dust.emission_shape = CPUParticles3D.EMISSION_SHAPE_BOX
	_dust.emission_box_extents = Vector3(92, 12, 64)
	_dust.direction = Vector3(1, 0.1, 0)
	_dust.spread = 18
	_dust.initial_velocity_min = 1.0
	_dust.initial_velocity_max = 2.5
	_dust.gravity = Vector3.ZERO
	_dust.scale_amount_min = 3.0
	_dust.scale_amount_max = 7.0
	var puff := QuadMesh.new()
	puff.size = Vector2(3, 3)
	var powder := ShaderMaterial.new()
	powder.shader = _cloud_material.shader
	powder.set_shader_parameter("powder_tint", Vector4(0.63, 0.52, 0.36, 1))
	puff.material = powder
	_dust.mesh = puff
	_dust.emitting = false
	_dust.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(_dust)
	if not run.weather_state.is_empty():
		_next_storm = float(run.weather_state.get("next", run.elapsed + 120))
		storm_edge = float(run.weather_state.get("edge", vehicle.position.x - 120))
		_storm_left = float(run.weather_state.get("left", 0))
		storm_active = _storm_left > 0
		storm_warning = maxf(0.0, float(run.weather_state.get("warning", 0)))
		has_shelter_target = float(run.weather_state.get("has_shelter", 0)) > 0.5
		shelter_target = Vector3(float(run.weather_state.get("shelter_x", 0)), float(run.weather_state.get("shelter_y", 0)), float(run.weather_state.get("shelter_z", 0)))
		_front_until = float(run.weather_state.get("front_until", 0))
		_front_serial = int(run.weather_state.get("front_serial", 0))
		_front_index = clampi(int(run.weather_state.get("front_index", 0)), 0, 4)
		_front_strength = clampf(float(run.weather_state.get("front_strength", 0.65)), 0.35, 1)
		_front_biome = int(run.weather_state.get("front_biome", -1))
		wetness = clampf(float(run.weather_state.get("wetness", 0)), 0, 1)
		intensity = clampf(float(run.weather_state.get("intensity", 0)), 0, 1)
		rain_mix = clampf(float(run.weather_state.get("rain_mix", 0)), 0, 1)
		snow_mix = clampf(float(run.weather_state.get("snow_mix", 0)), 0, 1)
		_wind_mix = clampf(float(run.weather_state.get("wind_mix", 0)), 0, 3)
		_storm_serial = int(run.weather_state.get("storm_serial", 0))
		storm_variant = clampi(int(run.weather_state.get("storm_variant", 0)), 0, 2)
		storm_center = Vector2(float(run.weather_state.get("storm_x", storm_edge + 60)), float(run.weather_state.get("storm_z", vehicle.position.z)))
		storm_velocity = Vector2(float(run.weather_state.get("storm_vx", 2.0)), float(run.weather_state.get("storm_vz", 0)))
		_storm_duration = clampf(float(run.weather_state.get("storm_duration", 90)), 50, 110)
		_storm_left = minf(_storm_left, _storm_duration)
		storm_warning = minf(storm_warning, 20.0)
		storm_visibility = clampf(float(run.weather_state.get("storm_visibility", 0)), 0, 1)
		if storm_active:
			_refresh_shelter(not run.weather_state.has("storm_x"))



func reset_particles() -> void:
	var rain_active := _particles.emitting
	var snow_active := _snow_particles.emitting
	var dust_active := _dust.emitting
	_particles.restart()
	_snow_particles.restart()
	_dust.restart()
	_particles.emitting = rain_active
	_snow_particles.emitting = snow_active
	_dust.emitting = dust_active


func reset_weather() -> void:
	storm_active = false
	storm_visibility = 0
	_storm_serial = 0
	_storm_left = 0
	_next_storm = run.elapsed + 120
	storm_warning = 0
	has_shelter_target = false
	_front_until = 0
	_front_serial = 0
	_front_biome = -1
	wetness = 0
	intensity = 0
	rain_mix = 0
	snow_mix = 0
	_wind_mix = 0
	_wall.visible = false
	_particles.emitting = false
	_snow_particles.emitting = false
	_dust.emitting = false
	reset_particles()


func start_sandstorm(variant: int = -1) -> void:
	if not _refresh_shelter(true): return
	var rng := RandomNumberGenerator.new()
	rng.seed = run.world_seed * 31337 + _storm_serial * 104729
	_storm_serial += 1
	storm_variant = rng.randi_range(0, 2) if variant < 0 else clampi(variant, 0, 2)
	_storm_duration = rng.randf_range(65, 100)
	_storm_left = _storm_duration
	storm_active = true
	var origin := Vector2(vehicle.position.x, vehicle.position.z)
	var forward := Vector2(cos(vehicle.heading), sin(vehicle.heading))
	var right := Vector2(-forward.y, forward.x)
	match storm_variant:
		0:
			storm_center = origin + forward * 140
			storm_velocity = -forward * 1.2
		1:
			storm_center = origin + forward * 40 + right * 95
			storm_velocity = forward * 0.5 - right * 2.4
		2:
			storm_center = origin - forward * 135
			storm_velocity = forward * 2.2


func _refresh_shelter(renew_warning: bool = false) -> bool:
	var serviceable := run.fuel > 0.1 and float(run.parts.engine) > 0.05 and (run.engine_running or run.battery >= 0.08)
	var refuge := {}
	# Keep a still-valid target. Choosing whichever roof is nearest every second
	# repeatedly restarted the warning when the player passed between shelters.
	if serviceable and has_shelter_target and not renew_warning:
		refuge = world.find_shelter(shelter_target, 2.0, false)
		if vehicle.position.distance_to(shelter_target) > 360.0: refuge = {}
	if serviceable and refuge.is_empty(): refuge = world.find_shelter(vehicle.position, 360.0, false)
	if not refuge.is_empty():
		# Sample the approach for impassable fords and steep access. A loaded
		# roof alone is insufficient if this car cannot reach it.
		var last := vehicle.position
		var travel := 0.0
		for step in range(1, 17):
			var p := vehicle.position.lerp(refuge.position, float(step) / 16.0)
			p.y = world.height_at(p.x, p.z)
			var horizontal := Vector2(p.x - last.x, p.z - last.z).length()
			if world.terrain.water_depth(p.x, p.z) > (1.7 if run.snorkel_installed else 0.65) or absf(p.y - last.y) > horizontal * 0.65 + 0.8:
				refuge = {}
				break
			travel += p.distance_to(last)
			last = p
		if not refuge.is_empty():
			var changed := not has_shelter_target or shelter_target.distance_to(refuge.position) > 2.0
			shelter_target = refuge.position
			has_shelter_target = true
			if renew_warning or changed:
				var crawl := maxf(clampf(run.power_factor() * 8.0, 2.0, 7.0), absf(vehicle.speed) * 0.75)
				if travel / crawl * 1.2 + 3.0 > 20.0:
					_defer_storm()
					return false
				if renew_warning: storm_warning = 20.0
				storm_center = Vector2(vehicle.position.x - 150.0, vehicle.position.z)
				storm_edge = storm_center.x - 110.0
			_shelter_check = 1.0
			return true
	_defer_storm()
	return false


func _defer_storm() -> void:
	# Defer a chase, rather than damage a player with no reachable escape.
	storm_active = false
	has_shelter_target = false
	storm_warning = 0.0
	_next_storm = run.elapsed + 30.0


func _advance_front(biome: int) -> void:
	var rng := RandomNumberGenerator.new()
	rng.seed = run.world_seed * 7919 + _front_serial * 104729 + floori(vehicle.position.x / 960) * 31
	_front_index = posmod(_front_index + rng.randi_range(1, 4), 5)
	_front_strength = rng.randf_range(0.35, 1.0)
	_front_until = run.elapsed + rng.randf_range(130, 290)
	_front_biome = biome
	_front_serial += 1


func _process(dt: float) -> void:
	var biome := world.biome_at(vehicle.position.x)
	if run.elapsed >= _front_until or _front_biome < 0: _advance_front(biome)
	mode = ["Jasno", "Oblačno", "Dážď", "Zamračené", "Vietor"][_front_index]
	if biome == 4 and _front_index in [2, 4]: mode = "Sneženie"
	if biome in [0, 1] and _front_index == 2: mode = "Prachový vietor"
	if not forced_mode.is_empty(): mode = forced_mode
	sheltered = world.is_sheltered(vehicle.position)
	if run.elapsed >= _next_storm and not storm_active and biome in [0, 1]: start_sandstorm()
	if storm_active:
		_shelter_check -= dt
		if _shelter_check <= 0.0: _refresh_shelter()
		var moving_time := maxf(0.0, dt - storm_warning)
		_storm_left = maxf(0, _storm_left - moving_time)
		storm_warning = maxf(0.0, storm_warning - dt)
		storm_center += storm_velocity * moving_time
		storm_edge = storm_center.x - 110.0
		if _storm_left <= 0:
			storm_active = false
			has_shelter_target = false
			_next_storm = run.elapsed + 280.0 + posmod(run.world_seed + _storm_serial * 71, 200)
	var radius := Vector2((vehicle.position.x - storm_center.x) / 110.0, (vehicle.position.z - storm_center.y) / 85.0).length()
	var life_fade := smoothstep(0, 8, _storm_duration - _storm_left) * smoothstep(0, 14, _storm_left)
	var dust_target := (1.0 - smoothstep(0.35, 1.0, radius)) * life_fade if storm_active and storm_warning <= 0 and not sheltered else 0.0
	storm_visibility = move_toward(storm_visibility, dust_target, dt * 0.35)
	var exposed := storm_active and storm_warning <= 0.0 and not sheltered and storm_visibility > 0.25
	if storm_active and storm_warning <= 0: mode = "Piesočná búrka"
	var target := maxf(storm_visibility, 0.6 if mode in ["Dážď", "Zamračené", "Sneženie", "Prachový vietor"] else (0.22 if mode == "Oblačno" else 0.0))
	intensity = move_toward(intensity, target, dt * 0.18)
	var strength := _front_strength if forced_mode.is_empty() else 1.0
	wetness = move_toward(wetness, 0.85 * strength if mode == "Dážď" else 0.0, dt * (0.018 if mode == "Dážď" else 0.004))
	world.weather_grip = 1.0 - wetness * 0.25
	world.terrain.soil_moisture = wetness
	_wind_mix = move_toward(_wind_mix, 2.5 if storm_active else (1.5 if mode in ["Vietor", "Prachový vietor"] else 0.0), dt * 0.18)
	world.wind_force = (sin(run.elapsed * 0.28 + run.world_seed) * 0.7 + sin(run.elapsed * 0.91 + run.world_seed * 0.13) * 0.3) * _wind_mix
	if exposed:
		run.parts.engine = maxf(0.0, float(run.parts.engine) - dt * 0.0007)
		run.parts.radiator = maxf(0.0, float(run.parts.radiator) - dt * 0.0012)
	_particles.position = vehicle.position + Vector3.UP * 15
	_snow_particles.position = _particles.position
	rain_mix = move_toward(rain_mix, strength if mode == "Dážď" and not sheltered else 0.0, dt * 0.12)
	snow_mix = move_toward(snow_mix, strength if mode == "Sneženie" and not sheltered else 0.0, dt * 0.12)
	_particles.emitting = rain_mix > 0.015
	_snow_particles.emitting = snow_mix > 0.015
	_particles.color = Color(1, 1, 1, rain_mix * 0.75)
	_snow_particles.color = Color(0.90, 0.95, 1, snow_mix * 0.85)
	_particles.gravity = Vector3(world.wind_force * 0.5, -2.0, world.wind_force)
	_snow_particles.gravity = Vector3(world.wind_force * 0.3, -0.4, world.wind_force * 0.5)
	_wall.visible = storm_active and storm_warning <= 0 and life_fade > 0.01
	_wall.position = Vector3(storm_center.x, vehicle.position.y + 9, storm_center.y)
	_cloud_material.set_shader_parameter("opacity", 0.18 * life_fade)
	_dust.position = Vector3(storm_center.x, vehicle.position.y + 6, storm_center.y)
	_dust.direction = Vector3(storm_velocity.x, 0.1, storm_velocity.y).normalized()
	_dust.emitting = _wall.visible
	_dust.color = Color(1, 1, 1, 0.20 * life_fade)
	run.weather_state = {"next": _next_storm, "edge": storm_edge, "left": _storm_left if storm_active else 0.0,
		"warning": storm_warning, "has_shelter": 1.0 if has_shelter_target else 0.0,
		"shelter_x": shelter_target.x, "shelter_y": shelter_target.y, "shelter_z": shelter_target.z,
		"front_until": _front_until, "front_serial": float(_front_serial), "front_index": float(_front_index), "front_strength": _front_strength, "front_biome": float(_front_biome),
		"storm_serial": float(_storm_serial), "storm_variant": float(storm_variant), "storm_x": storm_center.x, "storm_z": storm_center.y, "storm_vx": storm_velocity.x, "storm_vz": storm_velocity.y, "storm_duration": _storm_duration, "storm_visibility": storm_visibility,
		"wetness": wetness, "intensity": intensity, "rain_mix": rain_mix, "snow_mix": snow_mix, "wind_mix": _wind_mix}
