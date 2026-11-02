extends Node3D
## Starts as an uninterrupted, HUD-free moving diorama. Tab enables player driving.
const Surface = preload("res://desert_battlefield/terrain.gd")
const EndlessModels = preload("res://desert_battlefield/endless_models.gd")
const GameSettings = preload("res://offroad/game_settings.gd")
const MenuUI = preload("res://offroad/menu_ui.gd")
const PROFILES := ["notebook", "reference", "low"]
const PROFILE_NAMES := {"notebook": "Notebook", "reference": "Referenčná", "low": "Nízka"}
const CAMERA_NAMES := ["Prehľad", "Blízko zhora", "Za autom", "Nízko za autom", "Orbit", "Zo strechy", "Voľná · prstom"]
## Camera modes that orbit the car and follow finger/mouse dragging.
const FREE_CAMERAS := [4, 6]
const OVERVIEW_OFFSET := Vector3(41, 120, 58)
@export_enum("Auto", "Notebook", "Reference", "Low") var quality := "Auto"
## World the scene drives on (terrain.gd queries), null for the desert.
@export var world_script: Script
## Coarse ground mesh the soil field and fine patches attach to.
@export var ground_node := NodePath("SculptedDunes")
## Builds the ground meshes and props on top of the terrain (build, update_wakes,
## set_signs_visible).
@export var grounds_script: Script
## Sky of the perspective cameras: top, horizon and ground colours.
@export var sky_colors := PackedColorArray([Color("8fb0c8"), Color("d9cdb3"), Color("b9a585")])
## Start with the lead Jeep only (the other cars are removed unless the patrol
## runs or a check asks for them).
@export var single_vehicle := true
## Model the lead car (Scout01) drives, from scripts/vehicle_catalog.gd. The
## scene's own Jeep is kept as a second car (Scout03) only when more than one
## car is driven (patrol or checks); otherwise it is not built at all.
@export var lead_model := "m1009"
## Everything is made and the game is on.
signal ready_for_play
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
var follow_distance := [0.0, 0.0, 8.5, 5.2, 10.0, 0.0, 9.0]
var orbit_yaw := 0.7
var orbit_pitch := 0.42
var orbit_idle := 0.0
## The road camera's own place: the height it follows the car at (bumps and suspension
## smoothed away), and how far it is lifted to keep the ground out of the view.
var camera_centre_y := 0.0
var camera_lift := 0.0
## How steeply the car is climbing (radians, smoothed): the road camera stays at the same
## height over the road behind the car, uphill or down.
var camera_slope := 0.0
## How soon after a finger the camera goes back behind the car, how fast it follows the car's
## heading then, and the height it goes back to.
const CAMERA_RETURN_AFTER := 0.7
const CAMERA_FOLLOW := 4.0
const CAMERA_PITCH := 0.3
## Seconds without any input; past AFK_ORBIT the orbit camera circles the car.
var afk_time := 0.0
const AFK_ORBIT := 30.0
var chase_heading := 0.0
var camera_position := Vector3.ZERO
var camera_snap := true
## Fine particle density: fewer ballistic grains when the view is zoomed out.
var dust_detail := 1.0
## Dust-cloud density: ground-level views look through the cloud, so it is thinned.
var dust_density := 1.0
var test_spots := []
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
var pause_menu: ColorRect
var readout_clock := 0.0
var toast_time := 0.0
var base_environment: Environment
@onready var camera: Camera3D = $Camera3D
@onready var sun: DirectionalLight3D = $LateAfternoonSun
@onready var lead: CharacterBody3D = $Patrol/Scout01

## Worlds generated from a seed when the scene starts, and the expedition
## (fuel, supplies, camps) played in each.
const EXPEDITIONS := {
	"res://offroad/endless_terrain.gd": "res://offroad/endless_expedition.gd",
}

