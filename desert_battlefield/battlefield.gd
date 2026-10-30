extends Node3D
## Starts as an uninterrupted, HUD-free moving diorama. Space enables player driving.
const Surface = preload("res://desert_battlefield/terrain.gd")
const PROFILES := ["notebook", "reference", "low"]
const PROFILE_NAMES := {"notebook": "Notebook", "reference": "Referenčná", "low": "Nízka"}
const CAMERA_NAMES := ["Prehľad", "Blízko zhora", "Za autom", "Nízko za autom", "Orbit", "Zo strechy", "Voľná · prstom"]
## Camera modes that orbit the car and follow finger/mouse dragging.
const FREE_CAMERAS := [4, 6]
const OVERVIEW_OFFSET := Vector3(41, 120, 58)
@export_enum("Auto", "Notebook", "Reference", "Low") var quality := "Auto"
var quality_name := "reference"
var render_profile := "reference"
var elapsed := 0.0
var driving := false
var paused := false
var focus := Vector3(0, 1, 4)
## Orthographic size of the overview camera (cinematic 91, driving 65).
var camera_size := 91.0
var camera_mode := 0
var close_size := 26.0
## Chase/orbit distances in metres for modes 2, 3 and 4.
var follow_distance := [0.0, 0.0, 8.5, 5.2, 12.0, 0.0, 9.0]
var orbit_yaw := 0.7
var orbit_pitch := 0.42
var orbit_idle := 0.0
var chase_heading := 0.0
var camera_position := Vector3.ZERO
var camera_snap := true
## Fine particle density: fewer ballistic grains when the view is zoomed out.
var dust_detail := 1.0
## Dust-cloud density: ground-level views look through the cloud, so it is thinned.
var dust_density := 1.0
var test_spots := Surface.test_spots()
## The autonomous patrol (the original cinematic diorama). Off for test driving;
## `-- --patrol` or the regression scripts turn it on.
var patrol_enabled := false
var current_spot := -1
var soil := preload("res://desert_battlefield/soil.gd").new()
var test_grounds: Node3D
var driving_card: PanelContainer
var driving_readout: Label
var status_label: Label
var help_card: PanelContainer
var readout_clock := 0.0
var toast_time := 0.0
var base_environment: Environment
@onready var camera: Camera3D = $Camera3D
@onready var sun: DirectionalLight3D = $LateAfternoonSun
@onready var lead: CharacterBody3D = $Patrol/Scout01

func _ready() -> void:
	_configure_quality()
	soil.configure(self)
	_configure_test_grounds()
	_create_driving_readout()
	if OS.has_feature("android") or "--touch-controls" in OS.get_cmdline_user_args():
		add_child(preload("res://desert_battlefield/mobile_controls.gd").new())
	if "--patrol" in OS.get_cmdline_user_args(): patrol_enabled = true
	if not patrol_enabled:
		# Test-driving mode: the Jeep waits at the asphalt lane, the van beside it
		# on the gravel lane, and the player starts in control.
		var cars := vehicles()
		for i in cars.size():
			var spot: Array = test_spots[mini(i, test_spots.size() - 1)]
			cars[i].place_on_ground(Vector3(spot[1], 0, spot[2]), spot[3])
		teleport_to_test(0)

## Patrol vehicles only (the Patrol node also holds their tread-mesh roots).
func vehicles() -> Array:
	return $Patrol.get_children().filter(func(node): return node is CharacterBody3D)

func _configure_test_grounds() -> void:
	# The dune material hides the authored mesh beneath both flattened grounds.
	for material in [($SculptedDunes as MeshInstance3D).material_override, soil.fine_material]:
		material.set_shader_parameter("test_hole_a", Surface.COURSE)
		material.set_shader_parameter("test_hole_b", Surface.PAD)
		material.set_shader_parameter("test_hole_c", Surface.ROCKS)
	test_grounds = preload("res://desert_battlefield/test_course.gd").new()
	add_child(test_grounds)
	test_grounds.build(self)

