extends Node3D
## Dynamic weather for Lesná vrchovina. A slow state machine (clear, cloudy,
## rain, downpour, frost, snowfall) eases cloud cover, precipitation and
## temperature towards each state's targets; the ground reacts with its own
## inertia: rain soaks it, sun and wind dry it, snow settles below freezing and
## melts above, and frost hardens soil and glazes wet roads. The ground state
## goes to terrain.gd (grip, ruts, rolling resistance, particles) and to the
## ground shader; the sky, sun, fog, river and falling rain or snow follow the
## air. K advances to the next state at once.
const Surface = preload("res://desert_battlefield/terrain.gd")
var water_world: Script
## name, cloud cover, rain, snowfall, temperature (°C), the states it may become.
const STATES := [
	["Jasno", 0.1, 0.0, 0.0, 14.0, [1]],
	["Zamračené", 0.75, 0.0, 0.0, 8.0, [0, 2, 5]],
	["Dážď", 0.92, 0.6, 0.0, 7.0, [3, 1]],
	["Lejak", 1.0, 1.0, 0.0, 6.0, [2]],
	["Mráz", 0.2, 0.0, 0.0, -7.0, [0, 5]],
	["Sneženie", 0.95, 0.0, 0.9, -3.0, [4, 1]],
	["Piesočná búrka", 0.55, 0.0, 0.0, 11.0, [0, 1]],
	["Hmla", 0.8, 0.0, 0.0, 9.0, [0, 1, 2]],
]
const SANDSTORM := 6
const FOG := 7
## Each landscape's light (the order of endless_terrain.gd's enum): sun, haze,
## saturation, contrast, how much mist lies low.
const MOODS := [
	[Color(1.0, 0.97, 0.88), Color(0.62, 0.72, 0.66), 1.05, 1.03, 0.0],
	[Color(1.0, 0.97, 0.86), Color(0.72, 0.8, 0.84), 1.1, 1.02, 0.0],
	[Color(1.0, 0.88, 0.68), Color(0.86, 0.72, 0.52), 1.08, 1.08, 0.0],
	[Color(0.95, 0.97, 1.0), Color(0.72, 0.78, 0.86), 0.95, 1.06, 0.0],
	[Color(0.93, 0.95, 0.78), Color(0.55, 0.62, 0.5), 0.82, 0.98, 1.0],
	[Color(1.0, 0.95, 0.8), Color(0.78, 0.8, 0.72), 1.18, 1.03, 0.0],
	[Color(1.0, 0.9, 0.7), Color(0.82, 0.74, 0.58), 1.0, 1.06, 0.0],
	[Color(0.9, 0.95, 0.95), Color(0.56, 0.66, 0.68), 0.9, 1.04, 0.5],
	[Color(0.98, 0.98, 0.95), Color(0.66, 0.76, 0.82), 1.0, 1.05, 0.2],
]
var mood_sun := Color.WHITE
var mood_haze := Color(0.68, 0.74, 0.74)
var mood_saturation := 1.0
var mood_contrast := 1.0
var mood_mist := 0.0
## Air of the two extra states, and the wind (crops, road_fx.gd).
var sand := 0.0
var fog_bank := 0.0
var wind := 0.3
## Seconds a state lasts before the next one is drawn.
const DURATION := Vector2(110.0, 200.0)
var state := 0
var clock := 0.0
var duration := 150.0
## Air (eased towards the state).
var cloud := 0.1
var rain := 0.0
var snowfall := 0.0
var temperature := 14.0
## Ground (mirrored into terrain.gd).
var wetness := 0.0
var snow_cover := 0.0
## Depth of the snow pack (m) and how frozen the ponds are (0 open .. 1 solid).
var snow_depth := 0.0
const MAX_SNOW := 0.6
var pond_ice := 0.0
var frost := 0.0
var rng := RandomNumberGenerator.new()
var root: Node3D
var materials: Array = []
var river: ShaderMaterial
var rain_particles: GPUParticles3D
var snow_particles: GPUParticles3D
var last_camera := Vector3.ZERO
var camera_velocity := Vector3.ZERO
var base_sun := 1.3
var base_ambient := 0.45
var base_background := Color()
var mobile: bool = preload("res://offroad/game_settings.gd").is_mobile()

