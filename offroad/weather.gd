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
## Each state's sky (offroad/sky.gdshader): zenith, horizon, clouds where the light falls
## and at their heart, cloud cover, softness of their edges, how much sun gets through,
## how fast the clouds go.
const SKIES := [
	[Color("4f85bd"), Color("c7dbe6"), Color("fbfaf3"), Color("aab6c2"), 0.42, 0.07, 1.0, 1.0],
	[Color("76858f"), Color("aeb8be"), Color("c6cbce"), Color("68727c"), 0.86, 0.14, 0.22, 1.5],
	[Color("535d67"), Color("8b959d"), Color("858e96"), Color("3f4750"), 0.97, 0.18, 0.05, 2.2],
	[Color("30373f"), Color("5f6972"), Color("586069"), Color("22272d"), 1.0, 0.2, 0.0, 3.4],
	[Color("5b92cb"), Color("dae8f0"), Color("f5f9fc"), Color("cbd8e2"), 0.3, 0.05, 1.0, 0.5],
	[Color("a6aeb6"), Color("cbd1d6"), Color("d9dde0"), Color("939ba4"), 1.0, 0.22, 0.1, 0.8],
	[Color("a67c4c"), Color("c99a62"), Color("c8985f"), Color("855e33"), 0.74, 0.28, 0.25, 4.5],
	[Color("b3bbbc"), Color("c8cece"), Color("ccd1d1"), Color("a9b0b1"), 0.96, 0.32, 0.08, 0.3],
]
## How much the horizon goes over into the colour of the air, so the land fades into the sky.
const SKY_AIR := 0.6
const SKY_FOG := 0.2
## The way the wind blows (the clouds go the same way).
const WIND_WAY := Vector3(-0.93, 0.0, -0.37)
## How much each landscape gives the wind to carry (the order of endless_terrain.gd's enum).
const DUST_OF_LAND := [0.0, 0.12, 1.0, 0.35, 0.0, 0.4, 0.85, 0.1, 0.3]
const DUST_SAND := Color(0.9, 0.78, 0.58)
const DUST_SNOW := Color(0.93, 0.95, 0.98)
const DUST_GRAIN := Color(0.76, 0.62, 0.4)
const GRAIN_ALPHA := 0.9
const PUFF_ALPHA := 0.42
## How often (s) the sky's colours go to the shader, and at most how often its reflection is
## redrawn, if it has changed enough to matter by then.
const SKY_EVERY := 0.2
const SKY_RADIANCE_EVERY := 5.0
const SKY_RADIANCE_CHANGE := 0.02
## How fast the wind carries the clouds (noise repeats per second, at speed 1).
const CLOUD_DRIFT := 0.0022
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
## The sky as it eases from one state's towards the next.
var sky_look: Array = SKIES[0].duplicate()
var sky_material: ShaderMaterial
var sky_clock := 0.0
var sky_radiance_clock := 0.0
## How the sky looked when its reflection was last redrawn, and the number of that redraw.
var sky_radiance_seen := -1.0
var sky_radiance_tick := 0
static var sky_globals_ready := false
var sky_drift := Vector4.ZERO
## Lightning, 1 at the flash, dying away.
var lightning := 0.0
## Grains of sand or snow hopping over the ground, and small puffs of dust rolling along it;
## off (no cost at all) where there is nothing for the wind to carry.
var dust_grains: GPUParticles3D
var dust_puffs: GPUParticles3D
var dust_now := 0.0
var dust_tint := DUST_SAND
var gust_clock := 0.0

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
	var plain_water: bool = preload("res://offroad/game_settings.gd").effective_quality() == "low"
	dust_grains = _dust(false, plain_water)
	if not plain_water: dust_puffs = _dust(true, plain_water)
	_prepare_sky_globals()
	_publish()

