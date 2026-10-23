class_name DriveWeather
extends Node3D
## Weather is time-based, while the chase is spatial and only garages shelter the car.
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
var forced_mode := "" # Local preview/test override; never persisted.
var _particles: CPUParticles3D
var _precip_mesh: BoxMesh
var _precip_material: StandardMaterial3D
var _wall: MeshInstance3D
var _snow_particles: CPUParticles3D
var rain_mix := 0.0
var snow_mix := 0.0
var _wind_mix := 0.0


func _ready() -> void:
	physics_interpolation_mode = Node.PHYSICS_INTERPOLATION_MODE_OFF
	_particles = CPUParticles3D.new()
	_particles.amount = 160
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
	material.albedo_color = Color("b2c6cf")
	material.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	material.vertex_color_use_as_albedo = true
	mesh.material = material
	_particles.mesh = mesh
	_particles.emitting = false
	add_child(_particles)
	_snow_particles = CPUParticles3D.new()
	_snow_particles.amount = 96
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
	_wall = MeshInstance3D.new()
	var cloud := BoxMesh.new()
	cloud.size = Vector3(12, 35, 250)
	var dust := StandardMaterial3D.new()
	dust.albedo_color = Color(0.55, 0.40, 0.22, 0.65)
	dust.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	dust.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	cloud.material = dust
	_wall.mesh = cloud
	_wall.visible = false
	_wall.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(_wall)
	if not run.weather_state.is_empty():
		_next_storm = float(run.weather_state.get("next", run.elapsed + 120))
		storm_edge = float(run.weather_state.get("edge", vehicle.position.x - 120))
		_storm_left = float(run.weather_state.get("left", 0))
		storm_active = _storm_left > 0


func _process(dt: float) -> void:
	var phase := int(floor(run.elapsed / 100.0))
	var biome := world.biome_at(vehicle.position.x)
	var weather := posmod(phase + run.world_seed, 5)
	mode = ["Jasno", "Oblačno", "Dážď", "Hmla", "Vietor"][weather]
	if biome == 4 and weather in [2, 4]: mode = "Sneženie"
	if biome in [0, 1] and weather == 2: mode = "Prachový vietor"
	if not forced_mode.is_empty(): mode = forced_mode
	var shelter := world.nearest_building(vehicle.position, 12.0)
	sheltered = not shelter.is_empty() and shelter.get("kind", "") in ["repair", "workshop", "paint"] and absf(vehicle.position.x - float(shelter.position.x)) < 5.5 and absf(vehicle.position.z - float(shelter.position.z)) < 4.5 and absf(vehicle.speed) < 0.5
	if run.elapsed >= _next_storm and not storm_active:
		# Start a fair chase only when an actual reachable garage is nearby.
		var garage := world.nearest_building(vehicle.position, 145)
		if not garage.is_empty() and garage.get("kind", "") in ["repair", "workshop", "paint"]:
			storm_active = true
			storm_edge = vehicle.position.x - 105
			_storm_left = 70
			_next_storm = run.elapsed + 240
	if storm_active:
		_storm_left -= dt
		storm_edge += dt * 13.5
		mode = "Piesočná búrka"
		if _storm_left <= 0: storm_active = false
	var exposed := storm_active and vehicle.position.x < storm_edge + 22 and not sheltered
	var target := 1.0 if exposed else (0.6 if mode in ["Dážď", "Hmla", "Sneženie", "Prachový vietor"] else (0.22 if mode == "Oblačno" else 0.0))
	intensity = move_toward(intensity, target, dt * 0.18)
	wetness = move_toward(wetness, 0.85 if mode == "Dážď" else 0.0, dt * 0.02)
	world.weather_grip = 1.0 - wetness * 0.25
	world.terrain.soil_moisture = wetness
	_wind_mix = move_toward(_wind_mix, 2.5 if storm_active else (1.5 if mode in ["Vietor", "Prachový vietor"] else 0.0), dt * 0.18)
	world.wind_force = sin(run.elapsed * 0.28 + run.world_seed) * _wind_mix
	if exposed:
		run.parts.engine = maxf(0.0, float(run.parts.engine) - dt * 0.0007)
		run.parts.radiator = maxf(0.0, float(run.parts.radiator) - dt * 0.0012)
	_particles.global_position = vehicle.position + Vector3.UP * 15
	_snow_particles.global_position = _particles.global_position
	rain_mix = move_toward(rain_mix, 1.0 if mode == "Dážď" and not sheltered else 0.0, dt * 0.12)
	snow_mix = move_toward(snow_mix, 1.0 if mode == "Sneženie" and not sheltered else 0.0, dt * 0.12)
	_particles.emitting = rain_mix > 0.015
	_snow_particles.emitting = snow_mix > 0.015
	_particles.color = Color(0.65, 0.76, 0.82, rain_mix * 0.70)
	_snow_particles.color = Color(0.70, 0.80, 0.84, snow_mix * 0.85)
	_particles.gravity = Vector3(world.wind_force * 0.5, -2.0, world.wind_force)
	_snow_particles.gravity = Vector3(world.wind_force * 0.3, -0.4, world.wind_force * 0.5)
	_wall.visible = storm_active
	_wall.position = Vector3(storm_edge, vehicle.position.y + 12, world.center_at(storm_edge))
	run.weather_state = {"next": _next_storm, "edge": storm_edge, "left": _storm_left if storm_active else 0.0}