## The world must be active before the vehicles enter the tree: they sample
## the ground and the patrol route in their own _ready.
func _enter_tree() -> void:
	if world_script != null and EXPEDITIONS.has(world_script.resource_path):
		world_script.generate(world_script.initial_seed())
	Surface.world = world_script
	if endless_world: lead_model = preload("res://offroad/garage.gd").run_car()
	_fit_lead_model()
	# Weather belongs to a world: a new scene starts with dry, bare ground.
	Surface.wetness = 0.0
	Surface.snow_cover = 0.0
	Surface.snow_depth = 0.0
	Surface.frost = 0.0
	test_spots = Surface.test_spots()

func _ready() -> void:
	# (The road's world is made a little a frame, under its loading screen; nothing else
	# moves until it is there.)
	var loading: CanvasLayer
	if endless_world:
		loading = preload("res://offroad/loading_screen.gd").open(get_tree())
		set_process(false)
		set_process_input(false)
		set_process_unhandled_input(false)
		set_process_unhandled_key_input(false)
		for car in vehicles(): car.set_physics_process(false)
	_fit_window()
	_configure_quality()
	get_window().size_changed.connect(_on_window_resized)
	if single_vehicle and not patrol_enabled and "--patrol" not in OS.get_cmdline_user_args():
		# Switched off rather than freed (freeing a car straight after its own
		# setup corrupts the scene teardown): hidden, idle, without collision,
		# and left out of vehicles() so physics, ruts and the V key skip it.
		for car in vehicles():
			if car == lead: continue
			car.set_meta("switched_off", true)
			car.visible = false
			car.process_mode = Node.PROCESS_MODE_DISABLED
			car.get_node("CollisionShape3D").disabled = true
			car.position.y -= 500.0
	soil.configure(self)
	await _configure_test_grounds()
	_create_driving_readout()
	_create_pause_menu()
	if touch_ui:
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
	# Start in the orbit camera behind the car (it swings around by itself only
	# when nobody touches anything, see AFK_ORBIT).
	_set_camera_mode(4)
	toast_time = 0.0
	if world_script != null and EXPEDITIONS.has(world_script.resource_path):
		var expedition: Node = (load(EXPEDITIONS[world_script.resource_path]) as GDScript).new()
		expedition.name = "Expedition"
		add_child(expedition)
		await expedition.setup(self, loading)
	if loading != null:
		loading.close()
		set_process(true)
		set_process_input(true)
		set_process_unhandled_input(true)
		set_process_unhandled_key_input(true)
		lead.set_physics_process(true)
		ready_for_play.emit()

## Before the cars set themselves up: the lead becomes lead_model, and a copy of
## the scene's original lead (the Jeep) joins the patrol beside it.
func _fit_lead_model() -> void:
	var scout := get_node_or_null("Patrol/Scout01") as Node3D
	if scout == null or lead_model.is_empty() or has_node("Patrol/Scout03"): return
	var spare_needed := not single_vehicle or patrol_enabled or "--patrol" in OS.get_cmdline_user_args()
	var body := scout.get_node("Body") as Node3D
	if body.get_meta("endless_drive_model", "") == lead_model: return
	if spare_needed:
		var spare := scout.duplicate() as Node3D
		spare.name = "Scout03"
		spare.set("lane", 2)
		$Patrol.add_child(spare)
	for child in body.get_children():
		body.remove_child(child)
		child.free()
	EndlessModels.vehicle(body, lead_model, Color("737762"))
	var model: Dictionary = EndlessModels.Catalog.DATA[lead_model]
	scout.set("half_track", model.track * 0.5)
	scout.set("half_wheelbase", model.wheelbase * 0.5)
	scout.set("wheel_radius", model.radius)

## Patrol vehicles only (the Patrol node also holds their tread-mesh roots).
func vehicles() -> Array:
	return $Patrol.get_children().filter(func(node): return node is CharacterBody3D and not node.has_meta("switched_off"))

func _configure_test_grounds() -> void:
	if world_script == null:
		# The dune material hides the authored mesh beneath the test grounds.
		for material in [($SculptedDunes as MeshInstance3D).material_override, soil.fine_material]:
			material.set_shader_parameter("test_hole_a", Surface.COURSE)
			material.set_shader_parameter("test_hole_b", Surface.PAD)
			material.set_shader_parameter("test_hole_c", Surface.ROCKS)
			material.set_shader_parameter("test_hole_d", Surface.CIRCUIT)
	# The scene's own grounds: the desert test course, or another world's
	# dressing (forest, water, landmarks); see `grounds_script`.
	test_grounds = grounds_script.new()
	add_child(test_grounds)
	await test_grounds.build(self)

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
	get: return phone or "--touch-controls" in OS.get_cmdline_user_args()