## What the sky's shader reads must exist before it is compiled (and stays for the next run).
func _prepare_sky_globals() -> void:
	if sky_globals_ready: return
	sky_globals_ready = true
	for colour: String in ["sky_zenith", "sky_horizon", "sky_cloud_lit", "sky_cloud_dark"]:
		RenderingServer.global_shader_parameter_add(colour, RenderingServer.GLOBAL_VAR_TYPE_COLOR, Color.WHITE)
	for numbers: String in ["sky_cloud", "sky_drift"]:
		RenderingServer.global_shader_parameter_add(numbers, RenderingServer.GLOBAL_VAR_TYPE_VEC4, Vector4.ZERO)

## The battlefield's plain sky gives way to the weather's. Its reflection is redrawn over several
## frames, and seldom (see _show_sky).
func _install_sky(sky: Sky) -> void:
	var noise := FastNoiseLite.new()
	noise.noise_type = FastNoiseLite.TYPE_PERLIN
	noise.seed = 41
	noise.frequency = 0.012
	noise.fractal_octaves = 5
	var texture := NoiseTexture2D.new()
	texture.width = 256 if mobile else 512
	texture.height = texture.width
	texture.seamless = true
	texture.generate_mipmaps = true
	texture.noise = noise
	sky_material = ShaderMaterial.new()
	sky_material.shader = preload("res://offroad/sky.gdshader")
	sky_material.set_shader_parameter("cloud_noise", texture)
	sky_material.set_shader_parameter("sun_direction", root.sun.global_transform.basis.z.normalized())
	var plain: bool = preload("res://offroad/game_settings.gd").effective_quality() == "low"
	sky_material.set_shader_parameter("cloud_detail", 0.0 if plain else 1.0)
	sky.process_mode = Sky.PROCESS_MODE_INCREMENTAL
	sky.sky_material = sky_material
	_publish_sky()

## The sky as it stands, at once (a new state only eases there).
func snap_sky() -> void:
	sky_look = (SKIES[state] as Array).duplicate()
	if sky_material != null: _publish_sky()

func _publish_sky() -> void:
	var air: Color = root.get_node("DesertAtmosphere").environment.fog_light_color
	RenderingServer.global_shader_parameter_set("sky_zenith", (sky_look[0] as Color).srgb_to_linear())
	RenderingServer.global_shader_parameter_set("sky_horizon", (sky_look[1] as Color).lerp(air, SKY_AIR).srgb_to_linear())
	RenderingServer.global_shader_parameter_set("sky_cloud_lit", (sky_look[2] as Color).srgb_to_linear())
	RenderingServer.global_shader_parameter_set("sky_cloud_dark", (sky_look[3] as Color).srgb_to_linear())
	RenderingServer.global_shader_parameter_set("sky_cloud", Vector4(sky_look[4], sky_look[5], sky_look[6], lightning))

## A flash for the thunder to follow (car_audio.gd).
func flash() -> void:
	lightning = 1.0

## The clouds move every frame, the rest a few times a second, none of it touching the reflection;
## that is redrawn only when the sky has changed, and never often.
func _show_sky(delta: float) -> void:
	if sky_material == null: return
	var target: Array = SKIES[state]
	var change := 1.0 - exp(-delta / 25.0)
	for i in sky_look.size():
		if i < 4: sky_look[i] = (sky_look[i] as Color).lerp(target[i], change)
		else: sky_look[i] = lerpf(sky_look[i], target[i], change)
	# The clouds go with the wind, the small ones a little faster, across the same way.
	var carry := delta * CLOUD_DRIFT * (sky_look[7] as float) * (0.6 + wind)
	sky_drift = Vector4(fposmod(sky_drift.x + carry, 1.0), fposmod(sky_drift.y + carry * 0.4, 1.0), fposmod(sky_drift.z + carry * 2.6, 1.0), fposmod(sky_drift.w + carry * 1.2, 1.0))
	RenderingServer.global_shader_parameter_set("sky_drift", sky_drift)
	var flashing := lightning > 0.0
	if flashing:
		# Two pulses, gone within a second.
		lightning = maxf(0.0, lightning - delta * 2.4)
		RenderingServer.global_shader_parameter_set("sky_cloud", Vector4(sky_look[4], sky_look[5], sky_look[6], lightning * absf(sin(lightning * 9.0))))
	sky_clock += delta
	if sky_clock >= SKY_EVERY:
		sky_clock = 0.0
		if not flashing: _publish_sky()
	sky_radiance_clock += delta
	if sky_radiance_clock >= SKY_RADIANCE_EVERY:
		sky_radiance_clock = 0.0
		var seen: float = (sky_look[4] as float) + (sky_look[0] as Color).get_luminance()
		if absf(seen - sky_radiance_seen) >= SKY_RADIANCE_CHANGE:
			sky_radiance_seen = seen
			sky_radiance_tick = (sky_radiance_tick + 1) % 1000
			sky_material.set_shader_parameter("radiance_tick", float(sky_radiance_tick))