func _panel_style(alpha: float = 0.78) -> StyleBoxFlat:
	var background := StyleBoxFlat.new()
	background.bg_color = Color(0.09, 0.08, 0.06, alpha)
	background.set_corner_radius_all(8)
	background.content_margin_left = 16
	background.content_margin_right = 16
	background.content_margin_top = 10
	background.content_margin_bottom = 10
	return background

var touch_ui: bool:
	get: return OS.has_feature("android") or "--touch-controls" in OS.get_cmdline_user_args()

func _create_driving_readout() -> void:
	var layer := CanvasLayer.new()
	layer.name = "DrivingReadout"
	add_child(layer)
	driving_card = PanelContainer.new()
	driving_card.position = Vector2(24, 24)
	driving_card.add_theme_stylebox_override("panel", _panel_style())
	driving_card.mouse_filter = Control.MOUSE_FILTER_IGNORE
	layer.add_child(driving_card)
	driving_readout = Label.new()
	driving_readout.add_theme_font_size_override("font_size", 25 if touch_ui else 26)
	driving_readout.add_theme_color_override("font_color", Color("f3e4c8"))
	driving_card.add_child(driving_readout)
	driving_card.hide()
	status_label = Label.new()
	status_label.add_theme_font_size_override("font_size", 22)
	status_label.add_theme_color_override("font_color", Color("f3e4c8"))
	status_label.add_theme_color_override("font_outline_color", Color(0.09, 0.08, 0.06, 0.9))
	status_label.add_theme_constant_override("outline_size", 6)
	if touch_ui:
		# The pedals own the bottom edge on phones; show the status line under
		# the speed panel instead.
		status_label.position = Vector2(26, 196)
	else:
		status_label.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_LEFT)
		status_label.position = Vector2(24, -52)
		status_label.grow_vertical = Control.GROW_DIRECTION_BEGIN
	status_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	layer.add_child(status_label)
	help_card = PanelContainer.new()
	help_card.add_theme_stylebox_override("panel", _panel_style(0.86))
	help_card.mouse_filter = Control.MOUSE_FILTER_IGNORE
	help_card.set_anchors_and_offsets_preset(Control.PRESET_TOP_RIGHT)
	help_card.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	help_card.position.x -= 24
	help_card.position.y = 24
	layer.add_child(help_card)
	var help := Label.new()
	help.add_theme_font_size_override("font_size", 21)
	help.add_theme_color_override("font_color", Color("f3e4c8"))
	var lines := PackedStringArray([
		"MEDZERNÍK  jazda / kinematický pohľad",
		"W A S D / šípky  plyn, brzda, zatáčanie",
		"SHIFT  ručná brzda      R  späť na štart",
		"C  kamera   + − / koliesko  priblíženie",
		"Orbit / Voľná kamera: ťahaj myšou      P  pauza",
		"V  ďalšie auto      Q  kvalita grafiky",
		"G  pohon 4x4 / 4x2 (Jeep)      L  uzávierka diferenciálu",
		"T / Shift+T  ďalší / predošlý test,  1–9, 0  priamo:"])
	for i in test_spots.size(): lines.append("   %s  %s" % [str(i + 1) if i < 9 else ("0" if i == 9 else "T"), test_spots[i][0]])
	lines.append("H  skryť pomoc      ESC  kinematický pohľad")
	help.text = "\n".join(lines)
	help_card.add_child(help)
	help_card.hide()

func _configure_quality() -> void:
	var requested := quality.to_lower()
	for argument in OS.get_cmdline_user_args():
		if argument.begins_with("--quality="):
			requested = argument.trim_prefix("--quality=").to_lower()
	if requested == "auto":
		requested = "notebook" if OS.has_feature("android") or RenderingServer.get_video_adapter_type() == RenderingDevice.DEVICE_TYPE_INTEGRATED_GPU else "reference"
	quality_name = requested if requested in PROFILES else "reference"
	var atmosphere: WorldEnvironment = $DesertAtmosphere
	atmosphere.environment = atmosphere.environment.duplicate()
	base_environment = atmosphere.environment.duplicate()
	_apply_render_profile(quality_name)
	print("DEMO QUALITY ", quality_name, " GPU=", RenderingServer.get_video_adapter_name())