var phone := GameSettings.is_mobile()
var endless_world: bool:
	get: return world_script != null and world_script.resource_path == "res://offroad/endless_terrain.gd"

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
	driving_readout.add_theme_font_size_override("font_size", 34 if touch_ui else 26)
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
		"TAB  jazda / kinematický pohľad",
		"W A S D / šípky  plyn, brzda, zatáčanie",
		"SHIFT / MEDZERNÍK  ručná brzda      R  späť na štart",
		"C  kamera   + − / koliesko  priblíženie",
		"Orbit / Voľná kamera: ťahaj myšou      P  pauza",
		"V  ďalšie auto      Q  kvalita grafiky      F11  celá obrazovka",
		"K  ďalšie počasie (lesná vrchovina)",
		"U  stierače      J  ostrekovač      M  motor      Y  navijak",
		"G  pohon 4x4 / 4x2 (M1009, Jeep)      L  uzávierka diferenciálu      F  svetlá",
		"T / Shift+T  ďalší / predošlý test,  1–9, 0  priamo:"])
	for i in test_spots.size(): lines.append("   %s  %s" % [str(i + 1) if i < 9 else ("0" if i == 9 else "T"), test_spots[i][0]])
	lines.append("H  skryť pomoc      ESC  menu (pauza)")
	help.text = "\n".join(lines)
	help_card.add_child(help)
	help_card.hide()
	_layout_hud.call_deferred()

## ESC menu: pauses the game over a dimmed screen with the main actions, so
## nothing is ever hidden without a way back.
func _create_pause_menu() -> void:
	var layer := CanvasLayer.new()
	layer.name = "PauseMenu"
	layer.layer = 10
	add_child(layer)
	pause_menu = ColorRect.new()
	pause_menu.color = Color(0.03, 0.03, 0.02, 0.55)
	pause_menu.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	layer.add_child(pause_menu)
	var centre := CenterContainer.new()
	centre.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	pause_menu.add_child(centre)
	var card := PanelContainer.new()
	card.add_theme_stylebox_override("panel", _panel_style(0.92))
	centre.add_child(card)
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", 10)
	card.add_child(column)
	var title := Label.new()
	title.text = "Pauza"
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	title.add_theme_font_size_override("font_size", 34)
	title.add_theme_color_override("font_color", Color("f3e4c8"))
	column.add_child(title)
	if endless_world: _pause_card(column)
	var entries := [] if endless_world else [
		["Pokračovať  (ESC)", close_pause_menu],
		["Ovládanie  (H)", func(): help_card.visible = not help_card.visible],
		["Ďalšia kamera  (C)", func(): _set_camera_mode(camera_mode + 1)],
		["Kvalita grafiky  (Q)", func(): _apply_render_profile(PROFILES[(PROFILES.find(render_profile) + 1) % PROFILES.size()])],
		["Celá obrazovka  (F11)", toggle_fullscreen],
		["Kinematický pohľad  (TAB)", _cinematic_from_menu],
	]
	if not OS.has_feature("web") and not endless_world: entries.append(["Ukončiť hru", func(): get_tree().quit()])
	for entry: Array in entries:
		var button := Button.new()
		button.text = entry[0]
		button.custom_minimum_size = Vector2(380, 54)
		button.add_theme_font_size_override("font_size", 24)
		button.pressed.connect(entry[1])
		column.add_child(button)
	pause_menu.hide()

