extends Node
## The film around the drive on the endless road, after road-race games of the
## Need for Speed: The Run kind: a lens the weather lands on (road_fx.gdshader),
## camera shake and a wider view at speed, what the wind carries through each
## landscape, a card naming each new landscape, and the sun in the lens. Off on
## phones and the lowest graphics quality, which keep the bare picture.
const T = preload("res://offroad/endless_terrain.gd")
const Settings = preload("res://offroad/game_settings.gd")
## What each landscape's air carries: colour, size, speed, how many (share),
## falling speed.
const AIR := [
	[Color(0.5, 0.42, 0.14, 0.9), 0.07, 1.2, 0.3, 0.35], # forest: leaves
	[Color(1.0, 0.98, 0.8, 0.7), 0.03, 0.9, 0.4, -0.05], # meadow: seeds
	[Color(0.85, 0.68, 0.42, 0.42), 0.09, 7.0, 0.5, 0.0], # desert: sand
	[Color(0.95, 0.97, 1.0, 0.5), 0.05, 6.0, 0.5, 0.2], # mountains: spindrift
	[Color(0.8, 0.95, 0.5, 0.9), 0.025, 0.5, 0.35, 0.0], # marsh: midges
	[Color(0.85, 0.75, 0.42, 0.8), 0.035, 2.2, 0.5, 0.1], # fields: chaff
	[Color(0.8, 0.68, 0.42, 0.4), 0.07, 5.0, 0.4, 0.0], # steppe: dust
	[Color(0.3, 0.3, 0.14, 0.85), 0.04, 0.8, 0.25, 0.4], # taiga: needles
	[Color(1.0, 0.98, 0.85, 0.6), 0.03, 1.5, 0.3, 0.05], # foothills: seeds
]
const BASE_FOV := [55.0, 55.0, 62.0, 70.0, 55.0, 78.0, 60.0]
var game: Node3D
var car: CharacterBody3D
var weather: Node
var layer: CanvasLayer
var lens: ColorRect
var material: ShaderMaterial
var air: GPUParticles3D
var air_process: ParticleProcessMaterial
var air_quad: StandardMaterial3D
var card: Label
var card_clock := 99.0
var biome := -1
var shake := 0.0
var noise_time := 0.0
var landing_seen := 0
var sun_visible := 0.0
var sun_clock := 0.0
var last_mode := -1

func setup(root: Node3D) -> void:
	game = root
	car = root.lead
	weather = root.test_grounds.weather
	landing_seen = car.landing_count
	layer = CanvasLayer.new()
	layer.layer = -1
	add_child(layer)
	lens = ColorRect.new()
	lens.set_anchors_preset(Control.PRESET_FULL_RECT)
	lens.mouse_filter = Control.MOUSE_FILTER_IGNORE
	material = ShaderMaterial.new()
	material.shader = preload("res://offroad/road_fx.gdshader")
	lens.material = material
	layer.add_child(lens)
	_build_air()
	var cards := CanvasLayer.new()
	add_child(cards)
	card = Label.new()
	card.set_anchors_preset(Control.PRESET_CENTER_TOP)
	card.grow_horizontal = Control.GROW_DIRECTION_BOTH
	# (On a phone under the road's name and any message.)
	card.offset_top = 260.0 if root.touch_ui else 90.0
	card.add_theme_font_size_override("font_size", 46)
	card.add_theme_constant_override("outline_size", 8)
	card.add_theme_color_override("font_outline_color", Color(0, 0, 0, 0.55))
	card.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	card.modulate.a = 0.0
	cards.add_child(card)
	var environment: Environment = root.get_node("DesertAtmosphere").environment
	# (Not on a phone: glow, and the lens below, are passes over all its millions of pixels.)
	if _rich() and not Settings.is_mobile():
		environment.glow_enabled = true
		environment.glow_intensity = 0.55
		environment.glow_bloom = 0.04
		environment.glow_hdr_threshold = 1.1

func _rich() -> bool:
	return game.render_profile == "reference" if Settings.is_mobile() else game.render_profile != "low"

func _build_air() -> void:
	air = GPUParticles3D.new()
	air.name = "Air"
	air.amount = 180 if Settings.is_mobile() else 500
	air.lifetime = 4.0
	air.local_coords = false
	air.visibility_aabb = AABB(Vector3(-40, -20, -40), Vector3(80, 40, 80))
	air.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	air_process = ParticleProcessMaterial.new()
	air_process.emission_shape = ParticleProcessMaterial.EMISSION_SHAPE_BOX
	air_process.emission_box_extents = Vector3(22, 5, 22)
	air_process.direction = Vector3(1, 0, 0.35)
	air_process.spread = 18.0
	air_process.scale_min = 0.6
	air_process.scale_max = 1.5
	air_process.angle_max = 180.0
	air.process_material = air_process
	var quad := QuadMesh.new()
	quad.size = Vector2.ONE
	air_quad = StandardMaterial3D.new()
	air_quad.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	air_quad.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	air_quad.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
	air_quad.vertex_color_use_as_albedo = true
	air_quad.distance_fade_mode = BaseMaterial3D.DISTANCE_FADE_PIXEL_ALPHA
	air_quad.distance_fade_min_distance = 0.5
	air_quad.distance_fade_max_distance = 2.0
	var dot := GradientTexture2D.new()
	var gradient := Gradient.new()
	gradient.set_color(0, Color(1, 1, 1, 1))
	gradient.set_color(1, Color(1, 1, 1, 0))
	dot.gradient = gradient
	dot.fill = GradientTexture2D.FILL_RADIAL
	dot.fill_from = Vector2(0.5, 0.5)
	dot.fill_to = Vector2(0.5, 0.0)
	dot.width = 32
	dot.height = 32
	air_quad.albedo_texture = dot
	quad.material = air_quad
	air.draw_pass_1 = quad
	add_child(air)