## What the wind carries, in a box that travels with the camera, updated less often than it is
## drawn. Grains are tiny and hop (thrown up, falling back); puffs are small, soft and low, and
## grow as they go. Few pixels either way: nothing here ever covers much of the screen.
func _dust(puffs: bool, plain: bool) -> GPUParticles3D:
	var particles := GPUParticles3D.new()
	particles.name = "WindPuffs" if puffs else "WindGrains"
	var grains: int = (120 if plain else 220) if mobile else 400
	particles.amount = (24 if mobile else 44) if puffs else grains
	particles.lifetime = 2.4 if puffs else 1.1
	particles.fixed_fps = 20
	particles.interpolate = true
	particles.local_coords = false
	particles.emitting = false
	particles.visibility_aabb = AABB(Vector3(-40, -15, -40), Vector3(80, 30, 80))
	particles.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	var process := ParticleProcessMaterial.new()
	process.emission_shape = ParticleProcessMaterial.EMISSION_SHAPE_BOX
	process.emission_box_extents = Vector3(12, 0.25, 12) if puffs else Vector3(9, 0.2, 9)
	process.direction = (WIND_WAY + Vector3.UP * (0.08 if puffs else 0.22)).normalized()
	process.spread = 10.0 if puffs else 16.0
	process.gravity = Vector3(0.0, -0.15, 0.0) if puffs else Vector3(0.0, -2.2, 0.0)
	process.initial_velocity_min = 2.5 if puffs else 4.0
	process.initial_velocity_max = 5.0 if puffs else 9.0
	process.scale_min = 0.6
	process.scale_max = 1.5
	if puffs:
		var growth := Curve.new()
		growth.add_point(Vector2(0.0, 0.45))
		growth.add_point(Vector2(1.0, 1.0))
		var growth_texture := CurveTexture.new()
		growth_texture.curve = growth
		process.scale_curve = growth_texture
	# In and out softly: nothing pops up or vanishes.
	var ramp := Gradient.new()
	ramp.set_color(0, Color(1, 1, 1, 0))
	ramp.set_color(1, Color(1, 1, 1, 0))
	ramp.add_point(0.2, Color(1, 1, 1, 1))
	ramp.add_point(0.65, Color(1, 1, 1, 1))
	var ramp_texture := GradientTexture1D.new()
	ramp_texture.gradient = ramp
	ramp_texture.width = 32
	process.color_ramp = ramp_texture
	particles.process_material = process
	var quad := QuadMesh.new()
	quad.size = Vector2(3.2, 1.7) if puffs else Vector2(0.085, 0.085)
	var material := StandardMaterial3D.new()
	material.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	material.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
	material.vertex_color_use_as_albedo = true
	material.disable_receive_shadows = true
	var soft := Gradient.new()
	soft.set_color(0, Color(1, 1, 1, 1))
	soft.set_color(1, Color(1, 1, 1, 0))
	if not puffs: soft.add_point(0.5, Color(1, 1, 1, 1))
	var texture := GradientTexture2D.new()
	texture.gradient = soft
	texture.fill = GradientTexture2D.FILL_RADIAL
	texture.fill_from = Vector2(0.5, 0.5)
	texture.fill_to = Vector2(0.5, 0.0)
	texture.width = 32 if puffs else 16
	texture.height = texture.width
	material.albedo_texture = texture
	material.albedo_color = Color(DUST_SAND, PUFF_ALPHA) if puffs else Color(DUST_GRAIN, GRAIN_ALPHA)
	# (Right by the lens they fade out, instead of crossing it as blots.)
	material.distance_fade_mode = BaseMaterial3D.DISTANCE_FADE_PIXEL_ALPHA
	material.distance_fade_min_distance = 1.5 if puffs else 2.5
	material.distance_fade_max_distance = 5.0 if puffs else 7.0
	quad.material = material
	particles.draw_pass_1 = quad
	add_child(particles)
	return particles