## The endless road's pause menu, in the main menu's look.
func _pause_card(column: VBoxContainer) -> void:
	var card := column.get_parent() as PanelContainer
	card.add_theme_stylebox_override("panel", MenuUI.paper_style())
	card.custom_minimum_size.x = 620.0 * MenuUI.k()
	var title := column.get_child(0) as Label
	title.add_theme_font_override("font", MenuUI.FONT)
	title.add_theme_font_size_override("font_size", roundi(56.0 * MenuUI.k()))
	title.add_theme_color_override("font_color", MenuUI.PEN)
	column.add_child(MenuUI.button("POKRAČOVAŤ", close_pause_menu, "pen", 76.0, 40.0))
	column.add_child(MenuUI.button("SKOK  (TEST)", func():
		close_pause_menu()
		(get_node("Expedition") as Node).open_checkpoints(), "box"))
	column.add_child(MenuUI.button("NASTAVENIA", func(): MenuUI.open_settings(pause_menu, func(): _apply_render_profile(GameSettings.profile())), "box"))
	if not phone: column.add_child(MenuUI.button("OVLÁDANIE  (H)", func(): help_card.visible = not help_card.visible, "box"))
	if not phone: column.add_child(MenuUI.button("CELÁ OBRAZOVKA  (F11)", toggle_fullscreen, "box"))
	column.add_child(MenuUI.button("HLAVNÉ MENU", _to_main_menu, "box"))
	if not phone and not OS.has_feature("web"): column.add_child(MenuUI.button("UKONČIŤ HRU", func(): get_tree().quit(), "box"))

func _to_main_menu() -> void:
	(get_node("Expedition") as Node)._save_game()
	get_tree().change_scene_to_file("res://offroad/endless_menu.tscn")

func open_pause_menu() -> void:
	pause_menu.show()
	(pause_menu.find_children("*", "Button", true, false)[0] as Button).grab_focus()
	_set_paused(true)

func close_pause_menu() -> void:
	pause_menu.hide()
	_set_paused(false)

func _cinematic_from_menu() -> void:
	close_pause_menu()
	if driving: _release_lead()
	driving = false
	lead.manual = false
	camera_size = 91.0

func _set_paused(value: bool) -> void:
	paused = value
	for vehicle in $Patrol.get_children():
		vehicle.set_physics_process(not paused)
		for particle in vehicle.find_children("*", "GPUParticles3D", true, false):
			particle.speed_scale = 0.0 if paused else 1.0

func _configure_quality() -> void:
	GameSettings.load_settings()
	# (The endless road follows the player's settings, unless --quality= says otherwise.)
	var requested := GameSettings.profile() if endless_world else quality.to_lower()
	if endless_world: GameSettings.apply_settings()
	for argument in OS.get_cmdline_user_args():
		if argument.begins_with("--quality="):
			requested = argument.trim_prefix("--quality=").to_lower()
	if requested == "auto":
		requested = "notebook" if phone or RenderingServer.get_video_adapter_type() == RenderingDevice.DEVICE_TYPE_INTEGRATED_GPU else "reference"
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
		if not phone:
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
	if base_shadow_reach < 0.0: base_shadow_reach = sun.directional_shadow_max_distance
	sun.directional_shadow_max_distance = base_shadow_reach
	if phone:
		# Mobile Vulkan keeps GPU sand/particles and shadows, while unsupported
		# desktop screen-space passes and volumetric fog stay disabled. Each
		# quality gives up picture size and shadow range first.
		environment.ssao_enabled = false
		environment.ssil_enabled = false
		environment.volumetric_fog_enabled = false
		var tier: Array = PHONE_TIERS[profile]
		viewport.msaa_3d = tier[1]
		# (FXAA only where there is no MSAA.)
		viewport.screen_space_aa = Viewport.SCREEN_SPACE_AA_DISABLED if tier[1] != Viewport.MSAA_DISABLED else Viewport.SCREEN_SPACE_AA_FXAA
		viewport.scaling_3d_mode = Viewport.SCALING_3D_MODE_BILINEAR
		viewport.scaling_3d_scale = tier[0]
		sun.directional_shadow_mode = tier[2]
		RenderingServer.directional_shadow_atlas_set_size(tier[3], true)
		sun.directional_shadow_max_distance = tier[4]
	# Headlight shadows are a Reference-only extra (four shadowed spot lights).
	for car in vehicles():
		for beam: SpotLight3D in car.headlights: beam.shadow_enabled = profile == "reference" and not phone
	profile_scale = viewport.scaling_3d_scale
	for argument in OS.get_cmdline_user_args():
		if argument.begins_with("--render-scale="):
			viewport.scaling_3d_scale = clampf(argument.trim_prefix("--render-scale=").to_float(), 0.5, 1.0)
			viewport.scaling_3d_mode = Viewport.SCALING_3D_MODE_FSR
			profile_scale = -1.0
	_fit_render_scale()
	_apply_camera_environment()