## Rendering-only settings; safe to switch while driving (key Q). Terrain patch
## density is chosen once from the startup profile.
func _apply_render_profile(profile: String) -> void:
	render_profile = profile
	var viewport := get_viewport()
	var environment: Environment = $DesertAtmosphere.environment
	environment.ssao_enabled = base_environment.ssao_enabled
	environment.ssil_enabled = base_environment.ssil_enabled
	environment.volumetric_fog_enabled = base_environment.volumetric_fog_enabled
	environment.fog_density = base_environment.fog_density
	viewport.msaa_3d = Viewport.MSAA_4X if profile == "reference" else (Viewport.MSAA_2X if profile == "notebook" else Viewport.MSAA_DISABLED)
	viewport.screen_space_aa = Viewport.SCREEN_SPACE_AA_FXAA
	viewport.scaling_3d_mode = Viewport.SCALING_3D_MODE_BILINEAR
	viewport.scaling_3d_scale = 1.0
	RenderingServer.environment_set_volumetric_fog_volume_size(64, 64)
	RenderingServer.directional_shadow_atlas_set_size(4096, true)
	sun.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_4_SPLITS
	if profile != "reference":
		# Keep contact shadows, warm directional light, all dust layers and fog.
		# The faint secondary screen-space light bounce is the least visible pass.
		environment.ssil_enabled = false
		RenderingServer.environment_set_volumetric_fog_volume_size(32, 64)
		viewport.scaling_3d_mode = Viewport.SCALING_3D_MODE_FSR
		viewport.fsr_sharpness = 1.0
	if profile == "low":
		# For weak integrated GPUs: no SSAO/volumetrics, two shadow cascades on a
		# 2048 atlas and 80 % FSR. Plain depth fog keeps the hazy horizon.
		environment.ssao_enabled = false
		environment.volumetric_fog_enabled = false
		environment.fog_density = maxf(base_environment.fog_density, 0.0011)
		RenderingServer.directional_shadow_atlas_set_size(2048, true)
		sun.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_2_SPLITS
		viewport.scaling_3d_scale = 0.8
	if OS.has_feature("android"):
		# Mobile Vulkan keeps GPU sand/particles and shadows, while unsupported
		# desktop screen-space passes and volumetric fog stay disabled.
		environment.ssao_enabled = false
		environment.ssil_enabled = false
		environment.volumetric_fog_enabled = false
		viewport.scaling_3d_mode = Viewport.SCALING_3D_MODE_BILINEAR
		viewport.scaling_3d_scale = 0.85
	for argument in OS.get_cmdline_user_args():
		if argument.begins_with("--render-scale="):
			viewport.scaling_3d_scale = clampf(argument.trim_prefix("--render-scale=").to_float(), 0.5, 1.0)
			viewport.scaling_3d_mode = Viewport.SCALING_3D_MODE_FSR
	_apply_camera_environment()

func _process(delta: float) -> void:
	driving_card.visible = driving
	readout_clock += delta
	toast_time = maxf(0.0, toast_time - delta)
	if readout_clock >= 0.1:
		readout_clock = 0.0
		_update_readouts()
	if not paused:
		soil.update(delta)
		elapsed += delta
		test_grounds.update_wakes(vehicles(), delta)
	_update_camera(delta)

func _update_readouts() -> void:
	status_label.visible = driving or toast_time > 0.0 or help_card.visible
	# Test signs belong to the driving overview; they would block chase views.
	test_grounds.set_signs_visible(driving and camera_mode <= 1)
	if touch_ui:
		# Keep the status line just under the (variable-height) speed panel.
		status_label.position.y = driving_card.get_rect().end.y + 8.0 if driving_card.visible else 24.0
	status_label.text = "Kamera: %s (C)   ·   Kvalita: %s (Q)   ·   %d FPS   ·   H pomoc" % [CAMERA_NAMES[camera_mode], PROFILE_NAMES[render_profile], roundi(Engine.get_frames_per_second())]
	if not driving: return
	var state := "VO VZDUCHU" if not lead.grounded else ("SPIATOČKA" if lead.speed < -0.5 else "")
	if lead.overturned and lead.grounded: state = "PREVRÁTENÉ · R – späť na kolesá"
	driving_readout.text = "%d km/h   ·   %d kg" % [roundi(lead.speed_kmh), roundi(lead.mass_kg)]
	var ground: String = Surface.SURFACE_NAMES[lead.wheel_surface[0]]
	if lead.water_submersion > 0.0: ground += " · ponor %d cm" % roundi(lead.water_submersion * 100.0)
	driving_readout.text += "\nPovrch: " + ground
	driving_readout.text += "\nPohon: " + drivetrain_text()
	if current_spot >= 0 and Surface.in_test_ground(lead.position.x, lead.position.z, 4.0):
		driving_readout.text += "\nTest: " + str(test_spots[current_spot][0])
	if not state.is_empty(): driving_readout.text += "\n" + state