## How much the wind carries now (0..1).
func dust_amount() -> float:
	return dust_now

## Gusts come and go; what they lift depends on the land, and nothing lifts off wet ground.
func _show_wind(delta: float, camera_at: Vector3) -> void:
	if dust_grains == null: return
	gust_clock += delta
	var gust := 0.5 + 0.5 * sin(gust_clock * 0.37) * sin(gust_clock * 0.113 + 1.0)
	var land: float = DUST_OF_LAND[climate.biome] if climate.has("biome") else 0.3
	# Dry snow blows too, and more readily than sand.
	var snowy := smoothstep(0.3, 0.8, snow_cover) * (1.0 if temperature < 0.0 else 0.0)
	var lifted := maxf(land * (1.0 - snowy), 0.9 * snowy)
	var dry := clampf(1.0 - wetness * 2.5, 0.0, 1.0) * (1.0 - clampf(rain * 3.0, 0.0, 1.0))
	var target := clampf(lifted * dry * (0.35 + wind) * (0.55 + 0.45 * gust) + sand, 0.0, 1.0)
	dust_now = lerpf(dust_now, target, 1.0 - exp(-delta * 1.5))
	var on := dust_now > 0.04
	if dust_grains.emitting != on:
		dust_grains.emitting = on
		if dust_puffs != null: dust_puffs.emitting = on
	if not on: return
	var ground: float = Surface.height_at(camera_at.x, camera_at.z)
	var speed := 0.6 + wind * 1.1
	var ahead := Vector3(camera_at.x, 0.0, camera_at.z) - WIND_WAY * 5.0 + Vector3(camera_velocity.x, 0.0, camera_velocity.z) * 0.7
	dust_grains.global_position = ahead + Vector3.UP * (ground + 0.15)
	dust_grains.amount_ratio = clampf(dust_now * 2.0, 0.05, 1.0)
	dust_grains.speed_scale = speed
	if dust_puffs != null:
		dust_puffs.global_position = ahead + Vector3.UP * (ground + 0.5)
		dust_puffs.amount_ratio = clampf(dust_now * 1.6, 0.05, 1.0)
		dust_puffs.speed_scale = speed
	# (The tint only when it is due: these are materials too.)
	var tint: Color = DUST_SAND.lerp(DUST_SNOW, snowy)
	if not tint.is_equal_approx(dust_tint):
		dust_tint = tint
		((dust_grains.draw_pass_1 as QuadMesh).material as StandardMaterial3D).albedo_color = Color(DUST_GRAIN.lerp(DUST_SNOW, snowy), GRAIN_ALPHA)
		if dust_puffs != null: ((dust_puffs.draw_pass_1 as QuadMesh).material as StandardMaterial3D).albedo_color = Color(tint, PUFF_ALPHA)

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
	_show_wind(delta, now)
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
	if environment.sky != null and sky_material == null: _install_sky(environment.sky)
	# (The sky fades into the air by its own horizon: fog over all of it would only wash it out.)
	if perspective and sky_material != null: environment.fog_sky_affect = SKY_FOG
	_show_sky(delta)
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