## On a phone, per profile: 3D scale, MSAA, shadow splits, shadow atlas, shadow reach.
const PHONE_TIERS := {
	"low": [0.62, Viewport.MSAA_DISABLED, DirectionalLight3D.SHADOW_ORTHOGONAL, 1024, 40.0],
	"notebook": [0.75, Viewport.MSAA_DISABLED, DirectionalLight3D.SHADOW_ORTHOGONAL, 2048, 50.0],
	"reference": [0.88, Viewport.MSAA_2X, DirectionalLight3D.SHADOW_ORTHOGONAL, 2048, 60.0],
}
var base_shadow_reach := -1.0

## 3D scale chosen by the profile (-1 when fixed with --render-scale).
var profile_scale := 1.0
## Internal 3D pixels each profile renders at most: above this (1440p and 4K
## monitors, QHD phones) the scene is rendered smaller and upscaled, so frame
## cost stays near that of the resolution the profile was tuned for. The UI
## always stays at the native resolution (canvas_items stretch).
const PIXEL_BUDGET := {"reference": 2560 * 1440, "notebook": 1920 * 1080, "low": 1600 * 900}
const MOBILE_PIXEL_BUDGET := 1250 * 1000

func _fit_render_scale() -> void:
	if profile_scale < 0.0: return
	var viewport := get_viewport()
	var pixels := float(maxi(1, get_window().size.x * get_window().size.y))
	var budget: float = MOBILE_PIXEL_BUDGET if phone else PIXEL_BUDGET[render_profile]
	var scale := clampf(minf(profile_scale, sqrt(budget / pixels)), 0.5, 1.0)
	viewport.scaling_3d_scale = snappedf(scale, 0.01)
	# FSR sharpens the upscale on desktop; phones keep the cheaper bilinear pass.
	if scale < 1.0 and not phone: viewport.scaling_3d_mode = Viewport.SCALING_3D_MODE_FSR

func _on_window_resized() -> void:
	_fit_render_scale()
	_layout_hud()

## Desktop windows open at 80 % of the monitor (16:9) instead of a fixed
## 1280×720, unless a size was given on the command line.
func _fit_window() -> void:
	if OS.has_feature("mobile") or DisplayServer.get_name() == "headless": return
	var arguments := OS.get_cmdline_args()
	if "--resolution" in arguments or "-f" in arguments or "--fullscreen" in arguments: return
	var window := get_window()
	if window.mode != Window.MODE_WINDOWED: return
	var screen := DisplayServer.screen_get_usable_rect(window.current_screen)
	var size := Vector2(screen.size) * 0.8
	size = Vector2(minf(size.x, size.y * 16.0 / 9.0), minf(size.y, size.x * 9.0 / 16.0))
	window.size = Vector2i(size)
	window.position = screen.position + (screen.size - window.size) / 2

func toggle_fullscreen() -> void:
	var window := get_window()
	window.mode = Window.MODE_WINDOWED if window.mode in [Window.MODE_FULLSCREEN, Window.MODE_EXCLUSIVE_FULLSCREEN] else Window.MODE_FULLSCREEN

## Screen-edge insets (left, top, right, bottom) in UI units that notches,
## punch-hole cameras and rounded corners keep free on phones.
func safe_insets() -> Vector4:
	if not OS.has_feature("mobile"): return Vector4.ZERO
	var window := get_window()
	var screen := Vector2(DisplayServer.screen_get_size(window.current_screen))
	var safe := DisplayServer.get_display_safe_area()
	if safe.size.x <= 0 or screen.x <= 0.0: return Vector4.ZERO
	var units := get_viewport().get_visible_rect().size / screen
	return Vector4(safe.position.x * units.x, safe.position.y * units.y,
		(screen.x - safe.end.x) * units.x, (screen.y - safe.end.y) * units.y)