func setup(game: Node3D, ground_materials: Array, river_material: ShaderMaterial) -> void:
	name = "Weather"
	root = game
	if game.world_script != null: water_world = game.world_script
	materials = ground_materials
	river = river_material
	rng.randomize()
	water_world.flood = 0.0
	duration = rng.randf_range(DURATION.x, DURATION.y)
	base_sun = root.sun.light_energy
	var environment: Environment = root.get_node("DesertAtmosphere").environment
	base_ambient = environment.ambient_light_energy
	base_background = environment.background_color
	rain_particles = _precipitation(false)
	snow_particles = _precipitation(true)
	_publish()

## Falling rain streaks or snow flakes in a box that travels with the camera.
func _precipitation(snow: bool) -> GPUParticles3D:
	var particles := GPUParticles3D.new()
	particles.name = "Snowfall" if snow else "Rain"
	particles.amount = (3000 if snow else 2500) if mobile else (9000 if snow else 9000)
	particles.lifetime = 7.0 if snow else 1.1
	particles.preprocess = particles.lifetime
	particles.local_coords = false
	particles.emitting = false
	particles.visibility_aabb = AABB(Vector3(-40, -40, -40), Vector3(80, 60, 80))
	particles.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	var process := ParticleProcessMaterial.new()
	process.emission_shape = ParticleProcessMaterial.EMISSION_SHAPE_BOX
	# Both start in a slab above the camera and fall through the view: rain
	# in about a second, snow in six or seven.
	process.emission_box_extents = Vector3(24, 4, 24) if snow else Vector3(30, 1, 30)
	process.direction = Vector3(0.15, -1, 0.05)
	process.spread = 4.0 if not snow else 12.0
	process.initial_velocity_min = 3.0 if snow else 16.0
	process.initial_velocity_max = 4.5 if snow else 20.0
	# Flakes keep a steady 3–4.5 m/s with a little wind drift; rain accelerates.
	# (No turbulence: it steers the velocity towards the noise field and the
	# flakes stop falling.)
	process.gravity = Vector3(0.1, 0.0, 0.04) if snow else Vector3(0, -9.8, 0)
	process.scale_min = 0.6 if snow else 0.7
	process.scale_max = 1.4 if snow else 1.3
	particles.process_material = process
	var quad := QuadMesh.new()
	quad.size = Vector2(0.07, 0.07) if snow else Vector2(0.012, 0.55)
	var material := StandardMaterial3D.new()
	material.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	material.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES if snow else BaseMaterial3D.BILLBOARD_FIXED_Y
	material.albedo_color = Color(0.97, 0.98, 1.0, 0.9) if snow else Color(0.78, 0.84, 0.9, 0.35)
	material.disable_receive_shadows = true
	# Flakes and drops brushing past the lens fade out instead of filling it.
	material.distance_fade_mode = BaseMaterial3D.DISTANCE_FADE_PIXEL_ALPHA
	material.distance_fade_min_distance = 0.6
	material.distance_fade_max_distance = 2.5
	if snow:
		# Soft round flakes.
		var gradient := Gradient.new()
		# A solid flake with a short soft rim.
		gradient.set_color(0, Color(1, 1, 1, 1))
		gradient.set_color(1, Color(1, 1, 1, 0))
		gradient.add_point(0.55, Color(1, 1, 1, 1))
		var texture := GradientTexture2D.new()
		texture.gradient = gradient
		texture.fill = GradientTexture2D.FILL_RADIAL
		texture.fill_from = Vector2(0.5, 0.5)
		texture.fill_to = Vector2(0.5, 0.0)
		texture.width = 32
		texture.height = 32
		material.albedo_texture = texture
	quad.material = material
	particles.draw_pass_1 = quad
	add_child(particles)
	return particles

func next_weather() -> void:
	var order := [0, 1, 2, 3, 4, 5, 6, 7]
	_enter((order.find(state) + 1) % order.size())

## Climate where the car is (endless_terrain.gd: biome name, temperature
## offset, how often rain comes, whether snow may fall); empty elsewhere.
var climate := {}
var climate_clock := 10.0
## Climate cold enough (°C offset: about 600 m up) that rain falls as snow.
const SNOW_NOT_RAIN := -9.0
## Fog of clear air (the endless world thins it to show its far land).
var clear_fog := 0.0035