## The air and the card change with the landscape.
func _enter_land(next: int) -> void:
	var first := biome == -1
	biome = next
	var spec: Array = AIR[next]
	air_quad.albedo_color = spec[0]
	air_process.color = Color.WHITE
	(air.draw_pass_1 as QuadMesh).size = Vector2.ONE * float(spec[1])
	air_process.initial_velocity_min = float(spec[2]) * 0.7
	air_process.initial_velocity_max = float(spec[2]) * 1.2
	air_process.gravity = Vector3(0, -float(spec[4]), 0)
	air.amount_ratio = float(spec[3])
	air.emitting = true
	card.text = T.BIOME_NAMES[next].to_upper()
	card_clock = 0.0 if not first or true else 99.0

func _process(delta: float) -> void:
	if game.paused or weather.climate.is_empty(): return
	var rich := _rich()
	var climate: Dictionary = weather.climate
	if int(climate.biome) != biome: _enter_land(int(climate.biome))
	# The card: shows, holds, fades, with the height and the temperature.
	card_clock += delta
	if card_clock < 5.0:
		card.modulate.a = smoothstep(0.0, 0.6, card_clock) * (1.0 - smoothstep(3.8, 5.0, card_clock))
		card.text = "%s\n%d m · %d °C" % [T.BIOME_NAMES[biome].to_upper(), roundi(car.position.y), roundi(weather.temperature)]
	else: card.modulate.a = 0.0
	var camera: Camera3D = game.camera
	var at := camera.global_position
	air.global_position = at + Vector3(0.0, 1.0, 0.0) - camera.global_basis.z * 10.0
	var chase: bool = game.camera_mode >= 2
	lens.visible = rich and chase and not Settings.is_mobile()
	var kmh := absf(car.speed) * 3.6
	var fast := smoothstep(35.0, 120.0, kmh)
	# Weather on the lens: not through the windscreen, which has its own.
	var outside: bool = game.camera_mode != 5
	material.set_shader_parameter("speed_blur", fast)
	material.set_shader_parameter("lens_rain", smoothstep(0.08, 0.7, weather.rain) * (1.0 if outside else 0.0) * (1.0 if weather.temperature > 0.5 else 0.0))
	material.set_shader_parameter("frost", smoothstep(-1.0, -11.0, weather.temperature))
	material.set_shader_parameter("heat", smoothstep(26.0, 36.0, weather.temperature) * (1.0 - weather.cloud) * 1.2)
	material.set_shader_parameter("sand", weather.sand * (1.0 if outside else 0.4))
	_sun(delta, camera)
	if not rich or not chase:
		camera.h_offset = 0.0
		camera.v_offset = 0.0
		return
	# Shake: rough ground and speed, and each hard landing.
	if car.landing_count != landing_seen:
		landing_seen = car.landing_count
		shake = minf(1.0, shake + car.landing_impact_speed * 0.08)
	shake = maxf(0.0, shake - delta * 2.4)
	noise_time += delta * (6.0 + kmh * 0.12)
	var rough: float = fast * 0.012 + shake * 0.09
	if game.camera_mode == 5: rough *= 0.4
	camera.h_offset = sin(noise_time * 1.13) * sin(noise_time * 2.7 + 1.0) * rough
	camera.v_offset = sin(noise_time * 1.71 + 0.5) * sin(noise_time * 3.1) * rough
	# A wider view at speed.
	var wide: float = BASE_FOV[game.camera_mode] + fast * 7.0
	camera.fov = lerpf(camera.fov, wide, 1.0 - exp(-delta * 2.0))

## The sun in the lens: fades in when nothing stands before it (trunks and
## buildings: checked now and then along the line to it).
func _sun(delta: float, camera: Camera3D) -> void:
	var sun: DirectionalLight3D = game.sun
	var towards := sun.global_basis.z
	var ahead := towards.dot(-camera.global_basis.z)
	var visible_now := 0.0
	if ahead > 0.15 and not camera.is_position_behind(camera.global_position + towards * 100.0):
		var open: float = 1.0 - weather.cloud
		visible_now = smoothstep(0.15, 0.75, ahead) * open * open
		sun_clock -= delta
		if sun_clock <= 0.0:
			sun_clock = 0.25
			var space := car.get_world_3d().direct_space_state
			var query := PhysicsRayQueryParameters3D.create(camera.global_position, camera.global_position + towards * 180.0)
			query.exclude = [car.get_rid()]
			sun_blocked = not space.intersect_ray(query).is_empty()
		if sun_blocked: visible_now = 0.0
		var on_screen := camera.unproject_position(camera.global_position + towards * 100.0) / camera.get_viewport().get_visible_rect().size
		material.set_shader_parameter("sun_at", on_screen)
		material.set_shader_parameter("flare_tint", Color(sun.light_color.r, sun.light_color.g, sun.light_color.b))
	sun_visible = lerpf(sun_visible, visible_now, 1.0 - exp(-delta * 5.0))
	material.set_shader_parameter("flare", sun_visible)

var sun_blocked := false

func _exit_tree() -> void:
	if game != null and is_instance_valid(game.camera):
		game.camera.h_offset = 0.0
		game.camera.v_offset = 0.0