## Keeps the HUD clear of the screen edges and any notch.
func _layout_hud() -> void:
	if driving_card == null: return
	var inset := safe_insets()
	driving_card.position = Vector2(24 + inset.x, 24 + inset.y)
	status_label.position.x = (26 if touch_ui else 24) + inset.x
	# Anchored top-right and growing leftwards, so it stays aligned at any size.
	help_card.offset_left = help_card.offset_left - (help_card.offset_right + 24 + inset.z)
	help_card.offset_right = -24 - inset.z
	help_card.offset_top = 24 + inset.y

func _process(delta: float) -> void:
	# (A phone on the endless road has its own display, phone_hud.gd.)
	driving_card.visible = driving and not (touch_ui and endless_world)
	readout_clock += delta
	toast_time = maxf(0.0, toast_time - delta)
	# Someone holding the throttle is not away, even without new key presses.
	afk_time = 0.0 if driving and absf(lead.speed) > 1.0 else afk_time + delta
	if readout_clock >= 0.1:
		readout_clock = 0.0
		_update_readouts()
	if not paused:
		soil.update(delta)
		elapsed += delta
		test_grounds.update_wakes(vehicles(), delta)
	_update_camera(delta)

func _update_readouts() -> void:
	if touch_ui and endless_world:
		status_label.visible = false
		return
	status_label.visible = driving or toast_time > 0.0 or help_card.visible
	# Test signs belong to the driving overview; they would block chase views.
	test_grounds.set_signs_visible(driving and camera_mode <= 1)
	if touch_ui:
		# Keep the status line just under the (variable-height) speed panel.
		status_label.position.y = driving_card.get_rect().end.y + 8.0 if driving_card.visible else 24.0
	if endless_world: status_label.text = "Kvalita: %s   ·   %d FPS" % [GameSettings.QUALITY_NAMES[GameSettings.effective_quality()], roundi(Engine.get_frames_per_second())]
	else: status_label.text = "Kamera: %s (C)   ·   Kvalita: %s (Q)   ·   %d FPS   ·   H pomoc" % [CAMERA_NAMES[camera_mode], PROFILE_NAMES[render_profile], roundi(Engine.get_frames_per_second())]
	if not driving: return
	var state := "VO VZDUCHU" if not lead.grounded else ("SPIATOČKA" if lead.speed < -0.5 else "")
	if lead.overturned and lead.grounded: state = "PREVRÁTENÉ · R – späť na kolesá"
	driving_readout.text = "%d km/h   ·   %d kg" % [roundi(lead.speed_kmh), roundi(lead.mass_kg)]
	var ground: String = Surface.surface_name(lead.wheel_surface[0])
	if lead.water_submersion > 0.0: ground += " · ponor %d cm" % roundi(lead.water_submersion * 100.0)
	driving_readout.text += "\nPovrch: " + ground
	driving_readout.text += "\nPohon: " + drivetrain_text()
	if test_grounds.has_method("weather_text") and not endless_world: driving_readout.text += "\nPočasie: " + test_grounds.weather_text()
	if current_spot >= 0 and not endless_world and Surface.in_test_ground(lead.position.x, lead.position.z, 4.0):
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
	var heading_now := chase_heading
	if flat.length_squared() > 0.04:
		heading_now = atan2(-flat.x, -flat.y)
		chase_heading = heading_now if camera_snap else lerp_angle(chase_heading, heading_now, 1.0 - exp(-delta * 3.2))
	camera_centre_y = centre.y if camera_snap else lerpf(camera_centre_y, centre.y, 1.0 - exp(-delta * 9.0))
	centre.y = camera_centre_y
	var climbing := asin(clampf(forward.y, -0.6, 0.6))
	camera_slope = climbing if camera_snap else lerpf(camera_slope, climbing, 1.0 - exp(-delta * 2.5))
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
		orbit_idle += delta
		if not paused:
			# Driving: straight behind the car, whatever a finger did before.
			if orbit_idle > CAMERA_RETURN_AFTER and absf(lead.speed) > 1.0:
				var follow := 1.0 - exp(-delta * CAMERA_FOLLOW)
				orbit_yaw = lerp_angle(orbit_yaw, heading_now, follow)
				orbit_pitch = lerpf(orbit_pitch, CAMERA_PITCH, follow)
			elif camera_mode == 4 and afk_time > AFK_ORBIT:
				orbit_yaw += delta * 0.12
		# (Not while a finger holds it somewhere: there the angle is the finger's.)
		var settled := clampf((orbit_idle - CAMERA_RETURN_AFTER) / 1.0, 0.0, 1.0)
		var elevation := orbit_pitch - camera_slope * settled
		desired = centre + Vector3(sin(orbit_yaw) * cos(elevation), sin(elevation), cos(orbit_yaw) * cos(elevation)) * distance
		look = centre + Vector3.UP * 0.3
	else:
		var behind := Vector3(sin(chase_heading), 0, cos(chase_heading))
		desired = centre + behind * distance + Vector3.UP * (distance * 0.3 + (1.1 if camera_mode == 2 else 0.6))
		look = centre - behind * 2.5 + Vector3.UP * (0.7 if camera_mode == 2 else 0.5)
	camera_position = desired if camera_snap else camera_position.lerp(desired, 1.0 - exp(-delta * 12.0))
	# Ground between the lens and the car (a bank in a hairpin, a dune) lifts the camera:
	# smoothly, up quicker than down, so that it never jumps.
	var need := 0.0
	for share: float in [1.0, 0.66, 0.33]:
		var along_view: Vector3 = centre.lerp(camera_position, share)
		need = maxf(need, soil.height_at(along_view.x, along_view.z) + (1.2 if share == 1.0 else 0.9) - along_view.y)
	camera_lift = maxf(need, 0.0) if camera_snap else lerpf(camera_lift, maxf(need, 0.0), 1.0 - exp(-delta * (6.0 if need > camera_lift else 1.2)))
	var lens: Vector3 = camera_position + Vector3.UP * camera_lift
	# Never into the ground itself, nor below the water surface.
	lens.y = maxf(lens.y, soil.height_at(lens.x, lens.z) + 0.5)
	lens.y = maxf(lens.y, Surface.water_level_at(lens.x, lens.z) + 0.35)
	camera.look_at_from_position(lens, look)
	camera_snap = false