## A state the climate allows: no snow or frost in the desert and little rain,
## snow instead of rain high in the mountains, rain more often in the bogs.
func _suited(next: int) -> int:
	if climate.is_empty(): return next if next < SANDSTORM else 1
	# Sand storms belong to the desert and the steppe, fog to the wet and high lands.
	var dry: bool = climate.biome in [2, 6]
	if next == SANDSTORM and not dry: return 1
	if next == 1 and dry and rng.randf() < 0.45: return SANDSTORM
	if next == 1 and climate.biome in [4, 7, 8, 3] and rng.randf() < 0.35: return FOG
	if not climate.snow and next in [4, 5]: return 0 if next == 4 else 1
	if climate.temperature < SNOW_NOT_RAIN and next in [2, 3]: return 5
	if next in [2, 3] and rng.randf() > climate.wet: return 1 if rng.randf() < 0.5 else 0
	# High up the sky seldom clears.
	if next == 0 and climate.height > 650.0 and rng.randf() < 0.5: return 1
	if next == 0 and climate.wet > 1.2 and rng.randf() < 0.4: return 2
	return next

func _enter(next: int) -> void:
	next = _suited(next)
	state = next
	clock = 0.0
	duration = rng.randf_range(DURATION.x, DURATION.y)

func weather_text() -> String:
	var parts := ["%s%s %d °C" % [(climate.name + " · ") if climate.has("name") else "", STATES[state][0], roundi(temperature)]]
	if snow_cover > 0.1: parts.append("sneh %d cm" % roundi(maxf(snow_depth, snow_cover * 0.04) * 100.0))
	if pond_ice > 0.8: parts.append("jazierko zamrznuté")
	elif wetness > 0.1: parts.append("vlhkosť %d %%" % roundi(wetness * 100.0))
	if frost > 0.3: parts.append("mrzne")
	return "  ·  ".join(parts) + "   (K)"

func update(delta: float) -> void:
	clock += delta
	climate_clock += delta
	if climate_clock > 2.0 and water_world.resource_path == "res://offroad/endless_terrain.gd" and root.lead != null:
		climate_clock = 0.0
		var before: String = climate.get("name", "")
		climate = water_world.climate_at(root.lead.position.x, root.lead.position.z, root.lead.position.y)
		# Into another landscape: its weather takes over (the state is redrawn).
		if (climate.name != before or (state in [2, 3] and climate.temperature < SNOW_NOT_RAIN)) and _suited(state) != state: _enter(state)
	if clock > duration:
		var choices: Array = STATES[state][5]
		_enter(choices[rng.randi() % choices.size()])
	var target: Array = STATES[state]
	# The air turns over in about half a minute.
	var air := 1.0 - exp(-delta / 25.0)
	cloud = lerpf(cloud, target[1], air)
	rain = lerpf(rain, target[2], air)
	snowfall = lerpf(snowfall, target[3], air)
	sand = lerpf(sand, 1.0 if state == SANDSTORM else 0.0, air * 1.5)
	fog_bank = lerpf(fog_bank, 1.0 if state == FOG else 0.0, air)
	wind = lerpf(wind, 0.25 + 0.3 * cloud + 0.5 * rain + 0.75 * sand, air)
	if climate.has("biome"):
		var mood: Array = MOODS[climate.biome]
		var change := 1.0 - exp(-delta / 18.0)
		mood_sun = mood_sun.lerp(mood[0], change)
		mood_haze = mood_haze.lerp(mood[1], change)
		mood_saturation = lerpf(mood_saturation, mood[2], change)
		mood_contrast = lerpf(mood_contrast, mood[3], change)
		mood_mist = lerpf(mood_mist, mood[4], change)
	temperature = lerpf(temperature, target[4] + climate.get("temperature", 0.0), 1.0 - exp(-delta / 40.0))
	# Ground: a downpour soaks it in half a minute; drying takes minutes and
	# stops under cloud or frost.
	var freezing := temperature < 0.5
	if rain > 0.02 and not freezing: wetness = minf(1.0, wetness + rain * delta / 30.0)
	else: wetness = maxf(0.0, wetness - delta / 240.0 * (1.0 - cloud * 0.7) * (0.0 if freezing else 1.0))
	# Snow first covers the ground (about two minutes of a heavy fall), then
	# keeps piling up (some 5 cm a minute, up to 60 cm) for as long as it falls.
	if snowfall > 0.02 and freezing:
		snow_cover = minf(1.0, snow_cover + snowfall * delta / 120.0)
		snow_depth = minf(MAX_SNOW, snow_depth + snowfall * delta * 0.0008)
	elif temperature > 1.0:
		# Melting thins the pack first; the ground shows again once it is thin.
		var melt := delta * 0.0005 * clampf(temperature / 8.0, 0.2, 1.5)
		wetness = minf(1.0, wetness + minf(snow_depth, melt) * 20.0)
		snow_depth = maxf(0.0, snow_depth - melt)
		if snow_depth < 0.04: snow_cover = maxf(0.0, minf(snow_cover, snow_depth / 0.04) - delta / 600.0)
	# Still water freezes over after a few minutes of hard frost.
	if temperature < -1.0: pond_ice = minf(1.0, pond_ice + delta / 120.0 * clampf(-temperature / 5.0, 0.3, 1.5))
	elif temperature > 0.5: pond_ice = maxf(0.0, pond_ice - delta / 150.0)
	frost = clampf(frost + delta / 30.0 * (1.0 if freezing else -1.0), 0.0, 1.0)
	# Rivers swell a minute or two behind the rain and fall back slowly.
	var swollen: float = water_world.MAX_FLOOD * clampf(wetness * 1.25 - 0.25, 0.0, 1.0)
	water_world.flood = lerpf(water_world.flood, swollen, 1.0 - exp(-delta / (60.0 if swollen > water_world.flood else 150.0)))
	_publish()
	_visuals(delta)