## Centre of mass of the followed car, using the rendered (interpolated) pose.
func _lead_transform() -> Transform3D:
	var body: Node3D = lead.body
	var visual := body.get_global_transform_interpolated()
	return Transform3D(visual.basis, visual.origin + visual.basis * lead.COM)

func _update_camera(delta: float) -> void:
	var target := _lead_transform()
	if camera_mode <= 1:
		var follow := driving or camera_mode == 1
		var aim := target.origin if follow else Vector3(sin(elapsed * 0.035) * 4.0, 1, 4.0 + sin(elapsed * 0.027) * 3.0)
		var smooth := 1.0 if camera_snap else 1.0 - exp(-delta * (1.8 if camera_mode == 0 else 6.0))
		if not paused: focus = focus.lerp(aim, smooth)
		camera.position = focus + OVERVIEW_OFFSET * (1.0 if camera_mode == 0 else 0.55)
		camera.look_at(focus)
		var size := camera_size if camera_mode == 0 else close_size
		camera.size = size if camera_snap else lerpf(camera.size, size, 1.0 - exp(-delta * 4.0))
		dust_detail = clampf(65.0 / camera.size, 0.45, 1.0)
		dust_density = 1.0
		camera_snap = false
		return
	dust_detail = 1.0
	dust_density = 0.5
	var centre := target.origin
	var forward := -target.basis.z
	var flat := Vector2(forward.x, forward.z)
	if flat.length_squared() > 0.04:
		var heading := atan2(-flat.x, -flat.y)
		chase_heading = heading if camera_snap else lerp_angle(chase_heading, heading, 1.0 - exp(-delta * 3.2))
	var desired: Vector3
	var look := centre
	if camera_mode == 5:
		# Roof-front view that rolls and pitches with the body. Derived from the
		# chassis hull, so it sits just above either vehicle model, never inside it.
		var top := -INF
		var front := INF
		for point: Vector3 in lead.hull_points:
			top = maxf(top, point.y)
			front = minf(front, point.z)
		var eye := Vector3(0, top + 0.18, front * 0.3)
		camera.global_transform = Transform3D(target.basis * Basis(Vector3.RIGHT, deg_to_rad(-7.0)), centre + target.basis * eye)
		camera_position = camera.position
		camera_snap = false
		return
	var distance: float = follow_distance[camera_mode]
	if camera_mode in FREE_CAMERAS:
		# Orbit drifts around the car when left alone; the free camera stays
		# exactly where the finger put it (relative to the world, not the car).
		orbit_idle += delta
		if camera_mode == 4 and orbit_idle > 4.0 and not paused: orbit_yaw += delta * 0.12
		desired = centre + Vector3(sin(orbit_yaw) * cos(orbit_pitch), sin(orbit_pitch), cos(orbit_yaw) * cos(orbit_pitch)) * distance
		look = centre + Vector3.UP * 0.3
	else:
		var behind := Vector3(sin(chase_heading), 0, cos(chase_heading))
		desired = centre + behind * distance + Vector3.UP * (distance * 0.3 + (1.1 if camera_mode == 2 else 0.6))
		look = centre - behind * 2.5 + Vector3.UP * (0.7 if camera_mode == 2 else 0.5)
	camera_position = desired if camera_snap else camera_position.lerp(desired, 1.0 - exp(-delta * 7.0))
	# Never let the lens dip into a dune between the camera and the car.
	camera_position.y = maxf(camera_position.y, soil.height_at(camera_position.x, camera_position.z) + 0.6)
	camera.look_at_from_position(camera_position, look)
	camera_snap = false