func _set_camera_mode(mode: int) -> void:
	camera_mode = posmod(mode, CAMERA_NAMES.size())
	camera_snap = true
	focus = _lead_transform().origin if (camera_mode >= 1 or driving) else focus
	orbit_idle = 0.0
	if camera_mode in FREE_CAMERAS:
		# Start orbit and free camera behind the car.
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
	# (The endless world has its far land, out to 2.4 km and more.)
	var far_view: float = 4200.0 if world_script != null and world_script.resource_path == "res://offroad/endless_terrain.gd" else 420.0
	camera.far = far_view if perspective else 500.0
	var environment: Environment = $DesertAtmosphere.environment
	var base_fog := maxf(base_environment.fog_density, 0.0011) if render_profile == "low" else base_environment.fog_density
	environment.fog_density = 0.0035 if perspective else base_fog
	# Ground-level views get a pale desert sky; the haze blends it into the dunes.
	# Ambient light stays the authored constant colour in every view.
	environment.background_mode = Environment.BG_SKY if perspective else base_environment.background_mode
	environment.fog_sky_affect = 0.55 if perspective else base_environment.fog_sky_affect
	if perspective and environment.sky == null:
		var sky_material := ProceduralSkyMaterial.new()
		sky_material.sky_top_color = sky_colors[0]
		sky_material.sky_horizon_color = sky_colors[1]
		sky_material.sky_curve = 0.12
		sky_material.ground_bottom_color = sky_colors[2]
		sky_material.ground_horizon_color = sky_colors[1]
		sky_material.sun_angle_max = 12.0
		var sky := Sky.new()
		sky.sky_material = sky_material
		sky.radiance_size = Sky.RADIANCE_SIZE_32 if phone else Sky.RADIANCE_SIZE_64
		sky.process_mode = Sky.PROCESS_MODE_INCREMENTAL if phone else Sky.PROCESS_MODE_QUALITY
		environment.sky = sky
	# (A phone keeps the reach of its quality.)
	sun.directional_shadow_max_distance = PHONE_TIERS[render_profile][4] if phone else (90.0 if perspective else 250.0)