func _publish() -> void:
	Surface.wetness = wetness
	Surface.snow_cover = snow_cover
	Surface.snow_depth = snow_depth
	Surface.snowfall = snowfall if temperature < 0.5 else 0.0
	Surface.rain = rain if temperature > 0.5 else 0.0
	water_world.pond_ice = pond_ice
	Surface.frost = frost

func _visuals(delta: float = 0.0) -> void:
	_update_overlays()
	for material: ShaderMaterial in materials:
		material.set_shader_parameter("wetness", wetness)
		material.set_shader_parameter("snow_cover", snow_cover)
		material.set_shader_parameter("snow_depth", snow_depth)
		material.set_shader_parameter("soil_bury", smoothstep(0.03, 0.1, snow_depth * snow_cover))
		material.set_shader_parameter("frost", frost)
	var camera: Camera3D = root.camera
	# Lead the falling boxes by where the camera will be when the drops and
	# flakes reach it (rain ~1 s, snow ~4 s), so a fast car never outruns them.
	var now := camera.global_position
	var step := now - last_camera
	last_camera = now
	if delta > 0.0 and step.length() < 30.0:
		camera_velocity = camera_velocity.lerp(step / delta, 1.0 - exp(-delta * 3.0))
	var drift := Vector3(camera_velocity.x, 0.0, camera_velocity.z)
	rain_particles.global_position = now + Vector3(0, 18, 0) + drift * 1.0
	snow_particles.global_position = now + Vector3(0, 14, 0) + drift * 4.0
	# Below freezing whatever still falls is snow.
	rain_particles.emitting = rain > 0.08 and temperature > 0.5
	snow_particles.emitting = snowfall > 0.08
	rain_particles.amount_ratio = clampf(rain, 0.05, 1.0)
	snow_particles.amount_ratio = clampf(snowfall, 0.05, 1.0)
	# Overcast: weaker sun, flatter light, greyer air, more haze.
	root.sun.light_energy = base_sun * (1.0 - 0.7 * cloud) * (1.0 - 0.45 * sand)
	if not climate.is_empty(): root.sun.light_color = mood_sun
	var environment: Environment = root.get_node("DesertAtmosphere").environment
	environment.ambient_light_energy = base_ambient * (1.0 + 0.25 * cloud)
	var grey := Color(0.56, 0.6, 0.63) if snowfall < 0.3 else Color(0.66, 0.69, 0.74)
	environment.background_color = base_background.lerp(grey, cloud * 0.8)
	var perspective: bool = root.camera_mode >= 2
	var fog: float = clear_fog if perspective else root.base_environment.fog_density
	# Cold air is clear: the haze thins as it freezes harder.
	var clear_air := lerpf(1.0, 0.45, smoothstep(1.0, -8.0, temperature))
	environment.fog_density = fog * (1.0 + 1.6 * cloud + 3.0 * maxf(rain, snowfall) + 9.0 * climate.get("mist", 0.0) + 22.0 * sand + 16.0 * fog_bank) * clear_air
	var haze: Color = mood_haze if not climate.is_empty() else Color(0.68, 0.74, 0.74)
	var storm := Color(0.74, 0.52, 0.3)
	environment.fog_light_color = haze.lerp(grey, cloud * 0.8).lerp(storm, sand)
	if not climate.is_empty():
		# Low mist in the marsh and on a foggy day, and each landscape's colours.
		environment.fog_height = camera.global_position.y + 5.0
		environment.fog_height_density = (mood_mist * (0.5 + 0.5 * cloud) + fog_bank) * 0.05 * clear_air
		if not mobile:
			environment.adjustment_enabled = true
			environment.adjustment_saturation = mood_saturation * (1.0 - 0.2 * cloud)
			environment.adjustment_contrast = mood_contrast
	if environment.sky != null and environment.sky.sky_material is ProceduralSkyMaterial:
		var sky := environment.sky.sky_material as ProceduralSkyMaterial
		sky.sky_top_color = root.sky_colors[0].lerp(grey * 0.85, cloud)
		sky.sky_horizon_color = root.sky_colors[1].lerp(haze, 0.45 if not climate.is_empty() else 0.0).lerp(grey, cloud).lerp(storm, sand)
		sky.sky_top_color = sky.sky_top_color.lerp(storm, sand * 0.7)
	if river != null:
		# Rain rings on the surface; a soaked land swells the rivers with silt.
		river.set_shader_parameter("rain", rain)
		river.set_shader_parameter("turbidity", clampf((wetness - 0.4) / 0.6, 0.0, 1.0))
		river.set_shader_parameter("sky_tint", Color(0.68, 0.78, 0.84).lerp(grey, cloud))