func _set_camera_mode(mode: int) -> void:
	camera_mode = posmod(mode, CAMERA_NAMES.size())
	camera_snap = true
	focus = _lead_transform().origin if (camera_mode >= 1 or driving) else focus
	orbit_idle = 0.0
	if camera_mode == 6:
		# Start the free camera behind the car; from then on the finger rules.
		var forward := -_lead_transform().basis.z
		orbit_yaw = atan2(-forward.x, -forward.z)
		orbit_pitch = 0.3
	_apply_camera_environment()
	toast_time = 2.5

## Perspective views see the horizon: thicker depth haze hides the map edge,
## and tighter shadow cascades sharpen the nearby car. Overviews keep the
## original orthographic settings.
func _apply_camera_environment() -> void:
	if camera == null: return
	var perspective := camera_mode >= 2
	camera.projection = Camera3D.PROJECTION_PERSPECTIVE if perspective else Camera3D.PROJECTION_ORTHOGONAL
	camera.fov = [55.0, 55.0, 62.0, 70.0, 55.0, 78.0, 60.0][camera_mode]
	camera.near = 0.08 if camera_mode == 5 else (0.2 if perspective else 0.5)
	camera.far = 420.0 if perspective else 500.0
	var environment: Environment = $DesertAtmosphere.environment
	var base_fog := maxf(base_environment.fog_density, 0.0011) if render_profile == "low" else base_environment.fog_density
	environment.fog_density = 0.0035 if perspective else base_fog
	# Ground-level views get a pale desert sky; the haze blends it into the dunes.
	# Ambient light stays the authored constant colour in every view.
	environment.background_mode = Environment.BG_SKY if perspective else base_environment.background_mode
	environment.fog_sky_affect = 0.55 if perspective else base_environment.fog_sky_affect
	if perspective and environment.sky == null:
		var sky_material := ProceduralSkyMaterial.new()
		sky_material.sky_top_color = Color("8fb0c8")
		sky_material.sky_horizon_color = Color("d9cdb3")
		sky_material.sky_curve = 0.12
		sky_material.ground_bottom_color = Color("b9a585")
		sky_material.ground_horizon_color = Color("d9cdb3")
		sky_material.sun_angle_max = 12.0
		var sky := Sky.new()
		sky.sky_material = sky_material
		sky.radiance_size = Sky.RADIANCE_SIZE_64
		sky.process_mode = Sky.PROCESS_MODE_QUALITY
		environment.sky = sky
	sun.directional_shadow_max_distance = 90.0 if perspective else 250.0

func _unhandled_input(event: InputEvent) -> void:
	# On touch screens dragging belongs to the pedals; the orbit drifts by itself.
	# Touch screens drive the free cameras through mobile_controls (drag/pinch).
	if event is InputEventMouseMotion and event.button_mask != 0 and camera_mode in FREE_CAMERAS and not touch_ui:
		camera_drag(event.relative)
	elif event is InputEventMouseButton and event.pressed:
		if event.button_index == MOUSE_BUTTON_WHEEL_UP: _zoom(-1)
		elif event.button_index == MOUSE_BUTTON_WHEEL_DOWN: _zoom(1)

func _zoom(direction: int) -> void:
	match camera_mode:
		0: camera_size = clampf(camera_size + direction * 8.0, 12.0, 115.0)
		1: close_size = clampf(close_size + direction * 3.0, 8.0, 60.0)
		2, 3, 4, 6: follow_distance[camera_mode] = clampf(follow_distance[camera_mode] + direction * 1.0, 3.0, 30.0)

## Finger/mouse drag on a free camera: yaw around the car and tilt.
func camera_drag(relative: Vector2) -> void:
	orbit_yaw -= relative.x * 0.006
	orbit_pitch = clampf(orbit_pitch + relative.y * 0.004, 0.03, 1.4)
	orbit_idle = 0.0