func _input(event: InputEvent) -> void:
	var active := event is InputEventKey or event is InputEventMouseButton or event is InputEventScreenTouch
	active = active or event is InputEventJoypadButton or event is InputEventScreenDrag
	if active or (event is InputEventMouseMotion and event.button_mask != 0):
		afk_time = 0.0

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
	# (A thumb merely resting on the screen does not hold the camera away from behind the car.)
	if relative.length_squared() < 2.0: return
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

func toggle_lights() -> void:
	var on: bool = not lead.lights_on
	for car in vehicles(): car.lights_on = on
	toast_time = 2.5

func toggle_wipers() -> void:
	lead.wipers_on = not lead.wipers_on
	toast_time = 2.5

## Off at once; on through the starter (the expedition has the tank and the sound).
func toggle_engine() -> void:
	if has_node("Expedition"): (get_node("Expedition") as Node).toggle_engine()
	else: lead.engine_running = not lead.engine_running
	toast_time = 2.5

func toggle_diff() -> void:
	if lead.front_drive: return
	lead.diff_locked = not lead.diff_locked
	toast_time = 2.5

func drivetrain_text() -> String:
	var drive := "4x2 predný" if lead.front_drive else ("4x4" if lead.four_wheel_drive else "4x2 zadný")
	return "%s · dif. %s" % [drive, "zamknutý · max %d km/h" % roundi(lead.DIFF_LOCK_SPEED * 3.6) if lead.diff_locked else "otvorený"]

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
	if key == KEY_TAB:
		driving = not driving
		lead.manual = driving
		if not driving: _release_lead()
		camera_size = 65.0 if driving else 91.0
	if key == KEY_R:
		# (In the endless world back onto the road where the car is, not to a test spot.)
		var endless := world_script != null and world_script.resource_path == "res://offroad/endless_terrain.gd"
		if current_spot >= 0 and not endless and Surface.in_test_ground(lead.position.x, lead.position.z, 10.0): teleport_to_test(current_spot)
		else: lead.reset_to_route()
	if key == KEY_P: _set_paused(not paused)
	if key == KEY_ESCAPE:
		if pause_menu.visible: close_pause_menu()
		else: open_pause_menu()
		return
	if pause_menu.visible: return
	if key == KEY_EQUAL or key == KEY_KP_ADD: _zoom(-1)
	if key == KEY_MINUS or key == KEY_KP_SUBTRACT: _zoom(1)
	# (The endless road has the one orbit camera, with zoom.)
	if key == KEY_C and not endless_world: _set_camera_mode(camera_mode + (-1 if event.shift_pressed else 1))
	if key == KEY_T: teleport_to_test(current_spot + (-1 if event.shift_pressed else 1))
	if key >= KEY_1 and key <= KEY_9 and key - KEY_1 < test_spots.size(): teleport_to_test(key - KEY_1)
	if key == KEY_0 and test_spots.size() > 9: teleport_to_test(9)
	if key == KEY_V: _switch_vehicle()
	if key == KEY_G: toggle_drive()
	if key == KEY_L: toggle_diff()
	if key == KEY_F: toggle_lights()
	if key == KEY_U: toggle_wipers()
	if key == KEY_M: toggle_engine()
	if key == KEY_Y and has_node("Expedition"): (get_node("Expedition") as Node).toggle_winch()
	if key == KEY_K and test_grounds.has_method("next_weather"): test_grounds.next_weather()
	if key == KEY_J and "washer_strokes" in lead: lead.washer_strokes = 4
	if key == KEY_Q:
		_apply_render_profile(PROFILES[(PROFILES.find(render_profile) + 1) % PROFILES.size()])
		toast_time = 2.5
	if key == KEY_F11 or (key == KEY_ENTER and event.alt_pressed): toggle_fullscreen()
	if key == KEY_H or key == KEY_F1:
		help_card.visible = not help_card.visible