## Snow on props: every material of the world's meshes and the cars gets a
## snow overlay as its next pass, attached only while snow lies.
var overlays: Array = []
var overlays_on := false
var overlay_amount := -1.0
const OVERLAY_SHADER := preload("res://offroad/snow_overlay.gdshader")

func _collect_overlays() -> void:
	var seen := {}
	var nodes: Array = get_parent().find_children("*", "GeometryInstance3D", true, false)
	# (Cars carry their own dirt and snow layer, see car_grime.gdshader.)
	for node in nodes:
		var meshes: Array = []
		if node is MultiMeshInstance3D and node.multimesh != null: meshes.append(node.multimesh.mesh)
		elif node is MeshInstance3D:
			if node.material_override != null: _overlay_for(node.material_override, seen, node)
			if node.mesh != null:
				meshes.append(node.mesh)
				for i in node.get_surface_override_material_count():
					var override: Material = node.get_surface_override_material(i)
					if override != null: _overlay_for(override, seen, node)
		for mesh: Mesh in meshes:
			if mesh == null: continue
			for i in mesh.get_surface_count():
				var material := mesh.surface_get_material(i)
				if material != null: _overlay_for(material, seen, node)

func _overlay_for(material: Material, seen: Dictionary, node: Node) -> void:
	# The ground, water and particles draw their own weather.
	if material in seen or material is ShaderMaterial or node is GPUParticles3D or material.next_pass != null: return
	seen[material] = true
	var overlay := ShaderMaterial.new()
	overlay.shader = OVERLAY_SHADER
	if material is BaseMaterial3D:
		var base := material as BaseMaterial3D
		if base.transparency == BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR and base.albedo_texture != null:
			overlay.set_shader_parameter("albedo_texture", base.albedo_texture)
			overlay.set_shader_parameter("use_texture", true)
			overlay.set_shader_parameter("alpha_scissor", base.alpha_scissor_threshold)
		# Foliage (two-sided, cut out) holds less snow than stone or metal.
		overlay.set_shader_parameter("hold", 0.75 if base.cull_mode == BaseMaterial3D.CULL_DISABLED else 1.0)
	overlays.append([material, overlay])

func _update_overlays() -> void:
	var on := snow_cover > 0.02
	if on != overlays_on:
		overlays_on = on
		for pair: Array in overlays: (pair[0] as Material).next_pass = pair[1] if on else null
	if on and absf(snow_cover - overlay_amount) > 0.005:
		overlay_amount = snow_cover
		for pair: Array in overlays: (pair[1] as ShaderMaterial).set_shader_parameter("amount", snow_cover)