## Two-finger pinch: factor > 1 spreads the fingers (moves closer).
func camera_pinch(factor: float) -> void:
	if camera_mode in FREE_CAMERAS or camera_mode in [2, 3]:
		follow_distance[camera_mode] = clampf(follow_distance[camera_mode] / maxf(factor, 0.01), 3.0, 30.0)
	elif camera_mode == 0:
		camera_size = clampf(camera_size / maxf(factor, 0.01), 12.0, 115.0)
	elif camera_mode == 1:
		close_size = clampf(close_size / maxf(factor, 0.01), 8.0, 60.0)
	orbit_idle = 0.0

## Part-time 4x4 / rear-drive 4x2 (the Jeep; the van is front-drive only).
func toggle_drive() -> void:
	if lead.front_drive: return
	lead.four_wheel_drive = not lead.four_wheel_drive
	toast_time = 2.5

func toggle_diff() -> void:
	lead.diff_locked = not lead.diff_locked
	toast_time = 2.5

func drivetrain_text() -> String:
	var drive := "4x2 predný" if lead.front_drive else ("4x4" if lead.four_wheel_drive else "4x2 zadný")
	return "%s · dif. %s" % [drive, "zamknutý" if lead.diff_locked else "otvorený"]

func teleport_to_test(index: int) -> void:
	current_spot = posmod(index, test_spots.size())
	var spot: Array = test_spots[current_spot]
	lead.place_on_ground(Vector3(spot[1], 0, spot[2]), spot[3])
	lead._stop_emitters()
	lead.reset_physics_interpolation()
	driving = true
	lead.manual = true
	if camera_mode == 0: camera_size = minf(camera_size, 65.0)
	camera_snap = true
	focus = lead.position
	toast_time = 2.5

func _switch_vehicle() -> void:
	var cars := vehicles()
	var next: CharacterBody3D = cars[(cars.find(lead) + 1) % cars.size()]
	_release_lead()
	lead = next
	lead.manual = driving
	camera_snap = true
	toast_time = 2.5

## A car handed back to the patrol returns to its route rather than trying to
## drive cross-country from a distant test lane.
func _release_lead() -> void:
	lead.manual = false
	if not patrol_enabled: return
	var nearest := INF
	for i in 64:
		nearest = minf(nearest, Surface.route_xz(float(i) / 64.0 * TAU, lead.lane).distance_to(Vector2(lead.position.x, lead.position.z)))
	if nearest > 18.0: lead.reset_to_route()

func _unhandled_key_input(event: InputEvent) -> void:
	if not event.is_pressed() or event.is_echo(): return
	var key: int = event.keycode
	if key == KEY_SPACE:
		driving = not driving
		lead.manual = driving
		if not driving: _release_lead()
		camera_size = 65.0 if driving else 91.0
	if key == KEY_R:
		if current_spot >= 0 and Surface.in_test_ground(lead.position.x, lead.position.z, 10.0): teleport_to_test(current_spot)
		else: lead.reset_to_route()
	if key == KEY_P:
		paused = not paused
		for vehicle in $Patrol.get_children():
			vehicle.set_physics_process(not paused)
			for particle in vehicle.find_children("*", "GPUParticles3D", true, false):
				particle.speed_scale = 0.0 if paused else 1.0
	if key == KEY_ESCAPE:
		if driving: _release_lead()
		driving = false
		lead.manual = false
		camera_size = 91.0
	if key == KEY_EQUAL or key == KEY_KP_ADD: _zoom(-1)
	if key == KEY_MINUS or key == KEY_KP_SUBTRACT: _zoom(1)
	if key == KEY_C: _set_camera_mode(camera_mode + (-1 if event.shift_pressed else 1))
	if key == KEY_T: teleport_to_test(current_spot + (-1 if event.shift_pressed else 1))
	if key >= KEY_1 and key <= KEY_9 and key - KEY_1 < test_spots.size(): teleport_to_test(key - KEY_1)
	if key == KEY_0 and test_spots.size() > 9: teleport_to_test(9)
	if key == KEY_V: _switch_vehicle()
	if key == KEY_G: toggle_drive()
	if key == KEY_L: toggle_diff()
	if key == KEY_Q:
		_apply_render_profile(PROFILES[(PROFILES.find(render_profile) + 1) % PROFILES.size()])
		toast_time = 2.5
	if key == KEY_H or key == KEY_F1:
		help_card.visible = not help_card.visible
